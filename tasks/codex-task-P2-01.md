# P2-01 Codex 实现任务单 — 大表分区归档与查询优化

> 主控：Claude Code　执行：Codex
> 依据：`tasks/claude-plan-P2-01.md`（第一性原理计划）、`docs/requirements.md` §3.1、`docs/database-design.md` §7
> 前置：P0-01~P1-05 已合入 master，迁移版本 V021
> 分支：`ai/p2-partition-archive`
> 日期：2026-06-30

---

## 0. 必读

1. `AGENTS.md`（职责边界）
2. `tasks/claude-plan-P2-01.md`（本任务第一性原理计划，权威）
3. `docs/requirements.md` §3.1（NFR-P04/P07）、§4（数据要求）
4. `docs/database-design.md` §7（分区建议）、§9（国产库兼容）
5. `db/migration/V005`、`V008`、`V003`、`V009`、`V013`、`V016`（三表现状）
6. `platform-common/src/main/java/com/platform/common/log/JdbcServiceInvokeLogRepository.java`
7. `platform-common/src/main/java/com/platform/common/audit/JdbcAuditLogRepository.java`
8. `platform-pipeline/src/main/java/com/platform/pipeline/service/AsyncInvokeLogWriter.java`
9. `platform-billing/src/main/java/com/platform/billing/BillingApplication.java`、`bill/BillGenerator.java`、`stats/StatsAggregator.java`

---

## 1. 任务目标

让 `t_service_invoke_log`、`t_audit_log`、`t_raw_data` 三张大表具备 RANGE 月分区能力，消除全表扫描查询路径，新增分区维护与归档能力，使带时间范围的查询 `EXPLAIN` 命中分区裁剪与索引。通过标准：**EXPLAIN 命中索引/分区**。

---

## 2. 实现边界（严格遵守）

**只做**：F-1 ~ F-10（见下）。
**不做**：
- 不补 `t_raw_data` 的 JDBC 仓储读写路径（独立缺口，单独任务）。
- 不引入对象存储/新中间件依赖。
- 不做分库分表。
- 不修改 `.env`、密钥、生产配置、无关模块。
- 不重构无关代码。
- 不跳过测试。

---

## 3. 任务清单

### F-1　分区表迁移 V022（MySQL 基线） — `db/migration/V022__large_table_partition.sql`

对三张表：主键改造 + RANGE 月分区 + 归档表。

**通用规则**：
- MySQL/达梦分区表要求分区键在主键中 → 三表 `PRIMARY KEY (id)` 改为 `(id, created_at)`。
- 月分区粒度，初始建 `p202601` ~ `p202612`（覆盖 2026 全年）+ `pmax VALUES LESS THAN MAXVALUE`。
- 分区键表达式用 `TO_DAYS(created_at)`（MySQL）。
- 脚本须幂等可重入（用 `INFORMATION_SCHEMA` 判断是否已分区，避免重复执行报错）。

**三表处理**：

1. `t_service_invoke_log`：
   - `DROP PRIMARY KEY` → `ADD PRIMARY KEY (id, created_at)`。
   - `PARTITION BY RANGE (TO_DAYS(created_at)) (...)`。
   - 既有索引保留（V009/V013 建的）。

2. `t_audit_log`：
   - `DROP PRIMARY KEY` → `ADD PRIMARY KEY (id, created_at)`。
   - `PARTITION BY RANGE (TO_DAYS(created_at)) (...)`。
   - 注意：审计日志 append-only + 哈希链（prev_hash/hash），分区改造不影响哈希链计算（哈希基于内容，与存储分区无关）。

3. `t_raw_data`：
   - `DROP PRIMARY KEY` → `ADD PRIMARY KEY (id, created_at)`。
   - `PARTITION BY RANGE (TO_DAYS(created_at)) (...)`。
   - 该表当前无索引，本任务**不补索引**（读写路径未落地，避免无意义索引维护成本）。

4. 归档表 `t_service_invoke_log_archive`：
   - 结构与 `t_service_invoke_log` 一致（含索引），**非分区表**（归档表数据量可控，无需分区）。
   - 仅对调用日志建归档表（审计日志 ≥3 年合规留存，只归档不销毁，复用归档表模式；`t_raw_data` 无读写路径，暂不建归档表）。
   - 实际归档表清单：`t_service_invoke_log_archive`、`t_audit_log_archive`。

**数据迁移策略**（脚本须同时支持两种库状态）：
- 开发/测试空库：直接 `ALTER TABLE ... PARTITION BY`（若表无数据或数据可丢弃）。
- 已有数据的生产库：`ALTER TABLE ... PARTITION BY` 对已有数据 MySQL 会自动分配到对应分区，**无需重建表**（RANGE 分区 + 已有 `created_at` 列，MySQL 原生支持在线转换）。Codex 须在脚本注释中说明此点，并验证 NOT NULL 约束。

### F-2　分区表迁移 V022（达梦） — `db/migration-dm/V022__large_table_partition.sql`

- 同构达梦方言。沿用 P0-02 策略：`TEXT→CLOB`、`TINYINT→SMALLINT`，避开 `AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`ON DUPLICATE KEY UPDATE`、`JSON_`、手写 `LIMIT`、`TEXT`、`TINYINT`。
- 达梦 `PARTITION BY RANGE` 语法：分区键直接用列名 `created_at`（达梦 RANGE 分区按日期列即可，不支持 `TO_DAYS()` 函数），分区值用日期字面量 `DATE '2026-02-01'`。
- Codex 须确认达梦分区语法正确性（参考达梦 DM8 文档），并在脚本注释标注"真实达梦实测留上线前"。

### F-3　回滚脚本 U022 — `db/migration/U022__large_table_partition.sql`、`db/migration-dm/U022__large_table_partition.sql`

- 三表 `ALTER TABLE ... REMOVE PARTITIONING`（MySQL）/达梦等价语法。
- 主键还原为 `(id)`。
- 删除归档表 `t_service_invoke_log_archive`、`t_audit_log_archive`（`DROP TABLE IF EXISTS`）。
- 回滚后查询仍正确（仅失去分区裁剪）。

### F-4　调用日志查询优化 — `platform-common` + `platform-pipeline` + `platform-billing`

**F-4.1 `JdbcServiceInvokeLogRepository`**：
- 新增 `findByRange(Instant from, Instant to, int page, int size)`：SQL `WHERE created_at >= ? AND created_at < ?` + `ORDER BY created_at DESC, id DESC` + `LIMIT ? OFFSET ?` + `COUNT`。
- 新增 `findByServiceRange(String serviceCode, String consumerCode, String status, Instant from, Instant to, int page, int size)`：在现有 `queryFiltered` 基础上叠加时间范围谓词。
- `findAll()` 标 `@Deprecated`，注释"仅供测试，生产禁止使用（全表扫描）"。保留实现以兼容既有测试。
- 现有 `findByService`/`findByConsumer` 保留（已有 SQL 分页），但内部可复用 `findByServiceRange`（from/to 传 null 时不加时间谓词）。

**F-4.2 `AsyncInvokeLogWriter`**（platform-pipeline）：
- `logs()` 方法：当 `repository != null` 时不再调用 `findAll()`。改为提供 `logs(Instant from, Instant to, int page, int size)` 委托 `repository.findByRange`。
- `findByService` 已走 SQL 分页，保持。但其内存回退分支（`repository == null`）保留。
- 调用方 `DataServiceManager.logs` / `DataServiceController.logs` 若当前依赖 `logs()` 全量，改为传入时间范围 + 分页参数（若调用方无时间范围，传最近 N 天默认窗口，如最近 30 天，避免全表）。

**F-4.3 `BillingApplication` + `BillGenerator` + `RegulatoryReportService` + `StatsAggregatorJobHandler`**（platform-billing）：
- 当前通过 `Supplier<List<ServiceInvokeLog>>` 注入 `findAll()`，调用方已有时间范围（`BillGenerator.generate` 有 `start/end`）。
- 改造：把 `Supplier<List<ServiceInvokeLog>>` 升级为 `Function<BillPeriodContext, List<ServiceInvokeLog>>` 或新增 `findByRange` 供应器。
  - **最小改动方案**（推荐）：`BillGenerator.generate` 内已有 `start/end`，把 `logSupplier` 改为 `BiFunction<Instant, Instant, List<ServiceInvokeLog>>`（from, to → logs），`generate` 内用 `logSupplier.apply(startInstant, endInstant)` 替代 `logSupplier.get().stream().filter(...)`。同步改 `RegulatoryReportService`、`StatsAggregatorJobHandler`。
  - `BillingApplication` 的 Bean 注入改为 `() -> invokeLogRepository == null ? List.of() : invokeLogRepository.findByRange(from, to, page, size)` → 调整为 `BiFunction` 注入。
- 账期 `start.atStartOfDay().toInstant(ZoneOffset.UTC)` ~ `end.plusDays(1)...` 已有，直接透传。
- `StatsAggregatorJobHandler` 若无明确时间范围，用最近 30 天窗口（可配）。

**F-4.4 grep 守证**：完成后 grep 确认生产代码（非 test）不再调用 `findAll()`，仅测试可保留。

### F-5　审计哈希链分批校验 — `platform-common/audit/JdbcAuditLogRepository.verify()`

- 当前 `verify()` 全表 `SELECT * ORDER BY id` 加载内存。
- 改为**按 id 游标分批**：每批 N 条（N=1000，可配），`WHERE id > ? ORDER BY id LIMIT N`，跨批用上一批末尾 `hash` 作为下一批起始 `prevHash` 校验基准。
- 哈希链连续性必须保持：分批结果与一次性结果完全等价（测试覆盖）。
- `latestHash()` 已用 `setMaxRows(1)`，无需改。
- 注意 `verify()` 是 `synchronized`（继承自接口默认或显式），分批不破坏线程安全。

### F-6　分区维护任务 — `platform-common` 新增 `PartitionMaintainer`

- 接口与实现：
  ```java
  public interface PartitionMaintainer {
      void ensureFuturePartitions(String table, int monthsAhead);
      void archiveExpiredPartitions(String table, Instant cutoff);
      void dropExpiredPartitions(String table, Instant cutoff);
  }
  ```
- `JdbcPartitionMaintainer` 实现：
  - `ensureFuturePartitions`：查询 `INFORMATION_SCHEMA.PARTITIONS`，若未来 N 月分区缺失则 `ALTER TABLE ... ADD PARTITION`。
  - `archiveExpiredPartitions`：对调用日志/审计日志，将 `cutoff` 之前的分区数据 `INSERT INTO t_*_archive SELECT` 后再处理（归档表非分区，按行搬）。
  - `dropExpiredPartitions`：归档完成后 `ALTER TABLE ... DROP PARTITION`（仅对已归档分区）。
- `@Scheduled` 定时触发（cron 可配，默认 `0 0 2 * * ?` 凌晨 2 点），开关 `platform.partition.maintain.enabled` 默认 `false`（开发环境关闭，生产开启）。
- 每次操作写 `t_audit_log`（event_type=`PARTITION_MAINTAIN`，detail 记录表名/分区/操作/行数）。
- 注册 Bean：在 `BillingApplication`（或 platform-common 配置类）注册，`@ConditionalOnProperty` 控制开关。

### F-7　归档表与归档任务

- 归档表 DDL 在 F-1 内（`t_service_invoke_log_archive`、`t_audit_log_archive`）。
- 归档搬运逻辑在 F-6 `archiveExpiredPartitions` 内。
- 归档表查询：暂不提供查询端点（归档数据检索留后续），仅保证归档写入 + 审计留痕。

### F-8　Testcontainers 分区集成测试 — `platform-common` 或 `platform-pipeline` test

- 新增 `LargeTablePartitionIntegrationTest`（Testcontainers MySQL 8.0）：
  - 启容器 → 跑 `db/migration/V001~V022` → 灌入跨 3 个月（2026-01、02、03）的 `t_service_invoke_log` 数据 →
  - `EXPLAIN SELECT ... WHERE created_at BETWEEN '2026-02-01' AND '2026-02-28'` → 断言 `partitions` 列只含 `p202602`、`key` 列命中索引。
  - 查询结果正确性：返回数据与插入的 2 月数据一致。
  - 同样对 `t_audit_log`、`t_raw_data` 做分区裁剪断言。
- 测试须 `@Testcontainers` + `@Container` MySQL 8.0，沿用 platform-pipeline 现有 Testcontainers 用法（参考其 pom 依赖配置）。
- 若 platform-common 无 Testcontainers 依赖，在 platform-common pom 添加 `testcontainers-mysql`（test scope），或把测试放 platform-pipeline。

### F-9　方言静态守护 — 扩展 `MigrationDialectCompatibilityTest`

- 纳入 V022：MySQL 基线与达梦目录都跑 V001~V022 迁移 + contract CRUD。
- 静态守护断言（在现有守护基础上扩展）：
  - MySQL 目录：允许 `PARTITION BY RANGE`、`TO_DAYS`。
  - 达梦目录：断言不含 `TO_DAYS`（达梦不用此函数）、不含 `TEXT`/`TINYINT`/`AUTO_INCREMENT`/`ON UPDATE CURRENT_TIMESTAMP`/`ON DUPLICATE KEY UPDATE`/`JSON_`。
  - 两目录均断言不含手写 `LIMIT` 在迁移脚本中（除分页查询代码外）——迁移脚本不应有 LIMIT。

### F-10　EXPLAIN 证据文档 — `docs/perf/p2-01-explain-evidence.md`

- 记录三表带时间范围查询的 `EXPLAIN` 输出（来自 Testcontainers 测试或本地 MySQL）。
- 标注 `partitions` 列命中目标分区、`key` 列命中索引。
- 标注未实测项（如真实达梦）。

---

## 4. 测试要求

1. **新增测试**：
   - `LargeTablePartitionIntegrationTest`（Testcontainers，分区裁剪 + EXPLAIN + 数据正确性）。
   - `JdbcServiceInvokeLogRepositoryTest` 扩展：`findByRange` / `findByServiceRange` 分页与时间过滤正确。
   - `JdbcAuditLogRepositoryTest` 扩展：`verify` 分批与一次性等价（构造 >2 批数据）。
   - `PartitionMaintainerTest`：预建/归档/丢弃行为 + 审计留痕（可用内存或 Testcontainers）。
   - `MigrationDialectCompatibilityTest` 扩展 V022。
2. **回归**：
   - `BillGeneratorTest`、`BillingControllerTest`、`RegulatoryReportServiceTest`、`StatsAggregatorJobHandler` 相关测试：更新 `Supplier` → `BiFunction` 注入，断言账单聚合金额不变（对齐 P0-06 RW-5 多调用聚合一致性）。
   - `AsyncInvokeLogWriterTest`、`DataServiceManagerTest`/`DataServiceControllerTest`：更新 `logs()` 调用为带时间范围。
   - `mvn test`（全量 8 模块）BUILD SUCCESS。
   - `cd platform-ui && npm run test:unit` 全绿（本任务预计不涉前端，但跑一次确认无回归）。
3. **测试命令**：
   ```bash
   mvn test "-Dspring.profiles.active=jdbc"
   mvn test -pl platform-common -am
   cd platform-ui && npm run test:unit
   ```

---

## 5. 输出要求（完成后提交给 Claude Code 审查）

1. 修改/新增文件清单。
2. 测试命令与测试结果（贴 mvn 输出摘要 + Testcontainers 通过证据）。
3. EXPLAIN 证据（贴 `partitions`/`key` 列）。
4. grep 证据：生产代码不再调用 `findAll()`。
5. 潜在风险与未实测项（真实达梦分区、生产在线数据迁移）。
6. 偏离说明（若计划与实现有出入，必须说明原因）。

---

## 6. 共性约束

- 国产化：MySQL + 达梦双库，`MigrationDialectCompatibilityTest` 守护，避开方言禁忌。
- 不破坏 P0/P1 既有闭环（落库、事实源、审计防篡改、账单聚合一致性、调用日志事实源）。
- 版本号：V022/U022（续 V021）。
- 已执行旧 V022 的库：`flyway:repair`（P0-01 策略）。
- 审计日志防篡改：分区改造不影响哈希链；归档搬运后原分区 DROP 须在归档表写入成功后执行（事务/先搬后删）。
- 上线前门禁：真实达梦分区实测、生产在线迁移 runbook（本任务标注未实测，不替代上线门禁）。

---

## 7. 验收标准（对齐 claude-plan-P2-01 §10）

- [ ] V022/U022 双方言迁移可执行，`MigrationDialectCompatibilityTest` 通过。
- [ ] 三表 RANGE 月分区，主键含 `created_at`。
- [ ] EXPLAIN 证据：带时间范围查询命中分区裁剪 + 索引。
- [ ] 生产代码不再调用 `findAll()`（grep 证实）。
- [ ] `verify` 分批与一次性结果等价（测试覆盖）。
- [ ] 分区维护 + 归档任务可执行并写审计留痕。
- [ ] `mvn test` 全绿（含 Testcontainers）+ 前端测试全绿。
- [ ] 无明显安全风险（归档不丢审计、不泄露敏感字段）。
- [ ] U022 可回滚。
