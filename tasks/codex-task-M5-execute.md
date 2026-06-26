# Codex 桌面端 M5 阶段执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M4 阶段已通过 Claude Code 审查（见 reviews/claude-review.md 结论"通过"）。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M5.md   ← 本阶段任务来源
7. reviews/claude-review.md ← M1~M4 审查历史，含 M4 遗留 P-01~P-10

当前执行阶段：M5（集成测试 + 性能调优 + 安全 + 国产化兼容 + 双活配置 + M4 遗留修复）
请只执行本提示词中「M5 阶段任务」段落列出的任务，不越界到 M6 阶段。

---

## 项目背景（一句话）

金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。M1~M4 已完成全部功能模块（合作方/接入/服务/目录/消费方/质量/存储/计费/统计/审计/前端）。本阶段聚焦集成、性能、安全、国产化、双活，并修复 M4 审查遗留项。

### 既有现状（必须基于此，不得推翻）
- 后端 7 模块 + 前端 platform-ui 已就绪，`mvn test` 全绿、`npm run test:unit` 全绿。
- **无** k8s 目录、**无** Dockerfile、**无** Testcontainers、**无** JMeter 脚本、**无** 端到端集成测试 —— 本阶段新建。
- 数据源已 `${ENV_VAR}` 占位（默认 MySQL）。Flyway V001~V008 已有迁移。
- M4 审查遗留 10 项（P-01~P-10，见 reviews/claude-review.md §9），本阶段修复其中可在代码层完成的项。
- 开发环境为 Windows + Maven + npm，**无达梦/OceanBase 实际实例、无 K8s 集群、无 JMeter 运行环境**。因此本阶段对"需外部环境"的交付物，采取**输出脚本/配置/报告框架 + 开发环境可验证部分实际验证**的策略，不得虚构实测数据。

---

## 执行规则（全局）

1. 只实现 M5 阶段任务，不越界到 M6。本阶段以测试、调优、加固、修复、配置为主，原则上不新增业务功能。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 是权威来源）。
3. 优先采用最小改动修复问题，不进行无关重构。修复必须有测试覆盖。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须运行全部测试并输出结果。测试不通过不得声明完成。
6. 国产数据库双适配：开发期用 H2(MySQL 模式)/MySQL 跑通；对达梦/OceanBase 做**方言兼容性静态审查 + 驱动依赖 + 不兼容 SQL 修复**，实际环境验证列为 M6 验收项（不得虚构"达梦实测通过"）。
7. 不重新发明轮子：压测用 JMeter（输出 .jmx 脚本），安全扫描用 OWASP ZAP（输出配置与运行说明），故障注入用脚本。
8. 配置外置，不硬编码。
9. 安全基线：SQL 注入/XSS/CSRF 防护、接口防刷、敏感数据脱敏复查。
10. 可回滚：本阶段如改既有 V00x 迁移，须同步 U00x；新增表用 V009/U009（原则上 M5 不建新业务表）。
11. 不做最终验收，最终验收由 Claude Code 完成。
12. **诚实原则**：开发环境无法验证的项（实际压测数据、ZAP 扫描结果、达梦/OceanBase 实测、双活切换实测），输出产物 + 报告模板并明确标注"待 M6 外部环境验证"，**不得编造实测数值**。

---

## M5 阶段任务

### 阶段目标
端到端集成测试套件、性能压测脚本与调优配置、安全代码加固、国产化方言兼容与驱动、双活部署配置、Docker化，并修复 M4 遗留项。

### 前置条件
M4 验收通过（全部模块后端 + 前端已就绪）。

### 本阶段交付物
- 端到端集成测试套件（Testcontainers 或 H2 + Mock 协议，全链路 + 关键异常分支）。
- JMeter 压测脚本（.jmx）+ 性能调优配置（HikariCP/Redis/JVM）+ 压测报告框架。
- 安全加固：SQL 注入/XSS/CSRF 防护、Sentinel 限流防刷、脱敏复查。
- 国产化：达梦/OceanBase 驱动依赖 + 方言兼容性审查报告 + 不兼容 SQL 修复。
- 双活：k8s/dev 双机房部署描述 + 故障切换演练脚本 + Dockerfile。
- M4 遗留项修复（P-01/05/06/08/09 等代码层可修项）。

### 实现要求

#### A. M4 遗留项修复（代码层，必须有测试）
- **P-01**：`BillGenerator` 的 billNo/targetId 不再用 `hashCode()`。改为确定性 billNo（period + consumerCode/partnerCode + 序号）或注入 ID 生成器；targetId 用稳定映射。补测试验证 billNo 唯一性与稳定性。
- **P-09**：`BillGenerator` 区分 billType 分组维度——SETTLEMENT 按 partner 聚合、EXPENSE 按 consumer 聚合。补 SETTLEMENT 账单测试。
- **P-05**：为 BillGenerator/StatsAggregator 定义 XXL-Job Handler 类（`@XxlJob` 注解或实现 `JobHandler`，开发期不依赖 XXL-Job 运行时——用注解但 pom 可选依赖，或定义 Handler 类持有可调用入口）。说明：若引 XXL-Job 依赖，仅在 billing 模块引 `xxl-job-core`，版本与 docker-compose(2.4.1) 一致。
- **P-06**：`AuditLogAspect` 提取方法参数（脱敏后摘要）写入 detail。
- **P-08**：`AuditLogAspect` 从 SecurityContext（或传入上下文）取真实 actorType/actorId，无上下文时回退 SYSTEM。
- **P-02**：`StatsAggregator.TRANSFER_BYTES`——在 V005 t_service_invoke_log 补 `response_size BIGINT` 字段（新增 V009 迁移加列，U009 回滚），StatsAggregator 改用 response_size 求和；保留 elapsedMillis 不再作传输量代理。
- **P-03**：`DashboardService.summarize` serviceCount 不再硬编码 1，从数据计算。
- **P-04**：billing pom 移除未用的 EasyExcel 依赖（ReportGenerator 已用 POI）。
- **P-07**：前端测试全局注册 Element Plus（main.ts 已注册则在测试 setup 引入），消除 resolve warn。
- **P-10**：将 `ServiceInvokeLog` 下沉到 platform-common（或 platform-common 定义共享 DTO），消除 billing→pipeline 反向依赖中跨模块的领域对象耦合。若改动过大可保留并注明，优先最小改动。

#### B. 端到端集成测试
1. 新增 `platform-pipeline/src/test/java` 下的端到端集成测试（或独立 `platform-it` 模块，优先放 pipeline 测试目录避免新模块）。
2. 全链路：合作方注册→创建接入任务(HTTP+JSON)→质量校验→落库→服务发布→消费方调用→调用日志→计费账单生成→统计聚合。复用各模块既有 Service，不依赖真实外部服务（HttpServer Mock + H2 内存库）。
3. 关键异常分支：接口不可达、格式错误、质量拦截(fail_rate 超阈值)、配额超限、鉴权失败、熔断触发。每分支至少 1 用例。
4. 用 H2(MySQL 模式) 跑 Flyway 全量迁移(V001~V009) 验证脚本可执行。

#### C. 性能压测脚本与调优配置
1. 新建 `perf/jmeter/` 目录，输出 JMeter .jmx 脚本覆盖：标准服务调用、批量接入、数据加工、目录查询。脚本参数化（线程数/循环数用 ${__P()} 变量）。
2. 调优配置：
   - HikariCP：`maximum-pool-size/minimum-idle/connection-timeout/idle-timeout` 写入各服务 application.yml（${ENV_VAR} 占位，给默认值）。
   - Redis：连接池参数（lettuce pool）。
   - JVM：输出 `perf/jvm.args` 文件含堆/GC 参数建议（-Xms/-Xmx/-XX:+UseG8GC 等，注释说明）。
3. 慢 SQL 排查：审查 V001~V009 索引是否覆盖高频查询（t_service_invoke_log 按 created_at/service_code/consumer_code 查询、t_audit_log 按时间范围查），缺则补索引（V009 或注明）。
4. 输出 `perf/report-template.md` 压测报告框架：NFR-P01~P07 逐项表格（指标/标准/实测值/是否达标/测试方法），实测值留空标注"待 M6 外部环境压测填充"。
5. 缓存命中率验证：构造热点场景单测，验证 LfuCacheService 命中率≥90%（M3 已有，可在 perf 报告引用）。

#### D. 安全加固
1. SQL 注入：审查所有 JdbcTemplate/Statement 拼接点，确认参数化。DbAdapter（M2）的 SQL 从 URL path 获取执行——补 SQL 白名单校验或只读账号说明（代码层加注释 + 限制为 SELECT）。
2. XSS：网关或 common 加输入过滤/输出编码过滤器（`XssFilter`）。
3. CSRF：若前后端分离用 JWT 可禁用 CSRF（Spring Security 配置 csrf().disable() + JWT），说明理由。
4. 接口防刷：网关集成 Sentinel 限流配置（`SentinelGatewayFilter` 或配置文件），关键接口 QPS 阈值可配。
5. 脱敏复查：审查日志输出，确保敏感字段（手机号/身份证/密钥）不落明文日志；`DesensitizeUtil` 在审计 detail/调用日志脱敏点接入。
6. 输出 `security/owasp-zap.md` 运行说明（目标 URL/扫描策略/报告导出）+ `security/manual-pentest-checklist.md` 手工渗透清单（SQL注入/XSS/CSRF/越权/签名绕过/重放）。

#### E. 国产化适配
1. 方言兼容性审查：审查 V001~V009 SQL，标注达梦/OceanBase 不兼容点并修复：
   - 分页：MySQL `LIMIT` → 达梦 `TOP`/`ROWNUM`、OceanBase 兼容 MySQL 模式。统一用 MyBatis-Plus 分页插件方言切换，不手写 LIMIT。
   - 日期函数：`CURRENT_TIMESTAMP` 达梦/OceanBase 均支持，确认。
   - JSON 函数：若有用 JSON 类型（contact_info JSON 等），达梦用 CLOB + 应用层解析，OceanBase 支持 JSON。审查并调整。
   - `TINYINT`：达梦支持，OceanBase 兼容 MySQL 模式支持。确认。
2. 驱动依赖：各服务 pom 加 OceanBase 驱动 `mysql-connector-oceanbase` 或 `oceanbase-client`（Maven 中央仓库可用）；达梦驱动 `DmJdbcDriver` 不在中央仓库，pom 用 `<scope>provided</scope>` + 注释说明需手动安装或部署时挂载。
3. MyBatis-Plus 方言：配置 `DbType` 切换（达梦 `DM` / OceanBase `OB_ORACLE` 或 `MYSQL`），通过 `${DB_TYPE}` 配置外置。
4. 输出 `docs-compatible/national-db-compat-report.md`（放项目根 `national-db-compat-report.md`，不写 docs/ 目录）：逐表/逐 SQL 审查结论 + 修复记录 + 待实测项。

#### F. 双活部署配置
1. 新建 `k8s/dev/` 目录（**不创建 k8s/prod/**）：双机房模拟部署描述。
   - `k8s/dev/namespace.yaml`、`k8s/dev/deployment-platform-a.yaml`、`k8s/dev/deployment-platform-b.yaml`（A/B 两机房各一份，镜像用 ${IMAGE_TAG} 占位）。
   - `k8s/dev/service.yaml`、`k8s/dev/configmap.yaml`（Nacos/Redis/DB 地址 ${ENV_VAR} 占位）。
2. 故障切换演练脚本 `k8s/dev/failover-drill.sh`：模拟主机房宕机（kubectl scale platform-a=0）→ 验证 platform-b 接管 → 恢复。脚本含 RPO/RTO 检查点（标注待实测）。
3. 双活中间件配置说明 `k8s/dev/ha-middleware.md`：Redis Sentinel/Cluster、DB 主从、Kafka 多机房复制配置要点（配置项 + ${ENV_VAR} 占位，不实际部署）。
4. Dockerfile：为每个后端模块（或统一一个多阶段 Dockerfile）生成容器镜像构建文件，基础镜像 `eclipse-temurin:17-jre`，端口 ${SERVER_PORT} 占位。前端 platform-ui 用 `node:20` 构建 + `nginx` 部署两阶段。

### 数据库变更
- V009__perf_and_compat.sql：t_service_invoke_log 加 response_size 列 + 补索引；U009 反向。如无其他建表需求，仅此。
- 原则：不新建业务表，仅加列/加索引。

### 测试要求
- M4 遗留项修复各有对应测试（billNo 唯一性、SETTLEMENT 分组、审计参数/actor、StatsAggregator response_size、Dashboard serviceCount、前端 EP 注册）。
- 端到端集成测试全链路通过 + 6 个异常分支通过。
- H2 跑 V001~V009 全量迁移成功。
- `mvn test` 全绿、`npm run test:unit` 全绿。
- JMeter 脚本语法正确（可被 JMeter 加载，开发环境无需运行）。
- 安全加固项有单测（XssFilter 测试、SQL 白名单测试、脱敏测试）。
- 国产化方言修复有对应 SQL 兼容性测试（H2 MySQL 模式验证修复后 SQL 可执行）。

### 完成判定（本阶段验收标准）
- M4 遗留 P-01/05/06/08/09/02/03/04/07/10 代码层修复完成且有测试。
- 端到端集成测试全链路 + 异常分支通过。
- JMeter 脚本 + 调优配置 + 压测报告框架产出。
- 安全加固代码层完成 + ZAP/渗透清单产出。
- 国产化方言审查报告 + 驱动依赖 + 不兼容 SQL 修复完成。
- k8s/dev 双活配置 + 故障切换脚本 + Dockerfile 产出。
- `mvn test` + `npm run test:unit` 全绿。
- **实测类指标（压测数值/ZAP 扫描结果/达梦 OceanBase 实测/双活切换实测）明确标注"待 M6 外部环境验证"，不得编造。**

---

## 完成后必须输出（阶段完成报告 - M5）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M5

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M；分：M4遗留修复 / 集成测试 / 性能 / 安全 / 国产化 / 双活 / Dockerfile）

### 2. 关键实现说明
（M4 遗留逐项修复说明；集成测试覆盖；调优措施；安全加固项；国产化修复项；双活配置）

### 3. 测试命令
（`mvn test` / `npm run test:unit` / H2 迁移验证 / JMeter 脚本加载 / 安全单测）

### 4. 测试结果
- 单元/集成测试：通过数/失败数
- H2 V001~V009 迁移：是否成功
- JMeter 脚本：语法是否可加载
- 安全单测：通过数
- 国产化方言：H2 验证修复后 SQL 可执行
- **待 M6 外部环境验证项**（明确列出）：压测实测值、ZAP 扫描、达梦/OceanBase 实测、双活切换实测

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致或因环境限制调整的项，必须列出原因）

### 6. 潜在风险与遗留问题
（未达标项、待 M6 处理项、技术债）

===== 复制到此结束 =====
