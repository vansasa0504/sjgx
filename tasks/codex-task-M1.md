# Codex 桌面端 M1 阶段执行文本（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 工作目录：E:\project\sjgx

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md

当前执行阶段：M1（基础设施搭建 + 核心链路贯通）
请只执行本提示词中「M1 阶段任务」段落列出的任务，不越界到 M2 及之后阶段。

---

## 项目背景（一句话）

从零搭建金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。一次性全量交付 9 大模块，6 个月周期。当前仓库为空（greenfield），需从脚手架开始搭建。

---

## 执行规则（全局）

1. 只实现当前 M1 阶段任务，不越界到其他阶段。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 是架构/数据结构/接口/脚手架结构的权威来源，必须遵循）。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。如确需引入新依赖，必须在完成报告中说明理由。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须补充或更新测试，完成后运行全部测试。测试不通过不得声明完成。
6. 国产数据库双适配：开发期可用 MySQL/H2 跑通，但 DAO 层 SQL 必须兼容达梦与 OceanBase 方言（避免 MySQL 专有语法如裸写 LIMIT、ON DUPLICATE KEY UPDATE，改用 MyBatis-Plus 提供的能力）。
7. 不重新发明轮子：协议用成熟库，调度用 XXL-Job，缓存用 Redis，不写自定义工作流引擎/规则引擎。状态机用枚举 + 事件驱动，不引入 Flowable/Activiti。
8. 配置外置：数据库连接、MQ 地址、Redis 地址等一律 ${ENV_VAR} 占位，通过 Nacos 或环境变量注入，不硬编码。
9. 安全基线：密码/密钥加密存储（SM4），外部输入校验，REST 接口经网关鉴权，敏感日志脱敏。
10. 可回滚：所有 SQL 迁移脚本必须有对应回滚脚本，用 Flyway 管理。
11. 不做最终验收，最终验收由 Claude Code 完成。

---

## M1 阶段任务

### 阶段目标
搭建项目脚手架与基础设施服务，贯通"合作方注册 → HTTP+JSON 单协议接入 → 落库"最小闭环。

### 本阶段交付物
- Maven 多模块父 POM 与子模块结构（platform-common / platform-gateway / platform-auth / platform-partner / platform-pipeline）
- platform-common 共享库（数据模型、工具类、SM4 加密工具、脱敏工具、审计注解、统一异常、统一响应体）
- platform-gateway（Spring Cloud Gateway，路由 + JWT 解析 + CORS + Sentinel 限流骨架）
- platform-auth（账号密码登录 + JWT 颁发 + 用户/角色/权限 CRUD + RBAC 基础）
- platform-partner（合作方 CRUD + 生命周期状态机：注册→提交→审核→准入→评级→退出）
- platform-pipeline 的 ingest 子模块（仅 HTTP/HTTPS 协议 + JSON 格式 + 落库 t_raw_data）
- docker-compose.yml（本地 Nacos + Redis + MySQL 开发环境 + XXL-Job 调度中心）
- db/migration V001~V003 初始化脚本（建库 + 合作方表 + 接入任务表 + 原始数据表）

### 实现要求
1. 按 tasks/claude-plan.md「附录：项目脚手架结构」创建完整目录（本阶段只建 common / gateway / auth / partner / pipeline 五个模块，quality/billing/ops/ui 留空目录或暂不创建）。
2. 父 POM 统一管理版本：Spring Boot 3.x、Spring Cloud Alibaba 2023.x、MyBatis-Plus 3.5.x、Hutool、Lombok、Flyway。各子模块 POM 继承父 POM。
3. platform-common 提供：
   - Result<T> 统一响应体（code/message/data）
   - BusinessException 业务异常 + 全局异常处理器（@RestControllerAdvice）
   - @AuditLog 注解 + AOP 切面（本阶段先记录到日志，M4 接入审计表）
   - Sm4Util 国密 SM4 加解密工具
   - DesensitizeUtil 脱敏工具（掩码/替换/哈希三种）
   - 通用枚举（状态机状态、协议类型、数据类型等）
4. platform-gateway：
   - Spring Cloud Gateway + Nacos 服务发现路由
   - JWT 过滤器：解析 Token 并透传用户信息到下游 Header（X-User-Id / X-User-Roles）
   - 全局 CORS 配置
   - Sentinel 限流规则配置骨架（按路径）
5. platform-auth：
   - Spring Security + JWT
   - 实现 /auth/login（账号密码）、/auth/refresh、/auth/logout
   - 用户/角色/权限表 CRUD（t_user / t_role / t_permission / t_user_role / t_role_permission）
   - 权限校验注解 @RequirePermission
6. platform-partner：
   - 按 claude-plan.md 4.4.1 设计表：t_partner / t_partner_interface / t_partner_event
   - 合作方全生命周期状态机用枚举 + 事件驱动（状态：PENDING/ACTIVE/SUSPENDED/TERMINATED；事件：REGISTER/SUBMIT/APPROVE/REJECT/ACTIVATE/SUSPEND/RATE/TERMINATE），非法转移抛异常
   - t_partner_interface.credential 用 Sm4Util 加密存储
   - 合作方接口配置、密钥管理 REST API
7. platform-pipeline.ingest：
   - 定义 ProtocolAdapter 接口（拉取数据方法）与 FormatConverter 接口（解析方法）
   - 实现 HttpAdapter（Apache HttpClient，支持 HTTP/HTTPS）与 JsonConverter（Jackson）
   - 接入任务表 t_ingest_task 状态机（DRAFT/TESTING/PENDING_APPROVAL/ONLINE/OFFLINE）
   - 接入执行记录 t_ingest_record
   - 原始数据落库 t_raw_data（raw_content 存原始 JSON 文本，data_hash 存 SHA256 用于去重）
   - 用工厂模式按 protocol 字段路由适配器（本阶段只有 HTTP，但接口要预留扩展）
8. docker-compose.yml：Nacos 2.x、Redis 7、MySQL 8（开发期替代达梦，DAO 写法兼容）、XXL-Job 调度中心。

### 数据库表设计依据
严格按 tasks/claude-plan.md 第 4.4 节（4.4.1 合作方管理、4.4.2 数据接入的 t_ingest_task / t_ingest_record / t_raw_data）建表。达梦/OceanBase 兼容写法（BIGINT 主键、JSON 字段用 CLOB 或 TEXT、避免 MySQL 专有语法）。

### 测试要求
- platform-common：Sm4Util 加解密对称性测试、DesensitizeUtil 三种脱敏测试、统一响应体与异常处理测试。
- platform-auth：登录成功/失败、Token 刷新、过期 Token、无权限访问测试。
- platform-partner：合作方全生命周期状态流转测试（含所有非法转移被拒绝）、接口凭证加密存储验证（存的是密文）。
- platform-pipeline.ingest：HttpAdapter 拉取 JSON 数据并落库的端到端测试（用 MockWebServer 模拟外部接口）。
- 整体行覆盖率 ≥80%，核心方法 100%。

### 完成判定（本阶段验收标准）
- docker-compose up 可起本地依赖（Nacos/Redis/MySQL/XXL-Job）。
- 5 个模块 mvn clean install 通过。
- 全部单元/集成测试通过。
- 可通过 API 完成端到端：登录 → 创建合作方 → 配置 HTTP 接口 → 创建接入任务 → 触发接入 → 查询 t_raw_data 有数据。

---

## 完成后必须输出（阶段完成报告 - M1）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M1

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M）

### 2. 关键实现说明
（每个核心类/接口一句话职责说明）

### 3. 测试命令
（可在项目根目录直接运行的命令，如 mvn test -pl platform-auth）

### 4. 测试结果
（覆盖率、通过数/失败数、失败用例明细；如有失败不得声明完成）

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致之处，必须列出原因）

### 6. 潜在风险与遗留问题
（未完成项、待下一阶段处理项、技术债）

===== 复制到此结束 =====
