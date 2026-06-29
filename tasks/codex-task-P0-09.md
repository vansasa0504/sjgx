# Codex 执行任务 - P0-09：前端边界测试

> 阶段：P0（上线阻断修复）
> 任务编号：P0-09
> 分支：`ai/p0-frontend-boundary`（已从 master 切出，master 已含 P0-08）
> 依据：`tasks/codex-task-P0-09-frontend-boundary.md`、`docs/development-process-workflow.md` §5.4、§7.3
> 前置：无后端依赖（纯前端），master 已含 P0-01~P0-08
> 日期：2026-06-29

---

## 1. 背景与目标

M7-D D2-01 指出前端边界测试缺失（空列表态/加载失败态/表单校验态/权限不足态未系统覆盖），M7-D D2-04 指出状态流转按钮断言未补，M7-C R-05/R-06/R-07 三项遗留交互未修。本任务系统性补齐前端边界测试与遗留交互修复，使前端达到流程文档 §5.4 + §7.3 验收级别。

**现状核查（2026-06-29）**：
- 现有测试 `m7c-pages.test.ts` 17 用例，覆盖 10 个业务页面，但仅 Partner/Catalog 有权限态示例，**无空态/失败态/校验态系统覆盖**。
- **R-05 未修**：`BillingView.confirm` 仅 `await confirmBill(row.billNo); refresh()`，无 ElMessage 反馈、无 try/catch。
- **R-06 未修**：`IngestView.createFields` 的 partnerId 为 `type:'number'`，未用 select。
- **R-07 未修**：`StatsView.exportReport` 为假导出（仅 ElMessage.success 提示）。
- FormDialog 已支持 `type:'select'` + `options`，listPartners 端点已存在，R-06 可直接落地。

**最小可行结果**：10 个业务页面均有空态/失败态/校验态/权限态测试；状态流转按钮断言；M7-C R-05/R-06/R-07 修复；`npm run test:unit` + `npm run build` 全绿。

## 2. 范围

### 本次实现

#### 2.1 边界测试（流程文档 §5.4，每页覆盖 4 态）

10 个业务页面：PartnerView / ConsumerView / IngestView / ServiceView / CatalogView / QualityView / BillingView / StatsView / SystemView / MonitorView。

每页覆盖：
- **空列表态**：mock list 返回空 → 断言渲染 `el-empty`（或空态提示）。
- **加载失败态**：mock list reject → 断言 `ElMessage.error` 调用（依赖全局错误提示；若页面无错误提示则断言不崩溃、列表为空）。
- **表单校验态**：必填字段空时提交，断言不调用提交 API（FormDialog rules 校验拦截）。
- **权限不足态**：auth mock 缺对应权限 → 断言操作按钮隐藏（v-if hasPermission）。

#### 2.2 状态流转按钮断言（M7-D D2-04）

每页至少 1 个"点击状态流转按钮 → 断言对应 API 调用"：
- PartnerView：submit/approve/admit/reject
- IngestView：submit/approve
- ServiceView：publish/offline
- ConsumerView：submit/approve
- BillingView：confirm/dispute
- CatalogView：apply/approve/reject（已有 reject，补 apply/approve 断言）

#### 2.3 遗留交互修复

- **R-05 BillingView.confirm**：补 `ElMessage.success` 反馈 + try/catch（失败 ElMessage.error）。
- **R-06 IngestView partnerId**：createFields 的 partnerId 改 `type:'select'`，onMounted 调 `listPartners` 填充 options（option label=合作方名称，value=id）。
- **R-07 StatsView.exportReport**：改真实导出——调用 `generateReport` 获取文件后用 Blob 触发下载，或若后端返回文件流则直接下载；失败 ElMessage.error。保留 ElMessage.success 成功提示。

#### 2.4 前端 build

- `npm run build` 通过（流程文档 §7.1 改前端路由/页面时必须）。

### 不做

- 不改后端。
- 不新增页面/功能（仅测试 + 遗留修复）。
- 不重构组件（FormDialog 已支持 select，无需改）。
- 不改 LoginView/ForbiddenView/NotFoundView（非业务列表页，不在 10 页边界范围）。

## 3. 必读输入

- `AGENTS.md`、`docs/development-process-workflow.md` §5.4、§7.3
- `platform-ui/src/views/*.vue`（10 业务页面）
- `platform-ui/src/views/__tests__/m7c-pages.test.ts`（现有 17 测试，参考 mock 模式）
- `platform-ui/src/components/FormDialog.vue`（FormField select options 结构）
- `platform-ui/src/api/*.ts`（mock 数据对齐端点返回结构 Page/List）
- `reviews/claude-review.md`（M7-C R-05/R-06/R-07、M7-D D2-01/D2-04）

## 4. 需要修改的模块

| 文件 | 改动 |
|---|---|
| `platform-ui/src/views/BillingView.vue` | R-05：confirm 反馈 + try/catch |
| `platform-ui/src/views/IngestView.vue` | R-06：partnerId select + onMounted listPartners |
| `platform-ui/src/views/StatsView.vue` | R-07：真实导出 Blob |
| `platform-ui/src/views/__tests__/` | 新增边界测试（可拆分文件如 `boundary-pages.test.ts`，或扩展 m7c-pages.test.ts） |

## 5. API/前端影响

- **API**：无新增端点。IngestView partnerId select 调既有 `listPartners`；StatsView 导出调既有 `generateReport`。
- **前端**：3 页面交互修复 + 全页面边界测试。

## 6. 必须补充的测试

### 6.1 边界测试矩阵（10 页 × 4 态）

| 页面 | 空态 | 失败态 | 校验态 | 权限态 |
|---|---|---|---|---|
| PartnerView | ✓ | ✓ | ✓ | ✓ |
| ConsumerView | ✓ | ✓ | ✓ | ✓ |
| IngestView | ✓ | ✓ | ✓ | ✓ |
| ServiceView | ✓ | ✓ | ✓ | ✓ |
| CatalogView | ✓ | ✓ | ✓ | ✓ |
| QualityView | ✓ | ✓ | ✓ | ✓ |
| BillingView | ✓ | ✓ | ✓ | ✓ |
| StatsView | ✓ | ✓ | ✓ | ✓ |
| SystemView | ✓ | ✓ | ✓ | ✓ |
| MonitorView | ✓ | ✓ | ✓ | ✓ |

> 共 40 个边界用例。可按页拆分测试文件便于维护，或集中一个 boundary 测试文件。

### 6.2 状态流转断言

- 每页至少 1 个"点击按钮 → 断言 API 调用"（见 §2.2）。

### 6.3 遗留修复测试

- IngestView：挂载调 listPartners，partnerId select 选项填充；create 提交带选中 partnerId。
- StatsView：导出触发 Blob 下载或 generateReport 调用（断言 URL.createObjectURL 或 API 调用）。
- BillingView：confirm 成功 ElMessage.success、失败 ElMessage.error。

## 7. 验收命令

```bash
cd platform-ui
npm run test:unit   # 全绿
npm run build       # 通过
```

## 8. M7 衔接

- **M7-D D2-01**：前端边界测试缺失 → 本任务补齐 40 用例。
- **M7-D D2-04**：前端状态流转断言缺失 → 本任务补齐。
- **M7-C R-05/R-06/R-07**：BillingView confirm 反馈、IngestView partnerId select、StatsView 假导出 → 本任务修复。
- 本任务不依赖后端 P0 改动，须在 P0-10 E2E 前完成。

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| 边界测试 mock 与真实 API 契约不符 | mock 数据对齐 M7-A 端点返回结构（Page `{records,total,current,size}` / List）；参考 m7c-pages.test.ts 既有 mock |
| IngestView partnerId select 改动影响现有 create 测试 | 更新 IngestView 相关 mock（listPartners 返回合作方列表） |
| StatsView Blob 导出在 jsdom 环境无 URL.createObjectURL | 测试中 mock `URL.createObjectURL` 或断言 generateReport 调用而非真实下载 |
| 校验态测试依赖 FormDialog 异步校验 | 用 flushPromises 等待校验，断言 submit API 未调用 |
| **回滚** | 纯前端改动，可逐文件回退 |

## 10. 完成判定

- [ ] 10 业务页面 × 4 边界态测试全覆盖（40 用例），全绿。
- [ ] 状态流转按钮断言全覆盖（每页 ≥1）。
- [ ] R-05 BillingView confirm 反馈 + try/catch 修复。
- [ ] R-06 IngestView partnerId select + listPartners 填充修复。
- [ ] R-07 StatsView 真实导出修复。
- [ ] `npm run test:unit` 全绿。
- [ ] `npm run build` 通过。
- [ ] 输出测试覆盖矩阵 + 遗留修复清单 + 测试结果。

## 11. 实现边界（Codex 遵守）

1. 纯前端改动，不改后端、不新增页面/功能、不重构组件。
2. FormDialog 已支持 select，R-06 直接用 `type:'select'` + `options`，不改 FormDialog。
3. mock 数据对齐既有 API 返回结构，参考 m7c-pages.test.ts 模式。
4. 测试不写真实密钥/token。
5. 边界测试可拆分文件或扩展 m7c-pages，保持可维护性。
6. R-07 导出优先用 Blob 触发下载；jsdom 测试 mock URL.createObjectURL。
7. 不跳过测试，`test:unit` + `build` 全绿。
8. 完成后输出修改文件、测试命令、测试结果、覆盖矩阵、遗留修复清单、潜在风险。
