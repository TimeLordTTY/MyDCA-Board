# MyDCA v0.8 草稿强幂等与并发去重

核验日期：2026-09-27
目标仓库：`TimeLordTTY/MyDCA-Board`（分支 `v2`）
对应任务：`task-mydca-v08-draft-strong-idempotency-20260927`（owner 直接批准，L3）
本任务范围：把“同一采集事件重复提交只产生一条草稿”从**应用层幂等**提升为可验证的**强幂等**；
不改变人工确认边界，不新增自动 preview / confirm / 正式入账。

## 一、背景与缺口

Android v0.6 起，服务端草稿创建（`DraftLedgerEntryService.createDraft`）已经按 `sourceType + sourceRef`
做“先查后插”的应用层幂等；v0.6 的三份文档与 `Phase3-开发进度总结.md` 都明确记录了一个已知缺口：

> 未为 `draft_ledger_entry` 引入数据库唯一约束，并发重放同一 `sourceRef` 时仍存在极窄的重复插入窗口。

具体竞态：两个请求（同一设备的重复点击、Outbox 手动重试与批量重试重叠、多端同时提交）都在幂等查询阶段
**查不到**已有草稿，随后**同时插入**，于是同一来源落下两条草稿。应用层幂等无法单独消除这个窗口，
必须由数据库唯一性兜底。本任务补上这一层，并保证冲突可恢复而不是 500。

## 二、基线核对结论

| 核对项 | 结论 |
| --- | --- |
| `draft_ledger_entry` 建表 SQL | `sql/updatesql/20260610/01_create_draft_ledger_entry.sql`（已发布，本任务不改写） |
| 既有索引 | `idx_draft_ledger_owner_user_status`、`idx_draft_ledger_owner_family_status`、`idx_draft_ledger_source(source_type, source_ref)`、`idx_draft_ledger_confirm_txn`；**没有任何唯一键** |
| `createDraft` 现有逻辑 | 归一化 `sourceType`（空则 `manual`）→ `selectVisibleBySource` 先查 → 命中直接返回既有草稿 → 否则 `insert` |
| `selectVisibleBySource` 可见性语义 | `owner_user_id = :userId OR owner_family_id = :familyId`，不限制草稿状态，`ORDER BY id ASC LIMIT 1` |
| 空 `sourceRef` | 应用层不参与幂等，每次插入新草稿 |
| Android sourceRef 规则 | 手工文本 / OCR：进入输入页或选图时生成一次 `UUID`，`sourceRef = android-ocr-<requestId>`，同一次采集内重试沿用；支付通知候选 / Outbox：`sourceRef = fingerprint`（`PaymentNotificationParser.fingerprint`，含脱敏摘要 + 分钟桶的 SHA-256） |
| 数据库类型与版本 | MySQL 8.0（`README.md`、`sql/initsql/DDL.sql` 头部、`docs/开发实施指南.md` 均声明），JDBC 驱动 `com.mysql.cj.jdbc.Driver` |
| migration 风格 | `sql/updatesql/YYYYMM/NN_description.sql`，增量 ALTER，不重写历史脚本；脚本按编号顺序执行，只执行未执行过的脚本 |

## 三、四个层次必须分清

本任务的核心是**不要把四件事混为一谈**：

1. **应用层幂等**（v0.6 已有）：请求进来先按可见作用域 + `sourceType + sourceRef` 查一次，
   命中就直接返回既有草稿。它把绝大多数重复请求挡在插入之前，但不具备原子性。
2. **数据库唯一性**（v0.8 新增）：用唯一键把“同一可见作用域 + `source_type` + `source_ref` 只能有一条”
   变成数据库不变量。它是并发场景下的最终裁决者。
3. **并发冲突恢复**（v0.8 新增）：插入撞上唯一键时，`createDraft` 不抛 500，而是按同一作用域重查并返回
   先写入的那条草稿；查不到才原样抛出冲突。
4. **人工确认边界**（始终不变）：草稿仍是草稿。恢复路径不 preview、不 confirm、不调用 `QuickEntryService`、
   不写正式 `ledger_txn` / `ledger_post` / 订单 / 结算 / 持仓。

## 四、数据库唯一性设计

### 4.1 采用两个唯一键，而不是一个三列唯一键

```sql
ALTER TABLE draft_ledger_entry
    ADD UNIQUE KEY uk_draft_ledger_user_source (owner_user_id, source_type, source_ref);
ALTER TABLE draft_ledger_entry
    ADD UNIQUE KEY uk_draft_ledger_family_source (owner_family_id, source_type, source_ref);
```

**为什么不能写成 `(owner_user_id, owner_family_id, source_type, source_ref)` 一个唯一键：**
`owner_family_id` 允许为 `NULL`（个人草稿），而 MySQL 唯一索引不约束“多行 NULL”。
把可空列放进唯一键会让**个人作用域**（`owner_family_id IS NULL`）完全失去数据库级去重能力，
正好漏掉“同一采集事件重复提交”的主要场景。

**为什么两个唯一键正好等于“可见归属作用域”：**
可见性定义是“同一个人 **或** 同一个家庭可见”。因此两行草稿只要
`owner_user_id` 相同 **或** `owner_family_id` 相同（且非空），就必须被去重：

- 唯一键 1 覆盖“同一个人”，并且**不关心家庭是否为空**，所以个人草稿也被约束；
- 唯一键 2 覆盖“同一个家庭”，`owner_family_id IS NULL` 时自动不参与，与“无家庭归属不强制家庭级去重”一致。

二者合起来恰好等于可见作用域，不多不少。

### 4.2 为什么不会把不同用户 / 家庭的合法草稿误判为同一条

- 不同的人、不同的家庭不会命中同一个唯一键：唯一键 1 要求 `owner_user_id` 相同；
  唯一键 2 要求 `owner_family_id` **非空且相同**。因此不同用户 / 不同家庭可以合法复用同一 `sourceRef`。
- 同家庭内不同成员复用同一 `sourceRef` 会被判为重复——这不是新增的误判，
  而是**与既有应用层语义完全一致**：`selectVisibleBySource(第二个成员, familyId, ...)` 本来就
  会命中第一位成员创建的、对该家庭可见的那条草稿并直接返回它，本来就不会插入第二条。
  数据库唯一键只是把这个既有语义变成原子不变量。
- Android 生成的 `sourceRef` 是**每次采集事件**级别的（`android-ocr-<UUID>` / 通知指纹），
  不是日期、金额、备注等弱条件，因此不会因为格式过宽而把两次不同采集误去重。

### 4.3 NULL 与空串语义

- `source_ref IS NULL`：MySQL 唯一索引不参与判定，多行都能存在 —— 完整保留“空来源不强制幂等”的旧行为。
- 空串 / 纯空白串：应用层已在 `normalizeSourceRef` 中统一**按 NULL 落库**，
  迁移脚本也把历史空串归一化为 NULL。否则空串会被唯一键当成有效来源参与去重，与旧行为冲突。

## 五、迁移脚本（`sql/updatesql/20260927/`）

按要求采用**增量 migration**，不改写 `20260610` 的历史建表脚本：

| 顺序 | 脚本 | 作用 | 是否改数据 |
| --- | --- | --- | --- |
| 1 | `01_precheck_draft_ledger_source_duplicates.sql` | 只读预检：按用户作用域 / 家庭作用域列出历史重复草稿、列出空串 `source_ref` 明细、输出总体统计 | 否（纯 SELECT） |
| 2 | `02_normalize_blank_draft_ledger_source_ref.sql` | 把空串 / 纯空白 `source_ref` 归一化为 `NULL` | 只改这一列，不改状态、金额与追踪字段 |
| 3 | `03_add_draft_ledger_source_unique_keys.sql` | 添加两个唯一键 + 生效核对查询 + 回退语句 | 否（只加索引） |

**历史重复数据的处理策略：明确阻断，不静默删除。**
预检 1 / 2 返回任何行时，步骤 3 的 `ALTER TABLE` 会以 `Duplicate entry`（错误码 1062）失败；
脚本不含任何 `DELETE`，不会删除或改写真实草稿，由人工决定保留哪一条并处理其余行。
预检脚本输出 `keep_candidate_min_id` 只作为人工判断的参考，不是自动决策。

**回退说明**：步骤 3 末尾给出 `DROP INDEX` 回退语句；步骤 2 的归一化不丢业务信息
（空串与 NULL 都表示“没有来源标识”），无需回退。

**可审查性**：脚本内含完整注释，说明唯一键与可见性定义的对应关系、NULL 语义、
以及为什么这样不会跨用户 / 跨家庭误合并。

## 六、`createDraft` 并发安全实现

`DraftLedgerEntryService.createDraft`：

1. `sourceRef` 先经 `normalizeSourceRef` 归一化（空 / 空白 → `NULL`）；
2. 保留既有应用层幂等快速查询 `findReplayableDraft`，命中直接返回既有草稿；
3. `insert` 包在唯一键冲突捕获中：仅捕获 `org.springframework.dao.DuplicateKeyException`；

   命中时走 `replayExistingDraftAfterDuplicateKey`：按**同一可见作用域**重新查询并返回既有草稿
   （`DRAFT` / `CONFIRMED` / `IGNORED` 都直接返回，保持既有重放语义）；
   若重查不到（例如冲突行不可见或已消失），**原样抛出该冲突异常**，绝不吞掉并伪装成成功；
4. 其他数据库异常（例如 `DataAccessResourceFailureException`）不进入恢复分支，正常向上抛出 —— 只捕获明确的唯一键冲突。

事务说明：`createDraft` 本身不是 `@Transactional`，单条 `INSERT` 自成一个自动提交事务；
即使被上层事务包裹，MySQL InnoDB 在唯一键冲突时也只回滚失败的那条语句，事务其余部分仍可用，
因此“冲突后重查”不需要新事务。

恢复路径依旧不 preview、不 confirm、不调用 `QuickEntryService`、不写正式账本 —— 有单元测试固定该边界。

## 七、`updateDraft` 的连带影响

唯一键同时约束 `updateDraftContent` 对 `source_ref` 的改写。为避免把这种冲突变成未经处理的异常，
`updateDraft` 会把 `DuplicateKeyException` 转成明确业务提示
（“该来源已被同一可见范围内的另一条草稿占用，请更换来源标识或刷新后重试”），
并且同样把空 `sourceRef` 归一化为 `NULL`。

## 八、Android / Outbox 兼容核对结论

**本轮未修改任何 Android 代码**，理由是核对后确认现有实现已满足要求：

- 三类来源的 `sourceRef` 都是“一次采集事件”级别且在同一次重试链路中稳定：
  手工文本 / OCR 由页面进入或选图时生成一次 `UUID`（`sourceRef = android-ocr-<requestId>`）；
  支付通知候选使用 `PaymentNotificationParser.fingerprint`（含脱敏摘要与分钟桶的 SHA-256，60 秒内同一条通知稳定）。
- `DraftOutboxQueue` 只承载草稿创建：`enqueue` 以 `sourceType + sourceRef` 去重，重试始终复用同一 `intent`，
  缺少稳定 `sourceRef` 不入队；唯一的网络能力 `DraftCreationGateway.createDraft` 类型上不存在 preview / confirm。
- 未引入后台常驻重试，未改变 v0.6 的有限退避策略。

上述行为由既有 Android 测试覆盖并在本轮**重新执行验证**（见下）。

## 九、测试与验证

- `backend: mvn -B test`：**70 项通过**（0 失败 / 0 错误 / 0 跳过），由 v0.7 的 55 项增至 70 项。
  - 新增 `DraftLedgerEntryConcurrentReplayTest`（10 项）：
    同一 user 作用域并发重放只落一条并返回既有草稿；同一 family 作用域并发重放只落一条并返回家庭成员创建的既有草稿；
    不同 owner 作用域可复用同一 `sourceRef`；空 `sourceRef` 仍允许创建多条且按 `NULL` 落库；
    冲突恢复返回既有 `CONFIRMED` / `IGNORED` 草稿且不改状态；冲突恢复不调用 `QuickEntryService`、
    不写 preview / confirm；重查不到既有草稿时原样抛出冲突；非唯一键数据库异常不被吞掉且不触发恢复重查；
    `updateDraft` 的来源冲突转为业务提示、空来源归一化为 `NULL`。
  - 新增 `DraftLedgerSourceUniqueMigrationTest`（5 项）：**不连接数据库**的等价可验证方案 ——
    校验迁移脚本静态内容（两个唯一键的精确列、回退语句、只读预检、无删除语句），
    并从脚本解析出唯一键列定义驱动内存唯一性模型，断言“同作用域去重、跨作用域不冲突、NULL 不参与唯一性”成立。
- `android-app: gradlew.bat --no-daemon testDebugUnitTest --rerun-tasks`：**24 个测试类 / 119 项通过**
  （0 失败 / 0 错误 / 0 跳过），与 v0.7 基线一致，确认三入口 `sourceRef` 稳定性与 Outbox 重试不触达 preview / confirm 无回归。
- `scripts/post-task-compile-hook.ps1`：通过。
- `git diff --check`：通过（无空白错误）。

**未连接任何数据库**：迁移脚本只做静态审查与模型验证，未执行到任何环境（含测试环境），
未连接生产数据库，未修改真实财务记录。

## 十、明确不做

- 不自动 preview、不自动 confirm、不自动正式入账、不执行交易；
- 不连接生产数据库，不部署 migration 到生产，不删除历史重复数据；
- 不改变账户余额、持仓、订单或正式流水；
- 不把 `sourceRef` 扩展为包含敏感完整通知原文（通知来源仍只用脱敏指纹）；
- 不新增 Android 后台常驻重试，不改变 v0.6 有限退避策略。

## 十一、提交与推送

本轮执行约束为“提交但不推送”：改动只提交到本地 `v2`，
由 owner 批准的 Codex auto-executor 在进程退出后校验改动路径落在 `allowed_paths` 内再推送。
