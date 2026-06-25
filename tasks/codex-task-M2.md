# Codex 桌面端 M2 阶段执行文本（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M1 阶段已通过 Claude Code 审查（reviews/claude-review.md 判定通过）。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md

当前执行阶段：M2（核心管道完整：多协议 + 服务发布 + 消费方 + 目录）
请只执行本提示词中「M2 阶段任务」段落列出的任务，不越界到 M3 及之后阶段。

---

## 项目背景（一句话）

从零搭建金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。一次性全量交付 9 大模块，6 个月周期。M1 已完成脚手架与核心链路（合作方 + HTTP/JSON 接入）。

---

## 执行规则（全局）

1. 只实现当前 M2 阶段任务，不越界到其他阶段。复用 M1 已建好的 platform-common / gateway / auth / partner / pipeline 基础。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 是架构/数据结构/接口/脚手架结构的权威来源，必须遵循）。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。如确需引入新依赖，必须在完成报告中说明理由。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须补充或更新测试，完成后运行全部测试。测试不通过不得声明完成。
6. 国产数据库双适配：开发期可用 MySQL/H2 跑通，但 DAO 层 SQL 必须兼容达梦与 OceanBase 方言（避免 MySQL 专有语法如裸写 LIMIT、ON DUPLICATE KEY UPDATE，改用 MyBatis-Plus 提供的能力）。
7. 不重新发明轮子：协议用成熟库（HttpClient/CXF/JSch/Spring Kafka/Spring AMQP），调度用 XXL-Job，缓存用 Redis，不写自定义工作流引擎/规则引擎。状态机用枚举 + 事件驱动。
8. 配置外置：数据库连接、MQ 地址、Redis 地址等一律 ${ENV_VAR} 占位，通过 Nacos 或环境变量注入，不硬编码。
9. 安全基线：密码/密钥加密存储（SM4），外部输入校验，REST 接口经网关鉴权，敏感日志脱敏。
10. 可回滚：所有 SQL 迁移脚本必须有对应回滚脚本，用 Flyway 管理。
11. 不做最终验收，最终验收由 Claude Code 完成。

---

## M2 阶段任务

### 阶段目标
补全全部协议适配器与格式转换器，完成数据服务发布调用全链路，实现消费方管理与数据目录基础。

### 前置条件
M1 验收通过（platform-common / gateway / auth / partner / pipeline.ingest HTTP+JSON 已就绪）。

### 本阶段交付物
- platform-pipeline.ingest 补全协议适配器：WebService（Apache CXF）、SFTP/FTP（JSch）、Kafka（Spring Kafka）、MQ（Spring AMQP/RabbitMQ）、DB 直连（MyBatis-Plus 动态数据源）、API 网关对接。
- platform-pipeline.ingest 补全格式转换器：XML（Jackson XML）、CSV（commons-csv）、Excel（EasyExcel）。
- platform-pipeline.ingest.sync：增量/全量/实时推送/定时拉取四种同步策略 + 断点续传。
- platform-pipeline.service：服务全生命周期（注册→定义→测试→发布→版本→下线）、服务路由、负载均衡、Sentinel 限流/熔断/重试、服务调用日志全量异步记录到 t_service_invoke_log。
- platform-partner 扩展：消费方管理（t_consumer 等表 + 全生命周期 + 分类分级 + 配额管理 + 超额拦截）。
- platform-pipeline.catalog：数据目录浏览（多维分类）、元信息管理（t_data_catalog 表）。

### 实现要求
1. 协议适配器全部实现 M1 已定义的 ProtocolAdapter 接口，用工厂模式按 protocol 字段路由。每个适配器独立测试。新增协议枚举值：WEBSERVICE/SFTP/FTP/KAFKA/MQ/DB/API_GW。
2. 格式转换器全部实现 M1 已定义的 FormatConverter 接口，支持流式解析大文件（CSV/Excel），避免 OOM。新增格式枚举值：XML/CSV/EXCEL。
3. 同步策略用策略模式（SyncStrategy 接口 + IncrementalSync/FullSync/RealtimeSync/ScheduledSync）；增量同步基于数据哈希或时间戳字段；断点续传记录 offset 到 Redis。
4. 服务调用日志用 Kafka 异步写入，避免阻塞主链路；日志表 t_service_invoke_log 按日分区（设计分区方案，开发期 MySQL 可用按月分区模拟）。
5. 消费方配额用 Redis 计数器实现（原子递增 + Lua 脚本判断超额），配额预警阈值触发告警事件。消费方表参照 claude-plan.md 合作方表结构设计 t_consumer / t_consumer_quota / t_consumer_event。
6. 服务鉴权：消费方调用服务时校验 API Key + 签名（HMAC-SHA256）+ 时间戳防重放（5 分钟窗口）。实现签名验签工具（可放 platform-common）。
7. 数据目录元信息表 t_data_catalog 按 claude-plan.md 4.4 设计（字段定义/格式/更新频率/来源/合规说明/使用限制），多维分类（主题/合作方/类型/场景）。
8. 数据服务表 t_data_service 按 claude-plan.md 4.4.3 设计。服务路由支持按 service_code 路由到对应数据源。
9. docker-compose 补充 Kafka、RabbitMQ、SFTP 测试服务器（如适用）。

### 数据库表设计依据
严格按 tasks/claude-plan.md 第 4.4 节扩展：4.4.2 数据接入补充、4.4.3 数据服务（t_data_service / t_service_invoke_log）、新增消费方表（t_consumer / t_consumer_quota / t_consumer_event）、数据目录表（t_data_catalog）。达梦/OceanBase 兼容写法。

### 测试要求
- 每个协议适配器至少 1 个功能测试（用对应协议的 Mock 端点：MockWebServer/MockFtpServer/Kafka embedded/RabbitMQ Testcontainers）。
- 每个格式转换器正常 + 异常（损坏文件）测试。
- 断点续传测试：传输中途中断 → 恢复 → 数据不重不漏。
- 服务全生命周期状态流转测试（含非法转移拒绝）。
- 限流/熔断触发测试（Sentinel 模拟超限与依赖故障）。
- 消费方配额耗尽被拦截测试。
- API Key 签名验证：正确签名通过、错误签名拒绝、过期时间戳拒绝、重放拒绝。
- 数据目录多维分类查询测试。
- 整体行覆盖率 ≥80%，核心方法 100%。

### 完成判定（本阶段验收标准）
- 全部协议/格式适配器测试通过。
- 端到端：合作方 HTTP 接口接入 → 服务发布 → 消费方申请权限 → 调用服务获取数据 → 调用日志可查。
- 配额超额拦截生效。
- 签名鉴权四类场景全部正确。

---

## 完成后必须输出（阶段完成报告 - M2）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M2

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M）

### 2. 关键实现说明
（每个核心类/接口一句话职责说明）

### 3. 测试命令
（可在项目根目录直接运行的命令）

### 4. 测试结果
（覆盖率、通过数/失败数、失败用例明细；如有失败不得声明完成）

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致之处，必须列出原因）

### 6. 潜在风险与遗留问题
（未完成项、待下一阶段处理项、技术债）

===== 复制到此结束 =====
