"""Fixed-target SSH transport. Construction is offline; lock() opens one session.

Only owner supplied readiness evidence can enable a live invocation. Tests inject
Popen; this module never discovers credentials or weakens existing SSH trust.
"""
import base64
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import re
import uuid

import systemd_local_rollback as core

TARGET = 'baota-124'
RELEASE_ROOTS = {core.JAR, 'frontend/dist/wealth-hub', 'frontend/dist/wealth-hub-mobile'}
CONFIGS = {
    'config/unit': core.CONTRACT['FragmentPath'],
    'config/override': core.CONTRACT['DropInPaths'],
    'config/nginx': '/www/server/panel/vhost/nginx/java_wealth-hub-1.conf',
}


def restricted(path):
    """Inspect effective on-disk protection; never trust a manifest ACL string."""
    path = Path(path)
    if any(p.is_symlink() or (hasattr(p, 'is_junction') and p.is_junction()) for p in [path, *path.parents]):
        raise core.Blocked('restricted backup symlink')
    if os.name == 'nt':
        # -File binds the path as data, avoiding PowerShell -Command parsing.
        script = Path(__file__).with_name('verify_backup_acl.ps1')
        result = subprocess.run(['powershell', '-NoProfile', '-NonInteractive',
                                 '-ExecutionPolicy', 'Bypass', '-File', str(script),
                                 '-BackupPath', str(path)], capture_output=True, timeout=15)
        if result.returncode:
            raise core.Blocked('restricted Windows ACL required')
    else:
        info = path.stat()
        if info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise core.Blocked('restricted Unix owner/mode required')


def readiness(evidence, release):
    required = {'target', 'expires_at', 'owner_approved', 'checks', 'artifacts', 'originals', 'source_commit'}
    if set(evidence) != required or evidence['target'] != TARGET:
        raise core.Blocked('missing fixed-target readiness evidence')
    checks = {'build', 'types', 'offline_recovery', 'schema', 'roles', 'backup', 'release_approval'}
    if evidence['owner_approved'] is not True or evidence['checks'] != dict.fromkeys(checks, 'PASS'):
        raise core.Blocked('incomplete readiness evidence')
    if not isinstance(evidence['expires_at'], (int, float)) or evidence['expires_at'] <= time.time():
        raise core.Blocked('expired readiness evidence')
    if not isinstance(evidence['source_commit'], str) or not re.fullmatch('[0-9a-f]{40}', evidence['source_commit']):
        raise core.Blocked('frozen source SHA required')
    artifacts = release_inventory(release)
    if evidence['artifacts'] != artifacts or not evidence['originals']:
        raise core.Blocked('release/original evidence mismatch')
    return artifacts


def release_inventory(release):
    if set(release) != RELEASE_ROOTS:
        raise core.Blocked('fixed release roots required')
    result = {}
    for name, source in release.items():
        source = Path(source)
        if not source.exists() or any(p.is_symlink() or (hasattr(p, 'is_junction') and p.is_junction())
                                      for p in [source, *source.parents]):
            raise core.Blocked('missing or symlink release')
        if (name == core.JAR and not source.is_file()) or (name != core.JAR and not source.is_dir()):
            raise core.Blocked('release object type mismatch')
        if name != core.JAR and not (source / 'index.html').is_file():
            raise core.Blocked('missing frontend index')
        for path in [source, *sorted(source.rglob('*'))] if source.is_dir() else [source]:
            if path.is_symlink() or (hasattr(path, 'is_junction') and path.is_junction()) or not (path.is_file() or path.is_dir()):
                raise core.Blocked('unsupported release object')
            key = name if path == source else name + '/' + path.relative_to(source).as_posix()
            core.relative_path(key)
            result[key] = {'kind': 'directory' if path.is_dir() else 'file',
                           'size': path.stat().st_size if path.is_file() else 0,
                           'sha256': core.digest(path) if path.is_file() else None}
            if len(result) > 4096 or result[key]['size'] > 256 * 1024 * 1024:
                raise core.Blocked('release inventory bound')
    if sum(m['size'] for m in result.values()) > 512 * 1024 * 1024:
        raise core.Blocked('release total bound')
    return result


class SSHAdapter:
    offline = False
    security_check = staticmethod(restricted)

    def __init__(self, evidence, release, backup_parent, popen=subprocess.Popen, timeout=45, start_timeout=60):
        self.expected = readiness(evidence, release)
        restricted(backup_parent)
        self.evidence, self.release, self.popen = evidence, release, popen
        self.timeout, self.process, self.pool = timeout, None, None
        self.token = uuid.uuid4().hex
        self.start_timeout = min(start_timeout, 60)

    def rpc(self, op, **args):
        if self.process is None or self.process.poll() is not None:
            raise core.Blocked('SSH session unavailable')
        def exchange():
            self.process.stdin.write(json.dumps({'op': op, **args}).encode() + b'\n')
            self.process.stdin.flush()
            raw = self.process.stdout.readline(2 * core.CHUNK + 1)
            if not raw or len(raw) > 2 * core.CHUNK:
                self.process.kill()
                raise core.Blocked('SSH stream truncated/oversized')
            response = json.loads(raw)
            if not isinstance(response, dict) or set(response) not in ({'error'}, {'result'}):
                self.process.kill()
                raise core.Blocked('invalid SSH response schema')
            if 'error' in response:
                raise core.Blocked('remote operation rejected: ' + op)
            return response['result']
        try:
            return self.pool.submit(exchange).result(timeout=self.timeout)
        except concurrent.futures.TimeoutError:
            self.process.kill()  # Only this adapter's owned SSH child.
            raise core.Blocked('SSH operation timeout') from None
        except (BrokenPipeError, json.JSONDecodeError) as error:
            self.process.kill()
            raise core.Blocked('SSH connection interrupted') from error

    def lock(self, recovering=False):
        payload = base64.b64encode(Path(__file__).with_name('ssh_stream_remote.py').read_bytes()).decode()
        bootstrap = "import base64;exec(base64.b64decode('" + payload + "'))"
        self.process = self.popen(['ssh', '-T', '-o', 'BatchMode=yes', '-o',
                                  'StrictHostKeyChecking=yes', '-o', 'ConnectTimeout=10',
                                  TARGET, 'python3 -u -c "' + bootstrap + '"'],
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                 stderr=subprocess.DEVNULL)
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=1)
        try:
            self.rpc('lock', token=self.token, recovering=recovering)
        except BaseException:
            self.unlock()
            raise

    def unlock(self):
        if self.process:
            self.process.stdin.close()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)
        if self.pool:
            self.pool.shutdown(wait=False, cancel_futures=True)

    def contract(self):
        return self.rpc('contract')

    def state(self):
        return self.rpc('state')

    def preflight(self, release):
        readiness(self.evidence, release)
        actual = self.inventory()
        if actual != self.evidence['originals']:
            raise core.Blocked('original SHA/mode/owner drift')
        if any(name in actual and actual[name]['kind'] != meta['kind'] for name, meta in self.expected.items()):
            raise core.Blocked('release file/directory collision')

    def inventory(self):
        return self.rpc('inventory')

    def read(self, name):
        meta = self.evidence['originals'][name]
        sha, size = __import__('hashlib').sha256(), 0
        while size < meta['size']:
            chunk = base64.b64decode(self.rpc('read', name=name, offset=size), validate=True)
            if not chunk or len(chunk) > core.CHUNK:
                raise core.Blocked('invalid read chunk')
            size += len(chunk)
            sha.update(chunk)
            yield chunk
        if size != meta['size'] or sha.hexdigest() != meta['sha256']:
            raise core.Blocked('read SHA/length mismatch')

    def stop(self):
        self.rpc('prepare', token=self.token, originals=self.evidence['originals'],
                 artifacts=self.expected, source_commit=self.evidence['source_commit'])
        self.rpc('stop')

    def reconnect_for_restore(self):
        # 原会话中断后只接管同一随机事务；新的普通发布无法越过远端未完成标记。
        if self.process is None or self.process.poll() is not None:
            self.unlock()
            deadline = time.monotonic() + min(self.timeout, 45)
            while True:
                try:
                    self.lock(recovering=True)
                    break
                except core.Blocked:
                    if time.monotonic() >= deadline:
                        raise
                    time.sleep(1)
        core.contract_valid(self.contract())
        actual = self.inventory()
        for name, meta in actual.items():
            known = [m for m in (self.evidence['originals'].get(name), self.expected.get(name)) if m]
            if not known or not any(all(meta[k] == m[k] for k in ('kind', 'size', 'sha256')) for m in known):
                raise core.Blocked('unknown drift during disconnected recovery')
        if any(actual.get(name) != self.evidence['originals'].get(name) for name in CONFIGS):
            raise core.Blocked('configuration drift during disconnected recovery')

    def finish(self):
        self.rpc('finish', token=self.token)

    def start(self):
        self.rpc('start')
        deadline = time.monotonic() + self.start_timeout
        while time.monotonic() < deadline:
            try:
                core.accept(self)
                return
            except core.Blocked:
                time.sleep(1)
        raise core.Blocked('systemd cold-start/health timeout')

    def upload(self, inventory, reader, restoring=False):
        self.rpc('begin', inventory=inventory, restoring=restoring,
                 originals=None if restoring else self.evidence['originals'])
        for name, meta in inventory.items():
            if meta['kind'] != 'file':
                continue
            offset = 0
            for chunk in reader(name):
                if not chunk or len(chunk) > core.CHUNK:
                    raise core.Blocked('invalid upload chunk')
                self.rpc('write', name=name, offset=offset,
                         data=base64.b64encode(chunk).decode())
                offset += len(chunk)
            self.rpc('seal', name=name)
        self.rpc('commit')

    def replace(self, release):
        if release_inventory(release) != self.expected:
            raise core.Blocked('local artifacts drift')
        def reader(name):
            root = next(key for key in RELEASE_ROOTS if name == key or name.startswith(key + '/'))
            path = Path(release[root])
            if name != root:
                path = path / name[len(root) + 1:]
            with path.open('rb') as source:
                yield from iter(lambda: source.read(core.CHUNK), b'')
        self.upload(self.expected, reader)
        self.rpc('verify', inventory=self.expected)

    def restore(self, backup, inventory):
        restricted(backup.path)
        self.upload(inventory, backup.read, restoring=True)

    def backend_health(self):
        code, body = self.rpc('health')
        return code, base64.b64decode(body)

    def upstream_status(self):
        return self.rpc('upstream')

    def pc_status(self):
        return self.rpc('pc')


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--readiness', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--pc', type=Path, required=True)
    parser.add_argument('--h5', type=Path, required=True)
    parser.add_argument('--backup-parent', type=Path, required=True)
    parser.add_argument('--backup-receipt', required=True,
                        help='SHA256 receipt independently verified by Work; never OFFLINE_AUTO_CONFIRM')
    args = parser.parse_args()
    if args.backup_receipt == 'OFFLINE_AUTO_CONFIRM':
        raise core.Blocked('offline evidence cannot authorize live')
    release = {core.JAR: args.jar, 'frontend/dist/wealth-hub': args.pc,
               'frontend/dist/wealth-hub-mobile': args.h5}
    adapter = SSHAdapter(json.loads(args.readiness.read_bytes()), release, args.backup_parent)
    result = core.deploy(adapter, release, args.backup_parent, args.backup_receipt)
    print(json.dumps(result))
    return result['exit_code']


if __name__ == '__main__':
    raise SystemExit(main())
