# Codex 执行任务 - P0-07 改进（目录申请驳回与并发/一致性加固）

> 阶段：P0-07 审查后改进
> 任务编号：P0-07-improvement
> 分支建议：基于 `ai/p0-catalog-application` 继续开发（或新建 `ai/p0-catalog-application-rework`）
> 依据：`reviews/claude-review-P0-07.md` §6.2 F-1 ~ F-4、`docs/requirements.md` §2.4 FR-404
> 前置：P0-07 审查通过（改动已在工作区，测试全绿）
> 日期：2026-06-29

---

## 1. 背景与目标

P0-07 审查结论为"建议通过"，最小可行结果已全部达成。本次改进针对审查发现的 4 项 P2 问题，补齐驳回能力闭环、加固并发与一致性，不改变既有授权/脱敏/审计行为。

**最小可行结果**：
1. REJECTED 状态可通过 API/UI 可达（驳回端点 + 前端按钮）。
2. JDBC 仓储并发审批不会返回与库不一致的状态。
3. approve 中目录缺失不会导致审批已落库而授权失败的不一致。
4. dev profile 启动有 catalog 种子数据（可选）。

## 2. 范围

### 本次实现

- **F-1 驳回端点**：
  - `CatalogController` 新增 `POST /api/v1/catalog/applications/{id}/reject`，权限码复用 `catalog:approve`，调用 `applicationRepository.reject(id, approver)`，返回 `CatalogApplication`。
  - 不新增权限码（驳回属审批动作，复用 `catalog:approve`）。
  - 前端 `platform-ui/src/api/catalog.ts` 新增 `rejectApplication(id)`；`CatalogView.vue` 在"审批申请"按钮旁新增"驳回"按钮（同样受 `catalog:approve` 与 `pendingApplicationId(item)` 控制），驳回后清除 `pendingApplicationIds`。
- **F-2 JDBC transit 原子性**：
  - `JdbcCatalogApplicationRepository.transit` 校验 `UPDATE` 影响行数；为 0 时抛 `BusinessException("CATALOG_APP-409", "application already reviewed")`，与内存仓储行为一致。
- **F-3 approve 目录前置校验**：
  - `CatalogController.approve` 在调用 `applicationRepository.approve` **之前** 先 `requireItem(approved.catalogId())`（注意：approve 返回前 catalogId 已知，可先按 `applicationRepository.findById(id)` 取申请，或调整顺序确保目录缺失时审批不被持久化）。
  - 实现建议：先 `findById` 取申请 → `requireItem(catalogId)` → `approve` → `grantCatalogPartner`。 findById 不存在时抛 CATALOG_APP-404。
- **F-4 dev 种子（可选）**：
  - 新增 `db/migration/V016__catalog_seed.sql` + `db/migration-dm/V016__catalog_seed.sql`（及对应 U016 回滚），向 `t_data_catalog` 插入 2~3 条示例资产；U016 为 `DELETE FROM t_data_catalog WHERE ...`（按种子 catalog_code 精确删除，避免误删业务数据）。
  - 仅种子数据，不改表结构。

### 不做

- 不改 apply/preview 的授权、脱敏、审计逻辑（P0-07 已闭环）。
- 不改 DataServiceManager.grantCatalogPartner 的 partnerCode 语义（partnerId 占位可接受，列为后续）。
- 不引入新权限码、新依赖。
- 不重构 catalog 列表/检索。

## 3. 必读输入

- `AGENTS.md`、`reviews/claude-review-P0-07.md`（§6.2、§11）
- `platform-pipeline/src/main/java/.../catalog/CatalogController.java`、`CatalogApplicationRepository.java`、`InMemoryCatalogApplicationRepository.java`、`JdbcCatalogApplicationRepository.java`
- `platform-pipeline/src/test/java/.../catalog/CatalogApplicationRepositoryJdbcTest.java`、`CatalogControllerTest.java`、`PipelineModuleMockMvcTest.java`
- `platform-ui/src/api/catalog.ts`、`platform-ui/src/views/CatalogView.vue`
- `db/migration/V006__data_catalog.sql`（`t_data_catalog` 字段，种子 INSERT 对齐列）

## 4. 需要修改的模块

- `platform-pipeline.catalog`：CatalogController（reject 端点 + approve 顺序调整）、JdbcCatalogApplicationRepository（transit 行数校验）
- `db/migration` + `db/migration-dm`：V016/U016 种子（仅 F-4）
- `platform-ui/src/api/catalog.ts`：rejectApplication
- `platform-ui/src/views/CatalogView.vue`：驳回按钮

## 5. API/前端影响

- **新增 API**：`POST /api/v1/catalog/applications/{id}/reject` → `Result<CatalogApplication>`，权限 `catalog:approve`，401/403/200/404/409。
- **变更 API**：`POST .../approve` 在目录缺失时返回 404（CATALOG-404）而非 500，且不持久化审批。
- **前端**：CatalogView 卡片新增"驳回"按钮（与"审批申请"同条件显示）。

## 6. 必须补充的测试

- **后端单测**：
  - `CatalogControllerTest`：reject 流转 PENDING→REJECTED；已审批再 reject 抛 CATALOG_APP-409。
  - `CatalogApplicationRepositoryJdbcTest`：并发/重复 reject 后到者抛 409（可模拟：先 approve 再 reject 同一 id，期望 409；transit 影响行数为 0 的路径）。
  - `JdbcCatalogApplicationRepository`：approve 目录缺失场景（若可单测）。
- **MockMvc**：
  - `reject` 401（无 token）/ 403（权限不足）/ 200（admin，status=REJECTED，approver=admin）/ 404（不存在）/ 409（重复）。
  - `approve` 目录缺失返回 404 且申请状态仍为 PENDING（不持久化）。
- **前端**：`src/views/__tests__/m7c-pages.test.ts` 或新增用例验证驳回按钮在 `catalog:approve` 且有 pendingApplicationId 时渲染、点击调用 rejectApplication。
- 全量回归：后端 `mvn -pl platform-pipeline test` 全绿；前端 `npm run test:unit` 全绿。

## 7. 验收命令

```bash
mvn -pl platform-common install -DskipTests   # 若 common 有变更才需要
mvn -pl platform-pipeline test
npm run test:unit
# F-4 种子：Flyway 迁移到 V016 成功，dev 启动后 GET /api/v1/catalog 非空
```

## 8. 实现边界（Codex 遵守）

1. 只实现本任务列出的 F-1~F-4，F-4 为可选（标注是否实现）。
2. reject 复用 `catalog:approve` 权限，不新增权限码、不改 `PermissionCodes`。
3. F-3 实现须保证：目录缺失时审批**不被持久化**（approve 在 requireItem 之后）。
4. F-2 须保证内存与 JDBC 两仓储的 409 行为一致。
5. F-4 种子 SQL 须与 `t_data_catalog` 现有列对齐，U016 仅删种子行（按 catalog_code 精确匹配），不删全表。
6. 最小改动，不动无关模块，不跳过测试，不改密钥/生产配置。

## 9. 风险与回滚

- **风险**：approve 顺序调整改变既有 MockMvc 行为（目录存在场景应无变化）。控制：先跑现有 approve 用例确认无回归。
- **风险**：reject 端点缺少权限码可能误放开。控制：复用 `catalog:approve`，并补 401/403 MockMvc。
- **风险**：F-4 种子 SQL 在已有数据环境重复执行。控制：U016 按 catalog_code 精确删除；V016 用 `INSERT ... SELECT WHERE NOT EXISTS` 或固定 code 避免主键冲突。
- **回滚**：F-4 有 U016；F-1~F-3 为代码改动，git 可回退。

## 10. 完成判定

- reject 端点 + 前端按钮可用，REJECTED 状态可达，401/403/404/409 覆盖。
- JDBC transit 影响行数为 0 抛 409，与内存仓储一致。
- approve 目录缺失返回 404 且不持久化审批。
- F-4（如实现）：dev 启动目录非空，V016/U016 迁移成功。
- 后端 + 前端测试全绿。
- 输出修改文件、测试命令、测试结果、F-4 是否实现、潜在风险。
