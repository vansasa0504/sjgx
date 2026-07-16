# Claude Code 审查结果 — M7-A 阶段

> 审查阶段：M7-A — 后端 Controller 补齐 + 鉴权接通（由 Claude Code 直接实现，Codex 不可用）
> 审查日期：2026-06-26
> 审查范围：工作区未提交改动（M7-A 代码 + 既有 M5/M6 遗留 pom/yml 改动）
> 任务单：`tasks/codex-task-M7-api-and-frontend.md` §5（A 阶段）

## 1. 审查对象

- **鉴权基础设施**（platform-common）：`RequirePermission`、`JwtAuthFilter`、`RequirePermissionAspect`、`CommonSecurityConfiguration`、`PermissionCodes`、`JacksonConfiguration`
- **9 个新 Controller**：ConsumerController、DataServiceController、CatalogController、QualityController、BillingController、StatsController、UserController、RoleController、PermissionController
- **2 个扩展 Controller**：PartnerController、IngestController
- **Service 扩展**：AuthService（用户/角色内存 CRUD）、PartnerService（list/update/listInterfaces/listEvents）、ConsumerService（list/find）、IngestService（list/detail/apply/updateMapping/updateRules/records 分页）、DataServiceManager（list/detail/update/logs 分页）、CatalogService（search/findById）
- **领域对象扩展**：Partner（+dataType/industry/complianceLevel）、IngestTask（+syncMode/cron/fieldMapping/qualityRules）、DataServiceDefinition（name/routeKey 改非 final）、QualityRuleConfig（+id）
- **仓储扩展**：BillingRuleRepository（+save/findAll）、InMemoryBillingRuleRepository（+无参构造/save/findAll）、新增 QualityRuleRepository + InMemoryQualityRuleRepository
- **Application @Bean 补齐**：PartnerApplication、PipelineApplication、QualityApplication、BillingApplication
- **配置**：父 POM 加 `maven.compiler.parameters=true`（修复 Spring Boot 3 `@RequestParam` 参数名）、各模块 pom 补 actuator/jdbc/mysql/spring-boot-maven-plugin（M5/M6 遗留）、Gateway 路由补 `/api/v1/consumers/**`
- **删除**：`platform-auth/RequirePermission.java`（移到 common）

## 2. Git 状态

工作区改动：48 文件，+1251 / -333。其中 M7-A 新增 9 个 Controller + 4 个 common security 类 + 9 个测试，修改 Service/Application/领域对象；其余 pom/yml/sql 为 M5/M6 遗留改动一并存在于工作区。

## 3. 代码差异摘要

| 类别 | 变更 | 审查意见 |
|---|---|---|
| 鉴权公共层 | 4 个新类放 platform-common | 设计合理，全模块复用 |
| Controller | 9 新 + 2 扩展，统一 `Result<T>` + `@RequirePermission` | 风格一致 |
| Service 扩展 | 补 list/detail/update 查询方法 | 未改既有业务逻辑，符合最小改动 |
| 领域对象 | Partner/IngestTask 加字段 | 保留旧构造器，向后兼容 |
| 配置 | `maven.compiler.parameters=true` + Jackson 字段可见性 | 必要修复 |
| 测试 | filter/aspect 单测 + 9 Controller 直接调用单测 | 覆盖正常路径，401/403 靠启动验证 |

## 4. 需求满足情况

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| FR-101~104 | 合作方管理 | 是 | 全生命周期端点齐全（create/list/detail/update/submit/approve/admit/reject/rating/terminate/interfaces/events） |
| FR-201~205 | 数据接入 | 是 | create/list/detail/mapping/rules/test/submit/approve/offline/records 齐全 |
| FR-301~305 | 数据服务 | 是 | register/list/detail/define/test/publish/offline/logs/invoke 齐全 |
| FR-401~405 | 数据目录 | 部分 | list/search/meta/apply/approve 齐全；preview 为桩（返回空 sample） |
| FR-501~505 | 消费方管理 | 部分 | register/list/detail/quota/events/audit 齐全；logs 返回空 |
| FR-701~705 | 计费管理 | 是 | rules CRUD/bills generate+confirm+dispute 齐全 |
| FR-801~805 | 统计监管 | 部分 | dashboard/reports/audit 齐全；reports 为桩（写文件，无导出） |
| FR-901~906 | 数据质量 | 是 | rules CRUD/checks trigger/issues assign+resolve/reports/scores 齐全 |
| NFR-S01 | 认证权限 | 部分 | JWT + 权限码生效；用户/角色内存实现（未落表）；MFA/SSO 待外部 |
| NFR-S03 | 应用安全 | 部分 | SQL 白名单/XSS/限流保留；ZAP 待上线 |

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| claude-plan §4.5.2 接口对齐 | 符合 | 端点方法/路径基本对齐，部分端点为最小可用桩 |
| 最小可行结果 | 符合 | 九大模块 API 可调用，鉴权生效 |
| 最小改动 | 符合 | Controller 只包装 Service，未重写领域逻辑 |
| 避免过度设计 | 符合 | 未引入新框架，内存仓储与既有模式一致 |
| 可回滚 | 符合 | 无新 SQL 迁移（用户内存实现，未落表） |

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 是否修改敏感文件 | 通过 | 无 .env/证书/k8s-prod 改动 |
| 是否修改 docs/tasks/reviews | 注意 | reviews/claude-review.md 本次覆盖（属 Claude 职责）；未改 docs/tasks |
| 是否引入大型依赖 | 通过 | 仅 common 加 spring-boot-starter-test(test scope)，无生产大型依赖 |
| 是否无关重构 | 通过 | `@RequirePermission` 从 auth 移到 common 是方向正确化，必要 |
| 是否配置外置 | 通过 | JWT secret 仍 `${ENV_VAR}` |

## 7. 测试检查

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `mvn test`（8 模块） | 是 | 通过 | BUILD SUCCESS，含新增 filter/aspect/9 Controller 测试 |
| 启动验证 | 是 | 通过 | 6 服务 + gateway 全 UP |
| 写操作（partner/service/quality/billing/consumer 创建） | 是 | 通过 | 全 200 |
| 列表端点 | 是 | 通过 | 8 个列表端点全 200 |
| 401（无 token） | 是 | 通过 | 受保护端点返 401 |
| 403（低权限 viewer） | 是 | 通过 | viewer 访问 /api/v1/partners 返 403，/stats/dashboard 返 200 |
| MockMvc 测试 | 否 | 缺失 | 未给每模块加 spring-boot-starter-test MockMvc，401/403 靠启动 curl 验证 |

## 8. 安全与风险检查

### 8.1 发现的问题

| 编号 | 问题 | 严重度 | 说明 |
|---|---|---|---|
| **S-01** | `JwtAuthFilter` 白名单含 `/actuator/**` 全放行 | **中** | actuator 暴露 health/metrics/info 无需鉴权。当前各模块 `management.endpoints.web.exposure.include: health,metrics,info`，metrics 可能泄露 JVM/HTTP 指标。生产应只放行 `/actuator/health`，其余需鉴权或禁用。 |
| **S-02** | `/api/v1/services/*/invoke` 全放行，依赖 API Key+签名 | **中** | 设计合理（消费方调用走 API Key），但 `DataServiceManager.invoke` 的 `secret` 由调用方在 body 明文传入（`request.secret()`），即 apiKey→secret 映射未实现，任何知道 secret 的人可调用。当前为 demo 实现，上线前需补 apiKey→secret 仓储查找。 |
| **S-03** | `GlobalExceptionHandler.handleThrowable` 吞异常不记录日志 | **中** | 500 错误只返 `"internal error"`，无日志输出，排查困难。本次调试期间已证实：生产环境将无法定位 500 根因。应至少 `log.error` 记录堆栈。 |
| **S-04** | 用户/角色内存实现，重启丢失 | **低** | 偏离计划（计划落 t_user 表）。admin 每次重启需重新创建非 admin 用户。金融生产环境必须落表，当前仅开发可用。 |
| **S-05** | `QualityController.score` 用 `history.get(size-1)` 取最新 | **低** | 内存 history 无序保证（ArrayList 顺序追加，当前可用），但语义上"最新"应按 `checkedAt` 排序取最大。多并发触发校验时可能取错。 |
| **S-06** | `BillingController` 无 `stats` 端点 | **低** | M7 总任务 §A.6 列了 `GET /api/v1/billing/stats`，本次未实现。前端 BillingView Tab3 费用统计将无后端支撑。 |
| **S-07** | `CatalogController.preview` 返回空 sample | **低** | 偏离计划，前端预览无数据。记入遗留。 |
| **S-08** | `ConsumerController.logs` 返回空 | **低** | partner 模块无调用日志仓储。记入遗留。 |
| **S-09** | Controller 测试无 401/403 覆盖 | **低** | 仅直接调用单测验证正常路径，权限边界靠启动 curl。生产前应补 MockMvc。 |
| **S-10** | `AuthService` 无 `@Service`，靠 `@Bean` 注册 | **低** | 设计选择，非问题，但 `Role`/`UserAccount` 为 record 无落库，扩展受限。 |

### 8.2 安全正向项

- JWT 校验 + 权限码 AOP 实际生效（启动验证 401/403 通过）
- `@RequirePermission` 注解全 Controller 写端点覆盖
- credential 仍经 Sm4Util 加密存储
- DbAdapter SQL 白名单保留
- 密码 BCrypt 存储
- `.gitignore` 排除敏感文件

## 9. 审查结论

```text
✓ 通过（有条件） — 可进入 M7-B，但 S-01/S-03 建议在 M7-D 前修复
```

**理由**：M7-A 达成阶段目标——九大模块 API 齐全且受鉴权保护，`mvn test` 全绿，启动验证 401/403/200 全部通过。鉴权基础设施设计合理（filter + aspect 分层，common 复用），Controller 风格统一，未重写领域逻辑，符合最小改动。

S-01（actuator 全放行）和 S-03（异常不记录日志）是两个应在 M7-D 测试回归阶段修复的中等安全问题，不阻塞 M7-B/C 前端开发。S-02（invoke secret 明文）、S-04（用户落表）、S-06~S-08（端点桩）记入遗留，上线前补齐。

## 10. 返工任务清单

本次无阻塞性返工。以下为 M7-D 前建议修复项：

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-01 | actuator 全放行 | `JwtAuthFilter` 白名单改为只放行 `/actuator/health`；或各模块 actuator 暴露收紧为仅 health | 中 |
| F-02 | 500 异常无日志 | `GlobalExceptionHandler.handleThrowable` 加 `log.error` 记录堆栈 | 中 |
| F-03 | Controller 缺 401/403 MockMvc 测试 | M7-D 阶段给每模块加 spring-boot-starter-test，补 MockMvc 正常/401/403/异常测试 | 中 |
| F-04 | billing 缺 `/stats` 端点 | 补 `GET /api/v1/billing/stats`，包装费用统计 | 低 |
| F-05 | catalog preview 为桩 | 后续补真实预览（sample+stats+qualityReport） | 低 |
| F-06 | consumer logs 为空 | 后续补调用日志仓储或跨模块查询 | 低 |
| F-07 | 用户/角色落表 | 上线前落 t_user/t_role/t_permission，补 V010+U010 | 低（上线前） |
| F-08 | invoke secret 明文 | 上线前补 apiKey→secret 仓储查找，secret 不由调用方传入 | 低（上线前） |

## 11. 建议提交信息

```text
feat(M7-A): backend controllers, JWT auth and permission AOP

- add common security: JwtAuthFilter, RequirePermissionAspect, PermissionCodes
- add 9 controllers (consumer/service/catalog/quality/billing/stats/user/role/permission)
- extend partner/ingest controllers with full lifecycle endpoints
- wire JWT + @RequirePermission on all write endpoints
- add Jackson field visibility for id()-style domain objects
- enable maven.compiler.parameters for Spring Boot 3 @RequestParam
- tests: filter/aspect unit tests + 9 controller tests; mvn test green
- verified: login, 5 write ops, 8 list endpoints, 401/403 isolation

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：M7-A 通过，可进入 M7-B（前端基础设施）。建议在 M7-D 前修复 F-01/F-02/F-03。

---

# Claude Code 审查结果 — M7-B 阶段

> 审查阶段：M7-B — 前端基础设施（API client / auth store / 路由守卫 / 共享组件 / 布局）
> 审查日期：2026-06-27
> 审查范围：`platform-ui/src/` 下 M7-B 新增及修改文件（5 改 + 多新增）
> 任务单：`tasks/codex-task-M7B-execute.md`（M7-B 阶段任务）
> 前置：M7-A 已通过审查（见上文）

## 1. 审查对象

| 类别 | 文件 | 新增/修改 |
|---|---|---|
| api 核心 | `src/api/client.ts` | 修改（重构为 `createClient` 工厂 + `unwrap`） |
| api 模块 | `src/api/auth.ts` `partner.ts` `consumer.ts` `ingest.ts` `service.ts` `catalog.ts` `quality.ts` `billing.ts` `stats.ts` `system.ts` `types.ts` | 新增 11 |
| stores | `src/stores/auth.ts` | 修改（接真实 API） |
| router | `src/router/index.ts` | 修改（真实权限校验 + 403/404 + ConsoleLayout 嵌套） |
| views | `src/views/ForbiddenView.vue` `NotFoundView.vue` | 新增 2 |
| components | `src/components/PageTable.vue` `FormDialog.vue` `StatusTag.vue` | 新增 3 |
| layouts | `src/layouts/ConsoleLayout.vue` | 新增 1 |
| 入口 | `src/App.vue` | 修改（auth-expired 监听） |
| 测试 | `api/__tests__/{client,modules}.test.ts` `stores/__tests__/auth.test.ts` `router/__tests__/guard.test.ts` `components/__tests__/{PageTable,FormDialog,StatusTag}.test.ts` `layouts/__tests__/ConsoleLayout.test.ts` | 新增 8 |
| 既有测试 | `src/__tests__/ui.spec.ts` | 修改（适配新 import） |

## 2. 测试结果

```text
npm run test:unit  →  9 文件 / 18 用例 全绿（6.56s）
```

覆盖：client 拦截器（token 注入 / 401 事件 / 业务错误 reject）、模块 API（partner/service URL+method）、auth store（login 拉权限 / logout 清空）、路由守卫（无 token 跳 login / 权限不足跳 403 / permittedRoutes 过滤）、3 组件、ConsoleLayout（菜单按权限 / 登出）。

## 3. 后端契约对齐核查（与 M7-A Controller 逐项比对）

| 前端函数 | 后端端点 / 返回 | 对齐 |
|---|---|---|
| `auth.login/refresh/logout/fetchPermissions` | `POST /auth/login` `Result<TokenResponse{token}>`；`POST /auth/refresh`；`POST /auth/logout`；`GET /auth/permissions` `Result<List<String>>` | ✅ 路径、方法、`unwrap` 拆包均正确 |
| `listPartners` → `Page<Partner>` | `GET /api/v1/partners` `Result<Page<Partner>>` | ✅ |
| `listConsumers/listIngestTasks/listServices/listCatalog/listBillingRules/listBills/listQualityRules` → 数组 | 后端均返回 `Result<List<...>>`（非 Page） | ✅ 类型匹配 |
| `listIngestRecords/getConsumerAudit/getConsumerLogs/listServiceLogs` → `Page` | 后端 `Result<Page<...>>` | ✅ |
| `ratePartner(id,score)` `PUT /partners/{id}/rating` `{score}` | 后端 `RatingRequest{score}` | ✅ |
| `invokeService` `POST /services/{code}/invoke` | 后端 `InvokeRequest` | ✅ |
| `system.listUsers/createUser/updateUser/listRoles/...` | `GET /users` `POST /users` `PUT /users/{username}` `GET /roles` … | ⚠️ 路径对，但 dev proxy 不转发（见 B-01） |

## 4. 需求满足情况（对照 M7-B 任务）

| 任务 | 状态 | 说明 |
|---|---|---|
| B.1 API client 重构 | 基本满足 | 分层清晰，`createClient` 工厂复用拦截器，`unwrap` 统一拆 `Result`；`fetchDashboard` 已移至 `stats.ts`。`system.ts` 走裸路径有 proxy 缺陷（B-01） |
| B.2 auth store 重构 | 满足 | login 后自动 fetchPermissions；logout 即使服务端失败也清本地；token 持久化 localStorage；hasPermission/hasAnyPermission 齐全 |
| B.3 路由守卫 | 满足 | beforeEach 四步校验完整；403/404 路由 + catch-all 齐全；业务路由 meta.permission 预置 |
| B.4 共享组件 | 满足 | PageTable/FormDialog/StatusTag Props 设计合理，各有测试 |
| B.5 布局 | 满足 | ConsoleLayout 顶栏+左侧菜单（按权限过滤）+内容区；菜单用 `permittedRoutes` |
| B.6 app 入口与全局错误处理 | **部分** | auth-expired 监听 + 跳登录 ✓；但**全局非 401 错误 `ElMessage.error` 缺失**（B-02） |
| 业务页面保持原样 | 满足 | 10 个业务页面未动，留待 C 阶段 |

## 5. 发现的问题

### B-01【中】system.ts 用 `rootApi`（baseURL `''`）调 `/users` `/roles` `/permissions`，dev 模式不可达

- **现象**：`system.ts` 用 `rootApi`（`createClient('')`）请求 `/users`、`/roles`、`/permissions`。后端 `UserController`/`RoleController`/`PermissionController` 挂在 auth 服务，路径无 `/auth` 前缀。Gateway 8080 **确实**路由了 `Path=/auth/**,/users/**,/roles/**,/permissions/**` → auth(8081)，所以**生产/网关直连可用**。
- **问题**：Vite dev proxy（`vite.config.ts`）只转发 `/auth` 和 `/api`，**未转发 `/users` `/roles` `/permissions`**。dev 模式（`http://localhost:5173/`）下浏览器请求 `http://localhost:5173/users` 命中 SPA fallback 返回 `index.html`，axios JSON 解析失败 → C 阶段 SystemView 全部接口不可用。
- **约束冲突**：M7-B 任务声明"不修改 `vite.config.ts`（proxy 已配好）"，但实际 proxy 并未覆盖这三个路径。Codex 直接用 `rootApi` 未上报此偏离。
- **建议**：二选一（需 Claude 决策）：
  1. 在 `vite.config.ts` 的 proxy 补 `'/users'`、`'/roles'`、`'/permissions'` → `http://localhost:8080`（破例改 vite.config，但属必要修复）；
  2. 后端给 `UserController`/`RoleController`/`PermissionController` 加 `/auth` 前缀（`/auth/users` 等），前端改用 `authApi`——改动跨阶段，影响 M7-A 已验收接口，不推荐。
  - 推荐方案 1，C 阶段 SystemView 开发前必须解决。

### B-02【中】缺全局业务错误提示

- 任务 B.6 明确要求"非 401 错误用 `ElMessage.error` 显示后端错误消息"。实际 `client.ts` 响应拦截器只 `Promise.reject(new Error(...))`，`App.vue` 只处理 `auth-expired`，业务错误（如 500、校验失败、状态非法转移）不弹窗，用户无感知。
- **建议**：在 `client.ts` 响应拦截器的 error 分支（非 401）调 `ElMessage.error(message)`；注意与 `FormDialog` 内的错误显示去重（FormDialog 已自行 catch 显示），可让拦截器统一弹窗，FormDialog 提交失败时吞掉重复提示。

### B-03【低】auth-expired 与 logout 的重入

- `App.vue.handleAuthExpired` → `auth.logout()` → `authApi.logout()`。`/auth/logout` 不在 `JwtAuthFilter` 白名单（仅 `/auth/login` 放行），需 JWT 校验；token 已过期时该请求再 401 → 拦截器再 dispatch `auth-expired` → `handleAuthExpired` 重入。
- 因 `logout` 在 `finally` 清空 `token`，第二次进入时 `if(this.token)` 跳过 `authApi.logout()`，**最多 2 次调用，非死循环**。但不优雅，且产生冗余 401 请求。
- **建议**：`logout` 前置清 token，或 `/auth/logout` 加入 filter 白名单免鉴权。

### B-04【低】FormDialog 校验失败未捕获

- `submitForm` 中 `await formRef.value?.validate()` 若校验失败会抛异常，外层无 try/catch（try 只包 `props.submit`），导致 unhandled rejection，且弹窗内不显示校验失败原因（Element Plus 会标红字段，但控制台报错）。
- **建议**：将 `validate()` 纳入 try/catch，校验失败静默或显示提示。

### B-05【低】`ui.spec.ts` dashboard mock 与真实后端契约不符

- 测试 mock `GET /stats/dashboard` 返回裸对象 `{invokeCount:10}`，而真实后端返回 `Result<DashboardSummary>`（`{success:true,data:{...}}`）。测试在假数据上通过，未验证 `unwrap` 对真实 `Result` 的拆包。`fetchDashboard` 实际能正确拆包（`unwrap` 判 `success` 字段），但测试无回归保护。
- **建议**：mock 改为 `{success:true,data:{invokeCount:10}}`，与后端契约一致。

### B-06【低】ConsoleLayout 窄屏仅堆叠不折叠

- 任务要求"窄屏菜单折叠"。实现仅 `@media (max-width:760px)` 把 aside 改全宽堆叠，无折叠/抽屉交互，窄屏菜单常驻占满首屏。
- **建议**：C 阶段前可接受；若要达标，加 `el-menu` `collapse` 或抽屉模式。

### B-07【低】StatusTag 默认映射缺常见状态

- 默认 map 覆盖 PENDING/ACTIVE/SUSPENDED/TERMINATED/DRAFT/ONLINE/OFFLINE/GENERATED/CONFIRMED/DISPUTED/SETTLED/OPEN/CLOSED，但缺 SUBMITTED/APPROVED/REJECTED/PUBLISHED/TESTING/RUNNING 等状态机常见值。未命中时回退渲染原状态文本（不会崩），但中文体验不一致。
- **建议**：C 阶段按各模块实际状态码补全。

### B-08【低】路由守卫已登录访问 `/login` 硬跳 `/partners`

- `to.path==='/login'` 且已登录 → `/partners`。若用户无 `partner:view` 权限，会再跳 `/403`。
- **建议**：跳首个有权限路由，或跳 `/`（`/`→`/partners` 重定向可改为守卫递归选首个有权限项）。

### B-09【低】`listAudit` 后端要求 eventType 非空

- 后端 `StatsController.audit` 当 `eventType` 为空时返回空列表。前端 `listAudit` 透传 params 无此约束提示。C 阶段 StatsView 审计页须强制选 eventType，否则永远空。

## 6. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 最小可行结果 | 符合 | 基础设施齐备，C 阶段可直接复用 |
| 最小改动 | 符合 | 复用既有 axios/Element Plus，未引入新依赖 |
| 避免过度设计 | 符合 | `createClient` 工厂 + `unwrap` 简洁，未过度抽象 |
| 不动业务页面 | 符合 | 10 页面保持 A 类骨架 |
| 不改 vite.config | 符合（但暴露 B-01） | 未改 vite.config，代价是 system.ts dev 不可达 |
| TypeScript 类型 | 基本符合 | DTO 用 interface；少量 `unknown`（catalog preview / logs）尚可，C 阶段细化 |

## 7. 安全检查

- token 存 localStorage（既有模式，非本次引入）；XSS 风险属既有架构，不在 M7-B 范围。
- 401 自动登出 + 跳登录（带 redirect）✓。
- 无敏感信息写入日志/文档 ✓。
- 未引入新依赖 ✓。

## 8. 审查结论

```text
✓ 通过（有条件） — 可进入 M7-C，但 B-01 必须在 C 阶段 SystemView 开发前修复，B-02 建议同步修复
```

**理由**：M7-B 达成阶段目标——API client 分层、auth store 接真实权限、路由守卫真实校验、3 个共享组件 + 布局组件齐备，18 个测试全绿，后端契约对齐核查通过（返回类型与 M7-A 的 Page/List 一致）。代码风格统一，最小改动，未越界改业务页面。

**不阻塞但需跟进**：B-01（system.ts dev proxy 不可达）是 C 阶段 SystemView 的硬阻塞，必须在开发 SystemView 前解决（推荐补 vite proxy）。B-02（全局错误提示）影响所有 C 阶段页面的错误反馈体验，建议在 C 阶段首个页面开发时一并补上。其余 B-03~B-09 为低优先级，可并入 M7-D 回归。

## 9. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 | 阶段 |
|---|---|---|---|---|
| F-01 | system.ts dev proxy 不可达 | `vite.config.ts` proxy 补 `/users` `/roles` `/permissions` → 8080；或后端加 `/auth` 前缀（不推荐） | 中 | C 前 |
| F-02 | 缺全局业务错误提示 | `client.ts` 响应拦截器非 401 错误调 `ElMessage.error`；与 FormDialog 去重 | 中 | C 首 |
| F-03 | FormDialog 校验失败未捕获 | `submitForm` 包裹 `validate()` 的 try/catch | 低 | C |
| F-04 | ui.spec.ts dashboard mock 契约不符 | mock 改 `{success:true,data:{...}}` | 低 | D |
| F-05 | auth-expired/logout 重入 | `/auth/logout` 加 filter 白名单，或 logout 前置清 token | 低 | D |
| F-06 | 路由已登录跳 `/partners` 硬编码 | 改跳首个有权限路由 | 低 | C |
| F-07 | StatusTag 默认映射不全 | C 阶段按实际状态码补全 | 低 | C |
| F-08 | ConsoleLayout 窄屏不折叠 | 加 collapse/抽屉（可选） | 低 | C/D |

## 10. 建议提交信息

```text
feat(M7-B): frontend infrastructure — api client, auth, router guard, shared components

- refactor api client: createClient factory + unwrap(Result) + 10 module files
- auth store: real login/refresh/logout/fetchPermissions, auto-load perms after login
- router guard: real permission check, 403/404 pages, ConsoleLayout nesting
- shared components: PageTable, FormDialog, StatusTag
- ConsoleLayout: permission-filtered menu + logout
- App.vue: auth-expired listener → logout + redirect
- tests: 18 vitest cases green (client/auth/guard/components/layout)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：M7-B 通过（有条件），可进入 M7-C（前端九大模块页面功能化）。C 阶段 SystemView 开发前必须先修复 F-01（system.ts dev proxy）；C 阶段首个页面开发时同步修复 F-02（全局错误提示）。

---

# Claude Code 审查结果 — M7-C 阶段

> 审查阶段：M7-C — 前端九大模块页面功能化（10 个页面 A→C 升级）
> 审查日期：2026-06-27
> 审查范围：`platform-ui/src/views/*.vue`（10 页）+ `src/components/PageTable.vue`（被改）+ `src/views/__tests__/m7c-pages.test.ts`
> 任务单：`tasks/codex-task-M7C-execute.md`（M7-C 阶段任务）
> 前置：M7-B 已通过审查（有条件）

## 1. 审查对象

| 文件 | 新增/修改 | 说明 |
|---|---|---|
| `views/PartnerView.vue` | 修改 | 列表+详情+新建/编辑/评级/接口/状态流转 |
| `views/IngestView.vue` | 修改 | 列表+详情+创建/映射/测试/状态流转 |
| `views/ServiceView.vue` | 修改 | 列表+详情+注册/编辑/测试/发布/下线 |
| `views/CatalogView.vue` | 修改 | 卡片网格+检索+元信息/预览/申请 |
| `views/ConsumerView.vue` | 修改 | 列表+详情+注册/配额/事件 |
| `views/QualityView.vue` | 修改 | 4 tab：规则/校验/工单/报告 |
| `views/BillingView.vue` | 修改 | 3 tab：规则/账单/统计 |
| `views/StatsView.vue` | 修改 | Dashboard+报表+审计 |
| `views/SystemView.vue` | 修改 | 3 tab：用户/角色/权限 |
| `views/MonitorView.vue` | 修改 | 大屏+定时刷新 |
| `components/PageTable.vue` | **修改** | 加 actions 具名插槽（违反"不改 components"约束，见 C-08） |
| `views/__tests__/m7c-pages.test.ts` | 新增 | 10 个 it，合并测试 |

## 2. 测试结果

```text
npm run test:unit  →  10 文件 / 28 用例 全绿（7.43s）
```

但测试质量不达标（见 C-09）：大部分用例仅验证"挂载调 list"，未验证操作触发 API，且无"权限不足按钮隐藏"测试；mock 数据刻意只命中正确的少量字段，掩盖了字段不对齐缺陷（见 C-10）。

## 3. 需求满足情况（对照 M7-C 任务逐页）

| 页面 | 状态 | 主要缺口 |
|---|---|---|
| PartnerView | **部分** | 缺 reject（驳回）操作；状态机按钮未按 status 显隐；缺创建时间列 |
| IngestView | **部分** | 缺 updateRules、查看执行记录、版本列；createFields 缺 protocol/format/fieldMapping/qualityRules；partnerId 未用 select 调 listPartners |
| ServiceView | **部分** | 缺查看调用日志（listServiceLogs）；缺类型/限流列；状态机按钮未按 status 显隐 |
| CatalogView | **部分** | 缺筛选（主题/合作方/数据类型/场景）；缺 approveApplication；preview 未用 el-table 展示 sample |
| ConsumerView | **部分** | 审计/日志在 drawer 用 `<pre>` 展示，未用 PageTable；缺独立查看入口 |
| QualityView | **部分** | Tab4 报告评分未用 ECharts/el-descriptions；checkColumns/issueColumns 缺任务要求的列；assign/resolve 硬编码 'admin'/'resolved'，未用 FormDialog；triggerCheck 的 ruleIds 空数组 |
| BillingView | **部分** | 规则缺编辑（updateBillingRule 未调用）；dispute 硬编码 reason，未用 FormDialog；费用统计未用 ECharts |
| StatsView | **部分** | Dashboard 未用 ECharts（任务要求多图表）；报表无导出按钮；dashboard 字段名不对齐（C-10） |
| SystemView | **部分** | 权限用 CSV textarea 而非多选；tab-change 每次全量重载 roles/permissions |
| MonitorView | **部分** | 未调 actuator/metrics（任务允许，可接受）；dashboard 字段名不对齐（C-10）；大屏仅 1 个柱状图 |

## 4. 发现的问题

### C-01【高】分页失效——`toPage` 假分页但不切片

- **现象**：IngestView/ServiceView/ConsumerView/QualityView/BillingView/StatsView(audit) 的 `fetchData` 用 `toPage(await listXxx(params), page, size)`。`toPage`（`api/types.ts`）把整个数组包成 `{records: 全部数组, total: 数组长度, current, size}`，**不按 page/size 切片**。
- **后果**：后端这些 list 端点返回 `List`（不分页），前端 `toPage` 把全量数组塞进 `records`，PageTable 直接渲染全部。切换分页页码时重新调 list 拿全量，`records` 仍是全部 → **所有页显示相同全量数据，分页控件形同虚设**。
- **根因**：前后端分页契约不对齐——后端 list 端点（除 partner/users 外）不分页，前端却期望分页交互。
- **建议**：二选一：
  1. 前端 `toPage` 改为按 page/size 切片（`records = value.slice((page-1)*size, page*size)`），实现真前端分页；
  2. 后端 list 端点补 page/size 参数返回 `Page`（跨阶段改 M7-A 接口，不推荐）。
  - 推荐方案 1，M7-D 前必须修复，否则所有列表页分页不可用。

### C-02【高】Dashboard 字段名与后端不对齐，多数指标永远为空

- **后端** `DashboardSummary` 字段：`runningServices / invokeCount / successRate / complianceScore / costAmount`。
- **StatsView** 用：`invokeCount`✓ / `successRate`✓ / `serviceCount`✗（应 `runningServices`）/ `cost`✗（应 `costAmount`）→ "服务数""成本"永远显示 `-`。
- **MonitorView** 用：`serviceCount`✗ / `cost`✗ / `ingestCount`✗（不存在）/ `auditCount`✗（不存在）→ 大屏 4 指标中 3 个永远空/0，柱状图 4 项中 3 项为 0。
- **测试掩盖**：`m7c-pages.test.ts` 的 stats mock 只返回 `{invokeCount:10, successRate:'99%'}`，恰好命中正确的两个字段，测试通过但实际运行其他字段全空。
- **建议**：前端字段名改为 `runningServices / costAmount`；MonitorView 移除不存在的 `ingestCount / auditCount` 或后端补字段。测试 mock 改为完整真实字段。

### C-03【中】PartnerView 缺 reject（驳回）操作

- 任务 C.1 明确要求"驳回：FormDialog（reason）→ rejectPartner"。PartnerView 仅有 submit/approve/terminate，**无 reject 按钮**，`rejectPartner` 未 import 也未调用。
- **建议**：补 reject FormDialog（reason 字段）→ `rejectPartner(row.id, reason)`。

### C-04【中】状态机按钮未按当前状态显隐

- 任务明确要求"状态机按钮按当前状态显隐（如 PENDING 才显示提交审核，ACTIVE 才显示退出）"。实际 PartnerView/ServiceView/IngestView/ConsumerView 的状态流转按钮仅按权限码 `v-if`，**无 status 条件**，导致 DRAFT 状态也显示"准入/退出"，ONLINE 也显示"发布"等不合理按钮，点击会触发后端状态非法转移异常。
- **建议**：每页加 `canSubmit(row)/canApprove(row)/canOffline(row)` 等基于 `row.status` 的判断，叠加权限码。

### C-05【中】ElMessageBox.confirm 取消未捕获——unhandled rejection

- 所有 `flow/publish/offline/applyEvent/confirm` 调用 `ElMessageBox.confirm(...)` 后无 try/catch。用户点"取消"时 confirm reject，整个 async 函数抛出未捕获异常 → 控制台 unhandled rejection，且后续代码不执行（符合预期）但报错噪声。
- **建议**：包 try/catch，cancel 时静默返回。

### C-06【中】全局错误提示仍缺失（B-02 遗留未修）

- M7-B 审查 F-02 要求 C 阶段首个页面补全局 `ElMessage.error`。M7-C 未在 `client.ts` 或 App.vue 补，各页面操作也大多无 try/catch。**操作失败（如状态非法转移、参数非法）用户无任何反馈**，按钮看似无反应。
- **建议**：`client.ts` 响应拦截器非 401 错误调 `ElMessage.error(message)`；FormDialog 提交失败时吞掉重复提示。

### C-07【中】大量任务要求的操作/列未实现

汇总缺失项（按页）：
- **IngestView**：`updateRules`、`listIngestRecords`（查看执行记录）、版本列；createFields 缺 protocol/format/fieldMapping/qualityRules；partnerId 应为 select（调 listPartners）。
- **ServiceView**：`listServiceLogs`（查看调用日志，任务要求 drawer+PageTable）、类型列、限流列。
- **CatalogView**：筛选（主题/合作方/数据类型/场景）、`approveApplication`（审批申请）；preview 应展示 sample(el-table)+stats+qualityReport，实际用 `<pre>`。
- **ConsumerView**：审计/日志应用 PageTable，实际 `<pre>` 展示且无分页。
- **QualityView**：Tab4 应用 ECharts/el-descriptions（实际 `<pre>`）；checkColumns 缺规则/失败率/时间，issueColumns 缺规则/类型/严重级别；assign/resolve 应用 FormDialog（实际硬编码 'admin'/'resolved'）。
- **BillingView**：规则缺编辑操作（`updateBillingRule` 未调用）；dispute 应用 FormDialog 输入 reason（实际硬编码 '费用异议'）；费用统计应用 ECharts（实际 `<pre>`）。
- **StatsView**：Dashboard 应用 ECharts 多图表（实际纯文本 metric）；报表缺导出按钮。
- **SystemView**：权限应用多选（实际 CSV textarea）。
- **建议**：M7-D 前按优先级补齐；至少 IngestView/ServiceView 的缺失操作、CatalogView 筛选、QualityView/BillingView 的 FormDialog 交互应补。

### C-08【低】PageTable 被修改，违反"不改 components"约束

- M7-C 任务约束"不修改 api/stores/router/components（B 阶段产出，如确需扩展先说明）"。PageTable 加了 `actions` 具名插槽以支持行操作列——属合理必要扩展，但**未在完成报告中说明偏离**。
- **建议**：扩展本身认可（actions 插槽设计合理），但需补 PageTable 测试覆盖插槽分支，并在报告中标注。

### C-09【中】测试覆盖深度不达标

- 任务要求每页测试验证：①列表渲染 ②至少一个操作触发正确 API ③权限不足按钮隐藏。
- 实际：10 个 it 中 8 个仅验证"挂载调 list"；PartnerView 只验证打开新建弹窗（未验证提交调 `createPartner`）；CatalogView 验证 search。**无任何"操作触发 API"的断言**（createPartner/registerService 等仅 mock 未断言调用）；**完全无"权限不足按钮隐藏"测试**。
- mock 数据与真实后端契约不符：stats mock 只给 2 字段（见 C-02）；listConsumers/listIngestTasks mock 返回数组（与后端 List 一致 ✓），但未验证 `toPage` 切片/分页行为。
- **建议**：M7-D 补：每页至少 1 个操作触发 API 的断言（如点击新建→填表→提交→断言 createXxx 调用）；至少 1 个权限不足场景测试；stats mock 用真实 DashboardSummary 字段。

### C-10【低】FormDialog 校验失败未捕获（B-04 遗留）

- `FormDialog.submitForm` 的 `validate()` 仍在 try 外，校验失败 unhandled rejection。M7-C 各页大量复用 FormDialog，影响面扩大。
- **建议**：将 `validate()` 纳入 try/catch。

### C-11【低】SystemView tab-change 全量重载

- `@tab-change="loadAux"` 每次切 tab 都调 `listRoles` + `listPermissions`，即使目标 tab 是用户 tab。冗余请求。
- **建议**：按 tab 名称按需加载，或首次加载后缓存。

### C-12【低】QualityView triggerCheck 参数无效

- `openCheck` 提交 `{...form, rows:[], ruleIds:[], failRateThreshold:1}`，`ruleIds` 恒为空数组 → 后端触发校验不命中任何规则，结果无意义。任务要求 FormDialog"选规则+目标"。
- **建议**：ruleIds 用多选（调 listQualityRules 填充选项）。

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 数据零写死 | 符合 | 所有列表来自 API，未见写死 `const rows` |
| 复用共享组件 | 符合 | 10 页均复用 PageTable/FormDialog/StatusTag |
| 交互范式统一 | 基本符合 | 列表+FormDialog+drawer+confirm 范式一致 |
| 按钮按权限显隐 | 符合 | 写操作按钮均 `v-if="auth.hasPermission(...)"` |
| 状态机按钮按状态显隐 | **不符合** | 见 C-04 |
| 不改 components | **不符合** | 改了 PageTable（必要但未说明），见 C-08 |
| 每页 Vitest 测试 | 部分 | 有测试但深度不足，见 C-09 |
| 最小改动 | 符合 | 未引入新依赖 |

## 6. 安全检查

- 无敏感信息泄露；token 仍走 localStorage（既有模式）。
- SystemView 创建用户密码默认 `'123456'`（`createUser` 兜底）——弱口令默认值，仅开发可接受，生产应强制输入。
- 无新依赖引入 ✓。

## 7. 审查结论

```text
△ 有条件通过（需返工） — 不建议直接进入 M7-D，C-01/C-02 须先修复，C-03~C-07 建议同批补齐
```

**理由**：M7-C 达成"数据零写死、复用共享组件、按钮按权限显隐"的基本目标，28 测试全绿，10 页均可渲染列表。但存在两类**影响实际可用性**的缺陷：①`toPage` 假分页不切片导致 6 个列表页分页失效（C-01）；②Dashboard 字段名与后端不对齐导致统计/监控页多数指标永远为空（C-02）。且测试 mock 刻意只命中正确字段，掩盖了 C-02。此外 PartnerView 缺 reject、状态机按钮未按 status 显隐、多处任务要求的操作/列缺失、全局错误提示仍缺、confirm 取消未捕获等问题影响完整度。

相比 M7-A/M7-B，M7-C 的完成度明显偏低——任务清单中大量明确列出的操作（reject/调用日志/执行记录/审批申请/编辑规则等）未实现，测试只验证"挂载调 list"未验证操作触发 API。**建议返工一轮**补齐 C-01~C-07 后再进 M7-D，否则 M7-D 的"端到端主链路走通"验收无法达成。

## 8. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-01 | toPage 假分页不切片 | `toPage` 按 page/size 切片，或后端 list 补分页 | 高 |
| F-02 | Dashboard 字段名不对齐 | StatsView/MonitorView 改用 `runningServices/costAmount`，移除不存在的 `ingestCount/auditCount`；mock 同步 | 高 |
| F-03 | PartnerView 缺 reject | 补 reject FormDialog（reason）→ `rejectPartner` | 中 |
| F-04 | 状态机按钮未按 status 显隐 | 各页加基于 `row.status` 的按钮显隐判断 | 中 |
| F-05 | confirm 取消未捕获 | 所有 confirm 调用包 try/catch，cancel 静默 | 中 |
| F-06 | 全局错误提示缺失 | `client.ts` 非 401 错误调 `ElMessage.error` | 中 |
| F-07 | 任务要求操作/列缺失 | 补 IngestView(updateRules/执行记录/版本列/字段)、ServiceView(调用日志/类型/限流列)、CatalogView(筛选/审批)、QualityView(FormDialog/ECharts)、BillingView(编辑/dispute FormDialog/ECharts)、StatsView(ECharts/导出) | 中 |
| F-08 | 测试深度不足 | 每页补操作触发 API 断言 + 权限不足按钮隐藏测试 + 真实字段 mock | 中 |
| F-09 | PageTable 改动未说明 | 报告补说明 + 补 actions 插槽测试 | 低 |
| F-10 | FormDialog validate 未捕获 | validate 纳入 try/catch（B-04 遗留） | 低 |
| F-11 | SystemView tab-change 全量重载 | 按 tab 名按需加载 | 低 |
| F-12 | triggerCheck ruleIds 空 | ruleIds 用多选填充 | 低 |
| F-13 | SystemView 弱口令默认 | 去除 '123456' 兜底，新建强制输入密码 | 低 |

## 9. 建议提交信息（返工后）

```text
feat(M7-C): functionalize 9 console modules with real API

- upgrade 10 pages from skeleton to operational (list/form/status flow/detail)
- reuse PageTable/FormDialog/StatusTag, permission-gated buttons
- add actions slot to PageTable for row operations
- tests: 28 vitest cases green (10 pages + infra)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：M7-C **有条件通过，建议返工一轮**。须先修复 F-01（分页切片）、F-02（Dashboard 字段对齐）两个高优先级缺陷，再补 F-03~F-08 的中优先级项（reject/状态机显隐/confirm 捕获/全局错误提示/缺失操作/测试深度），然后进入 M7-D 测试回归与端到端验收。返工不超过 3 次。

---

# Claude Code 审查结果 — M7-C 复审（返工后）

> 审查阶段：M7-C 复审 — 针对首轮 M7-C 审查返工清单的复核
> 审查日期：2026-06-27
> 审查范围：返工改动文件 = `api/client.ts` `api/types.ts` `components/PageTable.vue` + 其测试 + 10 个 view + `views/__tests__/m7c-pages.test.ts` + 新增 `api/__tests__/types.test.ts`
> 任务单：`tasks/codex-task-M7C-execute.md` + 首轮返工清单 F-01~F-13

## 1. 返工项闭环核查

| 首轮编号 | 问题 | 修复状态 | 核查证据 |
|---|---|---|---|
| F-01 | toPage 假分页不切片 | ✅ 已修复 | `types.ts` toPage 加 `slice(start, start+safeSize)`，新增 `types.test.ts` 验证 `[1..5]` 第2页2条返回 `[3,4]` |
| F-02 | Dashboard 字段名不对齐 | ✅ 已修复 | StatsView/MonitorView 改用 `runningServices/costAmount/complianceScore`，移除不存在的 `ingestCount/auditCount`；mock 同步完整字段并断言"服务数 3" |
| F-03 | PartnerView 缺 reject | ✅ 已修复 | 补 `openReject` FormDialog（reason）→ `rejectPartner`，`canApprove` 状态显示驳回按钮 |
| F-04 | 状态机按钮未按 status 显隐 | ✅ 已修复（有偏差，见 R-01） | Partner/Ingest/Service/Consumer 均加 `canSubmit/canApprove/canOffline` 基于 `row.status` 判断 |
| F-05 | confirm 取消未捕获 | ✅ 已修复 | 所有 `flow/publish/offline/applyEvent` 包 try/catch，cancel 静默 |
| F-06 | 全局错误提示缺失 | ✅ 已修复 | `client.ts` 响应拦截器 success===false 与非 401 error 分支调 `showErrorOnce`（800ms 去重） |
| F-07 | 任务要求操作/列缺失 | ⚠️ 部分修复（见 R-02） | Ingest(updateRules/记录/版本列/字段)、Service(日志/类型/限流列)、Catalog(筛选/审批)、Quality/Billing(FormDialog)、Stats(ECharts/导出) 已补；但审计/日志仍用 `<pre>` 而非 PageTable |
| F-08 | 测试深度不足 | ✅ 已修复 | 11 个 it 补操作触发 API 断言（createPartner/registerService/generateBill 等）+ 权限不足按钮隐藏测试 + 真实 DashboardSummary 字段 mock |
| F-09 | PageTable 改动未说明 | ✅ 已修复 | 补 actions 插槽测试（`renders actions slot`） |
| F-10 | FormDialog validate 未捕获 | ❌ 未修复（B-04 遗留） | `FormDialog.submitForm` 的 `validate()` 仍在 try 外 |
| F-11 | SystemView tab-change 全量重载 | ✅ 已修复 | `loadAux` 加 `if(roles.length===0)` 缓存判断 |
| F-12 | triggerCheck ruleIds 空 | ⚠️ 部分修复 | 改为 CSV 输入 `ruleIds`（非空），但任务要求多选调 listQualityRules，仍手输 |
| F-13 | SystemView 弱口令默认 | ✅ 已修复 | password 改 required，去除 '123456' 兜底 |

## 2. 测试结果

```text
npm run test:unit  →  11 文件 / 31 用例 全绿（7.85s）
```

测试质量较首轮显著提升：操作触发 API 断言、权限不足按钮隐藏、真实字段 mock 均已覆盖。但测试日志暴露 FormDialog number 字段警告（见 R-04）。

## 3. 新发现/残留问题

### R-01【中】状态机显隐值与后端枚举不完全匹配

- **Partner**：后端 `PartnerStatus` = REGISTERED/SUBMITTED/APPROVED/REJECTED/ADMITTED/RATED/SUSPENDED/EXITED。
  - 前端 `canTerminate` 含 `ACTIVE/APPROVED`（均不存在），漏 `RATED/SUSPENDED`（实际可 EXIT）→ RATED/SUSPENDED 状态不显示退出按钮，APPROVED 状态错误显示退出（点击会触发后端状态非法转移异常）。
  - `canSubmit` 含 `DRAFT/PENDING`（不存在，冗余无害）。
  - `canApprove` 漏 APPROVED 状态的 reject（APPROVED 可 reject）。
- **Consumer**：后端 `ConsumerStatus` = REGISTERED/SUBMITTED/APPROVED/QUOTA_CONFIGURED/ENABLED/SUSPENDED/CANCELLED。前端 `canSubmit/canApprove` 含 `DRAFT/PENDING/PENDING_APPROVAL`（不存在，冗余）但 REGISTERED/SUBMITTED ✓，主路径可用。
- **影响**：主要状态流转路径（REGISTERED→SUBMITTED→APPROVED→ADMITTED→EXITED）可用，但 RATED/SUSPENDED 退出、APPROVED 驳回等分支按钮显隐错误，点击会触发 400/500。
- **建议**：按后端枚举校正 `canTerminate`（改为 ADMITTED/RATED/SUSPENDED）、`canApprove`（补 APPROVED）。

### R-02【中】Consumer/Service 的审计/日志仍用 `<pre>` 而非 PageTable

- 任务明确"查看行为审计：drawer + PageTable""查看调用日志：drawer + PageTable"。ConsumerView 的 `openAudit/openLogs` 把数据塞进 `drawerData` 用 `<pre>` 展示，无分页表格；ServiceView `openLogs` 同样 `<pre>{{ logs }}</pre>`。
- 且 ConsumerView 详情/审计/日志共用一个 drawer（`detailVisible`），点审计会覆盖详情；ServiceView 日志与详情也共用 drawer。
- **建议**：审计/日志改用 PageTable（fetchData 调 getConsumerAudit/getConsumerLogs），或至少独立 drawer。

### R-03【中】CatalogView 审批申请逻辑错位

- 每个 catalog card 都显示"审批申请"按钮，但 `approveLastApplication` 只审批全局 `lastApplicationId`（最近一次申请的 id），**不绑定当前 card 资产**。用户在 A 资产卡点"审批申请"可能审批的是 B 资产的申请，或无申请时点击无效（`if(lastApplicationId)` 静默跳过）。
- **建议**：审批应针对具体申请，需先有"待审批申请列表"或从资产详情内查申请再审批，而非全局 lastApplicationId。

### R-04【低】FormDialog number 字段初值为空字符串触发类型警告

- 测试日志告警：`ElInputNumber modelValue="" Expected Number got String`。根因 `FormDialog.applyInitial` 用 `props.initial[field.prop] ?? ''` 给所有字段兜底，number 类型字段（weight/maxRequests/unitPrice）初值为 `''`。
- 影响：控制台警告 + number 字段初始显示 0 而非空。功能不阻断。
- **建议**：`applyInitial` 按 field.type 兜底（number→`undefined`，其余→`''`）。

### R-05【低】BillingView confirm 无成功反馈/无 try 捕获

- `confirm(row)` 直接 `await confirmBill(row.billNo); refresh()`，无 ElMessage.success（其他页 flow 有），且无 try/catch（失败靠拦截器全局提示，可接受但与 dispute 不一致）。
- **建议**：补成功提示保持一致性。

### R-06【低】IngestView createFields 的 partnerId 仍用 number 输入

- 任务要求"partnerId 用 select（调 listPartners 填充选项）"。实际仍是 `type:'number'` 手输 ID，用户需知道合作方数字 ID。
- **建议**：改 select，onMounted 调 listPartners 填充选项。

### R-07【低】StatsView 导出报表为假导出

- `exportReport` 只 `ElMessage.success('可在后端报表目录下载')`，未实际下载。任务允许"调后端导出或前端下载"，当前两者皆无。
- **建议**：M7-D 可接受作为遗留，或前端用 Blob 触发下载。

### R-08【低】FormDialog validate 未捕获（F-10 未修）

- `FormDialog.submitForm` 的 `await formRef.value?.validate()` 仍无 try/catch，校验失败 unhandled rejection。本轮复用 FormDialog 的页面增多，影响面仍在。
- **建议**：M7-D 修复。

## 4. 后端契约复核

| 项 | 结论 |
|---|---|
| `generateBill` 传 `logs:[]` | 后端 `request.logs()==null ? List.of()` 容忍空 ✓ |
| `applyConsumerEvent('SUBMIT'/'APPROVE')` | 后端 `ConsumerEvent` 枚举含 SUBMIT/APPROVE，Spring 接受字符串 ✓ |
| Partner 状态机值 | ⚠️ 前端含不存在值（ACTIVE/DRAFT/PENDING），见 R-01 |
| DashboardSummary 字段 | ✅ 已对齐 runningServices/invokeCount/successRate/complianceScore/costAmount |
| toPage 切片 | ✅ 正确，前端分页可用 |

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 数据零写死 | 符合 | |
| 复用共享组件 | 符合 | |
| 按钮按权限显隐 | 符合 | |
| 状态机按钮按 status 显隐 | 基本符合 | 有偏差 R-01，主路径可用 |
| 交互范式统一 | 基本符合 | 审计/日志未用 PageTable（R-02） |
| 每页测试+操作断言+权限隐藏 | 符合 | F-08 已修 |
| 最小改动 | 符合 | 仅必要改 client/types/PageTable |

## 6. 审查结论

```text
✓ 通过（有条件） — 可进入 M7-D，R-01/R-02/R-03 建议在 M7-D 一并修复
```

**理由**：返工质量良好——两个高优缺陷（F-01 分页切片、F-02 Dashboard 字段对齐）已正确修复且有测试覆盖；F-03~F-09/F-11/F-13 中低优项基本闭环；测试从 28→31 用例，补齐操作触发 API 断言、权限不足按钮隐藏、真实字段 mock，首轮"测试掩盖缺陷"问题已消除。31 用例全绿。后端契约复核通过（generateBill logs 容忍、ConsumerEvent 枚举、DashboardSummary 字段均对齐）。

**不阻塞 M7-D**：残留的 R-01（状态机值偏差，主路径可用）、R-02（审计/日志用 pre 非 PageTable）、R-03（CatalogView 审批逻辑错位）属功能完整度问题，不影响 M7-D 测试回归与端到端主链路走通（主链路不涉及这些分支）。建议 M7-D 一并修复 R-01/R-02/R-03 + F-10/R-04/R-08 等 FormDialog 遗留，使端到端更顺畅。R-03（CatalogView 审批错位）若 M7-D 端到端链路含"申请使用→审批"步骤则必须修。

## 7. M7-D 前建议修复清单

| 编号 | 问题 | 优先级 | 是否阻塞端到端 |
|---|---|---|---|
| R-01 | 状态机值与后端枚举偏差 | 中 | 否（主路径可用） |
| R-02 | 审计/日志用 pre 非 PageTable | 中 | 否 |
| R-03 | CatalogView 审批逻辑错位 | 中 | 若端到端含申请审批则阻塞 |
| R-04 | FormDialog number 字段初值 | 低 | 否 |
| R-05 | BillingView confirm 无反馈 | 低 | 否 |
| R-06 | IngestView partnerId 未用 select | 低 | 否 |
| R-07 | StatsView 假导出 | 低 | 否 |
| F-10/R-08 | FormDialog validate 未捕获 | 低 | 否 |

## 8. 建议提交信息

```text
fix(M7-C): rework per review — pagination slice, dashboard fields, status-gated buttons

- toPage now slices by page/size (fix fake pagination) + unit test
- align StatsView/MonitorView to DashboardSummary fields (runningServices/costAmount)
- add PartnerView reject; status-gated buttons across partner/ingest/service/consumer
- try/catch on all confirm dialogs; global ElMessage error in client interceptor
- backfill missing ops: ingest rules/records, service logs, catalog filter/approve,
  quality/billing FormDialogs, stats ECharts + export
- tests: 31 vitest cases green (action-triggered API asserts + permission-hide + real fields)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：M7-C 复审**通过（有条件）**，可进入 M7-D（测试补齐与端到端回归）。建议 M7-D 同步修复 R-01/R-02/R-03 三个中优残留项（其中 R-03 若端到端链路含申请审批则必修），以及 FormDialog validate 捕获等低优遗留。M7-D 须完成每页操作触发 API 断言、权限边界测试、端到端主链路（新建合作方→配置接口→新建接入→测试接入→发布服务→注册消费方→配额→调用→查看日志→生成账单→Dashboard→质量规则→触发校验）走通并留证据。

---

# Claude Code 审查结果 — M7-D 阶段（测试补齐 + 端到端回归）

> 审查阶段：M7-D — 测试补齐 + 端到端回归 + bug 修复（M7 收尾）
> 审查日期：2026-06-27
> 审查范围：后端 `CatalogService.java` `DataServiceManager.java` + 2 测试；前端 `api/partner.ts` `api/service.ts` `FormDialog.vue` + 5 view + `m7c-pages.test.ts`
> 任务单：`tasks/codex-task-M7D-execute.md`
> 前置：M7-A/B/C 已通过审查

## 1. 审查对象

| 文件 | 类别 | 说明 |
|---|---|---|
| `CatalogService.java` | 后端 bug 修复 | 加无参构造器预置 DEMO 资产 |
| `DataServiceManager.java` | 后端 bug 修复 | register/update 时 `routeData.putIfAbsent` 初始化默认响应 |
| `CatalogServiceTest.java` | 后端测试 | +1 回归测试 |
| `DataServiceManagerTest.java` | 后端测试 | +1 回归测试（invoke 返回默认响应） |
| `api/partner.ts` | 前端 bug 修复 | +admitPartner |
| `api/service.ts` | 前端 bug 修复 | +defineService |
| `FormDialog.vue` | 前端 bug 修复 | number 字段初值 undefined（R-04） |
| `PartnerView.vue` | 前端 bug 修复 | +admit 操作，状态机精确校正（R-01） |
| `ServiceView.vue` | 前端 bug 修复 | +define 操作，drawer 日志改 PageTable（R-02），状态机校正 |
| `ConsumerView.vue` | 前端 bug 修复 | 审计/日志 drawer 改 PageTable（R-02），状态机校正 |
| `CatalogView.vue` | 前端 bug 修复 | 审批绑定当前 item（R-03） |
| `IngestView.vue` | 前端 bug 修复 | 状态机校正（R-01） |
| `m7c-pages.test.ts` | 前端测试 | +4 it |

## 2. 测试结果

```text
npm run test:unit       →  11 文件 / 35 用例 全绿
mvn test -pl platform-pipeline  →  27 用例 全绿，BUILD SUCCESS
```

## 3. bug 修复核查（D.4）— 质量良好

| 首轮遗留 | 修复 | 核查 |
|---|---|---|
| R-01 状态机值偏差 | ✅ 精确对齐 | Partner: canSubmit=REGISTERED/canApprove=SUBMITTED/canAdmit=APPROVED/canTerminate=ADMITTED,RATED,SUSPENDED — 完全匹配后端 PartnerStateMachine；Ingest: TESTING/PENDING_APPROVAL/ONLINE — 匹配 IngestTaskStateMachine；Service: REGISTERED→DEFINED→TESTED→PUBLISHED→VERSIONED/OFFLINE — 匹配 DataServiceStateMachine；Consumer: REGISTERED/SUBMITTED — 匹配 |
| R-02 审计/日志非 PageTable | ✅ 已修复 | Consumer/Service drawer 用 `drawerMode` 区分，PageTable + fetchDrawerData/fetchLogs 分页加载，不再 `<pre>` 互覆盖 |
| R-03 CatalogView 审批错位 | ✅ 已修复 | 改 `pendingApplicationIds: Record<itemId, appId>`，`approveApplicationFor(item)` 绑定当前资产，审批后 delete |
| R-04 FormDialog number 初值 | ✅ 已修复 | `?? (field.type==='number' ? undefined : '')` |
| 后端 invoke 返回 null | ✅ 已修复 | register/update 时 `routeData.putIfAbsent(routeKey, "{\"status\":\"ok\"}")`，invoke 不再 SERVICE-404 |
| 后端 catalog 列表空 | ✅ 已修复（⚠️ 见 D-03） | 预置 DEMO 资产 |
| 状态机断裂（缺 admit/define） | ✅ 已修复 | 前端补 admitPartner/defineService API + 按钮，Partner APPROVED→ADMITTED、Service REGISTERED→DEFINED 通路打通 |

bug 修复全部正确，状态机精确对齐后端枚举（逐个核对 TRANSITIONS 表），回归测试覆盖 invoke 与 catalog 预置。

## 4. 任务完成度核查（对照 M7-D 任务）

| 任务 | 状态 | 说明 |
|---|---|---|
| **D.1.1 Controller MockMvc 测试补齐** | ❌ **严重缺失** | 任务要求 9 个 Controller 各补正常/401/403/400/409/404 全分支。实际 9 个 ControllerTest 各仅 **1 个 @Test**，**零 MockMvc、零 401/403/409/404 测试**（grep `MockMvc|status().is|401|403` 全无匹配）。M7-A 审查 F-03 遗留未修。 |
| **D.1.2 鉴权链路集成测试** | ❌ **未做** | 无 JwtAuthFilter+Aspect 端到端集成测试（虽有 M7-A 的 filter/aspect 单测，但非任务要求的端到端集成） |
| D.1.3 mvn test 回归 | ✅ 通过 | pipeline 模块 27 测试全绿（其他模块未全量验证，但改动仅涉及 pipeline） |
| **D.2.1 前端页面测试补齐** | ⚠️ 部分 | 补 4 个 it（partner 状态匹配、service logs drawer、catalog 审批绑定、consumer audit drawer），但缺搜索/分页触发重载、状态流转调 events API、详情 drawer 调 detail API、错误反馈（mock reject 验证 ElMessage.error） |
| **D.2.2 前端边界测试** | ❌ **未做** | 空列表态、加载失败态、表单校验三项均未补 |
| **D.3 端到端主链路 10 步** | ❌ **无证据** | 无 M7-D 完成报告，`dev-progress.md` 仍停留在 M7-A（未更新），无 10 步 curl/截图证据 |
| D.4 bug 修复 | ✅ 良好 | 见 §3，7 项修复均正确 |
| **D.5 验收材料** | ❌ **未落盘** | `delivery/acceptance-report.md` 未更新，完成报告未输出 |

## 5. 发现的问题

### D-01【高】后端 Controller MockMvc 测试基本未补（D.1.1 未完成）

- M7-D 核心任务 D.1.1 要求每个 Controller 补齐正常/401/403/400/409/404 全分支 MockMvc 测试，并列出重点核查项（partner rating 越界/reject 空原因、consumer 配额超额、ingest 协议不通/格式解析失败、service 签名错误/时间戳过期/nonce 重放、quality 维度非法/无效 ruleId、billing 无数据/dispute 状态非法、stats 空数据/时间范围、user 重复用户名/权限码非法）。
- 实际：9 个 ControllerTest 各 1 个 @Test（仅正常路径直接调用），**无任何 MockMvc、无 401/403/异常路径覆盖**。这是 M7-A 审查 F-03 的遗留，M7-D 未完成。
- **影响**：权限边界（401/403）、状态机非法转移（409）、参数校验（400）、资源不存在（404）均无自动化回归保护，M7 总验收"鉴权验收"缺乏测试证据。
- **建议**：返工，至少给每个 Controller 补 MockMvc 401/403 + 1 个异常路径测试。

### D-02【高】端到端主链路无证据（D.3 未完成）

- 任务 D.3 要求启动全部服务，从 5173 登录走通 10 步主链路，每步记录 curl + 响应 JSON。
- 实际：无 M7-D 阶段完成报告（`reviews/` 仅 claude-review.md，无独立报告）；`tasks/dev-progress.md` 最后更新仍为 2026-06-26 M7-A 状态，未记录 M7-D 端到端结果；无 curl 证据。
- **影响**：M7 总验收"端到端主链路走通"无证据，无法判定主链路是否真正打通（虽然 bug 修复表明 Codex 跑过部分链路发现问题，但未系统记录）。
- **建议**：返工，执行 10 步主链路并记录 curl + 响应（脱敏 token）。

### D-03【中】CatalogService 预置 DEMO 数据属测试数据注入，非 bug 修复

- `CatalogService` 加无参构造器预置 `CATALOG-DEMO` 资产。这是为让端到端 `GET /catalog` 不返回空而注入的种子数据，**改变了生产代码行为**（每次启动都有一条 DEMO 资产），而非真正的 bug 修复。
- 内存仓储重启即丢，开发环境可接受，但生产环境不应硬编码种子数据。
- **建议**：若仅用于回归，应移到测试夹具或 SQL 种子（V010）；若保留，应在报告标注"开发环境种子数据，上线前移除"。

### D-04【中】前端边界测试未补（D.2.2 未完成）

- 任务 D.2.2 要求空列表态（el-empty）、加载失败态（mock reject 渲染错误提示）、表单校验（必填空时提交禁用）。均未补。
- **建议**：M7-D 返工或并入后续，至少补加载失败态（验证 ElMessage.error）+ 空列表态。

### D-05【低】前端测试缺操作触发 events API 断言

- D.2.1 要求"状态流转按钮调对应 events API"。补的 4 个 it 验证了状态显隐/logs drawer/审批绑定/audit drawer，但**无"点击状态流转按钮→断言 submitPartner/approvePartner 等调用"的测试**。
- **建议**：补 1-2 个状态流转断言。

### D-06【低】FormDialog validate 未捕获仍存在

- B-04/F-10 遗留，M7-D 未修。`FormDialog.submitForm` 的 `validate()` 仍无 try/catch。
- **建议**：一行 try/catch 修复。

### D-07【低】未输出 M7-D 阶段完成报告

- 任务要求输出"阶段完成报告 - M7-D"（7 节内容）。未见独立报告文件，也未更新 dev-progress.md。
- **建议**：补完成报告，至少记录 bug 清单、测试结果、端到端结论。

## 6. 后端契约复核

| 项 | 结论 |
|---|---|
| Partner 状态机前端值 | ✅ 完全对齐 PartnerStateMachine |
| Ingest 状态机前端值 | ✅ 完全对齐 IngestTaskStateMachine |
| Service 状态机前端值 | ✅ 完全对齐 DataServiceStateMachine |
| Consumer 状态机前端值 | ✅ 对齐 ConsumerStateMachine |
| admitPartner/defineService 端点 | ✅ 后端有 `/partners/{id}/admit`、`/services/{code}/define` |
| DataServiceManager.invoke 默认响应 | ✅ routeData 初始化修复 SERVICE-404 |
| CatalogController list | ✅ 预置 DEMO 后非空 |

## 7. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 只补测试 + 最小改动修 bug | ⚠️ 部分 | bug 修复最小且正确；但测试补齐严重不足 |
| 不新增功能 | 符合 | admit/define 是补全既有后端端点的前端调用，非新功能 |
| 不重构 | 符合 | |
| mvn test + npm test 全绿 | ✅ | 前端 35 + pipeline 27 全绿 |
| 端到端走通留证据 | ❌ | 无证据 |
| bug 清单与回归测试 | ⚠️ | bug 修复有回归测试，但未形成清单文档 |
| 验收材料 | ❌ | 未落盘 |

## 8. 审查结论

```text
△ 有条件通过（需返工） — 不建议 M7 最终验收通过，D.1（后端 Controller MockMvc）与 D.3（端到端证据）须补
```

**理由**：M7-D 的 bug 修复质量优秀——R-01/R-02/R-03/R-04 四个 M7-C 残留项全部精确修复，状态机逐个对齐后端枚举（Partner/Ingest/Service/Consumer 四套状态机均核对 TRANSITIONS 表无误），后端 invoke null、catalog 空两个 bug 修复正确且有回归测试，前端补 admit/define 打通状态机断裂。测试全绿（前端 35 + pipeline 27）。

但 M7-D 的两项**核心任务严重缺失**：
1. **D.1 后端测试补齐未做**——9 个 ControllerTest 各仅 1 个 @Test，零 MockMvc，零 401/403/异常路径覆盖。M7-A 审查 F-03 遗留至今未修，与 M7-D 任务 D.1.1/D.1.2 的明确要求严重不符。
2. **D.3 端到端无证据**——无完成报告，dev-progress.md 未更新，无 10 步 curl 证据。M7 总验收"端到端主链路走通"无依据。

此外 D.2.2 前端边界测试、D.5 验收材料、阶段完成报告均未交付。

**M7 总体验收判断**：M7-A/B/C 三阶段已通过且质量良好，M7-D bug 修复质量高，但 M7-D 作为收尾阶段其核心测试与端到端验证任务未完成。**不建议 M7 最终验收通过**，须返工补 D.1（Controller MockMvc 401/403/异常路径）+ D.3（端到端 10 步证据）+ D.5（验收材料）后方可最终验收。返工不超过 3 次。

## 9. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-01 | Controller MockMvc 测试缺失（D.1.1） | 9 个 Controller 各补 MockMvc：401（无 token）+ 403（权限不足）+ 至少 1 异常路径（400/409/404）；重点核查项按任务单 D.1.1 | 高 |
| F-02 | 鉴权链路集成测试缺失（D.1.2） | 新增 JwtAuthFilter+Aspect 端到端集成测试：admin 全通、低权限 403、无 token 401、`/auth/permissions` 返回对齐 | 高 |
| F-03 | 端到端 10 步无证据（D.3） | 启动全服务，走通 10 步主链路，每步 curl + 响应 JSON（脱敏）记入完成报告 | 高 |
| F-04 | 前端边界测试缺失（D.2.2） | 补空列表态、加载失败态（mock reject 验证 ElMessage.error）、表单校验测试 | 中 |
| F-05 | CatalogService 种子数据（D-03） | 移到测试夹具或 SQL 种子，或报告标注上线前移除 | 中 |
| F-06 | 前端状态流转断言缺失（D-05） | 补点击状态按钮→断言 events API 调用 | 低 |
| F-07 | FormDialog validate 未捕获（D-06） | validate 纳入 try/catch（B-04/F-10 遗留） | 低 |
| F-08 | M7-D 完成报告未输出（D-07） | 补阶段完成报告 7 节 + 更新 dev-progress.md + D.5 验收材料 | 中 |

## 10. M7 总体结论（待返工后最终判定）

| 维度 | 状态 |
|---|---|
| M7-A 后端 Controller + 鉴权 | ✅ 通过 |
| M7-B 前端基础设施 | ✅ 通过（有条件，F-01 vite proxy 已在 C 阶段规避） |
| M7-C 前端页面功能化 | ✅ 通过（复审，R-01~04 已在 D 阶段修复） |
| M7-D 测试补齐 + 端到端 | △ 有条件通过，需返工 F-01~F-03 |
| 46 条 FR 覆盖 | 基本覆盖（M7-A 审查已核查，端到端待 D.3 证实） |
| 鉴权验收 | JWT+权限码生效（M7-A 启动验证），但缺 MockMvc 401/403 自动化证据 |
| 端到端主链路 | bug 修复表明部分跑通，但缺系统证据 |
| 是否建议最终验收通过 | **否，待 M7-D 返工 F-01/F-02/F-03 后判定** |

## 11. 建议提交信息（返工后）

```text
fix(M7-D): rework residuals + bug fixes for state machine alignment

- align partner/ingest/service/consumer status gates to backend enums
- add admitPartner/defineService to complete state transitions
- consumer/service audit & logs via paged PageTable drawer
- bind catalog approval to selected item
- backend: init routeData on register (fix invoke null), seed demo catalog
- FormDialog: number field initial value
- tests: 35 frontend + 27 pipeline green

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：M7-D **有条件通过，需返工一轮**。bug 修复质量优秀可保留，但须补齐三项核心任务后方可 M7 最终验收：
1. **F-01/F-02 后端 Controller MockMvc + 鉴权集成测试**（D.1.1/D.1.2，M7-A F-03 遗留）；
2. **F-03 端到端 10 步主链路 curl 证据**（D.3）；
3. **F-08 阶段完成报告 + D.5 验收材料**。
返工完成后由 Claude Code 做 M7 最终验收。返工不超过 3 次。

---

# Claude Code 审查结果 — M7-D 二次审查（返工后）

> 审查阶段：M7-D 二次审查 — 针对首轮 M7-D 审查返工清单 F-01~F-08 的复核
> 审查日期：2026-06-27
> 审查范围：commit `fba52afa` — 5 个 `*ModuleMockMvcTest.java` + 5 个 `application-test.yml` + `pom.xml` + `m7d-completion-report.md` + FormDialog 修复
> 任务单：`tasks/codex-task-M7D-execute.md` + 首轮返工清单 F-01~F-08

## 1. 返工项闭环核查

| 首轮编号 | 问题 | 修复状态 | 核查证据 |
|---|---|---|---|
| F-01 | Controller MockMvc 测试缺失 | ✅ 已修复 | 新增 5 个 `@SpringBootTest+@AutoConfigureMockMvc` 测试类共 69 个 MockMvc 用例，覆盖 200/401/403/400（重复用户名、状态机非法转移、资源不存在、无效 ruleId） |
| F-02 | 鉴权链路集成测试缺失 | ✅ 已修复 | AuthModuleMockMvcTest 含 4 个 authChain* 测试：admin 全通 / 低权限 403 / 无 token 401 / `/auth/permissions` 返回对齐 token 权限 |
| F-03 | 端到端 10 步无证据 | ✅ 已修复 | `m7d-completion-report.md` §2 F-03 记录 10 步主链路证据（login→partner 全生命周期→ingest→service 全生命周期→invoke→consumer→quality→billing→stats→system→权限校验），含状态码与返回值 |
| F-04 | 前端边界测试缺失 | ❌ 未修复 | 报告自行降级为"低优遗留"，空列表/加载失败态/表单校验三项仍未补 |
| F-05 | CatalogService 种子数据 | ❌ 未修复（已降级） | 报告标注"上线前移至 SQL 种子"，仍保留在生产行为 |
| F-06 | 前端状态流转断言缺失 | ❌ 未修复（已降级） | 报告以"MockMvc 已覆盖后端状态机"为由降级，前端断言未补 |
| F-07 | FormDialog validate 未捕获 | ✅ 已修复 | `submitForm` 拆分两段 try/catch，validate 失败直接返回 |
| F-08 | 完成报告未输出 | ✅ 已修复 | `m7d-completion-report.md` 7 节完整 + 端到端证据 + M7 总体结论 |

## 2. 测试结果（独立验证，非仅信报告）

```text
mvn test（全 7 模块）→ BUILD SUCCESS，154 测试全绿（0 failures）
  platform-common 21 / platform-gateway 2 / platform-auth 25 / platform-partner 23
  / platform-quality 15 / platform-pipeline 42 / platform-billing 26
npm run test:unit → 11 文件 / 35 用例 全绿
```

> 完成报告称后端 144 测试，实测 154（报告略保守，不影响结论）。新增 MockMvc 69 个（auth 17 + partner 14 + pipeline 15 + quality 9 + billing 14）已实测全绿。

## 3. MockMvc 测试质量核查

| 覆盖维度 | 状态 | 说明 |
|---|---|---|
| 200 正常路径 | ✅ | 各模块 admin token 访问，校验返回体结构（records/id/billNo 等） |
| 401 无 token | ✅ | 各模块列表/写端点无 token 均 isUnauthorized |
| 403 权限不足 | ✅ | viewer token（仅 stats:view）访问越权端点 isForbidden |
| 400 异常路径 | ✅ | partner 状态机非法转移、user 重复用户名、consumer 资源不存在、quality 无效 ruleId |
| invoke 白名单 | ✅ | serviceInvokeIsWhitelistedNoToken 验证 `/invoke` 免 JWT 但签名错返 400 |
| 鉴权链路集成 | ✅ | 4 个 authChain* 端到端 |

测试设计合理：用 `@SpringBootTest` 而非 `@WebMvcTest`（因 RequirePermissionAspect 需 AOP），通过 `application-test.yml` 排除 DataSource/Flyway/Nacos/Redis/Kafka/RabbitMQ 自动配置，内存仓储无外部依赖。

## 4. 端到端证据核查

`m7d-completion-report.md` §2 F-03 记录 10 步主链路：
- 步骤 1-2：login token(695) + 32 权限码 + partner create→interface→submit→approve→admit 全生命周期 ✓
- 步骤 3-4：ingest create + records / service register→define→test→publish→invoke(`{status:ok}`)→logs ✓
- 步骤 5-7：consumer→quota→audit / quality rule→check→issues / billing rule→generate→confirm ✓
- 步骤 8-9：stats dashboard+audit / system users+roles ✓
- 步骤 10：低权限用户 partners→403 + stats→200 权限隔离 ✓

证据含状态码与关键返回值，与 M7-D 任务 D.3 的 10 步要求一一对应。

## 5. 残留问题

### D2-01【低】前端边界测试仍未补（F-04 降级）

- 首轮 F-04 要求补空列表态/加载失败态/表单校验测试，返工未做，报告以"MockMvc 已覆盖后端异常路径"降级为低优。
- 评估：后端异常路径已有 MockMvc 覆盖，前端边界测试确属锦上添花，降级合理。但"加载失败态验证 ElMessage.error"是 M7-D 任务 D.2.2 明确项，未做属偏离。
- **建议**：M7 后续或专项补，不阻塞验收。

### D2-02【低】CatalogService 种子数据仍在生产代码（F-05）

- DEMO 资产仍在 `CatalogService` 无参构造器，改变生产行为。报告已标注上线前移除。
- **建议**：上线前移至 SQL 种子（V010）或测试夹具。

### D2-03【低】PartnerController.detail 异常类型

- 报告偏离说明 §5.2 指出 PartnerController.detail 对不存在资源抛 IllegalArgumentException→500 而非 BusinessException→400。MockMvc 测试规避了此路径（改用状态机测 400）。
- **建议**：改为 `BusinessException("PARTNER-404",...)`，低优。

### D2-04【低】前端状态流转断言未补（F-06 降级）

- 前端测试仍无"点击状态按钮→断言 submitPartner 等调用"。报告以 MockMvc 覆盖后端为由降级。
- **建议**：可后续补，不阻塞。

### D2-05【中/上线前】内存仓储与 invoke secret 遗留（跨阶段）

- 用户/角色内存实现不落表、`/invoke` secret 明文传入——M7-A 遗留，非 M7-D 范围，报告已记入上线前清单。

## 6. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| D.1.1 Controller MockMvc 补齐 | ✅ | 69 个用例覆盖 200/401/403/400 |
| D.1.2 鉴权链路集成测试 | ✅ | 4 个 authChain* 测试 |
| D.1.3 mvn test 回归 | ✅ | 154 全绿 |
| D.2.1 前端页面测试 | ⚠️ | M7-C 已补 4 it，本轮未新增 |
| D.2.2 前端边界测试 | ❌ | 未补（已降级） |
| D.3 端到端 10 步 | ✅ | 证据完整 |
| D.4 bug 修复 | ✅ | 上轮已修，本轮无新增 bug |
| D.5 验收材料 | ✅ | 完成报告落盘 |
| 不新增功能/不重构 | ✅ | 仅测试 + FormDialog 修复 |
| 最小改动 | ✅ | |

## 7. 审查结论

```text
✓ 通过 — M7-D 返工闭环，建议 M7 最终验收通过
```

**理由**：M7-D 首轮三项核心缺陷全部修复且有实测验证：
1. **F-01 Controller MockMvc**：新增 5 个测试类 69 个用例，覆盖 200/401/403/400 全分支，MockMvc 设计合理（`@SpringBootTest`+排除外部依赖+内存仓储），实测全绿。
2. **F-02 鉴权链路集成**：4 个 authChain* 端到端测试，验证 admin 全通/低权限 403/无 token 401/permissions 对齐。
3. **F-03 端到端 10 步**：完成报告记录完整 10 步证据，含状态码与返回值，与任务 D.3 一一对应。
4. **F-07 FormDialog validate** 修复 + **F-08 完成报告** 落盘。

测试经独立实测：后端 154 + 前端 35 全绿（非仅信报告）。MockMvc 覆盖维度完整，鉴权边界（401/403）与异常路径（状态机/重复/不存在/无效 ruleId）均有自动化保护，弥补了 M7-A 审查 F-03 以来的遗留。

**残留均为低优**：前端边界测试（F-04）、种子数据（F-05）、PartnerController.detail 异常类型、前端状态流转断言——均不阻塞验收，可上线前或专项处理。内存仓储/invoke secret 属 M7-A 跨阶段遗留，已记入上线前清单。

## 8. M7 最终验收结论

| 阶段 | 结论 |
|---|---|
| M7-A 后端 Controller + 鉴权 | ✅ 通过 |
| M7-B 前端基础设施 | ✅ 通过 |
| M7-C 前端页面功能化 | ✅ 通过（返工后） |
| M7-D 测试补齐 + 端到端 | ✅ 通过（返工后） |

**M7 最终验收：通过**。

- 46 条 FR 覆盖：11 个 Controller 全端点 + 10 个功能化页面，端到端主链路 10 步走通有据。
- 鉴权验收：JWT + `@RequirePermission` 生效，69 个 MockMvc 401/403 测试 + 端到端权限隔离证据。
- 测试验收：后端 154 + 前端 35 = 189 测试全绿。
- 端到端：10 步主链路 curl 证据完整。
- 遗留：前端边界测试、种子数据清理、内存仓储落表、invoke secret 仓储——均低优或上线前项，不阻塞开发环境验收。

**建议**：可提交 M7 全部改动。上线前需处理：①用户/角色落表（V010+U010）；②invoke apiKey→secret 仓储查找；③CatalogService 种子数据移出生产代码；④PartnerController.detail 异常类型修正。

## 9. 建议提交信息

```text
fix(M7-D): add MockMvc tests, e2e evidence, FormDialog validate fix

- add 5 ModuleMockMvcTest classes (69 cases): 200/401/403/400 coverage
- add application-test.yml per module (exclude DataSource/Redis/Kafka/Nacos)
- add spring-boot-starter-test to parent pom
- FormDialog: split validate/submit try-catch
- add m7d-completion-report.md with 10-step e2e evidence
- verified: mvn test 154 green, npm test 35 green

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：M7-D 二次审查**通过**，**M7 最终验收通过**。M7 全阶段（A/B/C/D）闭环，46 条 FR 覆盖、鉴权生效、189 测试全绿、端到端主链路打通。可提交 M7 改动。上线前处理遗留项（用户落表/invoke secret/种子数据/detail 异常类型）。

---

# Claude Code 审查结果 - fix-01 安全认证（第三轮返工复审）

> 审查阶段：fix-01 第 3 轮返工（F-01 必修 + F-02 建议同轮，最后一次）
> 审查日期：2026-07-16
> 审查范围：`ai/fix-01-security-auth` 分支未提交改动（本轮增量聚焦 F-01/F-02）
> 返工任务单：`tasks/codex-task-fix-01-rework-3.md`
> 前序：原始（8 P1）→ 第 1 轮（1 P1 回归 + 1 相邻 P1）→ 第 2 轮（通过有条件，存活 P2-1/P2-2）→ **本轮**
> 完整报告：`reviews/claude-review-fix-01-rework-3.md`（本节为摘要，详细对抗式反例与逐项核验见完整报告）

## 1. 审查对象（本轮增量）

| 文件 | 改动 |
|---|---|
| `AdvancedAuthService.java` | F-01：`unbindMfa:109-121` 在 `Totp.verify` 后加 `advanceCounter(username, counter)`，失败抛 `AUTH-401 "MFA replay rejected"`，再 `clearMfa` |
| `SsoConfiguration.java` | F-02：改白名单 `Set.of("test","dev")` + 默认拒绝（`activeProfiles.length>0 && allMatch`），`mockEnabled && !mockAllowed` 抛异常 |
| `AdvancedAuthServiceTest.java` | +1 攻击路径测试 `consumedTotpCannotUnbindMfaButNewTotpCan`；扩写 `productionSsoRejects...`（prod/production/default/dev/test 分支） |
| `AdvancedAuthEndpointSecurityTest.java` | 现有 unbind 改用 `Totp.generate(secret, counter+1)` 新窗口 code（配套防重放） |

> RR-1~RR-5 已验收逻辑（challenge 4 段 HMAC/JWT_SECRET fail-fast/cert fail-closed/多层 CA）逐行比对未回归。

## 2. 测试检查（独立实测）

```text
mvn test（全 7 后端模块）-> BUILD SUCCESS，338 测试全绿（0 failures 0 errors）
  common 40(1 skipped) / gateway 2 / auth 49 / partner 37 / pipeline 122 / quality 35 / billing 53
auth 48(round2) -> 49(round3, +1 攻击路径测试)；AdvancedAuthServiceTest 11 -> 12
```

用户"完整回归"声明经独立验证成立。F-01/F-02 为后端 auth 内部改动，前端不受影响。

## 3. 需求满足情况

| 返工项 | 是否满足 | 核查证据 |
|---|---|---|
| **F-01** unbindMfa 加 TOTP counter 防重放（P2-1 必修） | **是** | `unbindMfa` 调 `advanceCounter` 并校验返回，与 `completeChallenge:85`/`confirmMfaBinding:105-106` 三点防重放一致；DB 路径 `mfa_enabled=1` 在 unbind 时仍成立（clearMfa 在后）。攻击路径测试：已消费 code 解绑抛 AUTH-401 且 MFA 仍 bound，新 code 解绑成功 |
| **F-02** SSO mock 不依赖单一 "prod" profile 名（P2-2） | **是** | 白名单 `Set.of("test","dev")` + 默认拒绝；production/prd/默认/空/混合 profile + env 注入一律 fail-fast；test/dev 仍激活 MockSsoAdapter。五分支断言覆盖 |

## 4. 对抗式审查（反例全部反驳，无存活 P1/P2）

- **F-01**：已消费 code 重放解绑 → `advanceCounter` 返回 false 抛 AUTH-401（DB 原子 `WHERE mfa_last_counter<?` + 内存 `synchronized`）；`mfa_enabled=1` 在 unbind 时成立；并发竞态原子拦截；负/溢出 counter 被 `counter<0` 拦截。内存-DB enabled 守卫不一致为**预存在 P3-2**（非本轮引入，对已启用 MFA 的防重放两路径一致）。
- **F-02**：production/prd/默认/空/混合 profile + `SECURITY_SSO_MOCK_ENABLED=true` → 全部 fail-fast；空 profile 被 `length>0` 前置守卫拦截（不致 allMatch 空真值）；profile 为启动期配置非请求可控。

## 5. 范围与边界

| 检查项 | 结果 |
|---|---|
| 敏感文件（.env/证书/k8s-prod） | 未动 ✓ |
| docs/tasks（rework-3 任务单除外） | 未改 ✓ |
| 大型依赖 | 无新增 ✓ |
| 无关重构 | 无 ✓ |
| 超出 F-01/F-02 | 无 ✓ |
| 动 RR-1~RR-5 | 未动 ✓ |
| 既有迁移 V001-V023 | 未改（仅 V024/U024 round2 既有）✓ |

## 6. 审查结论

```text
✓ 通过（无条件） - F-01/F-02 两项 P2 全部修复，攻击路径覆盖，全量回归 338 测试全绿
- 无存活 P1；无存活 P2（round 2 的 P2-1/P2-2 本轮消除）
- RR-1~RR-5 未回归
- 可提交 + PR + 合并 master
- G-S01 仍 BLOCKED；本审查通过不代表生产放行
- P3 项 F-03~F-15 留上线前/专项（任务单 §8.7 明确不纳入本轮）
```

**重要口径**：G-S01 仍 `BLOCKED`，须 PV-SEC PROD_EQ + 安全负责人复核方可解锁，**不签发正式上线批准**；机构 CRL/OCSP 待真实 CA 联调；A4 真实 IAM/SSO 联调待外部规范。

## 7. 返工任务清单（上线前/专项，P3，不阻断本轮）

无新增返工项。P3 延续清单（F-03 sm4Key 分离 / F-04 requiredMfa enabled 守卫 / F-05 审计时序 / F-06 `/auth/**` 白名单收紧 / F-07 多实例共享存储 / F-08 EdDSA / F-09 死代码 / F-10~F-15）详见完整报告 §10，均留上线前/专项。

## 8. 建议提交信息

```text
fix(fix-01): security auth rework round 3 - unbind MFA replay guard + SSO mock whitelist

- F-01: unbindMfa now calls advanceCounter after Totp.verify and rejects
  replayed codes (AUTH-401), aligning with completeChallenge/confirmMfaBinding
- F-02: SsoConfiguration switches to allow-list (test/dev only) with default
  deny; blocks mock under production/prd/default/empty/mixed profiles and
  SECURITY_SSO_MOCK_ENABLED env injection
- tests: add consumedTotpCannotUnbindMfaButNewTotpCan (attack path), expand SSO
  profile coverage; adjust existing unbind to fresh counter+1 code
- verified: mvn test 338 green (0 failures); RR-1~RR-5 untouched
- G-S01 still BLOCKED (not production approval)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**下一步**：fix-01 第三轮返工**通过（无条件）**，**可提交 + PR + 合并 master**。G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核；A4 真实联调待机构 IAM/SSO 规范；P3 项 F-03~F-15 留上线前/专项；**不签发正式上线批准**。
