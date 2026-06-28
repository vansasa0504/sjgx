# Codex 执行任务 - P0-05：调用日志事实源

> 阶段：P0（上线阻断修复）
> 任务编号：P0-05
> 分支建议：`ai/p0-invoke-log`
> 依据：`docs/development-process-workflow.md` §3.1 P0-05、§6.3、§6.8、`docs/database-design.md`（`t_service_invoke_log`）
> 前置：P0-03 通过（JDBC 仓储可用）
> 日期：2026-06-27

---

## 1. 背景与目标

M7-A F-06/S-08 指出：`ConsumerController.logs` 返回空（partner 模块无调用日志仓储）；`DataServiceManager.invoke` 虽写 `ServiceInvokeLog`，但 `AsyncInvokeLogWriter` 为内存/异步未持久化，且缺 `trace_id`/`request_hash`/`error` 字段。billing/stats/audit 无法从统一事实源聚合。本任务把 `t_service_invoke_log` 建为调用日志事实源，补字段与写入逻辑，让 billing（账单聚合）、stats（调用量统计）、audit（trace_id 追溯）共用。

**最小可行结果**：每次服务调用（成功/失败）写一条 `t_service_invoke_log`，含 `trace_id/request_hash/status/latency/response_size/error/consumer_code/service_code/created_at`；billing/stats/audit 可从该表聚合；消费方可查自身调用日志。

## 2. 范围

### 本次实现
- `t_service_invoke_log` 表结构核对/补字段（V011 + U011 若需）。
- `ServiceInvokeLog` 领域对象补 `traceId`/`requestHash`/`error` 字段。
- `DataServiceManager.invoke` 写日志：成功/失败均写，含 trace_id（从请求头或生成）、request_hash（params HMAC）、error（异常消息）。
- `AsyncInvokeLogWriter` 改为 JDBC 持久化（`jdbc` profile），内存保留 test。
- `ConsumerController.logs` 改为从 `t_service_invoke_log` 按 `consumer_code` 查询分页（修复 M7-A F-06）。
- `DataServiceController.logs` 已有，核对从事实源查。
- billing/stats 聚合改用该表（为 P0-06 铺路）。

### 不做
- 不做账单明细聚合（P0-06）。
- 不做审计防篡改（P0-08，但本任务的 trace_id 为其铺路）。

## 3. 必读输入

- `AGENTS.md`、`docs/database-design.md`（`t_service_invoke_log`）
- `docs/detailed-requirements-design.md`（调用链路）
- `platform-pipeline/src/main/java/.../service/DataServiceManager.java`、`AsyncInvokeLogWriter.java`
- `platform-common/src/main/java/.../audit/`（trace_id 上下文）
- `reviews/claude-review.md`（M7-A F-06/S-08）

## 4. 需要修改的模块

- `platform-pipeline.service`（DataServiceManager.invoke、AsyncInvokeLogWriter、ServiceInvokeLog）
- `platform-partner.consumer`（ConsumerController.logs 改事实源）
- `platform-billing`（stats 聚合改用该表，为 P0-06 准备）
- `platform-common`（trace_id 上下文传播）
- `db/migration`（V011 + U011 若补字段）
- `platform-ui`（前端 logs 列对齐新字段）

## 5. 数据库/API/前端影响

- **数据库**：`t_service_invoke_log` 补 `trace_id`/`request_hash`/`error`/`response_size`/`latency` 字段 + 索引（`consumer_code`、`service_code`、`created_at`）。
- **API**：`GET /consumers/{id}/logs` 返回真实数据（非空）；`GET /services/{code}/logs` 字段对齐。
- **前端**：ConsumerView/ServiceView 日志列对齐 trace_id/status/latency/response_size/error/时间。

## 6. 必须补充的测试

- **写入测试**：invoke 成功/失败各写一条日志，字段完整。
- **聚合测试**：billing 从日志聚合调用量、stats 从日志聚合 dashboard 指标，结果与日志一致。
- **消费方查询测试**：`GET /consumers/{id}/logs` 只返回该消费方的日志。
- **trace_id 链路测试**：一次主链路调用，trace_id 贯穿 invoke→log→audit（为 P0-08 铺路）。
- **MockMvc**：`/consumers/{id}/logs` 返回非空分页。

## 7. 验收命令

```bash
mvn test -Dspring.profiles.active=jdbc
npm run test:unit
# 聚合一致性：invoke N 次 → billing/stats 聚合 = N
```

## 8. M7 衔接

- **M7-A F-06**：consumer logs 返回空 → 本任务修复。
- **M7-A S-08**：consumer logs 无仓储 → 本任务建事实源。
- **M7-D D2-05**：为 billing/stats 真实化铺路。
- 前端 ConsumerView/ServiceView 日志 drawer（M7-C R-02 已改 PageTable）字段对齐。

## 9. 风险与回滚

- **风险**：大表写入性能（每次 invoke 写日志）。控制：异步写 + 索引；分区归档留 P2-01。
- **风险**：trace_id 上下文跨线程丢失（异步）。控制：`ThreadLocal`/MDC 传播或在日志写入时显式传入。
- **回滚**：V011 有 U011；AsyncInvokeLogWriter 保留内存实现可切回。
- **敏感约束**：`request_hash` 只存 hash 不存明文 params；error 消息脱敏不含 secret。

## 10. 完成判定

- `t_service_invoke_log` 字段完整，成功/失败调用均写入。
- consumer/service logs 端点返回真实数据。
- billing/stats 聚合与日志一致。
- trace_id 链路测试通过。
- MockMvc + 前端测试全绿。
- 输出字段清单 + 聚合一致性证据。
