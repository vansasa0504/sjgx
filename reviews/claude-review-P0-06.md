# Claude Code 审查结果 — P0-06 账单明细

## 1. 审查对象

- 任务：P0-06 账单明细
- 分支：**当前在 master 分支直接改动（未建 `ai/p0-bill-item` 分支）**
- 任务单：`tasks/codex-task-P0-06-bill-item.md`
- 审查日期：2026-06-28
- 前置：P0-05 已合入 master（`010b5a63`）
- 改动范围：新增 `t_bill_item` 表 + BillItem/Repository（JDBC+内存）、BillGenerator 改事实源聚合、GenerateBillRequest 移除 logs、Bill 补 items、新增 `/billing/stats` + `/bills/{billNo}` 详情 + adjust 端点、状态机 409、前端对齐

## 2. Git 状态

改动全部未提交（工作区，**直接在 master**）：

```text
 M platform-billing/BillingApplication.java
 M platform-billing/BillingController.java
 M platform-billing/bill/Bill.java
 M platform-billing/bill/BillGenerator.java
 M platform-billing/bill/BillService.java
 M platform-billing/bill/InMemoryBillRepository.java
 M platform-billing/bill/JdbcBillRepository.java
 M platform-billing/job/BillGeneratorJobHandler.java
 M platform-billing/test/.../BillingControllerTest.java
 M platform-billing/test/.../BillingGovernanceTest.java
 M platform-billing/test/.../BillingModuleMockMvcTest.java
 M platform-billing/test/.../it/M5EndToEndIntegrationTest.java
 M platform-billing/test/.../it/RepositoryContractTest.java
 M platform-ui/api/billing.ts
 M platform-ui/api/types.ts
 M platform-ui/views/BillingView.vue
?? db/migration/V014__bill_item.sql
?? db/rollback/U014__bill_item.sql
?? platform-billing/bill/BillItem.java
?? platform-billing/bill/BillItemRepository.java
?? platform-billing/bill/InMemoryBillItemRepository.java
?? platform-billing/bill/JdbcBillItemRepository.java
```

未触及：`.env`、密钥、证书、生产配置。未引入新依赖。无大批量删除。

## 3. 代码差异摘要

### 3.1 事实源聚合（任务主线，达成）
- `BillGenerator.generate` 签名移除 `logs` 参数，改为从注入的 `Supplier<List<ServiceInvokeLog>>`（即 `JdbcServiceInvokeLogRepository::findAll`）取调用日志。
- 按 `groupKey`（EXPENSE 按 consumerCode，SETTLEMENT 按 partnerCode/serviceCode）分组 → 每组算 `BillingUsage` → `ruleEngine.calculate` → 生成 BillItem。
- 总额 = `sum(items.amount)`，明细合计=总额（测试断言）。
- `BillGeneratorJobHandler` 同步移除 logs 参数。
- **修复 M7-A 偏离**：`GenerateBillRequest` 移除 `logs` 字段，请求体 logs 不再影响账单（MockMvc `billGenerateIgnoresTamperedRequestLogs` 验证）。

### 3.2 t_bill_item 表 + 仓储（达成）
- V014 建表：`bill_id/bill_no/item_type/ref_id/quantity/unit_price/amount/period/service_code/consumer_code/partner_code` + 3 索引。
- `BillItemRepository` 接口 + `JdbcBillItemRepository`（`saveAll` 先 DELETE 后批量 INSERT，幂等）+ `InMemoryBillItemRepository`。
- `Bill` 补 `items` 关联，`JdbcBillRepository.findByBillNo` 联查 items，`BillService.changeStatus` 保留 items。

### 3.3 新增端点（达成）
- `GET /bills/{billNo}` 详情（含明细）。
- `GET /billing/stats`：按 from/to/partnerId/consumerId 聚合费用统计（修复 M7-A F-04）。
- `POST /bills/{billNo}/adjust`：调整状态。
- 状态机非法转移 → `BILL_STATE_INVALID` → 409 Conflict（`statusChange` 包装）。

### 3.4 前端对齐（达成）
- `billing.ts` 加 `getBill`、`getBillingStats` 类型化；`types.ts` 加 `BillItem`/`BillingStats`，`Bill` 字段对齐后端。

## 4. 需求满足情况

依据 `tasks/codex-task-P0-06-bill-item.md` §2 与 §10：

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| R1 | t_bill_item 表 + 索引 | ✅ | V014 |
| R2 | generate 从调用日志聚合，不接收 logs | ✅ | BillGenerator 改造 + 篡改测试 |
| R3 | 明细合计=总额 | ✅ | 测试断言 sum(items)=total |
| R4 | GenerateBillRequest 移除 logs | ✅ | 同步前端 |
| R5 | Bill 补 items 关联 | ✅ | |
| R6 | GET /billing/stats | ✅ | 端点 + MockMvc 401/403/200 |
| R7 | 账单状态机异议/调整/确认受控 | ✅ | adjust + 409 |
| R8 | 明细一致性测试 | ✅ | BillingControllerTest + GovernanceTest |
| R9 | 事实源测试（篡改 logs 不影响） | ✅ | billGenerateIgnoresTamperedRequestLogs |
| R10 | 状态机 409 测试 | ✅ | billInvalidTransitionReturnsConflict |
| R11 | stats 端点测试 | ⚠️ | 200/401/403 ✅，但**按 partnerId/consumerId 聚合正确性未验证且实际有 bug**（见 5.1） |
| R12 | 输出聚合逻辑 + 明细证据 | ⚠️ | dev-progress 未更新 P0-06 章节（工作区 dev-progress 未改） |

## 5. 安全与风险检查

### 5.1 `/billing/stats` 的 partnerId/consumerId 过滤失效（中-高，功能缺陷，建议修复）
`BillingController.stats`：
```java
.filter(item -> partnerId == null
    || ("PARTNER".equals(item.itemType()) && String.valueOf(partnerId).equals(item.refId())))
.filter(item -> consumerId == null
    || ("CONSUMER".equals(item.itemType()) && String.valueOf(consumerId).equals(item.refId())))
```
而 `BillGenerator.itemFor` 中 `refId = String.valueOf(usage.targetId())`，`targetId = stableTargetId(targetCode)`（SHA-256 衍生的 long 哈希）。

- **`item.refId` 是 targetCode 的哈希值，`partnerId`/`consumerId` 是数据库主键数字，两者永远不相等**。
- 后果：传 partnerId 或 consumerId 时，过滤条件永远 false → **统计结果恒为空**（totalAmount=0, count=0）。
- 测试 `billingStatsWithAdminToken` 未传 partnerId/consumerId，只断言字段存在，故未暴露。
- **建议**：stats 过滤应基于 `consumerCode`/`partnerCode`（BillItem 已存这两列）或基于 refId 的稳定映射，而非用数据库 id 比 refId 哈希。需明确 stats 查询语义（按 code 还是按 id）并修正过滤逻辑 + 补带参测试。

### 5.2 V014 缺达梦版（中，三库一致性破坏）
- P0-02 起**所有迁移均有 `db/migration-dm/V0xx` 对等版**（V011/V012/V013 均有）。
- V014 仅在 `db/migration/`，**缺 `db/migration-dm/V014`**。
- 后果：达梦库迁移会停在 V013，`t_bill_item` 不建表 → 达梦环境账单明细功能不可用，破坏 P0-02 三库兼容目标。
- `db/migration-dm/V014` 需用达梦语法（V014 用了 `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`，达梦需确认兼容；DECIMAL/索引语法通用）。
- **建议**：补 `db/migration-dm/V014__bill_item.sql` + `U014`，并在 `MigrationDialectCompatibilityTest` 静态守护中纳入 t_bill_item。

### 5.3 U014 回到 `db/rollback/` 目录（低，违反既有统一）
- P0-03 RW-11 已统一：删除 `db/rollback/U0xx`，回滚脚本统一放 `db/migration/U0xx`（V011/V012/V013 的 U 均在 `db/migration/`）。
- P0-06 的 U014 又放回 `db/rollback/`，**退回旧约定**，与前三项不一致。
- **建议**：移到 `db/migration/U014__bill_item.sql`，删除 `db/rollback/U014`。

### 5.4 `unitPrice` 为平均价非真实单价（低-语义）
`BillGenerator.itemFor`：
```java
BigDecimal unitPrice = amount.divide(BigDecimal.valueOf(logs.size()), 6, ...);
```
- unitPrice = 总额/调用次数，是"平均每次费用"而非计费规则的单价。
- 计费规则可能有真实 unitPrice（如 BY_COUNT 的 1.00/次），这里用算术平均会与规则单价不一致（当规则有阶梯/包量时偏差更大）。
- **评估**：P0-06 范围是"明细合计=总额"，unitPrice 仅作展示。但语义上应取规则匹配的真实单价更合理。低优，建议后续从 `ruleEngine.calculate` 返回单价。

### 5.5 改动直接在 master 分支（流程问题）
- 任务单 §1 建议 `ai/p0-bill-item` 分支，CLAUDE.md §9 "不在审查未通过时提交主分支"。
- 当前改动直接堆在 master 工作区。虽未提交，但提交时若直接 commit master 会违反分支隔离。
- **建议**：提交前先 `git checkout -b ai/p0-bill-item`（或 `git switch -c`）把改动带到分支，审查通过后再 ff-merge master。

### 5.6 dev-progress 未更新（低）
- 工作区 `tasks/dev-progress.md` 未含 P0-06 章节（git status 未列该文件改动）。
- 任务 §10 要求"输出聚合逻辑说明 + 明细一致性证据"。
- **建议**：补 P0-06 章节（聚合逻辑、明细一致性证据、stats 过滤语义说明）。

### 5.7 无安全红线违反
- 金额用 BigDecimal + setScale(4, HALF_UP)；不写明文费用；未改 .env/密钥；未连生产库。

## 6. 测试检查

| 测试 | 结果 | 说明 |
|---|---|---|
| `mvn test`（全量 8 模块） | ✅ 全绿 | 193 测试 BUILD SUCCESS |
| `BillingControllerTest` | ✅ 3/3 | 含明细一致性（sum=total）、多调用聚合=3×单价 |
| `BillingGovernanceTest` | ✅ 7/7 | 含明细一致性、幂等 billNo、V014 迁移可执行 |
| `BillingModuleMockMvcTest` | ✅ 19/19 | 含篡改 logs 忽略、stats 401/403/200、状态机 409 |
| `RepositoryContractTest` | ✅ 3/3 | bill+item JDBC/内存一致性 |
| `M5EndToEndIntegrationTest` | ✅ 4/4 | 适配新 BillGenerator 签名 |
| 前端 `npm run test:unit` | 未本地复跑 | — |

**测试缺口**：stats 端点带 partnerId/consumerId 参数的聚合正确性无测试（恰好掩盖 5.1 的 bug）。

## 7. 审查结论

### 建议有条件通过

核心目标达成：账单从调用日志事实源聚合、生成 bill+bill_item、明细合计=总额、移除请求体 logs、新增 stats/详情/adjust 端点、状态机 409。193 测试全绿。架构清晰（BillItemRepository 接口 + JDBC/内存双实现，与 P0-03 一致）。

存在 1 个功能缺陷 + 2 个一致性破坏，建议合入前修复：
- **RW-1（中-高-功能）** `/billing/stats` partnerId/consumerId 过滤失效（refId 哈希 vs id 永不匹配）→ 传参统计恒空；
- **RW-2（中-一致性）** V014 缺达梦版 → 三库兼容破坏；
- **RW-3（低-一致性）** U014 回到 db/rollback → 与 P0-03 RW-11 统一冲突。

低优 3 项（unitPrice 语义、dev-progress 未更新、提交前建分支）。

未触及"暂不通过"红线。**RW-1 是真实功能 bug**（stats 按维度过滤不可用），建议必修。

## 8. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RW-1 | stats partnerId/consumerId 过滤失效 | 明确 stats 查询语义（按 consumerCode/partnerCode 或按 id 映射），修正过滤逻辑使传参能正确聚合；补带 partnerId/consumerId 参数的 stats 测试 | 中-高 |
| RW-2 | V014 缺达梦版 | 补 `db/migration-dm/V014__bill_item.sql` + `U014`（达梦语法），纳入 MigrationDialectCompatibilityTest 守护 | 中 |
| RW-3 | U014 目录不一致 | 移到 `db/migration/U014__bill_item.sql`，删除 `db/rollback/U014` | 低 |
| RW-4 | unitPrice 语义 | 从 ruleEngine 取真实单价而非算术平均（若规则引擎支持）；或文档标注 unitPrice 为平均价 | 低 |
| RW-5 | dev-progress 未更新 | 补 P0-06 章节：聚合逻辑、明细一致性证据、stats 语义说明 | 低 |
| RW-6 | 分支隔离 | 提交前建 `ai/p0-bill-item` 分支，不直接 commit master | 低（流程） |

## 9. 建议提交信息

返工后提交（在 `ai/p0-bill-item` 分支）：

```text
feat(P0-06): generate bills with itemized details from invoke log fact source

- Add t_bill_item table (V014/U014, mysql + dm parity) + BillItem/
  BillItemRepository with JDBC and in-memory implementations
- BillGenerator.generate no longer accepts logs; aggregates from
  t_service_invoke_log fact source via injected supplier, writes bill +
  items, sum(items.amount) == bill.totalAmount
- Remove logs from GenerateBillRequest (breaking); request body logs no
  longer affect billing
- Bill carries items; GET /bills/{billNo} returns details with items
- Add GET /billing/stats (from/to/partnerId/consumerId aggregation, F-04)
- Add POST /bills/{billNo}/adjust; invalid state transitions return 409
- Align BillingView/billing.ts/types.ts with bill items and stats

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**审查结论**：建议有条件通过（RW-1 功能 bug 建议必修，RW-2/RW-3 一致性建议同修）。
**是否需要 Codex 返工**：建议返工 RW-1~RW-3；RW-1 为功能缺陷必修。
**是否建议提交**：返工 RW-1~RW-3 后提交；提交前先建分支。
