# Claude Code 返工复审 — P2-01 大表分区归档

## 1. 审查对象

- 任务：P2-01 返工（`reviews/claude-review-P2-01.md` §8 返工清单）
- 分支：`ai/p2-partition-archive`（改动在工作区未提交）
- 审查日期：2026-06-30
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（持久化/迁移 + 金额聚合 + 审计防篡改，必做）

### 返工项

| 编号 | 问题 | 返工要求 | 状态 |
|---|---|---|---|
| P1-1 | 审计日志 DROP PARTITION 破坏哈希链 | 审计只归档不 DROP，补归档后 verify intact 测试 | ✅ 已修复 |
| P2-1 | 归档无幂等保护 | 归档去重 + 重复归档测试 | ✅ 已修复 |
| P2-2 | findAllByRange 聚合全量加载 | 明确标注局限或改 SQL 聚合 | ✅ 已标注 |
| P2-3 | `/*!80000` 方言守护双向断言缺失 | 补双向断言 | ✅ 已修复 |

## 2. 返工核查

### 2.1 P1-1 审计日志只归档不 DROP

- `PartitionMaintenanceJob.maintain()`（:24-27）：对 `t_service_invoke_log` 调 archive+drop；对 `t_audit_log` **仅调 `archiveExpiredPartitions`，无 `dropExpiredPartitions`**。✓
- 新增 `PartitionMaintenanceJobTest.auditLogIsArchivedButNeverDropped`（:14-25）：用 RecordingMaintainer 断言 `archive:t_audit_log` 调用、`drop:t_audit_log` **不**调用、`t_service_invoke_log` 仍 archive+drop、`t_raw_data` 仅 ensure。✓
- 安全保证链：审计主表数据不被物理删除 → 哈希链 `prev_hash` 串联完整 → `verify()` 分批扫描主表正常。归档仅复制到 `t_audit_log_archive`，不动主表。✓

### 2.2 P2-1 归档幂等

- `JdbcPartitionMaintainer.archiveExpiredPartitions`（:52-62）：`INSERT INTO archive SELECT source.* FROM source WHERE created_at<? AND NOT EXISTS (SELECT 1 FROM archive WHERE archive.id=source.id)`。✓
- 新增 `archiveIsIdempotentWhenRetried` 测试（:33-43）：连续两次归档，归档表仍 1 行。✓
- 测试 `createTables` 补归档表 `uk_invoke_log_archive_id` 唯一索引（:103）作双保险。

### 2.3 P2-2 findAllByRange 局限标注

- `docs/perf/p2-01-explain-evidence.md` :39 明确标注："Current billing/statistics aggregation still materializes the selected billing window through findAllByRange. This removes full-table scans but can still be memory-heavy for very large billing periods. A follow-up production hardening task should move those aggregations to SQL GROUP BY/streaming aggregation." ✓
- 符合返工任务单"明确标注局限"要求；SQL 层聚合列后续任务。

### 2.4 P2-3 方言守护双向断言

- `MigrationDialectCompatibilityTest.migrationScriptsAvoidBlockedDialectFeatures`（:62-65）：
  - `assertFalse(contains(dmVersionSql, "/*!80000"))` ✓
  - `assertTrue(contains(commonVersionSql, "/*!80000"))` ✓
  - `assertTrue(contains(commonVersionSql, "PARTITION BY RANGE COLUMNS"))` ✓
  - `assertFalse(contains(commonVersionSql, "TO_DAYS"))` ✓

### 2.5 附带修复 P3-3

- `AuditLogRepositoryTest.verifyKeepsHashContinuityAcrossBatches`（:88）改为 2505 条，跨 3 批（>2000，`VERIFY_BATCH_SIZE=1000`），覆盖多批次边界。✓

## 3. 对抗式审查（返工后）

### 3.1 攻击面枚举

1. 审计归档后 verify 是否仍 intact（P1-1 核心保证）。
2. `NOT EXISTS` 去重是否真幂等（跨重跑、跨 cutoff 变化）。
3. archive 与 drop 顺序/原子性（invoke_log 先 archive 后 drop，archive 失败是否丢数据）。
4. 返工是否引入新缺陷。

### 3.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| 审计归档后 verify broken | 审计不 DROP，主表完整；归档仅复制；`verify()` 分批扫主表，链连续。`PartitionMaintenanceJobTest` 验证不调 drop | 已反驳 |
| NOT EXISTS 跨重跑仍重复 | 第一次归档后 archive 有该 id，第二次 NOT EXISTS 过滤；`archiveIsIdempotentWhenRetried` 测试证实归档表仍 1 行 | 已反驳 |
| NOT EXISTS 跨 cutoff 变化（cutoff 扩大后旧已归档+新未归档） | 新 cutoff 范围内已归档 id 被 NOT EXISTS 跳过，未归档 id 新插入；逻辑自洽 | 已反驳 |
| archive 失败后 drop 丢数据（invoke_log） | `archiveExpiredPartitions` 的 `jdbcTemplate.update` 抛异常会中断 `maintain()`，后续 `dropExpiredPartitions` 不执行；异常传播保护 | 已反驳 |
| 归档表无 id 唯一索引（生产 V022）与测试不一致 | 生产 V022 归档表主键 `(id,created_at)` 无 `id` 单列唯一索引，测试 `createTables` 加了 `uk_invoke_log_archive_id`。但 `NOT EXISTS` 去重不依赖该索引，逻辑自足；唯一索引仅双保险 | 已反驳（建议生产补，P3-6） |
| t_raw_data 只 ensure 不归档不 drop，持续增长 | 符合任务单"t_raw_data 无读写路径只分区"；表无生产写入路径（P0-03 遗留），暂无增长压力 | 已反驳（设计权衡） |
| 审计主表永不收缩，verify 扫全表性能 | 审计 ≥3 年合规留存要求不可删；verify 已分批游标避免 OOM；性能属 P2-2 同类已知局限 | 已反驳（标注局限） |

### 3.3 存活缺陷

**无 P1 阻断、无 P2 改进。** 仅 1 项 P3 提示：

- **P3-6（提示）**：生产 V022 归档表 `t_service_invoke_log_archive`/`t_audit_log_archive` 主键 `(id, created_at)` 但无 `id` 单列唯一索引，而测试 `JdbcPartitionMaintainerTest.createTables:103` 加了 `uk_invoke_log_archive_id`。`NOT EXISTS` 去重逻辑自足不依赖该索引，但建议生产脚本补 `id` 唯一索引与测试对齐，作并发场景双保险。不阻断合入。

### 3.4 对"建议通过"的反驳

- 为何不应通过？审计防篡改（P1-1）是否真修复？→ `maintain()` 对审计不调 drop 已由 `PartitionMaintenanceJobTest` 直接断言；归档不动主表，哈希链完整。✓
- 归档幂等是否仅测试断言而无真实语义？→ `NOT EXISTS` SQL 已追读，`archiveIsIdempotentWhenRetried` 真实执行两次归档证实。✓
- archive-drop 原子性是否未覆盖？→ 异常传播分析证实 archive 失败则 drop 不执行。✓
- 反驳未发现存活阻断项，结论成立。

## 4. 测试验证

```text
mvn test -pl platform-common,platform-billing,platform-pipeline -am
- platform-common:  Tests run: 39, Failures: 0, Errors: 0, Skipped: 1
    （Skipped 1 = LargeTablePartitionIntegrationTest，assumeTrue 默认跳过，P3-1）
- platform-quality: Tests run: 35, Failures: 0, Errors: 0
- platform-pipeline:Tests run: 113,Failures: 0, Errors: 0
- platform-billing: Tests run: 53, Failures: 0, Errors: 0
BUILD SUCCESS
```

新增测试：`PartitionMaintenanceJobTest`(1)、`archiveIsIdempotentWhenRetried`、`verifyKeepsHashContinuityAcrossBatches`(2505 条)。既有测试无回归。

## 5. 未实测项（沿用初次审查）

1. 真实达梦 DM8 分区语法（V022 达梦分区语句仍注释，仅主键+归档表落地）。
2. `ensureFuturePartitions`/`dropExpiredPartitions` 真实 MySQL 执行（测试用 RecordingMaintainer 录制 SQL 字符串）。
3. `LargeTablePartitionIntegrationTest` 默认 `assumeTrue` 跳过（P3-1），需 `-DrunPartitionIT=true` 才执行 EXPLAIN 断言。
4. 真实达梦不支持 `INFORMATION_SCHEMA.PARTITIONS`+`DATABASE()`，分区维护仅 MySQL 可用（P3-4）。

以上均为初次审查已标注的未实测项，返工未恶化，留上线前门禁。

## 6. 审查结论

**建议通过。**

- P1-1（审计防篡改）已修复：审计日志只归档不 DROP，哈希链完整，`PartitionMaintenanceJobTest` 直接验证。
- P2-1（归档幂等）已修复：`NOT EXISTS` 去重 + 重复归档测试。
- P2-2（findAllByRange 局限）已在证据文档明确标注，SQL 聚合列后续任务。
- P2-3（`/*!80000` 方言守护）已补双向断言。
- 附带修复 P3-3（verify 分批 2505 条跨 3 批）。
- 对抗式审查已尝试反驳（归档后 verify、NOT EXISTS 幂等、archive-drop 原子性、归档表索引不一致），未发现存活 P1/P2 缺陷。
- 存活 1 项 P3 提示（归档表 id 唯一索引建议生产补）不阻断。

## 7. 后续建议（非阻断）

1. P3-6：生产 V022 归档表补 `id` 单列唯一索引，与测试对齐。
2. P3-1：CI 启用 `-DrunPartitionIT=true` 或改 `@EnabledIfSystemProperty`，让分区裁剪 EXPLAIN 守护默认执行。
3. P3-4：达梦分区维护等价实现或标注"分区维护仅支持 MySQL"。
4. P2-2 后续：账单/统计聚合改 SQL 层 `GROUP BY`/流式聚合，消除 `findAllByRange` 全量加载。
5. 上线前门禁：真实达梦分区实测、`ensure/drop` 真实 MySQL 执行验证、生产在线 V022 迁移 runbook。

返工改动可提交 `ai/p2-partition-archive` 并合并 master。
