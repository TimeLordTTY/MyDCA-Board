"""In-memory remote worker; executed only by an explicitly enabled SSHAdapter.

No remote archive or application backup is created. Only temporary new/restore
uploads are staged; flock is held by this worker until stdin EOF or its deadline.
"""
import base64
import fcntl
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import ssl
import stat
import subprocess
import sys
import re
import urllib.error
import urllib.request

ROOT = Path('/www/wwwroot/wealth-hub')
UNIT = 'spring_wealth-hub-1.service'
JAR = 'backend/wealth-hub-1.0.0.jar'
JVM = ['/www/server/java/jdk-17.0.8/bin/java', '-Xmx512M', '-Xms128M', '-jar', str(ROOT / JAR)]
CONFIGS = {'config/unit': '/usr/lib/systemd/system/' + UNIT,
           'config/override': '/etc/systemd/system/' + UNIT + '.d/override.conf',
           'config/nginx': '/www/server/panel/vhost/nginx/java_wealth-hub-1.conf'}
ROOTS = {JAR, 'frontend/dist/wealth-hub', 'frontend/dist/wealth-hub-mobile'}
CHUNK = 65536
JOURNAL = Path('/run/lock/mydca-systemd-release.transaction.json')


def journal_read():
    if not JOURNAL.exists():
        return None
    info = safe(JOURNAL).stat()
    if info.st_uid != 0 or stat.S_IMODE(info.st_mode) != 0o600 or info.st_size > 2 * CHUNK:
        raise ValueError('journal identity')
    return json.loads(JOURNAL.read_bytes())


def journal_write(value):
    # 这里只保存事务身份与哈希，不在服务器保存旧应用副本。
    data = json.dumps(value, sort_keys=True, separators=(',', ':')).encode()
    if len(data) > 2 * CHUNK:
        raise ValueError('journal size')
    fd = os.open(safe(JOURNAL), os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as sink:
        sink.write(data)
        sink.flush()
        os.fsync(sink.fileno())


def manifest_sha(entries):
    return hashlib.sha256(json.dumps(entries, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(CHUNK), b''):
            h.update(chunk)
    return h.hexdigest()


def allowed(name):
    if not isinstance(name, str) or '\\' in name or any(p in ('', '.', '..') for p in name.split('/')):
        raise ValueError('path')
    if name in CONFIGS:
        return Path(CONFIGS[name])
    if name != 'frontend/dist' and not any(name == r or name.startswith(r + '/') for r in ROOTS):
        raise ValueError('allowlist')
    return ROOT / name


def safe(path):
    for parent in [path, *path.parents]:
        if parent.is_symlink():
            raise ValueError('symlink')
    return path


def metadata(path):
    safe(path)
    info = path.stat()
    if not (stat.S_ISREG(info.st_mode) or stat.S_ISDIR(info.st_mode)):
        raise ValueError('object')
    directory = path.is_dir()
    return {'kind': 'directory' if directory else 'file', 'mode': stat.S_IMODE(info.st_mode),
            'uid': info.st_uid, 'gid': info.st_gid, 'size': 0 if directory else info.st_size,
            'sha256': None if directory else sha(path)}


def inventory():
    result = {}
    for name in [JAR, 'frontend/dist', *CONFIGS]:
        path = safe(allowed(name))
        paths = [path, *sorted(path.rglob('*'))] if path.is_dir() else [path]
        for item in paths:
            key = name if item == path else name + '/' + item.relative_to(path).as_posix()
            allowed(key)
            result[key] = metadata(item)
    return result


def command(*args):
    run = subprocess.run(args, capture_output=True, timeout=35, check=True)
    return run.stdout.decode().strip()


def properties():
    output = command('systemctl', 'show', UNIT, '--no-pager')
    return dict(line.split('=', 1) for line in output.splitlines() if '=' in line)


def state():
    props = properties()
    pid = int(props['MainPID'])
    pids = []
    argv = []
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():
            continue
        try:
            args = (path / 'cmdline').read_bytes().rstrip(b'\0').split(b'\0')
            args = [a.decode() for a in args]
        except (OSError, UnicodeError):
            continue
        if str(ROOT / JAR) in args:
            pids.append(int(path.name))
        if int(path.name) == pid:
            argv = args
            import pwd
            uid = int((path / 'status').read_text().split('Uid:', 1)[1].split()[1])
            if uid != pwd.getpwnam('www').pw_uid:
                raise ValueError('service identity')
    return {'active': props['ActiveState'], 'pid': pid, 'pids': sorted(pids), 'argv': argv}


def contract():
    props = properties()
    keys = ['Type', 'PIDFile', 'WorkingDirectory', 'User', 'Group', 'KillMode', 'Restart',
            'RestartUSec', 'TimeoutStopUSec', 'FragmentPath', 'DropInPaths']
    result = {key: props[key] for key in keys}
    # Parse the effective systemd argv, rather than substituting the expected JVM.
    result['ExecStart'] = props['ExecStart'].split('argv[]=', 1)[1].split(' ;', 1)[0].split()
    result['unit_sha256'] = sha(safe(Path(CONFIGS['config/unit'])))
    result['override_sha256'] = sha(safe(Path(CONFIGS['config/override'])))
    return result


def http(url):
    # Disable environment proxies and redirects: probes stay on fixed loopback.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        response = opener.open(urllib.request.Request(url, headers={'Host': 'www.timelordtty.cn'}), timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read(CHUNK + 1)
        if len(body) > CHUNK:
            raise ValueError('probe length')
        return response.code, base64.b64encode(body).decode()


def nginx(path):
    class LoopbackTLS(http.client.HTTPSConnection):
        def connect(self):
            raw = socket.create_connection(('127.0.0.1', 443), timeout=5)
            self.sock = self._context.wrap_socket(raw, server_hostname='www.timelordtty.cn')
    connection = LoopbackTLS('www.timelordtty.cn', timeout=5, context=ssl.create_default_context())
    try:
        connection.request('GET', path)
        response = connection.getresponse()
        if len(response.read(CHUNK + 1)) > CHUNK:
            raise ValueError('probe length')
        return response.status
    finally:
        connection.close()


class Worker:
    def __init__(self):
        self.lock = None
        self.stage = None
        self.entries = None
        self.sealed = set()
        self.restoring = False
        self.originals = None
        self.token = None
        self.transaction = None

    def call(self, request):
        op = request['op']
        if op == 'lock':
            if self.lock is not None or os.geteuid() != 0:
                raise ValueError('identity/lock')
            path = Path('/run/lock/mydca-systemd-release.lock')
            fd = os.open(path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
            self.lock = os.fdopen(fd, 'wb')
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            token = request.get('token')
            if not isinstance(token, str) or not re.fullmatch('[0-9a-f]{32}', token):
                raise ValueError('transaction token')
            self.transaction = journal_read()
            if self.transaction and (not request.get('recovering') or self.transaction['token'] != token):
                raise ValueError('unfinished transaction')
            if request.get('recovering') and self.transaction is None:
                raise ValueError('missing recovery transaction')
            self.token = token
            return True
        if self.lock is None:
            raise ValueError('lock required')
        if op == 'contract':
            return contract()
        if op == 'state':
            return state()
        if op == 'inventory':
            return inventory()
        if op == 'prepare':
            if request['token'] != self.token or not re.fullmatch('[0-9a-f]{40}', request['source_commit']):
                raise ValueError('prepare identity')
            proof = {'token': self.token, 'source_commit': request['source_commit'],
                     'originals_sha': manifest_sha(request['originals']),
                     'artifacts_sha': manifest_sha(request['artifacts'])}
            if self.transaction:
                if proof != self.transaction:
                    raise ValueError('recovery proof drift')
            else:
                if inventory() != request['originals']:
                    raise ValueError('original drift before stop')
                journal_write(proof)
                self.transaction = proof
            return True
        if op == 'finish':
            if request['token'] != self.token or journal_read() != self.transaction:
                raise ValueError('finish identity')
            JOURNAL.unlink()
            self.transaction = None
            return True
        if op == 'read':
            path = safe(allowed(request['name']))
            with path.open('rb') as source:
                source.seek(request['offset'])
                return base64.b64encode(source.read(CHUNK)).decode()
        if op in ('stop', 'start'):
            if not self.transaction:
                raise ValueError('prepared transaction required')
            command('systemctl', op, UNIT)
            if op == 'stop' and state()['pids']:
                raise ValueError('stop incomplete')
            return True
        if op == 'health':
            return http('http://127.0.0.1:8766/actuator/health')
        if op == 'upstream':
            return nginx('/api/v2/mobile/overview')
        if op == 'pc':
            return nginx('/wealth-hub/')
        if op == 'begin':
            entries = request['inventory']
            if not self.transaction or manifest_sha(entries) != self.transaction[
                    'originals_sha' if request['restoring'] else 'artifacts_sha']:
                raise ValueError('transaction manifest drift')
            if len(entries) > 4096 or sum(m['size'] for m in entries.values()) > 512 * 1024 * 1024:
                raise ValueError('inventory bound')
            if not request['restoring']:
                self.originals = request['originals']
                if inventory() != self.originals:
                    raise ValueError('original drift before upload')
            for name, meta in entries.items():
                safe(allowed(name))
                if not request['restoring'] and name in CONFIGS:
                    raise ValueError('config release forbidden')
                if meta['kind'] not in ('file', 'directory') or not 0 <= meta['size'] <= 256 * 1024 * 1024:
                    raise ValueError('metadata')
            if self.stage:
                shutil.rmtree(self.stage)
            import tempfile
            self.stage = Path(tempfile.mkdtemp(prefix='.stream-release-', dir=safe(ROOT)))
            self.entries, self.restoring, self.sealed = entries, request['restoring'], set()
            for name, meta in entries.items():
                target = self.stage / name
                target.parent.mkdir(parents=True, exist_ok=True)
                if meta['kind'] == 'directory':
                    target.mkdir(exist_ok=True)
                else:
                    target.touch(exist_ok=False)
            return True
        if op == 'write':
            name = request['name']
            meta = self.entries[name]
            data = base64.b64decode(request['data'], validate=True)
            target = self.stage / name
            if name in self.sealed or not data or len(data) > CHUNK or target.stat().st_size != request['offset']:
                raise ValueError('stream offset')
            if target.stat().st_size + len(data) > meta['size']:
                raise ValueError('stream length')
            with target.open('ab') as sink:
                sink.write(data)
                sink.flush()
                os.fsync(sink.fileno())
            return True
        if op == 'seal':
            name = request['name']
            meta = self.entries[name]
            target = self.stage / name
            if target.stat().st_size != meta['size'] or sha(target) != meta['sha256']:
                raise ValueError('stream SHA')
            self.sealed.add(name)
            return True
        if op == 'commit':
            if self.sealed != {n for n, m in self.entries.items() if m['kind'] == 'file'}:
                raise ValueError('unsealed')
            if state()['pids'] or properties()['ActiveState'] != 'inactive':
                raise ValueError('service must be stopped')
            if not self.restoring and inventory() != self.originals:
                raise ValueError('original drift before write')
            for name in sorted(self.entries, key=lambda n: n.count('/'), reverse=True):
                target = self.stage / name
                meta = self.entries[name]
                original = meta if self.restoring else metadata(safe(allowed(next(r for r in ROOTS if name == r or name.startswith(r + '/')))))
                os.chown(target, original['uid'], original['gid'])
                target.chmod(original['mode'] if self.restoring else (0o755 if meta['kind'] == 'directory' else 0o644))
            # Each file is atomically replaced on the same filesystem. Obsolete
            # assets are removed only after all replacements; no old tree copy.
            for root in ['frontend/dist/wealth-hub', 'frontend/dist/wealth-hub-mobile']:
                target = safe(allowed(root))
                staged = self.stage / root
                target.mkdir(exist_ok=True)
                for item in sorted(staged.rglob('*'), key=lambda p: (not p.is_dir(), len(p.parts))):
                    destination = safe(target / item.relative_to(staged))
                    if item.is_dir():
                        destination.mkdir(exist_ok=True)
                    else:
                        os.replace(item, destination)
                for item in sorted(target.rglob('*'), key=lambda p: len(p.parts), reverse=True):
                    safe(item)
                    name = root + '/' + item.relative_to(target).as_posix()
                    if name not in self.entries:
                        item.rmdir() if item.is_dir() else item.unlink()
                for name in sorted(self.entries, key=lambda n: n.count('/'), reverse=True):
                    if name == root or name.startswith(root + '/'):
                        meta = self.entries[name]
                        destination = safe(allowed(name))
                        if self.restoring:
                            os.chown(destination, meta['uid'], meta['gid'])
                            destination.chmod(meta['mode'])
                        elif meta['kind'] == 'directory':
                            staged_meta = metadata(self.stage / name)
                            os.chown(destination, staged_meta['uid'], staged_meta['gid'])
                            destination.chmod(0o755)
            os.replace(self.stage / JAR, safe(allowed(JAR)))
            if self.restoring:
                for name in CONFIGS:
                    # Config temporary must be on the destination filesystem.
                    target = safe(allowed(name))
                    if metadata(target) == self.entries[name]:
                        continue
                    import tempfile
                    fd, temp = tempfile.mkstemp(prefix='.mydca-stream-', dir=target.parent)
                    os.close(fd)
                    try:
                        shutil.copyfile(self.stage / name, temp)
                        meta = self.entries[name]
                        os.chown(temp, meta['uid'], meta['gid'])
                        os.chmod(temp, meta['mode'])
                        os.replace(temp, target)
                    finally:
                        if os.path.exists(temp):
                            os.unlink(temp)
                meta = self.entries['frontend/dist']
                os.chown(ROOT / 'frontend/dist', meta['uid'], meta['gid'])
                (ROOT / 'frontend/dist').chmod(meta['mode'])
                command('systemctl', 'daemon-reload')
            shutil.rmtree(self.stage)
            self.stage = None
            return True
        if op == 'verify':
            actual = inventory()
            for name, meta in request['inventory'].items():
                if any(actual[name][k] != meta[k] for k in ('kind', 'size', 'sha256')):
                    raise ValueError('release verify')
            if {n for n in actual if n not in CONFIGS and n != 'frontend/dist'} != set(request['inventory']):
                raise ValueError('unknown frontend files')
            return True
        raise ValueError('unknown operation')


def main():
    def deadline(signum, frame):
        raise TimeoutError('session deadline')
    signal.signal(signal.SIGALRM, deadline)
    signal.alarm(900)
    worker = Worker()
    try:
        while True:
            line = sys.stdin.buffer.readline(2 * CHUNK + 1)
            if not line:
                break
            if len(line) > 2 * CHUNK:
                break
            try:
                response = {'result': worker.call(json.loads(line))}
            except Exception:
                response = {'error': 'REMOTE_OPERATION_REJECTED'}
            print(json.dumps(response), flush=True)
    finally:
        # 未确认事务在断流后保持停服，释放flock后仅同token可接续本地原件恢复。
        if worker.transaction:
            try:
                command('systemctl', 'stop', UNIT)
            except Exception:
                pass  # 标记保留；客户端只能报告未确认恢复，不能宣称成功。
        if worker.stage:
            shutil.rmtree(worker.stage)
        if worker.lock:
            worker.lock.close()


if __name__ == '__main__':
    main()
