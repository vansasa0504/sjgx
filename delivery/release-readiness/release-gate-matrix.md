# Release-01 上线门禁矩阵

> **总状态：`NOT_READY / BLOCKED`。** 判定规则：任一 `FAIL`，或任一未取得完整书面豁免的阻断 `BLOCKED`，均不准入。本矩阵当前无 `PASS`、无 `WAIVED`。

## 1. 流程 §9 与补充发布门禁

| Gate ID | 来源 | 原始阈值/规则 | PASS 最低证据及目标环境 | 现有证据 | 状态 | 阻断级/原因 | 责任角色 | 下一步/到期日 |
|---|---|---|---|---|---|---|---|---|
| G-FUNC-01 | 流程 §9；FR-101~906 | 九大模块主链路可用，46 FR 有证据 | PROD_EQ 功能报告、原始输出、业务签署 | `fr-evidence-index.md`；`delivery/acceptance-report.md`（DEV） | BLOCKED | 阻断；无 PROD_EQ 全链路与外部联调证据 | 业务验收人 | 执行 `production-validation-plan.md` 的 `PV-FUNC`；TBD |
| G-DB-01 | 流程 §9 | 空库/升级库迁移成功，目标结构与库设一致 | 目标数据库 PROD_EQ 空库+升级库原始迁移输出与结构对比 | `reviews/claude-review-P2-01-返工复审.md`（DEV_REVIEW） | BLOCKED | 阻断；达梦/OceanBase 和在线升级未实测 | DBA/测试负责人 | 执行 `production-validation-plan.md` 的 `PV-DB` 三方比对；TBD |
| G-PERSIST-01 | 流程 §9 | 主链路数据重启后不丢 | PROD_EQ 重启前后数据、约束、事务和一致性原始记录 | `delivery/p2-05-report.md`（DEV/Testcontainers） | BLOCKED | 阻断；目标存储拓扑未验证 | DBA/测试负责人 | 执行 `production-validation-plan.md` 的 `PV-RESTORE` 主链路重启维度；TBD |
| G-SEC-01 | 流程 §9 | secret 密文、鉴权、签名、审计防篡改通过 | PROD_EQ 安全配置核查+授权扫描+审计留存/不可篡改证据 | `security/p2-04-report.md`（DEV） | BLOCKED | 阻断；功能缺口、SCA/DAST/TLS/等保缺证 | 安全负责人 | 关闭 `gap-and-dependency-register.md` 安全阻断；TBD |
| G-TEST-01 | 流程 §9 | 单元、MockMvc、集成、E2E、前端边界全绿 | 发布 commit 的受控 CI 全量结果；环境型 E2E 在 PROD_EQ 通过 | `delivery/p1-acceptance-summary.md`；P2 审查（DEV） | BLOCKED | 阻断；无本发布基线受控 CI 与 PROD_EQ E2E | 测试负责人 | 冻结候选版本后执行批准测试集；TBD |
| G-PERF-01 | 流程 §9 | P95/P99、TPS、批量接入、日志聚合达标 | PROD_EQ/第三方原始压测结果和资源监控 | `perf/p2-02-report.md`（未实测） | BLOCKED | 阻断；NFR-P01~P07 与日志聚合均无达标数据 | 性能测试负责人 | 执行 `production-validation-plan.md` 的 `PV-PERF`，含日志写入吞吐/采集延迟/丢失率/积压量/查询时延；TBD |
| G-COMP-01 | 流程 §9；NFR-C01~C03 | 目标国产环境、外部系统、浏览器/移动端兼容 | PROD_EQ 兼容矩阵和真实联调记录 | `delivery/acceptance-report.md`（DEV） | BLOCKED | 阻断；目标环境与规范缺失 | 集成测试负责人 | 执行 PV-COMP/外部联调；TBD |
| G-COMPLY-01 | 流程 §9；NFR-G01 | trace 可追溯，监管报表可生成并满足合规 | PROD_EQ trace/报表证据+机构/第三方合规材料 | `delivery/p1-acceptance-summary.md`（Mock/DEV） | BLOCKED | 阻断；真实报送与测评缺失 | 合规负责人 | 提供规范、完成联调和合规审查；TBD |
| G-OPS-01 | 流程 §9；NFR-M01 | 健康、日志、指标、告警、备份恢复可用 | PROD_EQ 运维演练、告警工单、恢复原始记录 | `delivery/ops-manual.md`；`delivery/p2-05-report.md`（文档/DEV） | BLOCKED | 阻断；目标环境运维闭环未演练 | 运维负责人 | 执行稳定性、告警、备份恢复；TBD |
| G-DOC-01 | 原始 §6.3；NFR-M01/U01 | 文档完整、准确、可操作、版本一致，培训完成 | 候选版本文档审查+培训/考核受控记录 | `delivery/release-readiness/delivery-service-evidence.md` | BLOCKED | 阻断；培训、合同、服务证据缺失 | 交付负责人 | 完成交付清单并取得签署；TBD |
| G-ROLLBACK-01 | NFR-A05；发布要求 | 滚动无中断、回滚≤10min，角色和触发条件明确 | PROD_EQ 升级/回退计时原始记录+审批 | `delivery/upgrade-rollback-drill.md`；脚本仅就绪 | BLOCKED | 阻断；未实测、无变更窗口 | 运维负责人 | 执行 PV-AVAIL 的升级回退卡；TBD |

## 2. NFR 原子门禁

| Gate ID | 来源 | 原始阈值/规则 | PASS 最低证据及目标环境 | 现有证据 | 状态 | 阻断级/原因 | 责任角色 | 下一步/到期日 |
|---|---|---|---|---|---|---|---|---|
| G-P01-STD | NFR-P01 | 标准接口平均≤200ms、P95≤500ms、P99≤1s | PROD_EQ 原始 JMeter+监控 | perf/p2-02-report.md | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P01-CUSTOM | NFR-P01 | 定制接口平均≤500ms、P95≤1s | PROD_EQ 原始 JMeter+监控 | perf/p2-02-report.md | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P02 | NFR-P02 | ≥1000TPS，峰值≥2000TPS，线性扩展 | PROD_EQ 1/3/5/8 副本阶梯结果 | `perf/p2-02-report.md` | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P03 | NFR-P03 | 单批≥100万条、≥100MB/s、断点续传 | 真实 connector PROD_EQ 中断恢复结果 | `perf/p2-02-report.md` | BLOCKED | 阻断；connector 批量能力限制且未实测 | 接入负责人 | 补能力后 PV-PERF；TBD |
| G-P04 | NFR-P04 | 日均≥10亿条，单节点≥1万条/秒 | PROD_EQ 等比例接入结果 | `perf/p2-02-report.md` | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P05 | NFR-P05 | 日均≥5亿条，加工延迟≤5min | PROD_EQ ETL 原始数据和监控 | `perf/p2-02-report.md` | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P06-HIT | NFR-P06 | 热点缓存命中率≥90% | PROD_EQ Redis Cluster 命中统计原始结果 | perf/p2-02-report.md | BLOCKED | 阻断；仅逻辑单测 | 性能测试负责人 | PV-PERF；TBD |
| G-P06-LAT | NFR-P06 | 缓存查询≤10ms | PROD_EQ Redis Cluster 查询时延原始结果 | perf/p2-02-report.md | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P06-CAP | NFR-P06 | 缓存容量≥10TB且可水平扩展 | PROD_EQ Redis Cluster 容量/扩展原始结果 | perf/p2-02-report.md | BLOCKED | 阻断；未实测 | 性能测试负责人 | PV-PERF；TBD |
| G-P07 | NFR-P07 | 千万级≤2s、亿级≤5s | 目标 DB PROD_EQ 查询与 EXPLAIN 原始记录 | `perf/p2-02-report.md` | BLOCKED | 阻断；无规模实测 | DBA/性能测试负责人 | PV-PERF；TBD |
| G-A01 | NFR-A01 | ≥99.95%，年计划外停机≤4.38h；48h 稳定 | PROD_EQ 48h 监控/事件原始记录 | `delivery/p2-03-report.md` | BLOCKED | 阻断；未执行 48h | 运维负责人 | PV-STABLE；TBD |
| G-A02 | NFR-A02 | 核心≥99.99%，单服务故障不影响整体 | PROD_EQ 服务/依赖故障注入记录 | `delivery/p2-03-report.md`（DEV） | BLOCKED | 阻断；仅单测/门控 IT | 运维负责人 | PV-AVAIL；TBD |
| G-A03 | NFR-A03 | 单节点≤30s、集群≤5min、零丢失 | PROD_EQ K8s/DB 故障计时及一致性 | `delivery/p2-03-report.md` | BLOCKED | 阻断；脚本未在目标环境执行 | 运维负责人/DBA | PV-AVAIL；TBD |
| G-A04 | NFR-A04 | 同城双活，RPO≤5min，RTO≤30min | PROD_EQ 双活切换和数据校验 | `delivery/p2-03-report.md`；`delivery/p2-05-report.md` | BLOCKED | 阻断；未实测 | 灾备负责人 | PV-AVAIL/PV-RESTORE；TBD |
| G-A05 | NFR-A05 | 灰度/滚动无中断，回滚≤10min | PROD_EQ 升级回退计时与健康记录 | `delivery/p2-03-report.md` | BLOCKED | 阻断；未实测 | 运维负责人 | PV-AVAIL；TBD |
| G-S01 | NFR-S01 | MFA、IAM/SSO、RBAC+字段 ABAC、OAuth2/API Key/证书 | PROD_EQ 权限矩阵、IAM 联调、证书认证结果 | `security/p2-04-report.md` | BLOCKED | 阻断；MFA/IAM/ABAC/证书为功能缺口 | 安全负责人 | 独立实现后 PV-SEC；TBD |
| G-S02 | NFR-S02 | TLS1.2+、SM4、动态/静态脱敏、审计≥3年不可篡改 | PROD_EQ 配置/密码学/审计留存证据 | `delivery/p2-05-report.md`（DEV） | BLOCKED | 阻断；TLS、留存、DB 禁改缺证 | 安全/合规负责人 | PV-SEC/PV-RESTORE；TBD |
| G-S03 | NFR-S03 | SQLi/XSS/CSRF/防刷、等保三级 | 后端 SCA、授权 DAST、流量控制及第三方等保材料 | `security/p2-04-report.md` | BLOCKED | 阻断；SCA/DAST/等保未完成 | 安全负责人 | PV-SEC；TBD |
| G-E01 | NFR-E01 | 微服务/容器/弹性/水平扩展且无中断 | PROD_EQ 架构评审和扩缩容实测 | `delivery/system-architecture.md`（设计） | BLOCKED | 阻断；设计不能替代实测 | 架构负责人 | 执行 `production-validation-plan.md` 的 `PV-ARCH` E01；TBD |
| G-E02 | NFR-E02 | SDK；新接口≤3工作日；80%可视化；开放 API/插件 | PROD_EQ 接入演练、覆盖统计和业务签署 | `delivery/dev-guide.md`（文档） | BLOCKED | 阻断；无验收统计 | 架构/业务验收人 | 执行 `production-validation-plan.md` 的 `PV-ARCH` E02；TBD |
| G-E03 | NFR-E03 | 分布式、PB 级、多引擎 | PROD_EQ 架构评审和容量/引擎验证 | `delivery/system-architecture.md`（设计） | BLOCKED | 阻断；无 PB/多引擎实证 | 架构负责人 | 执行 `production-validation-plan.md` 的 `PV-ARCH` E03；TBD |
| G-C01 | NFR-C01 | 麒麟/UOS、达梦/OceanBase、X86/ARM、多部署模式 | 目标环境兼容矩阵原始结果 | `delivery/acceptance-report.md` | BLOCKED | 阻断；实际环境未测 | 兼容测试负责人 | PV-COMP；TBD |
| G-C02 | NFR-C02 | 中台/大数据/核心/风控/营销/财务/认证对接 | 各真实系统联调记录 | `delivery/p1-acceptance-summary.md`（Mock） | BLOCKED | 阻断；规范/环境缺失 | 机构接口负责人 | 提供规范后联调；TBD |
| G-C03 | NFR-C03 | Chrome/Edge/Firefox 最新版、移动端监控预警 | 目标版本浏览器/设备兼容矩阵 | `delivery/acceptance-report.md`（前端单测） | BLOCKED | 阻断；未实测 | 兼容测试负责人 | PV-COMP；TBD |
| G-M01 | NFR-M01 | 全链路监控、集中日志、可视化运维、五类文档 | PROD_EQ 运维演练+版本文档审查 | `delivery/ops-manual.md`；五类文档 | BLOCKED | 阻断；运维环境闭环/服务材料缺失 | 运维/交付负责人 | PV-OPS + 交付签署；TBD |
| G-U01 | NFR-U01 | 易用；培训≤3工作日；引导/个性化/批量模板 | 易用性测试+培训考核受控记录 | `delivery/user-guide.md` | BLOCKED | 阻断；培训/考核和完整易用性证据缺失 | 培训/业务验收人 | 执行易用性和培训验收；TBD |
| G-G01 | NFR-G01 | 法规/金融规范、全生命周期合规 | 机构合规审查+第三方/受控材料 | `security/p2-04-report.md`（开发差距） | BLOCKED | 阻断；测评和真实生命周期证据缺失 | 合规负责人 | PV-SEC/RESTORE + 合规审查；TBD |

## 3. P3 交付需求门禁

| Gate ID | 来源 | 判定规则 | PASS 最低证据及目标环境 | 现有证据 | 状态 | 阻断级/原因 | 责任角色 | 下一步/到期日 |
|---|---|---|---|---|---|---|---|---|
| G-RLS-01 | RLS-01 | 46 FR 唯一记录、证据/阻因、角色、下一步完整 | 独立文档审查通过 | `fr-evidence-index.md` | BLOCKED | 阻断；待 Claude Code 最终验收 | Claude Code | 复核并签署；TBD |
| G-RLS-02 | RLS-02 | 全部门禁唯一状态且证据真实 | 独立矩阵审查通过 | 本文件 | BLOCKED | 阻断；待最终验收 | Claude Code | 复核并签署；TBD |
| G-RLS-03 | RLS-03 | 未实测项均有可执行补测卡 | 执行负责人走查通过 | `production-validation-plan.md` | BLOCKED | 阻断；尚未走查/执行 | 测试/运维负责人 | 走查后安排授权；TBD |
| G-RLS-04 | RLS-04 | 差距分类准确，无功能缺口伪装待测 | 安全/架构/接口负责人复核 | `gap-and-dependency-register.md` | BLOCKED | 阻断；待复核 | 安全/架构负责人 | 逐项确认；TBD |
| G-RLS-05 | RLS-05 | 准入算法、角色、豁免、观察/回退完整 | 发布管理制度和有权角色确认 | `release-approval-checklist.md` | BLOCKED | 阻断；机构流程未确认 | 发布负责人 | 机构确认模板；TBD |
| G-RLS-06 | RLS-06 | 文档/培训/SLA/值守/巡检证据完整 | 交付审查及受控材料 | `delivery-service-evidence.md` | BLOCKED | 阻断；组织/合同材料缺失 | 交付负责人 | 补受控材料；TBD |
| G-R01 | NFR-R01 | 证据可定位并含日期/环境/角色/完整性 | 独立抽样通过 | P3 七文档 | BLOCKED | 阻断；外部原始证据位置未登记 | 证据管理员 | 登记受控材料；TBD |
| G-R02 | NFR-R02 | 无虚构生产结论 | 对照原始输出抽样通过 | P3 七文档 | BLOCKED | 阻断；待独立审查 | Claude Code | 真实性复核；TBD |
| G-R03 | NFR-R03 | 责任/审批/回退角色与期限完整 | 字段完整性审查 | P3 七文档 | BLOCKED | 阻断；机构角色/期限待确认 | 发布负责人 | 机构确认；TBD |
| G-R04 | NFR-R04 | 无敏感信息，受控引用合规 | 扫描+人工审查通过 | 本次静态检查记录 | BLOCKED | 阻断；仍需 Claude Code 独立复核 | 安全负责人 | 最终 diff 复核；TBD |
| G-R05 | NFR-R05 | 计划含前置/步骤/指标/停止/回退/归档 | 执行角色逐卡走查通过 | `production-validation-plan.md` | BLOCKED | 阻断；尚未由执行角色走查 | 测试/运维负责人 | 逐卡走查；TBD |

## 4. NFR 计数说明与校验记录

- `docs/requirements.md` §3 的 NFR 基线为 24 个唯一 NFR 编号：P01~P07、A01~A05、S01~S03、E01~E03、C01~C03、M01、U01、G01；本矩阵已全部映射。
- 矩阵将 NFR-P01 的标准/定制接口阈值拆为 2 条，并将 NFR-P06 的命中率/查询时延/容量阈值拆为 3 条，共形成 27 条原子门禁；该拆分不新增 NFR 编号、不改变原阈值。G-E01~G-E03 的下一步均指向独立 `PV-ARCH` 卡。
- 流程 §9 的功能、数据库、持久化、安全、测试、性能、合规、运维 8 类均已覆盖，并补充兼容、文档和发布回退门禁。
- 当前仅进行了本地 Markdown 结构、编号、引用和状态枚举检查；没有执行任何生产等价验证。
