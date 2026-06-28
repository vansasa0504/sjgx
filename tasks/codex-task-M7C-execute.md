# Codex 桌面端 M7 阶段 C 执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M7-B 已通过审查（前端基础设施：API client 分层、auth store、路由守卫、PageTable/FormDialog/StatusTag/ConsoleLayout 已就绪）。
> 本阶段为 M7 第三阶段：九大模块页面功能化。完成后进入 D（测试回归）。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M7-api-and-frontend.md   ← M7 总任务来源（权威）
7. reviews/claude-review.md                   ← 含 M7-A/B 审查结论

当前执行阶段：M7-C（前端九大模块页面功能化）
请只执行本提示词中「M7-C 阶段任务」段落列出的任务。

---

## 项目背景（一句话）

金融机构外部数据采集平台前端。M7-A 已补齐后端全部 Controller + 鉴权，M7-B 已完成前端基础设施（API client 分层、auth store 接 permissions、路由守卫、PageTable/FormDialog/StatusTag/ConsoleLayout 共享组件）。本阶段把 10 个 A 类骨架页面升级为可操作页面。

### 既有现状（必须基于此）

- `src/api/*.ts`：各模块 API 函数已就绪（M7-B 产出），函数签名对齐后端端点。
- `src/components/PageTable.vue`：分页表格，Props 含 columns/fetchData/filters。
- `src/components/FormDialog.vue`：表单弹窗，Props 含 title/fields/initial/submit。
- `src/components/StatusTag.vue`：状态标签。
- `src/layouts/ConsoleLayout.vue`：左侧菜单按权限过滤 + 顶栏登出。
- 10 个业务页面当前全是 A 类骨架：写死 `const rows=[...]`，无 API 调用，按钮无处理。
- 路由 meta.permission 已在 B 阶段配好。

### 目录约束

- 只修改 `platform-ui/src/views/*.vue`（10 个页面）+ 必要的子组件（放 `src/components/` 或页面同级目录）。
- 不修改 api/stores/router/components（B 阶段产出，如确需扩展先说明）。
- 不修改后端、docs/tasks/reviews。
- 不引入新的大型依赖。

---

## 执行规则（全局）

1. 只实现 M7-C 阶段任务（页面功能化）。
2. **禁止写死数据**：所有列表/数据必须来自 `src/api/*.ts` 的真实 API 调用。
3. 复用 PageTable/FormDialog/StatusTag，不重复造轮子。
4. 每个页面统一交互范式：列表用 PageTable、新增/编辑用 FormDialog、状态流转用确认弹窗 + 调 events 端点、详情用 el-drawer。
5. 按钮按权限码显隐：用 `auth.hasPermission` 控制 `v-if`。
6. 每个页面至少 1 个 Vitest 测试（mock API，验证列表渲染 + 至少一个操作触发正确 API 调用）。
7. `npm run test:unit` 全绿。
8. 完成后输出「阶段完成报告 - M7-C」。

---

## M7-C 阶段任务

### 阶段目标

10 个骨架页面全部从 A 升级到 C：列表来自真实 API、可新增/编辑/状态流转/查看详情，数据零写死。

### 前置条件

M7-B 审查通过；后端服务可启动（M7-A 产出）。

---

### 通用页面范式（所有页面遵循）

1. **列表区**：`<PageTable :columns :fetchData :filters>`，fetchData 调对应 api 的 list 函数。
2. **搜索区**：通过 PageTable 的 filters prop 传入（关键词、状态、类型等）。
3. **操作栏**：表格行操作列放"详情/编辑/状态流转"按钮；页面顶部放"新建"按钮。按钮按权限码 `v-if`。
4. **新增/编辑**：`<FormDialog>`，fields 定义表单字段，submit 调 create/update api。
5. **状态流转**：`ElMessageBox.confirm` 确认后调对应 events 端点，成功后 `pageTableRef.refresh()`。
6. **详情**：`el-drawer` 展示，调 detail api 拉数据。
7. **错误反馈**：`ElMessage.error` 显示后端错误消息。

---

### 任务 C.1：PartnerView.vue（合作方管理，FR-101~104）

权限码：partner:view（列表）、partner:create（新建）、partner:update（编辑/评级/接口）、partner:approve（审批/驳回/退出）。

- 列表列：合作方编码、名称、数据类型、行业、合规等级、状态（StatusTag）、评级、创建时间、操作。
- 搜索：关键词（name 模糊）、数据类型、状态。
- 操作：
  - 新建：FormDialog（name/dataType/industry/complianceLevel）→ `createPartner`。
  - 详情：drawer 展示基本信息 + 接口列表（`listInterfaces`）+ 生命周期事件流（`listPartnerEvents`）。
  - 编辑：FormDialog → `updatePartner`。
  - 提交审核：confirm → `submitPartner`。
  - 准入：confirm → `approvePartner`。
  - 驳回：FormDialog（reason）→ `rejectPartner`。
  - 评级：FormDialog（score）→ `ratePartner`。
  - 退出：confirm → `terminatePartner`。
  - 配置接口：FormDialog（protocol/endpoint/authType/credential/rateLimit）→ `configureInterface`。
- 状态机按钮按当前状态显隐（如 PENDING 才显示"提交审核"，ACTIVE 才显示"退出"）。

测试：`PartnerView.test.ts`，mock `api/partner`，验证挂载调 listPartners、新建按钮调 createPartner、状态流转调对应端点。

---

### 任务 C.2：IngestView.vue（接入任务，FR-201~205）

权限码：ingest:view/create/update/approve。

- 列表列：任务编码、合作方、协议、格式、同步模式、状态、版本、操作。
- 搜索：合作方、状态。
- 操作：
  - 新建任务：FormDialog（partnerId/interfaceId/protocol/format/endpoint/syncMode/cron/fieldMapping/qualityRules）→ `createIngestTask`。partnerId 用 select（调 listPartners 填充选项）。
  - 详情：drawer（任务信息 + 字段映射 + 规则）。
  - 编辑映射：FormDialog → `updateMapping`。
  - 编辑规则：FormDialog → `updateRules`。
  - 测试接入：按钮 → `testIngest`，结果用 el-table 展示 RawDataRecord。
  - 提交/审批/下线：confirm → `submitIngest`/`approveIngest`/`offlineIngest`。
  - 查看执行记录：drawer 或子 PageTable → `listIngestRecords`。

测试：`IngestView.test.ts`，验证 list、create、test 调用。

---

### 任务 C.3：ServiceView.vue（数据服务，FR-301~305）

权限码：service:view/create/update/approve。

- 列表列：服务编码、名称、类型、路由、限流、状态、版本、操作。
- 搜索：关键词、状态。
- 操作：
  - 注册：FormDialog → `registerService`。
  - 详情：drawer。
  - 编辑：FormDialog → `updateService`。
  - 测试：按钮 → `testService`。
  - 发布/下线：confirm → `publishService`/`offlineService`。
  - 查看调用日志：drawer + PageTable → `listServiceLogs`（列：consumer、状态、耗时、responseSize、时间）。
- 状态机按钮按状态显隐。

测试：`ServiceView.test.ts`，验证 list、register、publish 调用。

---

### 任务 C.4：CatalogView.vue（数据目录，FR-401~405）

权限码：catalog:view（浏览）、catalog:apply（申请）、catalog:approve（审批）。

- 用卡片网格（el-card）展示数据资产，不用表格。
- 筛选：主题、合作方、数据类型、场景（select 或 cascader）。
- 操作：
  - 查看元信息：drawer → `getCatalogMeta`。
  - 数据预览：drawer → `previewCatalog`，展示 sample（el-table）+ stats + qualityReport。
  - 检索：顶部搜索框 → `searchCatalog`。
  - 申请使用：FormDialog（reason/scope）→ `applyCatalog`。
  - 审批申请：在"我的审批"区或详情内 → `approveApplication`。

测试：`CatalogView.test.ts`，验证 list、search、apply 调用。

---

### 任务 C.5：ConsumerView.vue（消费方管理，FR-501~505）

权限码：consumer:view/create/update/approve。

- 列表列：编码、名称、业务条线、系统类型、合规等级、状态、操作。
- 搜索：关键词、业务条线、状态。
- 操作：
  - 注册：FormDialog → `registerConsumer`。
  - 详情：drawer。
  - 配置配额：FormDialog（freqLimit/volumeLimit/scope）→ `configureQuota`。
  - 审批事件：confirm → `applyConsumerEvent`（SUBMIT/APPROVE 等）。
  - 查看行为审计：drawer + PageTable → `getConsumerAudit`。
  - 查看调用日志：drawer + PageTable → `getConsumerLogs`。

测试：`ConsumerView.test.ts`，验证 list、register、configureQuota 调用。

---

### 任务 C.6：QualityView.vue（数据质量，FR-901~906）

权限码：quality:view/create/update/run。

用 `el-tabs` 分 4 个 tab：

- **Tab1 规则配置**：PageTable（列：编码/名称/维度/校验对象/严重级别/启用）+ 新建/编辑 FormDialog（dimension 用 select 六维）→ `createQualityRule`/`updateQualityRule`。
- **Tab2 校验结果**：PageTable（列：规则/批次/总数/通过/失败/失败率/时间）+ "手动触发校验"按钮 FormDialog（选规则+目标）→ `triggerCheck`。
- **Tab3 问题工单**：PageTable（列：规则/类型/严重级别/状态/指派人）+ 指派/解决操作（FormDialog）→ `assignIssue`/`resolveIssue`。状态流转 StatusTag。
- **Tab4 报告与评分**：选择合作方/类型/时间 → `getQualityReport` + `getQualityScore`，用 ECharts 或 el-descriptions 展示。

测试：`QualityView.test.ts`，验证 rules 列表、create rule、trigger check 调用。

---

### 任务 C.7：BillingView.vue（计费管理，FR-701~705）

权限码：billing:view/create/update/approve/run。

用 `el-tabs` 分 3 个 tab：

- **Tab1 计费规则**：PageTable（列：编码/名称/模型/目标类型/目标ID/单价/生效期/状态）+ 新建/编辑 FormDialog → `createBillingRule`/`updateBillingRule`。
- **Tab2 账单**：PageTable（列：账单号/类型/周期/金额/状态/时间）+ 生成（FormDialog 选 billType/period/日期）→ `generateBill` + 确认 → `confirmBill` + 异议（FormDialog reason）→ `disputeBill`。状态流转 StatusTag。
- **Tab3 费用统计**：选时间/合作方/消费方 → `getBillingStats`，ECharts 展示趋势/占比。

测试：`BillingView.test.ts`，验证 rules 列表、generate bill、confirm 调用。

---

### 任务 C.8：StatsView.vue（统计监管，FR-801~805）

权限码：stats:view。

- **Dashboard 区**：挂载调 `fetchDashboard`，用 ECharts 展示：调用量趋势、成功率、传输量、缓存命中率、服务数、成本（多个图表组件，可拆 `src/components/charts/`）。
- **监管报表区**：选类型/时间 → `generateReport`，展示报表内容 + 导出按钮（调后端导出或前端下载）。
- **合规审计区**：PageTable（列：traceId/事件类型/actor/操作/状态/时间）+ 筛选（actorType/eventType/时间）→ `listAudit`。

测试：`StatsView.test.ts`，验证 fetchDashboard 调用 + 渲染、listAudit 调用。

---

### 任务 C.9：SystemView.vue（系统管理，支撑 NFR-S01）

权限码：system:view/create/update。

用 `el-tabs` 分 3 个 tab：

- **Tab1 用户**：PageTable（列：用户名/权限数/操作）+ 新建/编辑 FormDialog → `createUser`/`updateUser`。密码字段仅新建时必填。
- **Tab2 角色**：PageTable（列：角色名/权限数/操作）+ 新建 FormDialog → `createRole` + 配置权限（FormDialog 多选 permissions）→ `updateRolePermissions`。
- **Tab3 权限**：调用 `listPermissions`，用 el-tag 网格展示全部权限码。

测试：`SystemView.test.ts`，验证 users 列表、create user、roles 列表调用。

---

### 任务 C.10：MonitorView.vue（监控大屏，FR-805）

权限码：stats:view。

- 调 `fetchDashboard` + 各服务 `/actuator/metrics`（经 Gateway，但 actuator 可能需放行，若 401 则仅用 dashboard 数据）。
- ECharts 大屏布局：运行状态（调用量/成功率）、服务概览（服务数/调用量）、合规（审计事件数）、成本（账单总额）。
- 定时刷新：`setInterval` 30s 重新拉取，组件卸载时 `clearInterval`。
- 深色背景大屏风格（可选，用 CSS）。

测试：`MonitorView.test.ts`，验证 fetchDashboard 调用 + 定时刷新被设置 + 卸载清理。

---

### 测试要求（M7-C）

- 10 个页面各 1 个 Vitest 测试，mock 对应 api 模块，验证：
  - 挂载时调用正确的 list/fetch API。
  - 至少一个操作（新建/状态流转）触发正确的 API 调用。
  - 权限不足时按钮隐藏（用缺权限的 auth mock）。
- `npm run test:unit` 全绿。
- 启动验证：`npm run dev`，从 5173 登录后，依次点击各模块页面，验证列表加载（需后端启动）。

### 完成判定（M7-C 验收标准）

- 10 个页面全部从 A 升级到 C：数据来自真实 API，无写死。
- 复用 PageTable/FormDialog/StatusTag，交互范式统一。
- 按钮按权限码显隐。
- 每页有 Vitest 测试，`npm run test:unit` 全绿。
- 启动验证证据记入完成报告。

---

## 完成后必须输出（阶段完成报告 - M7-C）

### 1. 修改/新增文件清单
列出 10 个页面 + 子组件，标注新增 N / 修改 M。

### 2. 关键实现说明
- 每个页面的核心功能点与对应 API。
- 状态机按钮显隐逻辑。
- 共享组件复用情况。
- ECharts 图表拆分。

### 3. 测试命令
`npm run test:unit`、启动验证步骤。

### 4. 测试结果
- 测试数 / 通过数。
- 启动验证：各页面列表加载证据（截图或 console）。

### 5. 偏离计划说明
如有与 M7 总任务不一致之处及原因。

### 6. 潜在风险与遗留问题
- 某些后端端点返回结构与前端预期不符的适配。
- ECharts 大屏性能。
- D 阶段需补的测试。

===== 复制到此结束 =====
