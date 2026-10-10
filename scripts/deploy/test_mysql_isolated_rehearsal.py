"""Offline safety tests. Docker and SQL replies here are explicit test doubles."""
import contextlib
import io
import json
import subprocess
import unittest
from unittest.mock import patch

import mysql_isolated_rehearsal as m


def proc(out='', rc=0, err=''):
    return subprocess.CompletedProcess([], rc, out, err)


class FakeDocker(m.Docker):
    def __init__(self):
        super().__init__()
        self.exe = 'FAKE_DOCKER_ONLY'
        self.env = {}
        self.commands = []
        self.container = None
        self.existing = None
        self.pull_fail = False
        self.fail_start = False
        self.context_name = 'default'
        self.context_host = 'npipe:////./pipe/docker_engine'

    def call(self, args, **kwargs):
        self.commands.append((args, kwargs))
        if args == ['context', 'show']:
            return proc(self.context_name)
        if args[:2] == ['context', 'inspect']:
            return proc(json.dumps([{'Endpoints': {'docker': {'Host': self.context_host}}}]))
        if args[0] == 'info':
            return proc('linux')
        if args[0] == 'ps':
            return proc(self.name if self.existing == 'container' else '')
        if args[:2] == ['volume', 'ls']:
            return proc(self.name if self.existing == 'volume' else '')
        if args[0] == 'pull':
            return proc(rc=int(self.pull_fail))
        if args[:2] == ['image', 'inspect']:
            return proc('sha256:' + 'a' * 64)
        if args[0] == 'create':
            self.container = {
                'Id': 'b' * 64, 'Name': '/' + self.name,
                'Config': {'Labels': {'mydca.task': m.TASK, 'mydca.nonce': self.nonce}},
                'HostConfig': {'NetworkMode': 'none', 'Privileged': False, 'PidMode': '',
                               'PortBindings': {}, 'Binds': None},
                'Mounts': [{'Type': 'tmpfs'}], 'State': {'Running': False}}
            return proc(self.container['Id'])
        if args[0] == 'inspect':
            if self.container is None:
                return proc(rc=1)
            if '--format' in args:
                return proc(self.container['Id'] + ' ' + self.nonce)
            return proc(json.dumps([self.container]))
        if args[0] == 'start':
            if self.fail_start:
                return proc(rc=1)
            self.container['State']['Running'] = True
            return proc(self.owned)
        if args[:2] == ['rm', '-f']:
            self.container = None
            return proc()
        if args[0] == 'exec':
            return proc('MySQL Community Server - GPL')
        raise AssertionError('Unexpected mock command: ' + str(args))


class SafetyTests(unittest.TestCase):
    def test_default_and_help_never_execute(self):
        with patch.object(m.subprocess, 'run', side_effect=AssertionError('No process')), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(m.main([]), 0)
            with self.assertRaises(SystemExit) as e:
                m.main(['--help'])
            self.assertEqual(e.exception.code, 0)

    def test_no_database_or_file_cli_options(self):
        for option in ('--host', '--database', '--password', '--sql-file', '--image', '--dsn'):
            with self.subTest(option=option), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                m.main([option, '124.220.229.91'])

    def test_remote_and_unknown_endpoints_rejected(self):
        for context, host in [('default', 'tcp://localhost:2375'), ('default', 'ssh://host'),
                              ('remote', 'unix:///var/run/docker.sock'), ('default', 'tcp://124.220.229.91:3306'),
                              ('default', 'npipe:////server/pipe/docker_engine')]:
            with self.subTest(host=host), self.assertRaises(m.Blocked):
                m.local_endpoint(context, host, {})

    def test_explicit_local_endpoints(self):
        for context, host in [('default', 'unix:///var/run/docker.sock'),
                              ('default', 'npipe:////./pipe/docker_engine'),
                              ('desktop-linux', 'npipe:////./pipe/dockerDesktopLinuxEngine')]:
            m.local_endpoint(context, host, {})

    def test_env_rejected_before_docker_command(self):
        for key in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH', 'DOCKER_CONFIG'):
            d = FakeDocker()
            d.env[key] = 'redirect'
            with self.subTest(key=key), self.assertRaises(m.Blocked):
                d.gate()
            self.assertEqual(d.commands, [])

    def test_remote_context_stops_before_daemon(self):
        d = FakeDocker()
        d.context_host = 'ssh://remote'
        with self.assertRaises(m.Blocked):
            d.gate()
        self.assertEqual([a[0] for a, _ in d.commands], ['context', 'context'])

    def test_existing_container_and_volume(self):
        for kind in ('container', 'volume'):
            d = FakeDocker()
            d.existing = kind
            with self.subTest(kind=kind), self.assertRaises(m.Blocked):
                d.gate()
            self.assertFalse(d.validated)
            self.assertFalse(any(a[0] in ('create', 'start', 'rm', 'pull') for a, _ in d.commands))

    def test_no_gate_no_create_and_no_unlisted_image(self):
        d = FakeDocker()
        with self.assertRaises(m.Blocked):
            d.create(m.IMAGES['5.7'])
        d.gate()
        with self.assertRaises(m.Blocked):
            d.create('mariadb:latest')
        self.assertFalse(any(a[0] == 'create' for a, _ in d.commands))

    def test_image_failure_no_container(self):
        d = FakeDocker()
        d.gate()
        d.pull_fail = True
        with self.assertRaises(m.Blocked):
            d.create(m.IMAGES['5.7'])
        self.assertIsNone(d.owned)
        self.assertFalse(any(a[0] == 'create' for a, _ in d.commands))

    def test_container_options_and_secret_transport(self):
        d = FakeDocker()
        d.gate()
        d.create(m.IMAGES['5.7'])
        args, kwargs = next((a, k) for a, k in d.commands if a[0] == 'create')
        self.assertEqual(args[args.index('--network') + 1], 'none')
        self.assertIn('--skip-networking', args)
        self.assertIn('--tmpfs', args)
        self.assertNotIn(d.password, ' '.join(args))
        self.assertTrue(kwargs['secret_env'])
        for forbidden in ('--privileged', '--pid', '--publish', '-p', '-v', '--volume', '--mount'):
            self.assertNotIn(forbidden, args)
        d.sql('SELECT 0')
        args, kwargs = d.commands[-1]
        self.assertNotIn(d.password, ' '.join(args))
        self.assertIn(d.password, kwargs['stdin'])
        self.assertIn('--protocol=SOCKET', kwargs['stdin'])
        d.cleanup()
        self.assertIsNone(d.container)

    def test_subprocess_does_not_put_password_in_arguments(self):
        d = m.Docker()
        d.exe = 'mock'
        with patch.object(m.subprocess, 'run', return_value=proc()) as runner:
            d.call(['create', '-e', 'MYSQL_ROOT_PASSWORD'], secret_env=True)
            args, kwargs = runner.call_args
            self.assertNotIn(d.password, repr(args))
            self.assertEqual(kwargs['env']['MYSQL_ROOT_PASSWORD'], d.password)

    def test_ownership_and_mount_guards_prevent_sql_or_delete(self):
        for field in ('nonce', 'id', 'name', 'mount', 'network', 'privileged', 'ports', 'pid', 'bind'):
            d = FakeDocker()
            d.gate()
            d.create(m.IMAGES['5.7'])
            if field == 'nonce':
                d.container['Config']['Labels']['mydca.nonce'] = 'someone-else'
            elif field == 'id':
                d.container['Id'] = 'c' * 64
            elif field == 'name':
                d.container['Name'] = '/existing'
            elif field == 'mount':
                d.container['Mounts'] = [{'Type': 'volume'}]
            else:
                keys = {'network': 'NetworkMode', 'privileged': 'Privileged', 'ports': 'PortBindings', 'pid': 'PidMode', 'bind': 'Binds'}
                d.container['HostConfig'][keys[field]] = 'unsafe'
            before = len(d.commands)
            with self.subTest(field=field), self.assertRaises(m.Blocked):
                d.sql('SELECT 0')
            if field in ('nonce', 'id', 'name'):
                with self.assertRaises(m.Blocked):
                    d.cleanup()
                self.assertFalse(any(a[0] in ('exec', 'rm') for a, _ in d.commands[before:]))
            else:
                d.cleanup()
                self.assertIsNone(d.container)
                self.assertFalse(any(a[0] == 'exec' for a, _ in d.commands[before:]))

    def test_terminated_container_cannot_execute_sql(self):
        d = FakeDocker()
        d.gate()
        d.create(m.IMAGES['5.7'])
        d.container['State']['Running'] = False
        with self.assertRaisesRegex(m.Blocked, 'CONTAINER_TERMINATED'):
            d.sql('SELECT 0')
        d.cleanup()
        self.assertIsNone(d.container)

    def test_start_failure_cleanup_and_never_pass(self):
        d = FakeDocker()
        d.fail_start = True
        report = m.report_template()
        m.execute(report, m.sources(), lambda: d)
        self.assertIsNone(d.container)
        self.assertEqual(report['engines']['5.7']['status'], 'NOT_RUN')
        self.assertEqual(report['engines']['8.0']['status'], 'NOT_RUN')
        self.assertIn('ENGINE_NOT_TESTED', report['status'])
        self.assertFalse(report['production_ready'])

    def test_create_timeout_recovers_only_own_nonce(self):
        d = FakeDocker()
        d.gate()
        original = d.call
        def timeout_after_create(args, **kw):
            p = original(args, **kw)
            if args[0] == 'create':
                raise m.Blocked('DOCKER_COMMAND_FAILED_OR_TIMEOUT')
            return p
        with patch.object(d, 'call', side_effect=timeout_after_create), self.assertRaises(m.Blocked):
            d.create(m.IMAGES['5.7'])
        self.assertIsNotNone(d.owned)
        d.cleanup()
        self.assertIsNone(d.container)

    def test_interrupted_exercise_cleans_up(self):
        d = FakeDocker()
        d.wait_ready = lambda: '5.7.44'
        with patch.object(m, 'exercise', side_effect=KeyboardInterrupt), self.assertRaises(KeyboardInterrupt):
            m.execute(m.report_template(), m.sources(), lambda: d)
        self.assertIsNone(d.container)

    def test_cleanup_failure_blocks_result(self):
        d = FakeDocker()
        d.wait_ready = lambda: '5.7.44'
        with patch.object(m, 'exercise'), patch.object(d, 'cleanup', side_effect=m.Blocked('DOCKER_COMMAND_REJECTED')):
            report = m.report_template()
            m.execute(report, m.sources(), lambda: d)
        self.assertIn('ENGINE_NOT_TESTED', report['status'])
        self.assertEqual(report['engines']['5.7']['cleanup'], 'FAILED_MANUAL_REVIEW_REQUIRED')
        d.cleanup()

    def test_unconfirmed_create_cannot_claim_cleanup(self):
        d = FakeDocker()
        d.creation_uncertain = True
        with self.assertRaisesRegex(m.Blocked, 'CONTAINER_CREATION_OUTCOME_UNKNOWN'):
            d.cleanup()

    def test_subprocess_timeout_is_secret_free(self):
        d = m.Docker()
        d.exe = 'mock'
        with patch.object(m.subprocess, 'run', side_effect=subprocess.TimeoutExpired('secret', 1, stderr='private')):
            with self.assertRaises(m.Blocked) as e:
                d.call(['pull', m.IMAGES['5.7']])
        self.assertEqual(str(e.exception), 'DOCKER_COMMAND_FAILED_OR_TIMEOUT')

    def test_wrong_vendor_or_version_does_not_run_sql_scenarios(self):
        for version, vendor in [('5.7.44-MariaDB', 'MySQL Community Server - GPL'), ('5.7.44', 'MariaDB')]:
            d = FakeDocker()
            d.wait_ready = lambda: version
            d.sql = lambda *args, **kw: proc(vendor)
            with patch.object(m, 'exercise') as exercise:
                report = m.report_template()
                m.execute(report, m.sources(), lambda: d)
            exercise.assert_not_called()
            self.assertIn('ENGINE_NOT_TESTED', report['status'])
            self.assertIsNone(d.container)

    def test_missing_docker_records_not_run(self):
        with patch.object(m.shutil, 'which', return_value=None), patch.object(m.subprocess, 'run', side_effect=AssertionError('No process')):
            report = m.report_template()
            m.execute(report, m.sources())
        self.assertEqual(report['engines']['5.7']['blocker'], 'DOCKER_NOT_AVAILABLE')
        self.assertEqual(report['engines']['8.0']['status'], 'NOT_RUN')
        self.assertFalse(report['production_ready'])

    def test_source_allowlist_hashes_and_coverage(self):
        src = m.sources()
        self.assertEqual(len(m.TABLES), 9)
        self.assertEqual(len(m.INDEXES), 2)
        self.assertEqual(len(src), 11)
        self.assertEqual(sum(s.startswith('ALTER TABLE') for v in src.values() for s in v), 3)
        with patch.dict(m.PINS, {next(iter(m.PINS)): '0' * 64}), self.assertRaisesRegex(m.Blocked, 'SOURCE_HASH_DRIFT'):
            m.sources()
        with patch.dict(m.PINS, {'../outside.sql': '0' * 64}), self.assertRaises(m.Blocked):
            m.sources()
        # A newly added business path is refused, even if its hash is supplied.
        with patch.dict(m.PINS, {'backend/application.env': '0' * 64}), self.assertRaises(m.Blocked):
            m.sources()

    def test_output_rejects_existing_and_outside(self):
        for name in ('sql/new.json', 'backend/credentials.json', '../report.json', 'scripts/deploy/health_probe.py',
                     'scripts/deploy/mysql57_static_review_20261009.json'):
            with self.subTest(name=name), self.assertRaises(m.Blocked):
                m.output_path(name)

    def test_logs_redact_error_text(self):
        result = m.sql_result(proc(rc=1, err='ERROR 1062 (23000): password=secret raw-notification user-record'))
        self.assertEqual(result['category'], 'DUPLICATE_KEY')
        self.assertEqual(result['sqlstate'], '23000')
        for secret in ('secret', 'notification', 'record'):
            self.assertNotIn(secret, json.dumps(result))

    def test_failed_create_skips_payload_but_other_ddls_continue(self):
        class FakeSQL:
            schema = 'rehearsal_test'
            def __init__(self):
                self.statements = []
            def sql(self, statement, database=True):
                self.statements.append(statement)
                if statement.startswith('CREATE TABLE research_plan'):
                    return proc(rc=1, err='ERROR 1064 (42000): private error')
                return proc('synthetic-only')
        d = FakeSQL()
        record = {'events': []}
        m.exercise(d, m.sources(), record)
        self.assertFalse(any('INSERT INTO research_plan' in s for s in d.statements))
        self.assertTrue(any(s.startswith('UPDATE draft_ledger_entry') for s in d.statements))
        self.assertTrue(any('partial_state' == e['scenario'] for e in record['events']))
        self.assertTrue(any(e.get('status') == 'SKIPPED_CREATE_FAILED' for e in record['events']))
        self.assertEqual(sum(e['scenario'].startswith('original_create:') for e in record['events']), 9)


if __name__ == '__main__':
    unittest.main()
