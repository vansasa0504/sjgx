# Claude Code 复审结果 — P0-07 改进（目录申请驳回与并发/一致性加固）

## 1. 审查对象

- 任务：P0-07 改进（`tasks/codex-task-P0-07-improvement.md`，F-1~F-4）
- 分支：`ai/p0-catalog-application`（继续在原分支开发）
- 复审日期：2026-06-29
- 前置：P0-07 基线审查通过（`reviews/claude-review-P0-07.md`）
- 改动范围：CatalogController 新增 reject 端点 + approve 顺序调整、JdbcCatalogApplicationRepository transit 行数校验、GlobalExceptionHandler 扩展 404/409 映射、前端 reject 按钮 + API、测试补充

## 2. Git 状态

改动叠加在 P0-07 基线之上（工作区未提交）。本次改进相对基线新增/修改的关键文件：

```text
 M platform-common/.../exception/GlobalExceptionHandler.java        # 新增 404/409 映射（超出 F-1~F-4 字面，但为必要支撑）
 M platform-common/.../exception/GlobalExceptionHandlerTest.java     # 新增映射断言
 M platform-pipeline/.../catalog/CatalogController.java              # reject 端点 + approve 顺序调整
 M platform-pipeline/.../catalog/JdbcCatalogApplicationRepository.java # transit 行数校验（F-2）
 M platform-pipeline/test/.../PipelineModuleMockMvcTest.java         # +2 用例（reject 5 状态、approve 目录缺失不持久化）
 M platform-pipeline/test/.../catalog/CatalogControllerTest.java     # +1 用例（reject 流转）
 M platform-ui/src/api/catalog.ts                                    # rejectApplication
 M platform-ui/src/views/CatalogView.vue                             # 驳回按钮
 M platform-ui/src/api/__tests__/modules.test.ts                     # reject API 用例
 M platform-ui/src/views/__tests__/m7c-pages.test.ts                 # reject 按钮用例
```

> 注：`CatalogController.java`、`JdbcCatalogApplicationRepository.java` 等在 git 中显示为基线改动叠加，diff 同时含 P0-07 基线与本次改进内容。

## 3. 测试验证

### 3.1 后端

```bash
mvn -pl platform-common install -DskipTests   # 重建 common（本地 jar 过期会导致 NoSuchMethodError，非本次缺陷）
mvn -pl platform-common test
mvn -pl platform-pipeline test
```

结果：
- **platform-common**：BUILD SUCCESS，Tests run: 29, Failures: 0, Errors: 0
- **platform-pipeline**：BUILD SUCCESS，Tests run: 71, Failures: 0, Errors: 0
  - `PipelineModuleMockMvcTest`：21 → **23**（新增 `catalogRejectRequiresPermissionAndTransitionsState`、`catalogApproveMissingCatalogDoesNotPersistApproval`）
  - `CatalogControllerTest`：2 → **3**（新增 `rejectsPendingApplication`）
  - `CatalogApplicationRepositoryJdbcTest`：2 通过（含 transit 重复操作 409）

### 3.2 前端

```bash
npm run test:unit
```

结果：**11 文件 / 37 测试全部通过**（m7c-pages 15→16 新增 reject 按钮用例；modules 新增 reject API 用例）

### 3.3 测试结论

全绿，无回归。F-1~F-3 测试覆盖完整。

## 4. 改进项落实情况

| 改进项 | 要求 | 实现情况 | 结论 |
|---|---|---|---|
| **F-1** reject 端点 + 前端按钮 | `POST /applications/{id}/reject`，复用 `catalog:approve`，前端驳回按钮 | CatalogController.reject + `@RequirePermission("catalog:approve")`；前端 `rejectApplication` API + `rejectApplicationFor` 按钮（与审批按钮同条件 `catalog:approve && pendingApplicationId`，type=danger）；MockMvc 覆盖 401/403/200/404/409 | ✅ |
| **F-2** JDBC transit 原子性 | UPDATE 影响行数为 0 抛 409，与内存一致 | `transit` 校验 `updated == 0` 抛 `CATALOG_APP-409`，保留 PENDING 状态预检 + UPDATE 守卫双重防护；内存仓储 compute 原子 | ✅ |
| **F-3** approve 目录前置校验 | requireItem 前置，目录缺失返回 404 且不持久化审批 | `findById → requireItem(catalogId) → approve → grant`；MockMvc `catalogApproveMissingCatalogDoesNotPersistApproval` 验证目录缺失返回 404 且申请状态仍 PENDING | ✅ |
| **F-4** dev 种子（可选） | V016/U016 种子 SQL，标注是否实现 | **未实现**（无 V016/U016 文件） | ⚠️ 可选，未实现 |

## 5. 额外改动评估（超出 F-1~F-4 字面范围）

### 5.1 GlobalExceptionHandler 扩展 404/409 映射（必要支撑）

为使 reject/approve 返回语义正确的 HTTP 状态码（404 申请不存在、409 重复审批），Codex 扩展了 `GlobalExceptionHandler`：

```java
HttpStatus status = code.endsWith("401") ? UNAUTHORIZED
        : code.endsWith("403") ? FORBIDDEN
        : code.endsWith("404") && code.startsWith("CATALOG") ? NOT_FOUND   // 仅 CATALOG 前缀
        : code.endsWith("409") ? CONFLICT                                    // 全局
        : BAD_REQUEST;
```

**评估**：
- 这是 F-1（reject 返回 404/409）的必要支撑，否则 reject 的 404/409 会回落为 400，MockMvc 断言无法通过。属合理伴随改动。
- **409 全局映射的副作用**：`AUTH-409`（重放请求）、`INGEST-409`（任务状态机违规）、`SERVICE-409`（服务状态机违规）原先返回 400，现返回 409 CONFLICT。语义上 409 更准确，且**无测试断言这些返回 400，无回归**。属可接受的范围扩大，但应在报告中知悉。
- **404 仅 CATALOG 前缀**：`AUTH-404`、`QUALITY-404` 仍返回 400，与 `CATALOG_APP-404`/`CATALOG-404` 返回 404 存在不一致。Codex 采用最小改动（仅服务 catalog），未做全仓统一，可接受，但属一致性技术债。

### 5.2 范围合规性

- 未改 apply/preview 的授权/脱敏/审计逻辑。
- 未引入新权限码（reject 复用 `catalog:approve`，符合任务边界）。
- 未改 DataServiceManager.grantCatalogPartner 语义（partnerId 占位，符合"不做"清单）。
- 无新依赖、无密钥/生产配置改动、无无关模块改动。

## 6. 代码质量

### 6.1 优点

1. **F-2 双重防护**：transit 先做 PENDING 预检（快速失败 + 友好错误），再 UPDATE 带 `WHERE status='PENDING'` 守卫并校验影响行数，兼顾性能与并发安全。
2. **F-3 顺序正确**：findById 取申请 → requireItem 校验目录 → approve 持久化 → grant，目录缺失时审批未持久化，与任务要求一致，且有测试固化。
3. **F-1 权限复用**：reject 复用 `catalog:approve`，前端按钮与审批按钮同条件渲染，交互一致。
4. **测试边界完整**：reject 覆盖 401/403/200/404/409 全路径；approve 目录缺失验证"不持久化"不变量；前端验证按钮渲染与 API 调用。

### 6.2 发现的问题

#### P3（提示，不阻断）

**G-1：F-4 未实现且未在改动中显式说明**
- 现象：任务单 §10 要求"输出 F-4 是否实现"，但工作区无 V016/U016 种子 SQL，F-4 未实现。
- 影响：dev 环境启动目录仍为空。任务单标注 F-4 为可选，未实现不违规，但需在 Codex 输出中明示（本次复核未见 Codex 输出文本，以文件实际为准）。
- 建议：后续若需 dev 演示种子，单独补 V016/U016；或明确文档化"dev 种子暂不提供"。

**G-2：GlobalExceptionHandler 404 映射的前缀不一致**
- 现象：仅 `CATALOG` 前缀的 404 映射 NOT_FOUND，`AUTH-404`/`QUALITY-404` 仍 400。
- 影响：全仓 404 语义不统一。当前仅 catalog 模块依赖 404，无功能影响。
- 建议：后续可统一为 `endsWith("404") → NOT_FOUND`（需评估是否有测试断言 AUTH-404/QUALITY-404 返回 400）。属一致性技术债，不阻断。

**G-3：F-3 中 grant 失败仍可能 leave 审批已落库但授权未生效**
- 现象：approve 持久化后，若 `grantCatalogPartner` 抛异常（当前实现不会，因 put 操作无外部依赖），审批已 APPROVED 但授权未写入。
- 影响：当前 grantCatalogPartner 仅内存 put，不会失败，无实际风险。
- 评估：理论隐患，当前不可达。不阻断。

## 7. 是否超出任务范围

- **GlobalExceptionHandler 改动**：超出 F-1~F-4 字面清单，但为 F-1 reject 返回正确 HTTP 状态码的必要支撑，属合理伴随改动。409 全局映射的副作用（AUTH/INGEST/SERVICE-409 由 400→409）无回归且语义更佳，可接受。
- 其余改动均落在 catalog 模块、异常处理、前端、测试范围内，无无关重构，无大型依赖，符合最小改动。

## 8. 是否过度设计

未发现过度设计。transit 双重防护为合理并发加固；reject 端点为最小必要实现；异常映射为最小扩展（仅新增 404/409 两条规则）。无冗余。

## 9. 安全风险

- ✅ reject 复用 `catalog:approve` 权限，未授权不可操作（401/403 覆盖）。
- ✅ 状态机单向流转，重复审批/驳回返回 409，不可篡改已审批申请。
- ✅ approve 目录缺失不持久化，避免不一致状态。
- ✅ 无 SQL 注入（参数化 + IdGenerator 表名不可外部输入）。
- ✅ 无敏感信息入日志。
- 无新增安全风险。

## 10. 复审结论

**建议通过**

P0-07 改进的 F-1（reject 闭环）、F-2（transit 原子性）、F-3（approve 一致性）全部正确实现，测试覆盖完整（reject 401/403/200/404/409、approve 目录缺失不持久化、transit 行数校验、前端 reject 按钮/API），后端 71 + common 29 + 前端 37 测试全绿，无回归。F-4（dev 种子）为可选项，未实现，不违规。

额外改动 `GlobalExceptionHandler` 扩展 404/409 映射为 F-1 的必要支撑，合理；其全局 409 映射对 AUTH/INGEST/SERVICE-409 的副作用（400→409）语义更佳且无回归，可接受。

P3 提示（G-1~G-3）不阻断合入，可作为后续技术债跟进。

## 11. 后续可选改进（不阻断）

1. [ ] F-4 dev 种子：若需 dev 演示，补 V016/U016 catalog 种子 SQL（按 catalog_code 精确删除回滚）。
2. [ ] G-2 一致性：评估将 `endsWith("404")` 统一映射 NOT_FOUND（需确认 AUTH-404/QUALITY-404 无 400 断言）。
3. [ ] G-3 健壮性：grant 失败时不影响审批结果（当前不可达，可后续加事务/补偿）。

## 12. 建议提交

P0-07 基线 + 改进可一并提交。建议提交信息：

```text
feat(P0-07): persist catalog applications with approval workflow and preview authorization

- t_catalog_application table + JDBC/in-memory repositories (PENDING→APPROVED/REJECTED)
- preview: real sample + stats + qualityReport, 403 for unauthorized, sensitive field masking, audit
- apply/approve/reject endpoints with catalog:approve permission reuse
- approve validates catalog existence before persisting; transit guards concurrent review (409)
- DataServiceManager partner_code closed via grantCatalogPartner
- frontend CatalogView preview drawer + reject button
```
