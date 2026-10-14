import copy
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import systemd_local_rollback as engine

HERE = Path(__file__).resolve().parent


class ContractTests(unittest.TestCase):
    def test_authoritative_contract_and_every_drift_field(self):
        engine.contract_valid(engine.CONTRACT)
        for key in engine.CONTRACT:
            changed = copy.deepcopy(engine.CONTRACT)
            changed[key] = 'unknown'
            with self.subTest(key=key), self.assertRaises(engine.Blocked):
                engine.contract_valid(changed)
        changed = {**engine.CONTRACT, 'unknown': 'value'}
        with self.assertRaises(engine.Blocked):
            engine.contract_valid(changed)

    def test_pid_and_effective_jvm(self):
        good = {'active': 'active', 'pid': 100, 'pids': [100], 'argv': engine.JVM}
        self.assertTrue(engine.validate_state(good))
        for bad in [{'pid': 0}, {'pids': [100, 101]}, {'active': 'failed'},
                    {'argv': [engine.JAVA, '-Xmx1024M', '-jar', engine.JAR]}]:
            with self.subTest(bad=bad), self.assertRaises(engine.Blocked):
                engine.validate_state({**good, **bad})


class StreamTests(unittest.TestCase):
    def test_binary_bounded_stream_and_truncation(self):
        data = bytes(range(256)) * 1024
        meta = {'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        with tempfile.TemporaryDirectory(dir=HERE, prefix='stream-') as directory:
            target = Path(directory) / 'raw'
            engine.transfer((data[i:i+engine.CHUNK] for i in range(0, len(data), engine.CHUNK)), target, meta)
            self.assertEqual(data, target.read_bytes())
            for index, chunks in enumerate([[b'bad'], [data], [data[:engine.CHUNK]], ['text']]):
                with self.subTest(index=index), self.assertRaises(engine.Blocked):
                    engine.transfer(iter(chunks), Path(directory) / str(index), meta)

    def test_paths_refuse_traversal(self):
        for path in ['../backend', '/etc/passwd', 'C:/secret', 'a\\b', 'a/./b', 'a//b']:
            with self.subTest(path=path), self.assertRaises(engine.Blocked):
                engine.relative_path(path)


class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=HERE, prefix='systemd-offline-')
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.live, self.stage = self.base / 'server', self.base / 'release'
        self.configs = ['backend/fixture-config.json']
        self.original = {
            engine.JAR: b'old-jar\x00\xff' * 20000,
            'backend/fixture-config.json': b'{"synthetic":true}\r\n',
            'frontend/dist/wealth-hub/index.html': b'old-pc',
            'frontend/dist/wealth-hub/assets/all.js': b'old-asset',
            'frontend/dist/wealth-hub-mobile/index.html': b'old-h5',
            'frontend/dist/wealth-hub-mobile/assets/all.js': b'old-h5-asset',
        }
        for name, data in self.original.items():
            target = self.live / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        (self.live / 'frontend/dist/wealth-hub/empty').mkdir()
        self.release = {}
        for name, data in {
            engine.JAR: b'new-jar',
            'frontend/dist/wealth-hub/index.html': b'new-pc',
            'frontend/dist/wealth-hub/assets/new.js': b'new-asset',
            'frontend/dist/wealth-hub-mobile/index.html': b'new-h5',
        }.items():
            target = self.stage / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        for name in [engine.JAR, 'frontend/dist/wealth-hub', 'frontend/dist/wealth-hub-mobile']:
            self.release[name] = self.stage / name
        self.guards = [patch.object(socket, 'socket', side_effect=AssertionError('NETWORK_FORBIDDEN')),
                       patch.object(socket, 'create_connection', side_effect=AssertionError('NETWORK_FORBIDDEN')),
                       patch.object(subprocess, 'Popen', side_effect=AssertionError('PROCESS_FORBIDDEN')),
                       patch.object(os, 'system', side_effect=AssertionError('PROCESS_FORBIDDEN'))]
        for guard in self.guards:
            guard.start()
            self.addCleanup(guard.stop)

    def run_scenario(self, scenario, confirm='OFFLINE_AUTO_CONFIRM'):
        adapter = engine.SandboxAdapter(self.live, self.configs, scenario)
        before = adapter.inventory()
        result = engine.deploy(adapter, self.release, self.base / 'local-rescue', confirm)
        return result, adapter, before

    def assert_original(self, adapter, before):
        self.assertEqual(before, adapter.inventory())
        for name, data in self.original.items():
            self.assertEqual(data, (self.live / name).read_bytes())
        self.assertFalse((self.live / 'frontend/dist/wealth-hub/assets/new.js').exists())
        self.assertTrue((self.live / 'frontend/dist/wealth-hub/empty').is_dir())
        self.assertFalse((self.live / 'backups').exists())

    def test_success_full_pc_h5_and_local_only_backup(self):
        result, adapter, before = self.run_scenario('success')
        self.assertEqual('SUCCESS', result['status'])
        self.assertEqual(0, result['exit_code'])
        self.assertEqual(b'new-jar', (self.live / engine.JAR).read_bytes())
        self.assertEqual(b'new-h5', (self.live / 'frontend/dist/wealth-hub-mobile/index.html').read_bytes())
        self.assertEqual(self.original[self.configs[0]], (self.live / self.configs[0]).read_bytes())
        backup = Path(result['backup_path'])
        self.assertEqual(before, json.loads((backup / 'manifest.json').read_bytes()))
        self.assertEqual(result['backup_manifest_sha256'], engine.digest(backup / 'manifest.json'))
        for name, data in self.original.items():
            self.assertEqual(data, (backup / name).read_bytes())
        self.assertFalse((self.live / 'backups').exists())
        self.assertEqual(['lock', 'systemd_stop', 'switch', 'systemd_start', 'unlock'], adapter.events)

    def test_health_and_start_failure_stream_restore_old_bytes(self):
        for scenario in ['html', 'down', 'start_failure', 'upstream_html']:
            with self.subTest(scenario=scenario):
                result, adapter, before = self.run_scenario(scenario)
                self.assertEqual(1, result['exit_code'])
                self.assertEqual('ROLLED_BACK', result['status'])
                self.assertEqual('UP', result['rollback_backend'])
                self.assert_original(adapter, before)
                self.assertIn('restore_stream', adapter.events)

    def test_cold_start_success_and_failed_cold_restore_original_inactive(self):
        result, _, _ = self.run_scenario('cold_success')
        self.assertEqual('SUCCESS', result['status'])
        # Recreate original bytes for a second independent transaction.
        for name, data in self.original.items():
            (self.live / name).parent.mkdir(parents=True, exist_ok=True)
            (self.live / name).write_bytes(data)
        result, adapter, before = self.run_scenario('cold')
        self.assertEqual('ROLLED_BACK', result['status'])
        self.assertEqual('ORIGINAL_INACTIVE', result['rollback_backend'])
        self.assertFalse(adapter.running)
        self.assertEqual(before, adapter.inventory())

    def test_prestop_connection_corruption_drift_abnormal_and_double_block(self):
        for scenario in ['backup_disconnect', 'corrupt_stream', 'drift', 'abnormal', 'double']:
            with self.subTest(scenario=scenario):
                result, adapter, before = self.run_scenario(scenario)
                self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
                self.assertNotIn('systemd_stop', adapter.events)
                self.assert_original(adapter, before)

    def test_missing_config_original_and_confirmation_block(self):
        result, adapter, before = self.run_scenario('success', confirm=None)
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assert_original(adapter, before)
        self.configs = []
        result, adapter, _ = self.run_scenario('success')
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', adapter.events)
        self.configs = ['backend/fixture-config.json']
        (self.live / 'frontend/dist/wealth-hub-mobile/index.html').unlink()
        adapter = engine.SandboxAdapter(self.live, self.configs)
        result = engine.deploy(adapter, self.release, self.base / 'local-rescue', 'OFFLINE_AUTO_CONFIRM')
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', adapter.events)

    def test_confirmation_receipt_and_late_source_change(self):
        adapter = engine.SandboxAdapter(self.live, self.configs)
        def confirm(path, receipt):
            self.assertEqual(receipt, engine.digest(path / 'manifest.json'))
            (self.live / self.configs[0]).write_bytes(b'changed-during-confirmation')
            return receipt
        result = engine.deploy(adapter, self.release, self.base / 'local-rescue', confirm)
        self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
        self.assertNotIn('systemd_stop', adapter.events)
        self.assertIn('source changed before stop', result['reason'])

    def test_connection_loss_and_failed_restore_never_claim_rollback(self):
        for scenario in ['disconnect', 'restore_failure']:
            with self.subTest(scenario=scenario):
                result, _, _ = self.run_scenario(scenario)
                self.assertEqual(2, result['exit_code'])
                self.assertEqual('ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED', result['status'])
                self.assertNotIn('rollback_backend', result)

    def test_real_lock_competition_preserves_owner_lock(self):
        owner = engine.SandboxAdapter(self.live, self.configs)
        owner.lock()
        try:
            result, candidate, before = self.run_scenario('success')
            self.assertEqual('BLOCKED_BEFORE_STOP', result['status'])
            self.assertEqual([], candidate.events)
            self.assertTrue(owner.lock_path.exists())
            self.assert_original(candidate, before)
        finally:
            owner.unlock()

    def test_corrupt_missing_backup_or_manifest_prevents_restore(self):
        for damage in ['file', 'missing', 'manifest']:
            with self.subTest(damage=damage):
                adapter = engine.SandboxAdapter(self.live, self.configs, 'down')
                original_capture = engine.LocalBackup.capture
                def capture(backup, source, inventory):
                    receipt = original_capture(backup, source, inventory)
                    old_start = source.start
                    def start():
                        path = backup.path / ('manifest.json' if damage == 'manifest' else engine.JAR)
                        if damage == 'missing':
                            path.unlink()
                        else:
                            path.write_bytes(b'corrupt')
                        old_start()
                    source.start = start
                    return receipt
                with patch.object(engine.LocalBackup, 'capture', capture):
                    result = engine.deploy(adapter, self.release, self.base / 'local-rescue', 'OFFLINE_AUTO_CONFIRM')
                self.assertEqual(2, result['exit_code'])
                self.assertNotIn('restore_stream', adapter.events)
                self.assertNotIn('rollback_backend', result)


if __name__ == '__main__':
    unittest.main()
