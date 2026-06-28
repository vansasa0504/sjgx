# 开发进度文档

> 最后更新：2026-06-28
> 项目：金融机构外部数据采集平台（sjgx）
> 当前阶段：P0-02 返工完成，待 Claude Code 复审
> 明天读取此文档后可直接继续开发，无需重新摸排代码

---

## 0. 明天开工必读顺序

1. 本文件（`tasks/dev-progress.md`）—— 总览进度与上下文
2. `reviews/claude-review.md` —— M7-A 审查结论（含遗留问题清单）
3. `tasks/codex-task-M7B-execute.md` —— 下一阶段（M7-B）详细任务
4. `docs/requirements.md` §7 + `tasks/claude-plan.md` §4.5.2 —— 验收标准与接口权威

---

## 1. 项目协作模式

- **Claude Code** = 主控：需求/计划/审查/本次直接实现（Codex 不可用）
- **Codex** = 执行：当前不可用，由 Claude Code 代为实现
- 工作流文件：`CLAUDE.md` / `AGENTS.md` / `docs/` / `tasks/` / `reviews/`
- 禁止修改：`.env`、证书、`docs/`、`tasks/`（本文件除外）、`reviews/`、`k8s/prod/`、`delivery/`、`perf/`、`security/`

## 2. 技术栈

- 后端：Java 17 + Spring Boot 3.2.5 + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0 + MyBatis-Plus 3.5.6 + Nacos + Sentinel
- 前端：Vue3 3.4 + Vite 5 + Pinia 2 + Element Plus 2.7 + ECharts 5.5 + axios + vue-router 4
- 数据库：开发用 MySQL 8.0（docker-compose），生产目标达梦/OceanBase 双适配
- 依赖中间件：MySQL 8.0 / Redis 7 / Nacos 2.3.2 / Kafka / RabbitMQ（均 docker-compose）

## 3. 里程碑进度

| 阶段 | 状态 | 说明 |
|---|---|---|
| M1 基础设施 + 核心链路 | ✅ 已提交 | 脚手架、Gateway、Auth、Partner、Ingest(HTTP+JSON) |
| M2 核心管道 | ✅ 已提交 | 多协议/格式、服务、消费方、目录 |
| M3 质量 + 存储 | ✅ 已提交 | 六维规则、ETL、分级存储、集市、生命周期 |
| M4 计费 + 统计 + 前端骨架 | ✅ 已提交 | Billing、Stats、Vue3 骨架页面 |
| M5 集成测试 + 性能 + 安全 + 国产化 | ✅ 已提交 | E2E 测试、JMeter、XssFilter、national-db、k8s/dev |
| M6 稳定性 + 文档 + 验收 | ✅ 已提交 | 稳定性方案、故障演练脚本、五类文档、验收报告 |
| **M7-A 后端 Controller + 鉴权** | ✅ **本会话完成（未提交）** | 9 个新 Controller + JWT filter + 权限 AOP |
| M7-B 前端基础设施 | ⏳ 待开始 | API client 分层、auth store、路由守卫、共享组件 |
| M7-C 前端页面功能化 | ⏳ 待开始 | 10 个骨架页面 A→C |
| M7-D 测试 + 端到端回归 | ⏳ 待开始 | MockMvc 补齐 + 主链路验证 |

---

## 4. M7-A 本会话完成内容（未提交）

### 4.1 鉴权基础设施（platform-common，全模块复用）

新增 `platform-common/src/main/java/com/platform/common/security/`：
- `RequirePermission.java` —— 权限注解（从 auth 移到 common）
- `JwtAuthFilter.java` —— JWT 过滤器，放行 `/auth/**`、`/actuator/health`、`/api/v1/services/*/invoke`，其余校验 token，失败返 401
- `RequirePermissionAspect.java` —— AOP 校验权限码，缺失抛 AUTH-403
- `CommonSecurityConfiguration.java` —— JwtUtil/Filter/Aspect bean + FilterRegistration
- `PermissionCodes.java` —— 31 个权限码全集

新增 `platform-common/src/main/java/com/platform/common/config/`：
- `JacksonConfiguration.java` —— 字段可见性（领域对象用 `id()` 风格访问器，Jackson 默认无法序列化）

### 4.2 Controller 补齐（9 新 + 2 扩展）

| 模块 | Controller | 基路径 | 端点数 |
|---|---|---|---|
| auth | AuthController（扩展） | /auth | +permissions, +all-permissions |
| auth | UserController（新） | /users | list/create/update |
| auth | RoleController（新） | /roles | list/create/updatePermissions |
| auth | PermissionController（新） | /permissions | list |
| partner | PartnerController（扩展） | /api/v1/partners | 12 端点全生命周期 |
| partner | ConsumerController（新） | /api/v1/consumers | register/list/detail/quota/events/audit/logs |
| pipeline | IngestController（扩展） | /api/v1/ingest/tasks | 10 端点 |
| pipeline | DataServiceController（新） | /api/v1/services | register/list/detail/define/test/publish/offline/logs/invoke |
| pipeline | CatalogController（新） | /api/v1/catalog | list/search/meta/preview/apply/approve |
| quality | QualityController（新） | /api/v1/quality | rules CRUD/checks/issues/reports/scores |
| billing | BillingController（新） | /api/v1/billing | rules CRUD/bills generate+confirm+dispute |
| billing | StatsController（新） | /api/v1/stats | dashboard/reports/audit |

### 4.3 关键修复（本会话过程问题）

1. **`maven.compiler.parameters=true`**（父 pom）—— Spring Boot 3 解析 `@RequestParam` 参数名必需
2. **JacksonConfiguration 字段可见性**—— 解决 Partner/Consumer 等领域对象序列化失败
3. **V009 SQL 兼容**（M6 遗留，本会话修）—— 移除 `ADD COLUMN IF NOT EXISTS`/`CREATE INDEX IF NOT EXISTS`（MySQL 8.x 不支持）
4. **flyway-mysql 依赖**（父 pom）—— Flyway 9.22.3 支持 MySQL 8 需要
5. **docker-compose MySQL 8.4→8.0**—— Flyway 9.22.3 不兼容 8.4
6. **F-01 actuator 收紧**—— 白名单 `/actuator/**` → `/actuator/health`，metrics/info 需鉴权
7. **F-02 异常日志**—— `GlobalExceptionHandler.handleThrowable` 加 `LOG.error` 记录堆栈

### 4.4 测试

- `mvn test`（8 模块）BUILD SUCCESS
- 新增：JwtAuthFilterTest(6)、RequirePermissionAspectTest(3)、9 个 Controller 直接调用单测
- 既有测试全部回归通过

### 4.5 启动验证（已实测）

- 6 服务 + gateway 全 UP
- admin 登录 → token
- 5 个写操作（partner/service/quality/billing/consumer 创建）全 200
- 8 个列表端点全 200
- 无 token → 401
- 低权限用户 viewer → /api/v1/partners 返 403，/stats/dashboard 返 200
- actuator/health 无 token 200，actuator/metrics 无 token 401

---

## 5. 当前运行环境（明天可能已关闭）

### 5.1 服务地址

| 服务 | 地址 | 端口 |
|---|---|---|
| 前端 | http://localhost:5173/ | 5173 |
| Gateway | http://localhost:8080 | 8080 |
| Auth | http://localhost:8081 | 8081 |
| Partner | http://localhost:8082 | 8082 |
| Pipeline | http://localhost:8083 | 8083 |
| Quality | http://localhost:8084 | 8084 |
| Billing | http://localhost:8085 | 8085 |
| Nacos 控制台 | http://localhost:8848/nacos | 8848 |

### 5.2 登录

```
POST http://localhost:8080/auth/login
Content-Type: application/json
{"username":"admin","password":"admin123"}
```

返回 token 后，调用其他接口加 `Authorization: Bearer <token>`。

### 5.3 依赖中间件（docker-compose）

```bash
docker compose up -d mysql redis nacos   # 核心
docker compose up -d                      # 全部（含 kafka/rabbitmq/sftp/minio/es）
```

### 5.4 启动命令（明天重启用）

```bash
cd /e/project/sjgx

# 1. 启动依赖
docker compose up -d mysql redis nacos

# 2. 确保 MySQL schema 已迁移（V001~V009）
docker exec sjgx-mysql-1 mysql -uroot -proot -e "DROP DATABASE IF EXISTS sjgx; CREATE DATABASE sjgx;"
for f in db/migration/V00*.sql; do docker exec -i sjgx-mysql-1 mysql -uroot -proot sjgx < "$f"; done

# 3. 启动后端服务（禁用 flyway，schema 已手动迁移；禁用 nacos discovery，gateway 用静态路由）
ARGS="--spring.flyway.enabled=false --spring.cloud.nacos.discovery.enabled=false"
mvn -pl platform-auth spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 $ARGS" > /tmp/auth.log 2>&1 &
mvn -pl platform-partner spring-boot:run -Dspring-boot.run.arguments="--server.port=8082 $ARGS" > /tmp/partner.log 2>&1 &
mvn -pl platform-pipeline spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 $ARGS" > /tmp/pipeline.log 2>&1 &
mvn -pl platform-quality spring-boot:run -Dspring-boot.run.arguments="--server.port=8084 $ARGS" > /tmp/quality.log 2>&1 &
mvn -pl platform-billing spring-boot:run -Dspring-boot.run.arguments="--server.port=8085 $ARGS" > /tmp/billing.log 2>&1 &
# 等 40 秒
mvn -pl platform-gateway spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --spring.cloud.nacos.discovery.enabled=false" > /tmp/gateway.log 2>&1 &

# 4. 启动前端
cd platform-ui && npm run dev > /tmp/platform-ui.log 2>&1 &

# 5. 停止服务（Windows Git Bash）
for p in 8080 8081 8082 8083 8084 8085 5173; do
  pid=$(netstat -ano 2>/dev/null | grep ":$p " | grep LISTENING | awk '{print $5}' | head -1)
  [ -n "$pid" ] && taskkill //PID $pid //F
done
```

### 5.5 测试命令

```bash
mvn test                                    # 后端全量
mvn test -pl platform-common                # 单模块
cd platform-ui && npm run test:unit         # 前端
```

---

## 6. 遗留问题清单（来自 reviews/claude-review.md §10）

### 已修复（本会话）
- ✅ F-01 actuator 全放行 → 收紧为仅 health
- ✅ F-02 异常无日志 → 加 LOG.error

### 待 M7-D 修复（中等）
| 编号 | 问题 | 优先级 |
|---|---|---|
| F-03 | Controller 缺 401/403 MockMvc 测试 | 中 |

### 待上线前修复（低）
| 编号 | 问题 |
|---|---|
| F-04 | billing 缺 `GET /api/v1/billing/stats` 端点 |
| F-05 | catalog preview 为桩（返回空 sample） |
| F-06 | consumer logs 返回空（partner 模块无调用日志仓储） |
| F-07 | 用户/角色内存实现，需落 t_user/t_role/t_permission（+V010/U010） |
| F-08 | `/invoke` 的 secret 由调用方 body 明文传入，需补 apiKey→secret 仓储查找 |

### 偏离说明（已记入审查）
1. 用户/角色内存实现不落表
2. catalog preview / consumer logs 为桩
3. billing 缺 /stats 端点
4. /invoke secret 明文（demo）
5. Controller 测试用直接调用单测，未用 MockMvc

---

## 7. 下一步：M7-B 前端基础设施

任务清单：`tasks/codex-task-M7B-execute.md`（可全文复制粘贴执行）

### M7-B 核心交付

1. **API client 重构**（`platform-ui/src/api/`）
   - `client.ts`：axios 实例（baseURL `/api/v1`，token 拦截器，401 跳登录）
   - 按模块分文件：`auth.ts`/`partner.ts`/`consumer.ts`/`ingest.ts`/`service.ts`/`catalog.ts`/`quality.ts`/`billing.ts`/`stats.ts`/`system.ts`
   - 函数签名对齐 M7-A 端点（见本文件 §4.2）

2. **auth store 重构**（`stores/auth.ts`）
   - login 接 `/auth/login`，存 token
   - fetchPermissions 接 `/auth/permissions`
   - refresh/logout
   - 登录后自动 fetchPermissions

3. **路由守卫**（`router/index.ts`）
   - 无 token 跳 /login
   - 有 token 但 permissions 为空先 fetchPermissions
   - meta.permission 真实校验，不满足跳 /403
   - 新增 /403、/404 页面

4. **共享组件**（`components/`）
   - `PageTable.vue`：分页表格（columns/fetchData/filters）
   - `FormDialog.vue`：表单弹窗（title/fields/submit）
   - `StatusTag.vue`：状态标签

5. **布局**（`layouts/ConsoleLayout.vue`）
   - 左侧菜单按权限码过滤
   - 顶栏用户名/登出

### M7-B 完成判定
- API client 按模块分文件，签名对齐 M7-A
- auth store 接真实 permissions
- 路由守卫真实校验
- 共享组件有 Vitest 测试
- `npm run test:unit` 全绿
- 业务页面内容保持原样（C 阶段改）

---

## 8. M7-B/C/D 后续阶段

| 阶段 | 任务文件 | 核心产出 |
|---|---|---|
| M7-B | `tasks/codex-task-M7B-execute.md` | 前端基础设施 |
| M7-C | `tasks/codex-task-M7C-execute.md` | 10 个骨架页面功能化（A→C） |
| M7-D | `tasks/codex-task-M7D-execute.md` | MockMvc 测试 + 端到端回归 + bug 修复 |

每阶段完成后 Claude Code 审查 `git diff` + 测试，通过后才进下一阶段。

---

## 9. 关键架构事实（避免明天踩坑）

1. **所有模块** `@SpringBootApplication(scanBasePackages={"com.platform.<module>","com.platform.common"})` —— common 的 @Configuration/Filter/Aspect 自动被扫描
2. **无 Spring Security filter chain**（只有 spring-security-crypto/core）—— 用普通 servlet Filter + FilterRegistrationBean
3. **JwtUtil** 现在是 bean（`CommonSecurityConfiguration` 注册），各模块注入即可
4. **领域对象**用 `id()` 风格访问器 —— 靠 `JacksonConfiguration` 字段可见性序列化，新增领域对象无需加 `@Data`
5. **所有仓储当前内存实现**（`InMemory*Repository`），无 MyBatis-Plus mapper
6. **Gateway 静态路由**（不依赖 Nacos discovery）—— `${PLATFORM_*_URI:http://localhost:80xx}`
7. **启动必须禁用 flyway**（`--spring.flyway.enabled=false`）—— schema 手动迁移，避免 flyway-mysql 兼容问题
8. **启动必须禁用 nacos discovery**（`--spring.cloud.nacos.discovery.enabled=false`）—— gateway 用静态路由，无需注册
9. **父 POM 非 spring-boot-starter-parent** —— 需手动配 `maven.compiler.parameters=true`、`maven-surefire-plugin`
10. **Windows 环境** —— `fuser` 不可用，用 `netstat -ano | grep :PORT | grep LISTENING | awk '{print $5}'` + `taskkill //PID xxx //F`

---

## 10. 注意事项

- M7-A 改动**尚未提交**，工作区有 85 个文件改动（含 M5/M6 遗留 pom/yml）。建议明天先 review 是否提交 M7-A，再开始 M7-B
- 前端 `platform-ui` 当前只有 LoginView 接真实 API，其余 10 页面是骨架——这正是 M7-B/C 要解决的
- `vite.config.ts` 已配 dev proxy（`/auth`、`/api` → `http://localhost:8080`）
- 前端登录已可用：`http://localhost:5173/`，admin/admin123
- 后端 Flyway 与 MySQL 8.4 兼容问题已通过降级 8.0 + flyway-mysql 解决，但启动仍禁用 flyway（双保险）

---

## 11. P0-01 迁移返工记录（2026-06-28）

### 11.1 Flyway checksum 处置

P0-01 修改了 `db/migration/V001~V010` 的内容。任何已经执行过旧版迁移的开发库都会出现 Flyway checksum 偏差。

开发库处理方式二选一：

```bash
# 方式 A：开发库无保留价值时重建
docker exec sjgx-mysql-1 mysql -uroot -proot -e "DROP DATABASE IF EXISTS sjgx; CREATE DATABASE sjgx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 方式 B：保留已有库并接受本次脚本为新基线
mvn -pl platform-common flyway:repair -Dflyway.url=jdbc:mysql://localhost:3306/sjgx -Dflyway.user=root -Dflyway.password=root -Dflyway.locations=filesystem:db/migration
mvn -pl platform-common flyway:validate -Dflyway.url=jdbc:mysql://localhost:3306/sjgx -Dflyway.user=root -Dflyway.password=root -Dflyway.locations=filesystem:db/migration
```

说明：Windows PowerShell 下 `-Dflyway.url=...` 建议整体加引号，避免冒号被解析异常。

### 11.2 Seed 数据衔接

旧 `U010__seed_data.sql` 已删除，因为它不是 rollback，且包含错误表名 `t_data_catalog_item`、MySQL `ON DUPLICATE KEY UPDATE` 和 demo `api-key/secret` 明文。P0-03 必须以正确方式重新提供开发/测试初始化数据：

- admin 用户和必要权限码：写入 `t_user/t_user_permission`，`permission_code` 按 `VARCHAR(128)`。
- 测试 API Key：写入 `t_api_credential`，不得使用生产真实密钥；生产 secret 后续由 P0-04 密文化。
- 示例目录：如仍需要 E2E 种子，必须写入 `t_data_catalog`，不再使用 `t_data_catalog_item`。
- `t_user.status` 为 `NOT NULL`，JDBC 创建用户必须写入状态，或补迁移默认值。

---

## 12. P0-02 国产库兼容返工记录（2026-06-28）

### 12.1 方言策略与脚本位置

P0-02 采用方言拆分策略：

- `db/migration/`：MySQL 8.0 基线，同时供 OceanBase MySQL 模式复用。
- `db/migration-dm/`：达梦 DM8 迁移脚本，版本号、表名、索引名与基线保持一致。
- 达梦目录只做最小类型替换：`TEXT -> CLOB`，`TINYINT -> SMALLINT`。

`db/migration-dm/` 刻意不放在 `db/migration/` 子目录下，因为 Flyway filesystem location 会递归扫描子目录；放在外层可以避免默认 MySQL/OceanBase location 扫到重复 V001~V010。

### 12.2 三库迁移证据

已补自动化测试：`platform-common/src/test/java/com/platform/common/db/MigrationDialectCompatibilityTest.java`。

测试覆盖：

- MySQL/OceanBase 基线：H2 MySQL mode 执行 `filesystem:../db/migration`，Flyway 成功应用 V001~V010，并通过 contract CRUD。
- 达梦模拟：H2 标准模式执行 `filesystem:../db/migration-dm`，Flyway 成功应用 V001~V010，并通过同一组 contract CRUD。
- 静态方言守护：扫描两套 V*.sql，断言不含 `AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`ON DUPLICATE KEY UPDATE`、`JSON_`、手写 `LIMIT`；达梦目录额外断言不含 `TEXT`/`TINYINT`。

关键测试输出摘录：

```text
MigrationDialectCompatibilityTest
- mysql_ob_baseline: Successfully applied 10 migrations to schema "PUBLIC", now at version v010
- dm_baseline: Successfully applied 10 migrations to schema "PUBLIC", now at version v010
- Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
```

本地全量回归已执行：

```text
Command: mvn test
Finished: 2026-06-28 12:53:18 +08:00
Reactor: sjgx-platform / platform-common / platform-gateway / platform-auth / platform-partner / platform-quality / platform-pipeline / platform-billing all SUCCESS
Total time: 01:33 min
Result: BUILD SUCCESS
```

### 12.3 未实测说明

当前开发环境未提供合法可用的达梦 DM8 / OceanBase 实例或镜像；本次未宣称真实达梦/OceanBase 实测通过。

已完成的验证范围：

- MySQL 8.0 基线脚本保持不变，P0-01 已验证，P0-02 通过 H2 MySQL mode 继续回归。
- OceanBase MySQL 模式暂复用 `db/migration/`，按 MySQL 兼容路径做自动化模拟验证。
- 达梦使用 `db/migration-dm/`，按 H2 标准模式模拟验证迁移和 contract CRUD。

上线前必须在真实环境补齐以下验证，不能以 H2 模拟替代上线门禁：

1. 达梦 DM8 空库执行：

```bash
mvn -pl platform-common flyway:migrate \
  -Dflyway.url=jdbc:dm://<host>:<port>/<schema> \
  -Dflyway.user=<user> \
  -Dflyway.password=<password> \
  -Dflyway.locations=filesystem:db/migration-dm
```

2. 达梦 DM8 执行 `flyway:validate`，并跑核心 contract CRUD：`t_user`、`t_api_credential`、`t_service_invoke_log`、`t_billing_rule`、`t_raw_data` 插入/查询/更新。
3. 达梦重点核对 `CLOB` 字段写入/读取语义：`t_raw_data.payload`、`t_ingest_task.mapping_config`、`t_data_catalog.field_definitions`、`t_audit_log.detail`。
4. 达梦重点核对 `SMALLINT` 标志位：`t_api_credential.enabled`、`t_quality_rule.enabled`、`t_quality_weight.enabled`、`t_storage_policy.enabled`。
5. OceanBase MySQL 模式空库执行：

```bash
mvn -pl platform-common flyway:migrate \
  -Dflyway.url=jdbc:oceanbase://<host>:<port>/<schema> \
  -Dflyway.user=<user> \
  -Dflyway.password=<password> \
  -Dflyway.locations=filesystem:db/migration
```

6. OceanBase 执行 `flyway:validate`，并跑同一组 contract CRUD。
7. OceanBase 重点核对 MySQL 模式下 `TEXT`、`TINYINT`、`TIMESTAMP DEFAULT CURRENT_TIMESTAMP` 行为是否与 MySQL 8.0 一致。
8. 三库均需核对索引名长度、唯一索引行为、`DECIMAL(12,6)/(16,4)/(20,4)` 精度、`DATE`/`TIMESTAMP` 写入读取，以及 V009 新增索引是否创建成功。

### 12.4 低优协调项

- `db.type=${DB_TYPE:MYSQL}` 当前未被分页/DAO 代码消费，因为项目现阶段分页仍是内存分页；保留为 P0-03 Repository 落库后接入 MyBatis-Plus 方言配置的预留项，避免本次为文档返工扩大代码改动。
- `db/rollback/` 目录存在孤岛脚本（如 `db/rollback/U009__perf_and_compat.sql`），与 P0-01 审查 D-2 的 U0xx 目录统一建议一并处理。本次 P0-02 不删除/迁移 rollback 文件，避免影响回滚约定。

---

## 13. P0-03 核心 Repository 落库返工记录（2026-06-28）

### 13.1 返工依据

依据 `reviews/claude-review-P0-03.md` 审查报告，P0-03 初版存在 12 项问题（RW-1~RW-12），其中 RW-1（nextId 并发不安全）和 RW-2（bootstrapAdmin 默认弱密码+吞异常）为阻断级。

### 13.2 返工完成项

| 编号 | 问题 | 状态 | 说明 |
|---|---|---|---|
| RW-1 | nextId 并发不安全 | ✅ 已修复 | 新增 `IdGenerator`（platform-common），用 `AtomicLong` + 初始化从 MAX(id) 读取替代 `SELECT MAX(id)+1`；auth/partner/ingest/service/consumer/catalog/quality/billing 全部模块替换 |
| RW-2 | bootstrapAdmin 默认弱密码+吞异常 | ✅ 已修复 | 移除 `admin123` 默认值，未设 `AUTH_BOOTSTRAP_ADMIN_PASSWORD` 环境变量（或系统属性）时抛 `IllegalStateException` 拒绝启动；移除 `catch(Exception ignored)` 吞异常 |
| RW-3 | auth/partner/ingest/service/consumer/quality jdbc 路径零测试 | ✅ 已修复 | 新增 5 个 JDBC 路径集成测试：AuthJdbcRepositoryTest(8)、PartnerJdbcRepositoryTest(6)、IngestServiceJdbcTest(4)、QualityJdbcRepositoryTest(3)、IdGeneratorTest(4) |
| RW-4 | AuthService 未拆 UserRepository/RoleRepository 接口 | ⚠️ 未修复 | 保留 AuthService 内 JdbcTemplate；当前双写机制可工作，接口拆分留后续重构 |
| RW-5 | 缺重启恢复测试 | ✅ 已修复 | PartnerJdbcRepositoryTest.jdbcRestartRecovery + IngestServiceJdbcTest.restartRecoveryDataSurvivesNewServiceInstance |
| RW-6 | 缺 jdbc profile MockMvc 回归 | ⚠️ 未修复 | MockMvc 测试仍走 memory 路径；jdbc 路径由单元级 JDBC 测试覆盖 |
| RW-7 | 双写一致性核查 | ✅ 已核查 | 所有读路径在 useDb() 为 true 时走 DB，内存 map 仅用于测试回退 |
| RW-8 | IngestService INSERT/UPDATE 列不对称 | ✅ 已修复 | persistTask 改为 upsert（先 UPDATE，affected==0 时 INSERT 全字段），INSERT/UPDATE 均包含 sync_mode/schedule_cron/mapping_config/rule_config |
| RW-9 | profile 切换机制 | ⚠️ 保留现状 | 采用 `@Autowired(required=false) JdbcTemplate` + `jdbcTemplate != null` 判断替代 `@Profile`；测试排除 `DataSourceAutoConfiguration` 实现 memory 回退 |
| RW-10 | 缺证据文档 | ✅ 已修复 | 本章节即为证据文档 |
| RW-11 | U011 孤岛 | ✅ 已修复 | 删除 `db/rollback/U011__billing_rule_package_allowance.sql`，统一使用 `db/migration/U011` |
| RW-12 | catalog/demo API Key 种子 | ⚠️ 未修复 | 留 P0-10 E2E 前置处理 |

### 13.3 落库清单

| 模块 | JDBC 表 | 实现方式 | JDBC 路径测试 |
|---|---|---|---|
| auth | t_user/t_role/t_permission/t_user_permission/t_role_permission | AuthService 内 JdbcTemplate + IdGenerator | AuthJdbcRepositoryTest(8) |
| partner | t_partner/t_partner_interface/t_partner_event | PartnerService 双写 + IdGenerator | PartnerJdbcRepositoryTest(6) |
| partner.consumer | t_consumer/t_consumer_quota/t_consumer_event | ConsumerService 双写 + IdGenerator | PartnerJdbcRepositoryTest(6) |
| pipeline.ingest | t_ingest_task | IngestService 双写 + IdGenerator | IngestServiceJdbcTest(4) |
| pipeline.service | t_data_service | DataServiceManager 双写 + IdGenerator | — |
| pipeline.catalog | t_data_catalog | CatalogService + IdGenerator | — |
| quality | t_quality_rule | JdbcQualityRuleRepository + IdGenerator | QualityJdbcRepositoryTest(3) |
| billing.bill | t_bill | JdbcBillRepository + IdGenerator | RepositoryContractTest(3) |
| billing.rule | t_billing_rule | JdbcBillingRuleRepository + IdGenerator | RepositoryContractTest(3) |
| billing.stats | t_stats_snapshot | JdbcStatsSnapshotRepository + IdGenerator | RepositoryContractTest(3) |
| common.audit | t_audit_log | JdbcAuditLogRepository（已有） | — |

### 13.4 测试结果

```text
mvn test（全量 8 模块）

platform-common:   Tests run: 28, Failures: 0, Errors: 0 (含 IdGeneratorTest 4)
platform-gateway:  Tests run: 2,  Failures: 0, Errors: 0
platform-auth:     Tests run: 33, Failures: 0, Errors: 0 (含 AuthJdbcRepositoryTest 8)
platform-partner:  Tests run: 29, Failures: 0, Errors: 0 (含 PartnerJdbcRepositoryTest 6)
platform-quality:  Tests run: 18, Failures: 0, Errors: 0 (含 QualityJdbcRepositoryTest 3)
platform-pipeline: Tests run: 46, Failures: 0, Errors: 0 (含 IngestServiceJdbcTest 4)
platform-billing:  Tests run: 30, Failures: 0, Errors: 0 (含 RepositoryContractTest 3)
总计:              Tests run: 186, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 13.5 重启恢复证据

- `PartnerJdbcRepositoryTest.jdbcRestartRecovery`：创建 Partner → 新建 PartnerService（同一 JdbcTemplate）→ find 返回数据
- `IngestServiceJdbcTest.restartRecoveryDataSurvivesNewServiceInstance`：创建 IngestTask（含 syncMode/cron/mapping/qualityRules）→ 新建 IngestService → detail 返回完整数据

### 13.6 IdGenerator 并发安全证据

`IdGeneratorTest.nextIdIsConcurrencySafe`：20 线程 × 50 次 = 1000 个 ID 全部唯一，无碰撞。

### 13.7 未实测说明

- jdbc profile MockMvc 回归（RW-6）未实现：MockMvc 测试仍走 memory 路径（排除 DataSourceAutoConfiguration）。jdbc 路径由各模块 JDBC 单元测试覆盖 CRUD/查询/状态流转。
- AuthService UserRepository/RoleRepository 接口拆分（RW-4）未做：保留当前 AuthService 内 JdbcTemplate 实现，功能完整，留后续重构。
- catalog/demo API Key 种子（RW-12）未做：留 P0-10 E2E 前置处理。
- 达梦/OceanBase 真实环境 CLOB/SMALLINT 语义验证未做（沿用 P0-02 §12.3 未实测说明）。

---

## 14. P0-04 API 凭证安全开发记录（2026-06-28）

### 14.1 完成项

| 项 | 状态 | 说明 |
|---|---|---|
| invoke 移除 secret 入参 | 已完成 | `InvokeRequest` 不包含 `secret`，调用链按 `apiKey` 服务端查凭证并验签 |
| secret 密文存储 | 已完成 | `t_api_credential` 新增 `secret_cipher`、`secret_hash`、`status`、`rotated_from`；旧 `secret` 列仅写固定占位 |
| 凭证管理 API | 已完成 | 新增创建、列表、轮换、禁用端点，创建/轮换仅当次返回明文 secret |
| 禁用与轮换 | 已完成 | 轮换会禁用旧 key，新 key 生效，并写入 `rotated_from`；禁用后调用拒绝 |
| 前端入口 | 已完成 | ServiceView 增加凭证管理入口，支持创建、轮换、禁用 |
| P0-04 审查返工 | 已完成 | 修复 RW-1~RW-3：生产密钥强制、rotated_from 写入、JDBC 轮换/禁用/list 测试 |

### 14.2 测试结果

```text
mvn test -pl platform-pipeline -am "-Dspring.profiles.active=jdbc"

platform-common:   Tests run: 28, Failures: 0, Errors: 0
platform-quality:  Tests run: 18, Failures: 0, Errors: 0
platform-pipeline: Tests run: 57, Failures: 0, Errors: 0
BUILD SUCCESS

npm run test:unit
Test Files: 11 passed
Tests:      35 passed
```

### 14.3 安全验证

- `ApiCredentialRepositoryJdbcTest.jdbcStorageDoesNotPersistPlainSecret` 直接查询 `t_api_credential`，断言旧 `secret` 列为固定占位，`secret_cipher` 和 `secret_hash` 不包含明文 secret。
- `ApiCredentialRepositoryJdbcTest` 补充 JDBC 轮换、禁用、list 过滤、hash 篡改拒绝、响应/日志/异常不含明文 secret 验证。
- `DataServiceManagerTest` 覆盖正确签名通过、错误签名拒绝、时间戳过期拒绝、nonce 重放拒绝。
- `DataServiceManagerTest.productionProfileRequiresConfiguredCredentialSm4Key` 覆盖生产 profile 未配置 `API_CREDENTIAL_SM4_KEY` 时拒绝启动。
- `DataServiceManagerTest.rotationDisablesOldKeyAndDisableRejectsCurrentKey` 覆盖旧 key 拒绝、新 key 通过、禁用后拒绝。
- MockMvc 覆盖凭证创建端点 200/401/403，以及 invoke 不传 `secret` 仍可工作。

### 14.4 变更说明与风险

- `POST /api/v1/services/{serviceCode}/invoke` 为破坏性变更：消费方请求体不再传 `secret`，只传 `consumerCode/apiKey/timestamp/nonce/params/signature`。
- `API_CREDENTIAL_SM4_KEY` 未配置时仅非生产 profile 使用本地开发默认值；生产 profile（`prod`/`production`）缺失该变量会拒绝启动。
- V012 会将旧 `secret` 列改为固定占位；若环境中已有旧明文凭证，需要迁移后重新创建新凭证。
- 当前未接入真实 KMS，符合 P0-04 范围约束，KMS 留后续任务。

---

## 15. P0-05 调用日志事实源开发记录（2026-06-28）

### 15.1 前置与口径

- P0-04 已提交：`f10ca229 feat(P0-04): secure API credentials with ciphertext storage and server-side lookup`。
- 本次基于 `ai/p0-invoke-log` 开发，任务口径更新为 V013/U013，避免与 P0-04 的 V012 冲突。

### 15.2 完成项

| 项 | 状态 | 说明 |
|---|---|---|
| `t_service_invoke_log` 补字段 | 已完成 | V013/U013 与 DM 版本补 `trace_id/partner_code/api_key/request_hash/error_code/error_message` 和索引 |
| 统一 JDBC 仓储 | 已完成 | 新增 `JdbcServiceInvokeLogRepository`，作为 pipeline/partner/billing 共用事实源 |
| 成功/失败调用写日志 | 已完成 | `DataServiceManager.invoke` 成功/失败均写入，支持 `X-Trace-Id` 透传或自动生成 |
| request_hash | 已完成 | 凭证查到后用 secret 做 HMAC-SHA256；鉴权前失败回退 SHA-256，不存明文 params |
| 消费方日志查询 | 已完成 | `ConsumerController.logs` 改为按 `consumer_code` 查事实表分页 |
| 服务日志查询 | 已完成 | `DataServiceController.logs` 通过 `DataServiceManager` 读取 JDBC/内存日志源 |
| billing/stats 衔接 | 已完成 | 账单生成和 XXL-Job 日志供应源可从事实表取数；stats report 也可基于事实表输出 |
| 前端列对齐 | 已完成 | ConsumerView/ServiceView 日志列增加 trace、耗时、响应大小、request_hash、错误 |

### 15.3 测试结果

```text
mvn test -pl platform-billing -am "-Dspring.profiles.active=jdbc"

platform-common:   SUCCESS
platform-partner:  SUCCESS
platform-quality:  SUCCESS
platform-pipeline: SUCCESS
platform-billing:  SUCCESS
Total: Tests run: 31, Failures: 0, Errors: 0（billing 模块汇总；-am 已回归依赖模块）
BUILD SUCCESS

mvn test "-Dspring.profiles.active=jdbc"
Reactor: sjgx-platform / platform-common / platform-gateway / platform-auth / platform-partner / platform-quality / platform-pipeline / platform-billing all SUCCESS
Total time: 02:13 min
BUILD SUCCESS

npm run test:unit
Test Files: 11 passed
Tests:      35 passed
```

### 15.4 关键验证

- `JdbcServiceInvokeLogRepositoryTest`：保存日志并按 service/consumer/status 查询。
- `DataServiceManagerTest`：成功日志含 `traceId/requestHash/responseSize`；失败日志含 `traceId/requestHash/errorCode/status`。
- `ConsumerControllerTest`：standalone MockMvc 验证 `/api/v1/consumers/{id}/logs` 从事实表返回非空分页。
- `BillingControllerTest`：有 JDBC 日志仓储时，账单生成不依赖请求体 logs，而从事实表聚合。
- `BillingGovernanceTest`：V013 迁移补字段可执行，`TRACE_ID/REQUEST_HASH` 存在。

### 15.5 风险与后续

- `JdbcServiceInvokeLogRepository.findAll()` 当前为最小实现后在内存分页，适合 P0 功能闭环；大表高并发分页优化留后续性能任务。
- trace_id 已进入调用日志，审计防篡改与更完整的 audit 链路仍留 P0-08。
## 16. P0-05 返工记录（RW-1~RW-5，2026-06-28）

依据 `reviews/claude-review-P0-05.md` §8 返工清单，修复 RW-1~RW-5：

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| RW-1 | request_hash 算法不一致（成功 HMAC/失败 SHA-256） | 统一为 `sha256(body)`，与 secret 解耦；删除 `hmacSha256` 方法 | `requestHashIsConsistentBetweenSuccessAndFailure`：同一 body 成功/失败 hash 相等 |
| RW-2 | partner_code 列永远为空 | 标注暂留 null（凭证/服务定义未关联 partner，留 P0-07 补充），`writeInvokeLog` 加注释 | — |
| RW-3 | logs() 全表内存分页 | `findByConsumer`/`findByService` 改 SQL 层 `WHERE + LIMIT/OFFSET + ORDER BY + COUNT`；`DataServiceManager.logs` 接 `logWriter.findByService`；`findAll` 保留给聚合 | `JdbcServiceInvokeLogRepositoryTest` 覆盖 |
| RW-4 | localMirror 无界增长 | `AsyncInvokeLogWriter.write` 改 if/else：repository 非 null 时只落库，null 时才写 localMirror | — |
| RW-5 | 聚合一致性多调用测试缺失 | 新增 `aggregationMatchesMultipleInvokes`：写 3 条日志 → 账单金额 = 3 × 单价 | `BillingControllerTest` |

### 16.1 测试结果

```text
mvn test "-Dspring.profiles.active=jdbc"
platform-common:   29 tests PASS
platform-gateway:  2 tests PASS
platform-auth:     33 tests PASS
platform-partner:  30 tests PASS
platform-quality:  18 tests PASS
platform-pipeline: 60 tests PASS (含 RW-1 hash 一致性)
platform-billing:  32 tests PASS (含 RW-5 多调用聚合)
BUILD SUCCESS

npm run test:unit
11 files / 35 tests PASS
```

### 16.2 已知遗留（低优，不阻断合入）

- RW-6 trace_id 贯穿 audit：留 P0-08。
- `findAll()` 仍为全表读取，仅用于 billing/stats 聚合，非查询端点；大表优化留 P2-01。
- partner_code 填充留 P0-07 catalog-application。

## 16. P0-05 返工记录（RW-1~RW-5，2026-06-28）

依据 `reviews/claude-review-P0-05.md` §8 返工清单，修复 RW-1~RW-5：

| 编号 | 问题 | 修复 | 测试 |
|---|---|---|---|
| RW-1 | request_hash 算法不一致（成功 HMAC/失败 SHA-256） | 统一为 `sha256(body)`，与 secret 解耦；删除 `hmacSha256` 方法 | `requestHashIsConsistentBetweenSuccessAndFailure`：同一 body 成功/失败 hash 相等 |
| RW-2 | partner_code 列永远为空 | 标注暂留 null（凭证/服务定义未关联 partner，留 P0-07 补充），`writeInvokeLog` 加注释 | — |
| RW-3 | logs() 全表内存分页 | `findByConsumer`/`findByService` 改 SQL 层 `WHERE + LIMIT/OFFSET + ORDER BY + COUNT`；`DataServiceManager.logs` 接 `logWriter.findByService`；`findAll` 保留给聚合 | `JdbcServiceInvokeLogRepositoryTest` 覆盖 |
| RW-4 | localMirror 无界增长 | `AsyncInvokeLogWriter.write` 改 if/else：repository 非 null 时只落库，null 时才写 localMirror | — |
| RW-5 | 聚合一致性多调用测试缺失 | 新增 `aggregationMatchesMultipleInvokes`：写 3 条日志 → 账单金额 = 3 × 单价 | `BillingControllerTest` |

### 16.1 测试结果

```text
mvn test "-Dspring.profiles.active=jdbc"
platform-common:   29 tests PASS
platform-gateway:  2 tests PASS
platform-auth:     33 tests PASS
platform-partner:  30 tests PASS
platform-quality:  18 tests PASS
platform-pipeline: 60 tests PASS (含 RW-1 hash 一致性)
platform-billing:  32 tests PASS (含 RW-5 多调用聚合)
BUILD SUCCESS

npm run test:unit
11 files / 35 tests PASS
```

### 16.2 已知遗留（低优，不阻断合入）

- RW-6 trace_id 贯穿 audit：留 P0-08。
- `findAll()` 仍为全表读取，仅用于 billing/stats 聚合，非查询端点；大表优化留 P2-01。
- partner_code 填充留 P0-07 catalog-application。