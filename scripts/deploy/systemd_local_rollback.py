"""Shared transaction core with filesystem sandbox and separate SSH transport."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import uuid
from health_probe import healthy

ROOT = '/www/wwwroot/wealth-hub'
UNIT = 'spring_wealth-hub-1.service'
JAVA = '/www/server/java/jdk-17.0.8/bin/java'
JAR = 'backend/wealth-hub-1.0.0.jar'
JVM = [JAVA, '-Xmx512M', '-Xms128M', '-jar', ROOT + '/' + JAR]
CHUNK = 65536
CONTRACT = {
    'Type': 'simple', 'PIDFile': '', 'ExecStart': JVM,
    'WorkingDirectory': ROOT + '/backend', 'User': 'www', 'Group': '',
    'KillMode': 'control-group', 'Restart': 'on-failure', 'RestartUSec': '5s',
    'TimeoutStopUSec': '30s', 'FragmentPath': '/usr/lib/systemd/system/' + UNIT,
    'DropInPaths': '/etc/systemd/system/' + UNIT + '.d/override.conf',
    'unit_sha256': '8cb49e86773d46e02e4ef9c87898a81206f67efa16d91cdbb0989704f74e54d7',
    'override_sha256': 'a1be5e19c472cab60ac1b7292450257de6dccfa81aa012a9c5f4584b9a56aef4',
}


class Blocked(RuntimeError):
    pass


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda: source.read(CHUNK), b''):
            result.update(chunk)
    return result.hexdigest()


def relative_path(value):
    if not value or '\\' in value or ':' in value or any(
        part in ('', '.', '..') for part in value.split('/')
    ):
        raise Blocked('invalid relative path')
    return value


def contract_valid(contract):
    if contract != CONTRACT:
        raise Blocked('unknown or drifted systemd contract')


def validate_state(state, allow_cold=False):
    if allow_cold and state == {'active': 'inactive', 'pid': 0, 'pids': [], 'argv': []}:
        return False
    pid = state.get('pid')
    if state.get('active') != 'active' or type(pid) is not int or pid <= 0:
        raise Blocked('abnormal service state')
    if state.get('pids') != [pid] or state.get('argv') != JVM:
        raise Blocked('MainPID, unique instance or effective JVM mismatch')
    return True


def transfer(chunks, destination, expected):
    sha, size = hashlib.sha256(), 0
    with Path(destination).open('xb') as sink:
        for chunk in chunks:
            if not isinstance(chunk, bytes) or not chunk or len(chunk) > CHUNK:
                raise Blocked('invalid stream chunk')
            size += len(chunk)
            if size > expected['size']:
                raise Blocked('oversized stream')
            sink.write(chunk)
            sha.update(chunk)
        sink.flush()
        os.fsync(sink.fileno())
    if size != expected['size'] or sha.hexdigest() != expected['sha256']:
        raise Blocked('truncated or corrupt stream')


class LocalBackup:
    def __init__(self, parent):
        self.path = Path(parent) / ('mydca-' + uuid.uuid4().hex)
        self.receipt = None
        self.security_check = None

    def capture(self, adapter, inventory):
        self.path.mkdir(mode=0o700, parents=True)
        for name, meta in inventory.items():
            target = self.path / relative_path(name)
            target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            if meta['kind'] == 'directory':
                target.mkdir(mode=0o700, exist_ok=True)
            else:
                transfer(adapter.read(name), target, meta)
                target.chmod(0o600)
        if adapter.inventory() != inventory:
            raise Blocked('source changed during backup')
        manifest = self.path / 'manifest.json'
        manifest.write_bytes(json.dumps(inventory, sort_keys=True, separators=(',', ':')).encode())
        manifest.chmod(0o600)
        self.receipt = digest(manifest)
        self.verify()
        return self.receipt

    def verify(self):
        if self.security_check:
            self.security_check(self.path)
            for entry in self.path.rglob('*'):
                self.security_check(entry)
        if not self.receipt or digest(self.path / 'manifest.json') != self.receipt:
            raise Blocked('missing or corrupt local manifest')
        inventory = json.loads((self.path / 'manifest.json').read_bytes())
        actual = set()
        for path in self.path.rglob('*'):
            if path.is_symlink():
                raise Blocked('backup symlink')
            if path.is_file() and path != self.path / 'manifest.json':
                actual.add(path.relative_to(self.path).as_posix())
        if actual != {name for name, meta in inventory.items() if meta['kind'] == 'file'}:
            raise Blocked('backup file inventory mismatch')
        for name, meta in inventory.items():
            path = self.path / relative_path(name)
            if meta['kind'] == 'directory':
                if not path.is_dir():
                    raise Blocked('backup directory missing')
            elif path.stat().st_size != meta['size'] or digest(path) != meta['sha256']:
                raise Blocked('backup bytes mismatch')
        return inventory

    def read(self, name):
        with (self.path / relative_path(name)).open('rb') as source:
            yield from iter(lambda: source.read(CHUNK), b'')


def accept(adapter):
    contract_valid(adapter.contract())
    validate_state(adapter.state())
    code, body = adapter.backend_health()
    if not healthy(str(code), body):
        raise Blocked('backend requires current JSON UP')
    if adapter.upstream_status() != 401:
        raise Blocked('independent NGINX unauthenticated upstream must return 401')
    if adapter.pc_status() != 200:
        raise Blocked('PC status')
    validate_state(adapter.state())


def deploy(adapter, release, backup_parent, confirm):
    """Lock/session spans backup, switch and recovery. Never claim disconnected recovery.

    0 = accepted deployment; 1 = blocked or accepted restoration;
    2 = failed/unconfirmed restoration. OFFLINE_AUTO_CONFIRM cannot authorize live.
    """
    result = {'exit_code': 1, 'status': 'BLOCKED_BEFORE_STOP'}
    backup = LocalBackup(backup_parent)
    backup.security_check = getattr(adapter, 'security_check', None)
    result['backup_path'] = str(backup.path)
    changed, locked, was_running = False, False, False
    try:
        adapter.lock()
        locked = True
        contract_valid(adapter.contract())
        original_state = adapter.state()
        was_running = validate_state(original_state, allow_cold=True)
        adapter.preflight(release)
        inventory = adapter.inventory()
        receipt = backup.capture(adapter, inventory)
        result.update(backup_path=str(backup.path), backup_manifest_sha256=receipt)
        confirmation = confirm(backup.path, receipt) if callable(confirm) else confirm
        if confirmation != receipt and confirmation != 'OFFLINE_AUTO_CONFIRM':
            raise Blocked('local backup confirmation required')
        if confirmation == 'OFFLINE_AUTO_CONFIRM' and adapter.offline is not True:
            raise Blocked('offline confirmation forbidden for live adapter')
        backup.verify()
        contract_valid(adapter.contract())
        if adapter.state() != original_state:
            raise Blocked('service changed during capture')
        if adapter.inventory() != inventory:
            raise Blocked('source changed before stop')
        # A partial stop requires recovery as well; systemd is the only process owner.
        changed = True
        adapter.stop()
        adapter.replace(release)
        adapter.start()
        accept(adapter)
        if hasattr(adapter, 'finish'):
            adapter.finish()
        result.update(exit_code=0, status='SUCCESS', backend='UP', upstream=401)
    except (OSError, Blocked, ValueError) as error:
        result['reason'] = str(error)
        if changed:
            try:
                inventory = backup.verify()
                if hasattr(adapter, 'reconnect_for_restore'):
                    adapter.reconnect_for_restore()
                adapter.stop()
                adapter.restore(backup, inventory)
                if was_running:
                    adapter.start()
                    accept(adapter)
                    result['rollback_backend'] = 'UP'
                else:
                    if validate_state(adapter.state(), allow_cold=True):
                        raise Blocked('cold rollback must remain inactive')
                    result['rollback_backend'] = 'ORIGINAL_INACTIVE'
                if adapter.inventory() != inventory:
                    raise Blocked('restored bytes/mode/owner inventory mismatch')
                if hasattr(adapter, 'finish'):
                    adapter.finish()
                result.update(exit_code=1, status='ROLLED_BACK')
            except (OSError, Blocked, ValueError) as recovery_error:
                result.pop('rollback_backend', None)
                result.update(exit_code=2, status='ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED',
                              recovery_reason=str(recovery_error))
    finally:
        if locked:
            try:
                adapter.unlock()
            except (OSError, Blocked, ValueError) as error:
                result.pop('rollback_backend', None)
                result.update(exit_code=2 if changed else 1,
                              status='ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED' if changed else 'BLOCKED_BEFORE_STOP',
                              reason='session close unconfirmed: ' + str(error))
    return result


class SandboxAdapter:
    """Filesystem-only adapter. Unix ownership is not emulated on Windows."""
    offline = True

    def __init__(self, root, configs, scenario='success'):
        self.root = Path(root).resolve()
        self.configs, self.scenario, self.events = configs, scenario, []
        self.running, self.restoring = scenario not in ('cold', 'cold_success'), False
        self.lock_path = self.root / '.deployment-lock'
        self.lock_file = None

    def path(self, name):
        path = self.root / relative_path(name)
        current = path
        while current != self.root:
            if current.is_symlink():
                raise Blocked('symlink outside minimal write paths')
            current = current.parent
        if not path.resolve().is_relative_to(self.root):
            raise Blocked('outside sandbox')
        return path

    def lock(self):
        # Actual O_EXCL competition; live flock remains an unverified gate.
        self.lock_file = self.lock_path.open('xb')
        self.events.append('lock')

    def unlock(self):
        self.lock_file.close()
        self.lock_path.unlink()
        self.events.append('unlock')

    def contract(self):
        return {**CONTRACT, 'PIDFile': '/unknown.pid'} if self.scenario == 'drift' else CONTRACT

    def state(self):
        if self.scenario == 'abnormal' and not self.restoring:
            return {'active': 'failed', 'pid': 0, 'pids': [], 'argv': []}
        if not self.running:
            return {'active': 'inactive', 'pid': 0, 'pids': [], 'argv': []}
        return {'active': 'active', 'pid': 101,
                'pids': [101, 102] if self.scenario == 'double' else [101], 'argv': JVM}

    def preflight(self, release):
        if not self.configs or any(not name.startswith('backend/') for name in self.configs):
            raise Blocked('missing authoritative configuration inventory')
        for name in [JAR, 'frontend/dist/wealth-hub/index.html',
                     'frontend/dist/wealth-hub-mobile/index.html', *self.configs]:
            if not self.path(name).is_file():
                raise Blocked('missing original file')
        if set(release) != {JAR, 'frontend/dist/wealth-hub', 'frontend/dist/wealth-hub-mobile'}:
            raise Blocked('release paths are not fixed')
        for name, source in release.items():
            source = Path(source)
            if not source.exists() or source.is_symlink():
                raise Blocked('release missing or symlink')
            if name != JAR and not (source / 'index.html').is_file():
                raise Blocked('release frontend missing')
            for path in ([source] if source.is_file() else source.rglob('*')):
                if path.is_symlink():
                    raise Blocked('release symlink')

    def inventory(self):
        entries = {}
        for name in [JAR, 'frontend/dist', *self.configs]:
            base = self.path(name)
            for path in [base, *sorted(base.rglob('*'))] if base.is_dir() else [base]:
                self.path(path.relative_to(self.root).as_posix())
                info = path.stat()
                if not (path.is_file() or path.is_dir()):
                    raise Blocked('unsupported original object')
                entries[path.relative_to(self.root).as_posix()] = {
                    'kind': 'directory' if path.is_dir() else 'file',
                    'mode': stat.S_IMODE(info.st_mode), 'uid': info.st_uid, 'gid': info.st_gid,
                    'size': 0 if path.is_dir() else info.st_size,
                    'sha256': None if path.is_dir() else digest(path),
                }
        return entries

    def read(self, name):
        if self.scenario == 'backup_disconnect':
            raise ConnectionError('backup connection lost')
        with self.path(name).open('rb') as source:
            for chunk in iter(lambda: source.read(CHUNK), b''):
                yield b'X' * len(chunk) if self.scenario == 'corrupt_stream' else chunk

    def stop(self):
        self.events.append('systemd_stop')
        if self.scenario == 'disconnect' and 'switch' in self.events:
            raise ConnectionError('SSH connection lost; restoration unconfirmed')
        self.running = False

    def replace(self, release):
        self.events.append('switch')
        for name, source in release.items():
            target = self.path(name)
            if target.is_dir():
                shutil.rmtree(target)
                shutil.copytree(source, target)
            else:
                shutil.copyfile(source, target)

    def start(self):
        self.events.append('systemd_start')
        if self.scenario == 'start_failure' and not self.restoring:
            raise Blocked('new systemd start failed')
        self.running = True

    def restore(self, backup, inventory):
        self.events.append('restore_stream')
        self.restoring = True
        if self.scenario == 'restore_failure':
            raise ConnectionError('restore stream interrupted')
        frontend = self.path('frontend/dist')
        if frontend.exists():
            shutil.rmtree(frontend)
        for name, meta in inventory.items():
            target = self.path(name)
            target.parent.mkdir(parents=True, exist_ok=True)
            if meta['kind'] == 'directory':
                target.mkdir(exist_ok=True)
            else:
                if target.exists():
                    target.unlink()
                transfer(backup.read(name), target, meta)
        # Apply directory modes only after creating children (read-only originals).
        for name in sorted(inventory, key=lambda item: item.count('/'), reverse=True):
            meta = inventory[name]
            target = self.path(name)
            if os.name != 'nt':
                os.chown(target, meta['uid'], meta['gid'])
            target.chmod(meta['mode'])

    def backend_health(self):
        if self.restoring:
            return 200, b'{"status":"UP"}'
        if self.scenario in ('down', 'cold', 'restore_failure', 'disconnect'):
            return 200, b'{"status":"DOWN"}'
        if self.scenario == 'html':
            return 200, b'<html>SPA 200</html>'
        return 200, b'{"status":"UP"}'

    def upstream_status(self):
        return 200 if self.scenario == 'upstream_html' and not self.restoring else 401

    def pc_status(self):
        return 200
