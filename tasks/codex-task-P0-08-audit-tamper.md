# Codex 执行任务 - P0-08：审计防篡改

> 阶段：P0（上线阻断修复）
> 任务编号：P0-08
> 分支建议：`ai/p0-audit-tamper`
> 依据：`docs/development-process-workflow.md` §3.1 P0-08、§5.2.6、§6.8、`docs/database-design.md`（`t_audit_log`）
> 前置：P0-03 通过（JdbcAuditLogRepository 已有原型）、P0-05 通过（trace_id 链路）
> 日期：2026-06-27

---

## 1. 背景与目标

M7 期间 `t_audit_log` 已有 `JdbcAuditLogRepository` 原型，但无防篡改机制——审计表可被有 DB 权限的人直接 update/delete，篡改不可发现。金融场景要求审计不可篡改或篡改可发现。本任务实现审计 hash 链（每条记录含 `prev_hash` + 本条 `hash`），并加 DB 权限策略（只追加，禁 update/delete），让篡改可阻止或可发现。

**最小可行结果**：审计日志写库时形成 hash 链；校验接口可验证链完整性（断链可发现）；DB 层禁止业务 update/delete（DB 权限或触发器）；一次主链路可按 trace_id 查全事件。

## 2. 范围

### 本次实现
- `t_audit_log` 表补字段：`prev_hash`/`hash`/`trace_id`/`actor`/`event_type`/`payload`/`created_at`（若缺，V011 + U011）。
- `JdbcAuditLogRepository.write`：写时取上一条 hash 作为 `prev_hash`，本条 `hash = sha256(prev_hash + canonical(payload) + created_at)`，形成链。
- 新增 `GET /api/v1/stats/audit/verify`（或 `GET /audit/verify`）：校验 hash 链完整性，返回断链位置（权限码 `stats:view` 或新增 `audit:verify`）。
- DB 防篡改：`t_audit_log` 加触发器或 DB 用户权限限制（业务账号无 update/delete 权限，仅 insert + select）。
- 审计表只追加：Repository 不提供 update/delete 方法（流程文档 §5.2.6）。
- trace_id 全链路：每次主链路操作生成/传播 trace_id，写审计（P0-05 已铺路，本任务落地）。

### 不做
- 不做备份恢复（P2-05）。
- 不做监管报表（P1-04）。

## 3. 必读输入

- `AGENTS.md`、`docs/database-design.md`（`t_audit_log`）
- `docs/detailed-requirements-design.md`（审计设计）
- `platform-common/src/main/java/.../audit/JdbcAuditLogRepository.java`、`InMemoryAuditLogRepository.java`
- `reviews/claude-review.md`（M7-A S-03 异常日志、P0-05 trace_id）

## 4. 需要修改的模块

- `platform-common.audit`（JdbcAuditLogRepository hash 链、AuditEvent 补字段、verify 逻辑）
- `platform-billing.stats`（`StatsController.audit` 对齐 trace_id 查询；新增 verify 端点）
- `db/migration`（V011 + U011 补字段 + 触发器/权限；触发器须三库兼容，见 P0-02）
- 各模块写审计处（确保 trace_id 传播）

## 5. 数据库/API/前端影响

- **数据库**：`t_audit_log` 补 `prev_hash`/`hash`/`trace_id`；触发器或权限策略禁 update/delete。
- **API**：`GET /stats/audit` 按 trace_id 查询；新增 `GET /stats/audit/verify` 校验链。
- **前端**：StatsView 审计页支持 trace_id 查询；可选展示链校验状态。

## 6. 必须补充的测试

- **hash 链测试**：连续写 N 条 → verify 全通过；篡改某条 payload → verify 报断链。
- **只追加测试**：尝试 update/delete 审计记录 → 被 DB 拒绝（触发器或权限）。
- **trace_id 链路测试**：一次主链路（如 partner create→submit→approve）按 trace_id 查全事件。
- **MockMvc**：`/stats/audit` 按 trace_id 返回有序事件；`/stats/audit/verify` 200。

## 7. 验收命令

```bash
mvn test -Dspring.profiles.active=jdbc
# hash 链：写 N 条 → verify 通过 → 篡改 → verify 失败
# 只追加：尝试 UPDATE t_audit_log → 被拒绝
npm run test:unit
```

## 8. M7 衔接

- **M7-A S-03**：异常日志已修（`GlobalExceptionHandler.handleThrowable` 加 LOG.error），本任务的审计与异常日志互补。
- **P0-05 trace_id**：本任务复用 P0-05 的 trace_id 链路。
- **M7-D D2-05 上线前项**：审计防篡改属上线门禁（流程文档 §9 安全门禁），本任务闭环。

## 9. 风险与回滚

- **风险**：hash 链并发写入导致 prev_hash 竞态。控制：写入串行化（行锁或单写线程）或容忍短暂断链后修复；测试覆盖并发。
- **风险**：触发器三库不兼容（达梦/OceanBase 语法差异）。控制：优先用 DB 用户权限策略（ revoke update/delete），触发器作为补充；与 P0-02 协调。
- **风险**：历史审计数据无 hash。控制：迁移时为历史数据补 `prev_hash=''`/`hash` 占位，verify 跳过历史段或标记。
- **回滚**：V011 有 U011；触发器/权限可回退。
- **敏感约束**：审计 payload 不含明文 secret（与 P0-04 脱敏一致）。

## 10. 完成判定

- 审计写库形成 hash 链，verify 接口可用，篡改可发现。
- DB 层禁 update/delete（权限或触发器）。
- trace_id 全链路查询可用。
- 并发写入与篡改测试通过。
- MockMvc + 前端测试全绿。
- 输出 hash 链设计 + 篡改发现证据 + DB 权限策略说明。
