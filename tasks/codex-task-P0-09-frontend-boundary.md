# Codex 执行任务 - P0-09：前端边界测试

> 阶段：P0（上线阻断修复）
> 任务编号：P0-09
> 分支建议：`ai/p0-frontend-boundary`
> 依据：`docs/development-process-workflow.md` §3.1 P0-09、§5.4、§7.3
> 前置：无（可与 P0-03~P0-08 并行）
> 日期：2026-06-27

---

## 1. 背景与目标

M7-D D2-01 指出前端边界测试缺失（空列表态/加载失败态/表单校验态/权限不足态未覆盖），M7-D D2-04 指出前端状态流转按钮断言未补，M7-C R-05/R-06/R-07 指出 BillingView confirm 无反馈、IngestView partnerId 未用 select、StatsView 假导出。本任务系统性补齐前端边界测试与遗留交互修复，使前端达到流程文档 §5.4 + §7.3 的验收级别。

**最小可行结果**：10 个页面均有空态/失败态/校验态/权限不足态测试；状态流转按钮断言；M7-C R-05/R-06/R-07 修复；`npm run test:unit` + `npm run build` 全绿。

## 2. 范围

### 本次实现

#### 2.1 边界测试（流程文档 §5.4，每页覆盖）
- **空列表态**：mock list 返回空 → 渲染 `el-empty`。
- **加载失败态**：mock list reject → 验证 `ElMessage.error` 调用（依赖 M7-B 全局错误提示）。
- **表单校验态**：必填字段空时提交禁用/校验失败不调用 API。
- **权限不足态**：缺权限 auth mock → 按钮隐藏（M7-C 已有 partner 一例，本任务扩展到全部页面）。
- **状态按钮与后端状态机一致**：不同 `row.status` 下按钮显隐正确（M7-C R-01 已修，本任务补断言）。
- **操作按钮断言调用正确 API**：点击状态流转按钮 → 断言对应 events API 调用（M7-D D2-04）。

#### 2.2 遗留交互修复
- **M7-C R-05**：BillingView `confirm(row)` 补 `ElMessage.success` 反馈 + try/catch。
- **M7-C R-06**：IngestView `createFields` 的 `partnerId` 改 `select`（onMounted 调 `listPartners` 填充选项）。
- **M7-C R-07**：StatsView `exportReport` 改真实导出（Blob 触发下载，或调后端导出端点）。
- **M7-D D2-04**：补状态流转按钮断言（partner submit/approve/admit、ingest submit/approve、service publish/offline、consumer submit/approve）。

#### 2.3 前端 build
- `npm run build` 通过（流程文档 §7.1 改前端路由/页面时必须）。

### 不做
- 不改后端。
- 不新增页面/功能（仅测试 + 遗留修复）。
- 不重构组件（FormDialog validate 捕获若 M7-D 已修则跳过）。

## 3. 必读输入

- `AGENTS.md`、`docs/development-process-workflow.md` §5.4、§7.3
- `platform-ui/src/views/*.vue`（10 页面）
- `platform-ui/src/views/__tests__/m7c-pages.test.ts`（现有 15 测试）
- `reviews/claude-review.md`（M7-C R-05/R-06/R-07、M7-D D2-01/D2-04）

## 4. 需要修改的模块

- `platform-ui/src/views/__tests__/`（新增边界测试，可按页拆分文件或扩展现有 m7c-pages.test.ts）
- `platform-ui/src/views/BillingView.vue`（R-05）
- `platform-ui/src/views/IngestView.vue`（R-06）
- `platform-ui/src/views/StatsView.vue`（R-07）

## 5. 数据库/API/前端影响

- **数据库**：无。
- **API**：IngestView partnerId select 调 `listPartners`（已有端点）；StatsView 导出若调后端则需导出端点（若无则前端 Blob）。
- **前端**：3 页面交互修复 + 全页面边界测试。

## 6. 必须补充的测试

- 10 页面 × 4 边界态 = 40 个边界测试用例（空态/失败态/校验态/权限态）。
- 状态流转断言：每页至少 1 个"点击按钮→断言 API 调用"。
- IngestView partnerId select：验证挂载调 listPartners 填充选项。
- StatsView 导出：验证导出触发（Blob 或 API 调用）。

## 7. 验收命令

```bash
cd platform-ui
npm run test:unit   # 全绿
npm run build       # 通过
```

## 8. M7 衔接

- **M7-D D2-01**：前端边界测试缺失 → 本任务补齐。
- **M7-D D2-04**：前端状态流转断言缺失 → 本任务补齐。
- **M7-C R-05/R-06/R-07**：BillingView confirm 反馈、IngestView partnerId select、StatsView 假导出 → 本任务修复。
- 本任务不依赖后端 P0 改动，可与 P0-03~P0-08 并行，但须在 P0-10 前完成。

## 9. 风险与回滚

- **风险**：边界测试 mock 与真实 API 契约不符（M7-C 曾有此问题）。控制：mock 数据对齐 M7-A 端点返回结构（Page/List）。
- **风险**：IngestView partnerId select 改动影响现有 create 测试。控制：更新 mock。
- **回滚**：纯前端改动，可逐文件回退。
- **敏感约束**：测试不写真实密钥/token。

## 10. 完成判定

- 10 页面 × 4 边界态测试全覆盖，全绿。
- 状态流转按钮断言全覆盖。
- M7-C R-05/R-06/R-07 修复。
- `npm run test:unit` + `npm run build` 全绿。
- 输出测试覆盖矩阵 + 遗留修复清单。
