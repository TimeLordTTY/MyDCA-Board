"""Disposable Oracle MySQL comparison. No execution unless --run-isolated.

Only pinned repository SQL and synthetic fixtures enter newly created containers.
There is deliberately no host, database, credential, SQL-file or image CLI option.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
TASK = "task-mydca-mysql57-80-isolated-engine-rehearsal-20261010"
IMAGES = {"5.7": "docker.io/library/mysql:5.7.44", "8.0": "docker.io/library/mysql:8.0.40"}
PINS = {
    'sql/updatesql/20260928/01_create_draft_lifecycle_event.sql': 'cdf31ccfce929a64bb83013da5e6be9bfb8ef87040bc826cf7339a8f8a64c9e1',
    'backend/migrations/20261001_research_plan.sql': '3161d34f1a260ffbf0405cf887358c3a89fd6ddbe92c81b29f92b33971401afa',
    'backend/migrations/20261002_risk_watch.sql': 'df52f72c38674cb62ad40807ccc1a2eab9c2be45f5be124f3abc9649a54a8b77',
    'backend/migrations/20261002_risk_alert_history.sql': '88a558d9d3e43592fa87b575d594e0d515d01d31fae0dd720eda1c1d51840285',
    'backend/migrations/20261002_allocation_policy.sql': '45ddf6520e4c0286f14d1e6a13ca36f6628e3ac3fdf32d2ab18f075b431a62f2',
    'backend/migrations/20261002_goal_tracking.sql': 'da14864f443a8fd3b576a60b3bac3f4546d485c73925122a6affffc9a66e4213',
    'backend/migrations/20261002_monthly_budget.sql': '6c193b68716a22b3d4d9831132f2ad226f22801319beae1d59d5d98e0ce705f1',
    'sql/updatesql/20260929/01_settlement_audit_link.sql': 'c88adf2f2033d5c0ef42e788058aea9ae6c2abc07cabdbb15cb74c7721ce8501',
    'sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql': '20f4230fd221e2a05114c3e18e1619a311ed7ea83af2c2a08fdc638db784a280',
    'sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql': '5fd945ce0c3baad8b5df07d4c51b76548b64e1c36100e3a65804f45e6f8db47d',
    'sql/initsql/20260610_draft_ledger_entry.sql': '46accef2acaba3a41df5a2884dd5d4688cc1f9397ed70db9ed9b7d7b9fcdbdb5',
}
ALLOWED_SOURCES = frozenset(PINS)
TABLES = ('draft_lifecycle_event', 'research_plan', 'risk_watch_rule',
          'risk_watch_snapshot', 'risk_watch_event', 'risk_watch_mute',
          'allocation_policy', 'goal_tracking', 'monthly_budget')
NORMALIZE = 'sql/updatesql/20260927/02_normalize_blank_draft_ledger_source_ref.sql'
DRAFT = 'sql/initsql/20260610_draft_ledger_entry.sql'
INDEXES = ('uk_draft_ledger_user_source', 'uk_draft_ledger_family_source')


class Blocked(Exception):
    """Only fixed, secret-free categories may be published."""


def sources(root=ROOT):
    result = {}
    for name, digest in PINS.items():
        if name not in ALLOWED_SOURCES:
            raise Blocked('SOURCE_NOT_WHITELISTED')
        path = (root / name).resolve()
        if not path.is_relative_to(root.resolve()) or path.is_symlink():
            raise Blocked('SOURCE_PATH_REJECTED')
        try:
            data = path.read_bytes()
        except OSError:
            raise Blocked('SOURCE_UNAVAILABLE') from None
        if hashlib.sha256(data).hexdigest() != digest:
            raise Blocked('SOURCE_HASH_DRIFT')
        # These pinned files contain no semicolons inside SQL literals.
        clean = re.sub(r'^\s*--[^\n]*', '', data.decode('utf-8-sig'), flags=re.M)
        result[name] = [s.strip() for s in clean.split(';') if s.strip()]
    actual = [re.match(r'CREATE TABLE (\w+)', s).group(1)
              for name, statements in result.items() if name != DRAFT
              for s in statements if s.startswith('CREATE TABLE ')]
    if tuple(actual) != TABLES:
        raise Blocked('COVERAGE_DRIFT')
    return result


def local_endpoint(context, endpoint, env):
    if any(env.get(k) for k in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY',
                              'DOCKER_CERT_PATH', 'DOCKER_CONFIG')):
        raise Blocked('DOCKER_ENV_REDIRECT_REJECTED')
    allowed = {
        'default': ('unix:///var/run/docker.sock', 'npipe:////./pipe/docker_engine'),
        'desktop-linux': ('npipe:////./pipe/dockerDesktopLinuxEngine',),
    }
    if endpoint not in allowed.get(context, ()):
        raise Blocked('REMOTE_OR_UNKNOWN_CONTEXT_REJECTED')


def sql_result(proc):
    match = re.search(r'ERROR\s+(\d+)\s+\(([A-Z0-9]{5})\)', proc.stderr)
    codes = {1064: 'PARSE_ERROR', 3819: 'CHECK_REJECTED', 3140: 'JSON_REJECTED',
             1062: 'DUPLICATE_KEY', 1061: 'DUPLICATE_INDEX', 1060: 'DUPLICATE_COLUMN',
             1050: 'TABLE_EXISTS', 1146: 'TABLE_MISSING', 1048: 'NULL_REJECTED'}
    code = int(match[1]) if match else None
    return {'returncode': proc.returncode, 'sqlstate': match[2] if match else None,
            'error_code': code, 'category': 'OK' if proc.returncode == 0 else codes.get(code, 'SQL_ERROR')}


class Docker:
    def __init__(self):
        self.exe = shutil.which('docker')
        self.env = dict(os.environ)
        self.context = None
        self.endpoint = None
        self.validated = False
        self.owned = None
        self.creation_uncertain = False
        self.name = TASK + '-' + uuid.uuid4().hex[:12]
        self.nonce = uuid.uuid4().hex
        self.password = secrets.token_hex(32)
        self.schema = 'rehearsal_' + uuid.uuid4().hex

    def call(self, args, stdin=None, timeout=60, secret_env=False):
        if not self.exe:
            raise Blocked('DOCKER_NOT_AVAILABLE')
        prefix = [self.exe]
        if self.context:
            prefix += ['--context', self.context]
        env = dict(self.env)
        if secret_env:
            env['MYSQL_ROOT_PASSWORD'] = self.password
        try:
            return subprocess.run(prefix + args, input=stdin, text=True, encoding='utf-8',
                                  errors='replace', capture_output=True, timeout=timeout, env=env)
        except (OSError, subprocess.TimeoutExpired):
            raise Blocked('DOCKER_COMMAND_FAILED_OR_TIMEOUT') from None

    def require(self, args, **kwargs):
        p = self.call(args, **kwargs)
        if p.returncode:
            raise Blocked('DOCKER_COMMAND_REJECTED')
        return p.stdout.strip()

    def gate(self):
        # Validate environment before even asking Docker for its active context.
        local_endpoint('default', 'unix:///var/run/docker.sock', self.env)
        context = self.require(['context', 'show'])
        try:
            metadata = json.loads(self.require(['context', 'inspect', context]))[0]
            endpoint = metadata['Endpoints']['docker']['Host']
        except (ValueError, KeyError, IndexError, TypeError):
            raise Blocked('CONTEXT_METADATA_INVALID') from None
        local_endpoint(context, endpoint, self.env)
        self.context, self.endpoint = context, endpoint
        if self.require(['info', '--format', '{{.OSType}}']) != 'linux':
            raise Blocked('LINUX_CONTAINER_ENGINE_REQUIRED')
        # Never adopt an existing instance or reuse even a same-name volume.
        names = self.require(['ps', '-a', '--format', '{{.Names}}']).splitlines()
        volumes = self.require(['volume', 'ls', '--format', '{{.Name}}']).splitlines()
        if self.name in names or self.name in volumes:
            raise Blocked('EXISTING_CONTAINER_OR_VOLUME_REJECTED')
        self.validated = True

    def inspect_owned(self, require_isolation=True):
        if not self.owned:
            raise Blocked('NO_OWNED_CONTAINER')
        try:
            obj = json.loads(self.require(['inspect', self.owned]))[0]
            labels = obj['Config']['Labels']
            safe = (obj['Id'] == self.owned and obj['Name'] == '/' + self.name
                    and labels.get('mydca.task') == TASK
                    and labels.get('mydca.nonce') == self.nonce)
            if require_isolation:
                safe = (safe and obj['HostConfig']['NetworkMode'] == 'none'
                    and not obj['HostConfig']['Privileged']
                    and not obj['HostConfig'].get('PidMode')
                    and not obj['HostConfig'].get('PortBindings')
                    and not obj['HostConfig'].get('Binds')
                    and all(m['Type'] == 'tmpfs' for m in obj['Mounts']))
        except (ValueError, KeyError, IndexError, TypeError):
            safe = False
        if not safe:
            raise Blocked('CONTAINER_OWNERSHIP_OR_ISOLATION_REJECTED')
        return obj

    def create(self, image):
        if not self.validated:
            raise Blocked('LOCAL_GATE_REQUIRED')
        if image not in IMAGES.values():
            raise Blocked('IMAGE_NOT_WHITELISTED')
        # Pull only an exact public tag, after local endpoint validation.
        if self.call(['pull', image], timeout=300).returncode:
            raise Blocked('PINNED_IMAGE_UNAVAILABLE')
        image_id = self.require(['image', 'inspect', '--format', '{{.Id}}', image])
        if not re.fullmatch(r'sha256:[a-f0-9]{64}', image_id):
            raise Blocked('IMAGE_ID_INVALID')
        args = ['create', '--name', self.name, '--label', 'mydca.task=' + TASK,
                '--label', 'mydca.nonce=' + self.nonce, '--network', 'none',
                '--pull', 'never', '--no-healthcheck', '--memory', '1g', '--cpus', '2',
                '--tmpfs', '/var/lib/mysql:rw,nosuid,size=536870912',
                '-e', 'MYSQL_ROOT_PASSWORD', image_id, '--skip-networking']
        try:
            self.creation_uncertain = True
            created = self.require(args, secret_env=True)
            if not re.fullmatch(r'[a-f0-9]{64}', created):
                raise Blocked('CONTAINER_ID_INVALID')
            self.owned = created
            self.creation_uncertain = False
        except BaseException:
            # A timeout can occur after create succeeded. Recover only our nonce.
            p = self.call(['inspect', '--format', '{{.Id}} {{index .Config.Labels "mydca.nonce"}}', self.name])
            parts = p.stdout.strip().split()
            if p.returncode == 0 and len(parts) == 2 and parts[1] == self.nonce and re.fullmatch(r'[a-f0-9]{64}', parts[0]):
                self.owned = parts[0]
                self.creation_uncertain = False
            raise
        self.inspect_owned()
        self.require(['start', self.owned])
        return image_id

    def sql(self, statement, database=True):
        obj = self.inspect_owned()
        if not obj['State']['Running']:
            raise Blocked('CONTAINER_TERMINATED')
        # Credential goes over stdin, never argv, SQL result, report or Git.
        command = "export MYSQL_PWD='" + self.password + "'\nexec mysql --protocol=SOCKET --socket=/var/run/mysqld/mysqld.sock -uroot --batch --raw --skip-column-names"
        if database:
            command += ' --database=' + self.schema
        # The fixed SQL is supplied via a quoted here document, not shell expansion.
        command += " <<'MYDCA_SYNTHETIC_SQL'\n" + statement + ";\nMYDCA_SYNTHETIC_SQL\n"
        return self.call(['exec', '-i', self.owned, 'sh'], stdin=command)

    def wait_ready(self):
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            if not self.inspect_owned()['State']['Running']:
                raise Blocked('CONTAINER_TERMINATED')
            # The official entrypoint temporarily starts another mysqld during init.
            # Wait for PID 1 to become the final server before touching our schema.
            if self.call(['exec', self.owned, 'cat', '/proc/1/comm']).stdout.strip() != 'mysqld':
                self.inspect_owned()
                time.sleep(1)
                continue
            p = self.sql('SELECT VERSION()', database=False)
            if p.returncode == 0:
                return p.stdout.strip()
            time.sleep(1)
        raise Blocked('ENGINE_START_TIMEOUT')

    def cleanup(self):
        if self.owned:
            # Removal requires ownership; an isolation failure must still be cleaned.
            self.inspect_owned(require_isolation=False)
            self.require(['rm', '-f', self.owned])
            self.owned = None
        if self.creation_uncertain:
            raise Blocked('CONTAINER_CREATION_OUTCOME_UNKNOWN')


def exercise(docker, src, record):
    events = record['events']

    def run(name, sql, expected=None, read=False, database=True, expected_summary=None):
        p = docker.sql(sql, database=database)
        e = {'scenario': name, **sql_result(p)}
        if expected is not None:
            e['expectation_met'] = e['category'] == expected
        if read and p.returncode == 0:
            # Only synthetic SELECT/SHOW results; never publish raw stderr.
            e['synthetic_summary'] = p.stdout.strip()[:12000]
        if expected_summary is not None:
            e['summary_expectation_met'] = p.returncode == 0 and p.stdout.strip() == expected_summary
        events.append(e)
        return e

    schema = docker.schema
    e = run('fresh_synthetic_schema', f'CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'OK', database=False)
    if e['returncode']:
        raise Blocked('FRESH_SCHEMA_NOT_CREATED')
    run('engine_settings', 'SELECT @@version, @@version_comment, @@sql_mode, @@time_zone, @@character_set_database, @@collation_database, @@default_storage_engine', read=True)
    successful = []
    for name, statements in src.items():
        if name == DRAFT or name == NORMALIZE:
            continue
        for s in statements:
            if s.startswith('CREATE TABLE '):
                table = re.match(r'CREATE TABLE (\w+)', s)[1]
                e = run('original_create:' + table, s)
                if e['returncode'] == 0:
                    successful.append(table)
                    run('show_create:' + table, 'SHOW CREATE TABLE ' + table, read=True)
                    run('rerun_create:' + table, s, 'TABLE_EXISTS')
                else:
                    events.append({'scenario': 'payload_probes:' + table, 'status': 'SKIPPED_CREATE_FAILED'})
    for table in successful:
        if table in ('draft_lifecycle_event', 'risk_watch_mute'):
            continue
        key = 'fingerprint' if table == 'risk_watch_event' else 'id'
        extra = ',rule_id' if table in ('risk_watch_snapshot', 'risk_watch_event') else ''
        extra_value = ",'synthetic-rule'" if extra else ''
        for suffix, payload, expected in [('valid', "'{\"amount\":0}'", 'OK'),
                                          ('invalid', "'invalid-json'", 'CHECK_REJECTED'),
                                          ('empty', "''", 'CHECK_REJECTED'),
                                          ('zero', "'0'", 'OK'), ('json_null', "'null'", 'OK'),
                                          ('null', 'NULL', 'NULL_REJECTED')]:
            run('payload:' + table + ':' + suffix,
                f"INSERT INTO {table}({key},owner_user_id,owner_family_id,payload{extra}) VALUES('{suffix}',0,NULL,{payload}{extra_value})", expected)
        run('scope:' + table, f"SELECT owner_user_id,owner_family_id,COUNT(*) FROM {table} GROUP BY owner_user_id,owner_family_id", read=True)
        run('timestamp6:' + table, f"UPDATE {table} SET created_at='2026-01-02 03:04:05.123456' WHERE {key}='valid'", 'OK')
        run('timestamp6_read:' + table, f"SELECT MICROSECOND(created_at) FROM {table} WHERE {key}='valid'", read=True, expected_summary='123456')
    if 'risk_watch_mute' in successful:
        run('mute_null_zero', "INSERT INTO risk_watch_mute VALUES('synthetic-rule',0,NULL,NULL)", 'OK')
        run('mute_null_read', 'SELECT owner_user_id,owner_family_id,muted_until FROM risk_watch_mute', read=True)
    if 'draft_lifecycle_event' in successful:
        run('lifecycle_zero_null', "INSERT INTO draft_lifecycle_event(draft_id,event_type,actor_user_id,source_type,summary) VALUES(0,'SYNTHETIC',0,'synthetic',NULL)", 'OK')
    # Independently try the original dependency in a separate fresh schema.
    run('dependency_original_schema', f'CREATE DATABASE {schema}_dependency CHARACTER SET utf8mb4', 'OK')
    run('dependency_original_create', src[DRAFT][0].replace('draft_ledger_entry', schema + '_dependency.draft_ledger_entry', 1))
    # Minimal dependency fixture is explicitly synthetic, not an inferred production schema.
    for fixture, sql in [
        ('draft', 'CREATE TABLE draft_ledger_entry(id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL, owner_family_id BIGINT NULL, source_type VARCHAR(32) NOT NULL, source_ref VARCHAR(128) NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB ROW_FORMAT=DYNAMIC'),
        ('settlement', 'CREATE TABLE settlement_confirm(id BIGINT PRIMARY KEY) ENGINE=InnoDB')]:
        if run('fixture_' + fixture, sql, 'OK')['returncode']:
            raise Blocked('SYNTHETIC_FIXTURE_NOT_CREATED')
    settlement = src['sql/updatesql/20260929/01_settlement_audit_link.sql'][0]
    run('settlement_two_columns', settlement, 'OK')
    run('settlement_rerun', settlement, 'DUPLICATE_COLUMN')
    run('settlement_columns', 'SHOW FULL COLUMNS FROM settlement_confirm', read=True)
    idx_sql = [s for s in src['sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql'] if s.startswith('ALTER TABLE')]
    # Different users sharing family/source: first ALTER succeeds, second fails.
    run('partial_seed', "INSERT INTO draft_ledger_entry(owner_user_id,owner_family_id,source_type,source_ref) VALUES(1,9,'synthetic','dup'),(2,9,'synthetic','dup')", 'OK')
    run('partial_first_alter', idx_sql[0], 'OK')
    run('partial_second_alter', idx_sql[1], 'DUPLICATE_KEY')
    run('partial_state', 'SHOW INDEX FROM draft_ledger_entry', read=True)
    # Destroy only synthetic rows; never interpret ROLLBACK as DDL recovery.
    run('reset_synthetic_rows', 'DELETE FROM draft_ledger_entry', 'OK')
    run('finish_second_alter', idx_sql[1], 'OK')
    for i, s in enumerate(idx_sql):
        run('index_rerun:' + INDEXES[i], s, 'DUPLICATE_INDEX')
    run('indexes_final', 'SHOW INDEX FROM draft_ledger_entry', read=True)
    expected_indexes = '\n'.join(f'{index}\t{seq}\t{column}\t0'
                                 for index, owner in [(INDEXES[1], 'owner_family_id'), (INDEXES[0], 'owner_user_id')]
                                 for seq, column in enumerate((owner, 'source_type', 'source_ref'), 1))
    run('index_column_order_and_uniqueness', src['sql/updatesql/20260927/03_add_draft_ledger_source_unique_keys.sql'][-1],
        read=True, expected_summary=expected_indexes)
    for name, user, family, ref, expected in [
        ('base', 1, '9', "'ref'", 'OK'), ('owner_duplicate', 1, 'NULL', "'ref'", 'DUPLICATE_KEY'),
        ('family_duplicate', 2, '9', "'ref'", 'DUPLICATE_KEY'), ('different_scope', 2, '10', "'ref'", 'OK'),
        ('null1', 1, '9', 'NULL', 'OK'), ('null2', 1, '9', 'NULL', 'OK'),
        ('blank1', 3, 'NULL', "''", 'OK'), ('blank_duplicate', 3, 'NULL', "''", 'DUPLICATE_KEY'),
        ('spaces', 4, 'NULL', "'   '", 'OK'), ('zero', 0, 'NULL', "'0'", 'OK')]:
        run('source:' + name, f"INSERT INTO draft_ledger_entry(owner_user_id,owner_family_id,source_type,source_ref,updated_at) VALUES({user},{family},'synthetic',{ref},'2000-01-01')", expected)
    for i, s in enumerate(src[NORMALIZE]):
        run('normalize_synthetic_only:' + str(i), s, 'OK', read=s.startswith('SELECT'),
            expected_summary='2' if i == 0 else '0' if i == 2 else None)
    run('normalize_timestamp_effect', "SELECT COUNT(*) FROM draft_ledger_entry WHERE owner_user_id IN(3,4) AND source_ref IS NULL AND updated_at>'2000-01-01'", read=True, expected_summary='2')
    run('ddl_rollback_probe', 'START TRANSACTION; CREATE TABLE ddl_probe(id INT); ROLLBACK; SHOW TABLES LIKE \'ddl_probe\'', read=True, expected_summary='ddl_probe')
    run('tables_final', 'SHOW TABLES', read=True)


def report_template():
    return {'task_id': TASK, 'status': ['ENGINE_NOT_TESTED', 'PRODUCTION_SCHEMA_BLOCKED'],
            'production_ready': False, 'mode': 'DRY_RUN', 'counts': {'tables': 9, 'columns': 2, 'unique_indexes': 2},
            'source_sha256': PINS, 'tables': TABLES,
            'engines': {k: {'image_tag': v, 'status': 'NOT_RUN', 'events': []} for k, v in IMAGES.items()},
            'production_unknown': ['remaining_fields_indexes', 'duplicates', 'engine_charset_index_sizes',
                                   'lock_duration', 'recovery', 'full_application_compatibility']}


def execute(report, src, factory=Docker):
    report['mode'] = 'RUN_ISOLATED'
    for series, tag in IMAGES.items():
        d = factory()
        record = report['engines'][series]
        try:
            d.gate()
            record['image_id'] = d.create(tag)
            version = d.wait_ready()
            record['version'] = version
            expected = tag.split(':')[1]
            if version != expected:
                raise Blocked('ORACLE_MYSQL_VERSION_MISMATCH')
            vendor = d.sql('SELECT @@version_comment', database=False)
            record['vendor'] = vendor.stdout.strip() if vendor.returncode == 0 else 'UNKNOWN'
            if record['vendor'] != 'MySQL Community Server - GPL':
                record['vendor'] = 'UNVERIFIED'
                raise Blocked('ORACLE_MYSQL_VENDOR_MISMATCH')
            record['status'] = 'RUNNING'
            exercise(d, src, record)
            record['status'] = 'EXECUTED_REVIEW_REQUIRED'
        except Blocked as exc:
            record['blocker'] = str(exc)
            record['status'] = 'PARTIAL' if record['events'] else 'NOT_RUN'
        finally:
            try:
                d.cleanup()
                record['cleanup'] = 'NO_OWNED_CONTAINER_REMAINING'
            except Blocked as exc:
                record['cleanup'] = 'FAILED_MANUAL_REVIEW_REQUIRED'
                record['cleanup_category'] = str(exc)
        if record['status'] != 'EXECUTED_REVIEW_REQUIRED' or record['cleanup'] != 'NO_OWNED_CONTAINER_REMAINING':
            for pending in report['engines'].values():
                if pending['status'] == 'NOT_RUN' and 'blocker' not in pending:
                    pending['blocker'] = 'NOT_ATTEMPTED_AFTER_SAFETY_BLOCK'
            break
    if all(e['status'] == 'EXECUTED_REVIEW_REQUIRED' and e.get('cleanup') == 'NO_OWNED_CONTAINER_REMAINING'
           for e in report['engines'].values()):
        report['status'] = ['ENGINE_EXECUTED_REVIEW_REQUIRED', 'PRODUCTION_SCHEMA_BLOCKED']


def output_path(value):
    path = Path(value).resolve()
    allowed = (ROOT / 'scripts/deploy').resolve()
    if not path.is_relative_to(allowed) or path.suffix != '.json' or path.exists():
        raise Blocked('OUTPUT_PATH_OR_OVERWRITE_REJECTED')
    return path


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-isolated', action='store_true', help='explicitly create disposable local Docker engines')
    parser.add_argument('--output', help='new JSON within scripts/deploy; never overwrite')
    args = parser.parse_args(argv)
    report = report_template()
    try:
        destination = output_path(args.output) if args.output else None
        src = sources()
        if args.run_isolated:
            execute(report, src)
    except Blocked as exc:
        report['blocker'] = str(exc)
        destination = None
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if destination:
        with destination.open('x', encoding='utf-8') as stream:
            stream.write(rendered + '\n')
    print(rendered)
    if report.get('blocker') or (args.run_isolated and 'ENGINE_NOT_TESTED' in report['status']):
        return 2
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
