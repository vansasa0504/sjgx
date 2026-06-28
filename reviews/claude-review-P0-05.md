# Claude Code 审查结果 — P0-05 调用日志事实源

## 1. 审查对象

- 任务：P0-05 调用日志事实源
- 分支：`ai/p0-invoke-log`（基于 P0-04 `f10ca229`）
- 任务单：`tasks/codex-task-P0-05-invoke-log.md`
- 审查日期：2026-06-28
- 改动范围：新增 `JdbcServiceInvokeLogRepository` + V013/U013 迁移、`ServiceInvokeLog` 补字段、`DataServiceManager.invoke` 成功/失败均写日志、`AsyncInvokeLogWriter` JDBC 持久化、`ConsumerController.logs` 接事实源、billing/stats 聚合接事实表、前端日志列对齐

## 2. Git 状态

改动全部未提交（工作区）：

```text
 M platform-billing/BillingApplication.java
 M platform-billing/BillingController.java
 M platform-billing/StatsController.java
 M platform-billing/test/.../BillingControllerTest.java
 M platform-billing/test/.../BillingGovernanceTest.java
 M platform-billing/test/.../StatsControllerTest.java
 M platform-billing/test/.../it/M5EndToEndIntegrationTest.java
 M platform-common/model/ServiceInvokeLog.java
 M platform-partner/consumer/ConsumerController.java
 M platform-partner/consumer/ConsumerService.java
 M platform-partner/test/.../ConsumerControllerTest.java
 M platform-pipeline/service/AsyncInvokeLogWriter.java
 M platform-pipeline/service/DataServiceController.java
 M platform-pipeline/service/DataServiceManager.java
 M platform-pipeline/test/.../DataServiceManagerTest.java
 M platform-ui/views/ConsumerView.vue
 M platform-ui/views/ServiceView.vue
 M tasks/dev-progress.md
?? db/migration/V013__service_invoke_log_fact_source.sql
?? db/migration/U013__service_invoke_log_fact_source.sql
?? db/migration-dm/V013__service_invoke_log_fact_source.sql
?? db/migration-dm/U013__service_invoke_log_fact_source.sql
?? platform-common/log/JdbcServiceInvokeLogRepository.java
?? platform-common/test/log/JdbcServiceInvokeLogRepositoryTest.java
```

未触及：`.env`、密钥、证书、生产配置。未引入新依赖（复用 H2/Flyway/JdbcTemplate）。无大批量删除。

## 3. 代码差异摘要

### 3.1 事实源建表（任务主线，达成）
- **V013/U013**：`t_service_invoke_log` 补 `trace_id/partner_code/api_key/request_hash/error_code/error_message` 6 列 + 3 索引（trace、request_hash、consumer+created_at）。通用版与 dm 版对等（dm 省 `COLUMN` 关键字）。U013 对等回滚。
- 原表 V005 已有 `id/service_code/consumer_code/status_code/elapsed_millis/log_day/created_at`，V009 已加 `response_size`。`JdbcServiceInvokeLogRepository.save()` 写入 14 列与现表 + V013 新列完全匹配。✅ 字段无缺失。

### 3.2 统一 JDBC 仓储（达成）
- 新增 `JdbcServiceInvokeLogRepository`（platform-common/log），作为 pipeline/partner/billing 三模块共用事实源。
- `AsyncInvokeLogWriter` 改造：有 `JdbcServiceInvokeLogRepository` 时 `write()` 落库 + `logs()` 从库读；无则回退内存 `localMirror`。保留 Kafka 异步路径（未启用时不发）。
- 内存/JDBC 双模式与 P0-03/P0-04 的 `useDb()` 模式一致。

### 3.3 invoke 写日志（达成，含成功/失败）
- `DataServiceManager.invoke` 重构为 try/catch 包裹：成功写 200 + responseSize；`BusinessException` 写对应 status + errorCode + 脱敏 errorMessage；`RuntimeException` 写 500。
- `traceId` 支持 `X-Trace-Id` 透传或 `UUID.randomUUID()` 自动生成。
- `statusFor(ex)` 把业务异常码映射 HTTP status（429/401/403/404/503/400）。
- `sanitize()` 正则脱敏 `secret/api_key/signature` 字段，防明文进日志。

### 3.4 消费方/服务日志查询（达成）
- `ConsumerController.logs` 从空 `List.of()` 改为 `consumerService.logs(id,page,size)` → `JdbcServiceInvokeLogRepository.findByConsumer`。修复 M7-A F-06。
- `ConsumerService.logs` 按 consumerCode 查事实表分页。
- `DataServiceController.logs` 经 `DataServiceManager.logs` 读 JDBC/内存源。

### 3.5 billing/stats 聚合接事实表（达成，为 P0-06 铺路）
- `BillingController.generate`：有 invokeLogRepository 时从事实表 `findAll()` 聚合，否则回退请求体 logs。
- `StatsController.report`：从事实表生成报表行。
- `BillingApplication` 新增 `jdbcServiceInvokeLogRepository` bean + `BillGeneratorJobHandler`/`StatsAggregatorJobHandler` 注入事实表供应源。

### 3.6 前端列对齐（达成）
- ConsumerView/ServiceView 日志列增加 trace、耗时、响应大小、request_hash、错误列。

## 4. 需求满足情况

依据 `tasks/codex-task-P0-05-invoke-log.md` §2 与 §10：

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| R1 | t_service_invoke_log 字段完整 | ✅ | V013 补 6 列 + 索引 |
| R2 | 成功/失败调用均写一条日志 | ✅ | try/catch 全覆盖 |
| R3 | 含 trace_id/request_hash/status/latency/response_size/error/consumer_code/service_code | ⚠️ | `partner_code` 列已加但 invoke 写日志时**硬编码 null**，永远为空（见 5.2） |
| R4 | AsyncInvokeLogWriter JDBC 持久化 | ✅ | 双模式 |
| R5 | ConsumerController.logs 返回真实数据 | ✅ | 接事实表 |
| R6 | billing/stats 从事实表聚合 | ✅ | BillingController/StatsController/JobHandler 接入 |
| R7 | request_hash 不存明文 params | ✅ | 存 hash |
| R8 | error 消息脱敏不含 secret | ✅ | sanitize 正则 + 测试断言 |
| R9 | 写入测试（成功/失败字段完整） | ✅ | DataServiceManagerTest 2 用例 |
| R10 | 聚合一致性测试 | ✅ | BillingControllerTest.generatesBillFromInvokeLogFactTable |
| R11 | 消费方查询测试 | ✅ | ConsumerControllerTest.logsEndpointReturnsInvokeLogsFromFactTable |
| R12 | trace_id 链路测试 | ⚠️ | trace_id 进日志✅；但"invoke→log→audit 贯穿"未验证（audit 未接 trace_id，见 5.4） |
| R13 | MockMvc /consumers/{id}/logs 非空 | ✅ | standalone MockMvc 验证 |
| R14 | 字段清单 + 聚合证据 | ✅ | dev-progress §15 |

## 5. 安全与风险检查

### 5.1 `request_hash` 算法不一致（中-语义缺陷，建议修复）
`DataServiceManager.invoke`：
```java
String requestHash = sha256(body);          // 第157行：鉴权前用 SHA-256(body)
try {
    ...
    requestHash = hmacSha256(secret, body); // 第162行：鉴权后用 HMAC-SHA256(secret, body)
```
- **同一请求的 request_hash 在成功路径（HMAC）和失败路径（SHA-256）算法不同**。
- 鉴权前失败（如 apiKey not found）→ SHA-256(body)；鉴权后失败/成功 → HMAC(secret, body)。
- 后果：同一 body 在不同失败时机产生不同 hash，**无法用 request_hash 做请求去重或幂等追溯**——而 request_hash 的核心用途正是去重/追溯。
- 此外用 `secret` 做 HMAC key 语义可疑：request_hash 应是请求内容的指纹（与 secret 无关），用 secret 当 key 会让 hash 依赖凭证轮换而变化，进一步破坏稳定性。
- **建议**：统一用 `sha256(body)`（或规范化后的 body+timestamp+nonce），与 secret 解耦，成功/失败一致。任务 §9 也说"request_hash 只存 hash 不存明文 params"，未要求用 secret。

### 5.2 `partner_code` 列永远为空（中-功能缺陷，建议修复）
- V013 加了 `partner_code` 列 + 索引，但 `writeInvokeLog` 第 222 行 partnerCode 参数硬编码 `null`。
- invoke 链路未从凭证/服务解析 partnerCode 写入。该列永远为空，索引无意义。
- 与 P0-04 RW-2（rotated_from 未填充）同类问题——加了字段未消费。
- **建议**：要么从 `ApiCredential`/`DataServiceDefinition` 解析 partnerCode 写入，要么若本任务范围不含 partner 关联则在 dev-progress 标注"P0-07 目录申请或后续补 partner_code 填充"并暂不建索引。

### 5.3 `logs()` 全表加载内存分页（中-性能，已标注）
- `JdbcServiceInvokeLogRepository.findAll()` → `SELECT * ORDER BY created_at DESC` → 内存 filter + subList 分页。
- 调用日志是大表（每次 invoke 一条），全表加载在生产不可行。
- dev-progress §15.5 已标注"大表高并发分页优化留后续性能任务"。
- **评估**：P0 目标是"事实源可用"，内存分页功能正确但性能不达标。建议至少在 P2-01（大表分区归档）前补 SQL 层 `WHERE + LIMIT` 分页，否则 P0-10 E2E 真实依赖下数据量稍大即 OOM/超时。列为中优跟进项。

### 5.4 trace_id 未贯穿到 audit（低，任务范围边界）
- 任务 §6 要求"trace_id 链路测试：invoke→log→audit 贯穿"。当前 trace_id 仅进 `t_service_invoke_log`，`AuditLogger`/`t_audit_log` 未接 trace_id。
- 任务 §2 也说"不做审计防篡改（P0-08），但 trace_id 为其铺路"——属 P0-08 范围。
- **评估**：trace_id 已落事实表，P0-08 时补 audit 关联即可。当前不算缺陷，但 R12 的"贯穿"未完成，建议 dev-progress 明确标注留 P0-08。

### 5.5 `localMirror` 内存增长（低）
- `AsyncInvokeLogWriter.write()` 第 39 行 `localMirror.add(log)` 无界增长，即使 repository 非 null（已落库）仍往内存 list 加。
- 生产 JDBC 模式下 localMirror 永不清理，长期运行内存泄漏。
- **建议**：repository 非 null 时不写 localMirror，或限制大小。低优但生产隐患。

### 5.6 无安全红线违反
- 未改 .env/密钥/证书；未连生产库；request_hash 不存明文 params；error 脱敏；测试断言无明文泄露。

## 6. 测试检查

| 测试 | 结果 | 说明 |
|---|---|---|
| `mvn test`（全量 8 模块） | ✅ 全绿 | 187 测试 BUILD SUCCESS |
| `JdbcServiceInvokeLogRepositoryTest` | ✅ 1/1 | 保存 + 按 consumer/service/status 查询 |
| `DataServiceManagerTest` 新增 | ✅ 2/2 | 成功日志字段完整 + 失败日志 traceId/errorCode/脱敏 |
| `BillingControllerTest.generatesBillFromInvokeLogFactTable` | ✅ | 从事实表聚合账单，金额正确 |
| `ConsumerControllerTest.logsEndpointReturnsInvokeLogsFromFactTable` | ✅ | MockMvc 验证 /logs 非空分页 |
| `BillingGovernanceTest` | ✅ | V013 迁移可执行 + TRACE_ID/REQUEST_HASH 列存在 |
| `M5EndToEndIntegrationTest` | ✅ | 适配 P0-04 凭证（原硬编码 api-key/secret 改为 createCredential） |
| 前端 `npm run test:unit` | 未本地复跑 | dev-progress §15.3 记录 11 文件 35 用例通过 |

**测试缺口**：
- 无"invoke N 次 → billing/stats 聚合 = N"的端到端一致性测试（任务 §7 验收命令要求）。BillingControllerTest 仅 1 条日志聚合，未验证多调用量一致性。
- 无 trace_id 透传（X-Trace-Id 头）的 MockMvc 测试。

## 7. 审查结论

### 建议有条件通过

核心目标达成：`t_service_invoke_log` 建为统一事实源、成功/失败均写日志、consumer/service logs 端点返回真实数据、billing/stats 接事实表聚合、字段脱敏。187 测试全绿。复用 P0-03/P0-04 的双模式架构，一致性好。

存在 2 个建议合入前修复的中优项 + 1 个性能隐患：
- **RW-1（中-语义）** request_hash 算法不一致（成功 HMAC/失败 SHA-256）→ 无法用于去重追溯，建议统一为 SHA-256(body)；
- **RW-2（中-功能）** partner_code 列永远为空 → 要么填充要么标注暂不建索引；
- **RW-3（中-性能）** logs() 全表内存分页 → 生产不可行，建议补 SQL 分页或明确 P2-01 前补。

低优 3 项（trace_id 贯穿 audit 留 P0-08、localMirror 无界增长、聚合一致性多调用测试缺失）。

未触及"暂不通过"红线。是否先返工 RW-1~RW-3 再提交由你定夺——RW-1 影响事实源可用性（request_hash 是事实源核心字段），建议合入前修复。

## 8. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RW-1 | request_hash 算法不一致 | 统一成功/失败路径均用 `sha256(body)`（或规范化 body+timestamp+nonce），与 secret 解耦；补测试验证同一 body 成功/失败 hash 一致 | 中-高 |
| RW-2 | partner_code 列永远为空 | 从凭证/服务解析 partnerCode 写入；或 dev-progress 标注留 P0-07/后续填充并暂不建该索引 | 中 |
| RW-3 | logs() 全表内存分页 | `findByConsumer`/`findByService` 改 SQL 层 `WHERE + LIMIT/OFFSET + ORDER BY`，避免全表加载；保留内存模式回退 | 中 |
| RW-4 | localMirror 无界增长 | repository 非 null 时不写 localMirror，或限容 | 低 |
| RW-5 | 聚合一致性多调用测试 | 补"invoke N 次 → billing 聚合 = N"端到端测试 | 低 |
| RW-6 | trace_id 贯穿 audit | 留 P0-08：AuditLogger 接 trace_id，dev-progress 标注 | 低 |

## 9. 建议提交信息

若接受已知风险直接提交：

```text
feat(P0-05): establish t_service_invoke_log as the unified invoke log fact source

- Add V013/U013 (mysql + dm parity): trace_id/partner_code/api_key/
  request_hash/error_code/error_message columns + indexes
- Add JdbcServiceInvokeLogRepository as shared fact source for
  pipeline/partner/billing; AsyncInvokeLogWriter persists via JDBC with
  memory fallback
- DataServiceManager.invoke writes a log entry on success and failure
  (traceId, requestHash, status, latency, responseSize, sanitized error)
- ConsumerController.logs reads from fact table by consumer_code (fixes F-06)
- BillingController/StatsController/JobHandlers aggregate from fact table
  (paves way for P0-06)
- Align ConsumerView/ServiceView log columns with new fields
- Tests: JdbcServiceInvokeLogRepositoryTest, invoke log write (success/fail),
  billing aggregation from fact table, consumer logs MockMvc, V013 governance

Known follow-ups: unify request_hash algorithm, populate partner_code,
SQL-level pagination, trace_id->audit (P0-08).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**审查结论**：建议有条件通过（RW-1~RW-3 建议合入前修复，RW-1 为事实源核心字段建议必修）。
**是否需要 Codex 返工**：建议返工 RW-1~RW-3；若接受已知风险可不返工。
**是否建议提交**：可提交（建议先修 RW-1，RW-2/RW-3 可择期）。
