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
