# 项目功能审核、补充开发与测试验证计划

> 审核日期：2026-06-27  
> 审核范围：当前代码、`docs/detailed-requirements-design.md`、`docs/database-design.md`、`db/migration/*.sql`、`reviews/claude-review.md`。  
> 审核目标：对照需求详设和数据库设计，识别已开发功能、上线前缺口、补充开发项和可执行测试验证方法。  
> 参照项目：Apache SeaTunnel、DataHub、Great Expectations、Apache Ranger。

## 1. 开源项目调研结论

| 项目 | 可参照能力 | 对本项目的启发 |
|---|---|---|
| Apache SeaTunnel | 多源连接器、批流一体、全量/增量/实时同步、快照一致性、同步监控 | 接入模块不应只做 Controller 流程，应沉淀连接器能力矩阵、offset/checkpoint、Source/Sink/Transform 合约测试 |
| DataHub | 数据目录、元数据图谱、血缘、搜索、治理、审计、API-first | 目录模块应从静态列表升级为元数据事实源，补资产血缘、字段级元数据、质量摘要、申请记录和使用统计 |
| Great Expectations | Expectation 规则、校验结果、数据质量文档、数据源兼容性 | 质量模块应把规则、执行、结果、问题、报告完整落库，并用规则套件和校验结果驱动报告 |
| Apache Ranger | 集中授权、插件化策略执行、KMS、审计、安全管理 | 权限不能只停留在管理端 RBAC，应补服务调用、字段级权限、策略审计、密钥加密和不可篡改审计 |

## 2. 当前实现概览

当前 M7 开发环境验收结果较完整：后端 11 组 Controller 覆盖合作方、接入、服务、目录、消费方、质量、计费、统计、用户、角色、权限；前端 10 个功能页面已接真实 API；MockMvc 覆盖 200/401/403/400 主分支；端到端 10 步主链路已有开发环境证据。

但这仍是“开发环境闭环”，不是“生产上线闭环”。主要差距集中在：

1. 多个核心仓储仍为内存实现，重启丢失，无法支撑审计、计费、目录、质量的事实源要求。
2. 迁移脚本与目标库设存在表名、字段、方言、重复建表差异。
3. 凭证和密钥已有 apiKey 查找雏形，但仓储仍为内存，默认 `api-key/secret` 不符合生产安全。
4. 调用日志、账单明细、目录申请、审计不可篡改、血缘关系等关键生产数据模型未完全落地。
5. 测试主要覆盖开发环境和内存仓储，缺真实数据库、中间件、性能、安全和兼容性验证。

## 3. 上线前阻断项

| 编号 | 阻断项 | 现状证据 | 补充开发要求 | 验证方法 |
|---|---|---|---|---|
| P0-01 | 数据库迁移冲突 | `V001` 与 `V010` 均创建 `t_user/t_role`，字段定义不一致 | 合并身份权限迁移；已有表用 `ALTER` 演进；补 rollback | 空库执行 Flyway；旧库升级执行 Flyway；校验表结构 diff |
| P0-02 | SQL 方言不兼容 | `V010` 使用 `AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP` | 按 MySQL、达梦、OceanBase 分方言脚本或统一雪花 ID | 三类数据库分别跑迁移；CI 加 SQL 兼容检查 |
| P0-03 | 种子数据表名错误 | `U010` 写 `t_data_catalog_item`，目标表为 `t_data_catalog` | 统一表名；开发种子移出生产代码 | 空库 seed 后查询目录；删除生产硬编码 DEMO 数据 |
| P0-04 | 核心事实源未落库 | billing、quality、partner、catalog、service log 等多处内存仓储 | 为核心 Repository 补 JDBC/JPA 实现，并以 profile 切换 | 重启后数据仍存在；端到端链路重启恢复测试 |
| P0-05 | API 凭证生产安全不足 | `ApiCredentialRepository` 为内存，默认 `api-key/secret` | 落 `t_api_credential`，secret 密文/KMS 引用，支持禁用/轮换/服务范围 | 签名正确/错误/过期/重放测试；日志中不得出现 secret |
| P0-06 | 调用日志字段不足 | 目标库设要求 `trace_id/api_key/request_hash/error_code` | 扩展 `t_service_invoke_log` 与写入逻辑 | 每次 invoke 后按 trace_id 串起审计、计费、统计 |
| P0-07 | 账单缺明细事实 | 目标库设建议 `t_bill_item`，当前账单生成仍依赖请求 logs | 新增 `t_bill_item`；账单从调用日志聚合生成 | 构造调用日志后生成账单；金额、次数、数据量可复核 |
| P0-08 | 目录申请未落事实表 | `CatalogController` 内部 Map 保存 application | 新增 `t_catalog_application` 及仓储 | 申请、审批、开通、审计全链路可查询 |
| P0-09 | 审计不可篡改不足 | `t_audit_log` 仅应用层追加写 | 增加 DB 权限/触发器或 hash 链；禁止 UPDATE/DELETE | 直接 UPDATE/DELETE 审计表应失败；hash 链校验通过 |
| P0-10 | 前端边界测试不足 | 已知缺空态、失败态、表单校验、状态按钮 API 断言 | 补页面级 Vitest 测试 | mock empty/reject/invalid submit，断言 UI 与 API 调用 |

## 4. 模块级功能补充清单

### 4.1 合作方管理

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 合作方全生命周期 | Controller 与状态流转已具备 | 资源不存在异常仍可能返回 500；仓储内存 | 不存在统一返回业务错误；Partner/Interface/Event 落库 | MockMvc 404/400；重启后 partner/event 仍可查 |
| 接口配置与密钥 | 凭证加密测试存在 | 只保存合作方接口配置，未与真实调用适配联动 | 接口配置落库；密钥密文；支持轮换和启停 | 明文不入库；停用接口后接入任务不可上线 |
| 服务质量监控 | 有评级入口 | 缺健康检查、SLA、可用性统计 | 补接口健康检查任务和质量评分联动 | 模拟超时/失败，评级和告警指标变化 |

### 4.2 外部数据接入

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 多协议接入 | 具备 HTTP/DB/文件/格式转换等基础类 | 缺连接器统一合约和真实中间件验证 | 定义 `SourceConnector` 合约，补 HTTP/SFTP/Kafka/MQ/DB 能力矩阵 | 每类 connector 跑 contract test；失败、超时、重试、认证失败覆盖 |
| 全量/增量/实时/断点续传 | 有 sync/offset 测试雏形 | offset 内存，缺恢复和幂等 | offset/checkpoint 落库或 Redis；任务恢复策略 | 中断后恢复；重复消息不重复入库；offset 单调递增 |
| 接入流程管控 | API 齐全 | 上线前置条件未完全校验 | 上线审批前校验协议、映射、质量规则、测试结果 | 缺配置时 submit/approve 返回 400/409 |

### 4.3 外部数据服务

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 服务生命周期 | register/define/test/publish/offline 已有 | service/routeData 内存 | `t_data_service` JDBC 仓储；服务版本与路由配置落库 | 重启后服务仍可调用；版本升级可回滚 |
| API Key 签名 | 已改为 apiKey 查 secret | secret 仓储内存，默认凭证不安全 | `t_api_credential` 密文存储、轮换、禁用、服务范围 | 正确签名 200；错签/过期/重放/越权 400/403 |
| 限流熔断 | 有简易限流 | 缺按 consumer/service/IP 维度和分布式限流 | Redis 限流、熔断状态、降级响应 | 并发压测触发 429；熔断窗口恢复 |
| 调用日志 | 有异步日志 mirror | 字段和持久化不足 | 落库 `t_service_invoke_log`，增加 trace/request_hash/error | invoke 后日志可被 billing/stats/audit 聚合 |

### 4.4 数据目录与预览

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 目录检索 | list/search/meta/preview/apply/approve 已有 | CatalogService 内存；申请 Map 内存 | `t_data_catalog`、`t_catalog_application` 仓储 | 申请审批重启后仍可查；审批状态不可非法跳转 |
| 元数据与血缘 | 字段定义可展示 | 缺资产血缘、质量摘要、使用统计 | 增加 lineage、quality summary、usage stats | 目录详情展示来源、字段、质量、调用热度 |
| 数据预览 | preview 可调用 | 样本/脱敏/权限/审计不足 | 从资产样本读取，动态脱敏，写审计 | 未授权 403；敏感字段脱敏；审计可追溯 |

### 4.5 消费方管理

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 消费方生命周期 | register/list/detail/quota/events/audit 已有 | 仓储内存 | Consumer/Quota/Event 落库 | 重启后配额不丢；非法状态流转返回 409 |
| 配额管理 | 有本地计数 | 缺分布式配额、预警、周期重置 | Redis 原子计数 + DB 快照 | 并发扣减不超额；达到阈值生成预警事件 |
| 调用日志查询 | logs 入口存在 | 需要接 service invoke log | 按 consumer_id/code 查询调用日志 | 调用后消费方日志页可查 |

### 4.6 缓存、存储与再利用

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 热温冷分层 | 有内存/暖存储类 | 分层策略未与真实存储集成 | Redis/OceanBase/MinIO profile 实现 | 热数据低延迟；冷数据归档恢复成功 |
| 生命周期 | 有事件列表 | 未落库、无销毁证明 | `t_lifecycle_record` 增 operator/reason/proof_hash | 归档/恢复/销毁均有审计和证明 |
| 数据加工复用 | 有清洗/标准化/关联类 | 缺任务化编排和资产沉淀 | 加工任务、资产版本、集市发布 | 原始数据加工后生成 asset/catalog |

### 4.7 计费管理

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 计费规则 | rule CRUD 已有 | 仓储内存 | `t_billing_rule` JDBC 实现 | 规则生效期、优先级、停用测试 |
| 账单生成 | 可根据请求 logs 生成 | 不应由前端/请求传 logs | 从 `t_service_invoke_log` 聚合；生成 `t_bill_item` | 给定日志生成账单，明细与总额一致 |
| 财务采购对接 | 设计保留适配器 | 未实现 | BillExport/FinanceSync/PurchaseContract 接口 | mock 财务系统成功/失败/重试/回执 |

### 4.8 统计监管

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 大屏统计 | dashboard/reports/audit 已有 | stats 内存，报表为最小实现 | 从事实表聚合，报表文件可下载 | 空数据、时间范围、维度过滤测试 |
| 监管报送 | 仅框架 | 缺报文、加密、回执 | 定义监管报表模板和回执状态机 | 生成报文、脱敏加密、失败重试、回执入库 |
| 审计追溯 | audit 查询已有 | trace_id 串联不足 | 全模块写统一 trace_id | 一次主链路按 trace_id 查到全事件 |

### 4.9 数据质量

| 设计要求 | 当前状态 | 缺口 | 补充开发 | 测试验证 |
|---|---|---|---|---|
| 六维质量规则 | 规则和执行器已有 | 规则/结果/问题部分内存 | Rule/Result/Issue/Weight 落库 | 六维规则全覆盖；无效 ruleId 返回 400 |
| 问题闭环 | assign/resolve 已有 | 状态机和 SLA 告警不足 | OPEN/ASSIGNED/FIXING/VERIFYING/CLOSED 完整状态机 | 非法流转 409；超时生成告警 |
| 质量报告 | reports/scores 已有 | latest 需按 checkedAt；报告未持久化 | 按时间取最新；报告落库/导出 | 并发多次校验后取 checkedAt 最大记录 |

## 5. 数据库补充开发清单

| 表/脚本 | 问题 | 补充动作 | 验收 SQL/检查 |
|---|---|---|---|
| `V001`/`V010` | 重复建 `t_user/t_role` | 整合为单一身份权限迁移 | `SELECT COUNT(*)` 与 schema diff 无冲突 |
| `t_role` | `code/name` 设计与实现存在差异 | 明确 `code` 是否必填；AuthService insert 与表结构一致 | 插入 role 不报 NOT NULL 错 |
| `U010` | 写入不存在的 `t_data_catalog_item` | 改为 `t_data_catalog` | seed 执行成功 |
| `t_api_credential` | secret 字段语义为明文 | 改名或约定为 `secret_cipher/kms_key_id` | 表内不出现明文 secret |
| `t_service_invoke_log` | 缺审计排障字段 | 补 `trace_id/api_key/request_hash/error_code` | invoke 后字段非空 |
| `t_bill_item` | 未建表 | 新增迁移和 Repository | bill 与 bill_item 一对多 |
| `t_catalog_application` | 未建表 | 新增迁移和 Repository | 目录申请审批可查 |
| 大表分区 | 设计有，迁移无 | 为 raw/log/audit/stats/quality_result 给出生产方言脚本 | EXPLAIN 命中分区/索引 |

## 6. 测试验证方法

### 6.1 单元测试

| 类型 | 覆盖对象 | 方法 |
|---|---|---|
| 状态机 | partner、ingest、service、consumer、bill、quality issue | 枚举所有合法/非法流转，非法返回 409/400 |
| 规则引擎 | billing rule、quality rule、quota | 边界值、优先级、禁用、生效期、空数据 |
| 安全工具 | JWT、签名、脱敏、加密 | 过期、篡改、重放、明文泄露扫描 |
| 转换器 | JSON/XML/CSV/Excel | 编码、空值、类型转换、坏文件、超大文件 |

### 6.2 API 契约测试

1. 每个 Controller 覆盖 200、401、403、400、404、409。
2. 所有请求体补 Bean Validation，测试缺字段、非法枚举、越界数值。
3. `/api/v1/services/{code}/invoke` 单独验证无 JWT 但必须通过 API Key 签名。
4. 错误响应统一包含 `code/message/traceId`，前端可展示。

### 6.3 集成测试

| 集成对象 | 验证内容 |
|---|---|
| MySQL/达梦/OceanBase | Flyway 空库、旧库升级、rollback、索引、方言 |
| Redis | 分布式限流、配额计数、缓存 TTL、缓存击穿 |
| Kafka/RabbitMQ | 实时接入、ack、重试、死信、积压 |
| MinIO | 冷数据归档、恢复、销毁证明 |
| Gateway | 路由、鉴权头透传、限流、跨域 |

建议使用 Testcontainers 或可重复的 docker compose 环境。H2/内存仓储只能作为开发测试，不能作为上线证明。

### 6.4 端到端主链路测试

必须保留可复跑脚本，覆盖：

1. 登录获取 token。
2. 新建合作方、接口、提交、审批、准入。
3. 新建接入任务、配置映射、规则、测试、审批上线。
4. 生成原始数据、质量校验、问题闭环。
5. 生成资产、目录发布、预览、申请、审批。
6. 新建服务、定义、测试、发布。
7. 新建消费方、配置配额、生成 API Key。
8. 调用服务，写调用日志、审计日志、统计快照。
9. 生成账单、账单明细、确认/异议。
10. 按 trace_id 查询监管审计证据。

### 6.5 性能与稳定性测试

| 场景 | 指标 |
|---|---|
| 服务调用压测 | P50/P95/P99、TPS、错误率、429 比例 |
| 批量接入 | 100 万行 CSV/Excel、断点续传、内存占用 |
| 实时消息 | Kafka/MQ 积压、消费延迟、重复消费率 |
| 调用日志写入 | 异步日志不阻塞主链路，丢失率为 0 |
| 数据库查询 | 目录搜索、日志聚合、账单生成 EXPLAIN 命中索引 |
| 稳定性 | 24-48h soak test，无内存泄漏，无连接泄漏 |

### 6.6 安全测试

| 场景 | 验证点 |
|---|---|
| 鉴权绕过 | 所有管理端接口无 token 401，低权限 403 |
| 签名安全 | timestamp 过期、nonce 重放、body 篡改、service 越权 |
| 敏感信息 | secret/password/token/apiKey 不出现在日志、审计 detail、前端响应 |
| SQL 注入 | DB 接入 SQL 白名单和参数化 |
| XSS/CSRF | 管理台输入输出转义，写操作 CSRF 策略明确 |
| 审计防篡改 | UPDATE/DELETE 审计表失败，hash 链可校验 |
| 依赖漏洞 | Maven/npm audit 或 SCA 扫描无高危未处置 |

## 7. 分阶段补充路线

### 第一阶段：上线阻断修复

1. 整理 Flyway 迁移与 seed，修复表名、重复建表、方言问题。
2. 核心仓储落库：auth、partner、consumer、catalog、service、invoke log、quality、billing、audit。
3. API Key/secret 改为密文/KMS，移除默认生产凭证。
4. 补 `t_bill_item`、`t_catalog_application`、invoke log 扩展字段。
5. 补真实数据库集成测试和前端边界测试。

### 第二阶段：验收增强

1. 接入连接器合约测试和能力矩阵。
2. 目录元数据、血缘、质量摘要、使用统计。
3. 监管报表模板、导出、回执状态机。
4. 财务采购适配器和失败重试。
5. 质量报告持久化与导出。

### 第三阶段：生产强化

1. 大表分区、归档、冷热分层和容量规划。
2. Redis 分布式限流、配额和缓存。
3. Kafka/RabbitMQ/MinIO 真实环境演练。
4. 48 小时稳定性、故障注入、备份恢复。
5. 安全扫描、渗透测试、审计不可篡改验收。

## 8. 上线验收门禁

| 门禁 | 通过标准 |
|---|---|
| 功能门禁 | 46 条 FR 均有 API、页面或明确外部依赖说明 |
| 数据库门禁 | 空库/升级库迁移成功，目标表结构与库设一致 |
| 持久化门禁 | 主链路重启后数据不丢，可继续审批、调用、计费 |
| 安全门禁 | secret 密文、鉴权/签名/权限/审计全通过 |
| 测试门禁 | 单元、MockMvc、集成、E2E、前端边界全绿 |
| 性能门禁 | 达到业务确认的 TPS、延迟、批量接入和日志写入指标 |
| 合规门禁 | trace_id 可追溯，审计不可篡改，报表可生成 |
| 运维门禁 | 健康检查、指标、日志、告警、备份恢复方案可用 |

## 9. 当前结论

项目当前已完成开发环境主链路和主要页面/API 的闭环，适合继续进入“上线前工程化补强”阶段。上线前不建议只补页面或 Controller，而应优先解决数据库迁移一致性、核心仓储持久化、凭证密文、调用日志事实源、账单明细、目录申请、审计不可篡改和真实中间件集成测试。

最短上线路径是：先把 P0-01 至 P0-10 全部关闭，再以端到端脚本在真实依赖环境复跑 10 步主链路；通过后再做性能、安全、合规专项验收。
