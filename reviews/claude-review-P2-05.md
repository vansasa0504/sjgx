# Claude Code 审查结果 - P2-05 备份恢复

## 1. 审查对象

- 任务：P2-05 备份恢复（P2 末任务）
- 分支：`ai/p2-backup-restore`（改动在工作区未提交，含已提交计划 commit `836f5d9e`）
- 任务单：`tasks/codex-task-P2-05.md`，计划：`tasks/claude-plan-P2-05.md`
- 审查日期：2026-07-12
- 改动范围：备份恢复脚本（DB/MinIO）、恢复后审计链闭环 IT、销毁证明 proof_hash（V023 + DataLifecycleManager）、方案/runbook/ops-manual、P2-05 报告
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（涉及持久化/迁移、审计防篡改、外部依赖，必做）
- 测试执行状态：**Maven 回归与 Testcontainers IT 当前环境未跑**（环境无 `mvn`，`bash -n` 因 WSL 不可用未跑）；前端 `npm run test:unit` 已通过。本审查结论基于静态代码追踪，Java 侧绿状态需在标准开发环境补跑后确认。

## 2. Git 状态

5 文件修改 + 12 文件新增，与任务单 F-1~F-7 对齐，无越界模块改动（未触碰 `.env`/密钥/生产配置/无关模块）。

| 类别 | 文件 |
|---|---|
| 修改 | `DataLifecycleManager.java`、`LifecycleEvent.java`、`StorageServiceTest.java`、`MigrationDialectCompatibilityTest.java`、`delivery/ops-manual.md` |
| 新增 | `db/migration{,-dm}/V023__lifecycle_proof.sql`、`db/migration{,-dm}/U023__lifecycle_proof.sql`、`delivery/backup-restore/*`（4 脚本 + 2 文档）、`delivery/p2-05-report.md`、`BackupRestoreAuditChainIT.java` |

## 3. 常规审查

| 项 | 结论 |
|---|---|
| F-1 DB 备份恢复脚本 | `backup-db.sh`/`restore-db.sh` 参数化（`DB_*`/`BACKUP_DIR`/`BACKUP_FILE`）、`MYSQL_PWD` 传密、`--single-transaction --routines --no-tablespaces`、恢复后计数校验 + 可选 audit verify。✓ 但备份范围与恢复语义有缺陷（见 §4.3 P2-3/P2-4） |
| F-2 MinIO 脚本 | `mc mirror --overwrite` 备份/恢复、`mc version enable`（失败容忍 `|| true`）、`mc mb --ignore-existing`。✓ |
| F-3 审计链闭环 IT | `BackupRestoreAuditChainIT`：2505 条哈希链 → JDBC 导出 → `DELETE` → 恢复 → `verify().intact()` + 计数一致。`@Testcontainers(disabledWithoutDocker=true)` + `@EnabledIfEnvironmentVariable(RUN_BACKUP_RESTORE_IT=true)` 双门控。✓ 用 JDBC 导出属任务允许的 mysqldump 降级路径，已诚实标注 |
| F-4 proof_hash 补全 | V023（MySQL+达梦双库）`ADD COLUMN operator/reason/proof_hash/object_key`，U023 反向 DROP。`DataLifecycleManager.proofHash` SHA-256 + `persist` JDBC 写入。`LifecycleEvent` record 扩展并保留 3 参数兼容构造。✓ 但抗篡改强度与生产接入有缺陷（见 §4.3 P2-1/P2-2） |
| F-5 销毁证明测试 | `lifecycleDestroyCreatesProofHashAndPersistsEvidence`：销毁→proofHash 64 位→同输入复算一致→篡改 reason 变化→DB 持久化→重启可读。✓ 覆盖核心断言 |
| F-6 方案+runbook | `backup-restore-plan.md`（DB/Redis/MinIO 策略 + NFR 对照 + Known Limits）+ `runbook.md`（pre-check/backup/restore/verify/drill/cleanup）+ `ops-manual.md` 对齐。✓ |
| F-7 报告 | `p2-05-report.md` 开发闭环/生产待外部/NFR 对照/限制/补测清单。✓ 但未说明 DataLifecycleManager 未接入生产调用链（见 P2-2） |
| 国产化 | V023 MySQL+达梦双库一致；`MigrationDialectCompatibilityTest` 两用例纳入 `assertLifecycleProofColumns`。⚠ 守护仅 H2 MODE=MySQL，不连真实达梦（既有局限，非本次引入） |
| 共性约束 | 未改 `.env`/密钥/生产配置/`tasks`/`reviews`/`k8s prod`/`security`；脚本无硬编码密钥。✓ |

## 4. 对抗式审查

### 4.1 攻击面枚举

1. **proofHash 抗篡改**：canonical 用 `"|"` 分隔，分隔符注入是否可构造碰撞。
2. **persist 事务/并发**：`scan` 不在事务中；`IdGenerator` 每次 `new`；多实例并发主键冲突。
3. **生产接入**：`DataLifecycleManager` 是否被生产代码调用（proof_hash 是否真落库）。
4. **备份范围**：`backup-db.sh` TABLES 列表是否覆盖审计 3 年留存的归档表。
5. **恢复语义**：`restore-db.sh` 恢复到非空库是否冲突。
6. **IT 数据保真**：JDBC 导出/导入的 datetime 精度、NULL、编码是否保持哈希链。
7. **IT 真闭环**：是否真"破坏→恢复"而非"原地不动"。
8. **V023 可逆/兼容**：历史行 NULL 兼容；U023 可逆；应用层 vs DB 层必填。
9. **脚本注入/密钥**：`DB_NAME` 表名拼接；`MYSQL_PWD` 失败时残留。
10. **verify 调用时机**：`restore-db.sh` 恢复后立即 curl，服务是否已起。

### 4.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| proofHash 分隔符碰撞 | `canonical = join("\|", assetCode, action, operator, reason, operatedAt.toString(), objectKey)`。设 operatedAt=T，case A `reason="x", objectKey="y\|T\|z"` 与 case B `reason="x\|T\|y", objectKey="z"` 产生相同串 `a\|DESTROY\|u\|x\|T\|y\|T\|z`。攻击者控制 reason+objectKey 且知道 operatedAt 即可篡改两者保持 proofHash 不变 | **存活 P2-1** |
| DataLifecycleManager 未接入生产 | Grep `new DataLifecycleManager`：仅 `DataLifecycleManager.java` 自身构造函数 + `StorageServiceTest`。**生产代码无任何调用点**。proof_hash 落库能力已实现但生产销毁流程不会触发；报告"新销毁事件写入 operator/reason/object_key/proof_hash"易被读成生产已落库 | **存活 P2-2** |
| 备份遗漏归档表 | `backup-db.sh` TABLES = t_service_invoke_log/t_audit_log/t_bill/t_bill_item/t_finance_sync_record/t_raw_data/t_data_catalog/t_user/t_api_credential/t_lifecycle_record。**无 `t_*_archive`**。P2-01 归档表是审计/调用日志 3 年留存的载体，不备份即 3 年留存数据无备份覆盖，与 NFR-S02 矛盾 | **存活 P2-3** |
| restore 恢复到非空库冲突 | `mysqldump` 未加 `--add-drop-table`，dump 含 `CREATE TABLE`（无 IF NOT EXISTS）。恢复到已有表的库：CREATE 报错，mysql 默认停止，恢复不完整；若带 `--force` 则 CREATE 失败后 INSERT 继续可能主键冲突。runbook 虽写"isolated target first"但脚本本身不保障 | **存活 P2-4** |
| IT 原地不动假闭环 | 第 56 行 `DELETE FROM t_audit_log` + 第 57 行 `assertEquals(0L, count)` 确认真清空；第 59 行 restore；第 61 行计数恢复；第 62 行 verify intact。真闭环 | 已反驳 |
| IT datetime 精度丢失 | `append` 中 `createdAt.truncatedTo(ChronoUnit.SECONDS)`；V022 改 `DATETIME(6)` 但写入整秒（微秒=0）；导出用 `toInstant().toEpochMilli()`（绝对时间，避时区）；恢复 `Timestamp.from(Instant.ofEpochMilli(...))`。verify 读出与 append 写入同整秒，`AuditHashing.hash` 一致 | 已反驳 |
| IT NULL/编码失真 | 导出用 Base64 编码每个字符串字段（`encode` 处理 null→""），`split("\t",-1)` 保留空尾列；恢复 `decode` 还原。tab/base64 避免分隔符与换行问题 | 已反驳 |
| V023 历史行不兼容 | `ADD COLUMN` 无 NOT NULL，历史行 proof_hash=NULL；`MigrationDialectCompatibilityTest` 只断言列存在不断言非空；DESTROY 行 proof_hash 由应用层 `scan` 生成（非 null） | 已反驳（但 DB 层不强制，见 P3-1） |
| U023 不可逆 | DROP COLUMN 顺序与 ADD 相反；新列无索引/约束/外键，DROP 安全可逆 | 已反驳 |
| MigrationDialectCompatibilityTest 不守达梦 | 用 H2 `MODE=MySQL`，`INFORMATION_SCHEMA.COLUMNS` 查询；不连真实达梦。V023 `ALTER TABLE ADD COLUMN` 语法达梦兼容，既有测试局限非本次引入 | 已反驳 |
| DB_NAME SQL 注入 | `restore-db.sh` 第 40 行 `FROM ${DB_NAME}.${table}` 拼接。DB_NAME 来自运维环境变量（非外部输入），table 来自硬编码数组。非外部攻击面 | 已反驳（记 P3-2） |
| MYSQL_PWD 残留 | `set -euo pipefail` 下 mysql 失败则脚本立即退出，第 42 行 `unset MYSQL_PWD` 不执行，密码环境变量残留至进程退出。子进程期间可见 | 已反驳（记 P3-3） |
| IdGenerator 并发主键冲突 | `DataLifecycleManager` 构造时 `new IdGenerator(jdbcTemplate)`，IdGenerator 计数器为实例内 ConcurrentHashMap。多实例/并发 scan 时各自从 MAX(id) 初始化，`incrementAndGet` 可能撞号。但生产未调用（见 P2-2），实际触发面为 0 | 已反驳（记 P3-4） |
| persist 无事务污染 events | `scan` 先 `events.add(event)` 后 `persist(event)`；persist 抛异常则 events 已入列，调用者重试会重复入列。仅内存污染，不影响 DB | 已反驳（记 P3-5） |
| verify 调用时机 | `restore-db.sh` 恢复后立即 `curl AUDIT_VERIFY_URL`；若服务未起则 curl 失败（`-fsS` 非致命？`set -e` 下 curl 非 0 退出码会终止脚本）。runbook post-restore 才起服务。脚本应将 verify 作为独立可选步骤 | 已反驳（记 P3-6） |
| IT Flyway 路径硬编码 | `Path.of("..","db","migration")` 相对模块目录；`platform-common` 为工作目录时指向项目根。既有 `RealDependenciesIT` 同模式 | 已反驳 |
| proofHash 非 DESTROY 为 null | ARCHIVE/KEEP 不生成 proofHash（设计如此，销毁证明仅针对 DESTROY）；persist 写 NULL，DB 列允许 NULL | 已反驳 |

### 4.3 存活缺陷

**无 P1 阻断。** 4 项 P2 改进 + 6 项 P3 提示。

#### P2 改进（4 项）

**P2-1 proofHash canonical 分隔符碰撞（抗篡改强度不足）**
- 严重级：P2
- 可复现路径：`DataLifecycleManager.proofHash`，operatedAt=T 时 `reason="x", objectKey="y|T|z"` 与 `reason="x|T|y", objectKey="z"` 产生相同 proofHash。
- 影响：攻击者若有 DB UPDATE 权限且知道 operatedAt，可同时篡改 reason+objectKey 保持 proofHash 不变，掩盖真正销毁对象。单字段篡改仍可检出。
- 缓解现状：DB 层 REVOKE UPDATE/DELETE 是真正防线（报告已列生产门禁），但开发闭环未覆盖。
- 建议：canonical 改用长度前缀编码（每字段前加 `len:`）或 JSON 规范化（字段定序 + 转义），消除分隔符歧义。

**P2-2 DataLifecycleManager 未接入生产销毁调用链（诚实性缺口）**
- 严重级：P2
- 可复现路径：Grep `new DataLifecycleManager` 全仓，生产 main 代码无调用点。
- 影响：proof_hash + DB 写入能力已实现并测，但生产中无任何销毁流程触发它；`t_lifecycle_record` 在生产不会有新销毁记录写入。报告"新销毁事件写入 operator/reason/object_key/proof_hash"表述易被误读为生产已生效。
- 建议：在 `p2-05-report.md` 与 `backup-restore-plan.md` 明确"DataLifecycleManager 销毁证明落库能力已实现并通过单测，但尚未接入生产生命周期销毁调用链，生产销毁落库为后续接入项"；或将接入纳入本任务（需确认是否有生产销毁入口可挂）。

**P2-3 backup-db.sh 备份范围遗漏归档表**
- 严重级：P2
- 可复现路径：`backup-db.sh` 第 13-24 行 TABLES 数组无 `t_audit_log_archive`/`t_service_invoke_log_archive`。
- 影响：P2-01 归档表是审计/调用日志 3 年留存的物理载体；不纳入备份 = 3 年留存数据不在备份恢复范围，恢复后审计链仅含主表（当年分区），与 NFR-S02 审计≥3 年留存矛盾。`restore-db.sh` 计数校验同样遗漏归档表。
- 建议：TABLES 数组补充 `t_audit_log_archive`、`t_service_invoke_log_archive`（及 P2-01 其他归档表）；plan 的 Key backup tables 同步补充；runbook 计数校验补归档表。

**P2-4 restore-db.sh 恢复到非空库语义不明确**
- 严重级：P2
- 可复现路径：`backup-db.sh` 未传 `--add-drop-table`；`restore-db.sh` 第 36 行 `mysql ... < backup.sql` 直接导入。目标库已有表时 `CREATE TABLE` 失败，`set -e` 下 mysql 退出码非 0 导致脚本终止，恢复不完整且无明确报错。
- 影响：运维误操作恢复到运行中库时静默失败或数据不一致。
- 建议：`backup-db.sh` 加 `--add-drop-table`（dump 文件含 `DROP TABLE IF EXISTS`）；或在 `restore-db.sh` 恢复前显式提示"目标库必须为空/隔离库"，并在 runbook 强调。

#### P3 提示（6 项）

| 编号 | 项 | 建议 |
|---|---|---|
| P3-1 | proof_hash DB 层无 NOT NULL 约束，DESTROY 必填仅靠应用层 `scan` | 若需强约束，考虑应用层校验或生产 REVOKE 后补检查约束（达梦/MySQL 兼容性需验证） |
| P3-2 | `restore-db.sh` `FROM ${DB_NAME}.${table}` 表名拼接 | 维持运维输入边界；可选加 `[[ "$DB_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]` 校验 |
| P3-3 | `MYSQL_PWD` 在 mysql 失败时未 unset（`set -e` 提前退出） | 改 `trap 'unset MYSQL_PWD' EXIT` 或用 `mysql --password=` 配合 `MYSQL_PWD` 局部作用域 |
| P3-4 | `IdGenerator` 多实例并发主键冲突（生产未触发） | 接入生产调用链时确保 `DataLifecycleManager` 单例，或改雪花算法（IdGenerator 注释已标注 RW-1） |
| P3-5 | `persist` 无事务，失败时 `events` 内存列表污染 | `persist` 失败可 log 后不抛，或 `events.add` 移到 persist 成功后 |
| P3-6 | `restore-db.sh` 恢复后立即 curl verify，服务可能未起 | 将 verify 拆为独立步骤/脚本，runbook 已正确顺序，脚本对齐 |

### 4.4 对"建议通过"的反驳

- **为何不应通过？** Java 侧 Maven 回归 + Testcontainers IT **未实测**，无法证明编译通过与 IT 闭环真绿；P2-3（备份漏归档表）使 NFR-S02 审计 3 年留存的备份覆盖存在实质缺口；P2-2（未接入生产）使销毁证明落库在生产不生效，报告表述有误导风险。
- **反驳后存活动作：** 上述三项中，Maven/IT 补跑是硬门禁（用户已知）；P2-3 应在本次修复（备份范围是本任务 F-1 核心交付）；P2-2 可通过报告补充说明化解（不必本次接入生产）。P2-1/P2-4 可作为改进项跟进。
- **结论调整：** 不直接"通过"。建议**有条件通过**：P2-3 必须修复，P2-2 必须在报告补充说明，Maven + IT 必须在标准开发环境补跑通过后方可提交。P2-1/P2-4/P3 列为后续改进。

## 5. 需求/计划符合性

| 标准（claude-plan §9） | 结论 |
|---|---|
| DB/MinIO 备份恢复脚本套件完整，`bash -n` 通过 | 脚本完整；`bash -n` 未实测（WSL 不可用），需补 ⚠ |
| 恢复后审计链校验闭环测试通过 | IT 设计正确（真破坏→恢复→verify intact + 计数一致）；未实测，需补跑 ⚠ |
| 销毁证明 proof_hash 补全 + 测试通过 | V023 + DataLifecycleManager + 单测设计正确；未实测 mvn；未接入生产（P2-2） ⚠ |
| 备份恢复方案 + runbook 完整 | 完整，ops-manual 对齐 ✓ |
| 报告诚实分层 | 基本诚实；P2-2 未接入生产未明示，需补 ⚠ |
| `mvn test` + 前端测试全绿 | 前端已绿；mvn 未实测，需补 ⚠ |

## 6. 审查结论

**需返工（轻量）+ 条件通过。** 无 P1 阻断，核心 F-1~F-7 闭环逻辑与测试设计正确，诚实分层基本到位。但存在 4 项 P2 与测试未实测，需完成下列返工后方可提交。

### 返工任务清单

1. **[必须] P2-3**：`backup-db.sh`/`restore-db.sh` TABLES 数组补充 `t_audit_log_archive`、`t_service_invoke_log_archive`（及 P2-01 其他归档表）；`backup-restore-plan.md` Key backup tables 同步；`runbook.md` 计数校验同步。
2. **[必须] P2-2**：`p2-05-report.md` 与 `backup-restore-plan.md` 明确标注"DataLifecycleManager 销毁证明落库已实现并单测，但尚未接入生产销毁调用链"。
3. **[必须] 测试补跑**：标准开发环境执行 `mvn test -pl platform-common,platform-pipeline` + `RUN_BACKUP_RESTORE_IT=true mvn test -pl platform-common -Dtest=BackupRestoreAuditChainIT` + `bash -n delivery/backup-restore/*.sh`，附真实输出。
4. **[建议] P2-1**：proofHash canonical 改长度前缀/JSON 规范化，消除分隔符碰撞；补碰撞反例单测。
5. **[建议] P2-4**：`backup-db.sh` 加 `--add-drop-table` 或 `restore-db.sh` 恢复前空库校验。
6. **[记录] P3-1~P3-6**：记入后续改进，不阻断本次。

### 已尝试反驳且未发现存活阻断项的说明

本审查对"建议通过"做了主动证伪：枚举 16 个反例，逐条追代码验证，存活 4 项 P2 + 6 项 P3，**无 P1 阻断项**。存活 P2 中，P2-3（备份漏归档表）影响 NFR-S02 实质覆盖、P2-2（未接入生产）影响报告诚实性，二者必须化解；P2-1/P2-4 可作改进跟进。Java 侧测试未实测是硬门禁，需补跑确认。化解 P2-2/P2-3 + 测试补跑通过后可提交。

## 7. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| Java 侧未实测，编译/IT 状态未知 | 中 | 必须补跑 mvn + IT |
| 备份漏归档表致 3 年留存无备份 | 中 | P2-3 修复 |
| proofHash 碰撞致销毁证明可篡改 | 低-中 | P2-1 改进 + DB REVOKE 生产门禁 |
| restore 到非空库静默失败 | 中 | P2-4 改进 |
| DataLifecycleManager 未接入生产 | 低 | P2-2 报告标注 |

**回滚**：V023 有 U023 双库回滚；脚本可还原；DataLifecycleManager 改动有单测守护（既有 3 参数构造兼容保留）。
