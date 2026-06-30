# Claude Code 审查结果 — P2-01 大表分区归档

## 1. 审查对象

- 任务：P2-01 大表分区归档与查询优化
- 分支：`ai/p2-partition-archive`（改动在工作区未提交）
- 任务单：`tasks/codex-task-P2-01.md`，计划：`tasks/claude-plan-P2-01.md`
- 审查日期：2026-06-30
- 改动范围：V022/U022 双库迁移、`JdbcServiceInvokeLogRepository` 查询优化、`JdbcAuditLogRepository.verify` 分批、`JdbcPartitionMaintainer`+`PartitionMaintenanceJob`、`BillGenerator`/`RegulatoryReportService`/`StatsAggregatorJobHandler` 改 `BiFunction`、`AsyncInvokeLogWriter.logs` SQL 分页、Testcontainers 分区测试、方言守护扩展、EXPLAIN 证据
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（持久化/迁移 + 金额聚合 + 审计防篡改，必做）

## 2. Git 状态

20 文件修改 + 10 文件新增，与任务单 F-1~F-10 对齐，无越界模块改动。

## 3. 常规审查

| 项 | 结论 |
|---|---|
| F-1/F-2 分区迁移 V022 双库 | MySQL 用 `/*!80000 ... */` 条件注释隔离 H2/MySQL，主键改造 `(id,created_at)` + RANGE COLUMNS 月分区 + 2 张归档表；达梦仅主键改造+归档表，分区语句注释待实测。✓ |
| F-3 回滚 U022 双库 | MySQL REMOVE PARTITIONING + 主键还原 + 删归档表；达梦注释说明。✓ |
| F-4 调用日志查询优化 | 新增 `findByRange`/`findByServiceRange`/`findAllByRange`，`findAll` 标 `@Deprecated`；`BillGenerator` 等 `Supplier`→`BiFunction<Instant,Instant>`；`AsyncInvokeLogWriter.logs()` 默认最近 30 天。✓ |
| F-5 审计 verify 分批 | 按 id 游标 `WHERE id > ? LIMIT 1000`，跨批 hash 衔接。✓ |
| F-6 分区维护 | `JdbcPartitionMaintainer` + `@Scheduled` + `@ConditionalOnProperty` 默认关 + 写审计。✓ |
| F-7 归档表 | `t_service_invoke_log_archive`、`t_audit_log_archive`。✓ |
| F-8 Testcontainers 测试 | `LargeTablePartitionIntegrationTest` 真用 MySQL 8.0.36，EXPLAIN 断言 partitions+key。⚠ 默认 `assumeTrue` 跳过 |
| F-9 方言守护 | V022 纳入双库，断言主键含 created_at + 归档表存在。⚠ `/*!80000` 双向断言缺失 |
| F-10 EXPLAIN 证据 | `docs/perf/p2-01-explain-evidence.md` 含三表 EXPLAIN 输出。✓ |

## 4. 对抗式审查

### 4.1 攻击面枚举

1. 分区表主键改造破坏既有索引/哈希链。
2. `verify()` 分批后哈希链跨批衔接错误。
3. `dropExpiredPartitions` 对审计日志 DROP PARTITION 破坏哈希链防篡改。
4. `archiveExpiredPartitions` 重复归档导致归档表数据重复。
5. `findAllByRange` 全量加载账期数据仍 OOM。
6. 分区维护 SQL 注入面。
7. `ensureFuturePartitions` REORGANIZE pmax 语义。
8. 达梦分区未实测。

### 4.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| verify 分批跨批 hash 衔接 | `previousHash` 跨批传递，`AuditHashing.hash(prevHash,event)` 链式；测试 1005 条跨 1 次边界 intact | 已反驳（边界覆盖仅 1 次，P3） |
| **DROP PARTITION 破坏审计哈希链** | `PartitionMaintenanceJob.maintain()` 对 `t_audit_log` 执行 `dropExpiredPartitions`；`verify()` 从 `id>0` 扫描，早期分区被删后第一条可见事件 `prevHash` 指向已删除事件，`previousHash=""` 不匹配 → `prev_mismatch`，**verify 永久报 broken** | **存活 P1 阻断** |
| 归档重复 INSERT | `archiveExpiredPartitions` 用 `INSERT INTO archive SELECT * WHERE created_at<cutoff`，无幂等保护；归档表无唯一约束；若 DROP 失败或重跑，归档表数据重复 | 存活 P2 |
| findAllByRange 全量加载 | `findAllByRange` 分页取回所有范围内记录入 `List`，账单/统计聚合仍把整个账期数据塞内存；账期跨月+数据量大时 OOM（比原 findAll 全表好，但聚合场景未根本解决） | 存活 P2 |
| SQL 注入（drop/archive/ensure） | `table` 白名单 `requireAllowed`，`partition` 来自 INFORMATION_SCHEMA 且校验 `startsWith("p")`，`cutoff` 参数化；测试 `rejectsUnsupportedTableName` 覆盖 | 已反驳 |
| ensureFuturePartitions REORGANIZE | 逐月 `REORGANIZE pmax INTO (p<month>, pmax)`，已存在分区跳过；逻辑正确 | 已反驳 |
| 主键改造破坏既有索引 | V022 重建主键，既有二级索引保留；EXPLAIN 证据显示 key 命中 | 已反驳 |
| 账单聚合金额一致性 | `BiFunction` 传 from/to，`BillGenerator.generate` 仍 filter 双保险；`aggregationMatchesMultipleInvokes` 3 条×1.00=3.0000 通过 | 已反驳 |
| 达梦分区语法 | 达梦 V022 分区语句被注释，仅主键+归档表；守护不断言分区；真实 DM8 未实测 | 未实测（已标注） |

### 4.3 存活缺陷

#### P1 阻断（1 项）

**P1-1 审计日志 DROP PARTITION 破坏哈希链防篡改**
- 位置：`PartitionMaintenanceJob.java:24-26` + `JdbcPartitionMaintainer.dropExpiredPartitions:58-73`
- 场景：生产开启 `platform.partition.maintain.enabled=true` 后，定时任务对 `t_audit_log` 执行 `archiveExpiredPartitions`（行级搬至归档表）+ `dropExpiredPartitions`（DROP PARTITION 物理删除主表分区）。
- 后果：`t_audit_log` 哈希链是 `prev_hash` 串联的 append-only 链。DROP 早期分区后，主表第一条可见事件的 `prevHash` 指向已删除事件，而 `verify()` 的 `previousHash` 初值为 `""` → 立即 `prev_mismatch` → `verify()` 永久报 broken。归档表是独立表，不参与主表哈希链，无法恢复链连续性。
- 违反：NFR-S02 审计日志 ≥3 年不可篡改 + CLAUDE.md §7.1 安全/防篡改场景。审计日志被物理删除违反"不可篡改、可追溯"。
- 修复建议：`t_audit_log` **只归档不 DROP**。`PartitionMaintenanceJob.maintain()` 对 `t_audit_log` 仅调用 `archiveExpiredPartitions`，不调用 `dropExpiredPartitions`；或审计日志不纳入分区维护 DROP 范围。若需控制审计表体积，改为"归档表+主表保留全量"或"仅对调用日志/原始数据 DROP，审计日志永不 DROP"。

#### P2 改进（3 项）

**P2-1 归档无幂等保护**
- `JdbcPartitionMaintainer.archiveExpiredPartitions:52` 用 `INSERT INTO archive SELECT * WHERE created_at<cutoff`，无 `NOT EXISTS` 去重或归档表唯一约束。重跑或 DROP 失败时归档表数据重复。
- 修复：归档表加 `(id)` 唯一约束，或归档 SQL 加 `WHERE NOT EXISTS`，或归档后立即 DROP 同分区并记录已归档标记。

**P2-2 findAllByRange 聚合场景仍全量加载**
- `JdbcServiceInvokeLogRepository.findAllByRange:60-71` 分页取回范围内全部记录入内存 List，供 `BillGenerator`/`StatsAggregator` 聚合。账期数据量大时仍 OOM，只是从"全表"缩到"账期范围"。
- 修复：聚合改用 SQL 层 `GROUP BY` + `COUNT/SUM`，而非取回全量 Java stream。属较大重构，可列后续任务，但需明确当前 `findAllByRange` 在大账期下不满足 NFR-P04 生产规模。

**P2-3 `/*!80000` 方言守护双向断言缺失**
- `MigrationDialectCompatibilityTest` 未断言"达梦目录不含 `/*!80000`"和"MySQL 目录含 `/*!80000`"。V022 MySQL 脚本大量依赖该条件注释隔离 H2/MySQL，守护空白意味着未来达梦脚本误用 `/*!80000` 不会被拦截。
- 修复：守护测试补 `assertFalse(dmSql, "/*!80000")` + `assertTrue(commonSql, "/*!80000")`。

#### P3 提示（4 项，不阻断）

- **P3-1**：`LargeTablePartitionIntegrationTest` 用 `assumeTrue(runPartitionIT)` 默认跳过，分区裁剪 EXPLAIN 守护在默认构建不执行。建议 CI 加 `-DrunPartitionIT=true` 或改 `@EnabledIfSystemProperty`。
- **P3-2**：`t_raw_data` EXPLAIN 只断言 partitions 不断言 key（无索引，符合任务单），但证据文档 key=null 应明确"无索引是预期"。
- **P3-3**：`verify` 分批测试仅 1005 条跨 1 次边界，未达 >2 批。建议补 2500+ 条跨 3 批。
- **P3-4**：`JdbcPartitionMaintainer` 用 `INFORMATION_SCHEMA.PARTITIONS`+`DATABASE()` 是 MySQL 专有语法，达梦不支持。生产若用达梦，分区维护不可用。需达梦等价实现或标注"分区维护仅支持 MySQL"。
- **P3-5**：`JdbcServiceInvokeLogRepository.findAll()` 仅 `@Deprecated` 未删；`BillingController`/`StatsController`/`IngestService` 其他仓储 `findAll()` 仍在生产路径（非本次大表，属既有，不阻断）。

### 4.4 对"建议通过"的反驳

- 为何不应通过？审计防篡改是 §7.1 必查场景，P1-1 直接导致 `verify()` 永久 broken，生产开启分区维护即破坏审计链。**存在存活 P1 阻断项，不得通过。**

## 5. 测试验证

```text
mvn test -pl platform-common,platform-billing,platform-pipeline -am
- platform-common:  Tests run: 30,  Failures: 0, Errors: 0
- platform-quality: Tests run: 35,  Failures: 0, Errors: 0
- platform-pipeline:Tests run: 113, Failures: 0, Errors: 0
- platform-billing: Tests run: 53,  Failures: 0, Errors: 0
BUILD SUCCESS
```

默认构建全绿。但 `LargeTablePartitionIntegrationTest`（分区裁剪核心证据）默认 `assumeTrue` 跳过，未在默认构建执行（P3-1）。

## 6. 未实测项

1. 真实达梦 DM8 分区语法（V022 达梦分区语句被注释，仅主键+归档表落地）。
2. `ensureFuturePartitions`/`dropExpiredPartitions` 真实 MySQL 执行（测试用 `RecordingMaintainer` 录制 SQL 字符串，未真实执行 REORGANIZE/DROP PARTITION）。
3. 归档+DROP 联动数据完整性（无测试验证 archive 后 drop 不丢归档表数据）。
4. `findAllByRange` 大数据量内存压力。

## 7. 审查结论

**需返工。**

存在 1 项 P1 阻断（审计日志 DROP PARTITION 破坏哈希链），违反 NFR-S02 审计防篡改。3 项 P2 改进需处理。

## 8. 返工任务清单

### 必修（P1 阻断）

1. **P1-1**：`PartitionMaintenanceJob.maintain()` 对 `t_audit_log` 移除 `dropExpiredPartitions` 调用（审计日志只归档不销毁）。补测试：归档后 `verify()` 仍 intact。同时确认 `t_service_invoke_log`/`t_raw_data` 的 DROP 不影响审计链。

### 建议修（P2 改进）

2. **P2-1**：归档幂等——归档表加 `id` 唯一约束或归档 SQL 加去重，补重复归档测试。
3. **P2-2**：明确 `findAllByRange` 聚合场景的内存局限，列为已知限制或改 SQL 层聚合（可单独任务，但需在返工中明确标注）。
4. **P2-3**：`MigrationDialectCompatibilityTest` 补 `/*!80000` 双向断言。

### 可选（P3 提示，不阻断合入）

5. P3-1：CI 启用 `runPartitionIT` 或改 `@EnabledIfSystemProperty`。
6. P3-3：verify 分批测试补 2500+ 条跨 3 批。
7. P3-4：达梦分区维护等价实现或标注仅 MySQL 支持。
