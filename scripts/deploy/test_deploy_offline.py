"""Offline regression: deny all real network and process entry points."""
import socket
import subprocess
import shutil
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from health_probe import healthy
from test_systemd_local_rollback import TransactionTests, StreamTests, ContractTests


class ProbeTests(unittest.TestCase):
    def test_retired_bash_entry_point_cannot_touch_network_or_services(self):
        git = shutil.which('git')
        bash = Path(shutil.which('bash') or (Path(git).resolve().parent.parent / 'bin/bash.exe' if git else 'missing-bash'))
        if not bash.is_file():
            self.fail('Bash unavailable: retired-entry regression NOT_RUN')
        here = Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory(dir=here, prefix='bash-gate-') as directory:
            script = Path(directory) / 'gate.sh'
            script.write_text('''set -Eeuo pipefail
ssh() { echo NETWORK_FORBIDDEN; exit 99; }
scp() { echo NETWORK_FORBIDDEN; exit 99; }
curl() { echo NETWORK_FORBIDDEN; exit 99; }
systemctl() { echo SERVICE_FORBIDDEN; exit 99; }
source "$1"
"$2"
''', encoding='utf-8', newline='\n')
            for action, code, expected in [('deploy', 1, 'BLOCKED_LIVE_STREAM_ADAPTER_NOT_VERIFIED'),
                                           ('rollback', 2, 'NOT_RUN_LOCAL_STREAM_ADAPTER_REQUIRED')]:
                result = subprocess.run([str(bash), script.as_posix(),
                                         (here / 'remote_deploy.sh').as_posix(), action],
                                        capture_output=True, text=True, timeout=15)
                self.assertEqual(code, result.returncode, result.stderr)
                self.assertIn(expected, result.stderr)
                self.assertNotIn('FORBIDDEN', result.stdout)

    def test_strict_probe(self):
        self.assertTrue(healthy('200', b'{"status":"UP","components":{}}'))
        bodies = [b'<html>200 SPA</html>', b'{"status":"DOWN"}', b'{}', b'[]',
                  b'{"components":{"status":"UP"}}', b'{"status":', b'{"status":"up"}',
                  b'{"status":"DOWN","status":"UP"}', b'{"status":"UP","error":"failed"}',
                  b'{"status":"UP","invalid":NaN}', b'\xff', b'x' * 65537]
        for body in bodies:
            with self.subTest(body=body[:50]):
                self.assertFalse(healthy('200', body))
        for code in ['000', '201', '301', '401', '403', '500', '503']:
            self.assertFalse(healthy(code, b'{"status":"UP"}'))

    def test_isolation_guards_reject_real_process_and_network(self):
        with patch.object(socket, 'socket', side_effect=AssertionError('network forbidden')), \
             patch.object(subprocess, 'Popen', side_effect=AssertionError('process forbidden')):
            with self.assertRaises(AssertionError):
                socket.socket()
            for command in ['ssh', 'scp', 'systemctl', 'curl']:
                with self.assertRaises(AssertionError):
                    subprocess.Popen([command])


if __name__ == '__main__':
    unittest.main()
