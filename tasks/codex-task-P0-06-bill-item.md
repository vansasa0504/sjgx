# Codex 执行任务 - P0-06：账单明细

> 阶段：P0（上线阻断修复）
> 任务编号：P0-06
> 分支建议：`ai/p0-bill-item`
> 依据：`docs/development-process-workflow.md` §3.1 P0-06、§6.7、`docs/database-design.md`（`t_bill`/`t_bill_item`）
> 前置：P0-05 通过（调用日志事实源可用）
> 日期：2026-06-27

---

## 1. 背景与目标

M7 期间 `POST /billing/bills/generate` 的 `GenerateBillRequest` 接收调用方传入的 `logs` 作为账单依据（请求体 logs 作为事实源），这违反流程文档 §6.7"请求体 logs 不能作为账单事实源"。且缺 `t_bill_item` 明细表，账单只有总额无明细，无法核对。本任务：账单从 `t_service_invoke_log` 聚合生成，写 `t_bill` + `t_bill_item` 明细，明细合计=总额；补 `GET /billing/stats` 端点（M7-A F-04）。

**最小可行结果**：`generate bill` 不再接收 logs，服务端按规则从调用日志聚合，生成账单+明细，明细合计=总额；`/billing/stats` 返回费用统计。

## 2. 范围

### 本次实现
- `t_bill_item` 表（若 V001~V010 未建，补 V011 + U011）：`bill_no`/`item_type`/`ref_id`/`quantity`/`unit_price`/`amount`/`period`。
- `BillGenerator.generate` 改造：不接收 `logs` 参数，改为从 `t_service_invoke_log` 按规则（`BillingRule` 匹配）聚合明细，计算每条 item 与总额。
- `GenerateBillRequest` 移除 `logs` 字段（破坏性，同步前端）。
- `Bill` 领域对象补 `items` 关联（详情返回）。
- 新增 `GET /api/v1/billing/stats`：按 `from/to/partnerId/consumerId` 聚合费用统计（修复 M7-A F-04）。
- 账单状态机：异议/调整/确认受控（流程文档 §6.7）。

### 不做
- 不做财务适配器（P1-05）。
- 不改计费规则引擎内部逻辑（仅改数据来源）。

## 3. 必读输入

- `AGENTS.md`、`docs/database-design.md`（`t_bill`/`t_bill_item`）
- `docs/detailed-requirements-design.md`（计费设计）
- `platform-billing/src/main/java/.../BillGenerator.java`、`BillingRuleEngine.java`、`BillService.java`
- `reviews/claude-review.md`（M7-A F-04、M7-D 完成报告偏离说明）

## 4. 需要修改的模块

- `platform-billing`（BillGenerator、BillService、BillingController、GenerateBillRequest、新增 BillItemRepository）
- `db/migration`（V011 + U011 补 `t_bill_item`）
- `platform-ui/src/api/billing.ts`（移除 generateBill 的 logs，补 getBillingStats 调用对齐）
- `platform-ui/src/views/BillingView.vue`（费用统计 tab 用 getBillingStats，修复 M7-C R-07 相关）

## 5. 数据库/API/前端影响

- **数据库**：`t_bill_item` 新表 + 索引（`bill_no`）。
- **API**：
  - `POST /billing/bills/generate` 移除 `logs` 入参。
  - `GET /billing/bills/{billNo}` 返回含明细。
  - 新增 `GET /billing/stats`。
- **前端**：BillingView 账单详情展示明细；费用统计调真实 `/billing/stats`。

## 6. 必须补充的测试

- **明细一致性测试**：`generate bill` 后，`sum(bill_item.amount) == bill.totalAmount`。
- **事实源测试**：`generate bill` 不接收 logs，从 `t_service_invoke_log` 聚合；篡改请求体 logs 不影响账单。
- **状态机测试**：异议/调整/确认非法转移返回 409。
- **stats 端点测试**：`GET /billing/stats` 按 time/partner/consumer 聚合正确。
- **MockMvc**：generate 200/401/403；stats 200/401/403。

## 7. 验收命令

```bash
mvn test -pl platform-billing -Dspring.profiles.active=jdbc
npm run test:unit
# 明细一致性：generate → GET bill → sum(items)=total
```

## 8. M7 衔接

- **M7-A F-04**：billing 缺 `/stats` 端点 → 本任务补。
- **M7-A 审查偏离**：`GenerateBillRequest` 接收 logs → 本任务移除，改事实源聚合。
- **M7-C R-07**：BillingView 费用统计用真实 `/billing/stats`（M7-C 期间端点缺失，本任务补齐后前端可接真实数据）。
- **M7-D D2-04**：前端 billing 状态流转断言可顺带补。

## 9. 风险与回滚

- **风险**：聚合逻辑与既有 BillingRuleEngine 不兼容。控制：复用规则引擎，仅改数据来源；contract test 对比新旧账单口径。
- **风险**：历史账单无明细。控制：新账单才有明细，历史账单明细为空（在完成报告说明）。
- **回滚**：V011 有 U011；`GenerateBillRequest.logs` 移除是破坏性，回滚需同步前端。
- **敏感约束**：金额计算用 `BigDecimal`；不写明文费用敏感信息到日志。

## 10. 完成判定

- `generate bill` 从调用日志聚合，明细合计=总额。
- `GET /billing/stats` 可用。
- 请求体 logs 不再影响账单。
- MockMvc + 前端测试全绿。
- 输出聚合逻辑说明 + 明细一致性证据。
