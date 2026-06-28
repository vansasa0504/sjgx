# Codex 桌面端 M7 阶段 D 执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M7-A/B/C 已全部通过 Claude Code 审查。
> 本阶段为 M7 第四阶段（收尾）：测试补齐 + 端到端回归。完成后由 Claude Code 做 M7 最终验收。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M7-api-and-frontend.md   ← M7 总任务来源（权威）
7. reviews/claude-review.md                   ← 含 M7-A/B/C 审查结论

当前执行阶段：M7-D（测试补齐与端到端回归）
请只执行本提示词中「M7-D 阶段任务」段落列出的任务。

---

## 项目背景（一句话）

金融机构外部数据采集平台。M7-A/B/C 已完成后端 Controller 补齐 + 鉴权、前端基础设施、九大模块页面功能化。本阶段是收尾：补齐遗漏测试 + 端到端主链路回归验证，为 M7 最终验收提供证据。

### 既有现状（必须基于此）

- 后端：全部 Controller + JWT + `@RequirePermission` 已就绪（M7-A）。
- 前端：API client 分层、auth store、路由守卫、共享组件、10 个功能化页面已就绪（M7-B/C）。
- 测试现状：M7-A 每个新 Controller 有 MockMvc 测试；M7-B 基础设施与共享组件有测试；M7-C 每个页面有 1 个 Vitest 测试。但可能存在覆盖不全的分支（异常路径、权限边界、并发状态流转）。

### 目录约束

- 主要修改 `src/test/`（后端）和 `platform-ui/src/__tests__/`（前端）。
- 如发现 A/B/C 阶段代码有 bug，按最小改动修复，并在完成报告中说明。
- 不修改 docs/tasks/reviews/delivery/perf/security/k8s-prod。
- 不引入新的大型依赖。

---

## 执行规则（全局）

1. 只实现 M7-D 阶段任务（测试补齐 + 回归 + bug 修复）。
2. 不新增功能、不重构。发现的 bug 按最小改动修复。
3. 修复必须验证：`mvn test` + `npm run test:unit` 全绿。
4. 端到端验证用真实启动（docker-compose + 全部服务 + 前端），记录 curl/截图证据。
5. 完成后输出「阶段完成报告 - M7-D」。

---

## M7-D 阶段任务

### 阶段目标

补齐测试覆盖到验收级别，端到端走通主链路，修复回归中发现的 bug，为 M7 最终验收提供完整证据。

### 前置条件

M7-A/B/C 审查通过；docker-compose 依赖可启动。

---

### 任务 D.1：后端测试补齐

#### D.1.1 Controller 测试覆盖核查

对 M7-A 新增的每个 Controller，核查 MockMvc 测试是否覆盖：
- 正常路径（200 + 返回体字段校验）
- 401（无 token）
- 403（权限不足，用缺少权限码的用户）
- 参数校验异常（400，如缺必填字段）
- 状态机非法转移（400/409，BusinessException）
- 资源不存在（404）

缺失的补上。重点核查：
- PartnerController：rating 越界、reject 空 reason、interface credential 加密落库验证。
- ConsumerController：配额耗尽超额拦截（调 invoke 或 consume 触发）。
- IngestController：协议不通（endpoint 不可达）、格式解析失败、质量校验拦截。
- DataServiceController：签名错误、时间戳过期、nonce 重放。
- QualityController：规则维度非法、triggerCheck 无效 ruleId。
- BillingController：generate bill 无数据、dispute 状态非法。
- StatsController：dashboard 空数据、audit 时间范围校验。
- UserController：重复用户名、权限码非法。

#### D.1.2 鉴权链路集成测试

新增 `platform-common` 或 `platform-auth` 的集成测试：
- `JwtAuthFilter` + `RequirePermissionAspect` 端到端：带 admin token 可访问全部端点、带普通用户 token（仅部分权限）访问越权端点返回 403、无 token 返回 401。
- `/auth/permissions` 返回与 token 对应的权限码。

#### D.1.3 回归测试

- `mvn test` 全模块 BUILD SUCCESS，0 failures。
- 如有失败，定位是测试本身问题还是代码 bug，修复并记录。

---

### 任务 D.2：前端测试补齐

#### D.2.1 页面测试覆盖核查

对 M7-C 的 10 个页面，核查 Vitest 测试是否覆盖：
- 挂载时调用正确 list API
- 搜索/筛选触发重新加载
- 分页触发重新加载
- 新建 FormDialog 提交调 create API
- 状态流转按钮调对应 events API
- 权限不足时按钮隐藏（用缺权限的 auth mock）
- 详情 drawer 打开调 detail API
- 错误反馈（mock api reject，验证 ElMessage.error 调用）

缺失的补上。

#### D.2.2 边界测试

- 空列表态（mock 返回空）渲染 el-empty。
- 加载失败态（mock reject）渲染错误提示。
- 表单校验（必填字段空时提交禁用）。

#### D.2.3 回归

- `npm run test:unit` 全绿。
- 如有失败，修复并记录。

---

### 任务 D.3：端到端主链路验证（手动，记录证据）

启动全部依赖与服务，从 `http://localhost:5173/` 登录，依次走通主链路，每步记录 curl 或截图证据：

1. **登录**：admin/admin123 → 拿 token，`GET /auth/permissions` 返回权限码列表。
2. **合作方**：`POST /api/v1/partners` 新建 → `POST /{id}/interfaces` 配置 HTTP 接口 → `POST /{id}/submit` → `POST /{id}/approve`。
3. **接入任务**：`POST /api/v1/ingest/tasks` 新建 → `POST /{id}/test` 测试接入 → 验证 `GET /api/v1/ingest/tasks/records` 有数据 → `POST /{id}/submit` → `POST /{id}/approve`。
4. **数据服务**：`POST /api/v1/services` 注册 → `POST /{serviceCode}/publish` 发布 → `POST /{serviceCode}/invoke` 调用（带签名）→ `GET /{serviceCode}/logs` 看调用日志。
5. **消费方**：`POST /api/v1/consumers` 注册 → `PUT /{id}/quota` 配额 → `POST /{id}/events` 审批 → 调用服务触发配额 → 验证超额拦截。
6. **数据质量**：`POST /api/v1/quality/rules` 新建规则 → `POST /api/v1/quality/checks` 触发校验 → `GET /api/v1/quality/issues` 看工单 → `POST /{id}/resolve` 解决。
7. **计费**：`POST /api/v1/billing/rules` 规则 → `POST /api/v1/billing/bills/generate` 生成账单 → `POST /{id}/confirm` 确认 → `GET /api/v1/billing/stats` 统计。
8. **统计监管**：`GET /api/v1/stats/dashboard` → `GET /api/v1/stats/audit` 审计追溯。
9. **系统管理**：`GET /users` → `POST /users` 新建用户 → `GET /roles` → `PUT /roles/{id}/permissions` 配置权限。
10. **权限校验**：用新建的低权限用户 token 访问越权端点，验证 403。

每步记录：请求 curl + 响应 JSON（脱敏 token）+ 是否符合预期。

---

### 任务 D.4：bug 修复

对 D.1~D.3 中发现的 bug，按最小改动修复：
- 后端 bug：修 Controller/Service/DTO，补回归测试。
- 前端 bug：修页面/组件/api，补回归测试。
- 每个 bug 在完成报告中列出：现象、根因、修复文件、回归测试。

---

### 任务 D.5：验收材料整理

更新 `delivery/acceptance-report.md` 的 M7 部分（如该文件存在且允许补充；若属 reviews/delivery 禁改范围则在完成报告中输出，不落盘）：
- 功能验收：46 条 FR 对应端点 + 页面覆盖情况。
- 鉴权验收：JWT + 权限码生效证据。
- 测试验收：后端/前端测试数与覆盖率。
- 端到端：主链路 10 步证据。

> 注意：`delivery/` 在 M7 总任务约束中未明确禁止（仅禁 docs/tasks/reviews），但若不确定则只在完成报告中输出，不落盘。

---

### 测试要求（M7-D）

- `mvn test` 全模块 BUILD SUCCESS。
- `npm run test:unit` 全绿。
- 端到端主链路 10 步全部走通，有 curl/截图证据。
- 发现的 bug 全部修复并有回归测试。

### 完成判定（M7-D 验收标准）

- 后端 Controller 测试覆盖正常/401/403/异常/状态机/不存在全部分支。
- 前端页面测试覆盖加载/搜索/分页/新建/流转/权限/错误全部分支。
- 端到端主链路走通。
- `mvn test` + `npm run test:unit` 全绿。
- bug 清单与修复记录完整。
- 验收材料完整（落盘或在完成报告中）。

---

## 完成后必须输出（阶段完成报告 - M7-D）

### 1. 修改/新增文件清单
分测试文件 / bug 修复文件 / 验收材料列出，标注新增 N / 修改 M。

### 2. 关键实现说明
- 补齐的测试覆盖点清单。
- bug 清单：现象/根因/修复/回归测试。
- 端到端主链路 10 步证据（curl + 响应）。

### 3. 测试命令
`mvn test`、`npm run test:unit`、端到端 curl 序列。

### 4. 测试结果
- 后端各模块测试数 / 通过数。
- 前端测试数 / 通过数。
- 端到端 10 步每步结论。

### 5. 偏离计划说明
如有与 M7 总任务不一致之处及原因。

### 6. 潜在风险与遗留问题
- 仍存在的已知缺陷。
- 上线前待验证项（性能/安全/国产化实测，属 M5/M6 范围）。
- 内存仓储 vs 落表的遗留。

### 7. M7 总体结论
- 46 条 FR 覆盖情况。
- 是否满足 docs/requirements.md §7 验收标准（开发环境可验证部分）。
- 是否建议 Claude Code 最终验收通过。

===== 复制到此结束 =====
