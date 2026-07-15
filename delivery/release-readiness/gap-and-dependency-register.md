# 差距与外部依赖台账

> 分类仅使用 `VERIFY_PENDING`、`FUNCTION_GAP`、`EXTERNAL_DEPENDENCY`、`TECHNICAL_DEBT`。发布处置仅使用 `BLOCK`、`MAY_WAIVE`、`NON_BLOCKING`。`MAY_WAIVE` 只是允许发起申请，不等于已豁免；当前无 `WAIVED`。

## 1. 待生产等价/第三方验证（VERIFY_PENDING）

| Risk ID | 分类 | 影响 FR/NFR | 事实/来源 | 风险 | 发布处置 | 关闭证据 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|---|---|
| RISK-V-01 | VERIFY_PENDING | NFR-P01~P07；G-PERF-01 | `perf/p2-02-report.md` 明确无可采信实测值，日志聚合亦无 PROD_EQ 原始指标 | 高 | BLOCK | PROD_EQ/第三方完整性能报告及日志写入吞吐/采集延迟/丢失率/积压量/查询时延原始证据 | 性能测试负责人 | 执行 `PV-PERF`（含日志聚合维度） |
| RISK-V-02 | VERIFY_PENDING | NFR-A01 | `delivery/p2-03-report.md`：48h 未执行 | 高 | BLOCK | 48h 监控、事件和稳定性签署报告 | 运维负责人 | 执行 PV-STABLE |
| RISK-V-03 | VERIFY_PENDING | NFR-A02~A05 | `delivery/p2-03-report.md`：仅开发降级/脚本，生产演练未执行 | 高 | BLOCK | 节点/DB/Redis/Kafka/双活/升级原始演练 | 运维/灾备负责人 | 执行 PV-AVAIL |
| RISK-V-04 | VERIFY_PENDING | NFR-S03 | `security/p2-04-report.md`：后端 SCA 因 NVD 数据缺失未完成 | 高 | BLOCK | 有效数据源 SCA 报告及高危闭环复扫 | 安全负责人 | 取得受控访问后重跑 |
| RISK-V-05 | VERIFY_PENDING | NFR-S03 | `security/p2-04-report.md`：ZAP 未执行 | 高 | BLOCK | 获授权目标 DAST 原始报告与闭环 | 安全负责人 | 执行 PV-SEC |
| RISK-V-06 | VERIFY_PENDING | FR-603；NFR-S02 | `security/p2-04-report.md`：TLS 为部署核查项 | 高 | BLOCK | PROD_EQ TLS1.2+ 协议/证书链/弱套件核查 | 安全负责人 | 执行 PV-SEC |
| RISK-V-07 | VERIFY_PENDING | FR-804/606；NFR-S02/G01 | `delivery/p2-05-report.md`：三年留存、DB 禁改待验证 | 高 | BLOCK | 留存策略、权限/触发器、篡改拒绝与查询证据 | 合规/DBA | 执行 PV-SEC/PV-RESTORE |
| RISK-V-08 | VERIFY_PENDING | NFR-S03/G01 | `security/p2-04-report.md`：等保三级未测评 | 高 | BLOCK | 范围匹配的第三方等保材料及整改闭环 | 合规负责人 | 明确测评范围和机构 |
| RISK-V-09 | VERIFY_PENDING | NFR-C01 | `delivery/acceptance-report.md`：达梦/OceanBase、麒麟/UOS、ARM 未实测 | 高 | BLOCK | 目标国产软硬件兼容矩阵和原始结果 | 兼容测试负责人 | 执行 PV-COMP |
| RISK-V-10 | VERIFY_PENDING | NFR-C03；FR-805 | `delivery/acceptance-report.md`：跨浏览器/移动端未实测 | 中 | BLOCK | Chrome/Edge/Firefox/移动设备矩阵 | 兼容测试负责人 | 执行 PV-COMP |
| RISK-V-11 | VERIFY_PENDING | NFR-A03/A04/S02/G01；FR-606；G-PERSIST-01 | `delivery/p2-05-report.md`：真实恢复、RPO/RTO、MinIO、销毁抽样及主链路重启不丢待验证 | 高 | BLOCK | PROD_EQ 主链路重启事务边界/一致性、DB/MinIO 恢复、审计链、RPO/RTO、销毁证明原始记录 | 灾备/合规负责人 | 执行 `PV-RESTORE`（含主链路重启维度） |
| RISK-V-12 | VERIFY_PENDING | NFR-E01~E03 | `delivery/system-architecture.md` 仅提供设计 | 中 | BLOCK | PROD_EQ 扩缩容、容量/多引擎和架构评审证据 | 架构负责人 | 执行 `production-validation-plan.md` 的 `PV-ARCH` E01~E03 |

## 2. 功能缺口（FUNCTION_GAP）

| Risk ID | 分类 | 影响 FR/NFR | 事实/来源 | 风险 | 发布处置 | 关闭证据 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|---|---|
| RISK-F-01 | FUNCTION_GAP | NFR-S01 | `security/p2-04-report.md`：MFA 未实现 | 高 | BLOCK | 实现任务、测试、PROD_EQ 正反例和安全复核 | 身份认证负责人 | 建立独立实现任务 |
| RISK-F-02 | FUNCTION_GAP | NFR-S01/C02 | `security/p2-04-report.md`：IAM/SSO 未实现/未联调 | 高 | BLOCK | 实现与机构 IAM/SSO 真实联调记录 | 身份认证/机构接口负责人 | 取得规范后独立实现联调 |
| RISK-F-03 | FUNCTION_GAP | FR-304；NFR-S01 | `reviews/claude-review-P2-04.md`：无资源所有权/字段级 ABAC，所谓 IDOR 测试仅为 RBAC | 高 | BLOCK | 字段级/资源级 ABAC 实现、水平越权测试与 PROD_EQ 权限矩阵 | 安全负责人 | 建立独立 ABAC 任务 |
| RISK-F-04 | FUNCTION_GAP | NFR-S01 | `security/p2-04-report.md`：数字证书认证未实现 | 高 | BLOCK | 证书生命周期/认证实现与 PROD_EQ 正反例 | 安全负责人 | 建立独立证书认证任务 |
| RISK-F-05 | FUNCTION_GAP | NFR-P03/P04；FR-205 | `perf/p2-02-report.md`：真实 connector 批量读取能力未改造 | 高 | BLOCK | batch/offset 能力、百万条断点恢复和吞吐结果 | 接入负责人 | 独立能力实现后 PV-PERF |
| RISK-F-06 | FUNCTION_GAP | FR-606；NFR-G01 | `delivery/p2-05-report.md`：销毁证明尚未接入生产生命周期调用链 | 高 | BLOCK | 生产路径实现、审批关联和 PROD_EQ 抽样 | 数据治理/合规负责人 | 建立独立接入任务 |

## 3. 外部依赖（EXTERNAL_DEPENDENCY）

| Risk ID | 分类 | 影响 FR/NFR | 事实/来源 | 风险 | 发布处置 | 关闭证据 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|---|---|
| RISK-X-01 | EXTERNAL_DEPENDENCY | FR-705；NFR-C02 | `delivery/p1-acceptance-summary.md`：财务/采购仅 Mock，Q-06 规范未提供 | 高 | BLOCK | 正式接口规范、测试环境、对账/重试/签署联调记录 | 机构接口负责人 | 机构提供规范与窗口 |
| RISK-X-02 | EXTERNAL_DEPENDENCY | FR-803；NFR-C02/G01 | `delivery/p1-acceptance-summary.md`：监管报送仅 Mock，Q-05 标准未提供 | 高 | BLOCK | 正式监管规范、测试环境、报送/回执签署记录 | 合规/机构接口负责人 | 机构提供规范与窗口 |
| RISK-X-03 | EXTERNAL_DEPENDENCY | NFR-C02 | 原需求要求中台/大数据/核心/风控/营销等真实对接，仓库无签署联调材料 | 高 | BLOCK | 各上线必需系统的正式规范和联调签署 | 机构接口负责人 | 冻结必需系统清单 |
| RISK-X-04 | EXTERNAL_DEPENDENCY | NFR-S03/G01 | 测评机构、范围、机构认可标准未确定 | 高 | BLOCK | 测评合同/范围、第三方报告和整改闭环编号 | 合规负责人 | 采购方确认测评安排 |
| RISK-X-05 | EXTERNAL_DEPENDENCY | RLS-05；NFR-R03 | 发布审批、豁免、回退和签名流程未提供 | 高 | BLOCK | 机构发布制度、角色授权和受控审批单 | 发布负责人 | 确认正式流程 |
| RISK-X-06 | EXTERNAL_DEPENDENCY | NFR-M01/U01；RLS-06 | 培训、7×24、巡检、三年服务的合同/组织记录不在仓库 | 中 | BLOCK | 合同、服务台、值守、培训/考核、巡检计划受控材料 | 交付负责人 | 补充 `CONTROLLED-LOCATION-TBD` 材料编号 |
| RISK-X-07 | EXTERNAL_DEPENDENCY | NFR-G01 | ISO27001 资质认证属于供应商/组织资质，非软件功能，仓库无法生成或验证正式证书 | 中 | BLOCK | 机构确认适用范围；供应商提供有效证书及受控核验记录 | 合规/采购负责人 | 由机构/供应商提供 `CONTROLLED-LOCATION-TBD` 证书材料 |

## 4. 技术债（TECHNICAL_DEBT）

| Risk ID | 分类 | 影响 FR/NFR | 事实/来源 | 风险 | 发布处置 | 关闭证据 | 责任角色 | 下一步 |
|---|---|---|---|---|---|---|---|---|
| RISK-T-01 | TECHNICAL_DEBT | NFR-C01/M01 | `reviews/claude-review-P2-01-返工复审.md`：分区维护依赖 MySQL INFORMATION_SCHEMA，达梦等价实现缺失 | 中 | MAY_WAIVE | 明确支持边界或实现达梦分区维护并实测；豁免需完整书面材料 | DBA/架构负责人 | 上线 DB 选型后执行 `PV-DB` 分区三方比对；当前仍 BLOCKED 于 C01 |
| RISK-T-02 | TECHNICAL_DEBT | FR-305/804；NFR-S02 | 同一审查：归档表无 id 单列唯一索引，与测试双保险不一致，影响审计归档完整性/防篡改可信度 | 高 | BLOCK | 并发风险评估、迁移、目标 DB 约束/索引三方比对及审计完整性复核 | DBA/安全负责人 | 修复后纳入 `PV-DB` 与审计链验证；不得在 P3 修改 |
| RISK-T-03 | TECHNICAL_DEBT | FR-704/801；NFR-P07 | 同一审查：账单/统计 `findAllByRange` 全量加载，存在内存压力 | 中 | MAY_WAIVE | SQL GROUP BY/流式聚合压测，或有限数据窗风险材料 | 计费/统计负责人 | 与 PV-PERF 结果联合处置 |
| RISK-T-04 | TECHNICAL_DEBT | NFR-P03/P07 | `reviews/claude-review-P2-02.md`：数据生成器 MAX(id)+1 不可并行，raw_data 不跨月 | 低 | NON_BLOCKING | runbook 限制/生成器改进和相应自测 | 性能测试负责人 | 在后续压测工具任务处理 |
| RISK-T-05 | TECHNICAL_DEBT | FR-503；NFR-A02 | `reviews/claude-review-P2-03.md`：Redis 降级为 JVM 本地，多实例配额精度下降 | 中 | MAY_WAIVE | 故障期风险量化、监控/回退或强一致 fallback 实现 | 消费方/运维负责人 | PV-AVAIL 验证并决策 |
| RISK-T-06 | TECHNICAL_DEBT | NFR-S03/U01 | `reviews/claude-review-P2-04.md`：XSS 不改写 JSON body；Vite 构建警告需监控，影响应用安全门禁 | 高 | BLOCK | 输出编码策略、授权 DAST/手工 XSS 正反例、修复复扫及 CI 警告基线 | 前端/安全负责人 | 修复并纳入 PV-SEC；未关闭不得准入 |
| RISK-T-07 | TECHNICAL_DEBT | FR-606；NFR-S02/G01 | `reviews/claude-review-P2-05-最终复查.md`：proof_hash 约束、多实例 ID、事务/verify 时机等遗留，影响审计防篡改与销毁证明可信度 | 高 | BLOCK | 关闭约束/并发/事务/verify 风险，接入真实调用链并完成 PROD_EQ 审计链与销毁证明验证 | 数据治理/DBA/安全负责人 | 与 RISK-F-06、RISK-V-07 联合关闭；未关闭不得准入 |
| RISK-T-08 | TECHNICAL_DEBT | G-A01~A05、G-PERSIST-01；NFR-A01~A05/S02 | `delivery/chaos-drill/db-failover.sh`、`delivery/chaos-drill/redis-down.sh`、`delivery/chaos-drill/kafka-outage.sh`、`delivery/chaos-drill/dual-active-switch.sh` 存在吞失败/仅打印 RTO 路径；`perf/monitor/collect-metrics.sh` 多 measurement 可能写 `NA`；`delivery/backup-restore/restore-db.sh` 缺少源/目标计数或哈希一致性断言 | 高 | BLOCK | 修复退出/断言/采集与源目标一致性校验后复审，或执行/复核角色逐命令人工核验完整原始证据并签署 | 运维/性能/DBA/灾备负责人 | 在执行 PV-STABLE/PV-AVAIL/PV-RESTORE 前关闭；脚本退出码 0 不得单独作为 PASS 证据 |

## 5. 校验记录

### 本轮实际静态检查

| 命令 | 输出摘要 | 退出码 |
|---|---|---:|
| `git diff --check` | 无空白错误；仅输出 Git LF/CRLF 转换警告 | 0 |
| PowerShell 内联结构检查：`Get-ChildItem` + `[regex]` 核对 FR/NFR/原子门禁/§8.5 关键项 | `FILES=7; FR_UNIQUE=46; FR_ROWS_COLUMNS=46/46; NFR_UNIQUE=24; ATOMIC_GATES=27; FOLLOWUP_TOKENS=12/12; FOLLOWUP_CHECK=PASS` | 0 |

- NFR 基线口径统一为 24 个唯一 NFR 编号，拆分为 27 条原子门禁；RISK-V-12 已指向独立 PV-ARCH 卡。

- 已按四类登记后端 SCA、授权 DAST、TLS、等保、审计三年留存、达梦/OceanBase、性能/RPO/RTO、MFA、IAM/SSO、字段 ABAC、证书认证、财务/采购、监管及指定 P2 技术债。
- `FUNCTION_GAP` 与 `EXTERNAL_DEPENDENCY` 未表述为“仅待测试”；当前没有任何已批准豁免。
- 本次仅进行仓库事实源和表格字段静态核对；没有执行关闭动作或生产验证。
