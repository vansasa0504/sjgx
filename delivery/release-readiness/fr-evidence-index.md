# FR-101~FR-906 证据索引

> 当前发布层结论：全部条目均为 `BLOCKED`。下列 `DEV_TEST`/`DEV_REVIEW` 只证明开发层覆盖，不证明生产验收。共同事实源：`delivery/acceptance-report.md`、`delivery/p1-acceptance-summary.md`。

## 1. 合作方管理（FR-101~FR-104）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-101 | 生命周期及审核/退出留痕 | E-FR-101-01：`platform-partner/src/test/java/com/platform/partner/PartnerServiceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 在 PROD_EQ 走通全状态并归档审计原始记录 |
| FR-102 | 分类分级与标签 | E-FR-102-01：`platform-partner/src/test/java/com/platform/partner/PartnerModuleMockMvcTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 用机构分级规则和授权数据验收 |
| FR-103 | 接口权限、范围、频次和凭证保护 | E-FR-103-01：`platform-partner/src/test/java/com/platform/partner/PartnerJdbcRepositoryTest.java` | DEV_TEST | BLOCKED | 安全负责人 | 核对目标环境权限与受控凭证配置 |
| FR-104 | 可用性/响应/准确率/合规监控告警 | E-FR-104-01：`platform-partner/src/test/java/com/platform/partner/PartnerServiceTest.java` | DEV_TEST | BLOCKED | 运维负责人 | 在 PROD_EQ 注入越阈值样本并验证告警闭环 |

## 2. 外部数据接入（FR-201~FR-205）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-201 | HTTP/WebService/SFTP/FTP/Kafka/MQ/DB/API 网关与 JSON/XML/CSV/Excel | E-FR-201-01：`platform-pipeline/src/test/java/com/platform/pipeline/ingest/adapter/ProtocolAdaptersTest.java`；E-FR-201-02：`platform-pipeline/src/test/java/com/platform/pipeline/ingest/converter/FormatConvertersTest.java` | DEV_TEST | BLOCKED | 集成测试负责人 | 在获授权 PROD_EQ 对各真实协议/格式至少验收 1 例 |
| FR-202 | 标准/定制接入和字段映射 | E-FR-202-01：`platform-pipeline/src/test/java/com/platform/pipeline/ingest/IngestServiceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 以机构标准及定制样例完成验收 |
| FR-203 | 申请至下线、审批和版本全流程 | E-FR-203-01：`platform-pipeline/src/test/java/com/platform/pipeline/PipelineModuleMockMvcTest.java` | DEV_TEST | BLOCKED | 业务验收人 | PROD_EQ 走通审批、版本、下线及审计链 |
| FR-204 | 增量/全量/推送/拉取和调度 | E-FR-204-01：`platform-pipeline/src/test/java/com/platform/pipeline/ingest/sync/SyncStrategyTest.java` | DEV_TEST | BLOCKED | 集成测试负责人 | 对真实数据源执行四模式与调度验收 |
| FR-205 | 中断后的断点续传 | E-FR-205-01：`platform-pipeline/src/test/java/com/platform/pipeline/ingest/sync/JdbcOffsetStoreTest.java` | DEV_TEST | BLOCKED | 集成测试负责人 | 百万条真实 connector 中断恢复并校验无重漏 |

## 3. 外部数据服务（FR-301~FR-305）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-301 | 服务注册、测试、发布、迭代和下线 | E-FR-301-01：`platform-pipeline/src/test/java/com/platform/pipeline/service/DataServiceManagerTest.java` | DEV_TEST | BLOCKED | 业务验收人 | PROD_EQ 走通服务全生命周期并留痕 |
| FR-302 | 标准服务/定制接口封装 | E-FR-302-01：`platform-pipeline/src/test/java/com/platform/pipeline/service/DataServiceControllerTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 用机构接口样例验证契约和错误边界 |
| FR-303 | 路由、负载、限流、熔断、重试 | E-FR-303-01：`platform-pipeline/src/test/java/com/platform/pipeline/service/DataServiceManagerTest.java` | DEV_TEST | BLOCKED | 运维负责人 | PROD_EQ 多副本与依赖故障演练 |
| FR-304 | 角色/业务系统细粒度调用权限 | E-FR-304-01：`platform-pipeline/src/test/java/com/platform/pipeline/PipelineModuleMockMvcTest.java` | DEV_TEST | BLOCKED | 安全负责人 | 补齐字段级 ABAC 后执行越权矩阵；当前功能缺口 |
| FR-305 | 请求、响应、耗时、状态日志追溯 | E-FR-305-01：`platform-common/src/test/java/com/platform/common/log/JdbcServiceInvokeLogRepositoryTest.java` | DEV_TEST | BLOCKED | 合规负责人 | PROD_EQ 核对全量、脱敏、trace 与留存策略 |

## 4. 数据目录与预览（FR-401~FR-405）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-401 | 多维目录分类和浏览 | E-FR-401-01：`platform-pipeline/src/test/java/com/platform/pipeline/catalog/CatalogControllerTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 使用机构目录数据执行浏览验收 |
| FR-402 | 元信息完整性 | E-FR-402-01：`platform-pipeline/src/test/java/com/platform/pipeline/catalog/CatalogServiceTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 抽样核对字段、来源、合规和限制元数据 |
| FR-403 | 授权范围内预览/统计/质量报告 | E-FR-403-01：`platform-pipeline/src/test/java/com/platform/pipeline/catalog/CatalogGovernanceServiceTest.java` | DEV_TEST | BLOCKED | 安全负责人 | PROD_EQ 执行正反授权与脱敏预览 |
| FR-404 | 关键词/标签/场景检索和规则推荐 | E-FR-404-01：`platform-pipeline/src/test/java/com/platform/pipeline/catalog/CatalogServiceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 以机构样本验证准确性和无结果边界 |
| FR-405 | 在线申请、审批和自动开权 | E-FR-405-01：`platform-pipeline/src/test/java/com/platform/pipeline/catalog/CatalogApplicationRepositoryJdbcTest.java` | DEV_TEST | BLOCKED | 业务验收人 | PROD_EQ 走通批准/驳回及权限生效/回收 |

## 5. 消费方管理（FR-501~FR-505）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-501 | 注册至注销全生命周期 | E-FR-501-01：`platform-partner/src/test/java/com/platform/partner/consumer/ConsumerServiceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | PROD_EQ 走通状态、审批及审计记录 |
| FR-502 | 业务/系统/合规分类分级 | E-FR-502-01：`platform-partner/src/test/java/com/platform/partner/consumer/ConsumerControllerTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 按机构规则验证差异化权限 |
| FR-503 | 频次/数据量/范围配额、预警和拦截 | E-FR-503-01：`platform-partner/src/test/java/com/platform/partner/consumer/ConsumerServiceTest.java` | DEV_TEST | BLOCKED | 运维负责人 | PROD_EQ 多副本验证配额精度及告警 |
| FR-504 | 行为全量审计和违规预警 | E-FR-504-01：`platform-partner/src/test/java/com/platform/partner/PartnerModuleMockMvcTest.java` | DEV_TEST | BLOCKED | 合规负责人 | 用授权违规样本验证发现、追溯、处置 |
| FR-505 | 质量与性能反馈 | E-FR-505-01：`delivery/acceptance-report.md` | DEV_REVIEW | BLOCKED | 业务验收人 | 补可定位的 PROD_EQ 反馈闭环原始证据 |

## 6. 缓存存储再利用（FR-601~FR-606）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-601 | 接口/热点/全量缓存策略 | E-FR-601-01：`platform-pipeline/src/test/java/com/platform/pipeline/storage/StorageServiceTest.java` | DEV_TEST | BLOCKED | 运维负责人 | PROD_EQ 验证更新、失效、命中和容量 |
| FR-602 | 热/温/冷分层存储 | E-FR-602-01：`platform-pipeline/src/test/java/com/platform/pipeline/storage/StorageServiceTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 对真实存储拓扑验证迁移和读取 |
| FR-603 | SM4、传输加密、动态/静态脱敏 | E-FR-603-01：`platform-common/src/test/java/com/platform/common/security/Sm4UtilTest.java`；E-FR-603-02：`platform-common/src/test/java/com/platform/common/security/DesensitizeUtilTest.java` | DEV_TEST | BLOCKED | 安全负责人 | 补 TLS 和目标环境密钥/脱敏配置证据 |
| FR-604 | 清洗、标准化、关联和标签加工 | E-FR-604-01：`platform-pipeline/src/test/java/com/platform/pipeline/storage/StorageServiceTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 以机构数据核对加工正确性和血缘 |
| FR-605 | 数据集市共享复用 | E-FR-605-01：`platform-pipeline/src/test/java/com/platform/pipeline/storage/StorageServiceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | PROD_EQ 验证共享授权与二次使用 |
| FR-606 | 留存、归档和销毁留痕 | E-FR-606-01：`delivery/p2-05-report.md` | DEV_REVIEW | BLOCKED | 合规负责人 | 接入生产销毁调用链并抽样证明/审批记录 |

## 7. 计费管理（FR-701~FR-705）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-701 | 多维计费模型 | E-FR-701-01：`platform-billing/src/test/java/com/platform/billing/BillingGovernanceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 用签署计费口径执行 PROD_EQ 计算验收 |
| FR-702 | 合作方/服务/消费方差异化规则 | E-FR-702-01：`platform-billing/src/test/java/com/platform/billing/BillingGovernanceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | 验证冲突、优先级和生效范围 |
| FR-703 | 周期账单、核对、异议和调整审批 | E-FR-703-01：`platform-billing/src/test/java/com/platform/billing/BillingModuleMockMvcTest.java` | DEV_TEST | BLOCKED | 财务验收人 | PROD_EQ 走通生成、异议、调整和审批留痕 |
| FR-704 | 费用明细、趋势和占比 | E-FR-704-01：`platform-billing/src/test/java/com/platform/billing/StatsControllerTest.java` | DEV_TEST | BLOCKED | 财务验收人 | 以基准账单对账统计精度和大数据量表现 |
| FR-705 | 财务/采购系统真实对接 | E-FR-705-01：`delivery/p1-acceptance-summary.md`（仅 Mock） | EXTERNAL_DEPENDENCY | BLOCKED | 机构接口负责人 | 提供正式规范/测试身份并完成真实联调；规范未提供 |

## 8. 统计监管（FR-801~FR-805）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-801 | 全链路指标和趋势 | E-FR-801-01：`platform-billing/src/test/java/com/platform/billing/StatsControllerTest.java` | DEV_TEST | BLOCKED | 运维负责人 | PROD_EQ 核对指标口径、实时性和完整性 |
| FR-802 | 监管报表配置、生成和导出 | E-FR-802-01：`platform-billing/src/test/java/com/platform/billing/report/RegulatoryReportServiceTest.java` | DEV_TEST | BLOCKED | 合规负责人 | 用机构模板和脱敏样本验收 |
| FR-803 | 监管系统真实报送和回执 | E-FR-803-01：`delivery/p1-acceptance-summary.md`（仅 Mock） | EXTERNAL_DEPENDENCY | BLOCKED | 机构接口负责人 | 提供监管规范/测试环境并完成真实联调；规范未提供 |
| FR-804 | 全生命周期操作审计追溯 | E-FR-804-01：`platform-common/src/test/java/com/platform/common/audit/AuditLogRepositoryTest.java` | DEV_TEST | BLOCKED | 合规负责人 | PROD_EQ 核对 trace、不可篡改和三年留存 |
| FR-805 | 运行/服务/合规/成本大屏 | E-FR-805-01：`platform-ui/src/views/__tests__/m7c-pages.test.ts` | DEV_TEST | BLOCKED | 业务验收人 | 浏览器/移动端真实渲染及指标一致性验收 |

## 9. 数据质量管理（FR-901~FR-906）

| FR | 能力/验收动作 | 开发证据 ID、路径 | 环境 | 状态 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|
| FR-901 | 接入至服务全链路监测预警 | E-FR-901-01：`platform-quality/src/test/java/com/platform/quality/QualityServiceTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | PROD_EQ 注入全环节质量异常并验证告警 |
| FR-902 | 六维规则配置 | E-FR-902-01：`platform-quality/src/test/java/com/platform/quality/QualityControllerTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 用机构规则验证配置、版本和权限 |
| FR-903 | 自动校验异常/缺失/错误/重复 | E-FR-903-01：`platform-quality/src/test/java/com/platform/quality/QualityModuleMockMvcTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 用基准数据集核对识别率和误报 |
| FR-904 | 发现、工单、整改、验证、复盘闭环 | E-FR-904-01：`platform-quality/src/test/java/com/platform/quality/QualityServiceTest.java` | DEV_TEST | BLOCKED | 业务验收人 | PROD_EQ 走通闭环及超时告警 |
| FR-905 | 合作方/类型/时间质量报告 | E-FR-905-01：`platform-quality/src/test/java/com/platform/quality/report/QualityReportServiceTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 对基准数据核对报告内容和导出 |
| FR-906 | 六维加权评分评级 | E-FR-906-01：`platform-quality/src/test/java/com/platform/quality/QualityServiceTest.java` | DEV_TEST | BLOCKED | 数据治理负责人 | 核对权重、边界和评级可重复性 |

## 10. 汇总结论与校验记录

- NFR 基线口径统一为 24 个唯一 NFR 编号，拆分为 27 条原子门禁；本文件的主体仍按 46 条 FR 建立证据索引。

- 已逐项列出 46 条 FR；FR-705、FR-803 明确为外部规范缺失/未联调，未标 PASS。
- 当前 46 条发布状态均为 `BLOCKED`；没有 `PASS`、`FAIL` 或 `WAIVED`。
- 本次仅检查文档结构、编号和仓库路径；未重新运行任何开发测试，也未执行生产等价验证。
