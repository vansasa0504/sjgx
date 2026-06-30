# Claude Code 返工复审 — P1-05 财务适配（P2-1 / P2-2）

## 1. 审查对象

- 任务：P1-05 返工（`reviews/claude-review-P1-05.md` 遗留 P2-1、P2-2）
- 分支：`ai/p1-finance-adapter`（改动在工作区未提交）
- 返工任务来源：`tasks/progress-2026-06-29.md` §三 待办 1/2
- 审查日期：2026-06-30
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（金额/计费场景，必做）

### 返工项

| 编号 | 问题 | 返工要求 |
|---|---|---|
| P2-1 | 异常码 `BILL_STATE_INVALID` 不符后缀规约 | 改为 `BILL-409`，移除 `GlobalExceptionHandler` magic string 特判，同步状态机/服务/测试 |
| P2-2 | retryCount 语义不清 | 明确"最近失败序列计数"语义，补跨序列测试 |

## 2. Git 状态

```text
M platform-billing/.../BillingController.java            # 特判 BILL_STATE_INVALID → BILL-409
M platform-billing/.../bill/BillStateMachine.java        # 异常码 → BILL-409
M platform-billing/.../finance/FinanceSyncService.java   # 异常码 → BILL-409 + retryCount 语义注释
M platform-billing/test/.../BillingModuleMockMvcTest.java        # 断言 → BILL-409
M platform-billing/test/.../finance/FinanceSyncServiceTest.java  # +跨序列测试 + 断言 → BILL-409
M platform-common/.../exception/GlobalExceptionHandler.java      # 移除 magic string 特判
M platform-common/test/.../GlobalExceptionHandlerTest.java       # 断言 → BILL-409
```

改动 7 文件，+34/-9 行，范围与返工任务单一致，无越界。

## 3. 常规审查

### 3.1 P2-1 异常码统一

- `BillStateMachine`：`BILL_STATE_INVALID` → `BILL-409`。✓
- `FinanceSyncService.requireSyncableBill`：`BILL_STATE_INVALID` → `BILL-409`。✓
- `BillingController.statusChange`：特判 `"BILL-409"` 返回 CONFLICT，其他异常 re-throw。✓
- `GlobalExceptionHandler`：移除 `|| "BILL_STATE_INVALID".equals(code)`，统一靠 `code.endsWith("409")` 命中 CONFLICT。✓
- 测试断言（`BillingModuleMockMvcTest` 2 处、`FinanceSyncServiceTest` 1 处、`GlobalExceptionHandlerTest` 1 处）同步更新。✓

### 3.2 P2-2 retryCount 语义

- `FinanceSyncService.retry` 增注释："retryCount is scoped to the latest failed sync sequence; a new manual sync starts a new sequence at 0." ✓
- 新测试 `retryCountIsScopedToLatestFailedSequence`：FAILED(0)→retry SUCCESS(1)→sync FAILED(0)→retry SUCCESS(1)，断言 retryCount 在新 manual sync 后重置。✓
- 既有测试 `adapterExceptionIsPersistedAsFailedAndRepeatedRetryCountsIncrement` 覆盖连续失败累加（third.retryCount()==2）。✓

## 4. 对抗式审查

### 4.1 攻击面枚举

1. 异常码改名波及所有状态机/同步失败返回路径。
2. `GlobalExceptionHandler` 规则简化，`endsWith("409")` 可能误伤其他码。
3. `retryCount` 跨序列重置逻辑。
4. `findLastFailed` 内存与 JDBC 双实现语义一致性。
5. `BillingController` 特判与 `GlobalExceptionHandler` 重复处理。

### 4.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| `endsWith("409")` 误伤非 CONFLICT 语义码 | 枚举全仓 `BusinessException` 码：`AUTH-409`(replay)/`USER-409`/`ROLE-409`/`PARTNER-409`/`QUALITY-409`/`CONSUMER-409`/`SERVICE-409`/`BILL-409`/`FINANCE_SYNC-409`/`CATALOG_APP-409` 均为 CONFLICT 语义；`AUTH-409` 旧逻辑也已命中 409→CONFLICT，无回归 | 已反驳 |
| retryCount 跨序列未重置 | 追 `sync`→`doSync(...,0,...)`、`retry`→`findLastFailed.retryCount()+1`；新测试覆盖 sync/retry/sync/retry 交替，断言重置 | 已反驳 |
| JDBC 与内存 `findLastFailed` 语义分歧导致双写不一致 | 内存 `filter FAILED + max(syncedAt,id)`；JDBC `WHERE status=FAILED ORDER BY synced_at DESC, id DESC LIMIT 1`，等价 | 已反驳 |
| 连续失败 retryCount 累加错误 | `retry` 每次取最新 FAILED 的 retryCount+1；既有测试 third.retryCount()==2 覆盖 | 已反驳 |
| `BillingController` 特判与全局处理器冲突 | controller 特判返回 CONFLICT，其他 re-throw；全局处理器也映射 BILL-409→CONFLICT，两路径一致 | 已反驳（冗余但无害，见 P3-1） |
| `429`(CONSUMER-429/SERVICE-429) 被误映射 | 429 不 endsWith 401/403/404/409 → 落 BAD_REQUEST；但此为**既有行为**（旧逻辑同样落 BAD_REQUEST），非本次返工引入 | 已反驳（非回归，见 P3-3） |

### 4.3 存活缺陷

**无 P1 阻断项、无 P2 改进项。** 存活项均为 P3 提示，不阻断合入：

- **P3-1（提示）**：`BillingController.statusChange` 的 `"BILL-409"` 特判现已冗余——`GlobalExceptionHandler` 已统一映射 `*-409`→CONFLICT，controller 特判可移除，让异常冒泡由全局处理器处理。不影响正确性，留后续清理。
- **P3-2（提示，既有问题）**：`retry` 前置条件仅检查"存在 FAILED 记录"，未检查"最近一次同步是否为 FAILED"。场景：sync FAILED(0)→retry SUCCESS(1)→再次 retry，`findLastFailed` 仍返回历史 FAILED(0)，retry 不会被拒绝且 retryCount 回退为 1。此为**既有行为**，非本次返工引入，P2-2 仅要求明确 retryCount 计数语义（已满足）。建议后续增强：仅当最近一次同步 FAILED 时才允许 retry。
- **P3-3（提示，既有问题）**：`CONSUMER-429`/`SERVICE-429`（quota/rate limit）在 `GlobalExceptionHandler` 落为 BAD_REQUEST 而非 429 Too Many Requests。非本次返工引入，留后续统一 HTTP 语义时处理。

### 4.4 对"建议通过"的反驳

- 为何不应通过？异常码改名是否遗漏调用点？→ 已 grep 全仓 `BILL_STATE_INVALID`，仅返工涉及的 7 文件命中，无遗漏。
- retryCount 测试是否仅验证断言而无真实语义？→ 已追 `doSync`/`sync`/`retry`/`findLastFailed` 代码路径，测试断言与代码行为一致。
- 双写一致性是否未实测？→ JDBC 路径有 `FinanceSyncRepositoryJdbcTest`(1) + `RepositoryContractTest`(3) 覆盖，内存与 JDBC 语义等价已比对。
- 反驳未发现存活阻断项，结论成立。

## 5. 测试验证

```text
mvn test -pl platform-common,platform-billing -am
- platform-common:  PASS（含 GlobalExceptionHandlerTest）
- platform-pipeline: Tests run: 113, Failures: 0, Errors: 0
- platform-billing: Tests run: 53, Failures: 0, Errors: 0
  含 FinanceSyncServiceTest(4)、BillingModuleMockMvcTest(25)、BillingGovernanceTest(7)
BUILD SUCCESS
```

返工新增/修改测试均通过，既有测试无回归。

## 6. 未实测项

- 本次返工为纯 Java 异常码 + 注释 + 测试，不涉及 DB 迁移/国产库方言/外部依赖，无未实测项。

## 7. 审查结论

**建议通过。**

- P2-1 异常码统一为 `BILL-409`，移除全局处理器 magic string 特判，全仓无遗漏调用点。
- P2-2 retryCount 语义明确为"最近失败序列计数"，跨序列测试覆盖。
- 对抗式审查已尝试反驳（误伤、跨序列、双写一致性、累加、冲突、429），未发现存活 P1/P2 缺陷。
- 存活 3 项 P3 提示均为既有问题或冗余清理，不阻断合入。

## 8. 后续建议（非阻断）

1. P3-1：移除 `BillingController.statusChange` 冗余 BILL-409 特判。
2. P3-2：增强 retry 前置条件——仅当最近一次同步 FAILED 时允许 retry。
3. P3-3：统一 429 码 HTTP 语义（CONSUMER-429/SERVICE-429 → 429 Too Many Requests）。
4. 返工改动可提交至 `ai/p1-finance-adapter` 分支并合并 master，然后启动 P2-01。
