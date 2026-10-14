"""Offline transport tests: injected Popen, sockets denied, synthetic bytes only."""
import base64
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import time
import unittest
from unittest.mock import patch

import ssh_stream_adapter as ssh
import systemd_local_rollback as core
import test_systemd_local_rollback as original_tests


class Pipe:
    def __init__(self, process):
        self.process = process

    def write(self, data):
        self.process.requests.append(json.loads(data))
        request = self.process.requests[-1]
        try:
            self.process.reply = json.dumps({'result': self.process.dispatch(request)}).encode() + b'\n'
        except (OSError, core.Blocked, ValueError):
            self.process.reply = b'{"error":"rejected"}\n'
        return len(data)

    def flush(self):
        pass

    def close(self):
        if self.process.adapter.lock_file is not None:
            self.process.adapter.unlock()
            self.process.adapter.lock_file = None
        self.process.closed = True


class FakeProcess:
    def __init__(self, adapter, stage):
        self.adapter, self.stage = adapter, stage
        self.requests, self.reply, self.closed = [], b'', False
        self.stdin, self.stdout = Pipe(self), self

    def poll(self):
        return 0 if self.closed else None

    def wait(self, timeout):
        return 0

    def kill(self):
        self.closed = True

    def readline(self, limit):
        return self.reply[:limit]

    def dispatch(self, r):
        op = r['op']
        if op == 'lock':
            self.adapter.lock()
            return True
        if op in ('prepare', 'finish'):
            return True
        if op in ('contract', 'state', 'inventory', 'stop', 'start'):
            return getattr(self.adapter, op)()
        if op == 'read':
            return base64.b64encode(b''.join(self.adapter.read(r['name']))[r['offset']:r['offset']+core.CHUNK]).decode()
        if op == 'health':
            code, body = self.adapter.backend_health()
            return code, base64.b64encode(body).decode()
        if op == 'upstream':
            return self.adapter.upstream_status()
        if op == 'pc':
            return self.adapter.pc_status()
        if op == 'begin':
            self.entries, self.restoring, self.data = r['inventory'], r['restoring'], {}
            return True
        if op == 'write':
            data = self.data.setdefault(r['name'], bytearray())
            if len(data) != r['offset']:
                raise ValueError('offset')
            data.extend(base64.b64decode(r['data']))
            if self.adapter.scenario == 'upload_disconnect' and not self.restoring:
                raise ConnectionError('disconnected')
            return True
        if op == 'seal':
            meta, data = self.entries[r['name']], self.data.get(r['name'], b'')
            if len(data) != meta['size'] or hashlib.sha256(data).hexdigest() != meta['sha256']:
                raise ValueError('sha')
            return True
        if op == 'commit':
            if self.stage.exists():
                shutil.rmtree(self.stage)
            self.stage.mkdir()
            for name, meta in self.entries.items():
                target = self.stage / name
                target.parent.mkdir(parents=True, exist_ok=True)
                if meta['kind'] == 'directory':
                    target.mkdir(exist_ok=True)
                else:
                    target.write_bytes(self.data.get(name, b''))
            if self.restoring:
                class Backup:
                    def read(_, name):
                        data = (self.stage / name).read_bytes()
                        return (data[i:i+core.CHUNK] for i in range(0, len(data), core.CHUNK))
                self.adapter.restore(Backup(), self.entries)
            else:
                self.adapter.replace({name: self.stage / name for name in ssh.RELEASE_ROOTS})
            return True
        if op == 'verify':
            actual = self.adapter.inventory()
            for name, meta in r['inventory'].items():
                for key in ('kind', 'size', 'sha256'):
                    if actual[name][key] != meta[key]:
                        raise ValueError('verify')
            return True
        raise ValueError('unknown')


class TransportTests(unittest.TestCase):
    def setUp(self):
        # Reuse the original transaction's synthetic fixtures and network denial.
        self.fixture = original_tests.TransactionTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.base, self.release = self.fixture.base, self.fixture.release
        self.backup = self.base / 'rescue'
        self.backup.mkdir()

    def execute(self, scenario='success', mutate=None):
        sandbox = core.SandboxAdapter(self.fixture.live, self.fixture.configs, scenario)
        originals = sandbox.inventory()
        evidence = {'target': ssh.TARGET, 'expires_at': time.time() + 3600, 'owner_approved': True,
                    'source_commit': 'a' * 40,
                    'checks': dict.fromkeys(['build', 'types', 'offline_recovery', 'schema', 'roles',
                                             'backup', 'release_approval'], 'PASS'),
                    'artifacts': ssh.release_inventory(self.release), 'originals': originals}
        process = FakeProcess(sandbox, self.base / 'upload')
        calls = []
        def popen(args, **kwargs):
            calls.append(args)
            return process
        with patch.object(ssh, 'restricted') as acl, patch.object(ssh.SSHAdapter, 'security_check', staticmethod(lambda p: None)):
            adapter = ssh.SSHAdapter(evidence, self.release, self.backup, popen=popen, start_timeout=0.01)
            if mutate:
                mutate(adapter)
            def confirm(path, receipt):
                self.assertEqual(receipt, core.digest(path / 'manifest.json'))
                return receipt
            result = core.deploy(adapter, self.release, self.backup, confirm)
            acl.assert_called()
        return result, sandbox, process, calls

    def test_success_single_session_trust_and_lock_lifetime(self):
        result, sandbox, process, calls = self.execute()
        self.assertEqual('SUCCESS', result['status'])
        self.assertEqual(1, len(calls))
        for option in ['BatchMode=yes', 'StrictHostKeyChecking=yes', 'ConnectTimeout=10']:
            self.assertIn(option, calls[0])
        self.assertEqual('lock', process.requests[0]['op'])
        self.assertEqual('unlock', sandbox.events[-1])
        self.assertTrue(process.closed)
        self.assertIn('verify', [r['op'] for r in process.requests])

    def test_rollback_health_html_upload_and_start_failure(self):
        for scenario in ['html', 'down', 'upload_disconnect', 'start_failure', 'upstream_html']:
            with self.subTest(scenario=scenario):
                result, sandbox, _, _ = self.execute(scenario)
                self.assertEqual('ROLLED_BACK', result['status'])
                self.assertEqual('UP', result['rollback_backend'])
                for name, data in self.fixture.original.items():
                    self.assertEqual(data, sandbox.path(name).read_bytes())

    def test_unrecoverable_connection(self):
        result, _, _, _ = self.execute('disconnect')
        self.assertEqual('ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED', result['status'])

    def test_drift_double_abnormal_and_missing_readiness(self):
        for scenario in ['drift', 'double', 'abnormal', 'corrupt_stream', 'backup_disconnect']:
            result, sandbox, _, _ = self.execute(scenario)
            self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
            self.assertNotIn('systemd_stop', sandbox.events)
        result, sandbox, _, _ = self.execute(mutate=lambda a: a.evidence['checks'].update(schema='UNKNOWN'))
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', sandbox.events)
        result, sandbox, _, _ = self.execute(mutate=lambda a: a.evidence['originals'][core.JAR].update(sha256='0' * 64))
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', sandbox.events)

    def test_acl_gate_is_before_popen(self):
        with patch.object(ssh, 'readiness', return_value={}), patch.object(ssh, 'restricted', side_effect=core.Blocked('ACL')):
            with self.assertRaises(core.Blocked):
                ssh.SSHAdapter({}, {}, self.backup, popen=lambda *a, **k: self.fail('network'))

    def test_timeout_and_truncated_protocol(self):
        def truncate(adapter):
            original = adapter.lock
            def lock():
                original()
                adapter.process.readline = lambda limit: b''
            adapter.lock = lock
        result, _, _, _ = self.execute(mutate=truncate)
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        def timeout(adapter):
            original = adapter.lock
            def lock():
                original()
                adapter.timeout = 0.01
                def slow(limit):
                    time.sleep(0.05)
                    return b'{"result":null}\n'
                adapter.process.readline = slow
            adapter.lock = lock
        result, sandbox, _, _ = self.execute(mutate=timeout)
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', sandbox.events)

    def test_transport_lock_competition_and_cold_start(self):
        owner = core.SandboxAdapter(self.fixture.live, self.fixture.configs)
        owner.lock()
        try:
            result, sandbox, _, _ = self.execute()
            self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
            self.assertTrue(owner.lock_path.exists())
            self.assertNotIn('systemd_stop', sandbox.events)
        finally:
            owner.unlock()
        result, _, _, _ = self.execute('cold_success')
        self.assertEqual('SUCCESS', result['status'])

    def test_unknown_paths_and_symlinks(self):
        bad = dict(self.release)
        bad['../outside'] = bad.pop(core.JAR)
        with self.assertRaises(core.Blocked):
            ssh.release_inventory(bad)
        with patch.object(Path, 'is_symlink', return_value=True):
            with self.assertRaises(core.Blocked):
                ssh.release_inventory(self.release)

    def test_source_sha_and_disconnected_restore(self):
        def bad_source(adapter):
            adapter.evidence['source_commit'] = 'unbound'
        result, sandbox, _, _ = self.execute(mutate=bad_source)
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', sandbox.events)
        sandbox = core.SandboxAdapter(self.fixture.live, self.fixture.configs)
        evidence = {'target': ssh.TARGET, 'expires_at': time.time() + 60, 'owner_approved': True,
                    'source_commit': 'b' * 40,
                    'checks': dict.fromkeys(['build', 'types', 'offline_recovery', 'schema', 'roles', 'backup', 'release_approval'], 'PASS'),
                    'artifacts': ssh.release_inventory(self.release), 'originals': sandbox.inventory()}
        processes = []
        def popen(args, **kwargs):
            p = FakeProcess(sandbox, self.base / 'upload')
            original = p.dispatch
            if not processes:
                def interrupted(request):
                    if request['op'] == 'write':
                        p.stdin.close()
                        raise ConnectionError('synthetic disconnected SSH pipe')
                    return original(request)
                p.dispatch = interrupted
                original_read = p.readline
                p.readline = lambda limit: b'' if p.closed else original_read(limit)
            processes.append(p)
            return p
        with patch.object(ssh, 'restricted'), patch.object(ssh.SSHAdapter, 'security_check', staticmethod(lambda p: None)):
            adapter = ssh.SSHAdapter(evidence, self.release, self.backup, popen=popen)
            result = core.deploy(adapter, self.release, self.backup, lambda p, receipt: receipt)
        self.assertEqual('ROLLED_BACK', result['status'])
        self.assertEqual('UP', result['rollback_backend'])
        self.assertEqual(2, len(processes))
        self.assertTrue(processes[1].requests[0]['recovering'])
        self.assertEqual(processes[0].requests[0]['token'], processes[1].requests[0]['token'])
        self.assertEqual(evidence['originals'], sandbox.inventory())

    def test_remote_transaction_identity_and_proof_reject_foreign_recovery(self):
        import types
        import os
        remote = types.ModuleType('offline_journal')
        source = Path(ssh.__file__).with_name('ssh_stream_remote.py')
        with patch.dict('sys.modules', {'fcntl': types.SimpleNamespace()}):
            exec(compile(source.read_bytes(), str(source), 'exec'), remote.__dict__)
        remote.JOURNAL = self.base / 'transaction.json'
        proof = {'token': 'a' * 32, 'source_commit': 'b' * 40,
                 'originals_sha': remote.manifest_sha({'old': 'bytes'}),
                 'artifacts_sha': remote.manifest_sha({'new': 'bytes'})}
        remote.JOURNAL.write_text(json.dumps(proof))
        def opened(*args, **kwargs):
            return real_open(self.base / 'lock', os.O_CREAT | os.O_RDWR, 0o600)
        real_open = os.open
        with patch.object(remote.os, 'geteuid', return_value=0, create=True), patch.object(remote.os, 'O_NOFOLLOW', 0, create=True), patch.object(remote.os, 'open', side_effect=opened), patch.object(remote.fcntl, 'flock', create=True), patch.object(remote.fcntl, 'LOCK_EX', 2, create=True), patch.object(remote.fcntl, 'LOCK_NB', 4, create=True), patch.object(remote, 'journal_read', return_value=proof):
            for request in [{'op': 'lock', 'token': 'a' * 32},
                            {'op': 'lock', 'token': 'c' * 32, 'recovering': True}]:
                worker = remote.Worker()
                try:
                    with self.assertRaises(ValueError):
                        worker.call(request)
                finally:
                    worker.lock.close()
            worker = remote.Worker()
            try:
                self.assertTrue(worker.call({'op': 'lock', 'token': 'a' * 32, 'recovering': True}))
                request = {'op': 'prepare', 'token': 'a' * 32, 'source_commit': 'b' * 40,
                           'originals': {'old': 'bytes'}, 'artifacts': {'new': 'bytes'}}
                self.assertTrue(worker.call(request))
                with self.assertRaises(ValueError):
                    worker.call({**request, 'source_commit': 'd' * 40})
                with self.assertRaises(ValueError):
                    worker.call({'op': 'begin', 'inventory': {}, 'restoring': True})
                self.assertTrue(remote.JOURNAL.exists())
                self.assertTrue(worker.call({'op': 'finish', 'token': 'a' * 32}))
                self.assertFalse(remote.JOURNAL.exists())
            finally:
                worker.lock.close()

    def test_remote_worker_actual_file_switch_restore_and_allowlist(self):
        import types
        worker_module = types.ModuleType('offline_remote')
        worker_path = Path(ssh.__file__).with_name('ssh_stream_remote.py')
        with patch.dict('sys.modules', {'fcntl': types.SimpleNamespace()}):
            exec(compile(worker_path.read_bytes(), str(worker_path), 'exec'), worker_module.__dict__)
        remote = worker_module
        import os
        local_lock = self.base / 'remote-lock-fixture'
        fd = os.open(local_lock, os.O_CREAT | os.O_RDWR, 0o600)
        with patch.object(remote.os, 'geteuid', return_value=0, create=True), patch.object(remote.os, 'O_NOFOLLOW', 0, create=True), patch.object(remote.os, 'open', return_value=fd) as opened, patch.object(remote.fcntl, 'flock', create=True) as flock, patch.object(remote.fcntl, 'LOCK_EX', 2, create=True), patch.object(remote.fcntl, 'LOCK_NB', 4, create=True):
            lock_worker = remote.Worker()
            with patch.object(remote, 'journal_read', return_value=None):
                self.assertTrue(lock_worker.call({'op': 'lock', 'token': 'a' * 32}))
            flock.assert_called_once_with(fd, 6)
            self.assertIn('mydca-systemd-release.lock', str(opened.call_args.args[0]))
            with self.assertRaises(ValueError):
                lock_worker.call({'op': 'lock'})
            lock_worker.lock.close()
        with patch.object(remote.os, 'geteuid', return_value=1000, create=True):
            with self.assertRaises(ValueError):
                remote.Worker().call({'op': 'lock'})
        remote.ROOT = self.fixture.live
        remote.CONFIGS = {name: str(self.fixture.live / 'config' / name.split('/')[1]) for name in ssh.CONFIGS}
        for name in remote.CONFIGS:
            path = Path(remote.CONFIGS[name])
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(b'synthetic configuration')
        # The old fixture config is outside the fixed live inventory.
        (self.fixture.live / self.fixture.configs[0]).unlink()
        worker = remote.Worker()
        worker.lock = object()
        original = remote.inventory()
        old_bytes = {n: remote.allowed(n).read_bytes() for n, m in original.items() if m['kind'] == 'file'}
        def upload(entries, contents, restoring):
            worker.transaction = {'originals_sha': remote.manifest_sha(entries),
                                  'artifacts_sha': remote.manifest_sha(entries)}
            worker.call({'op': 'begin', 'inventory': entries, 'restoring': restoring,
                         'originals': None if restoring else remote.inventory()})
            for name, meta in entries.items():
                if meta['kind'] == 'file':
                    data = contents[name]
                    for offset in range(0, len(data), core.CHUNK):
                        worker.call({'op': 'write', 'name': name, 'offset': offset,
                                     'data': base64.b64encode(data[offset:offset+core.CHUNK]).decode()})
                    worker.call({'op': 'seal', 'name': name})
            worker.call({'op': 'commit'})
        with patch.object(remote, 'state', return_value={'pids': []}), patch.object(remote, 'properties', return_value={'ActiveState': 'inactive'}), patch.object(remote, 'command', return_value=''), patch.object(remote.os, 'chown', create=True):
            entries = ssh.release_inventory(self.release)
            contents = {}
            for name, meta in entries.items():
                if meta['kind'] == 'file':
                    root = next(r for r in ssh.RELEASE_ROOTS if name == r or name.startswith(r + '/'))
                    path = self.release[root] if name == root else self.release[root] / name[len(root)+1:]
                    contents[name] = path.read_bytes()
            upload(entries, contents, False)
            self.assertTrue(worker.call({'op': 'verify', 'inventory': entries}))
            self.assertFalse((self.fixture.live / 'frontend/dist/wealth-hub/assets/all.js').exists())
            upload(original, old_bytes, True)
            self.assertEqual(original, remote.inventory())
            for name, data in old_bytes.items():
                self.assertEqual(data, remote.allowed(name).read_bytes())
            # 真正文件替换中途失败后，仍从本地合成原件恢复全部字节与元数据。
            real_replace = remote.os.replace
            replaced = []
            def partial_replace(source, destination):
                if replaced:
                    raise ConnectionError('synthetic partial atomic switch interruption')
                replaced.append(str(destination))
                return real_replace(source, destination)
            with patch.object(remote.os, 'replace', side_effect=partial_replace):
                with self.assertRaises(ConnectionError):
                    upload(entries, contents, False)
            self.assertEqual(1, len(replaced))
            upload(original, old_bytes, True)
            self.assertEqual(original, remote.inventory())
            for name in ['../outside', 'backend/db_config.json', 'frontend/dist/unknown/index.html', '/etc/passwd']:
                with self.subTest(name=name), self.assertRaises(ValueError):
                    remote.allowed(name)
            worker.transaction = {'artifacts_sha': remote.manifest_sha(entries)}
            worker.call({'op': 'begin', 'inventory': entries, 'restoring': False, 'originals': remote.inventory()})
            with self.assertRaises(ValueError):
                worker.call({'op': 'commit'})
            for name, data in contents.items():
                for offset in range(0, len(data), core.CHUNK):
                    worker.call({'op': 'write', 'name': name, 'offset': offset,
                                 'data': base64.b64encode(data[offset:offset+core.CHUNK]).decode()})
                worker.call({'op': 'seal', 'name': name})
            remote.allowed('config/nginx').write_bytes(b'changed during upload')
            with self.assertRaises(ValueError):
                worker.call({'op': 'commit'})
            remote.allowed('config/nginx').write_bytes(old_bytes['config/nginx'])
            shutil.rmtree(worker.stage)
        with patch.object(remote, 'http', return_value=(200, base64.b64encode(b'{"status":"UP"}').decode())) as health, patch.object(remote, 'nginx', return_value=401) as upstream:
            self.assertEqual(200, worker.call({'op': 'health'})[0])
            health.assert_called_once_with('http://127.0.0.1:8766/actuator/health')
            self.assertEqual(401, worker.call({'op': 'upstream'}))
            upstream.assert_called_once_with('/api/v2/mobile/overview')


class ACLTests(unittest.TestCase):
    def test_actual_platform_acl_refuses_public_directory_accepts_private(self):
        import os
        import subprocess
        import tempfile
        with tempfile.TemporaryDirectory(dir=Path(ssh.__file__).parent, prefix='acl-fixture-') as directory:
            path = Path(directory)
            if os.name == 'nt':
                script = ("& {param($p,$public) $ErrorActionPreference='Stop'; $sid=[System.Security.Principal.WindowsIdentity]::GetCurrent().User; "
                          "$acl=New-Object System.Security.AccessControl.DirectorySecurity; "
                          "$acl.SetOwner($sid);$acl.SetAccessRuleProtection($true,$false); "
                          "$rule=New-Object System.Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'); "
                          "$acl.AddAccessRule($rule);if($public -eq 'yes'){"
                          "$everyone=New-Object System.Security.Principal.SecurityIdentifier('S-1-1-0'); "
                          "$acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($everyone,'Read','Allow')))}; "
                          "[System.IO.Directory]::SetAccessControl($p,$acl) }")
                for public in ['yes', 'no']:
                    subprocess.run(['powershell', '-NoProfile', '-NonInteractive', '-Command', script,
                                    str(path), public], check=True, timeout=15)
                    if public == 'yes':
                        with self.assertRaises(core.Blocked):
                            ssh.restricted(path)
                    else:
                        ssh.restricted(path)
            else:
                path.chmod(0o755)
                with self.assertRaises(core.Blocked):
                    ssh.restricted(path)
                path.chmod(0o700)
                ssh.restricted(path)


if __name__ == '__main__':
    unittest.main()
