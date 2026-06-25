# Codex 执行任务

> 本文件由 Claude Code 生成，Codex 必须严格按照本文件执行。
> 本文件按 M1-M6 六个里程碑阶段组织，每个阶段可独立复制到 Codex 桌面端执行。
> 日期：2026-06-24

---

## 0. 使用说明（给操作者）

本平台为**从零搭建**的外部数据采集平台，技术栈：Java 17 + Spring Cloud Alibaba + Vue3 + 达梦/OceanBase 双适配 + 同城双活，一次性全量交付 9 大模块，6 个月周期。

**执行方式**：
1. 每个 M 阶段（M1~M6）是一个**独立的 Codex 桌面端会话**。
2. 在对应阶段会话中，将「阶段任务单」全文（从 `## Mx` 标题开始到下一阶段标题前）粘贴给 Codex。
3. Codex 完成当前阶段后，Claude Code 会读取 git diff + 测试结果，生成 `reviews/claude-review.md`，通过后才进入下一阶段。
4. **严格按顺序执行**：M1 未通过验收不得开始 M2，因为后续阶段依赖前序阶段的脚手架与基础设施。

**每个阶段会话开始时，Codex 必须先读取**：
```
AGENTS.md
docs/requirements.md
tasks/requirement-analysis.md
tasks/claude-plan.md
tasks/codex-task.md
```

---

## 1. 任务目标

从零搭建金融机构外部数据采集平台，分 6 个里程碑阶段实现 9 大功能模块（合作方管理、外部数据接入、外部数据服务、数据目录与预览、消费方管理、数据缓存存储、计费管理、统计监管、数据质量管理），满足性能、可用性、安全、扩展性、兼容性技术指标，支持国产化部署与同城双活。

## 2. 需求依据

- 技术要求文档：`docs/technical-requirements.md`
- 需求文档：`docs/requirements.md`（46 条功能需求 + 24 条非功能需求）
- 需求分析文件：`tasks/requirement-analysis.md`
- 开发计划：`tasks/claude-plan.md`（架构、数据结构、接口、里程碑、脚手架结构的权威来源）

## 3. 允许修改范围

- 所有 `src/main/java` 下的业务代码（7 个微服务 + 1 个 common 库）
- 所有 `src/main/resources` 下的配置文件
- 所有 `src/test` 下的测试代码
- `platform-ui/src` 下的前端代码
- `pom.xml`（Maven 多模块父 POM 与子模块 POM）
- `docker-compose.yml` / `Dockerfile` / `k8s/dev/`
- SQL 迁移脚本（`db/migration/`）
- 项目脚手架目录结构

## 4. 禁止修改范围

- `.env` / `.env.*`（含密钥、密码）—— 用 `${ENV_VAR}` 占位符
- 任何 `*.pem` / `*.key` / `*.crt`（证书文件）
- `CLAUDE.md` / `AGENTS.md`（协作规范文件）
- `docs/` 目录下所有文档（Claude Code 维护）
- `tasks/` 目录下所有任务文件（Claude Code 维护）
- `reviews/` 目录下审查文件（Claude Code 维护）
- `k8s/prod/` 生产部署配置（仅可改 `k8s/dev/`）
- 生产数据库连接配置（仅能用占位符）
- 真实账号密码、访问令牌（一律用占位符）

## 5. 实现要求（全局，适用于所有阶段）

1. **最小改动原则**：优先复用现有结构，不进行无关重构，不引入不必要抽象，不为未来假设场景过度设计。
2. **技术栈严格遵守**：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel。不擅自更换框架或引入大型新依赖（如需引入，必须在「完成后输出」中说明理由并等待确认）。
3. **国产数据库双适配**：用 MyBatis-Plus 方言抽象，开发期可用 MySQL/H2 跑通，但 DAO 层 SQL 必须兼容达梦与 OceanBase 方言（避免 MySQL 专有语法如 `LIMIT` 直接写、`ON DUPLICATE KEY UPDATE`，改用 MyBatis-Plus 提供的能力）。
4. **不重新发明轮子**：协议用成熟库（HttpClient/CXF/JSch/Spring Kafka/Spring AMQP），不写自定义协议实现；调度用 XXL-Job；缓存用 Redis；不写自定义工作流引擎/规则引擎（质量规则用策略模式 + Java Predicate，六维各一策略类）。
5. **安全基线**：所有密码/密钥加密存储（SM4），所有外部输入校验，所有 REST 接口经网关鉴权，敏感日志脱敏。
6. **可回滚**：所有 SQL 迁移脚本必须有对应回滚脚本，用 Flyway 管理。
7. **配置外置**：数据库连接、MQ 地址、Redis 地址等一律 `${ENV_VAR}` 占位，通过 Nacos 或环境变量注入，不硬编码。

## 6. 测试要求（全局，适用于所有阶段）

- 每个 Service 类至少 1 个单元测试，核心方法 100% 覆盖，整体行覆盖率 ≥80%。
- 每个 DAO/Repository 至少 1 个集成测试（用 Testcontainers 起达梦/OceanBase/MySQL）。
- 每个 REST Controller 至少 1 个 API 测试（正常 + 异常路径）。
- 每个协议适配器/格式转换器至少 1 个功能验证测试。
- 状态机必须有流转测试（所有合法 + 非法状态转移）。
- 权限控制必须有未授权/越权/过期 Token 测试。
- 阶段完成后必须运行全部测试并输出结果，**测试不通过不得声明完成**。

## 7. 完成后输出（每个阶段会话结束时）

每个阶段完成后，Codex 必须输出以下内容（Claude Code 据此审查）：

```
## 阶段完成报告 - Mx

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M）

### 2. 关键实现说明
（每个核心类/接口的一句话职责说明）

### 3. 测试命令
（可在本项目根目录直接运行的命令，如 mvn test -pl platform-auth）

### 4. 测试结果
（覆盖率、通过数/失败数、失败用例明细）

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致之处，必须列出原因）

### 6. 潜在风险与遗留问题
（未完成项、待下一阶段处理项、技术债）
```

## 8. 返工规则

1. Claude Code 审查 `reviews/claude-review.md` 后，如判定返工，会在 `tasks/codex-task.md` 末尾追加「返工任务」段落。
2. Codex 接到返工任务后，只修复返工清单中的问题，不做额外改动。
3. 返工完成后再次输出「阶段完成报告」。
4. 同一阶段返工不超过 3 次；超过则升级由 Claude Code 介入分析。

---

# 阶段任务单

> 以下每个 `## Mx` 是一个独立 Codex 会话的任务。会话开始时粘贴对应阶段全文。

---

## M1：基础设施搭建 + 核心链路贯通

**阶段目标**：搭建项目脚手架与基础设施服务，贯通"合作方注册 → HTTP+JSON 单协议接入 → 落库"最小闭环。

**本阶段交付**：
- Maven 多模块父 POM 与子模块结构（platform-common / gateway / auth / partner / pipeline）
- platform-common 共享库（数据模型、工具类、SM4 加密工具、脱敏工具、审计注解、统一异常、统一响应体）
- platform-gateway（Spring Cloud Gateway，路由 + JWT 解析 + CORS + Sentinel 限流骨架）
- platform-auth（账号密码登录 + JWT 颁发 + 用户/角色/权限 CRUD + RBAC 基础）
- platform-partner（合作方 CRUD + 生命周期状态机：注册→提交→审核→准入→评级→退出）
- platform-pipeline 的 ingest 子模块（仅 HTTP/HTTPS 协议 + JSON 格式 + 落库 t_raw_data）
- docker-compose.yml（本地 Nacos + Redis + MySQL 开发环境）
- db/migration V001~V003 初始化脚本（建库 + 合作方表 + 接入任务表 + 原始数据表）

**实现要求**：
1. 按 `tasks/claude-plan.md`「附录：项目脚手架结构」创建完整目录。
2. 父 POM 统一管理 Spring Boot 3.x / Spring Cloud Alibaba 2023.x / MyBatis-Plus 3.5.x / Hutool / Lombok 版本。
3. platform-common 提供：`Result<T>` 统一响应体、`BusinessException`、全局异常处理器、`@AuditLog` 注解 + AOP 切面（先记录到日志，M4 接入审计表）、SM4 加解密工具 `Sm4Util`、脱敏工具 `DesensitizeUtil`（掩码/替换/哈希三种）。
4. platform-gateway：配置 Nacos 服务发现路由，JWT 过滤器解析 Token 并透传用户信息 Header，全局 CORS，Sentinel 限流规则配置（按路径）。
5. platform-auth：Spring Security + JWT；实现 `/auth/login`、`/auth/refresh`、`/auth/logout`；用户/角色/权限表 CRUD；权限校验注解 `@RequirePermission`。
6. platform-partner：合作方表（t_partner / t_partner_interface / t_partner_event）按 claude-plan.md 4.4.1 设计；状态机用枚举 + 事件驱动（不引入 Flowable）；接口配置的 credential 字段用 Sm4Util 加密存储。
7. platform-pipeline.ingest：定义 `ProtocolAdapter` 接口与 `FormatConverter` 接口；实现 `HttpAdapter`（Apache HttpClient）与 `JsonConverter`；接入任务 t_ingest_task 状态机（DRAFT/TESTING/PENDING_APPROVAL/ONLINE/OFFLINE）；落库 t_raw_data。
8. docker-compose：Nacos 2.x、Redis 7、MySQL 8（开发期替代达梦，DAO 兼容写法）、XXL-Job 调度中心。

**测试要求**：
- platform-common：Sm4Util 加解密对称性测试、DesensitizeUtil 三种脱敏测试。
- platform-auth：登录成功/失败、Token 刷新、过期、无权限访问测试。
- platform-partner：合作方全生命周期状态流转测试（含非法转移拒绝）、接口凭证加密存储验证。
- platform-pipeline.ingest：HTTPAdapter 拉取 JSON 数据并落库的端到端测试（用 MockWebServer）。

**完成判定**：
- `docker-compose up` 可起本地依赖。
- 7 个模块 `mvn clean install` 通过。
- 全部单元/集成测试通过。
- 可通过 API 完成：登录 → 创建合作方 → 配置 HTTP 接口 → 创建接入任务 → 触发接入 → 查询 t_raw_data 有数据。

---

## M2：核心管道完整（多协议 + 服务发布 + 消费方 + 目录）

**阶段目标**：补全全部协议适配器与格式转换器，完成数据服务发布调用全链路，实现消费方管理与数据目录基础。

**前置条件**：M1 验收通过。

**本阶段交付**：
- platform-pipeline.ingest 补全协议适配器：WebService（Apache CXF）、SFTP/FTP（JSch）、Kafka（Spring Kafka）、MQ（Spring AMQP/RabbitMQ）、DB 直连（MyBatis-Plus 动态数据源）、API 网关对接。
- platform-pipeline.ingest 补全格式转换器：XML（Jackson XML）、CSV（commons-csv）、Excel（EasyExcel）。
- platform-pipeline.ingest.sync：增量/全量/实时推送/定时拉取四种同步策略 + 断点续传。
- platform-pipeline.service：服务全生命周期（注册→定义→测试→发布→版本→下线）、服务路由、负载均衡、Sentinel 限流/熔断/重试、服务调用日志全量异步记录到 t_service_invoke_log。
- platform-partner 扩展：消费方管理（t_consumer 等表 + 全生命周期 + 分类分级 + 配额管理 + 超额拦截）。
- platform-pipeline.catalog：数据目录浏览（多维分类）、元信息管理（t_data_catalog 表）。

**实现要求**：
1. 协议适配器全部实现 `ProtocolAdapter` 接口（M1 已定义），用工厂模式按 protocol 字段路由。每个适配器独立测试。
2. 格式转换器全部实现 `FormatConverter` 接口，支持流式解析大文件（CSV/Excel），避免 OOM。
3. 同步策略用策略模式；增量同步基于数据哈希或时间戳字段；断点续传记录 offset 到 Redis。
4. 服务调用日志用 Kafka 异步写入，避免阻塞主链路；日志表按日分区。
5. 消费方配额用 Redis 计数器实现（原子递增 + Lua 脚本判断超额），配额预警阈值触发告警事件。
6. 服务鉴权：消费方调用服务时校验 API Key + 签名（HMAC-SHA256）+ 时间戳防重放（5 分钟窗口）。
7. 目录元信息字段按 claude-plan.md 4.4 设计。

**测试要求**：
- 每个协议适配器至少 1 个功能测试（用对应协议的 Mock 端点）。
- 每个格式转换器正常 + 异常（损坏文件）测试。
- 断点续传测试：传输中途中断 → 恢复 → 数据不重不漏。
- 服务全生命周期状态流转测试。
- 限流/熔断触发测试（Sentinel 模拟）。
- 消费方配额耗尽被拦截测试。
- API Key 签名验证：正确签名通过、错误签名拒绝、过期时间戳拒绝、重放拒绝。

**完成判定**：
- 全部协议/格式适配器测试通过。
- 端到端：合作方 HTTP 接口接入 → 服务发布 → 消费方申请权限 → 调用服务获取数据 → 调用日志可查。
- 配额超额拦截生效。

---

## M3：质量体系 + 加工存储

**阶段目标**：完成数据质量管理全量功能与数据缓存/分级存储/加工/集市/生命周期。

**前置条件**：M2 验收通过。

**本阶段交付**：
- platform-quality 完整服务：六维质量规则（完整性/准确性/一致性/及时性/有效性/唯一性）+ 规则配置 + 自动校验执行器 + 问题工单闭环 + 质量报告 + 评分评级。
- platform-pipeline.storage：多维缓存（接口结果/热点/全量）、分级存储（热/温/冷）、数据加工 ETL（清洗/标准化/关联/标签）、外部数据集市、数据生命周期（归档/销毁）。
- 接入链路集成质量校验：接入数据落库前触发质量校验，fail_rate 超阈值暂停接入（对应 E-04 异常场景）。

**实现要求**：
1. 六维规则用策略模式：`QualityRule` 接口 + 6 个实现类（CompletenessRule 等），规则配置存 t_quality_rule，表达式用 JSON 配置（非硬编码）。
2. 校验执行器对接入数据/加工数据/服务输出数据均可触发；校验结果写 t_quality_check_result。
3. 问题工单状态机：OPEN→ASSIGNED→FIXING→VERIFYING→CLOSED。
4. 评分模型：六维加权可配（权重存配置表），输出 0-100 分与评级（A/B/C/D）。
5. 缓存用 Redis + Spring Cache 注解；热点数据用 LFU 策略；缓存失效规则可配（TTL/主动失效）。
6. 分级存储：热数据 Redis、温数据 OceanBase、冷数据 MinIO/归档；路由策略按访问频率自动判定。
7. ETL 用 Spring Batch 或自定义 pipeline；清洗规则可配；标签加工输出到 t_data_asset。
8. 生命周期：定时任务（XXL-Job）扫描到期数据，按归档/销毁规则处置，留痕。

**测试要求**：
- 六维规则各至少 1 个用例（正常通过 + 异常命中）。
- 质量校验执行器对脏数据识别测试（缺失/重复/格式错误）。
- 工单状态流转测试。
- 评分模型计算准确性测试。
- 缓存命中/失效测试。
- ETL 加工链路测试（输入原始数据 → 输出标准化资产）。
- 生命周期归档/销毁执行测试。

**完成判定**：
- 六维质量规则可配可执行。
- 接入脏数据被校验拦截。
- 缓存命中率可达 ≥90%（构造热点场景验证）。
- ETL 加工产出标准化资产可查。

---

## M4：治理（计费 + 统计监管）+ 前端

**阶段目标**：完成计费管理、统计监管全量功能，并实现 Vue3 管理控制台全部页面。

**前置条件**：M3 验收通过。

**本阶段交付**：
- platform-billing：多维度计费模型（按次/量/接口/套餐/时长）、计费规则配置、账单自动生成（日/月/季度）+ 核对 + 异议处理、费用统计分析、财务/采购系统对接适配器接口（Mock 实现，待外部规范）。
- platform-billing.stats：全链路统计（接入量/调用量/传输量/缓存命中率/成功率）、监管报表自动生成（合规/来源/个人信息）、监管报送适配器接口（Mock）、合规审计追溯、可视化大屏数据接口。
- platform-ui（Vue3 + Element Plus + ECharts + Vite + Pinia）：全部模块页面（登录、合作方、接入任务、数据服务、数据目录、消费方、数据质量、计费、统计监管、系统管理、监控大屏）。
- 审计日志落地：`@AuditLog` 切面写入 t_audit_log（不可篡改，追加写）。

**实现要求**：
1. 计费基于 M2 的服务调用日志（t_service_invoke_log）聚合计算，定时任务（XXL-Job）生成账单。
2. 计费规则引擎：按 target_type（PARTNER/CONSUMER/SERVICE）+ billing_model 匹配规则，支持套餐叠加。
3. 账单状态机：GENERATED→CONFIRMED→DISPUTED→ADJUSTED→SETTLED。
4. 统计指标用定时任务聚合到 t_stats_snapshot（不做实时流计算，满足 ≤5 分钟延迟）。
5. 监管报表用模板引擎（如 EasyExcel/POI）生成，支持自定义配置导出。
6. 财务/监管对接：定义适配器接口 + Mock 实现，真实对接待外部规范明确后替换（不影响核心链路）。
7. 前端：统一 API 封装（axios + 拦截器处理 Token/错误）、路由权限控制（基于后端返回的权限码）、响应式布局、ECharts 大屏。
8. 前端按角色菜单动态渲染。
9. 审计日志表 t_audit_log 追加写，提供防篡改校验（记录哈希链或仅追加约束）。

**测试要求**：
- 计费规则匹配准确性测试（各计费模型）。
- 账单生成 + 状态流转测试。
- 统计指标聚合准确性测试。
- 报表生成导出测试。
- 审计日志写入 + 不可篡改测试（尝试更新/删除被拒绝）。
- 前端：核心页面渲染测试（Vue Test Utils）、API 调用 Mock 测试。

**完成判定**：
- 计费账单可生成核对。
- 统计面板数据准确。
- 前端可操作全部 9 大模块功能。
- 审计日志全量记录且不可篡改。

---

## M5：集成测试 + 性能调优 + 安全 + 国产化兼容

**阶段目标**：端到端集成测试、性能压测达标、安全测试通过、国产化环境兼容验证。

**前置条件**：M4 验收通过。

**本阶段交付**：
- 端到端自动化测试套件（全链路：合作方注册→接入→校验→服务→消费→审计→计费）。
- 性能压测脚本（JMeter）与调优（连接池、缓存、SQL、JVM）。
- 安全加固：SQL 注入/XSS/CSRF 防护验证、接口防刷、漏洞扫描修复。
- 国产化适配验证：达梦 DM8 + OceanBase 实际环境测试、麒麟/UOS 兼容、X86/ARM。
- 同城双活部署配置（k8s/dev/ 双机房模拟）。

**实现要求**：
1. 端到端测试用 Testcontainers + 真实协议 Mock，覆盖主链路与关键异常分支。
2. 性能调优：数据库连接池（HikariCP）参数、Redis 连接池、JVM 堆/GC 参数、慢 SQL 优化、索引补充。
3. 安全：MyBatis-Plus 参数化查询防 SQL 注入、输入过滤防 XSS、CSRF Token、接口限流防刷（Sentinel）、敏感数据日志脱敏复查。
4. 国产化：切换达梦/OceanBase 实际驱动跑全部集成测试，修复方言不兼容 SQL。
5. 双活：Redis Sentinel/Cluster、数据库主从、Kafka 多机房复制配置；故障切换演练脚本。
6. 性能指标对照 NFR-P01~P07 逐项验证。

**测试要求**：
- 端到端全链路测试通过。
- 性能压测达标：
  - 标准接口 P50≤200ms / P95≤500ms / P99≤1s
  - ≥1000TPS（峰值 ≥2000TPS）
  - 单批次 100 万条 ≥100MB/s 断点续传
  - 单节点 ≥1 万条/秒接入
  - 缓存命中率 ≥90%、查询 ≤10ms
  - 千万级查询 ≤2s、亿级 ≤5s
- 安全测试：OWASP ZAP 扫描无高危。
- 国产化：达梦 + OceanBase 全部集成测试通过。

**完成判定**：
- 全部性能指标达标（输出压测报告数据）。
- 安全扫描无高危漏洞。
- 达梦 + OceanBase 双环境测试通过。
- 端到端全链路自动化测试通过。

---

## M6：稳定性 + 文档 + 验收

**阶段目标**：48 小时稳定性测试、故障演练、文档交付、验收准备。

**前置条件**：M5 验收通过。

**本阶段交付**：
- 48 小时连续稳定性测试（无宕机、无内存泄漏、无性能下降）。
- 故障注入演练（节点宕机、DB 主从切换、Redis 故障、MQ 故障、双活切换），验证 RPO≤5min / RTO≤30min / 单节点切换 ≤30s / 集群恢复 ≤5min。
- 灰度发布 + 滚动升级 + 回滚演练（回滚 ≤10 分钟）。
- 完整文档：系统架构文档、部署手册、运维手册、开发手册、用户操作手册。
- 验收测试报告（功能 + 性能 + 安全 + 文档）。

**实现要求**：
1. 稳定性测试：持续 48h 混合负载压测，监控 JVM Heap/GC/TPS/响应时间/错误率，确认无泄漏无衰减。
2. 故障演练：用 Chaos Mesh 或脚本注入故障，验证自动切换与数据零丢失。
3. 升级演练：灰度发布 10%→50%→100%，异常自动回滚，验证回滚时间。
4. 文档与实际系统功能一致，可操作。
5. 整理验收报告：功能覆盖率 100%、用例覆盖率 100%、核心通过率 100%、性能达标、安全通过、文档齐全。

**测试要求**：
- 48h 稳定性：无宕机、Heap 稳定、GC 正常、TPS 波动 <10%。
- 故障演练全部场景恢复达标。
- 升级回滚演练达标。
- 文档完整性审查。

**完成判定**：
- 48h 稳定性测试通过。
- 全部故障演练场景达标（RPO/RTO/切换时间）。
- 回滚演练 ≤10 分钟。
- 五类文档交付且与系统一致。
- 验收报告全部项通过。

---

## 附录 A：Codex 桌面端会话启动提示词（每阶段会话开头粘贴）

```
你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md

当前执行阶段：Mx（替换为 M1~M6 对应阶段）
请只执行 tasks/codex-task.md 中「## Mx」段落列出的任务。

执行规则：
1. 只实现当前阶段任务单中列出的任务，不越界到其他阶段。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。
4. 不修改 .env、密钥、证书、生产配置、CLAUDE.md/AGENTS.md/docs/tasks/reviews。
5. 必须补充或更新测试，完成后运行全部测试。
6. 测试不通过不得声明完成。
7. 完成后输出「阶段完成报告 - Mx」（格式见 codex-task.md 第 7 节）。
8. 不做最终验收，最终验收由 Claude Code 完成。
```

## 附录 B：各阶段技术栈与依赖速查

| 阶段 | 主要技术 | 关键依赖 |
|---|---|---|
| M1 | Spring Cloud Gateway / Security / JWT / MyBatis-Plus | Nacos, Redis, MySQL(dev) |
| M2 | CXF / JSch / Spring Kafka / Spring AMQP / EasyExcel / commons-csv | Kafka, RabbitMQ, SFTP server |
| M3 | Spring Batch / Spring Cache / Redis Lua / XXL-Job | Redis, MinIO, OceanBase |
| M4 | Vue3 / Element Plus / ECharts / Pinia / Vite / EasyExcel | — |
| M5 | JMeter / Testcontainers / OWASP ZAP / Chaos Mesh | 达梦, OceanBase, 麒麟/UOS |
| M6 | 监控 / Chaos Mesh / 文档工具 | 全栈 |

## 附录 C：阶段依赖关系

```
M1 (脚手架+核心链路) ──→ M2 (多协议+服务+消费方+目录)
                           │
                           ▼
                       M3 (质量+加工存储)
                           │
                           ▼
                       M4 (计费+统计+前端)
                           │
                           ▼
                       M5 (集成+性能+安全+国产化)
                           │
                           ▼
                       M6 (稳定性+文档+验收)
```

**严格串行**：每阶段需 Claude Code 审查通过（`reviews/claude-review.md` 判定通过）后方可进入下一阶段。
