# 第一性原理开发计划 — P1-05 财务适配

> 任务编号：P1-05
> 分支：`ai/p1-finance-adapter`（已从 master 切出，master 已含 P1-04，最新迁移 V020）
> 依据：`tasks/claude-plan-P1.md` §3、`docs/requirements.md` §2.7（FR-705）、`docs/development-process-workflow.md` §6.7、`docs/github-reference-functional-design.md` §9（财务适配 + FinanceSyncRecord）
> 前置：P0-06（账单明细）已合入 master；P1-04 审查通过并合入 master
> 日期：2026-06-29

---

## 1. 需求来源

- **技术要求**：计费管理（FR-701~705）要求账单核对后与财务/采购系统对接。验收要求"财务/采购系统对接框架 + mock 成功/失败/重试"。
- **验收口径**：P1-05 通过标准"mock 成功/失败/重试通过"。
- **参考**：github-reference-functional-design.md §9.3 —— FinanceSyncAdapter、PurchaseContractAdapter、FinanceSyncRecord；§9.4 测试 "财务同步测试 mock 成功、失败、重试、回执入库"；事件 `BillSyncedToFinance`。
- **待定口径**：财务/采购系统接口规范待 Q-06 明确（`docs/requirements.md` 第227行），本期仅做账单生成 + 对接适配框架 + mock。
- **现状缺口**：
  - `FinanceSystemAdapter` / `MockFinanceSystemAdapter` / `FinanceSyncResult` 已存在，但**未装配为 Bean、未被任何代码调用**。
  - 无 `PurchaseContractAdapter`（采购合同对接适配器，§9.3 列出但未实现）。
  - **无同步记录落库**：财务同步结果（成功/失败/重试/回执 externalNo）不入库，重启丢失、无审计可追溯。
  - `t_bill` 表无 external_no / sync 状态字段。
  - 无财务同步端点、无重试机制。
  - 账单确认后无自动/手动推送财务的链路。

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？
账单确认后可对接财务/采购系统，同步结果（成功/失败/重试/回执）可入库、可查询、可重试、可审计，让验收方确认"对接框架就位 + mock 成功/失败/重试通过"。

### 2.2 最小可行结果
1. 财务同步记录持久化（`t_finance_sync_record`，含 externalNo/状态/重试次数/回执）。
2. 确认账单推送财务（调用 FinanceSystemAdapter.sync）→ 回执入库（SUCCESS/FAILED）。
3. 失败可重试（基于上次记录重试，重试次数累加）。
4. 采购合同对接适配器（PurchaseContractAdapter，mock 框架）。
5. 同步记录查询 + 审计事件（`BillSyncedToFinance`）。

### 2.3 系统必须接收哪些输入？
- 账单号（billNo，已 CONFIRMED）。
- 对接类型（FINANCE/PURCHASE，可选，默认 FINANCE）。
- 事实源：`BillRepository`（取账单）。

### 2.4 系统必须产生哪些输出？
- `FinanceSyncRecord`：账单号/对接类型/externalNo/状态/重试次数/回执信息/同步时间，持久化。
- 审计事件 `BILL_SYNC_TO_FINANCE`（trace_id 链路）。

### 2.5 不可省略的处理过程？
1. 校验账单存在且状态合法（CONFIRMED/ADJUSTED/SETTLED 可推送；GENERATED/DISPUTED 不可）。
2. 调用 `FinanceSystemAdapter.sync(bill)` → 回执（success/externalNo/message）。
3. 失败可重试：记录 FAILED，下次 sync 基于上次记录 retryCount+1。
4. 持久化同步记录（每次同步一条）。
5. 成功时回写账单 externalNo（可选，落 sync record 即可，不改 Bill 表结构）。
6. 写审计事件。

### 2.6 哪些是核心能力？
- 同步记录持久化（`t_finance_sync_record`）。
- 推送财务（FinanceSystemAdapter.sync）+ 回执入库。
- 重试（失败后可重试，次数累加）。
- 采购适配器框架（PurchaseContractAdapter mock）。

### 2.7 哪些是增强能力？
- 真实财务/采购对接（Q-06 待定，mock）。
- 自动推送（账单确认自动触发，本任务手动触发）。
- 对账文件导出（BillExportAdapter，留后续）。
- 签名/加密传输（待 Q-06）。

### 2.8 最小改动路径？
- **新增 `t_finance_sync_record` 表**（bill_no/adapter_type/external_no/status/retry_count/message/synced_at）。
- **新增 FinanceSyncRecord/FinanceSyncRepository**（JDBC+内存）+ FinanceSyncService（sync/retry/query）。
- **新增 PurchaseContractAdapter**（接口 + MockPurchaseContractAdapter，§9.3 要求）。
- **装配** FinanceSystemAdapter / PurchaseContractAdapter 为 Bean（MockFinanceSystemAdapter 已存在）。
- **端点**：POST /billing/bills/{billNo}/sync（推送+回执）、POST /billing/bills/{billNo}/sync/retry（重试）、GET /billing/bills/{billNo}/sync（同步记录）。
- **不改 `t_bill` 结构**（externalNo 落 sync record，最小侵入）。
- **复用**：BillRepository 取账单、AuditLogRepository 写审计、IdGenerator + JdbcTemplate 持久化（借鉴 P1-03/P1-04）。

### 2.9 如何测试？
- 成功：CONFIRMED 账单 → POST /sync → SUCCESS + externalNo + 持久化。
- 失败：mock adapter 返回失败 → FAILED + message + 持久化。
- 重试：FAILED 记录 → retry → retryCount 累加；重试成功转 SUCCESS。
- 状态校验：GENERATED/DISPUTED 账单 sync → 409（状态非法）。
- 账单不存在 → 404。
- 查询：GET /sync 返回该账单同步记录列表。
- MockMvc：sync/retry/query 200 + 401/403 + 状态 409 + 404。
- 三库迁移：MigrationDialectCompatibilityTest 纳入 V021。

### 2.10 如何验收？
mock 成功/失败/重试通过 + 回执入库 + 审计可追溯。

### 2.11 如何避免过度设计？
- 不做真实财务/采购对接（Q-06 待定，mock 框架）。
- 不做自动推送（手动触发）。
- 不做对账文件导出（BillExportAdapter 留后续）。
- 不做签名/加密传输（待 Q-06）。
- 不改 `t_bill` 结构（externalNo 落独立 sync record）。
- 不重构既有 `FinanceSystemAdapter` 接口（仅装配 + 调用）。
- 重试用简单 retryCount 累加（不做退避/定时重试，留后续）。

## 3. 功能拆解

| 编号 | 功能 | 说明 |
|---|---|---|
| F-1 | 同步记录表 + 仓储 | t_finance_sync_record + JdbcFinanceSyncRepository + 内存实现 |
| F-2 | 推送财务 | FinanceSyncService.sync(billNo, adapterType) 调用 adapter → 回执持久化（SUCCESS/FAILED） |
| F-3 | 重试 | FinanceSyncService.retry(billNo) 基于上次 FAILED 记录 retryCount+1 重试 |
| F-4 | 采购适配器 | PurchaseContractAdapter 接口 + MockPurchaseContractAdapter |
| F-5 | 状态校验 | 账单状态合法才可 sync（CONFIRMED/ADJUSTED/SETTLED）；非法 409 |
| F-6 | 同步记录查询 | GET /billing/bills/{billNo}/sync（按 billNo） |
| F-7 | 端点 | sync/retry/query，权限 billing:run（sync/retry）/ billing:view（query） |
| F-8 | 审计 | sync 写 BILL_SYNC_TO_FINANCE 审计事件（trace_id 链路） |

## 4. 影响模块

| 模块 | 改动 |
|---|---|
| platform-billing.finance | 装配 MockFinanceSystemAdapter；新增 PurchaseContractAdapter + MockPurchaseContractAdapter；新增 FinanceSyncRecord/FinanceSyncRepository/Jdbc/InMemory/FinanceSyncService |
| platform-billing.BillingController | 新增 sync/retry/query 端点 |
| platform-billing.BillingApplication | 新仓储/服务/adapter Bean 装配 |
| db/migration + db/migration-dm | V021 t_finance_sync_record + U021 |
| platform-common.db | MigrationDialectCompatibilityTest 纳入 V021 |
| platform-ui | BillingView 财务同步演示（可选，后端先就绪） |

## 5. 接口设计

### 5.1 模型

```java
public record FinanceSyncRecord(
    long id,
    String billNo,
    String adapterType,    // FINANCE/PURCHASE
    String externalNo,
    String status,         // SUCCESS/FAILED
    int retryCount,
    String message,
    Instant syncedAt) {}
```

### 5.2 适配器（新增 Purchase，Finance 已存在）

```java
public interface PurchaseContractAdapter {
    FinanceSyncResult sync(Bill bill);   // 复用 FinanceSyncResult
}
```

### 5.3 API

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `POST /api/v1/billing/bills/{billNo}/sync` | POST | billing:run | 推送财务+回执（query: adapterType? 默认 FINANCE） |
| `POST /api/v1/billing/bills/{billNo}/sync/retry` | POST | billing:run | 重试上次失败（query: adapterType?） |
| `GET /api/v1/billing/bills/{billNo}/sync` | GET | billing:view | 同步记录列表 |

> 权限说明：sync/retry 属写操作，复用既有 `billing:run`；query 用 `billing:view`。不新增权限码。

## 6. 数据结构

### 6.1 t_finance_sync_record（V021）

```sql
CREATE TABLE t_finance_sync_record (
    id BIGINT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    adapter_type VARCHAR(32) NOT NULL,       -- FINANCE/PURCHASE
    external_no VARCHAR(128),
    status VARCHAR(32) NOT NULL,             -- SUCCESS/FAILED
    retry_count INT NOT NULL,
    message VARCHAR(512),
    synced_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_finance_sync_bill ON t_finance_sync_record(bill_no, synced_at);
```

### 6.2 同步状态机
- 账单可推送状态：CONFIRMED / ADJUSTED / SETTLED。
- 不可推送：GENERATED / DISPUTED → BILL_STATE_INVALID（409）。
- sync 记录状态：SUCCESS / FAILED。
- 重试：取该 billNo+adapterType 最近一条 FAILED 记录的 retryCount，新记录 retryCount = 上次+1。

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 账单不存在 | 404（BILL-404） |
| 账单状态不可推送 | 409（BILL_STATE_INVALID） |
| adapter 失败 | status=FAILED + message 入库，不抛异常 |
| 无可重试的 FAILED 记录 | 409（FINANCE_SYNC-409：无失败记录可重试） |
| adapter 抛异常 | 捕获→FAILED + ex.message，不向上抛 |
| 并发 sync | 各自 INSERT 一条记录（不去重，时间快照） |
| adapterType 非法 | 400（FINANCE_SYNC-400） |

## 8. 测试策略

| 测试 | 覆盖 |
|---|---|
| 成功同步 | CONFIRMED 账单 → sync → SUCCESS + externalNo + 持久化 |
| 失败同步 | mock adapter 返回失败 → FAILED + message + 持久化 |
| 重试累加 | FAILED → retry → retryCount+1；重试成功转 SUCCESS |
| 重试无失败记录 | retry 无 FAILED → 409 |
| 状态校验 | GENERATED/DISPUTED sync → 409 |
| 账单不存在 | sync → 404 |
| 采购适配器 | adapterType=PURCHASE → 走 PurchaseContractAdapter |
| 查询 | GET /sync 返回记录列表 |
| MockMvc | sync/retry/query 200 + 401/403 + 409 + 404 |
| 三库迁移 | MigrationDialectCompatibilityTest 纳入 V021，t_finance_sync_record CRUD |
| 审计 | sync 写 BILL_SYNC_TO_FINANCE 事件 |
| 重启可查 | JDBC sync → 新仓储查回 |

## 9. Codex 实现边界

1. 同步记录用独立 `t_finance_sync_record` 表，不改 `t_bill` 既有结构。
2. V021/U021 MySQL + 达梦双库，避开 ` LIMIT `/DM ` TEXT`(带空格)/` TINYINT ` 方言守护。
3. Repository 用 IdGenerator + INSERT（每次同步一条，不去重）。
4. 复用 `FinanceSystemAdapter`/`MockFinanceSystemAdapter`（已存在，仅装配）；新增 `PurchaseContractAdapter` + Mock 实现。
5. FinanceSyncService 注入 adapter 映射（adapterType→adapter）+ BillRepository + FinanceSyncRepository + AuditLogRepository。
6. adapter 失败/抛异常 → FAILED + message，不向上抛（借鉴 P1-04 报送回退）。
7. 重试基于上次 FAILED 记录 retryCount 累加。
8. 审计事件单一 traceId 串联（借鉴 P1-04 P2-2 修复）。
9. 不做真实对接/自动推送/对账导出/签名加密/退避重试。
10. 不改密钥/生产配置；不动无关模块；不跳过测试；必须补测试并全绿。

## 10. 验收标准

- [ ] t_finance_sync_record（V021/U021 双库）。
- [ ] 推送财务回执入库（SUCCESS/FAILED）。
- [ ] 重试累加（retryCount+1，重试成功转 SUCCESS）。
- [ ] 采购适配器框架（PurchaseContractAdapter + mock）。
- [ ] 账单状态校验（不可推送 409）。
- [ ] 同步记录查询 + 持久化重启可查。
- [ ] 三库迁移测试纳入 V021。
- [ ] sync 审计事件（trace_id 串联）。
- [ ] mvn test 全绿。
- [ ] 输出对接设计 + 成功/失败/重试证据。

## 11. 风险与回滚

| 风险 | 控制 |
|---|---|
| 财务接口 Q-06 未定 | 框架 + mock，真实对接待规范 |
| 重试语义 | 简单 retryCount 累加，退避/定时留后续 |
| adapter 失败未持久化 | 失败记 FAILED + message，不抛异常 |
| 并发同步重复 | 每次快照 INSERT，无竞态 |
| 状态校验遗漏 | sync 前校验 BillStatus，非法 409 |
| **回滚** | V021 有 U021；独立表 + 新端点，移除不影响既有 billing 功能 |

## 12. 借鉴说明

借鉴 github-reference-functional-design.md §9.3 的 FinanceSyncAdapter/PurchaseContractAdapter/FinanceSyncRecord 设计（同步可重试、回执可入库、事件 `BillSyncedToFinance`）。同步记录独立表 + adapter 失败回退 + 审计 traceId 串联均借鉴 P1-03/P1-04 经验。详见 `docs/requirements.md` §2.7、`docs/development-process-workflow.md` §6.7。
