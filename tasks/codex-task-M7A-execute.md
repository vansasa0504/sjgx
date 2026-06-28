# Codex 桌面端 M7 阶段 A 执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M6 已通过审查；Gateway 静态路由与前端登录代理已修复；docker-compose（MySQL8.0/Redis/Nacos/RabbitMQ/Kafka）可启动。
> 本阶段为 M7 第一阶段：后端 Controller 补齐 + 鉴权接通。后续 B/C/D 阶段需本阶段审查通过后才能开始。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M7-api-and-frontend.md   ← M7 总任务来源（权威）
7. reviews/claude-review.md                   ← M6 审查历史

当前执行阶段：M7-A（后端 Controller 补齐 + 鉴权接通）
请只执行本提示词中「M7-A 阶段任务」段落列出的任务。

---

## 项目背景（一句话）

金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.2.5 + Spring Cloud Alibaba 2023.0.1.0 + MyBatis-Plus 3.5.6 + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。M1~M6 已完成全部领域逻辑、集成测试、性能调优、安全加固、国产化适配、稳定性方案与交付文档。

### 既有现状（必须基于此）

后端领域 Service 已全部实现，但 Controller 层严重缺失：

- **已有 Controller（仅 3 个，10 端点）**：
  - `platform-auth/AuthController`：`/auth/login`、`/auth/refresh`、`/auth/logout`
  - `platform-partner/PartnerController`：`POST /api/v1/partners`、`POST /api/v1/partners/{id}/events`、`POST /api/v1/partners/{id}/interfaces`、`GET /api/v1/partners/{id}`
  - `platform-pipeline/ingest/IngestController`：`POST /api/v1/ingest/tasks`、`POST /api/v1/ingest/tasks/{id}/test`、`GET /api/v1/ingest/tasks/records`

- **完全缺失 Controller 的模块**（但领域 Service 已存在）：
  - services → `DataServiceManager` 已存在
  - catalog → `CatalogService` 已存在
  - consumer → `ConsumerService` 已存在（在 platform-partner 模块）
  - quality → `QualityCheckExecutor`/`QualityIssueService`/`QualityScoringService` 已存在
  - billing → `BillService`/`BillGenerator`/`BillingRuleEngine`/`StatsAggregator`/`DashboardService`/`ReportGenerator`/`AuditTraceService` 已存在
  - users/roles/permissions → `UserAccount` 内存实现 + V001 已建 `t_user`/`t_role`/`t_permission` 表

- **鉴权现状**：`@RequirePermission` 注解已定义于 platform-auth，但全仓库零使用。Gateway 仅做 JWT 透传（X-User-Id/X-User-Roles header），各业务服务**无任何 JWT 校验或权限校验**。

- **Gateway 路由**：`platform-gateway/src/main/resources/application.yml` 已配置静态路由指向 localhost:8081~8085，覆盖 `/auth/**`、`/api/v1/partners/**`、`/api/v1/ingest/**`、`/api/v1/services/**`、`/api/v1/catalog/**`、`/api/v1/quality/**`、`/api/v1/billing/**`、`/api/v1/stats/**`。Controller 一旦存在即可通过 8080 访问。

- **统一响应**：所有返回值用 `com.platform.common.model.Result<T>` 包装。
- **统一异常**：`BusinessException` + `GlobalExceptionHandler` 已存在。
- **数据库**：V001~V009 已在 MySQL 8.0 验证通过；`t_user`/`t_role`/`t_permission` 在 V001 建表但当前 AuthService 用内存 admin/admin123。

### 目录约束

- 鉴权公共代码放 `platform-common`（供所有模块复用）。
- 用户/角色/权限 Controller 放 `platform-auth`。
- consumer Controller 放 `platform-partner`（与 ConsumerService 同模块）。
- services/catalog Controller 放 `platform-pipeline`。
- 不修改：`.env`、证书、`docs/`、`tasks/`、`reviews/`、`delivery/`、`perf/`、`security/`、`k8s/prod/`、`CLAUDE.md`、`AGENTS.md`。

---

## 执行规则（全局）

1. 只实现 M7-A 阶段任务。不实现前端（B/C 阶段）、不重写已有领域 Service 内部逻辑。
2. 不重新解释需求，`tasks/claude-plan.md` §4.5.2 是接口权威来源，`tasks/codex-task-M7-api-and-frontend.md` §5 是本阶段端点表权威来源。
3. 最小改动：Controller 只做参数装配 + 调用已有 Service + `Result<T>` 包装。如 Service 缺少 list/detail 等查询方法，按需补充（只加查询，不改既有逻辑）。
4. 不修改密钥/生产配置；密钥一律 `${ENV_VAR}`。不引入大型新依赖。
5. 国产化兼容：不手写 `LIMIT`/`ON DUPLICATE KEY UPDATE`，分页用 MyBatis-Plus `Page`。
6. 必须补充测试：每个新 Controller 至少 1 个 MockMvc 测试（正常 + 401 无 token + 403 权限不足 + 1 个异常路径）。
7. 必须运行 `mvn test` 全绿才可声明完成。
8. 可回滚：如新增 V010 迁移须同步 U010 回滚；优先复用既有表，不建新表。
9. 完成后输出「阶段完成报告 - M7-A」。

---

## M7-A 阶段任务

### 阶段目标

补齐 claude-plan §4.5.2 全部管理控制台端点的 Controller 实现，并接通 JWT 校验 + `@RequirePermission` 权限校验，使所有端点可通过 Gateway 8080 访问、受鉴权保护。

### 前置条件

M6 审查通过；docker-compose 依赖可启动（MySQL8.0/Redis/Nacos）。

---

### 任务 A.0：鉴权基础设施（最先做，所有 Controller 依赖）

#### A.0.1 JWT 校验过滤器

在 `platform-common/src/main/java/com/platform/common/security/` 新增 `JwtAuthFilter`（实现 `jakarta.servlet.Filter`）或 `OncePerRequestFilter`：

- 放行路径：`/auth/login`、`/auth/refresh`、`/actuator/**`、`/api/v1/services/*/invoke`（服务调用走 API Key+签名，不走 JWT）。
- 其余请求：从 `Authorization: Bearer <token>` 提取 token，用 `JwtUtil.parse(token)` 校验。
- 校验失败：返回 401 `Result.fail("AUTH-401", "unauthorized")`，JSON 响应，不继续链。
- 校验成功：将 `AuthPrincipal` 放入 `HttpServletRequest` attribute（key 如 `authPrincipal`），并设置 `SecurityContextHolder`。
- 通过 `ObjectProvider<JwtUtil>` 注入，避免硬依赖（各模块已用 `JwtUtil`）。

#### A.0.2 `@RequirePermission` AOP 切面

在 `platform-common/src/main/java/com/platform/common/security/` 新增 `RequirePermissionAspect`：

- 拦截 `@RequirePermission` 注解的方法。
- 从 request attribute 或 `SecurityContextHolder` 取当前 `AuthPrincipal`。
- 校验 `principal.permissions()` 是否包含注解声明的权限码。
- 不包含：返回 403 `Result.fail("AUTH-403", "forbidden")`。
- 注解支持 `value`（权限码，支持数组，满足其一即可）。

#### A.0.3 Security 配置

- `platform-auth`：Spring Security 配置放行 `/auth/login`、`/actuator/**`，其余 `authenticated()`；注册 `JwtAuthFilter`。
- 各业务模块（partner/pipeline/quality/billing）：若有 Spring Security 自动配置，配置放行 `actuator`，其余由 `JwtAuthFilter` 校验；若未引入 spring-security，仅注册 `JwtAuthFilter` + `RequirePermissionAspect`。
- 确保各模块 `@SpringBootApplication(scanBasePackages={"com.platform.<module>","com.platform.common"})` 能扫描到 common 的 filter/aspect。

#### A.0.4 权限下发端点

在 `platform-auth/AuthController` 新增：

```
GET /auth/permissions
  入参：Authorization header
  返回：Result<List<String>>  （当前用户权限码列表）
```

从 `AuthPrincipal.permissions()` 返回。admin 用户默认拥有全部权限码（在 AuthService 初始化 admin 时赋予）。

权限码全集（供 admin 默认与前端菜单用）：
```
partner:view,partner:create,partner:update,partner:approve,
consumer:view,consumer:create,consumer:update,consumer:approve,
ingest:view,ingest:create,ingest:update,ingest:approve,
service:view,service:create,service:update,service:approve,
catalog:view,catalog:apply,catalog:approve,
quality:view,quality:create,quality:update,quality:run,
billing:view,billing:create,billing:update,billing:approve,billing:run,
stats:view,
system:view,system:create,system:update
```

#### A.0.5 测试

- `JwtAuthFilter` 单测：无 token 返回 401、无效 token 返回 401、有效 token 放行并设置 principal。
- `RequirePermissionAspect` 单测：有权限通过、无权限返回 403。

---

### 任务 A.1：platform-partner（合作方 FR-101~104 + 消费方 FR-501~505）

#### A.1.1 补齐 PartnerController（基路径 `/api/v1/partners`）

对齐 claude-plan §4.5.2 与 M7 总任务 §5.A.1。保留已有 4 个端点签名兼容，补齐其余端点：

| 方法 | 路径 | 入参 DTO | 返回 | 权限码 | Service 调用 |
|---|---|---|---|---|---|
| POST | `/` | `CreatePartnerRequest{name,dataType,industry,complianceLevel}` | `Result<Partner>` | partner:create | PartnerService.create |
| GET | `/` | query: page,size,keyword,dataType,status | `Result<Page<Partner>>` | partner:view | PartnerService.list(新增) |
| GET | `/{id}` | id | `Result<Partner>` | partner:view | PartnerService.detail(已有) |
| PUT | `/{id}` | `UpdatePartnerRequest` | `Result<Partner>` | partner:update | PartnerService.update(新增) |
| POST | `/{id}/submit` | id | `Result<Partner>` | partner:approve | PartnerService.apply(SUBMIT) |
| POST | `/{id}/approve` | id | `Result<Partner>` | partner:approve | PartnerService.apply(APPROVE) |
| POST | `/{id}/reject` | `RejectRequest{reason}` | `Result<Partner>` | partner:approve | PartnerService.apply(REJECT) |
| PUT | `/{id}/rating` | `RatingRequest{score}` | `Result<Partner>` | partner:update | PartnerService.rating(新增) |
| POST | `/{id}/terminate` | id | `Result<Partner>` | partner:approve | PartnerService.apply(TERMINATE) |
| POST | `/{id}/interfaces` | `InterfaceRequest{protocol,endpoint,authType,credential,rateLimit}` | `Result<PartnerInterfaceConfig>` | partner:update | PartnerService.configureInterface(已有) |
| GET | `/{id}/interfaces` | id | `Result<List<PartnerInterfaceConfig>>` | partner:view | PartnerService.listInterfaces(新增) |
| GET | `/{id}/events` | id | `Result<List<PartnerEvent>>` | partner:view | PartnerService.listEvents(新增) |

要求：
- `credential` 入参明文，Service 层用 `Sm4Util` 加密后存储；返回时脱敏（如 `****`）。
- `list` 用 MyBatis-Plus `Page`，支持 keyword 模糊匹配 name、dataType/status 精确过滤。
- 状态流转非法转移由 Service/状态机抛 `BusinessException`，Controller 不吞异常（由 GlobalExceptionHandler 处理）。
- 每个写端点加 `@RequirePermission("...")`。

#### A.1.2 新增 ConsumerController（基路径 `/api/v1/consumers`，放 platform-partner）

包装 `ConsumerService`：

| 方法 | 路径 | 入参 | 返回 | 权限码 | Service |
|---|---|---|---|---|---|
| POST | `/` | `CreateConsumerRequest{name,bizLine,systemType,complianceLevel}` | `Result<Consumer>` | consumer:create | ConsumerService.register |
| GET | `/` | query: page,size,keyword,bizLine,status | `Result<Page<Consumer>>` | consumer:view | ConsumerService.list(新增) |
| GET | `/{id}` | id | `Result<Consumer>` | consumer:view | ConsumerService.detail(新增) |
| PUT | `/{id}/quota` | `QuotaRequest{freqLimit,volumeLimit,scope}` | `Result<Consumer>` | consumer:update | ConsumerService.configureQuota |
| POST | `/{id}/events` | `ConsumerEventRequest{event}` | `Result<Consumer>` | consumer:approve | ConsumerService.apply |
| GET | `/{id}/audit` | query: page,size | `Result<Page<AuditEvent>>` | consumer:view | AuditLogRepository.findByActor(consumer) |
| GET | `/{id}/logs` | query: page,size | `Result<Page<ServiceInvokeLog>>` | consumer:view | 调用日志仓储按 consumerCode 查询 |

#### A.1.3 测试

- `PartnerControllerTest`（MockMvc）：create 正常、list 分页、detail、submit/approve/reject 状态流转、无 token 401、权限不足 403、非法状态转移 400。
- `ConsumerControllerTest`：register、list、configureQuota、超额拦截（配额耗尽返回错误）、401/403。

---

### 任务 A.2：platform-pipeline.ingest（接入 FR-201~205）

补齐 `IngestController`（基路径 `/api/v1/ingest/tasks`）：

| 方法 | 路径 | 入参 | 返回 | 权限码 | Service |
|---|---|---|---|---|---|
| POST | `/` | `CreateIngestTaskRequest{partnerId,interfaceId,protocol,format,endpoint,syncMode,cron,fieldMapping,qualityRules}` | `Result<IngestTask>` | ingest:create | IngestService.createTask |
| GET | `/` | query: page,size,partnerId,status | `Result<Page<IngestTask>>` | ingest:view | IngestService.list(新增) |
| GET | `/{id}` | id | `Result<IngestTask>` | ingest:view | IngestService.detail(新增) |
| PUT | `/{id}/mapping` | `MappingRequest{fieldMapping}` | `Result<IngestTask>` | ingest:update | IngestService.updateMapping(新增) |
| PUT | `/{id}/rules` | `RulesRequest{transformRules,qualityRules}` | `Result<IngestTask>` | ingest:update | IngestService.updateRules(新增) |
| POST | `/{id}/test` | id | `Result<List<RawDataRecord>>` | ingest:update | IngestService.testAndIngest |
| POST | `/{id}/submit` | id | `Result<IngestTask>` | ingest:approve | IngestService.apply(SUBMIT) |
| POST | `/{id}/approve` | id | `Result<IngestTask>` | ingest:approve | IngestService.apply(APPROVE) |
| POST | `/{id}/offline` | id | `Result<IngestTask>` | ingest:approve | IngestService.apply(OFFLINE) |
| GET | `/records` | query: page,size,taskId,batchNo | `Result<Page<IngestRecord>>` | ingest:view | IngestService.records(扩展分页) |

测试：`IngestControllerTest`，覆盖 create、test、list、状态流转、401/403、协议不通异常。

---

### 任务 A.3：platform-pipeline.service（数据服务 FR-301~305）

新增 `DataServiceController`（基路径 `/api/v1/services`，放 platform-pipeline），包装 `DataServiceManager`：

| 方法 | 路径 | 入参 | 返回 | 权限码 | Service |
|---|---|---|---|---|---|
| POST | `/` | `RegisterServiceRequest{serviceCode,name,type,endpointPath,rateLimit,timeoutMs,retryCount,circuitBreaker}` | `Result<DataService>` | service:create | register |
| GET | `/` | query: page,size,keyword,status | `Result<Page<DataService>>` | service:view | list(新增) |
| GET | `/{serviceCode}` | serviceCode | `Result<DataService>` | service:view | detail(新增) |
| PUT | `/{serviceCode}` | `UpdateServiceRequest` | `Result<DataService>` | service:update | update(新增) |
| POST | `/{serviceCode}/test` | serviceCode | `Result<TestResult>` | service:update | apply(TEST) |
| POST | `/{serviceCode}/publish` | serviceCode | `Result<DataService>` | service:approve | apply(PUBLISH) |
| POST | `/{serviceCode}/offline` | serviceCode | `Result<DataService>` | service:approve | apply(OFFLINE) |
| GET | `/{serviceCode}/logs` | query: page,size,consumerId,status | `Result<Page<ServiceInvokeLog>>` | service:view | AsyncInvokeLogWriter.logs 分页 |
| POST | `/{serviceCode}/invoke` | `InvokeRequest{consumerCode,apiKey,timestamp,nonce,params,signature}` | `Result<String>` | （公开，API Key+签名鉴权） | invoke |

要求：
- `/invoke` **不走 JWT**，由 `JwtAuthFilter` 放行；走 API Key + HMAC-SHA256 签名 + nonce 防重放（DataServiceManager.invoke 已实现）。
- 其余端点走 JWT + 权限码。
- 测试：`DataServiceControllerTest`，覆盖 register/publish/invoke 正常签名通过、错误签名 401、重放拒绝、401/403。

---

### 任务 A.4：platform-pipeline.catalog（数据目录 FR-401~405）

新增 `CatalogController`（基路径 `/api/v1/catalog`，放 platform-pipeline），包装 `CatalogService`：

| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/` | query: page,size,topic,partnerId,dataType,scene | `Result<Page<DataCatalog>>` | catalog:view |
| GET | `/{id}/meta` | id | `Result<DataCatalogMeta>` | catalog:view |
| GET | `/{id}/preview` | id | `Result<PreviewResult{sample,stats,qualityReport}>` | catalog:view |
| GET | `/search` | query: keyword,tags,page,size | `Result<Page<DataCatalog>>` | catalog:view |
| POST | `/{id}/apply` | `ApplyRequest{reason,scope}` | `Result<Application>` | catalog:apply |
| POST | `/applications/{id}/approve` | id | `Result<Application>` | catalog:approve |

测试：`CatalogControllerTest`，覆盖 list 多维筛选、search、apply、approve、401/403。

---

### 任务 A.5：platform-quality（数据质量 FR-901~906）

新增 `QualityController`（基路径 `/api/v1/quality`），包装 `QualityCheckExecutor`/`QualityIssueService`/`QualityScoringService`：

| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| POST | `/rules` | `CreateQualityRuleRequest{code,name,dimension,targetObject,expression,severity}` | `Result<QualityRuleConfig>` | quality:create |
| GET | `/rules` | query: page,size,dimension,enabled | `Result<Page<QualityRuleConfig>>` | quality:view |
| PUT | `/rules/{id}` | `UpdateQualityRuleRequest` | `Result<QualityRuleConfig>` | quality:update |
| GET | `/checks` | query: page,size,ruleId,batchNo | `Result<Page<QualityCheckResult>>` | quality:view |
| POST | `/checks` | `TriggerCheckRequest{ruleIds,target}` | `Result<List<QualityCheckResult>>` | quality:run |
| GET | `/issues` | query: page,size,status,severity | `Result<Page<QualityIssue>>` | quality:view |
| POST | `/issues/{id}/assign` | `AssignRequest{assignee}` | `Result<QualityIssue>` | quality:update |
| POST | `/issues/{id}/resolve` | `ResolveRequest{resolution}` | `Result<QualityIssue>` | quality:update |
| GET | `/reports` | query: partnerId,dataType,from,to | `Result<QualityReport>` | quality:view |
| GET | `/scores` | query: partnerId,from,to | `Result<QualityScore>` | quality:view |

测试：`QualityControllerTest`，覆盖 create rule、trigger check、issue 状态流转、report/score、401/403。

---

### 任务 A.6：platform-billing（计费 FR-701~705 + 统计监管 FR-801~805）

#### A.6.1 BillingController（基路径 `/api/v1/billing`）

包装 `BillingRuleEngine`/`BillService`/`BillGenerator`：

| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/rules` | query: page,size,targetType,model | `Result<Page<BillingRule>>` | billing:view |
| POST | `/rules` | `CreateBillingRuleRequest` | `Result<BillingRule>` | billing:create |
| PUT | `/rules/{id}` | `UpdateBillingRuleRequest` | `Result<BillingRule>` | billing:update |
| GET | `/bills` | query: page,size,billType,period,status | `Result<Page<Bill>>` | billing:view |
| POST | `/bills/generate` | `GenerateBillRequest{billType,period,start,end}` | `Result<Bill>` | billing:run |
| POST | `/bills/{id}/confirm` | id | `Result<Bill>` | billing:approve |
| POST | `/bills/{id}/dispute` | `DisputeRequest{reason}` | `Result<Bill>` | billing:approve |
| GET | `/stats` | query: from,to,partnerId,consumerId | `Result<BillingStats>` | billing:view |

#### A.6.2 StatsController（基路径 `/api/v1/stats`）

包装 `StatsAggregator`/`DashboardService`/`ReportGenerator`/`AuditTraceService`：

| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/dashboard` | 无 | `Result<DashboardSummary>` | stats:view |
| GET | `/reports` | query: type,from,to | `Result<GeneratedReport>` | stats:view |
| GET | `/audit` | query: page,size,actorType,eventType,from,to | `Result<Page<AuditEvent>>` | stats:view |

测试：`BillingControllerTest`、`StatsControllerTest`，覆盖 generate bill、状态流转、dashboard、audit 查询、401/403。

---

### 任务 A.7：platform-auth 用户/角色/权限（支撑 SystemView）

新增 `UserController`（`/users`）、`RoleController`（`/roles`）、`PermissionController`（`/permissions`）：

| 方法 | 路径 | 入参 | 返回 | 权限码 |
|---|---|---|---|---|
| GET | `/users` | query: page,size,keyword | `Result<Page<UserAccount>>` | system:view |
| POST | `/users` | `CreateUserRequest{username,password,permissions}` | `Result<UserAccount>` | system:create |
| PUT | `/users/{id}` | `UpdateUserRequest{permissions}` | `Result<UserAccount>` | system:update |
| GET | `/roles` | 无 | `Result<List<Role>>` | system:view |
| POST | `/roles` | `CreateRoleRequest{name,permissions}` | `Result<Role>` | system:create |
| PUT | `/roles/{id}/permissions` | `UpdateRolePermissionsRequest{permissions}` | `Result<Role>` | system:update |
| GET | `/permissions` | 无 | `Result<List<String>>` | system:view |

要求：
- 当前 AuthService 用内存 `Map<String,UserAccount>`（admin/admin123）。本阶段**优先落 `t_user`/`t_role`/`t_permission` 表**（V001 已建），用 MyBatis-Plus 实现仓储；admin 作为种子数据。
- 如落表需新增 V010 迁移（如需补字段或种子数据），同步 U010 回滚。
- 密码用 `BCryptPasswordEncoder` 存储（AuthService 已用）。
- 返回 UserAccount 时 passwordHash 不输出（脱敏）。

测试：`UserControllerTest`/`RoleControllerTest`，覆盖 CRUD、权限分配、401/403。

---

### 任务 A.8：Gateway 路由核对

核对 `platform-gateway/src/main/resources/application.yml`，确保以下路径都已路由（M6 已配置大部分，补缺）：
- `/users/**`、`/roles/**`、`/permissions/**` → auth (8081)
- `/api/v1/consumers/**` → partner (8082)
- `/api/v1/quality/**` → quality (8084)
- `/api/v1/billing/**`、`/api/v1/stats/**` → billing (8085)

如需新增路由，用 `${PLATFORM_*_URI:http://localhost:80xx}` 格式。

---

### 测试要求（M7-A）

- 每个新 Controller 至少 1 个 MockMvc 测试类，覆盖：
  - 正常路径（200 + 返回体校验）
  - 401（无 token）
  - 403（权限不足，用缺少权限码的用户）
  - 至少 1 个异常路径（参数非法 / 状态非法转移 / 资源不存在）
- 鉴权基础设施（A.0）有独立单测。
- `mvn test` 全模块 BUILD SUCCESS。
- 启动验证：docker-compose 起 MySQL/Redis/Nacos，启动全部服务，通过 Gateway 8080 用 admin 登录拿 token，curl 验证至少 5 个新端点（partner list、ingest list、service list、quality rules、stats dashboard）。

### 完成判定（M7-A 验收标准）

- claude-plan §4.5.2 全部管理控制台端点有 Controller 实现。
- 所有非 `/auth/login`、`/actuator/**`、`/invoke` 端点受 JWT + 权限码保护。
- `@RequirePermission` 实际生效（有 AOP + 测试）。
- `mvn test` 全绿。
- Gateway 8080 可访问新端点，curl 证据记入完成报告。
- 未修改 docs/tasks/reviews/delivery/perf/security/k8s-prod。

---

## 完成后必须输出（阶段完成报告 - M7-A）

完成后请严格按以下格式输出（Claude Code 据此审查）：

### 1. 修改/新增文件清单
分模块列出，标注新增 N / 修改 M。

### 2. 关键实现说明
- 鉴权过滤器与权限 AOP 实现要点。
- 各新增 Controller 清单与对应领域 Service。
- 用户/角色落表情况（V010 是否新增）。
- Service 层新增的查询方法清单。

### 3. 测试命令
`mvn test`、启动验证 curl 序列。

### 4. 测试结果
- 各模块测试数 / 通过数。
- 启动验证：admin 登录 token + 5 个新端点 curl 证据。

### 5. 偏离计划说明
与 claude-plan §4.5.2 或 M7 总任务不一致之处及原因。

### 6. 潜在风险与遗留问题
- 内存仓储 vs 落表的取舍。
- 鉴权过滤器与现有 Spring Security 自动配置的兼容性。
- 未实现项状态。

===== 复制到此结束 =====
