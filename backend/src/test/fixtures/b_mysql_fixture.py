"""原B专属合成库门禁及结构准备；不读取应用配置或root凭据。"""
import hashlib, json, os, re, subprocess, sys, tempfile, uuid
from pathlib import Path

TASK='task-mydca-isolated-auth-transaction-20261014'
DBS=('mydca_fixture_20261014','mydca_restore_fixture_20261014')
ROOT=Path('D:/AicoreRescue/release-readiness-20261014/synthetic-mysql80')
HISTORY=Path('D:/Project/ai-core-runtime/agent-runtime/dispatch/dual-worker-state/runs/20261014-owner-work-b-business-01')
TRUSTED=HISTORY.parent/'20261015-owner-work-b-fix-01'
EVID=Path(os.environ.get('AICORE_FIXTURE_EVIDENCE',''))
RECORDS=None
REPO=Path(__file__).resolve().parents[4]
FIXTURE=ROOT/'fixture-credentials.json'
PRIVATE=ROOT/'pid-backup-20261014-214751'

def ensure(ok,reason):
    if not ok: raise RuntimeError('BLOCKED_TEST_ENV: '+reason)

def evidence_context():
    # 控制器独占创建新run并绑定候选；Java ProcessBuilder继承同一环境。
    task=os.environ.get('AICORE_FIXTURE_TASK','')
    run=os.environ.get('AICORE_FIXTURE_RUN','')
    ensure(task=='task-mydca-b-transaction-consistency-fix-20261015','evidence task mismatch')
    ensure(re.fullmatch(r'owner-runtime-rescue-20261015-[A-Za-z0-9-]+',run),'evidence run mismatch')
    expected=HISTORY.parent/run/'fixture-evidence'
    ensure(EVID.is_absolute() and EVID==expected,'evidence path mismatch')
    for path in (EVID,*EVID.parents):
        ensure(not path.is_symlink() and not path.is_junction(),'linked evidence directory')
    ensure(EVID.is_dir(),'controller evidence directory missing')
    binding=json.loads((EVID.parent/'fixture-binding.json').read_bytes())
    ensure(binding.get('task_id')==task and binding.get('run_id')==run and binding.get('evidence')==str(EVID),'controller binding mismatch')
    ensure(binding.get('task_sha256')=='cfb5d74510f47a916d17e82f61a84a4eb801246bce56158378f1c43650492c6b','approval binding mismatch')
    head=subprocess.run(['git','-C',str(REPO),'rev-parse','HEAD'],capture_output=True,check=True,creationflags=0x08000000).stdout.decode().strip()
    ensure(head==binding.get('source'),'candidate binding mismatch')
    return binding

def save(name,obj):
    global RECORDS
    binding=evidence_context()
    ensure(re.fullmatch(r'[A-Za-z0-9-]+\.json',name),'invalid evidence filename')
    if RECORDS is None:
        RECORDS=EVID/uuid.uuid4().hex
        RECORDS.mkdir(exist_ok=False)
    ensure(not RECORDS.is_symlink() and not RECORDS.is_junction(),'linked evidence record')
    # 每次gate均追加不可覆盖记录；同一调用里的重复名称也保留独立事件。
    destination=RECORDS/(uuid.uuid4().hex+'-'+name)
    with destination.open('x',encoding='utf-8') as stream:
        json.dump({**obj,'_binding':binding},stream,ensure_ascii=False,indent=2)
    return destination

def gate():
    evidence_context()
    ensure(hashlib.sha256(FIXTURE.read_bytes()).hexdigest()=='a616afcba1a9d4a0e199aa23111bb553718463d8e7283a64154a141167dc7fd5','fixture SHA drift')
    c=json.loads(FIXTURE.read_bytes())
    ensure(c['owner_task_id']==TASK and c['synthetic_only'] is True,'task identity drift')
    ensure((c['database'],c['restore_database'])==DBS and c['host']=='127.0.0.1' and c['port']==33387,'scope drift')
    ensure(Path(c['datadir']).resolve()==(ROOT/'data').resolve(),'datadir drift')
    ps="""$l=@(Get-NetTCPConnection -State Listen -LocalPort 33387);if($l.Count -ne 1 -or $l[0].LocalAddress -ne '127.0.0.1'){throw 'listener mismatch'};$s=Get-CimInstance Win32_Process -Filter ('ProcessId='+$l[0].OwningProcess);$p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$s.ParentProcessId);@($s,$p)|ForEach-Object{[pscustomobject]@{pid=$_.ProcessId;parent=$_.ParentProcessId;created=$_.CreationDate.ToString('o');executable=$_.ExecutablePath;command=$_.CommandLine}}|ConvertTo-Json"""
    p=subprocess.run(['powershell','-NoProfile','-Command',ps],capture_output=True,creationflags=0x08000000)
    ensure(p.returncode==0,'live loopback listener/process identity unavailable; no database connection attempted')
    procs=json.loads(p.stdout)
    proof=TRUSTED/'restart-identity.json'
    ensure(hashlib.sha256(proof.read_bytes()).hexdigest()=='f990502b5a3b148cb40911891c249f55b601838003b35a70baaf6f0765bab346','restart identity proof drift')
    prior=json.loads(proof.read_bytes())['after_identity']
    ensure(len(procs)==2,'process chain missing')
    s,launcher=procs
    ensure(c['owned_server_pid']==prior['legacy_credential_owned_pid'] and s['pid']==prior['server_pid'] and launcher['pid']==s['parent']==prior['launcher_pid'],'PID drift')
    ensure(s['created']==prior['server_created'] and launcher['created']==prior['launcher_created'],'creation time drift')
    for proc in procs:
        ensure(proc['executable']==prior['executable'] and proc['command']==prior['command_line'],'binary or arguments drift')
    ensure(int((ROOT/'server.pid').read_text().strip())==s['pid'],'pid-file drift')
    save('identity-latest.json',{'fixture_sha256':hashlib.sha256(FIXTURE.read_bytes()).hexdigest(),'processes':procs,'task':TASK,'schemas':DBS})
    return c

def client(c,exe,args=(),sql=None):
    # 凭据仅写入已核验的受限目录中的临时option文件，永不进入argv。
    quote=lambda v:'"'+str(v).replace('\\','\\\\').replace('"','\\"')+'"'
    fd,name=tempfile.mkstemp(prefix='business-client-',suffix='.cnf',dir=PRIVATE)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:
            f.write('[client]\nprotocol=TCP\nhost=127.0.0.1\nport=33387\nuser='+quote(c['user'])+'\npassword='+quote(c['password'])+'\n')
        result=subprocess.run([str(Path(c['mysql_bin'])/exe),'--defaults-file='+name,*args],input=sql.encode('utf-8') if sql is not None else None,capture_output=True,timeout=120,creationflags=0x08000000)
        ensure(result.returncode==0,'client exit '+str(result.returncode)+'; '+result.stderr.decode('utf-8',errors='replace').replace(c['user'],'REDACTED_FIXTURE_ACCOUNT').replace(c['password'],'REDACTED_SECRET')[:500])
        return result.stdout.decode('utf-8')
    finally: Path(name).unlink(missing_ok=True)

def sql(c,db,query):
    ensure(db in DBS,'database outside whitelist')
    return client(c,'mysql.exe',('--batch','--skip-column-names','--default-character-set=utf8mb4','--database='+db),query)

def identity(c):
    lines=sql(c,DBS[0],'SELECT VERSION(),@@port,@@datadir,DATABASE(),USER(),CURRENT_USER(); SHOW GRANTS;').splitlines()
    row=lines[0].split('\t')
    ensure(row[:2]==['8.0.36','33387'] and Path(row[2]).resolve()==(ROOT/'data').resolve() and row[3]==DBS[0],'SQL instance mismatch')
    ensure(all(v.split('@')[0]==c['user'] for v in row[4:]),'CURRENT_USER mismatch')
    grants=[]
    for line in lines[1:]:
        m=re.fullmatch(r'GRANT (.+) ON (.+) TO (.+)',line)
        ensure(m is not None,'unknown grant')
        priv,target,account=m.groups()
        ensure((priv=='USAGE' and target=='*.*') or (priv=='ALL PRIVILEGES' and target in {'`'+db+'`.*' for db in DBS}),'grant outside scope')
        ensure('WITH GRANT OPTION' not in account,'delegated grant')
        grants.append({'privileges':priv,'target':target})
    ensure(len(grants)==3,'grant count drift')
    save('sql-identity-grants.json',{'exit_code':0,'version':row[0],'port':33387,'identity_matches_fixture':True,'grants':grants})

def statements(source):
    # 保留字符串里的分号；忽略行注释，不执行原文件中的其他语句。
    source=re.sub(r'^\s*--[^\n]*','',source,flags=re.M)
    parts=[];start=0;quote=None;escape=False
    for i,char in enumerate(source):
        if escape:escape=False;continue
        if char=='\\' and quote:escape=True;continue
        if quote:
            if char==quote:quote=None
        elif char in "'\"`":quote=char
        elif char==';':
            parts.append(source[start:i].strip());start=i+1
    ensure(not source[start:].strip(),'unterminated source SQL')
    return [p for p in parts if p]


def snapshot(c,db):
    tables=sql(c,db,"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;").splitlines()
    ensure(len(tables)==32 and all(re.fullmatch('[a-z_]+',t) for t in tables),'restore table scope mismatch')
    column_rows=sql(c,db,"SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;").splitlines()
    mapped={t:[] for t in tables}
    for row in column_rows:
        table,col=row.split('\t');ensure(table in mapped,'unexpected table');mapped[table].append(col)
    queries=[]
    for table in tables:
        columns=mapped[table]
        ensure(all(re.fullmatch('[a-z_][a-z0-9_]*',col) for col in columns),'restore column scope mismatch')
        query='SELECT '+','.join("IF(`"+col+"` IS NULL,'SQL_NULL',HEX(CAST(`"+col+"` AS BINARY)))" for col in columns)+' FROM `'+table+'` ORDER BY id;'
        structure="SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY ORDINAL_POSITION; SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY INDEX_NAME,SEQ_IN_INDEX; SELECT CONSTRAINT_NAME,CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY CONSTRAINT_NAME;"
        constraints="SELECT CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY CONSTRAINT_NAME,ORDINAL_POSITION; SELECT CONSTRAINT_NAME,UPDATE_RULE,DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='"+table+"' ORDER BY CONSTRAINT_NAME; SELECT cc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS cc JOIN information_schema.TABLE_CONSTRAINTS tc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='"+table+"' ORDER BY cc.CONSTRAINT_NAME;"
        queries+= ["SELECT '__B_DATA_"+table+"__';",query,"SELECT '__B_STRUCTURE_"+table+"__';",structure,"SELECT '__B_FK_"+table+"__';",constraints]
    lines=sql(c,db,'\n'.join(queries)).splitlines();segments={};current=None
    for line in lines:
        if line.startswith(('__B_DATA_','__B_STRUCTURE_','__B_FK_')):
            ensure(line not in segments,'duplicate snapshot marker');current=line;segments[current]=[]
        else:
            ensure(current is not None,'snapshot marker missing');segments[current].append(line)
    result={}
    for table in tables:
        data=segments['__B_DATA_'+table+'__'];structure=segments['__B_STRUCTURE_'+table+'__']
        # Windows mysql原始batch输出为CRLF；批量读取仍保持同一字节口径。
        encode=lambda rows:('\r\n'.join(rows)+('\r\n' if rows else '')).encode()
        result[table]={'row_count':len(data),'all_columns_data_sha256':hashlib.sha256(encode(data)).hexdigest(),'columns_indexes_constraints_sha256':hashlib.sha256(encode(structure)).hexdigest(),'fk_reference_rules_and_check_clauses_sha256':hashlib.sha256(encode(segments['__B_FK_'+table+'__'])).hexdigest()}
    return result

def restore(c):
    before=snapshot(c,DBS[0]);dest=snapshot(c,DBS[1])
    if any(t['row_count'] for t in dest.values()):
        # 重跑只接受此前已逐表验证且字节仍一致的本task恢复副本；未知记录不清理。
        markers=list(EVID.glob('*/*-restore-latest.json'))
        latest=max(markers,key=lambda p:p.stat().st_mtime_ns) if markers else TRUSTED/'restore-latest.json'
        ensure(not latest.is_symlink() and not latest.parent.is_symlink() and not latest.parent.is_junction(),'linked restore ownership')
        owner=latest.parent
        if not markers:
            ensure(hashlib.sha256(latest.read_bytes()).hexdigest()=='26699c1ce1b600258b0d9a841f4923fe98ad8dc36187ada9c90a6ab22d97ced2','historical restore marker drift')
        marker=json.loads(latest.read_bytes())
        previous=owner/marker['file']
        ensure(previous.parent==owner and not previous.is_symlink() and hashlib.sha256(previous.read_bytes()).hexdigest()==marker['sha256'],'restore ownership marker mismatch')
        owned=json.loads(previous.read_bytes())['restored']
        ensure(set(dest)==set(owned) and all(all(dest[t].get(k)==v for k,v in proof.items()) for t,proof in owned.items()),'restore destination drift')
    ensure(before['ledger_txn']['row_count']>0 and before['ledger_posting']['row_count']>0 and before['draft_ledger_entry']['row_count']>0 and before['settlement_confirm']['row_count']>0,'empty or incomplete business dump prohibited')
    gate();identity(c)
    dump=client(c,'mysqldump.exe',('--single-transaction','--skip-lock-tables','--skip-add-locks','--skip-disable-keys','--skip-add-drop-table','--skip-triggers','--skip-comments','--hex-blob','--set-gtid-purged=OFF','--no-tablespaces','--default-character-set=utf8mb4',DBS[0]))
    normalized=re.sub(r'/\*!\d{5}\s*(.*?)\*/',r'\1',dump,flags=re.S)
    normalized=re.sub(r'/\*.*?\*/','',normalized,flags=re.S)
    ss=statements(normalized)
    ensure(not re.search(r'\b(?:CREATE DATABASE|CREATE USER|GRANT|REVOKE|SET GLOBAL|OUTFILE|LOAD DATA|USE\s+`?)\b',normalized,re.I),'unsafe dump statement')
    for st in ss:
        if st.upper().startswith('SET '):
            ensure(not re.search(r'GLOBAL|PERSIST|SQL_LOG_BIN|GTID',st,re.I),'global dump setting')
            continue
        m=re.match(r'(?:CREATE TABLE|INSERT INTO)\s+`([a-z_]+)`\s*[\s(]',st,re.I)
        ensure(m is not None and m[1] in before,'unrecognized or crossschema dump target')
    nonce=uuid.uuid4().hex
    backup=PRIVATE/('business-full-synthetic-'+nonce+'.sql')
    with backup.open('x',encoding='utf-8') as stream:stream.write(dump)
    # 独立目标库先核验全空，逐表限定清理已由本轮建立的32张合成表。
    gate();identity(c)
    drop='SET FOREIGN_KEY_CHECKS=0;'+''.join('DROP TABLE `'+t+'`;' for t in before)+'SET FOREIGN_KEY_CHECKS=1;'
    sql(c,DBS[1],drop)
    gate();identity(c);sql(c,DBS[1],dump)
    after=snapshot(c,DBS[1])
    unchanged=snapshot(c,DBS[0])
    ensure(before==unchanged,'source changed during restore')
    ensure(before==after,'restored business rows/structure mismatch')
    evidence='restore-verification-'+nonce+'.json'
    result=save(evidence,{'status':'PASSED','source_schema':DBS[0],'destination_schema':DBS[1],'dump_sha256':hashlib.sha256(backup.read_bytes()).hexdigest(),'backup_kept_private':True,'audited_dump_statement_count':len(ss),'source_before':before,'restored':after,'all_tables_columns_rows_indexes_constraints_equal':True,'source_unchanged':True,'dump_exit_code':0,'restore_exit_code':0,'production_access':'NOT_RUN'})
    save('restore-latest.json',{'file':result.name,'sha256':hashlib.sha256(result.read_bytes()).hexdigest()})
    print('REAL_MYSQL_SYNTHETIC_FULL_DUMP_RESTORE_PASSED',len(before),'tables')

def ddl_verify(c):
    paths=['sql/initsql/20261015_order_funding_line_type.sql','sql/updatesql/20261015/01_order_funding_line_type.sql']
    scripts=[(path,(REPO/path).read_text(encoding='utf-8-sig')) for path in paths]
    audit=[]
    def run(db,path,text,expected=None):
        gate();identity(c)
        try:
            output=sql(c,db,text)
        except RuntimeError as error:
            ensure(expected is not None and expected in str(error),'unexpected migration rejection')
            audit.append({'schema':db,'script':path,'sha256':hashlib.sha256(text.encode()).hexdigest(),'expected_rejection':expected})
        else:
            ensure(expected is None,'migration silently accepted invalid schema')
            audit.append({'schema':db,'script':path,'sha256':hashlib.sha256(text.encode()).hexdigest(),'exit_code':0,'output':output})
    before={db:snapshot(c,db) for db in DBS}
    for db in DBS:
        for path,text in scripts:
            for repeat in range(2):run(db,path,text)
        ensure(before[db]==snapshot(c,db),'idempotent migration changed existing records or structure')
    # Preserve the nonempty restored funding table intact. Only this task's new
    # scratch table is dropped; never clear old rows to evade restore drift.
    db=DBS[1]
    ensure(int(sql(c,db,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='b_line_type_preserved';").strip())==0,'DDL scratch ownership conflict')
    sql(c,db,'RENAME TABLE order_funding_line TO b_line_type_preserved;')
    try:
        for path,text in scripts:run(db,path,text,'InnoDB base table required')
        sql(c,db,'CREATE TABLE order_funding_line (id BIGINT PRIMARY KEY) ENGINE=InnoDB;')
        for path,text in scripts:
            for repeat in range(2):run(db,path,text)
        column=sql(c,db,"SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_funding_line' AND COLUMN_NAME='line_type';").strip()
        ensure(column=='varchar(20)\tYES\tSOURCE','new canonical column differs: '+column)
        sql(c,db,"INSERT INTO order_funding_line(id) VALUES(1); INSERT INTO order_funding_line(id,line_type) VALUES(2,NULL),(3,'TARGET');")
        ensure(sql(c,db,"SELECT id,COALESCE(line_type,'SQL_NULL') FROM order_funding_line ORDER BY id;").splitlines()==['1\tSOURCE','2\tSQL_NULL','3\tTARGET'],'default/null/TARGET contract failed')
        for definition in ['VARCHAR(20) NOT NULL DEFAULT \'SOURCE\'','VARCHAR(20) NULL DEFAULT \'TARGET\'','VARCHAR(20) NULL DEFAULT \'source\'','INT NULL DEFAULT NULL']:
            # These are only three newly inserted scratch rows, not old records.
            sql(c,db,'DELETE FROM order_funding_line; ALTER TABLE order_funding_line MODIFY line_type '+definition+';')
            for path,text in scripts:run(db,path,text,'incompatible existing line_type')
    finally:
        scratch=int(sql(c,db,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='order_funding_line';").strip())
        if scratch:sql(c,db,'DROP TABLE order_funding_line;')
        sql(c,db,'RENAME TABLE b_line_type_preserved TO order_funding_line; DROP PROCEDURE IF EXISTS mydca_20261015_line_type;')
    ensure(before=={db:snapshot(c,db) for db in DBS},'DDL tests changed preserved synthetic records or structure')
    save('ddl-verification.json',{'status':'PASS','version':'8.0.36','mysql57':'NOT_RUN','commands':audit,'canonical_column':column,'compatible_existing':'VARCHAR(16) NULL DEFAULT NULL unchanged','before':before,'all_existing_records_and_structure_unchanged':True,'source_sha256':{p:hashlib.sha256((REPO/p).read_bytes()).hexdigest() for p in paths}})
    print('B_DDL_BOTH_PATHS_REPEAT_TWICE_PASS NEW_DEFAULT_SOURCE_NULL_TARGET_PASS MISSING_TABLE_AND_INCOMPATIBLE_COLUMNS_REJECTED OLD_RECORDS_UNCHANGED')

def audit_commands():
    # 只读取控制器真实命令记录并封存哈希；不执行命令、不连接数据库、不做基线检查。
    trace=HISTORY.parent/'20261014-234551-31728-0/task-mydca-b-transaction-consistency-fix-20261015.stdout.jsonl'
    raw=trace.read_bytes()
    items={}
    for line in raw.splitlines():
        event=json.loads(line)
        item=event.get('item',{})
        if item.get('type')=='command_execution':items[item['id']]=item
    rows=[]
    for key,item in items.items():
        if item.get('exit_code') is None:continue
        command=item['command'].replace('|','\\|').replace('\n',' ')
        output=item.get('aggregated_output','').encode('utf-8')
        rows.append(f"| {key} | `{command}` | {item['exit_code']} | {len(output)} | `{hashlib.sha256(output).hexdigest()}` |")
    report=REPO/'docs/mydca_b_transaction_fix_20261015.md'
    with report.open('a',encoding='utf-8') as f:
        f.write('\n## 控制器命令日志封存检查点\n\n')
        f.write(f"原始 JSONL：`{trace}`；本次读取前缀 {len(raw)} 字节，SHA-256 `{hashlib.sha256(raw).hexdigest()}`。\n\n")
        f.write('每项哈希为控制器 item.completed 的 aggregated_output 原文 UTF-8 字节；日志定位为 JSONL item ID。进行中的命令不伪造退出码，后续最终 hook、commit 与状态检查由控制器继续追加保全。此表不重新执行历史命令。\n\n')
        f.write('| Item | 原始命令 | exit_code | 输出字节 | 输出 SHA-256 |\n| --- | --- | --- | --- | --- |\n')
        f.write('\n'.join(rows)+'\n')
    print('CONTROLLER_COMMAND_LOG_HASHES_SEALED',len(rows),'completed native commands; database access NOT_RUN')

if __name__=='__main__':
    if sys.argv[1]=='command-audit':
        audit_commands()
        sys.exit(0)
    c=gate();identity(c)
    if sys.argv[1]=='gate':print('LIVE_INSTANCE_ACCOUNT_GRANTS_VERIFIED')
    elif sys.argv[1]=='restore':restore(c)
    elif sys.argv[1]=='ddl-verify':ddl_verify(c)
    else:raise RuntimeError('unsupported fixture action')
