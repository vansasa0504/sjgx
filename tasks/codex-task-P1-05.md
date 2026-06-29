# Codex 执行任务 - P1-05：财务适配

> 阶段：P1（验收增强）
> 任务编号：P1-05
> 分支：`ai/p1-finance-adapter`（已从 master 切出，master 已含 P1-04，最新迁移 V020）
> 依据：`tasks/claude-plan-P1-05.md`（第一性原理计划，权威）、`docs/requirements.md` §2.7（FR-705）、`docs/github-reference-functional-design.md` §9（FinanceSyncAdapter/PurchaseContractAdapter/FinanceSyncRecord）
> 前置：P0-06（账单明细）已合入 master；P1-04 审查通过并合入 master
> 日期：2026-06-29

---

## 1. 背景与目标

现有 `FinanceSystemAdapter`/`MockFinanceSystemAdapter`/`FinanceSyncResult` 已存在但**未装配为 Bean、未被调用**（财务同步回执不入库），无 `PurchaseContractAdapter`，无同步记录落库，无重试机制，无财务同步端点。`t_bill` 表无 external_no/sync 状态字段。验收要求"mock 成功/失败/重试通过"+ 回执入库 + 审计。

财务/采购系统接口规范待 Q-06 明确（`docs/requirements.md` 第227行），本期做**框架 + mock**：同步记录持久化 + 推送回执入库 + 重试 + 采购适配器 + 审计。

**最小可行结果**：
1. 财务同步记录持久化（`t_finance_sync_record`）。
2. 确认账单推送财务（FinanceSystemAdapter.sync）→ 回执入库（SUCCESS/FAILED）。
3. 失败可重试（retryCount 累加，重试成功转 SUCCESS）。
4. 采购合同对接适配器（PurchaseContractAdapter + mock）。
5. 同步记录查询 + 审计事件（`BillSyncedToFinance`）。
6. 持久化重启可查。

## 2. 范围

### 本次实现

- **F-1 同步记录表 + 仓储**：新增 `t_finance_sync_record(bill_no, adapter_type, external_no, status, retry_count, message, synced_at)` + JdbcFinanceSyncRepository + InMemoryFinanceSyncRepository。
- **F-2 推送财务**：FinanceSyncService.sync(billNo, adapterType) 校验账单状态 → 调用对应 adapter（FINANCE→FinanceSystemAdapter，PURCHASE→PurchaseContractAdapter）→ 回执持久化（SUCCESS/FAILED）。
- **F-3 重试**：FinanceSyncService.retry(billNo, adapterType) 取该 billNo+adapterType 最近一条 FAILED 记录 retryCount，新记录 retryCount=上次+1，重新调用 adapter；重试成功转 SUCCESS。
- **F-4 采购适配器**：新增 `PurchaseContractAdapter` 接口 + `MockPurchaseContractAdapter`（复用 FinanceSyncResult）。
- **F-5 状态校验**：账单状态 CONFIRMED/ADJUSTED/SETTLED 可推送；GENERATED/DISPUTED → BILL_STATE_INVALID（409）。
- **F-6 同步记录查询**：GET /billing/bills/{billNo}/sync 按 billNo 返回记录列表。
- **F-7 端点**：
  - `POST /api/v1/billing/bills/{billNo}/sync`（billing:run）query: adapterType?（默认 FINANCE）→ FinanceSyncRecord
  - `POST /api/v1/billing/bills/{billNo}/sync/retry`（billing:run）query: adapterType? → FinanceSyncRecord
  - `GET /api/v1/billing/bills/{billNo}/sync`（billing:view）→ List<FinanceSyncRecord>
- **F-8 审计**：sync/retry 写 `BILL_SYNC_TO_FINANCE` 审计事件（单一 traceId 串联 sync 动作，借鉴 P1-04 P2-2）。

### 不做

- 不做真实财务/采购对接（Q-06 待定，mock 框架）。
- 不做自动推送（账单确认自动触发，本任务手动触发）。
- 不做对账文件导出（BillExportAdapter 留后续）。
- 不做签名/加密传输（待 Q-06）。
- 不做退避/定时重试（简单 retryCount 累加，留后续）。
- 不改 `t_bill` 既有结构（externalNo 落 sync record）。
- 不重构既有 `FinanceSystemAdapter`/`MockFinanceSystemAdapter` 接口（仅装配 + 调用）。
- 不新增权限码（sync/retry 复用 `billing:run`，query 复用 `billing:view`）。
- 前端展示可选（后端端点先就绪）。

## 3. 必读输入

- `AGENTS.md`、`tasks/claude-plan-P1-05.md`（权威计划）
- `docs/requirements.md` §2.7（FR-705）、Q-06（第227行）
- `docs/github-reference-functional-design.md` §9.3（FinanceSyncAdapter/PurchaseContractAdapter/FinanceSyncRecord）、§9.4（mock 成功/失败/重试/回执入库）
- `platform-billing/src/main/java/.../finance/FinanceSystemAdapter.java`、`FinanceSyncResult.java`、`MockFinanceSystemAdapter.java`（已存在，需装配 + 新增 Purchase）
- `platform-billing/src/main/java/.../bill/Bill.java`、`BillService.java`、`BillStateMachine.java`、`JdbcBillRepository.java`、`BillRepository.java`
- `platform-billing/src/main/java/.../model/BillStatus.java`
- `platform-billing/src/main/java/.../BillingController.java`、`BillingApplication.java`
- `platform-billing/src/main/java/.../report/RegulatoryReportService.java`（P1-04 持久化+adapter 失败回退+审计 traceId 模板，**重点参考**）
- `platform-billing/src/main/java/.../report/JdbcRegulatoryReportRepository.java`（JDBC 持久化模板）
- `platform-common/src/main/java/.../audit/AuditEvent.java`、`AuditLogRepository.java`
- `platform-common/src/main/java/.../db/IdGenerator.java`
- `platform-common/src/main/java/.../exception/BusinessException.java`、`security/PermissionCodes.java`
- `db/migration/V020__regulatory_report.sql` + U020（最新表模板）、`db/migration/V008__governance.sql`（t_bill 现状）
- `platform-common/src/test/java/.../db/MigrationDialectCompatibilityTest.java`

## 4. 需要修改的模块

| 文件 | 改动 |
|---|---|
| 新增 `FinanceSyncRecord.java` | 同步记录模型 record |
| 新增 `FinanceSyncRepository.java` + Jdbc/InMemory 实现 | 同步记录仓储（save/findLastFailed/findByBillNo） |
| 新增 `FinanceSyncService.java` | sync/retry/query + 状态校验 + 审计 |
| 新增 `PurchaseContractAdapter.java` + `MockPurchaseContractAdapter.java` | 采购适配器 |
| `BillingController.java` | sync/retry/query 端点 |
| `BillingApplication.java` | 新仓储/服务/Finance/Purchase adapter Bean 装配 |
| `db/migration/V021__finance_sync_record.sql` + U021 | t_finance_sync_record |
| `db/migration-dm/V021__finance_sync_record.sql` + U021 | 达梦版 |
| `MigrationDialectCompatibilityTest.java` | 纳入 V021 + t_finance_sync_record CRUD |
| 新增 `FinanceSyncServiceTest.java`、`FinanceSyncRepositoryJdbcTest.java` | 成功/失败/重试/状态/404 测试 |
| `BillingModuleMockMvcTest.java` | sync/retry/query MockMvc |

## 5. 数据库/API 影响

### 5.1 数据库
- 新增 `t_finance_sync_record(id, bill_no, adapter_type, external_no, status, retry_count, message, synced_at)` + idx_finance_sync_bill。
- V021/U021 MySQL + 达梦双库；避开 ` LIMIT `/DM ` TEXT`(带空格)/` TINYINT ` 方言守护。

### 5.2 API
- `POST /api/v1/billing/bills/{billNo}/sync`（billing:run）→ FinanceSyncRecord
- `POST /api/v1/billing/bills/{billNo}/sync/retry`（billing:run）→ FinanceSyncRecord
- `GET /api/v1/billing/bills/{billNo}/sync`（billing:view）→ List<FinanceSyncRecord>

## 6. 实现细节约束

### 6.1 同步记录模型
```java
public record FinanceSyncRecord(
    long id, String billNo, String adapterType, String externalNo,
    String status, int retryCount, String message, Instant syncedAt) {}
```
- adapterType: FINANCE/PURCHASE
- status: SUCCESS/FAILED

### 6.2 适配器（Finance 已存在，新增 Purchase）
```java
public interface PurchaseContractAdapter {
    FinanceSyncResult sync(Bill bill);   // 复用 FinanceSyncResult(success, externalNo, message)
}
public class MockPurchaseContractAdapter implements PurchaseContractAdapter {
    public FinanceSyncResult sync(Bill bill) {
        return new FinanceSyncResult(true, "PUR-" + bill.billNo(), "mock purchase synced");
    }
}
```
- MockFinanceSystemAdapter 已存在（返回 success + "FIN-"+billNo），仅装配为 Bean。

### 6.3 sync 逻辑
- FinanceSyncService 注入 Map<String, Object> adapter 映射（FINANCE→FinanceSystemAdapter，PURCHASE→PurchaseContractAdapter）+ BillRepository + FinanceSyncRepository + AuditLogRepository。
- sync(billNo, adapterType)：
  1. 校验 adapterType（非法抛 FINANCE_SYNC-400）。
  2. 取账单 `billRepository.findByBillNo(billNo)`，不存在抛 BILL-404。
  3. 校验状态：CONFIRMED/ADJUSTED/SETTLED 可推送；否则抛 BILL_STATE_INVALID（409）。
  4. 调用 adapter.sync(bill) → 回执；try/catch：adapter 抛异常 → FAILED + ex.message（不向上抛）。
  5. 持久化记录（retryCount=0，status 由回执决定）。
  6. 写审计 BILL_SYNC_TO_FINANCE（status=SUCCESS/FAILED，单一 traceId）。
  7. 返回 FinanceSyncRecord。

### 6.4 retry 逻辑
- retry(billNo, adapterType)：
  1. 同 sync 校验账单存在 + 状态。
  2. 查该 billNo+adapterType 最近一条 FAILED 记录（repository.findLastFailed）；无则抛 FINANCE_SYNC-409（无可重试的失败记录）。
  3. 新记录 retryCount = 上次.retryCount + 1。
  4. 调用 adapter.sync(bill) → 回执；持久化（SUCCESS/FAILED）。
  5. 写审计（traceId 与本次 retry 关联）。
  6. 返回记录。

### 6.5 状态校验
- 可推送状态集合：`EnumSet.of(CONFIRMED, ADJUSTED, SETTLED)`。
- 不可推送（GENERATED/DISPUTED）→ BILL_STATE_INVALID（409，与既有 BillService 状态异常码一致）。

### 6.6 审计
- 事件类型 `BILL_SYNC_TO_FINANCE`，targetType=BILL，targetId=billNo，action=SYNC/RETRY，status=SUCCESS/FAILED。
- 单一 traceId 串联（service 方法入口生成，借鉴 P1-04 P2-2 修复，避免各事件 traceId 不一致）。

### 6.7 方言守护
- V021 SQL 不含 ` LIMIT `（带空格）；DM 不含 ` TEXT`(带空格)/` TINYINT `。
- 每次同步 INSERT 一条（不去重），无 upsert 竞态。
- retry_count 用 INT（MySQL/达梦通用）。

### 6.8 仓储方法
```java
public interface FinanceSyncRepository {
    FinanceSyncRecord save(FinanceSyncRecord record);
    Optional<FinanceSyncRecord> findLastFailed(String billNo, String adapterType);
    List<FinanceSyncRecord> findByBillNo(String billNo);
}
```
- findLastFailed：`WHERE bill_no=? AND adapter_type=? AND status='FAILED' ORDER BY synced_at DESC, id DESC LIMIT 1`。
- 注意：`LIMIT 1` 在仓储 SQL 中可用（非迁移脚本），达梦兼容 LIMIT（P0-02 已验证 invoke log 仓储用 LIMIT/OFFSET）。

## 7. 必须补充的测试

- **成功同步**：CONFIRMED 账单 → sync → status=SUCCESS + externalNo 非空 + 持久化。
- **失败同步**：mock adapter 返回 success=false → status=FAILED + message + 持久化。
- **adapter 抛异常**：mock adapter 抛 RuntimeException → status=FAILED + ex.message，不向上抛。
- **重试累加**：FAILED 记录 → retry → retryCount=1；再 FAILED → retry → retryCount=2；重试成功转 SUCCESS。
- **重试无失败记录**：无 FAILED → retry → FINANCE_SYNC-409。
- **状态校验**：GENERATED/DISPUTED 账单 sync → BILL_STATE_INVALID（409）。
- **账单不存在**：sync → BILL-404。
- **采购适配器**：adapterType=PURCHASE → 走 PurchaseContractAdapter，externalNo 前缀 PUR-。
- **非法 adapterType**：sync → FINANCE_SYNC-400。
- **查询**：GET /sync 返回该 billNo 记录列表（按时间）。
- **重启可查**：JDBC sync → 新仓储实例查回。
- **MockMvc**：sync/retry/query 200 + 401/403 + 409（状态/无失败记录）+ 404。
- **三库迁移**：MigrationDialectCompatibilityTest 纳入 V021，t_finance_sync_record CRUD 通过。
- **审计**：sync 写 BILL_SYNC_TO_FINANCE 事件（可查）。

## 8. 验收命令

```bash
mvn -pl platform-common install -DskipTests   # 若 common 改动
mvn test -pl platform-billing
mvn test                                       # 全量回归
```

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| 财务接口 Q-06 未定 | 框架 + mock，真实对接待规范 |
| 重试语义 | 简单 retryCount 累加，退避/定时留后续 |
| adapter 失败未持久化 | 失败记 FAILED + message，不抛异常 |
| 并发同步重复 | 每次快照 INSERT，无竞态 |
| 状态校验遗漏 | sync 前校验 BillStatus，非法 409 |
| **回滚** | V021 有 U021；独立表 + 新端点，移除不影响既有 billing 功能 |

## 10. 完成判定

- [ ] t_finance_sync_record（V021/U021 双库）。
- [ ] 推送财务回执入库（SUCCESS/FAILED）。
- [ ] 重试累加（retryCount+1，重试成功转 SUCCESS）。
- [ ] 采购适配器框架（PurchaseContractAdapter + mock）。
- [ ] 账单状态校验（不可推送 409）。
- [ ] 同步记录查询 + 持久化重启可查。
- [ ] 三库迁移测试纳入 V021。
- [ ] sync 审计事件（trace_id 串联）。
- [ ] mvn test 全绿。
- [ ] 输出对接设计 + 成功/失败/重试证据 + 潜在风险。

## 11. 实现边界（Codex 遵守）

1. 同步记录用独立 `t_finance_sync_record`，不改 `t_bill` 既有结构。
2. V021/U021 MySQL + 达梦双库；避开方言守护（` LIMIT `、DM ` TEXT`/` TINYINT `）。
3. Repository 用 IdGenerator + INSERT（每次同步一条，不去重）。
4. 复用 `FinanceSystemAdapter`/`MockFinanceSystemAdapter`（已存在，仅装配）；新增 `PurchaseContractAdapter` + Mock。
5. adapter 失败/抛异常 → FAILED + message，不向上抛（借鉴 P1-04 报送回退）。
6. 重试基于上次 FAILED 记录 retryCount 累加。
7. 审计事件单一 traceId 串联（借鉴 P1-04 P2-2 修复）。
8. 不做真实对接/自动推送/对账导出/签名加密/退避重试；不新增权限码。
9. 不改密钥/生产配置；不动无关模块；不跳过测试。
10. 完成后输出修改文件、测试命令、测试结果、对接设计、成功/失败/重试证据、潜在风险。

## 12. 借鉴说明

借鉴 github-reference-functional-design.md §9.3 的 FinanceSyncAdapter/PurchaseContractAdapter/FinanceSyncRecord 设计（同步可重试、回执可入库、事件 `BillSyncedToFinance`）。同步记录独立表 + adapter 失败回退 + 审计 traceId 串联均借鉴 P1-03/P1-04 经验。详见 `docs/requirements.md` §2.7、`docs/development-process-workflow.md` §6.7。
