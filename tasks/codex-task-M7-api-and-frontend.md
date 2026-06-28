# Codex 执行任务 - M7：API 补齐 + 前端功能化 + 鉴权接通

> 本文件由 Claude Code 基于 `docs/requirements.md`、`tasks/claude-plan.md` §4.5.2 接口设计、以及 M6 后的实际代码差距生成。
> 日期：2026-06-26
> 前置条件：M6 已通过审查，Gateway 静态路由与前端登录代理已修复（`http://localhost:5173/` 可用 admin/admin123 登录）。
> 本阶段为 M4 前端骨架的返工 + 后端缺失 Controller 的补齐，**不重写已有领域逻辑**。

---

## 0. 背景与差距（必须基于此）

经 Claude Code 审查，当前真实状态：

### 后端
- 仅 3 个 Controller、10 个端点：`/auth/*`(3)、`/api/v1/partners/*`(4)、`/api/v1/ingest/tasks/*`(3)。
- **完全缺失 Controller 的模块**：services、catalog、consumer、quality、billing、stats、users/roles/permissions。
- 但这些模块的**领域 Service 类已存在**：`DataServiceManager`、`CatalogService`、`ConsumerService`、`QualityIssueService`、`QualityScoringService`、`QualityCheckExecutor`、`BillService`、`BillGenerator`、`BillingRuleEngine`、`StatsAggregator`、`DashboardService`、`ReportGenerator`、`AuditTraceService`。
- `@RequirePermission` 注解已定义于 `platform-auth`，但**全仓库零使用**；Gateway 仅做 JWT 透传，无服务端权限校验。
- Gateway 已为所有目标路径配置了静态路由（`/auth/**`、`/api/v1/partners/**`、`/api/v1/ingest/**`、`/api/v1/services/**`、`/api/v1/catalog/**`、`/api/v1/quality/**`、`/api/v1/billing/**`、`/api/v1/stats/**`），**Controller 一旦存在即可通过 8080 访问**。

### 前端
- 仅 `LoginView.vue` 接真实 API（`/auth/login`）。
- 10 个页面（Partner/Ingest/Service/Catalog/Consumer/Quality/Billing/Stats/System/Monitor）全是 A 类骨架：写死 `const rows=[...]`，无任何 `api.*` 调用，按钮无处理或空处理。
- `api/client.ts` 只有 1 个函数 `fetchDashboard()`，且后端 `/api/v1/stats/dashboard` 未实现。
- `stores/auth.ts` 权限硬编码 9 个 `*:view`，不从后端拉取；无 refresh/logout API 调用。

### 目标接口（权威来源）
`tasks/claude-plan.md` §4.5.2 已定义全部目标 REST 端点。本任务所有新增 Controller 必须对齐该表的方法/路径/入参/返回。

---

## 1. 任务目标

把平台从"可登录的骨架"升级为"九大模块可操作的最小可用控制台"，对齐 `docs/requirements.md` 的 FR-101~906 与 `claude-plan.md` §4.5.2 接口设计。

**最小可行结果**：登录后，管理员可在前端完成每条 FR 对应的至少一个完整操作闭环（列表→新增→详情→状态流转→查询），且每个后端端点有 Controller 实现 + 单测/集成测试。

---

## 2. 范围

### 本次实现（M7）
- 后端：补齐 services/catalog/consumer/quality/billing/stats/users-roles-permissions 的 Controller + DTO + 权限注解。
- 后端：补齐 partner/ingest 缺失的端点（列表分页、详情、下线等）。
- 前端：API client 层重构（按模块分文件）、auth store 接 refresh/logout/permissions、路由守卫接真实权限、共享组件（分页表格、表单弹窗）。
- 前端：10 个骨架页面全部升级为可操作页面（分类 A→C）。
- 测试：每个新 Controller 至少 1 个 MockMvc 测试（正常+异常+权限）；每个前端页面至少 1 个 Vitest 渲染/交互测试。

### 本次不实现（保留给后续或外部环境）
- MFA、IAM/SSO 真实对接（保留接口与 Mock）。
- 监管报送真实对接（FR-803，待规范）。
- 财务/采购真实对接（FR-705，待规范）。
- AI 智能推荐（FR-404，仅规则推荐）。
- 48h 稳定性/压测/达梦 OceanBase 实测（M6 已交付脚本，待上线环境）。
- 不重写已有领域 Service 内部逻辑，只做 Controller 包装与必要适配。

---

## 3. 执行规则（全局）

1. 只实现本任务清单列出的端点与页面，不越界到 M5/M6 已交付的 perf/security/delivery。
2. 不重新解释需求，`claude-plan.md` §4.5.2 是接口权威来源。
3. 最小改动：复用已有领域 Service，Controller 只做参数装配 + 调用 + `Result<T>` 包装。
4. 不修改：`.env`、证书、`docs/`、`tasks/`、`reviews/`、`k8s/prod/`、`delivery/`、`perf/`、`security/`。密钥一律 `${ENV_VAR}`。
5. 必须补充或更新测试，`mvn test` + `npm run test:unit` 全绿才可声明完成。
6. 国产化兼容：不手写 `LIMIT`/`ON DUPLICATE KEY UPDATE`，分页用 MyBatis-Plus；SQL 迁移如有新增须同步 U00x 回滚。
7. 鉴权：所有非 `/auth/login` 端点必须通过 JWT 校验；写操作按本清单标注的权限码加 `@RequirePermission`。
8. 完成后输出"阶段完成报告 - M7"。

---

## 4. 阶段拆分

本任务分 4 个阶段，建议分 4 个 Codex 会话串行执行，每阶段 Claude Code 审查通过后再进入下一阶段。

- **阶段 A**：后端 Controller 补齐 + 鉴权接通
- **阶段 B**：前端基础设施（API client / auth / 路由 / 共享组件）
- **阶段 C**：前端九大模块页面功能化
- **阶段 D**：测试补齐与回归

---

## 5. 阶段 A：后端 Controller 补齐 + 鉴权接通

### A.0 鉴权基础设施（先做，所有 Controller 依赖）

1. 在 `platform-auth` 实现 `GET /auth/permissions`：解析当前 JWT，返回用户权限码列表（供前端路由守卫与按钮控制用）。入参：`Authorization` header；返回 `Result<List<String>>`。
2. 在 `platform-common` 新增 `JwtAuthFilter`（servlet filter）或 `@ControllerAdvice` + AOP：对除 `/auth/login`、`/actuator/**` 外的所有请求校验 JWT，失败返回 401 `Result`；将 `AuthPrincipal` 放入 request attribute 供 Controller 使用。
3. `@RequirePermission` AOP：被注解的方法在调用前校验当前用户权限码，缺失返回 403 `Result`。
4. 各业务模块 Spring Security 配置放行 `/auth/login`、`/actuator/**`，其余 `authenticated()`。

### A.1 platform-partner（FR-101~104 合作方 + FR-501~505 消费方）

补齐 `PartnerController` + 新增 `ConsumerController`，对齐 claude-plan §4.5.2。

Partner 端点（基路径 `/api/v1/partners`）：
| 方法 | 路径 | 入参 | 返回 | 权限码 | 领域 Service |
|---|---|---|---|---|---|
| POST | `/` | `CreatePartnerRequest{name,dataType,industry,complianceLevel}` | `Result<Partner>` | partner:create | PartnerService.create |
| GET | `/` | `page,size,keyword,dataType,status` (query) | `Result<Page<Partner>>` | partner:view | PartnerService.list(新增) |
| GET | `/{id}` | id | `Result<Partner>` | partner:view | PartnerService.detail |
| PUT | `/{id}` | `UpdatePartnerRequest` | `Result<Partner>` | partner:update | PartnerService.update(新增) |
| POST | `/{id}/submit` | id | `Result<Partner>` | partner:approve | PartnerService.apply(SUBMIT) |
| POST | `/{id}/approve` | id | `Result<Partner>` | partner:approve | PartnerService.apply(APPROVE) |
| POST | `/{id}/reject` | `RejectRequest{reason}` | `Result<Partner>` | partner:approve | PartnerService.apply(REJECT) |
| PUT | `/{id}/rating` | `RatingRequest{score}` | `Result<Partner>` | partner:update | PartnerService.rating(新增) |
| POST | `/{id}/terminate` | id | `Result<Partner>` | partner:approve | PartnerService.apply(TERMINATE) |
| POST | `/{id}/interfaces` | `InterfaceRequest{protocol,endpoint,authType,credential,rateLimit}` | `Result<PartnerInterfaceConfig>` | partner:update | PartnerService.configureInterface |
| GET | `/{id}/interfaces` | id | `Result<List<PartnerInterfaceConfig>>` | partner:view | PartnerService.listInterfaces(新增) |
| GET | `/{id}/events` | id | `Result<List<PartnerEvent>>` | partner:view | PartnerService.listEvents(新增) |

> credential 必须经 `Sm4Util` 加密后存储，返回时脱敏。

Consumer 端点（基路径 `/api/v1/consumers`）：
| 方法 | 路径 | 入参 | 返回 | 权限码 | 领域 Service |
|---|---|---|---|---|---|
| POST | `/` | `CreateConsumerRequest{name,bizLine,systemType,complianceLevel}` | `Result<Consumer>` | consumer:create | ConsumerService.register |
| GET | `/` | `page,size,keyword,bizLine,status` | `Result<Page<Consumer>>` | consumer:view | ConsumerService.list(新增) |
| GET | `/{id}` | id | `Result<Consumer>` | consumer:view | ConsumerService.detail(新增) |
| PUT | `/{id}/quota` | `QuotaRequest{freqLimit,volumeLimit,scope}` | `Result<Consumer>` | consumer:update | ConsumerService.configureQuota |
| POST | `/{id}/events` | `ConsumerEventRequest{event}` | `Result<Consumer>` | consumer:approve | ConsumerService.apply |
| GET | `/{id}/audit` | `page,size` | `Result<Page<AuditEvent>>` | consumer:view | AuditLogRepository.findByActor |
| GET | `/{id}/logs` | `page,size` | `Result<Page<ServiceInvokeLog>>` | consumer:view | AsyncInvokeLogWriter/Repository |

### A.2 platform-pipeline.ingest（FR-201~205 接入）

补齐 `IngestController`（基路径 `/api/v1/ingest/tasks`）：
| 方法 | 路径 | 入参 | 返回 | 权限码 | Service |
|---|---|---|---|---|---|
| POST | `/` | `CreateIngestTaskRequest{partnerId,interfaceId,protocol,format,endpoint,syncMode,cron,fieldMapping,qualityRules}` | `Result<IngestTask>` | ingest:create | IngestService.createTask |
| GET | `/` | `page,size,partnerId,status` | `Result<Page<IngestTask>>` | ingest:view | IngestService.list(新增) |
| GET | `/{id}` | id | `Result<IngestTask>` | ingest:view | IngestService.detail(新增) |
| PUT | `/{id}/mapping` | `MappingRequest` | `Result<IngestTask>` | ingest:update | IngestService.updateMapping(新增) |
| PUT | `/{id}/rules` | `RulesRequest` | `Result<IngestTask>` | ingest:update | IngestService.updateRules(新增) |
| POST | `/{id}/test` | id | `Result<List<RawDataRecord>>` | ingest:update | IngestService.testAndIngest |
| POST | `/{id}/submit` | id | `Result<IngestTask>` | ingest:approve | IngestService.apply(SUBMIT) |
| POST | `/{id}/approve` | id | `Result<IngestTask>` | ingest:approve | IngestService.apply(APPROVE) |
| POST | `/{id}/offline` | id | `Result<IngestTask>` | ingest:approve | IngestService.apply(OFFLINE) |
| GET | `/records` | `page,size,taskId,batchNo` | `Result<Page<IngestRecord>>` | ingest:view | IngestService.records |

### A.3 platform-pipeline.service（FR-301~305 数据服务）

新增 `DataServiceController`（基路径 `/api/v1/services`），包装 `DataServiceManager`：
| 方法 | 路径 | 入参 | 返回 | 权限码 | Service |
|---|---|---|---|---|---|
| POST | `/` | `RegisterServiceRequest{serviceCode,name,type,endpointPath,rateLimit,timeoutMs,retryCount,circuitBreaker}` | `Result<DataService>` | service:create | DataServiceManager.register |
| GET | `/` | `page,size,keyword,status` | `Result<Page<DataService>>` | service:view | DataServiceManager.list(新增) |
| GET | `/{id}` | id(或 serviceCode) | `Result<DataService>` | service:view | DataServiceManager.detail(新增) |
| PUT | `/{id}` | `UpdateServiceRequest` | `Result<DataService>` | service:update | DataServiceManager.update(新增) |
| POST | `/{id}/test` | id | `Result<TestResult>` | service:update | DataServiceManager.apply(TEST) |
| POST | `/{id}/publish` | id | `Result<DataService>` | service:approve | DataServiceManager.apply(PUBLISH) |
| POST | `/{id}/offline` | id | `Result<DataService>` | service:approve | DataServiceManager.apply(OFFLINE) |
| GET | `/{id}/logs` | `page,size,consumerId,status` | `Result<Page<ServiceInvokeLog>>` | service:view | AsyncInvokeLogWriter.logs |
| POST | `/{serviceCode}/invoke` | `InvokeRequest{consumerCode,apiKey,timestamp,nonce,params,signature}` | `Result<String>` | （公开，API Key+签名鉴权） | DataServiceManager.invoke |

> `/invoke` 走 API Key + HMAC 签名 + nonce 防重放，不走 JWT；其余走 JWT。

### A.4 platform-pipeline.catalog（FR-401~405 数据目录）

新增 `CatalogController`（基路径 `/api/v1/catalog`），包装 `CatalogService`：
| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/` | `page,size,topic,partnerId,dataType,scene` | `Result<Page<DataCatalog>>` | catalog:view |
| GET | `/{id}/meta` | id | `Result<DataCatalogMeta>` | catalog:view |
| GET | `/{id}/preview` | id | `Result<PreviewResult{sample,stats,qualityReport}>` | catalog:view |
| GET | `/search` | `keyword,tags,page,size` | `Result<Page<DataCatalog>>` | catalog:view |
| POST | `/{id}/apply` | `ApplyRequest{reason,scope}` | `Result<Application>` | catalog:apply |
| POST | `/applications/{id}/approve` | id | `Result<Application>` | catalog:approve |

### A.5 platform-quality（FR-901~906 数据质量）

新增 `QualityController`（基路径 `/api/v1/quality`），包装 `QualityCheckExecutor`/`QualityIssueService`/`QualityScoringService`：
| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| POST | `/rules` | `CreateQualityRuleRequest{code,name,dimension,targetObject,expression,severity}` | `Result<QualityRuleConfig>` | quality:create |
| GET | `/rules` | `page,size,dimension,enabled` | `Result<Page<QualityRuleConfig>>` | quality:view |
| PUT | `/rules/{id}` | `UpdateQualityRuleRequest` | `Result<QualityRuleConfig>` | quality:update |
| GET | `/checks` | `page,size,ruleId,batchNo` | `Result<Page<QualityCheckResult>>` | quality:view |
| POST | `/checks` | `TriggerCheckRequest{ruleIds,target}` | `Result<List<QualityCheckResult>>` | quality:run |
| GET | `/issues` | `page,size,status,severity` | `Result<Page<QualityIssue>>` | quality:view |
| POST | `/issues/{id}/assign` | `AssignRequest{assignee}` | `Result<QualityIssue>` | quality:update |
| POST | `/issues/{id}/resolve` | `ResolveRequest{resolution}` | `Result<QualityIssue>` | quality:update |
| GET | `/reports` | `partnerId,dataType,from,to` | `Result<QualityReport>` | quality:view |
| GET | `/scores` | `partnerId,from,to` | `Result<QualityScore>` | quality:view |

### A.6 platform-billing（FR-701~705 计费 + FR-801~805 统计监管）

新增 `BillingController`（基路径 `/api/v1/billing`），包装 `BillingRuleEngine`/`BillService`：
| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/rules` | `page,size,targetType,model` | `Result<Page<BillingRule>>` | billing:view |
| POST | `/rules` | `CreateBillingRuleRequest` | `Result<BillingRule>` | billing:create |
| PUT | `/rules/{id}` | `UpdateBillingRuleRequest` | `Result<BillingRule>` | billing:update |
| GET | `/bills` | `page,size,billType,period,status` | `Result<Page<Bill>>` | billing:view |
| POST | `/bills/generate` | `GenerateBillRequest{billType,period,start,end}` | `Result<Bill>` | billing:run |
| POST | `/bills/{id}/confirm` | id | `Result<Bill>` | billing:approve |
| POST | `/bills/{id}/dispute` | `DisputeRequest{reason}` | `Result<Bill>` | billing:approve |
| GET | `/stats` | `from,to,partnerId,consumerId` | `Result<BillingStats>` | billing:view |

新增 `StatsController`（基路径 `/api/v1/stats`），包装 `StatsAggregator`/`DashboardService`/`ReportGenerator`：
| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/dashboard` | 无 | `Result<DashboardSummary>` | stats:view |
| GET | `/reports` | `type,from,to` | `Result<GeneratedReport>` | stats:view |
| GET | `/audit` | `page,size,actorType,eventType,from,to` | `Result<Page<AuditEvent>>` | stats:view |

### A.7 platform-auth 用户/角色/权限（支撑 SystemView + 权限下发）

新增 `UserController`（基路径 `/users`）、`RoleController`（基路径 `/roles`）、`PermissionController`（基路径 `/permissions`）：
| 方法 | 路径 | 返回 | 权限码 |
|---|---|---|---|
| GET | `/users` | `Result<Page<UserAccount>>` | system:view |
| POST | `/users` | `Result<UserAccount>` | system:create |
| PUT | `/users/{id}` | `Result<UserAccount>` | system:update |
| GET | `/roles` | `Result<List<Role>>` | system:view |
| POST | `/roles` | `Result<Role>` | system:create |
| PUT | `/roles/{id}/permissions` | `Result<Role>` | system:update |
| GET | `/permissions` | `Result<List<String>>` | system:view |

> 用户/角色当前为内存实现（admin/admin123），本阶段保持内存或落 `t_user`/`t_role`/`t_permission`（V001 已建表），优先落表以支持 CRUD。如落表须补 V010 迁移 + U010 回滚。

### A.8 阶段 A 完成判定
- 所有上述端点可通过 Gateway 8080 访问，返回 `Result<T>`。
- 未带 token 访问受保护端点返回 401；权限不足返回 403。
- 每个新 Controller 至少 1 个 MockMvc 测试（正常 + 401/403）。
- `mvn test` 全绿。

---

## 6. 阶段 B：前端基础设施

### B.1 API client 重构
- `src/api/client.ts`：保留 axios 实例（baseURL `/api/v1`，token 拦截器，401 跳登录）；移除 `fetchDashboard` 到独立文件。
- 新增按模块分文件：`src/api/auth.ts`、`partner.ts`、`consumer.ts`、`ingest.ts`、`service.ts`、`catalog.ts`、`quality.ts`、`billing.ts`、`stats.ts`、`system.ts`。每个文件导出对应模块的 API 函数，函数签名对齐阶段 A 端点。
- `src/api/auth.ts` 单独用 baseURL `/auth` 的实例（或裸 axios），封装 login/refresh/logout/permissions。

### B.2 auth store 重构
- `login(username,password)`：调 `/auth/login`，存 token。
- `fetchPermissions()`：调 `/auth/permissions`，写入 `state.permissions`。
- `refresh()`：调 `/auth/refresh`，续期 token。
- `logout()`：调 `/auth/logout`，清 token + permissions。
- 登录成功后自动 `fetchPermissions()`。

### B.3 路由守卫
- `beforeEach`：无 token 跳 `/login`；有 token 但 permissions 为空时先 `await fetchPermissions()`。
- `meta.permission` 校验改为真实 `hasPermission`，权限不足跳 `/403`。
- 新增 `/403` 页面。

### B.4 共享组件
- `src/components/PageTable.vue`：分页表格（props: columns, fetchData(params)→{rows,total}, page, size），统一分页/loading/空态。
- `src/components/FormDialog.vue`：表单弹窗（props: title, fields, modelValue, onSubmit），统一新增/编辑交互。
- `src/components/StatusTag.vue`：状态标签（props: status, map），统一状态机状态渲染。

### B.5 布局
- `src/layouts/ConsoleLayout.vue`：左侧菜单（按权限码过滤显示）+ 顶栏（用户名/登出）+ 内容区。登录后页面套用此布局。

### B.6 阶段 B 完成判定
- 登录后菜单按权限显示，登出可清状态。
- `PageTable`/`FormDialog`/`StatusTag` 有 Vitest 渲染测试。
- `npm run test:unit` 全绿。

---

## 7. 阶段 C：前端九大模块页面功能化

每个页面统一要求：使用 `PageTable` 展示列表 + 搜索；使用 `FormDialog` 做新增/编辑；状态流转用按钮调对应 events 端点；详情用抽屉/弹窗。所有数据来自阶段 A 端点，**禁止写死 `const rows`**。

### C.1 PartnerView（合作方管理，FR-101~104）
- 列表列：编码、名称、数据类型、合规等级、状态、评级、创建时间。
- 搜索：关键词、数据类型、状态。
- 操作：新建（FormDialog）、查看详情（抽屉：基本信息 + 接口列表 + 生命周期事件）、提交审核、准入、驳回、评级、退出、配置接口。
- 权限码：partner:view/create/update/approve。

### C.2 IngestView（接入任务，FR-201~205）
- 列表列：任务编码、合作方、协议、格式、同步模式、状态、版本。
- 搜索：合作方、状态。
- 操作：新建任务（FormDialog：选合作方/接口、协议、格式、同步模式、cron、字段映射、质量规则）、测试接入（展示落库 RawData）、提交审批、审批通过、下线、查看执行记录（PageTable）。
- 权限码：ingest:view/create/update/approve。

### C.3 ServiceView（数据服务，FR-301~305）
- 列表列：服务编码、名称、类型、路由、限流、状态、版本。
- 操作：注册、编辑、测试、发布、下线、查看调用日志（PageTable：consumer、状态、耗时、responseSize、时间）。
- 权限码：service:view/create/update/approve。

### C.4 CatalogView（数据目录，FR-401~405）
- 列表/卡片：按主题/合作方/类型/场景多维筛选。
- 操作：查看元信息、数据预览（样本+统计+质量报告）、检索、申请使用、审批申请。
- 权限码：catalog:view/apply/approve。

### C.5 ConsumerView（消费方管理，FR-501~505）
- 列表列：编码、名称、业务条线、系统类型、合规等级、状态。
- 操作：注册、配置配额（频次/数据量/范围）、审批事件、查看行为审计、查看调用日志。
- 权限码：consumer:view/create/update/approve。

### C.6 QualityView（数据质量，FR-901~906）
- Tab1 规则：列表 + 新建/编辑（维度、校验对象、表达式、严重级别）。
- Tab2 校验结果：列表 + 手动触发校验。
- Tab3 问题工单：列表 + 指派 + 解决（状态流转）。
- Tab4 报告/评分：按合作方/类型/时间生成报告与评分。
- 权限码：quality:view/create/update/run。

### C.7 BillingView（计费管理，FR-701~705）
- Tab1 计费规则：列表 + 新建/编辑（模型、目标类型、目标 ID、单价、生效期）。
- Tab2 账单：列表 + 生成 + 确认 + 异议。
- Tab3 费用统计：按时间/合作方/消费方统计。
- 权限码：billing:view/create/update/approve/run。

### C.8 StatsView（统计监管，FR-801~805）
- Dashboard：调用 `GET /api/v1/stats/dashboard`，用 ECharts 展示调用量、成功率、传输量、缓存命中率、服务数、成本。
- 监管报表：选类型/时间，生成并导出。
- 合规审计：调用 `/api/v1/stats/audit`，列表展示审计事件，支持按 actor/eventType/时间筛选。
- 权限码：stats:view。

### C.9 SystemView（系统管理，支撑 NFR-S01）
- Tab1 用户：列表 + 新建/编辑。
- Tab2 角色：列表 + 新建 + 配置权限。
- Tab3 权限：列出全部权限码。
- 权限码：system:view/create/update。

### C.10 MonitorView（监控大屏，FR-805）
- 调用 `/api/v1/stats/dashboard` 与各服务 `/actuator/metrics`（经 Gateway），用 ECharts 展示运行/服务/合规/成本核心指标，定时刷新（30s）。
- 权限码：stats:view。

### C.11 阶段 C 完成判定
- 10 个页面全部从 A 升级到 C：列表来自真实 API、可新增/编辑/状态流转/查看详情。
- 每个页面至少 1 个 Vitest 测试（mock API，验证列表渲染 + 至少一个操作触发正确 API 调用）。
- `npm run test:unit` 全绿。

---

## 8. 阶段 D：测试补齐与回归

### D.1 后端测试
- 每个新 Controller：MockMvc 测试覆盖正常路径 + 401（无 token）+ 403（权限不足）+ 至少 1 个异常路径（参数非法/状态非法转移）。
- partner/ingest 已有测试保留；新增端点补测试。
- `mvn test` 全模块 BUILD SUCCESS。

### D.2 前端测试
- 每个页面 Vitest：mount 后验证列表调用正确 API、表单提交调用正确 API、权限不足按钮隐藏。
- `npm run test:unit` 全绿。

### D.3 端到端验证（手动，记录在完成报告）
- 从 `http://localhost:5173/` 登录 admin/admin123。
- 依次走通：新建合作方 → 配置接口 → 新建接入任务 → 测试接入 → 发布服务 → 注册消费方 → 配额 → 调用服务 → 查看调用日志 → 生成账单 → 查看 Dashboard → 配置质量规则 → 触发校验。
- 每步截图或 curl 证据记入完成报告。

---

## 9. 完成判定（M7 验收标准）

- 后端：claude-plan §4.5.2 列出的全部管理控制台端点均有 Controller 实现，受 JWT + 权限码保护。
- 前端：10 个骨架页面全部功能化，数据来自真实 API，无写死数据。
- 鉴权：`@RequirePermission` 实际生效，前端菜单/按钮按权限码控制。
- 测试：`mvn test` + `npm run test:unit` 全绿；新 Controller/新页面各有测试。
- 端到端：第 D.3 节主链路可走通。
- 未越界：未修改 docs/tasks/reviews/delivery/perf/security/k8s-prod。

---

## 10. 完成后必须输出（阶段完成报告 - M7）

### 1. 修改/新增文件清单
分阶段 A/B/C/D 列出，标注新增 N / 修改 M。

### 2. 关键实现说明
- 新增 Controller 清单与对应领域 Service。
- 鉴权过滤器与权限 AOP 实现要点。
- 前端 API client 分层与共享组件。
- 各页面功能化要点。

### 3. 测试命令
`mvn test`、`npm run test:unit`、端到端验证 curl 序列。

### 4. 测试结果
- 后端各模块测试数 / 通过数。
- 前端测试数 / 通过数。
- 端到端主链路每步证据。

### 5. 偏离计划说明
与 claude-plan §4.5.2 不一致之处及原因（如有）。

### 6. 潜在风险与遗留问题
- 内存仓储 vs 落表的取舍。
- 未实现项（MFA/SSO/报送/财务对接）状态。
- 上线前待验证项。

---

## 11. 依赖与执行顺序

```
阶段 A（后端 Controller + 鉴权）
    ↓  Claude 审查通过
阶段 B（前端基础设施）
    ↓  Claude 审查通过
阶段 C（前端页面功能化，可按模块并行子任务）
    ↓  Claude 审查通过
阶段 D（测试 + 端到端回归）
    ↓  Claude 最终验收
```

每阶段结束输出"阶段完成报告"，Claude Code 审查通过后方可进入下一阶段。同一阶段返工不超过 3 次。
