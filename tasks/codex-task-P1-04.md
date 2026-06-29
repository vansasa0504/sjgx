# Codex 执行任务 - P1-04：监管报表

> 阶段：P1（验收增强）
> 任务编号：P1-04
> 分支：`ai/p1-regulatory-report`（P1-03 合并 master 后从 master 切出）
> 依据：`tasks/claude-plan-P1-04.md`（第一性原理计划，权威）、`docs/requirements.md` §2.8（FR-801~805）、`docs/development-process-workflow.md` §6.8
> 前置：P0-05（invoke log 事实源）已合入 master；P1-03 审查通过
> 日期：2026-06-29

---

## 1. 背景与目标

现有 `/api/v1/stats/reports` 端点即时调用 `ReportGenerator` 生成 xlsx 写到本地 `target/reports` 目录，**无 DB 持久化**（重启丢失）、报文内容不入库、`RegulatoryReportingAdapter`/`MockRegulatoryReportingAdapter` 已存在但**未装配未调用**（回执不入库）、**无脱敏**、无列表/详情查询。验收要求"报文和回执入库"+ 脱敏 + 审计可追溯。

监管报送具体标准待 Q-05 明确（`docs/requirements.md` 第226行），本期做**框架 + mock 报送**：报文持久化 + 脱敏 + 报送回执入库 + 审计。

**最小可行结果**：
1. 监管报文持久化（`t_regulatory_report`）。
2. 按类型（COMPLIANCE/DATA_SOURCE/PERSONAL_INFO）+ 时间范围从 invoke log 聚合生成报文。
3. 个人信息类报表脱敏。
4. 报送回执入库（mock 适配器）。
5. 报告列表/详情查询 + 报文导出（Blob）。
6. 持久化重启可查 + 生成/报送审计事件。

## 2. 范围

### 本次实现

- **F-1 报告表 + 仓储**：新增 `t_regulatory_report(report_type, period_from, period_to, content, status, receipt_no, receipt_message, generated_at, submitted_at)` + JdbcRegulatoryReportRepository + InMemoryRegulatoryReportRepository。
- **F-2 报文生成**：RegulatoryReportService.generate(reportType, from, to) 从 `JdbcServiceInvokeLogRepository`（或 findAll 供应器）聚合（invokeCount/successCount/failCount/details）→ 结构化报文 JSON（ObjectMapper 序列化）→ 持久化（status=PENDING）。
- **F-3 脱敏**：PERSONAL_INFO 类对 `consumerCode`（首末各 1 掩码）、`apiKey`（全掩码）、`traceId`（前 4 后 4）掩码；COMPLIANCE/DATA_SOURCE 不脱敏。
- **F-4 报送回执**：调用 `RegulatoryReportingAdapter.submit` → 回执持久化（success→status=SUBMITTED+receiptNo，failure→status=FAILED+message）；适配器失败**不抛异常**，记 FAILED。
- **F-5 报告查询**：list（按 reportType）+ detail（by id，不存在 REGULATORY-404）。
- **F-6 导出**：GET /stats/reports/{id}/export 返回报文 content（Blob，Content-Disposition attachment）。
- **F-7 端点**：
  - `POST /api/v1/stats/reports/generate`（billing:run）body: {reportType, from?, to?} → RegulatoryReportRecord
  - `GET /api/v1/stats/reports`（stats:view）query: reportType → List<RegulatoryReportRecord>
  - `GET /api/v1/stats/reports/{id}`（stats:view）→ RegulatoryReportRecord
  - `GET /api/v1/stats/reports/{id}/export`（stats:view）→ Blob
- **F-8 审计**：生成写 `REGULATORY_REPORT_GENERATE`、报送写 `REGULATORY_REPORT_SUBMIT` 审计事件（含 trace_id）。

### 不做

- 不做真实监管对接/SM4 加密报送（Q-05 待定，mock 框架）。
- 不做加密报文存储（content 存脱敏后明文 JSON）。
- 不做报表模板可视化配置（固定三类型）。
- 不做定时报送（手动触发）。
- 不重构既有 `ReportGenerator`/`/stats/reports` 即时 xlsx 端点（新增独立 `/generate` 链路，最小侵入）。
- 不新增权限码（生成复用 `billing:run`，查询/导出复用 `stats:view`）。
- 前端展示可选（后端端点先就绪）。

## 3. 必读输入

- `AGENTS.md`、`tasks/claude-plan-P1-04.md`（权威计划）
- `docs/requirements.md` §2.8（FR-801~805）、Q-05（第226行）
- `platform-billing/src/main/java/.../report/ReportType.java`、`ReportGenerator.java`、`GeneratedReport.java`（既有，不重构）
- `platform-billing/src/main/java/.../regulatory/RegulatoryReportingAdapter.java`、`RegulatorySubmitResult.java`、`MockRegulatoryReportingAdapter.java`（已存在，需装配）
- `platform-billing/src/main/java/.../StatsController.java`、`BillingApplication.java`
- `platform-billing/src/main/java/.../stats/AuditTraceService.java`、`JdbcStatsSnapshotRepository.java`（JDBC 模板）
- `platform-quality/src/main/java/.../report/JdbcQualityReportRepository.java`、`QualityReportService.java`（P1-03 持久化+导出+ObjectMapper 模板，**重点参考**）
- `platform-common/src/main/java/.../log/JdbcServiceInvokeLogRepository.java`（事实源）
- `platform-common/src/main/java/.../audit/AuditEvent.java`、`AuditLogRepository.java`
- `platform-common/src/main/java/.../security/PermissionCodes.java`、`exception/BusinessException.java`
- `db/migration/V019__quality_report.sql` + U019（最新表模板）、`db/migration/V013__service_invoke_log_fact_source.sql`
- `platform-common/src/test/java/.../db/MigrationDialectCompatibilityTest.java`

## 4. 需要修改的模块

| 文件 | 改动 |
|---|---|
| 新增 `RegulatoryReportRecord.java` | 报告模型 record |
| 新增 `RegulatoryReportRepository.java` + Jdbc/InMemory 实现 | 报告仓储 |
| 新增 `RegulatoryReportService.java` | 生成/脱敏/报送/查询/导出 |
| `StatsController.java` | generate/list/detail/export 端点 + ObjectMapper 序列化 |
| `BillingApplication.java` | 新仓储/服务/`MockRegulatoryReportingAdapter` Bean 装配 |
| `db/migration/V020__regulatory_report.sql` + U020 | t_regulatory_report（MySQL: content LONGTEXT） |
| `db/migration-dm/V020__regulatory_report.sql` + U020 | 达梦版（content CLOB） |
| `MigrationDialectCompatibilityTest.java` | 纳入 V020 + t_regulatory_report CRUD |
| 新增 `RegulatoryReportServiceTest.java`、`RegulatoryReportRepositoryJdbcTest.java` | 生成/脱敏/回执/持久化测试 |
| `BillingModuleMockMvcTest.java` | generate/list/detail/export MockMvc + 脱敏断言 |
| `StatsControllerTest.java` | 构造器对齐新依赖 |

## 5. 数据库/API 影响

### 5.1 数据库
- 新增 `t_regulatory_report(id, report_type, period_from, period_to, content, status, receipt_no, receipt_message, generated_at, submitted_at)` + idx_regulatory_report_type。
- V020/U020 MySQL + 达梦双库；content MySQL `LONGTEXT`、达梦 `CLOB`；避开 ` LIMIT `/DM ` TEXT`(带空格)/` TINYINT ` 方言守护。

### 5.2 API
- `POST /api/v1/stats/reports/generate`（billing:run）→ RegulatoryReportRecord
- `GET /api/v1/stats/reports`（stats:view）→ List<RegulatoryReportRecord>
- `GET /api/v1/stats/reports/{id}`（stats:view）→ RegulatoryReportRecord
- `GET /api/v1/stats/reports/{id}/export`（stats:view）→ Blob（报文 content）

## 6. 实现细节约束

### 6.1 报告模型
```java
public record RegulatoryReportRecord(
    long id, String reportType, Instant periodFrom, Instant periodTo,
    String content, String status, String receiptNo, String receiptMessage,
    Instant generatedAt, Instant submittedAt) {}
```
- reportType: COMPLIANCE/DATA_SOURCE/PERSONAL_INFO
- status: PENDING/SUBMITTED/FAILED

### 6.2 生成逻辑
- RegulatoryReportService 注入 invoke log 供应器（`Supplier<List<ServiceInvokeLog>>`，与 BillGenerator 一致）+ RegulatoryReportingAdapter + RegulatoryReportRepository + AuditLogRepository。
- generate(reportType, dimensionValue, from, to)：
  1. 校验 reportType（非法抛 REGULATORY-400）；解析 from/to（捕获 DateTimeException 抛 REGULATORY-400，借鉴 P1-03 P2-2）。
  2. 过滤 invoke log（可选时间范围）。
  3. 聚合：invokeCount=匹配数，successCount=statusCode 2xx 数，failCount=其余；details=[{traceId,serviceCode,consumerCode,status}]。
  4. 脱敏（仅 PERSONAL_INFO）。
  5. ObjectMapper 序列化报文 JSON（含 JavaTimeModule，禁用 WRITE_DATES_AS_TIMESTAMPS，借鉴 P1-03 P2-1）；序列化失败抛 REGULATORY-500。
  6. 持久化（status=PENDING）。
  7. 调用 adapter.submit → 回执；更新记录（success→SUBMITTED+receiptNo+submittedAt，failure→FAILED+message）。
  8. 写审计事件（GENERATE + SUBMIT，trace_id）。
  9. 返回 RegulatoryReportRecord。
- 无 invoke log 则 0 统计报文，仍持久化 + 报送。

### 6.3 脱敏
- 工具方法：`maskMiddle(value)`（保留首末各 1，中间 `***`）、`maskAll()`、`maskHeadTail(value, head, tail)`。
- PERSONAL_INFO：consumerCode→maskMiddle，apiKey→maskAll，traceId→maskHeadTail(4,4)。
- COMPLIANCE/DATA_SOURCE：不脱敏。

### 6.4 报送回执
- `MockRegulatoryReportingAdapter.submit` 已存在（返回 success + "REG-"+type）；装配为 Bean。
- 可选：增加一个失败场景适配器或参数化 mock 便于测试 FAILED 分支（测试中可注入自定义 adapter）。
- 适配器抛异常或返回 success=false → status=FAILED + message，**不向上抛**。

### 6.5 导出
- export(id)：查报告 → 返回 content（byte[]，Content-Disposition attachment; filename=regulatory-report-{id}.json）。
- 借鉴 P1-03 QualityController export 模式。

### 6.6 方言守护
- V020 SQL 不含 ` LIMIT `（带空格）；DM 不含 ` TEXT`(带空格)/` TINYINT `。
- content：MySQL `LONGTEXT`，达梦 `CLOB`（双库脚本分别写）。
- 报告每次生成 INSERT 一条（不去重），无 upsert 竞态。

### 6.7 审计
- 生成：AuditEvent(type=REGULATORY_REPORT_GENERATE, resourceType=REGULATORY_REPORT, resourceId=id, status=SUCCESS)。
- 报送：AuditEvent(type=REGULATORY_REPORT_SUBMIT, ..., status=SUCCESS/FAILED)，含 trace_id。

## 7. 必须补充的测试

- **生成持久化**：invoke log 写入 → generate → 返回记录 + 持久化；JDBC 重启可查。
- **脱敏**：PERSONAL_INFO 报文 content 中 consumerCode 掩码（不含原明文）；COMPLIANCE 不脱敏（含原 consumerCode）。
- **回执入库**：mock 成功 → status=SUBMITTED + receiptNo 非空；失败 → status=FAILED + message。
- **列表/详情**：按 reportType 过滤；detail 不存在 404。
- **导出**：export 返回 content 含报文字段（reportType/summary）。
- **时间范围/非法参数**：from/to 过滤生效；非法 reportType/from/to → 400。
- **MockMvc**：generate/list/detail/export 200 + 401/403；PERSONAL_INFO export 内容断言不含明文 consumerCode。
- **三库迁移**：MigrationDialectCompatibilityTest 纳入 V020，t_regulatory_report CRUD（含 content 大字段）通过。
- **审计**：生成/报送写 AuditEvent（可查）。

## 8. 验收命令

```bash
mvn -pl platform-common install -DskipTests   # 若 common 改动
mvn test -pl platform-billing
mvn test                                       # 全量回归
```

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| 监管标准 Q-05 未定 | 框架 + mock，真实对接/加密待标准 |
| content 大字段方言 | MySQL LONGTEXT / 达梦 CLOB 双库；迁移测试守护 |
| 脱敏遗漏 PII | PERSONAL_INFO 强制掩码；MockMvc 断言不含明文 |
| 报送失败未持久化 | 适配器失败记 FAILED + message，不抛异常 |
| 并发生成重复 | 每次快照 INSERT，无竞态 |
| **回滚** | V020 有 U020；独立表 + 新端点，移除不影响既有 stats 功能 |

## 10. 完成判定

- [ ] t_regulatory_report（V020/U020 双库）。
- [ ] 报文生成持久化（三类型 + 时间范围）。
- [ ] 个人信息类脱敏。
- [ ] 报送回执入库（SUBMITTED/FAILED）。
- [ ] 报告列表/详情查询 + 持久化重启可查。
- [ ] 导出报文 Blob。
- [ ] 三库迁移测试纳入 V020。
- [ ] 生成/报送审计事件。
- [ ] mvn test 全绿。
- [ ] 输出报文设计 + 脱敏 + 回执证据 + 潜在风险。

## 11. 实现边界（Codex 遵守）

1. 报文用独立 `t_regulatory_report`，不重构既有 `ReportGenerator`/`/stats/reports` 即时端点。
2. V020/U020 MySQL + 达梦双库；content MySQL `LONGTEXT`/达梦 `CLOB`；避开方言守护（` LIMIT `、DM ` TEXT`/` TINYINT `）。
3. Repository 用 IdGenerator + INSERT（每次生成一条，不去重）。
4. 报文序列化用 ObjectMapper（含 JavaTimeModule），禁手写 JSON 拼接（借鉴 P1-03 P2-1）。
5. 脱敏仅对 PERSONAL_INFO 类；COMPLIANCE/DATA_SOURCE 不脱敏。
6. 报送回执调用 `RegulatoryReportingAdapter.submit`，mock 装配为 Bean；失败记 FAILED 不抛异常。
7. 不做真实监管对接/SM4 加密/模板配置/定时报送；不新增权限码。
8. 不改密钥/生产配置；不动无关模块；不跳过测试。
9. 完成后输出修改文件、测试命令、测试结果、报文设计、脱敏、回执证据、潜在风险。

## 12. 借鉴说明

借鉴**数据报送平台**的"报文生成 → 脱敏 → 报送 → 回执"链路（报文可追溯、回执可入库、审计 trace_id 链路）。报文序列化复用 ObjectMapper、parseInstant 校验、export Blob 模式均借鉴 P1-03 质量报告经验。详见 `docs/requirements.md` §2.8、`docs/development-process-workflow.md` §6.8。
