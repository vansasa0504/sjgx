# GitHub 高 Star 参照功能方案与开发详细设计

> 生成日期：2026-06-27  
> 基础文档：`docs/implementation-gap-and-test-plan.md`、`docs/detailed-requirements-design.md`、`docs/database-design.md`。  
> 目标：对照当前审核缺口，参考 GitHub 高 star 成熟项目，为九大业务模块形成可落地的功能方案、开发详细设计和任务拆解。  
> 边界：本文只作为设计与开发任务依据，不要求引入下列开源项目作为生产依赖，不替换现有 Spring Boot 微服务、Vue 管理台、Flyway 迁移和当前模块边界。

## 1. 参考仓库与映射原则

### 1.1 参考仓库

| 功能域 | 参考仓库 | GitHub star | 参考能力 | 本项目映射 |
|---|---|---:|---|---|
| 认证/权限 | [Keycloak](https://github.com/keycloak/keycloak) | 35.1k | 身份管理、强认证、用户联合、细粒度授权 | 管理端用户、角色、权限码、消费方/合作方身份模型 |
| 外部数据接入 | [Airbyte](https://github.com/airbytehq/airbyte) | 21.5k | Connector、ELT/ETL、API/DB/File 数据同步 | SourceConnector 合约、多协议接入、offset/checkpoint |
| API 服务治理 | [Kong](https://github.com/Kong/kong) | 43.7k | API Gateway、路由、插件、限流、认证 | 数据服务注册、路由、签名、限流、熔断、调用日志 |
| 数据目录治理 | [DataHub](https://github.com/datahub-project/datahub) | 12.2k | 元数据平台、数据目录、血缘、治理 | 目录元数据、字段定义、血缘、质量摘要、申请审批 |
| 数据质量 | [Great Expectations](https://github.com/great-expectations/great_expectations) | 11.6k | Expectations、校验结果、质量文档 | 六维质量规则、规则套件、校验任务、报告 |
| 数据安全审计 | [Apache Ranger](https://github.com/apache/ranger) | 1.1k | 集中授权、策略、审计、安全管理 | 字段级权限、策略审计、审计不可篡改、KMS 约束 |
| 对象存储 | [MinIO](https://github.com/minio/minio) | 61.2k | S3 兼容对象存储、高性能对象归档 | 冷数据归档、数据资产、生命周期、销毁证明 |
| 计量计费 | [Lago](https://github.com/getlago/lago) | 10.1k | 用量计量、订阅、价格、账单、收入分析 | 调用量计费、账单明细、计量事件、财务对接 |
| 统计可视化 | [Apache Superset](https://github.com/apache/superset) | 73.5k | BI、数据探索、图表、大屏、数据库连接 | 统计大屏、监管报表、指标口径、导出 |

### 1.2 设计映射原则

1. 参照成熟能力，不照搬技术栈：只吸收领域模型、接口边界、测试方法和治理口径。
2. 以 P0 上线阻断项为主线：数据库一致性、持久化、安全、事实源、真实依赖验证优先。
3. 保持现有九大模块边界：partner、pipeline、catalog、consumer、storage、billing、stats、quality、auth/gateway 继续作为主要实现边界。
4. 配置与事实分离：规则、策略、凭证、权限是配置；调用、校验、账单、审计是事实。
5. 审计和 trace 贯穿全链路：合作方、接入、质量、资产、服务、消费方、计费、监管必须能按 trace_id 串起。

## 2. 总体开发架构方案

### 2.1 目标架构

```text
管理台 Vue
  -> Gateway/JWT
  -> auth/partner/pipeline/quality/billing 微服务
  -> JDBC Repository + Redis + MQ + MinIO
  -> Flyway 管理 MySQL/达梦/OceanBase 方言迁移
  -> audit/stats/report 统一证据链
```

### 2.2 横向能力

| 能力 | 开发方案 | 关键落点 |
|---|---|---|
| 统一身份 | 管理员走 JWT/RBAC；消费方和合作方走 API Key/签名/证书预留 | `t_user`、`t_role`、`t_permission`、`t_api_credential` |
| 统一持久化 | 所有核心 Repository 从内存升级为 JDBC 实现，内存只保留 test/profile | P0-04 |
| 统一 trace | 请求入口生成 trace_id，跨模块透传，写审计、调用日志、账单明细 | `t_audit_log.trace_id`、`t_service_invoke_log.trace_id` |
| 统一错误 | 业务异常返回 `code/message/traceId`，资源不存在用 404 语义 | Controller + `GlobalExceptionHandler` |
| 统一安全 | 密钥密文/KMS 引用，日志脱敏，审计 hash 链 | P0-05、P0-09 |
| 统一测试 | MockMvc + JDBC 集成 + 真实依赖 E2E + 安全/性能专项 | `src/test`、docker compose/Testcontainers |

## 3. 模块一：合作方管理

### 3.1 参考能力

参考 Keycloak 的身份主体、角色/权限、细粒度授权模型；参考 Ranger 的策略审计和安全策略变更留痕。合作方在本项目中不是普通资料表，而是外部数据来源主体，应拥有身份、接口、凭证、准入状态、服务质量和审计证据。

### 3.2 功能方案

| 功能 | 方案 |
|---|---|
| 合作方档案 | 管理 partner_code、名称、行业、数据类型、合规等级、联系人、协议材料、状态 |
| 准入生命周期 | REGISTERED -> SUBMITTED -> APPROVED/REJECTED -> ADMITTED -> RATED -> TERMINATED |
| 接口配置 | 每个合作方可配置多个接口，包含协议、endpoint、数据范围、频率、凭证密文 |
| 凭证治理 | 凭证只存密文或 KMS 引用，支持轮换、禁用、掩码展示 |
| 服务质量 | 健康检查、成功率、P95/P99、质量通过率、合规事件共同形成评级 |
| 审计事件 | 所有状态流转、接口变更、凭证轮换写 `t_partner_event` 和 `t_audit_log` |

### 3.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | Partner、PartnerInterface、PartnerEvent、PartnerHealthSnapshot、PartnerCredentialVersion |
| Repository | `PartnerRepository`、`PartnerInterfaceRepository`、`PartnerEventRepository` 补 JDBC 实现 |
| 数据表 | 复用 `t_partner/t_partner_interface/t_partner_event`；接口表补 `enabled/credential_version/last_rotated_at` |
| API | 保留现有 `/api/v1/partners`；新增健康检查查询、凭证轮换、接口启停 |
| 权限码 | `partner:view/create/update/approve/manage-credential` |
| 异常码 | `PARTNER-404`、`PARTNER-DUPLICATE`、`PARTNER-STATE-INVALID`、`PARTNER-CREDENTIAL-INVALID` |
| 事件 | `PartnerStatusChanged`、`PartnerInterfaceChanged`、`PartnerCredentialRotated` |

### 3.4 测试验证

1. MockMvc 覆盖合作方创建、重复编码、状态非法流转、不存在资源 404。
2. JDBC 集成测试验证 partner/interface/event 重启后仍可查。
3. 凭证测试验证明文不入库、不出日志、不返回前端。
4. 健康检查模拟超时、5xx、签名失败，验证评级和告警事件。

## 4. 模块二：外部数据接入

### 4.1 参考能力

参考 Airbyte 的 connector 思路：把不同来源的差异收敛到统一 SourceConnector 合约，任务调度只处理状态、offset、错误和数据批次，不把协议细节散落在 Controller 或 Service 中。

### 4.2 功能方案

| 功能 | 方案 |
|---|---|
| Connector 合约 | 定义 discover、check、read、checkpoint、close 五类能力 |
| 协议矩阵 | HTTP、WebService、SFTP/FTP、Kafka、MQ、DB、API Gateway 均以 connector 实现 |
| 格式转换 | JSON/XML/CSV/Excel 统一输出标准记录模型 |
| 同步策略 | FULL、INCREMENTAL、REALTIME、SCHEDULED、RESUME |
| 状态管理 | offset/checkpoint 持久化，任务失败可恢复，重复数据可幂等 |
| 上线管控 | approve 前必须完成连接测试、字段映射、质量规则、样例校验 |

### 4.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | IngestTask、SourceConnector、ConnectorSpec、ConnectorCheckResult、OffsetCheckpoint、RawDataBatch |
| Repository | `IngestTaskRepository`、`RawDataRepository`、`OffsetCheckpointRepository` 补 JDBC 实现 |
| 数据表 | `t_ingest_task/t_raw_data`；新增或扩展 `t_ingest_checkpoint(task_id, connector_type, offset_value, checkpoint_json, updated_at)` |
| API | 现有任务 API 保留；新增 connector spec 查询、连接测试详情、checkpoint 查询 |
| 权限码 | `ingest:view/create/update/approve/run` |
| 异常码 | `INGEST-CONNECT-FAILED`、`INGEST-MAPPING-MISSING`、`INGEST-RULE-MISSING`、`INGEST-STATE-INVALID` |
| 事件 | `IngestTaskTested`、`IngestTaskApproved`、`IngestBatchCompleted`、`IngestBatchFailed` |

### 4.4 测试验证

1. Connector contract test：每类 connector 必须覆盖 check/read/checkpoint/失败。
2. 格式转换测试覆盖坏文件、空值、编码、超大 CSV/Excel。
3. 恢复测试：执行中断后从 checkpoint 恢复，offset 单调递增。
4. 上线前置测试：缺映射、缺规则、连接失败均不能 approve。

## 5. 模块三：外部数据服务

### 5.1 参考能力

参考 Kong 的 API Gateway 能力，把服务治理拆成路由、认证、限流、插件式治理、日志和可观测性。本项目不引入 Kong，但在 `pipeline.service` 内建立等价的轻量治理点。

### 5.2 功能方案

| 功能 | 方案 |
|---|---|
| 服务注册 | 管理 service_code、name、route_key、version、status、输入输出定义 |
| 服务发布 | REGISTERED -> DEFINED -> TESTED -> PUBLISHED -> OFFLINE/ARCHIVED |
| API Key 签名 | 调用方只传 apiKey、timestamp、nonce、body、signature；secret 从仓储解密 |
| 限流熔断 | 按 service、consumer、apiKey、IP 四个维度限流，错误率触发熔断 |
| 调用日志 | 所有调用写 `t_service_invoke_log`，包含 trace_id、request_hash、error_code |
| 插件式治理点 | 鉴权、限流、脱敏、缓存、审计作为固定执行链 |

### 5.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | DataServiceDefinition、ServiceRoute、ApiCredential、InvokeContext、InvokeLog、RateLimitPolicy |
| Repository | `DataServiceRepository`、`ApiCredentialRepository`、`InvokeLogRepository` 补 JDBC 实现 |
| 数据表 | `t_data_service/t_api_credential/t_service_invoke_log`；日志表补 `trace_id/api_key/request_hash/error_code` |
| API | 保留 `/api/v1/services`；新增 API Key 管理、服务版本、限流策略查询 |
| 权限码 | `service:view/create/update/publish/offline/credential` |
| 异常码 | `SERVICE-404`、`SERVICE-NOT-PUBLISHED`、`SIGNATURE-INVALID`、`RATE-LIMITED`、`CIRCUIT-OPEN` |
| 事件 | `DataServicePublished`、`DataServiceInvoked`、`ApiCredentialRotated`、`RateLimitExceeded` |

### 5.4 测试验证

1. 签名测试覆盖正确、错误、过期、nonce 重放、body 篡改。
2. 服务状态测试覆盖未发布不可调用、下线不可调用。
3. 限流测试并发触发 429，窗口恢复后可调用。
4. 调用日志测试验证成功和失败均落库，billing/stats 可聚合。

## 6. 模块四：数据目录与预览

### 6.1 参考能力

参考 DataHub 的元数据优先思想：目录不是静态菜单，而是资产、字段、血缘、质量、使用、权限和申请审批的综合入口。

### 6.2 功能方案

| 功能 | 方案 |
|---|---|
| 目录资产 | 目录条目由接入任务、加工资产、合作方、质量结果共同生成 |
| 元数据 | 字段定义、数据类型、来源、更新时间、合规说明、使用限制 |
| 血缘 | 记录 raw_data -> data_asset -> catalog -> service 的链路 |
| 质量摘要 | 展示最近质量分、问题数、质量报告链接 |
| 数据预览 | 授权后返回样本，敏感字段动态脱敏，写审计 |
| 申请审批 | 申请落 `t_catalog_application`，审批后开通服务/API Key 或授权关系 |

### 6.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | DataCatalogItem、CatalogApplication、CatalogLineage、CatalogPreview、CatalogQualitySummary |
| Repository | `CatalogRepository`、`CatalogApplicationRepository`、`CatalogLineageRepository` 补 JDBC 实现 |
| 数据表 | `t_data_catalog/t_catalog_application`；新增 `t_catalog_lineage` 可选 |
| API | 保留 list/search/meta/preview/apply/approve；新增 lineage、quality-summary、usage-summary |
| 权限码 | `catalog:view/preview/apply/approve/manage` |
| 异常码 | `CATALOG-404`、`CATALOG-PREVIEW-FORBIDDEN`、`CATALOG-APPLICATION-STATE-INVALID` |
| 事件 | `CatalogPublished`、`CatalogPreviewed`、`CatalogApplied`、`CatalogApproved` |

### 6.4 测试验证

1. 搜索测试覆盖关键词、主题、合作方、数据类型、场景。
2. 预览测试覆盖未授权 403、敏感字段脱敏、审计落库。
3. 申请审批测试覆盖状态非法流转、重启后仍可查。
4. 血缘测试验证从服务可回溯到资产、接入任务、合作方。

## 7. 模块五：数据消费方管理

### 7.1 参考能力

参考 Keycloak 的客户端身份模型和 Kong 的 consumer/API Key 管理，把消费方作为可审计、可授权、可限额的调用主体。

### 7.2 功能方案

| 功能 | 方案 |
|---|---|
| 消费方档案 | 管理 consumer_code、业务条线、系统类型、联系人、合规等级、状态 |
| 生命周期 | REGISTERED -> SUBMITTED -> APPROVED -> QUOTA_CONFIGURED -> ACTIVE -> SUSPENDED/TERMINATED |
| 授权范围 | 按服务、目录、字段、时间窗口和用途控制 |
| 配额管理 | Redis 实时计数，DB 保留配额配置和周期快照 |
| API 凭证 | 每个消费方可持有多个 API Key，支持禁用、轮换、服务范围 |
| 行为审计 | 调用、申请、预览、权限变更、超额均写审计 |

### 7.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | Consumer、ConsumerQuota、ConsumerEvent、ConsumerAuthorization、ConsumerUsageSnapshot |
| Repository | `ConsumerRepository`、`ConsumerQuotaRepository`、`ConsumerEventRepository` 补 JDBC 实现 |
| 数据表 | `t_consumer/t_consumer_quota/t_consumer_event/t_api_credential`；新增授权表可选 |
| API | 保留 consumer API；新增授权范围、API Key 列表、配额使用量 |
| 权限码 | `consumer:view/create/update/quota/credential` |
| 异常码 | `CONSUMER-404`、`CONSUMER-STATE-INVALID`、`QUOTA-EXCEEDED`、`CONSUMER-NOT-ACTIVE` |
| 事件 | `ConsumerApproved`、`ConsumerQuotaChanged`、`ConsumerQuotaExceeded`、`ConsumerCredentialRotated` |

### 7.4 测试验证

1. 生命周期测试覆盖合法/非法状态转换。
2. Redis 并发扣减测试验证不超额。
3. API Key 轮换测试验证旧 key 禁用、新 key 生效。
4. 调用日志查询测试验证 consumer 能查到自己的服务调用记录。

## 8. 模块六：缓存、存储与再利用

### 8.1 参考能力

参考 MinIO 的对象存储和 S3 兼容接口思路：冷数据、原始文件、归档证据、报表文件不应全部塞进关系库，而应形成对象存储 + 元数据表的组合。

### 8.2 功能方案

| 功能 | 方案 |
|---|---|
| 热数据 | Redis/OceanBase 热表，服务调用优先走缓存 |
| 温数据 | 关系库保存结构化资产、目录、质量结果 |
| 冷数据 | MinIO 保存大文件、原始批次归档、报表、销毁证明 |
| 数据资产 | 加工后的资产写 `t_data_asset`，可发布到集市 |
| 生命周期 | ARCHIVE、RESTORE、DESTROY 均写 `t_lifecycle_record` 和审计 |
| 销毁证明 | 记录 proof_hash、operator、reason、object_key、retention_policy |

### 8.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | StoragePolicy、DataAsset、MarketplaceData、LifecycleRecord、ObjectReference |
| Repository | `DataAssetRepository`、`MarketplaceRepository`、`LifecycleRecordRepository` 补 JDBC 实现 |
| 存储接口 | `ObjectStorageClient` 抽象，默认 MinIO/S3 profile，测试用 fake |
| 数据表 | `t_storage_policy/t_data_asset/t_marketplace_data/t_lifecycle_record`；生命周期表补 operator/reason/proof_hash/object_key |
| API | 资产查询、发布集市、归档、恢复、销毁、证明查询 |
| 权限码 | `storage:view/manage/archive/destroy` |
| 异常码 | `ASSET-404`、`OBJECT-STORAGE-FAILED`、`LIFECYCLE-STATE-INVALID` |
| 事件 | `AssetCreated`、`AssetPublished`、`LifecycleActionCompleted` |

### 8.4 测试验证

1. MinIO 集成测试上传、下载、归档、恢复、删除对象。
2. 生命周期测试验证 ACTIVE/ARCHIVED/DESTROYED 状态不可逆约束。
3. 销毁证明测试验证 proof_hash 可校验，审计不可删除。
4. 缓存测试验证 TTL、主动失效、缓存命中率统计。

## 9. 模块七：计费管理

### 9.1 参考能力

参考 Lago 的 metering 和 usage-based billing 思路：计费事实来自不可变用量事件，账单由规则和账期聚合生成，而不是由前端传入 logs。

### 9.2 功能方案

| 功能 | 方案 |
|---|---|
| 用量事件 | `t_service_invoke_log` 是调用事实源，账单只从事实源聚合 |
| 计费规则 | 支持 COUNT、VOLUME、INTERFACE、PACKAGE、DURATION |
| 规则优先级 | consumer+service > partner+service > service default > global |
| 账单明细 | 每条 bill_item 记录服务、消费方、合作方、次数、流量、单价、金额 |
| 异议调整 | GENERATED -> CONFIRMED/DISPUTED -> ADJUSTED -> SETTLED |
| 财务适配 | BillExportAdapter、FinanceSyncAdapter、PurchaseContractAdapter |

### 9.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | BillingRule、UsageEvent、Bill、BillItem、BillAdjustment、FinanceSyncRecord |
| Repository | `BillingRuleRepository`、`BillRepository`、`BillItemRepository` 补 JDBC 实现 |
| 数据表 | `t_billing_rule/t_bill`；新增 `t_bill_item`，可选 `t_bill_adjustment/t_finance_sync_record` |
| API | 规则 CRUD、账单生成、账单确认、异议、调整、导出、财务同步 |
| 权限码 | `billing:view/rule/run/confirm/dispute/export` |
| 异常码 | `BILLING-RULE-NOT-FOUND`、`BILL-NO-USAGE`、`BILL-STATE-INVALID` |
| 事件 | `BillGenerated`、`BillConfirmed`、`BillDisputed`、`BillSyncedToFinance` |

### 9.4 测试验证

1. 规则匹配测试覆盖四级优先级、生效期、停用规则。
2. 账单生成测试从 invoke log 聚合，不允许请求传 logs 作为事实源。
3. 金额测试验证单价、次数、数据量、四舍五入和总额一致。
4. 财务同步测试 mock 成功、失败、重试、回执入库。

## 10. 模块八：统计监管

### 10.1 参考能力

参考 Superset 的指标探索、图表和报表思想；参考 Ranger 的审计追溯。统计监管模块应以事实表聚合为基础，监管报表应能导出、回执、追溯，而不是只返回 dashboard 快照。

### 10.2 功能方案

| 功能 | 方案 |
|---|---|
| 指标口径 | 接入量、调用量、成功率、P95、质量通过率、费用、缓存命中率 |
| 指标快照 | 定时从事实表聚合写 `t_stats_snapshot` |
| 监管报表 | 外部数据来源、个人信息使用、审批、共享复用、质量处置 |
| 报送流程 | 准备 -> 口径校验 -> 脱敏/加密 -> 生成文件 -> 报送 -> 回执 |
| 审计追溯 | trace_id 串起 partner/ingest/quality/catalog/service/consumer/billing |
| 大屏 | Vue 页面展示趋势、TopN、风险、告警、费用 |

### 10.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | StatsSnapshot、MetricDefinition、RegReport、ReportReceipt、AuditTrace |
| Repository | `StatsSnapshotRepository`、`RegReportRepository`、`AuditLogRepository` 补 JDBC 实现 |
| 数据表 | `t_stats_snapshot/t_audit_log`；新增 `t_reg_report/t_report_receipt` 可选 |
| API | dashboard、reports、report download、receipt、audit trace |
| 权限码 | `stats:view/report/export/audit` |
| 异常码 | `REPORT-NO-DATA`、`REPORT-GENERATE-FAILED`、`AUDIT-TRACE-NOT-FOUND` |
| 事件 | `StatsSnapshotGenerated`、`RegReportGenerated`、`RegReportSubmitted`、`RegReportReceiptReceived` |

### 10.4 测试验证

1. 指标聚合测试验证空数据、时间范围、维度过滤、TopN。
2. 报表测试验证脱敏、导出文件、失败重试、回执状态。
3. 审计追溯测试用一次 E2E trace 查询全链路事件。
4. 性能测试验证大时间范围审计查询命中索引。

## 11. 模块九：数据质量

### 11.1 参考能力

参考 Great Expectations 的 Expectation、Validation Result、Data Docs 思路：规则可复用、校验可追溯、结果可报告，质量问题能驱动修复闭环。

### 11.2 功能方案

| 功能 | 方案 |
|---|---|
| 规则套件 | 按目标对象和场景组织质量规则，支持启停和版本 |
| 六维规则 | 完整性、准确性、一致性、及时性、有效性、唯一性 |
| 校验任务 | 接入测试、正式接入、加工后、服务输出抽样、定时巡检 |
| 校验结果 | 保存总数、通过数、失败数、失败率、样例、checked_at |
| 问题闭环 | OPEN -> ASSIGNED -> FIXING -> VERIFYING -> CLOSED |
| 质量报告 | 按合作方、资产、服务、批次、时间周期生成报告 |

### 11.3 开发详细设计

| 类别 | 设计 |
|---|---|
| 核心对象 | QualityRule、QualityRuleSuite、QualityCheckTask、QualityCheckResult、QualityIssue、QualityReport |
| Repository | `QualityRuleRepository`、`QualityCheckResultRepository`、`QualityIssueRepository`、`QualityWeightRepository` 补 JDBC 实现 |
| 数据表 | `t_quality_rule/t_quality_check_result/t_quality_issue/t_quality_weight`；可新增 `t_quality_report` |
| API | 规则 CRUD、规则套件、校验触发、问题分派/解决、报告、评分 |
| 权限码 | `quality:view/rule/create/check/issue/report` |
| 异常码 | `QUALITY-RULE-404`、`QUALITY-DIMENSION-INVALID`、`QUALITY-ISSUE-STATE-INVALID` |
| 事件 | `QualityCheckCompleted`、`QualityIssueCreated`、`QualityIssueResolved`、`QualityReportGenerated` |

### 11.4 测试验证

1. 六维规则单测覆盖必填、正则、范围、唯一、跨字段一致性、时效。
2. 结果持久化测试验证 checked_at 排序取最新。
3. 问题状态机测试覆盖非法流转和超时告警。
4. 报告测试验证按合作方/服务/批次聚合，质量评分影响目录展示和合作方评级。

## 12. 数据库与迁移详细设计

### 12.1 P0 迁移修复

| 编号 | 设计 |
|---|---|
| DB-01 | 合并 `V001` 与 `V010` 身份权限建表定义，消除 `t_user/t_role` 字段不一致 |
| DB-02 | 为 MySQL、达梦、OceanBase 分方言处理 ID、TEXT/CLOB、时间更新、外键策略 |
| DB-03 | 修复 `U010` 中 `t_data_catalog_item` 为 `t_data_catalog` |
| DB-04 | `t_api_credential.secret` 改为 `secret_cipher` 或明确只存 KMS 引用 |
| DB-05 | `t_service_invoke_log` 增 `trace_id/api_key/request_hash/error_code` |
| DB-06 | 新增 `t_bill_item`，支撑账单明细 |
| DB-07 | 新增 `t_catalog_application`，支撑目录申请审批事实源 |
| DB-08 | `t_audit_log` 增 `prev_hash/current_hash`，形成 hash 链 |

### 12.2 Repository 落库顺序

1. auth：用户、角色、权限。
2. partner/consumer：主数据和状态事件。
3. service：服务定义、API 凭证、调用日志。
4. catalog：目录和申请。
5. billing：规则、账单、明细。
6. quality：规则、结果、问题、权重。
7. audit/stats：审计、指标、报表。
8. storage：资产、集市、生命周期。

## 13. 端到端数据流详设

```text
1. 管理员登录 -> JWT + permissions
2. 创建合作方 -> 接口配置 -> 凭证密文
3. 创建接入任务 -> connector check -> mapping/rule 校验
4. 执行接入 -> raw_data -> quality check -> issue/report
5. 加工资产 -> data_asset -> catalog -> lineage
6. 消费方申请目录 -> 审批 -> 授权/API Key
7. 服务发布 -> invoke -> signature/rate limit/cache/mask
8. invoke_log -> bill_item -> bill -> finance sync
9. stats_snapshot -> dashboard/report
10. audit_log + trace_id -> 监管追溯
```

## 14. 可拆解任务清单

### 14.1 P0 上线阻断

| 编号 | 任务 | 验收 |
|---|---|---|
| P0-01 | 修复 Flyway 迁移冲突和 seed 表名错误 | 空库/旧库迁移成功 |
| P0-02 | 增加国产库方言策略或脚本拆分 | MySQL/达梦/OceanBase 迁移验证 |
| P0-03 | 核心 Repository JDBC 落库 | 重启后主链路数据不丢 |
| P0-04 | API secret 密文/KMS，移除默认生产凭证 | 表、日志、响应无明文 secret |
| P0-05 | 调用日志扩展并落库 | trace_id 可串 billing/stats/audit |
| P0-06 | 新增 `t_bill_item` 并改账单从日志聚合 | 账单明细与总额一致 |
| P0-07 | 新增 `t_catalog_application` | 目录申请审批可持久查询 |
| P0-08 | 审计 hash 链和禁止篡改策略 | UPDATE/DELETE 失败或 hash 校验失败可发现 |
| P0-09 | 前端边界测试补齐 | 空态、失败态、校验态、按钮 API 断言 |
| P0-10 | 真实依赖 E2E | 10 步主链路在真实 DB/Redis/MQ/MinIO profile 通过 |

### 14.2 P1 验收增强

| 编号 | 任务 | 验收 |
|---|---|---|
| P1-01 | Connector 合约和能力矩阵 | 每类协议 contract test 通过 |
| P1-02 | 目录血缘、质量摘要、使用统计 | 目录详情完整展示并可追溯 |
| P1-03 | 质量报告持久化与导出 | 按合作方/资产/服务生成报告 |
| P1-04 | 监管报表模板和回执状态机 | 报文生成、脱敏、回执入库 |
| P1-05 | 财务采购适配器 | mock 财务系统成功/失败/重试 |

### 14.3 P2 生产强化

| 编号 | 任务 | 验收 |
|---|---|---|
| P2-01 | 大表分区和归档 | invoke/audit/raw 查询命中索引/分区 |
| P2-02 | 压测与容量规划 | P95/P99、TPS、批量接入达标 |
| P2-03 | 故障演练 | DB/Redis/MQ/MinIO 故障有降级或恢复策略 |
| P2-04 | 安全扫描和渗透测试 | 无高危未处置 |
| P2-05 | 备份恢复和审计验收 | 数据可恢复，审计链可校验 |

## 15. 测试总方案

| 测试层级 | 覆盖内容 |
|---|---|
| 单元测试 | 状态机、规则引擎、签名验签、脱敏加密、格式转换、账单金额 |
| MockMvc | 所有 Controller 的 200/401/403/400/404/409 |
| JDBC 集成 | Repository CRUD、事务、唯一约束、重启恢复 |
| 中间件集成 | Redis 限流、Kafka/RabbitMQ 接入、MinIO 归档 |
| E2E | 10 步主链路 + 重启恢复 + 调用日志到账单聚合 |
| 性能 | 服务调用 P95/P99、100 万行批量接入、日志聚合、目录搜索 |
| 安全 | 鉴权绕过、签名重放、secret 泄露、SQL 注入、审计篡改 |

## 16. 不做事项

1. 不引入 Keycloak/Airbyte/Kong/DataHub/Great Expectations/Ranger/MinIO/Lago/Superset 整套系统作为强依赖。
2. 不替换现有微服务模块边界。
3. 不修改 `.env`、证书、真实密钥、生产部署脚本。
4. 不在方案阶段直接改业务代码。
5. 不用前端或请求体传入的数据作为计费、审计、监管事实源。

## 17. 结论

当前项目已有开发环境功能闭环，但生产级上线的核心矛盾是事实源、持久化、安全和真实依赖验证不足。参考高 star 项目的成熟设计后，最稳妥路径是先关闭 P0 阻断项，再补 P1 验收增强，最后进入 P2 生产强化。本文可直接作为下一轮 `tasks/codex-task` 的需求来源，也可作为 Claude Code 审查上线前补强任务的验收基准。
