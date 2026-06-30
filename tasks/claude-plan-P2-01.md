# P2-01 第一性原理开发计划 — 大表分区归档与查询优化

> 阶段：P2（生产强化）首任务
> 依据：`docs/development-process-workflow.md` §3.3、`tasks/phase-task-checklist.md` §4、`docs/database-design.md` §7、`docs/requirements.md` §3.1（NFR-P04/P07）、`docs/implementation-gap-and-test-plan.md`
> 前置：P0-01~P1-05 全部合入 master（最新提交 `807995e1`，迁移版本 V021）
> 日期：2026-06-30
> 分支：`ai/p2-partition-archive`（建议）

---

## 1. 需求来源

### 1.1 任务口径

| 项 | 内容 |
|---|---|
| 编号 | P2-01 |
| 主题 | 大表分区归档 |
| 依赖 | P0-01（迁移基线）、P0-05（调用日志事实源） |
| 涉及模块 | `db/migration`、`db/migration-dm`、`platform-common`、`platform-pipeline`、`platform-billing` |
| 输出 | 分区/归档脚本 + 查询优化 |
| 通过标准 | **EXPLAIN 命中索引/分区** |

### 1.2 触发事实（来自代码与遗留审查）

1. **`JdbcServiceInvokeLogRepository.findAll()` 全表扫描**：`SELECT * FROM t_service_invoke_log ORDER BY created_at DESC, id DESC`，无 WHERE、无 LIMIT。P0-05 RW-3 已标注"生产不可行，留 P2-01"。调用方：`BillingApplication`（账单聚合）、`StatsController`（统计）、`AsyncInvokeLogWriter.logs()`（调用日志查询，外层再内存分页）。
2. **`JdbcAuditLogRepository.verify()` 全表加载**：哈希链校验时 `SELECT * FROM t_audit_log ORDER BY id`，审计日志 ≥3 年留存，数据量增长后 OOM 风险。
3. **`t_raw_data` 无 JDBC 仓储**：表已建但 `RawDataRepository` 是纯内存实现，重启即丢，属 P0-03 遗留缺口（与本任务相关但不阻断分区工作）。
4. **三张表均无分区**：`db/` 下 grep `PARTITION` 0 命中；`docs/database-design.md §11` 明确"大表分区尚未体现在迁移脚本中，性能验收前需补充分区或分表方案"。
5. **设计已有分区建议但未落地**：`database-design.md §7.2` 给出分区键与周期，但无方言脚本。

### 1.3 现有大表结构（核对结果）

| 表 | 建表版本 | 主键 | 时间列 | 已有索引 | 状态列 |
|---|---|---|---|---|---|
| `t_service_invoke_log` | V005 + V009 + V013 | `id BIGINT` | `created_at`、`log_day` | idx_created_at / idx_service_created / idx_consumer_created / idx_service_consumer / idx_trace / idx_request_hash | `status_code` |
| `t_audit_log` | V008 + V016 | `id BIGINT` | `created_at` | idx_trace / idx_event_type_created / idx_actor / idx_audit_hash | `status` |
| `t_raw_data` | V003 | `id BIGINT` | `created_at` | 无（建表未建索引） | `quality_status` |

> 关键约束：MySQL 与达梦的 RANGE 分区表均要求**分区键必须包含在主键/唯一键中**。当前三表主键为单列 `id`，分区改造必须把主键改为联合主键 `(id, created_at)`。这是本任务最大且不可省略的结构变更。

---

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？

招采验收与生产运行要求：调用日志日均 ≥10 亿条接入规模（NFR-P04）、千万级查询 ≤2s（NFR-P07）、审计日志 ≥3 年留存且可检索、计费按账期聚合。当前全表扫描 + 单表无分区，数据量稍大即 OOM/超时，**性能验收无法通过、生产无法稳定承载**。本质问题不是"加分区"，而是"让高频写大表的查询只扫描它需要的那部分数据"。

### 2.2 最小可行结果

1. 三张大表建立按时间 RANGE 分区，使带时间范围的查询通过分区裁剪只命中目标分区。
2. 全表扫描路径（`findAll`/`verify`/`AsyncInvokeLogWriter.logs`）改造为带时间范围 + SQL 分页的查询，消除 OOM 风险。
3. 归档脚本（冷数据搬出热表）+ 定时清理能力，支撑留存策略。
4. 可验证：`EXPLAIN` 证据显示查询命中分区/索引，并有真实 MySQL（Testcontainers）集成测试。

### 2.3 系统必须接收哪些输入？

- 分区表 DDL（MySQL 基线 + 达梦双方言）。
- 查询的时间范围参数（账期 from/to、审计查询 from/to）。
- 归档配置（保留天数、归档目标、清理批次大小）。

### 2.4 系统必须产生哪些输出？

- 分区表 + 自动维护分区（新增未来分区、归档/删除过期分区）的脚本/调度。
- 改造后的查询 SQL（命中分区裁剪）。
- 归档/清理执行记录（写入审计，留痕）。
- EXPLAIN 证据文档 + 集成测试。

### 2.5 从输入到输出不可省略的处理过程

1. **主键改造**：三表 `PRIMARY KEY (id)` → `(id, created_at)`，否则分区表建不起来。
2. **分区 DDL**：RANGE 分区，按设计建议周期（调用日志按月、审计按月、原始数据按月；月粒度优先于日，避免分区数爆炸，符合"最小可行")。
3. **查询改造**：所有访问大表的路径补时间范围谓词与 SQL LIMIT，去掉全表 `findAll`。
4. **分区维护**：定时任务预创建未来分区、归档/丢弃过期分区（DROP PARTITION 比逐行 DELETE 高效且不产生大量 undo）。
5. **归档**：过期分区数据搬至归档表/对象存储后 DROP（审计日志因合规 ≥3 年保留，仅归档不销毁）。
6. **验证**：Testcontainers MySQL 跑分区 DDL + EXPLAIN 断言 + 数据正确性。

### 2.6 哪些是核心能力？

- 分区表 DDL（双方言）+ 主键改造迁移。
- `findAll`/`verify`/`logs` 查询优化（带时间范围 + SQL 分页）。
- 分区维护与归档调度。

### 2.7 哪些是增强能力？

- 冷数据归档到对象存储（MinIO）——属模块六，本任务只做"搬至归档表 + 留痕"，对象存储对接留后续。
- `t_raw_data` 的 JDBC 仓储落地——属 P0-03 遗留缺口，本任务仅对其表分区，不补全读写路径（避免范围蔓延）。

### 2.8 当前代码库最小改动路径

- **新增迁移 V022**（MySQL + 达梦）：重建三表为分区表（数据迁移：建新分区表 → INSERT SELECT → 重命名替换；或对空开发库直接 DROP+CREATE，生产用在线变更）。配套 U022 回滚。
- **改 `JdbcServiceInvokeLogRepository`**：`findAll` 改为带时间范围的 `findByRange`；账单/统计调用方传入账期；`AsyncInvokeLogWriter.logs` 改走 SQL 分页。
- **改 `JdbcAuditLogRepository.verify`**：分批游标读取（按 id 分批，避免全表加载）。
- **新增 `PartitionMaintainer`**（platform-common）：定时预建/归档/丢弃分区，记录审计。
- **新增归档表 `t_*_archive`**（或同库归档分区）+ 归档任务。

### 2.9 如何测试？

- Testcontainers MySQL 8.0 集成测试：建分区表 → 灌跨分区数据 → EXPLAIN 断言命中分区 → 查询结果正确。
- 达梦：H2 标准模式无法验证分区，沿用 P0-02 策略做"语法静态守护 + CLOB/SMALLINT 约束"，真实达梦分区验证标注"上线前实测"。
- 查询优化单测：`findByRange` 返回正确分页、`verify` 分批结果与一次性结果一致。
- `MigrationDialectCompatibilityTest` 扩展纳入 V022。

### 2.10 如何验收？

- EXPLAIN 证据：带时间范围的查询 `partitions` 列只显示目标分区、`key` 列命中索引。
- 全表扫描路径消除：grep 确认 `findAll()` 不再被生产聚合/查询路径调用（仅测试可保留）。
- `mvn test` 全绿 + Testcontainers 分区集成测试通过。
- 归档/清理任务可执行并写审计留痕。

### 2.11 如何避免过度设计？

- **月分区**而非日分区：日分区 3 年 = 1095 个分区，MySQL 分区数上限与维护成本过高；月分区 36 个，足够裁剪且可控。
- **不做分库分表**：当前业务规模参数缺失（Q-11），单库 RANGE 分区即可满足验收，分库分表留待容量压测（P2-02）后再评估。
- **不引入新中间件**：归档用同库归档表 + 定时任务，不引对象存储新依赖。
- **不动 `t_raw_data` 读写路径**：只分区表结构，不补 JDBC 仓储（独立缺口，单独任务）。

---

## 3. 功能拆解

| 编号 | 任务 | 模块 | 说明 |
|---|---|---|---|
| F-1 | 分区表迁移 V022（MySQL） | db/migration | 三表主键改造 + RANGE 月分区 + 初始分区 + 数据迁移 |
| F-2 | 分区表迁移 V022（达梦） | db/migration-dm | 同构达梦方言 |
| F-3 | 回滚脚本 U022 | db/migration + dm | 回到非分区单列主键表 |
| F-4 | 调用日志查询优化 | platform-common / pipeline / billing | `findAll` → `findByRange`，调用方传账期；`AsyncInvokeLogWriter.logs` SQL 分页 |
| F-5 | 审计哈希链分批校验 | platform-common | `verify` 改分批游标 |
| F-6 | 分区维护任务 | platform-common | `PartitionMaintainer`：预建未来分区 + 归档/丢弃过期分区，写审计 |
| F-7 | 归档表与归档任务 | platform-common | 归档表 DDL（V022 内）+ 归档搬运 + 留痕 |
| F-8 | Testcontainers 分区集成测试 | platform-common/pipeline test | 分区裁剪 + EXPLAIN 断言 + 数据正确性 |
| F-9 | 方言静态守护 | platform-common test | `MigrationDialectCompatibilityTest` 扩展 V022，禁用方言禁忌写法 |
| F-10 | EXPLAIN 证据文档 | docs/perf 或 reviews | 记录命中分区/索引的 EXPLAIN 输出 |

---

## 4. 影响模块

| 模块 | 改动类型 | 风险 |
|---|---|---|
| `db/migration`、`db/migration-dm` | 新增 V022/U022 | 主键改造影响既有 FK/索引；需 checksum 处置（P0-01 策略） |
| `platform-common.log` | `JdbcServiceInvokeLogRepository` 查询改造 | 调用方签名变更 |
| `platform-common.audit` | `JdbcAuditLogRepository.verify` 分批 | 哈希链连续性必须保持 |
| `platform-common`（新增） | `PartitionMaintainer`、归档仓储 | 新代码，低风险 |
| `platform-pipeline.ingest` | `AsyncInvokeLogWriter.logs` 走 SQL 分页 | 行为对齐 |
| `platform-billing` | `BillingApplication`/`StatsController` 改用 `findByRange` | 账期已存在，传入即可 |
| 测试 | 新增 Testcontainers + 扩展守护测试 | 需 Docker 可用 |

---

## 5. 接口设计

### 5.1 仓储查询接口变更

```java
// JdbcServiceInvokeLogRepository
// 废弃 findAll() 用于生产；保留供测试
Page<ServiceInvokeLog> findByRange(Instant from, Instant to, int page, int size);
Page<ServiceInvokeLog> findByServiceRange(String serviceCode, String consumerCode, String status,
                                          Instant from, Instant to, int page, int size);
```

账单/统计调用方：账期 `from/to` 已存在于 `GenerateBillRequest`/统计请求，直接透传。

### 5.2 审计分批校验

```java
// JdbcAuditLogRepository.verify() 内部改为按 id 游标分批读取（每批 N 条），
// 哈希链跨批用上一批末尾 hash 衔接，结果与一次性校验等价。
```

### 5.3 分区维护接口

```java
public interface PartitionMaintainer {
    void ensureFuturePartitions(String table, int monthsAhead);  // 预建未来分区
    void archiveExpiredPartitions(String table, Instant cutoff); // 归档过期分区
    void dropExpiredPartitions(String table, Instant cutoff);    // 丢弃已归档过期分区
}
```

通过 `@Scheduled` 定时触发（可配开关），每次操作写 `t_audit_log` 留痕。

---

## 6. 数据结构

### 6.1 分区 DDL 示例（MySQL，t_service_invoke_log）

```sql
-- 主键改为联合主键（分区键 created_at 必须在主键中）
ALTER TABLE t_service_invoke_log DROP PRIMARY KEY, ADD PRIMARY KEY (id, created_at);
ALTER TABLE t_service_invoke_log
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    ...
    PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

> 生产数据迁移采用"建新分区表 → INSERT SELECT → RENAME 替换"在线变更；开发/测试空库可直接 DROP+CREATE（V022 脚本对已存在数据需幂等处理，由 Codex 在任务单中明确两种路径）。

### 6.2 达梦方言

- 达梦支持 `PARTITION BY RANGE`，但语法与 MySQL 略有差异（分区键表达式限制），`db/migration-dm/V022` 单独编写。
- 沿用 P0-02 策略：`TEXT→CLOB`、`TINYINT→SMALLINT`，避开 `ON DUPLICATE KEY UPDATE`/`AUTO_INCREMENT`。

### 6.3 归档表

```sql
CREATE TABLE t_service_invoke_log_archive ( LIKE t_service_invoke_log 包括索引 );
-- 或采用独立归档分区方案，由 Codex 在任务单二选一
```

### 6.4 分区维护元数据

复用 `t_audit_log` 记录分区操作（event_type=`PARTITION_MAINTAIN`），不新增元数据表，避免过度设计。

---

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 分区键 created_at 为 NULL | 三表 created_at 均 NOT NULL，DDL 保证；迁移时校验 |
| 数据落入 pmax 分区（未及时预建） | `ensureFuturePartitions` 定时预建；REORGANIZE pmax 拆分 |
| 归档过程中查询 | 归档搬至归档表后 DROP PARTITION，查询走热表不受影响 |
| 哈希链分批校验跨批衔接 | 游标用上一批末尾 id + hash，等价性由测试覆盖 |
| 分区表主键改造影响既有索引 | V022 重建相关索引；EXPLAIN 验证 |
| 达梦分区语法差异 | 静态守护 + 上线前真实达梦实测（标注未实测） |
| 已执行旧 V022 的库 | flyway:repair（P0-01 策略） |
| Testcontainers 不可用（无 Docker） | 集成测试可跳过但须标注，EXPLAIN 证据改由本地 MySQL 手工补 |

---

## 8. 测试策略

1. **Testcontainers MySQL 集成测试**（platform-common 或 pipeline）：
   - 启 MySQL 8.0 容器 → 跑 V001~V022 → 灌入跨 3 个月分区数据 →
   - 断言 `EXPLAIN SELECT ... WHERE created_at BETWEEN ?` 的 `partitions` 列只含目标分区、`key` 非空。
   - 断言查询结果与未分区语义一致。
2. **审计分批校验等价性测试**：构造 N 条哈希链，分批 verify 与一次性 verify 结果一致。
3. **调用日志范围查询测试**：`findByRange` 分页正确、账期聚合金额与全量一致（对齐 P0-06 聚合一致性）。
4. **分区维护测试**：`ensureFuturePartitions`/`dropExpiredPartitions` 行为 + 审计留痕。
5. **方言守护**：`MigrationDialectCompatibilityTest` 纳入 V022，断言无禁忌写法。
6. **回归**：`mvn test` 全量 + 前端 `npm run test:unit`。

---

## 9. Codex 实现边界

Codex 须在 `tasks/codex-task-P2-01.md` 中实现，且**仅限**：

1. V022/U022（MySQL + 达梦）：三表主键改造 + RANGE 月分区 + 归档表。
2. `JdbcServiceInvokeLogRepository`：新增 `findByRange`/`findByServiceRange`，`findAll` 标 `@Deprecated` 仅供测试；改造调用方（billing/stats/AsyncInvokeLogWriter）传时间范围。
3. `JdbcAuditLogRepository.verify` 分批游标改造。
4. `PartitionMaintainer` + 归档任务 + `@Scheduled`（可配开关，默认开发环境关闭）。
5. Testcontainers 分区集成测试 + 方言守护扩展。
6. EXPLAIN 证据文档。

**不得做**：
- 不补 `t_raw_data` 的 JDBC 仓储读写路径（独立缺口）。
- 不引入对象存储/新中间件。
- 不做分库分表。
- 不修改 `.env`/生产配置/密钥。
- 不重构无关模块。

---

## 10. 验收标准

- [ ] V022/U022 双方言迁移可执行，`MigrationDialectCompatibilityTest` 通过。
- [ ] 三表为 RANGE 月分区，主键含 `created_at`。
- [ ] EXPLAIN 证据：带时间范围查询命中分区裁剪 + 索引（文档附输出）。
- [ ] `findAll()` 不再被生产聚合/查询路径调用（grep 证实）。
- [ ] `verify` 分批校验与一次性结果等价（测试覆盖）。
- [ ] 分区维护 + 归档任务可执行并写审计留痕。
- [ ] `mvn test` 全绿（含 Testcontainers 分区测试）+ 前端测试全绿。
- [ ] 无明显安全风险（归档不丢审计、不泄露敏感字段）。
- [ ] 可回滚（U022 验证）。

---

## 11. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| 主键改造破坏既有索引/FK | 高 | V022 重建索引；Testcontainers + EXPLAIN 验证；U022 回滚 |
| 生产在线数据迁移风险 | 高 | V022 提供"建新表+INSERT SELECT+RENAME"路径；生产变更走单独 runbook，开发/测试用空库路径 |
| 哈希链分批衔接错误 | 中 | 等价性测试；跨批 hash 衔接 |
| 达梦分区语法差异 | 中 | 静态守护 + 上线前真实达梦实测，标注未实测 |
| Testcontainers 依赖 Docker | 中 | 无 Docker 时标注跳过，手工补 EXPLAIN 证据 |
| 分区数膨胀 | 低 | 月分区 + 定时归档 DROP 过期分区 |
| 改动破坏 P0/P1 闭环 | 中 | 全量回归 mvn test + 前端；账单/统计聚合一致性回归 |

**回滚**：U022 将三表还原为非分区单列主键表；代码侧 `findByRange` 保留（非分区表同样可用），`verify` 分批逻辑可保留。回滚后查询仍正确，仅失去分区裁剪能力。

---

## 12. 下一步

本计划通过后，生成 `tasks/codex-task-P2-01.md`（Codex 实现任务单），按 F-1~F-10 拆解为可执行步骤，派发 Codex 实现。
