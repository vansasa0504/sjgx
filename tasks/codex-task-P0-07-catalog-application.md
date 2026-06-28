# Codex 执行任务 - P0-07：目录申请

> 阶段：P0（上线阻断修复）
> 任务编号：P0-07
> 分支建议：`ai/p0-catalog-application`
> 依据：`docs/development-process-workflow.md` §3.1 P0-07、§6.4、`docs/database-design.md`（`t_catalog_application`）
> 前置：P0-03 通过（Catalog 仓储已落库）
> 日期：2026-06-27

---

## 1. 背景与目标

M7-A F-05 指出 `CatalogController.preview` 为桩（返回空 sample）；M7-D D2-02 指出 `CatalogService` 无参构造器预置 DEMO 资产（种子数据在生产代码中）。目录申请 `t_catalog_application` 无持久化仓储，申请审批重启即丢。本任务：建 `t_catalog_application` + 仓储，申请审批持久可查；preview 真实化（sample+stats+qualityReport）并授权脱敏；移除生产代码中的种子数据。

**最小可行结果**：目录申请/审批落库重启可查；preview 返回真实 sample 且未授权返回 403、敏感字段脱敏；`CatalogService` 无生产种子数据。

## 2. 范围

### 本次实现
- `t_catalog_application` 表（若未建，补 V011 + U011）：`id`/`catalog_id`/`applicant`/`reason`/`scope`/`status`/`approver`/`created_at`/`approved_at`。
- `CatalogApplicationRepository` JDBC 实现 + 内存实现（test）。
- `CatalogService`：移除无参构造器的 DEMO 种子（M7-D D2-02），种子数据移至 `U010__seed_data.sql` 或测试夹具。
- `CatalogController.apply` / `approve`：接持久化仓储，申请审批状态机（PENDING→APPROVED/REJECTED）。
- `CatalogController.preview`：返回真实 sample（从资产数据源取）+ stats + qualityReport；未授权（无 `catalog:approve` 或非申请人）返回 403；敏感字段脱敏；预览写审计。
- 前端 CatalogView 审批绑定当前 item（M7-C R-03 已修，本任务对齐持久化）。

### 不做
- 不做血缘/质量摘要/使用统计（P1-02）。
- 不改目录列表/检索逻辑（M7 已可用）。

## 3. 必读输入

- `AGENTS.md`、`docs/database-design.md`（`t_catalog_application`、`t_data_catalog`）
- `docs/detailed-requirements-design.md`（目录设计）
- `platform-pipeline/src/main/java/.../catalog/CatalogService.java`、`CatalogController.java`
- `reviews/claude-review.md`（M7-A F-05、M7-C R-03、M7-D D2-02）

## 4. 需要修改的模块

- `platform-pipeline.catalog`（CatalogService、CatalogController、新增 CatalogApplicationRepository）
- `db/migration`（V011 + U011 补 `t_catalog_application`；种子数据移入 `U010__seed_data.sql`）
- `platform-ui/src/api/catalog.ts`（apply/approve 对齐持久化返回）
- `platform-ui/src/views/CatalogView.vue`（preview 展示 sample table + stats + qualityReport）

## 5. 数据库/API/前端影响

- **数据库**：`t_catalog_application` 新表；`CatalogService` 种子移到 SQL。
- **API**：`POST /catalog/{id}/apply` 返回持久化申请（含 id）；`POST /catalog/applications/{id}/approve` 状态流转；`GET /catalog/{id}/preview` 真实 sample + 403 授权。
- **前端**：CatalogView preview drawer 展示 sample el-table + stats + qualityReport；审批按钮绑定当前资产申请。

## 6. 必须补充的测试

- **持久化测试**：申请 → 重启 → 仍可查；审批后状态流转。
- **preview 授权测试**：无 `catalog:approve` 且非申请人 → 403。
- **脱敏测试**：preview 响应中敏感字段（如凭证、个人标识）脱敏。
- **预览审计测试**：preview 调用写一条审计事件。
- **种子数据测试**：`CatalogService` 生产代码无种子（移到 SQL），但 dev profile 启动有种子数据（从 SQL）。
- **MockMvc**：apply/approve 200/401/403；preview 200/403。

## 7. 验收命令

```bash
mvn test -pl platform-pipeline -Dspring.profiles.active=jdbc
npm run test:unit
# 持久化：apply → 重启 → GET 申请仍存在
```

## 8. M7 衔接

- **M7-A F-05**：catalog preview 为桩 → 本任务真实化。
- **M7-D D2-02**：CatalogService 种子数据在生产代码 → 本任务移至 SQL。
- **M7-C R-03**：CatalogView 审批绑定当前 item → 本任务对齐持久化（申请 id 来自后端）。
- **M7-D 完成报告偏离说明 §5.2**：preview 桩 → 本任务闭环。

## 9. 风险与回滚

- **风险**：preview 真实 sample 涉及数据源访问权限。控制：sample 从已落库数据取，不直连外部。
- **风险**：脱敏字段清单不全。控制：对照 `database-design.md` 敏感字段标注。
- **回滚**：V011 有 U011；种子数据移除后 dev 环境靠 SQL 种子，可回退。
- **敏感约束**：preview 脱敏；预览写审计；不泄露未授权资产数据。

## 10. 完成判定

- `t_catalog_application` 持久化，申请审批重启可查。
- preview 真实 sample + 授权 403 + 脱敏 + 审计。
- `CatalogService` 无生产种子（移至 SQL）。
- MockMvc + 前端测试全绿。
- 输出申请审批流转说明 + preview 脱敏证据。
