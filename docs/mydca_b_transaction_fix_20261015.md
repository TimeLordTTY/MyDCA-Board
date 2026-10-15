# B 并发交易一致性修复与本机隔离复验

任务：task-mydca-b-transaction-consistency-fix-20261015。输入 v2 SHA：3982958994817030601791810dd9e17270f13bbf。本报告是 Work 在用户2026-10-15直接授权内的独立候选复核，不是正式 Delivery，也不是部署验收。

## 自动执行和历史保全

原 Scheduled Dispatcher 在00:49领取 run 20261014-164930-8904-0，使用 OpenAI gpt-6.1-sol，随后因 quota_exhausted 延期。额度恢复后的 run 20261014-234551-31728-0 在07:59生成本机候选21a96f8fc58c991ca3b346d68e70971f253e498b，08:01正式候选复验拒绝 candidate_revalidation_paths_unsupported；原始进程关闭异常仍保留，未白名单。

21a96f8及旧e532ab8856c3ee9cfe54cbd75aa10adbdf9acf34、76266604ec95d5f70189c1f843ff3b983d92a85f均保留原 Git 对象/ref，旧日志不改写。专项失败卡与原B审批边界不改；A/A2/C没有重跑。MyDCA远端v2未更新，原B无正式Delivery。

Work run：20261015-owner-work-b-fix-01；独立工作树D:/AicoreRescue/b-fix-20261015，由原Runtime.task-lock/slot保护。从实时v2基线复用经审核的专项候选内容，最终只形成一个本机提交；不手工push业务源码绕过Runtime.integrate/Finalizer。

## B-TXN-001 最小实现

OrderService.cancelOrder与SettlementService.confirmSettlement在同一Spring REQUIRED事务内，先执行OrderMapper.lockByOrderId的SELECT id FROM orders WHERE order_id=? FOR UPDATE，由orders.uk_order_id定位同一行。锁后再校验归属、状态、已有结算及fresh preview；资金、结算、账本、分录与状态同事务提交或回滚。取消发现已有结算保守拒绝；确认按原契约幂等返回既有结算，取消非PENDING重试拒绝，避免重复退款。

两入口显式READ_COMMITTED，避免认证/预览在REPEATABLE READ先建立的旧一致读快照。REQUIRED不提升外层隔离级别，因此拒绝RR、未声明隔离级别或只读外层事务；显式READ_COMMITTED外层仍保持整体回滚，不引入REQUIRES_NEW资金提交。Controller当前无外层业务事务。共同订单锁设置flushCache=true/useCache=false，清除锁前MyBatis一级缓存；Mapper没有二级cache。同订单锁序是订单→身份/关联读取→既有资金/账本写入，不添加死锁重试，不把偶发死锁当正确性证明。本专项不证明不同订单共享账户的全系统串行化。

## B-DDL-001 正式配套

完整枚举Mapper和SQL后确认：应用资源已有backend/src/main/resources/db/migration/V2026012801__add_funding_line_type.sql，但正式sql/initsql和sql/updatesql缺少配套。历史加列/回填脚本未改写、未在生产执行。

新库按基础sql/initsql/DDL.sql后执行sql/initsql/20261015_order_funding_line_type.sql；既有库使用sql/updatesql/20261015/01_order_funding_line_type.sql。两文件字节一致，使用MySQL5.7/8已有information_schema、SIGNAL、存储过程和普通ALTER，不依赖ADD COLUMN IF NOT EXISTS。新列VARCHAR(20)可空DEFAULT SOURCE；接受已有可空VARCHAR(16)/NULL默认或VARCHAR(20)/严格大写SOURCE默认，保留旧NULL与结构。不兼容类型/默认/非空/EXTRA、缺表或非InnoDB明确拒绝。没有历史数据UPDATE或新索引，已有订单资金行索引保持。不得盲目批量运行历史updatesql，生产执行仍需独立审批及串行migration。

OrderFundingLineMapper的卖出占用汇总补计NULL-as-SOURCE，与结算既有NULL语义一致，仍排除TARGET/非PENDING/跨owner/其他产品账户，不回填真实记录。

## 实例与证据门禁

仅MySQL8.0.36、127.0.0.1:33387及mydca_fixture_20261014/mydca_restore_fixture_20261014。07时自动复验实例不在，数据库测试被跳过/阻断，不作为成功证据。09:01 Work按原二进制、--no-defaults、独立datadir和参数恢复同一合成实例；新server42744/launcher12720及创建时间、完整参数、exe哈希、pid文件在任何SQL前独立封存。

原fixture-credentials.json字节SHA256仍a616afcba1a9d4a0e199aa23111bb553718463d8e7283a64154a141167dc7fd5，未修改凭据或旧PID metadata。新身份凭证在Work run/restart-identity.json，测试固定校验其SHA256 f990502b5a3b148cb40911891c249f55b601838003b35a70baaf6f0765bab346，再核对现场父子/PID/创建时间/exe/参数/datadir。legacy PID仅用作未改历史metadata的交叉校验；实际活跃PID必须匹配新独立凭证。SQL VERSION、datadir、库名和CURRENT_USER核对后SHOW GRANTS仅USAGE及两个专属库各ALL PRIVILEGES，无GRANT OPTION。错配在连接前硬停止。密码仅短生命周期受限客户端option文件，不进入argv/Git/报告。

## 测试及独立审核

保留原confirm/cancel失败不变量，改为真实锁前/后latch固定两种交错；补双confirm、双cancel、幂等合法重试、真实写后故障rollback、锁前缓存、外层RC回滚和RR提前拒绝、旧NULL占用。并发断言返回值及订单/资金行/余额/settlement/ledger/posting/草稿终态，不靠sleep。真实Spring Security/AuthService/JWT、家庭ADMIN/MEMBER/跨owner均走真实MySQL；禁止Mockito/H2替代。

09:06首次真实完整Java311项、3失败、0错误、0跳过。三个失败均指新增assertConfirmedFundingAndCash误断言initial_shares=100；正式无关联现实账户基金按POSITION分录推导份额，initial_shares是另一契约。仅修正新增断言为唯一POSITION DEBIT分录100份/100成本及对应虚拟账户balance100、initial_shares NULL；原竞争数量/终态断言未削弱。所有失败日志保留。09:09最终完整Java真实执行311项、0失败、0错误、0跳过，exit0；17项真实业务测试全部执行通过，其中包含非空虚构业务dump/restore和两个交错方向、双confirm/cancel、写后故障及外层事务/缓存验证。

DDL真实复验exit0：两个路径在两库各重复两次，验证缺表、缺列补齐、SOURCE默认/显式NULL/TARGET、错误类型/default/null拒绝；暂存整张恢复资金行表，只对scratch表变更并最终恢复，两个库旧记录/结构快照相同。MySQL5.7动态验证NOT_RUN。

全量Mapper/SQL覆盖：25 Mapper（20 XML/5注解）、165 SQL语句、28引用对象、47 SQL脚本、66脚本对象、28引用对象有表级覆盖；未完整解析/动态表名/缺通用初始化对象为0，数量闭环true。表级覆盖不替代逐列契约或生产schema。范围内13个文件；API DTO/路由、依赖、配置和构建入口未引入新要求。

原始命令、exit_code、源码hash、日志hash、真实恢复与DDL快照在D:/Project/ai-core-runtime/agent-runtime/dispatch/dual-worker-state/runs/20261015-owner-work-b-fix-01。AiCore脱敏报告reports/work-rescue/20261015-b-transaction-fix-orchestration.md承载最终结果、拒绝证据和Git独立复核。完整Java、最终typecheck及最后原完整hook结果须由该run的final结果确认，不以本段计划当通过。

## 正式交付与生产边界

原candidate verifier不支持本专项必要XML/SQL/Python，控制器未改。即便本机业务回归通过，也不得跳过单提交、stderr、allowlist、源码hash、repo integration lock或Finalizer/Hermes。原B另有candidate_unknown_or_dangerous_stderr、旧多提交和资源路径限制，仍独立阻断。Runtime.integrate/Finalizer/B完成WeCom未运行；失败通知收据不代表完成收据。

生产SSH/SCP/systemctl/OOM扫描、服务器重启、生产SQL/DDL/DML、Jar/PC/H5上线、真实财务/交易全部NOT_RUN。生产MySQL5.7语义、2GB内存Swap/启动峰值、真实systemd rollback、owner设备验收仍是后续独立硬门禁。本轮只能交付本机候选与真实合成测试证据，不能进入生产发布操作。