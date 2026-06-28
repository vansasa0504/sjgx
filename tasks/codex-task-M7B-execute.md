# Codex 桌面端 M7 阶段 B 执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M7-A 已通过 Claude Code 审查（后端 Controller + 鉴权已就绪，`/auth/permissions` 可用）。
> 本阶段为 M7 第二阶段：前端基础设施。完成后才能进入 C（页面功能化）。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M7-api-and-frontend.md   ← M7 总任务来源（权威）
7. reviews/claude-review.md                   ← 含 M7-A 审查结论

当前执行阶段：M7-B（前端基础设施）
请只执行本提示词中「M7-B 阶段任务」段落列出的任务。

---

## 项目背景（一句话）

金融机构外部数据采集平台前端：Vue3 3.4 + Vite 5 + Pinia 2 + Element Plus 2.7 + ECharts 5.5 + axios + vue-router 4。M7-A 已补齐后端全部 Controller 与鉴权，`/auth/permissions` 返回当前用户权限码列表。

### 既有现状（必须基于此）

- 前端入口 `http://localhost:5173/`，Vite dev proxy 已配置：`/auth`、`/api` → `http://localhost:8080`（Gateway）。
- `src/api/client.ts`：仅 1 个 axios 实例（baseURL `/api/v1`，token 拦截器，401 触发 `auth-expired` 事件）+ 1 个函数 `fetchDashboard()`。**无其他 API 函数**。
- `src/stores/auth.ts`：login 已接 `/auth/login`，但 permissions 硬编码 9 个 `*:view`，无 refresh/logout API 调用，无 fetchPermissions。
- `src/router/index.ts`：11 个路由静态 import，`beforeEach` 检查 `meta.permission` 但 `hasPermission` 恒 true（权限全开）。无 403 页面。
- 10 个业务页面全是 A 类骨架（写死 `const rows`，无 API 调用）——**本阶段不动业务页面，C 阶段处理**。
- 无共享组件、无布局组件。

### 目录约束

- 所有新增前端代码放 `platform-ui/src/` 下。
- 不修改 `vite.config.ts`（proxy 已配好）、不修改后端代码、不修改 docs/tasks/reviews。
- 不引入新的大型前端依赖（现有 Element Plus/ECharts/Pinia/axios 足够）。

---

## 执行规则（全局）

1. 只实现 M7-B 阶段任务（基础设施），不动业务页面内容（C 阶段做）。
2. 最小改动，复用现有 axios 实例与 Element Plus 组件。
3. 必须补充 Vitest 测试，`npm run test:unit` 全绿。
4. 所有 API 函数签名对齐 M7-A 已实现的后端端点（见 `tasks/codex-task-M7-api-and-frontend.md` §5 端点表）。
5. TypeScript 类型优先用 interface 定义 DTO，避免 any。
6. 完成后输出「阶段完成报告 - M7-B」。

---

## M7-B 阶段任务

### 阶段目标

为 C 阶段页面功能化打好基础设施：API client 分层、auth store 接真实权限、路由守卫真实校验、共享组件、布局组件。

### 前置条件

M7-A 审查通过；后端 `/auth/permissions`、`/auth/refresh`、`/auth/logout` 可用。

---

### 任务 B.1：API client 重构

#### B.1.1 保留并增强 `src/api/client.ts`

- axios 实例 `api`：baseURL `/api/v1`，timeout 10000。
- 请求拦截器：注入 `Authorization: Bearer <token>`（从 localStorage）。
- 响应拦截器：401 触发 `auth-expired` 事件（已有，保留）；非 200 且 `data.success===false` 时 `Promise.reject(new Error(data.message))`。
- 移除 `fetchDashboard` 到 `src/api/stats.ts`。

#### B.1.2 新增 `src/api/auth.ts`

单独的 axios 实例（baseURL `/auth`，或裸 axios 调 `/auth/*`，因为后端 auth 基路径是 `/auth` 不是 `/api/v1/auth`）：

```ts
export async function login(username: string, password: string): Promise<string>  // 返回 token
export async function refresh(): Promise<string>
export async function logout(): Promise<void>
export async function fetchPermissions(): Promise<string[]>
```

#### B.1.3 新增按模块 API 文件

每个文件导出对应模块的 API 函数，函数签名严格对齐 M7-A 端点表。所有分页函数返回 `{ records: T[], total: number, current: number, size: number }`（或 `Page<T>` 形态）。

- `src/api/partner.ts`：listPartners, getPartner, createPartner, updatePartner, submitPartner, approvePartner, rejectPartner, ratePartner, terminatePartner, configureInterface, listInterfaces, listPartnerEvents
- `src/api/consumer.ts`：listConsumers, getConsumer, registerConsumer, configureQuota, applyConsumerEvent, getConsumerAudit, getConsumerLogs
- `src/api/ingest.ts`：listIngestTasks, getIngestTask, createIngestTask, updateMapping, updateRules, testIngest, submitIngest, approveIngest, offlineIngest, listIngestRecords
- `src/api/service.ts`：listServices, getService, registerService, updateService, testService, publishService, offlineService, listServiceLogs, invokeService
- `src/api/catalog.ts`：listCatalog, getCatalogMeta, previewCatalog, searchCatalog, applyCatalog, approveApplication
- `src/api/quality.ts`：listQualityRules, createQualityRule, updateQualityRule, listChecks, triggerCheck, listIssues, assignIssue, resolveIssue, getQualityReport, getQualityScore
- `src/api/billing.ts`：listBillingRules, createBillingRule, updateBillingRule, listBills, generateBill, confirmBill, disputeBill, getBillingStats
- `src/api/stats.ts`：fetchDashboard, generateReport, listAudit
- `src/api/system.ts`：listUsers, createUser, updateUser, listRoles, createRole, updateRolePermissions, listPermissions

要求：
- 每个函数有 TypeScript 入参/返回类型定义（用 interface 定义 DTO，放 `src/api/types.ts` 或各文件内）。
- GET 请求的 query 参数用 `params`；POST/PUT 用 `data`。
- 分页参数统一 `{ page, size, ...filters }`。

#### B.1.4 测试

- `src/api/__tests__/client.test.ts`：mock axios，验证 token 注入、401 触发事件、错误消息 reject。
- 至少 2 个模块 API 文件的测试（mock axios，验证调用了正确 url + method + params）。

---

### 任务 B.2：auth store 重构

重写 `src/stores/auth.ts`（Pinia options store 或 setup store 均可）：

State：
- `token: string`（从 localStorage 读，空字符串表示未登录）
- `permissions: string[]`（默认空数组，登录后从后端拉取）
- `username: string`

Actions：
- `login(username, password)`：调 `api/auth.login`，存 token 到 state + localStorage，然后 `await fetchPermissions()`。
- `fetchPermissions()`：调 `api/auth.fetchPermissions`，写入 `state.permissions`。
- `refresh()`：调 `api/auth.refresh`，更新 token。
- `logout()`：调 `api/auth.logout`（即使失败也继续），清 token + permissions + localStorage。
- `hasPermission(code: string): boolean`：`permissions.includes(code)`。
- `hasAnyPermission(codes: string[]): boolean`：满足其一即可。

要求：
- login 成功后必须 `fetchPermissions`，确保 permissions 非空。
- token 过期（401 事件）时自动 logout 并跳登录。
- 保留对 localStorage 的读写，刷新页面后状态不丢。

测试：`src/stores/__tests__/auth.test.ts`，mock api，验证 login 后 permissions 被拉取、hasPermission 正确、logout 清空。

---

### 任务 B.3：路由守卫重构

修改 `src/router/index.ts`：

- 路由 meta 增加 `permission?: string`（单个权限码）或 `permissions?: string[]`。
- `beforeEach`：
  1. 若去 `/login`：已登录则跳 `/partners`，否则放行。
  2. 若无 token：跳 `/login?redirect=<to.path>`。
  3. 若有 token 但 `permissions` 为空：`await auth.fetchPermissions()`；失败则 logout 跳登录。
  4. 校验 `meta.permission`/`meta.permissions`：用 `auth.hasPermission`/`hasAnyPermission`；不满足跳 `/403`。
- 新增 `/403` 路由 + `src/views/ForbiddenView.vue`（简单提示"权限不足" + 返回按钮）。
- 新增 `/404` 路由（catch-all）。
- 各业务路由补上 `meta.permission`（与 C 阶段页面对应，如 `/partners` → `partner:view`）。本阶段先把 meta 写上，C 阶段页面实现时不再改路由。

测试：`src/router/__tests__/guard.test.ts`，验证无 token 跳 login、权限不足跳 403、有权限放行。

---

### 任务 B.4：共享组件

#### B.4.1 `src/components/PageTable.vue`

通用分页表格组件：
- Props：
  - `columns: Array<{ prop, label, width?, formatter?(row,col,value) }>`
  - `fetchData: (params: { page, size, ...filters }) => Promise<{ records, total }>`
  - `filters?: Array<{ prop, label, options? }>`（搜索区）
  - `pageSizes?: number[]`（默认 [10,20,50]）
- 行为：
  - 挂载时调 `fetchData({ page:1, size:10 })` 加载。
  - 分页/size 变化重新加载。
  - filters 变化时重置到第 1 页。
  - loading 态用 `el-table` 的 `v-loading`。
  - 空态用 `el-empty`。
- 暴露 `refresh()` 方法供父组件刷新。

#### B.4.2 `src/components/FormDialog.vue`

通用表单弹窗：
- Props：
  - `modelValue: boolean`（v-model 控制显隐）
  - `title: string`
  - `fields: Array<{ prop, label, type: 'input'|'select'|'number'|'date'|'textarea', options?, required?, rules? }>`
  - `initial?: Record<string, any>`（编辑时初值）
  - `submit: (form) => Promise<void>`
- 行为：
  - 用 `el-dialog` + `el-form`，fields 动态渲染。
  - 提交调 `submit`，成功后关闭弹窗 + emit `success`；失败显示错误。
  - 关闭时重置表单。

#### B.4.3 `src/components/StatusTag.vue`

状态标签：
- Props：`status: string`，`map?: Record<string, { type: 'success'|'warning'|'danger'|'info', text }>`
- 用 `el-tag` 渲染，内置常见状态机状态默认映射（PENDING/ACTIVE/SUSPENDED/TERMINATED/DRAFT/ONLINE/OFFLINE/GENERATED/CONFIRMED/DISPUTED/SETTLED/OPEN/CLOSED 等）。

#### B.4.4 测试

每个组件 1 个 Vitest 测试：
- `PageTable.test.ts`：mock fetchData，验证挂载加载、分页触发重新加载、空态。
- `FormDialog.test.ts`：验证字段渲染、提交调用、关闭重置。
- `StatusTag.test.ts`：验证不同状态渲染对应 tag type。

---

### 任务 B.5：布局组件

#### B.5.1 `src/layouts/ConsoleLayout.vue`

- 顶栏：平台名 + 当前用户名 + 登出按钮（调 `auth.logout` 跳 `/login`）。
- 左侧菜单：用 `el-menu`，按权限码过滤显示（无权限的菜单项隐藏）。菜单项对应 11 个路由（合作方/接入/服务/目录/消费方/质量/计费/统计/系统/监控）。
- 内容区：`<router-view>`，套 `<el-main>`。
- 响应式：窄屏菜单折叠。

#### B.5.2 套用布局

修改路由：登录后的业务路由用 `ConsoleLayout` 作为父路由的 `component`，子路由 `<router-view>` 渲染。或用全局 layout 包裹。`/login`、`/403`、`/404` 不套 ConsoleLayout。

#### B.5.3 测试

- `ConsoleLayout.test.ts`：mock auth，验证菜单按权限显示/隐藏、登出调用。

---

### 任务 B.6：app 入口与全局错误处理

- `src/App.vue`：监听 `auth-expired` 事件，调 `auth.logout` 跳登录（带 redirect）。
- `src/main.ts`：确保 Pinia + router + ElementPlus 注册（已有，核对）。
- 全局错误提示：在 `client.ts` 响应拦截器或 App.vue 监听，用 `ElMessage.error` 显示后端错误消息（非 401）。

---

### 测试要求（M7-B）

- API client、auth store、路由守卫、3 个共享组件、布局组件各有 Vitest 测试。
- `npm run test:unit` 全绿。
- 启动验证：`npm run dev`，从 `http://localhost:5173/` 登录 admin/admin123，验证：
  - 登录成功后 permissions 被拉取（vue devtools 或 console 可查）。
  - 菜单按权限显示。
  - 登出可清状态跳回登录页。
  - 直接访问无权限路由（用非 admin 用户或改 meta 模拟）跳 403。

### 完成判定（M7-B 验收标准）

- API client 按模块分文件，函数签名对齐 M7-A 端点。
- auth store 接真实 login/refresh/logout/permissions。
- 路由守卫真实校验权限，有 403/404 页面。
- PageTable/FormDialog/StatusTag/ConsoleLayout 可用且有测试。
- `npm run test:unit` 全绿。
- 启动验证证据记入完成报告。
- 业务页面内容本阶段**保持原样**（C 阶段改）。

---

## 完成后必须输出（阶段完成报告 - M7-B）

### 1. 修改/新增文件清单
分 api/stores/router/components/layouts/views 列出，标注新增 N / 修改 M。

### 2. 关键实现说明
- API client 分层结构。
- auth store 状态机与 permissions 拉取时机。
- 路由守卫校验流程。
- 共享组件 Props 设计。

### 3. 测试命令
`npm run test:unit`、启动验证步骤。

### 4. 测试结果
- 测试数 / 通过数。
- 启动验证：登录 + permissions 拉取 + 登出 + 403 证据。

### 5. 偏离计划说明
如有与 M7 总任务不一致之处及原因。

### 6. 潜在风险与遗留问题
- TypeScript 类型完整性。
- C 阶段页面需注意的点。

===== 复制到此结束 =====
