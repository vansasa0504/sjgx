# 第一性原理开发计划 — P1-04 监管报表

> 任务编号：P1-04
> 分支：`ai/p1-regulatory-report`（P1-03 合并 master 后从 master 切出）
> 依据：`tasks/claude-plan-P1.md` §3、`docs/requirements.md` §2.8（FR-801~805）、`docs/development-process-workflow.md` §3.2 P1-04、§6.8 统计监管流程
> 前置：P0-05（invoke log 事实源）已合入 master；P1-03 审查通过
> 日期：2026-06-29

---

## 1. 需求来源

- **技术要求**：统计监管（FR-801~805）要求按监管要求生成合规/来源/个人信息报表，对接报送系统整理/校验/加密报送，报送回执入库，审计可追溯。
- **验收口径**：P1-04 通过标准"报文和回执入库"。
- **待定口径**：监管报送具体对接标准待 Q-05 明确（`docs/requirements.md` 第226行），本期仅做报送数据整理 + 脱敏 + 回执入库 + mock 报送框架。
- **现状缺口**：
  - `StatsController.report()`（`/api/v1/stats/reports`）即时调用 `ReportGenerator` 生成 xlsx 写到本地 `target/reports` 目录，**无 DB 持久化**（重启丢失、无记录可查）。
  - 报文内容（payload）不入库，仅返回本地文件 `Path`。
  - `RegulatoryReportingAdapter` / `MockRegulatoryReportingAdapter` 已存在但**未装配为 Bean、未被任何代码调用**，报送回执不入库。
  - **无脱敏**：个人信息类报表直接拼接 consumer_code/api_key/trace_id 明文。
  - 无报告记录列表/详情查询、无导出。

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？
监管报文可生成、可持久化、可脱敏、报送回执可入库可查询，让验收方确认"报文和回执入库"且审计可追溯。

### 2.2 最小可行结果
1. 监管报文持久化（`t_regulatory_report`，含报文内容/状态/回执）。
2. 按报表类型（COMPLIANCE/DATA_SOURCE/PERSONAL_INFO）+ 时间范围从 invoke log 聚合生成报文。
3. 个人信息类报表脱敏（敏感字段掩码）。
4. 报送回执入库（调用 RegulatoryReportingAdapter → 回执持久化）。
5. 报告列表/详情查询 + 报文导出（Blob）。

### 2.3 系统必须接收哪些输入？
- 报表类型（COMPLIANCE/DATA_SOURCE/PERSONAL_INFO）。
- 时间范围（可选 from/to）。
- 调用日志事实源（`t_service_invoke_log` / `JdbcServiceInvokeLogRepository`）。

### 2.4 系统必须产生哪些输出？
- `RegulatoryReportRecord`：类型/时间范围/报文内容（脱敏后 JSON）/状态/回执号/回执信息/生成时间/报送时间，持久化。
- 报送回执（success/receiptNo/message）写入报告记录。
- 导出文件（报文内容 Blob）。

### 2.5 不可省略的处理过程？
1. 按 reportType + 时间范围从 invoke log 聚合（调用量/成功/失败/明细）。
2. 构建结构化报文 JSON。
3. 脱敏（PERSONAL_INFO 类对 consumerCode/apiKey/traceId 掩码）。
4. 持久化报文记录（status=PENDING）。
5. 调用 `RegulatoryReportingAdapter.submit` → 回执。
6. 更新报告记录回执字段（receiptNo/message/status=SUBMITTED 或 FAILED）。
7. 写审计事件（生成/报送，trace_id 链路）。

### 2.6 哪些是核心能力？
- 报文持久化（`t_regulatory_report`）。
- 报送回执入库。
- 脱敏（个人信息类）。
- 报文生成（从事实源聚合）。

### 2.7 哪些是增强能力？
- 报表模板可视化配置（本任务固定三类型）。
- 定时报送（留后续）。
- 真实监管对接/SM4 加密报送（待 Q-05，本任务 mock）。
- 大屏可视化（留后续）。

### 2.8 最小改动路径？
- **新增 `t_regulatory_report` 表**（report_type/period_from/period_to/content/status/receipt_no/receipt_message/generated_at/submitted_at）。
- **新增 RegulatoryReportRepository**（JDBC + 内存）+ RegulatoryReportService（生成/脱敏/报送/查询/导出）。
- **装配** MockRegulatoryReportingAdapter 为 Bean（已存在，仅缺装配）。
- **复用** ObjectMapper 序列化报文（借鉴 P1-03 P2-1 修复，避免手写 JSON 注入）。
- **端点**：POST /stats/reports/generate（生成+持久化+报送+回执）、GET /stats/reports（列表）、GET /stats/reports/{id}（详情）、GET /stats/reports/{id}/export（导出报文 Blob）。
- **保留** 既有 `ReportGenerator`（xlsx）不动，新增独立报文持久化链路（最小侵入，不破坏既有 `/stats/reports` 即时端点语义；新端点用 `/generate` 子路径区分）。

### 2.9 如何测试？
- 生成：写 invoke log → POST /stats/reports/generate{type,from,to} → 持久化 + 报送回执 + 返回记录。
- 脱敏：PERSONAL_INFO 报文内容中 consumerCode 被掩码；COMPLIANCE 不脱敏。
- 回执：mock submit 成功 → status=SUBMITTED + receiptNo；失败 → status=FAILED。
- 持久化：JDBC 生成 → 新仓储查回（重启可查）。
- 查询/导出：list 按 type 过滤；detail 404；export 返回报文内容。
- MockMvc：generate/list/detail/export 200 + 401/403 + 脱敏断言。
- 三库迁移：MigrationDialectCompatibilityTest 纳入 V020。

### 2.10 如何验收？
报文 + 回执入库；按类型生成；个人信息脱敏；持久化重启可查；审计可追溯。

### 2.11 如何避免过度设计？
- 不做真实监管对接/SM4 加密（Q-05 待定，mock 框架）。
- 不做模板配置（固定三类型）。
- 不做定时报送（手动触发）。
- 不做加密报文存储（content 存脱敏后明文 JSON；加密报送待 Q-05）。
- 不重构既有 `ReportGenerator`/`/stats/reports` 即时端点（新增独立链路）。
- 回执字段内联到报告表（不拆 t_regulatory_receipt，最小表数）。

## 3. 功能拆解

| 编号 | 功能 | 说明 |
|---|---|---|
| F-1 | 报告表 + 仓储 | t_regulatory_report + JdbcRegulatoryReportRepository + 内存实现 |
| F-2 | 报文生成 | RegulatoryReportService.generate 聚合 invoke log → 报文 JSON → 脱敏 → 持久化 |
| F-3 | 脱敏 | PERSONAL_INFO 类对 consumerCode/apiKey/traceId 掩码；其余类型不脱敏 |
| F-4 | 报送回执 | 调用 RegulatoryReportingAdapter.submit → 回执持久化（SUBMITTED/FAILED） |
| F-5 | 报告查询 | list（按 reportType）+ detail（by id，404） |
| F-6 | 导出 | GET /stats/reports/{id}/export 返回报文内容 Blob |
| F-7 | 端点 | generate/list/detail/export，权限 billing:run（生成）/ stats:view（查询导出） |
| F-8 | 审计 | 生成/报送写 AuditEvent（trace_id 链路） |

## 4. 影响模块

| 模块 | 改动 |
|---|---|
| platform-billing.report | 新增 RegulatoryReportRecord/RegulatoryReportRepository/Jdbc/InMemory/RegulatoryReportService；复用 ReportType |
| platform-billing.regulatory | 装配 MockRegulatoryReportingAdapter 为 Bean（已存在） |
| platform-billing.StatsController | 新增 generate/list/detail/export 端点 |
| platform-billing.BillingApplication | 新仓储/服务/adapter Bean 装配 |
| db/migration + db/migration-dm | V020 t_regulatory_report + U020 |
| platform-common.db | MigrationDialectCompatibilityTest 纳入 V020 |
| platform-ui | StatsView 监管报表演示（可选，后端先就绪） |

## 5. 接口设计

### 5.1 模型

```java
public record RegulatoryReportRecord(
    long id,
    String reportType,        // COMPLIANCE/DATA_SOURCE/PERSONAL_INFO
    Instant periodFrom,
    Instant periodTo,
    String content,           // 脱敏后 JSON 报文
    String status,            // PENDING/SUBMITTED/FAILED
    String receiptNo,
    String receiptMessage,
    Instant generatedAt,
    Instant submittedAt) {}
```

### 5.2 API

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `POST /api/v1/stats/reports/generate` | POST | billing:run | 生成+持久化+报送+回执（body: reportType/from?/to?） |
| `GET /api/v1/stats/reports` | GET | stats:view | 列表（按 reportType 过滤） |
| `GET /api/v1/stats/reports/{id}` | GET | stats:view | 详情（404） |
| `GET /api/v1/stats/reports/{id}/export` | GET | stats:view | 导出报文内容 Blob |

> 权限说明：生成属写操作，复用既有 `billing:run`（不新增权限码，最小改动）；查询/导出用 `stats:view`。

## 6. 数据结构

### 6.1 t_regulatory_report（V020）

```sql
CREATE TABLE t_regulatory_report (
    id BIGINT PRIMARY KEY,
    report_type VARCHAR(32) NOT NULL,       -- COMPLIANCE/DATA_SOURCE/PERSONAL_INFO
    period_from TIMESTAMP,
    period_to TIMESTAMP,
    content CLOB NOT NULL,                  -- MySQL: LONGTEXT, 达梦: CLOB
    status VARCHAR(32) NOT NULL,            -- PENDING/SUBMITTED/FAILED
    receipt_no VARCHAR(128),
    receipt_message VARCHAR(512),
    generated_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP
);
CREATE INDEX idx_regulatory_report_type ON t_regulatory_report(report_type, generated_at);
```

### 6.2 报文内容结构（JSON）

```json
{
  "reportType": "COMPLIANCE",
  "periodFrom": "2026-06-01T00:00:00Z",
  "periodTo": "2026-06-30T23:59:59Z",
  "summary": { "invokeCount": 10, "successCount": 8, "failCount": 2 },
  "details": [
    { "traceId": "...", "serviceCode": "...", "consumerCode": "...", "status": 200 }
  ]
}
```

### 6.3 脱敏规则
- PERSONAL_INFO 类：`consumerCode` 保留首末各 1 字符中间掩码（`C1001`→`C***1`），`apiKey` 全掩码（`***`），`traceId` 保留前 4 后 4。
- COMPLIANCE/DATA_SOURCE：不脱敏（聚合统计/来源维度，无 PII）。

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 无 invoke log | 生成 0 统计报文，仍持久化 + 报送 |
| 报送适配器失败 | status=FAILED，回执 message 入库，不抛异常（记录失败状态） |
| 报告不存在 | 404（REGULATORY-404） |
| 非法 reportType | 400（REGULATORY-400） |
| 非法 from/to 格式 | 400（REGULATORY-400，捕获 DateTimeException） |
| 序列化失败 | 500（REGULATORY-500） |
| 并发生成 | 各自持久化（每次生成一条快照） |

## 8. 测试策略

| 测试 | 覆盖 |
|---|---|
| 生成持久化 | invoke log → generate → 返回记录 + 持久化 |
| 脱敏 | PERSONAL_INFO consumerCode 掩码；COMPLIANCE 不脱敏 |
| 回执入库 | mock 成功 → SUBMITTED+receiptNo；失败 → FAILED |
| 查询/导出 | list 按 type；detail 404；export 返回报文内容 |
| 重启可查 | JDBC 生成 → 新仓储查回 |
| MockMvc | generate/list/detail/export 200 + 401/403 + 脱敏断言 |
| 三库迁移 | MigrationDialectCompatibilityTest 纳入 V020，t_regulatory_report CRUD |
| 审计 | 生成/报送写 AuditEvent |

## 9. Codex 实现边界

1. 报文用独立 `t_regulatory_report` 表，不重构既有 `ReportGenerator`/`/stats/reports` 即时端点。
2. V020/U020 MySQL + 达梦双库；content MySQL 用 `LONGTEXT`、达梦用 `CLOB`；避开 ` LIMIT `/DM ` TEXT`(带空格)/` TINYINT ` 方言守护。
3. Repository 用 IdGenerator + INSERT（每次生成一条，不去重）。
4. 报文序列化用 ObjectMapper（含 JavaTimeModule），禁手写 JSON 拼接（借鉴 P1-03 P2-1）。
5. 脱敏仅对 PERSONAL_INFO 类，工具方法 maskMiddle/replaceAll。
6. 报送回执调用 `RegulatoryReportingAdapter.submit`，mock 适配器装配为 Bean；失败不抛异常，记 FAILED。
7. 不做真实监管对接/SM4 加密/模板配置/定时报送。
8. 不改密钥/生产配置；不动无关模块；不跳过测试。
9. 必须补测试并全绿。
10. 输出报文设计 + 脱敏 + 回执证据。

## 10. 验收标准

- [ ] t_regulatory_report（V020/U020 双库）。
- [ ] 报文生成持久化（三类型 + 时间范围）。
- [ ] 个人信息类脱敏。
- [ ] 报送回执入库（SUBMITTED/FAILED）。
- [ ] 报告列表/详情查询 + 持久化重启可查。
- [ ] 导出报文 Blob。
- [ ] 三库迁移测试纳入 V020。
- [ ] 生成/报送审计事件。
- [ ] mvn test 全绿。
- [ ] 输出报文设计 + 脱敏 + 回执证据。

## 11. 风险与回滚

| 风险 | 控制 |
|---|---|
| 监管标准 Q-05 未定 | 本任务做框架 + mock，真实对接/加密待标准明确 |
| content 大字段方言 | MySQL LONGTEXT / 达梦 CLOB 双库脚本；迁移测试守护 |
| 脱敏遗漏 PII | PERSONAL_INFO 类强制掩码 consumerCode/apiKey/traceId；MockMvc 断言不含明文 |
| 报送失败未持久化 | 适配器失败记 FAILED + message，不抛异常 |
| **回滚** | V020 有 U020；报告独立表 + 新端点，移除不影响既有 stats 功能 |

## 12. 借鉴说明

借鉴**数据报送平台**的"报文生成 → 脱敏 → 报送 → 回执"链路：报文可追溯（持久化）、回执可入库、审计可链接 trace_id。报文序列化复用 ObjectMapper（P1-03 P2-1 修复经验）。详见 `docs/requirements.md` §2.8、`docs/development-process-workflow.md` §6.8。
