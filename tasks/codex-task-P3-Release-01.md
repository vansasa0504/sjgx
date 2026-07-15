# Codex 执行任务 — P3/Release-01 上线验收准备

> 主控：Claude Code　执行：Codex
> 分支：`ai/p3-release-readiness`
> 日期：2026-07-12
> 任务性质：**仅文档/证据盘点任务**。不实现业务功能、不运行任何生产或外部环境操作。

---

## 1. 任务目标

交付一套版本化、可审查的上线验收准备包，使发布决策者能清楚区分：

1. P0-P2 已完成的开发环境证据；
2. 必须在生产等价环境补测的性能、可用性、安全、扩展性、兼容性、合规和运维项目；
3. 尚未实现、未联调或等待外部规范的功能/依赖；
4. 可申请但尚未获批的风险豁免。

**本任务的初始准入结论必须是“当前不准入 / BLOCKED”，除非存在与目标环境匹配、可复核的真实证据。**

本任务绝不等同于正式上线批准，不得虚构实测、审批或豁免结果。

---

## 2. 需求依据（必读）

Codex 开始前必须读取并遵守：

1. `AGENTS.md`
2. `docs/technical-requirements.md` §3.1~§3.5、§4~§6
3. `docs/requirements.md` §9~§16（P3/Release-01 上线验收准备需求附录）
4. `tasks/requirement-analysis.md` §10~§17（P3 需求分析附录）
5. `tasks/claude-plan-P3-Release-01.md`（本任务的权威计划）
6. `docs/development-process-workflow.md` §8.3、§9、§10、§12
7. 下列既有事实源（只读引用，禁止改写）：
   - `delivery/acceptance-report.md`
   - `delivery/p1-acceptance-summary.md`
   - `perf/p2-02-report.md`
   - `delivery/p2-03-report.md`
   - `security/p2-04-report.md`
   - `delivery/p2-05-report.md`
   - `reviews/claude-review-P2-01-返工复审.md`
   - `reviews/claude-review-P2-02.md`
   - `reviews/claude-review-P2-03.md`
   - `reviews/claude-review-P2-04.md`
   - `reviews/claude-review-P2-05-最终复查.md`
   - `delivery/{system-architecture,deployment-guide,ops-manual,dev-guide,user-guide}.md`

若报告和历史结论存在差异：以最新的 Claude Code 审查结论和可定位的原始测试输出为准；保留冲突说明，**不得自行美化为 PASS**。

---

## 3. 允许修改范围

### 3.1 初始交付文件（当前已存在；返工不得新增第 8 份）

`delivery/release-readiness/` 中的交付包固定为以下 7 份 Markdown：

1. `README.md`
2. `fr-evidence-index.md`
3. `release-gate-matrix.md`
4. `production-validation-plan.md`
5. `gap-and-dependency-register.md`
6. `release-approval-checklist.md`
7. `delivery-service-evidence.md`

所有文件使用 UTF-8 Markdown，使用仓库相对路径引用已有材料。返工只允许修改这 7 份现有文档；`PV-ARCH` 是 `production-validation-plan.md` 内的新卡片标题，不是新增文件。

### 3.2 条件允许修改

- **默认不要修改** `delivery/acceptance-report.md`。
- 仅当为帮助读者发现 P3 材料确有必要时，允许在文件最顶部的说明段后追加**不超过一段**索引文字，指向 `delivery/release-readiness/README.md`。
- 该索引不得修改 M6 历史结论、不得将“待上线环境执行”改写为“通过”、不得添加新的测试结论。

### 3.3 已由 Claude Code 修改，Codex 仅可读取

以下文件已包含 P3 需求/计划，Codex **不得修改**：

- `docs/requirements.md`
- `tasks/requirement-analysis.md`
- `tasks/claude-plan-P3-Release-01.md`
- `tasks/codex-task-P3-Release-01.md`

---

## 4. 禁止修改范围

严禁修改或执行以下内容：

1. 所有 `platform-*` 业务代码、单元测试、集成测试、前端代码、依赖、构建配置。
2. `db/` 的任何迁移/回滚脚本或数据库设计。
3. `.env`、`.env.*`、密钥、证书、真实账号/令牌、生产连接串、真实机构 URL、个人信息、生产导出数据。
4. `k8s/prod/`、生产部署脚本、生产运行配置。
5. `perf/`、`security/`、`delivery/chaos-drill/`、`delivery/backup-restore/` 下的脚本、配置和既有报告。
6. 所有 P0-P2 历史审查报告、阶段报告和测试结论。
7. 不新增代码、脚本、CI、数据库、接口、自动化发布工具、依赖或前端页面。
8. 不执行压测、DAST/SCA、故障注入、灾备切换、数据库恢复、真实外部接口调用、发布或任何生产/外部环境操作。
9. 不生成或填写虚构的测试结果、审批记录、签署人、执行人、环境数据、性能数据、RPO/RTO、漏洞结论或豁免。

发现任何敏感信息时：停止写入，使用 `CONTROLLED-LOCATION-TBD` 或受控材料编号占位，不得把敏感原文写入 Git。

---

## 5. 实现要求

### F-1：`delivery/release-readiness/README.md`

必须包含：

- P3 范围、非范围和当前 commit 基线（使用可从 Git 确认的实际 commit；不可编造）。
- 明确声明：这是验收准备包，**不是正式上线批准**；当前总状态是 `NOT_READY / BLOCKED`，直至门禁关闭。
- 证据分层：`DEV_TEST`、`DEV_REVIEW`、`PROD_EQ_TEST`、`PROD_RECORD`、`THIRD_PARTY`、`EXTERNAL_DEPENDENCY`。
- 状态枚举与规则：只能使用 `PASS`、`FAIL`、`BLOCKED`、`WAIVED`；尤其明确 DEV 证据不可单独让生产门禁 PASS。
- 目录导航、事实源、材料更新/复审规则与敏感信息边界。

### F-2：`fr-evidence-index.md`

必须覆盖原有 **46 条 FR**，按九大模块分组：

- FR-101~104 合作方管理
- FR-201~205 外部数据接入
- FR-301~305 外部数据服务
- FR-401~405 数据目录与预览
- FR-501~505 消费方管理
- FR-601~606 缓存存储再利用
- FR-701~705 计费管理
- FR-801~805 统计监管
- FR-901~906 数据质量管理

每条或可清晰覆盖的最小分组必须包含：FR 编号、能力/验收动作、当前开发证据 ID 和相对路径、证据环境、当前发布状态、责任角色、下一步。

特别要求：

- 真实财务/采购对接（FR-705）与真实监管报送（FR-803）必须标为外部规范未提供/未联调，不能写 PASS。
- 不得只写“已实现”；必须给出可定位的测试、审查或报告引用。
- 开发环境证据可标明“开发实现/测试已证实”，但发布层状态若仍依赖外部环境或接口，应为 BLOCKED。

### F-3：`release-gate-matrix.md`

覆盖 `docs/development-process-workflow.md` §9 的所有门禁：

1. 功能门禁；
2. 数据库门禁；
3. 持久化门禁；
4. 安全门禁；
5. 测试门禁；
6. 性能门禁；
7. 合规门禁；
8. 运维门禁；
9. 文档/发布回退门禁（按原始验收/运维要求补充，不得替代上述 8 类）。

矩阵每行至少包括：

`Gate ID`、来源 FR/NFR/流程条款、原始阈值或判定规则、PASS 的最低证据及目标环境、现有证据 ID/路径、当前状态、阻断级/原因、责任角色、下一步/到期日。

必须显式映射：

- NFR-P01~P07；
- NFR-A01~A05；
- NFR-S01~S03；
- NFR-E01~E03；
- NFR-C01~C03；
- NFR-M01、NFR-U01、NFR-G01。

状态规则：

- `PASS`：必须已有与最低证据要求和目标环境匹配的原始结果；
- `FAIL`：已执行且未达阈值/数据校验失败；
- `BLOCKED`：环境、授权、外部规范、功能或证据缺失；
- `WAIVED`：仅当风险、影响、批准角色、到期日、补齐任务、监控措施、回退条件全部已有受控书面材料时才可使用。本仓库当前没有此类材料时，**不得擅自填 WAIVED**。

初始矩阵必须如实体现 P2 报告事实：生产等价性能、48h、RPO/RTO、生产 DAST/后端 SCA、TLS/审计留存、国产化实际环境、跨浏览器/移动端、外部系统联调等未取得证据的项应为 BLOCKED，而不是 PASS。

### F-4：`production-validation-plan.md`

为尚未实测的项目提供可执行的补测卡/清单。每张卡必须具有：

- 覆盖的 Gate/NFR；
- 前置环境、授权和变更窗口；
- 数据规模/拓扑/版本/资源规格；
- 使用的既有脚本/Runbook（只引用）；
- 执行步骤和计时起止点；
- 采集指标、原始结果受控归档位置；
- PASS/FAIL 判定；
- 停止条件、清理/回退动作；
- 执行角色、复核角色、状态（初始 `BLOCKED`）。

至少覆盖：

1. NFR-P01~P07 性能、容量、查询、缓存、批量/断点续传（引用 `perf/p2-02-report.md`、`perf/runbook.md`、`perf/report-template.md`）；
2. NFR-A01 48h 稳定性；
3. NFR-A02~A05 节点/DB/Redis/Kafka、同城双活、滚动升级与回退（引用 `delivery/p2-03-report.md` 与 `delivery/chaos-drill/`）；
4. NFR-S01~S03 的后端 SCA、授权 DAST、TLS、审计不可篡改/三年留存、等保材料；
5. NFR-E01~E03 的扩展性验证必须使用独立 `PV-ARCH` 卡：NFR-E01 覆盖微服务/容器/弹性伸缩/水平扩展与扩缩容无中断；NFR-E02 覆盖 SDK、开放 API/插件、新接口开发≤3个工作日、80%常规接入可视化配置的计时与覆盖统计；NFR-E03 覆盖分布式、PB 级容量方案与关系/NoSQL/时序/文件多引擎验证；
6. NFR-C01~C03 的达梦/OceanBase、国产 OS/X86/ARM、浏览器与移动端；
7. P2-05 的数据库/MinIO 恢复、审计链验证、生产 RPO/RTO 与销毁证明抽样。

不得加入真实命令参数、认证头、URL、密码或生产数据。

### F-5：`gap-and-dependency-register.md`

建立分类台账，分类只能是：

- `VERIFY_PENDING`：代码/脚本已具备，但需要生产等价环境或第三方结果；
- `FUNCTION_GAP`：能力尚未实现；
- `EXTERNAL_DEPENDENCY`：等待采购方/机构规范、环境或审批；
- `TECHNICAL_DEBT`：已知技术风险/非阻断改进。

每项至少包含：Risk ID、分类、影响 FR/NFR、事实/来源路径、风险级别、发布处置（`BLOCK`/`MAY_WAIVE`/`NON_BLOCKING`）、关闭所需证据、责任角色、下一步。

至少纳入并正确分类：

- 后端 SCA 未完成、授权 DAST/TLS/等保、审计三年留存、真实达梦/OceanBase、性能与 RPO/RTO（`VERIFY_PENDING`）；
- MFA、IAM/SSO、字段级 ABAC、数字证书认证（`FUNCTION_GAP`）；
- 财务/采购、监管报送真实规范/联调（`EXTERNAL_DEPENDENCY`）；
- P2-01 审查的达梦分区维护限制、归档表 id 单列唯一索引建议、账单/统计聚合内存压力等（`TECHNICAL_DEBT`）。

不可将 `FUNCTION_GAP` 或 `EXTERNAL_DEPENDENCY` 表述为“仅待测试”。

### F-6：`release-approval-checklist.md`

必须包含：

1. 唯一决策规则：任一 `FAIL` 或未获得完整书面豁免的阻断 `BLOCKED` 存在时，结论为**不准入**。
2. 发布前、发布中、发布后观察、回退决策四个部分。
3. 角色占位：发布负责人、变更审批人、业务验收人、安全负责人、运维负责人、回退决策人、值守负责人；不写真实人名/电话/账号。
4. `WAIVED` 申请模板，字段必须含风险、影响范围、批准角色、受控材料编号、到期日、补齐任务、监控措施、回退触发条件。字段不完整不得改变门禁状态。
5. 上线后观察窗口、健康指标、异常触发、回退条件与引用已有 `delivery/deployment-guide.md`/`delivery/ops-manual.md`/`delivery/upgrade-rollback-drill.md` 的方式。
6. 明示该清单是模板/准备材料，**无权替代机构正式发布审批**。

### F-7：`delivery-service-evidence.md`

建立产品交付与服务承诺证据清单：

- 五类已有产品文档：架构、部署、运维、开发、用户手册；
- 测试报告/验收材料；
- 培训（管理员/运维/业务/开发、现场+线上、考核）；
- 7×24 支持、响应/解决时限；
- 月度远程/季度现场巡检；
- 三年原厂免费运维及版本升级服务。

每项列出：原始要求、当前可引用材料、状态、责任角色、还需的机构/供应商非代码证据、下一步。合同、培训记录、值守人员、服务台材料等无法从仓库验证时必须为 BLOCKED/外部受控材料，不得标 PASS。

### F-8：交叉引用和文档自检

1. 7 份文档必须从 README 可导航，且互相引用时路径正确。
2. 每份至少有“校验记录”小节，说明实际做过的静态自检和未执行的生产验证；不得写未做过的命令或结论。
3. 不复制冗长历史报告；采用“事实摘要 + 相对路径”追溯。
4. 不修改历史报告来消除不利结论；P3 仅组织、引用和分类事实。

---

## 6. 测试要求

这是文档任务，**不要求、也不得声称执行** Maven、npm、性能、DAST、故障演练、数据库恢复或生产命令，除非实际执行且符合本任务安全边界（通常不应执行）。

必须执行并报告真实结果：

1. `git diff --check`
2. 检查 7 个规定文件均存在。
3. 检查 7 个 P3 文档之间及其指向仓库文件的相对路径均存在；外部/受控材料仅允许 `CONTROLLED-LOCATION-TBD` 或受控材料编号占位。
4. 覆盖性核对：46 条 FR、24 个唯一既有 NFR 编号（NFR-P01~P07、A01~A05、S01~S03、E01~E03、C01~C03、M01/U01/G01）、按复合阈值拆分后的 27 条原子门禁、RLS-01~RLS-06、NFR-R01~NFR-R05 和流程文档 §9 门禁均已映射；NFR-E01~E03 必须由独立 `PV-ARCH` 卡覆盖。
5. 状态机核对：只使用 `PASS`/`FAIL`/`BLOCKED`/`WAIVED`；每一个 PASS 都有环境匹配的最低证据；每一个 WAIVED（若存在）都具备完整七字段，当前预期没有 WAIVED。
6. 敏感信息人工检查：确保未写入真实密钥、令牌、密码、连接串、内部 URL、个人联系方式、生产导出或原始漏洞细节。
7. 范围核对：`git diff --name-only` 只能包含本任务允许路径；若添加 `delivery/acceptance-report.md`，仅能是顶部一段索引。

建议以 PowerShell/Python/现有 Git 工具完成本地静态检查，但不得新增长期维护的检查脚本或依赖。

---

## 7. 完成后输出

完成后，Codex 必须提交以下说明给 Claude Code 审查：

1. 新增/修改文件清单（应为 7 份 P3 文档；如修改 `delivery/acceptance-report.md`，说明必要性与精确段落）。
2. FR-101~FR-906、24 个唯一 NFR 编号/27 条原子门禁、RLS-01~RLS-06、NFR-R01~NFR-R05 和流程 §9 门禁的覆盖核对方式与结果；其中须单列 NFR-E01~E03 与 `PV-ARCH` 的对应关系。
3. 当前总体准入结论与所有 BLOCKED/FAIL 项的摘要；不得输出“生产可上线”结论。
4. 实际执行的静态检查命令、完整/足够的真实输出摘要和退出码。
5. 明确未执行的生产等价验证项目、缺失环境/授权/外部规范和风险。
6. 敏感信息检查结果。
7. 偏离本任务单的内容；无偏离时明确说明“无偏离”。

---

## 8. 返工规则

以下任一情形，Claude Code 审查将判定返工：

1. 将开发单测、脚本语法检查、Mock、代码审查或文档当作生产/生产等价 PASS。
2. 缺少 46 FR、24 个唯一 NFR 编号/27 条原子门禁（包括 NFR-E01~E03 的独立 `PV-ARCH` 卡）、流程 §9 门禁的映射，或门禁状态/下一步缺失。
3. 将 MFA/IAM/SSO/字段 ABAC/证书认证、真实外部接口联调等功能/外部依赖伪装为“仅待测试”。
4. 出现没有完整受控审批字段的 `WAIVED`，或将 BLOCKED 默认豁免。
5. 文档含真实敏感信息、虚构测试结果/审批/指标，或不当执行外部环境操作。
6. 修改了禁止范围内文件，或 P3 之外的无关重构。
7. 补测计划缺少前置、指标、PASS/FAIL、停止、回退/清理、归档位置中的任一关键部分。
8. 准入清单可以在存在 `FAIL` 或未批准阻断 `BLOCKED` 时得出“可上线”结论。

返工后必须重新执行本任务 §6 的全部静态检查，并报告差异和真实结果。

### 8.1 本轮返工任务 F-10（P1 阻断，必须完成）

Claude Code 已修正上游需求、分析、计划和本任务单，正式口径为：**原 NFR 共 24 个唯一编号；将 NFR-P01 的标准/定制接口阈值拆分，并将 NFR-P06 的命中率/查询时延/容量阈值拆分后，共形成 27 条原子门禁。** Codex 不得再写“27 条原 NFR”。

Codex 只修改 `delivery/release-readiness/` 中现有 7 份 Markdown，完成以下闭环：

1. 在 `production-validation-plan.md` 新增独立 `PV-ARCH` 卡，覆盖 G-E01~G-E03 / NFR-E01~E03：
   - E01：目标 PROD_EQ 微服务、Docker/K8s、弹性伸缩、水平扩展及扩缩容无中断；
   - E02：SDK、开放 API、插件机制，新接口开发≤3个工作日，80%常规接入可视化配置；必须定义计时起止、样本分母、覆盖率算法和业务签署；
   - E03：分布式架构、PB 级容量方案以及关系型/NoSQL/时序/文件多引擎；不得用设计文档替代目标环境或经批准的容量证据。
2. `PV-ARCH` 必须与其他补测卡同构，至少含覆盖范围、前置/授权/窗口、数据/拓扑/版本/资源、既有材料、步骤/计时、采集/归档、PASS/FAIL、停止条件、清理/回退、执行/复核角色和初始 `BLOCKED`。
3. 更新 `release-gate-matrix.md`：G-E01~G-E03 的下一步明确指向 `PV-ARCH`，不得继续使用悬空或泛化的“PV-PERF/AVAIL、接入演练、容量验证”。
4. 更新 `gap-and-dependency-register.md`：RISK-V-12 的下一步明确指向 `PV-ARCH`。
5. 更新 `release-approval-checklist.md`：发布前补测复核显式包含 E01~E03/扩展性，不得只列 P/A/C/M/U/G。
6. 只有在 `PV-ARCH` 实际补齐且交叉引用一致后，才可将 `production-validation-plan.md` 校验记录写为已覆盖 E01~E03；否则必须标明缺口，不得保留不实覆盖声明。
7. 更新七份文档中涉及 NFR 数量的表述，统一为“24 个唯一 NFR 编号，拆分为 27 条原子门禁”。
8. 不修改 `docs/requirements.md`、`tasks/requirement-analysis.md`、`tasks/claude-plan-P3-Release-01.md` 或本任务单；这些属于 Claude Code 上游事实源。

### 8.2 F-10 验收与输出

完成后必须报告：

1. `PV-ARCH` 对 NFR-E01、E02、E03 的逐项对应关系；
2. G-E01~G-E03、RISK-V-12、发布前清单和校验记录的同步修改位置；
3. 24 个唯一 NFR/27 条原子门禁的覆盖核对结果；
4. §6 全部静态检查的真实命令、输出摘要和退出码；
5. 明确当前总体状态仍为 `NOT_READY / BLOCKED`，不得因补齐计划而改成生产可上线。

F-10 完成后停止，不得自行执行审查报告中的 F-11 或其他 P2/P3 返工项；下一轮范围由 Claude Code 复审后另行派发。

### 8.3 本轮返工任务 F-11（P1 阻断，必须完成）

> 状态：F-10（AD-04，NFR-E01~E03）已由 Claude Code 复验通过（见 `reviews/claude-review-P3-Release-01.md` §15）。本轮处理 AD-05/F-11：§9 上线门禁补测卡系统性覆盖缺口。Claude Code 已核实 `docs/development-process-workflow.md` §9（line 408-415）原文，确认以下门禁要求未被现有 9 张 PV 卡完整覆盖。

Codex 只修改 `delivery/release-readiness/` 中现有 7 份 Markdown，完成以下闭环：

1. **PV-PERF 补强日志聚合维度（G-PERF-01）**：
   - §9 性能门禁原文为"P95/P99、TPS、批量接入、日志聚合达标"，现有 PV-PERF 采集指标无日志聚合维度。
   - 在 PV-PERF 的"采集/归档"字段增日志聚合指标：日志写入吞吐、采集延迟、丢失率、积压量、查询时延。
   - 在 PV-PERF 的"PASS/FAIL"字段增日志聚合达标的判定规则（与既有 P95/P99/TPS/批量/缓存/查询判定并列，不得改动或弱化现有阈值）。

2. **新增 PV-DB 专用补测卡（G-DB-01）**：
   - §9 数据库门禁原文："空库/升级库迁移成功，目标结构与库设一致"。
   - `PV-DB` 是 `production-validation-plan.md` 内的新卡片标题，**不是新增文件**（交付包保持 7 份）。
   - 卡片须与其他 PV 卡同构，至少含：覆盖（G-DB-01）、前置/授权/窗口（获授权目标达梦/OceanBase PROD_EQ + 迁移授权 + 变更窗口）、数据/拓扑/版本/资源（目标 DB 版本、空库与旧库升级库、库设定义）、既有材料（只读引用 `db/migration/`、`reviews/claude-review-P2-01-返工复审.md`）、步骤/计时、采集/归档（迁移日志与结构对比原始输出，`CONTROLLED-LOCATION-TBD`）、PASS/FAIL、停止条件、清理/回退、角色/状态（初始 `BLOCKED`）。
   - 必须明确三方比对：①空库执行迁移结果、②旧库升级迁移结果、③目标库设定义，逐项比对结构/约束/索引/分区；不得以单一路径或仅 DEV Testcontainers 结果替代。

3. **PV-RESTORE 补强重启维度（G-PERSIST-01）**：
   - §9 持久化门禁原文："主链路数据重启后不丢"。
   - 现有 PV-RESTORE 步骤仅为备份/恢复/审计链，无重启维度。
   - 在 PV-RESTORE 增专门步骤与 PASS/FAIL：主链路写入前后应用重启 + 事务边界 + 重启后数据一致性校验。
   - 重启维度 PASS/FAIL 须明确：重启前后主链路数据零丢失、事务边界一致、约束/索引/分区完整；不得以备份恢复替代重启不丢验证，也不得以开发环境重启替代 PROD_EQ。

4. **新增 PV-FUNC 逐 FR 生产等价验收卡（G-FUNC-01）**：
   - §9 功能门禁原文："九大模块主链路可用，46 条 FR 有证据"。
   - `PV-FUNC` 是 `production-validation-plan.md` 内的新卡片标题，**不是新增文件**。
   - 卡片同构，覆盖 G-FUNC-01 与 46 FR，初始 `BLOCKED`。
   - 必须对 46 FR（按九大模块分组：FR-101~104/201~205/301~305/401~405/501~505/601~606/701~705/801~805/901~906）定义生产等价验收规则；每条或每组至少含：验收环境（PROD_EQ）、输入（业务请求/数据）、预期（功能结果 + 可复核证据）、PASS/FAIL 判定、原始输出归档位置、业务验收人签署规则。
   - 引用 `fr-evidence-index.md` 作为 FR 分组与开发证据输入；开发证据可作为起点但不得替代 PROD_EQ 验收；外部依赖 FR（如 FR-705/803）按既有 `EXTERNAL_DEPENDENCY` 处置，不得改写为已验收。

5. **同步修正矩阵、台账与校验记录**：
   - `release-gate-matrix.md`：G-PERF-01 下一步显式含日志聚合维度并指向 PV-PERF；G-DB-01 下一步指向 `PV-DB`；G-PERSIST-01 下一步指向 PV-RESTORE 的重启维度；G-FUNC-01 下一步指向 `PV-FUNC`（不再仅为自然语言"执行 46 FR 生产等价验收"）。
   - `production-validation-plan.md` §3 校验记录：如实反映 PV-PERF/PV-RESTORE 补强与 PV-DB/PV-FUNC 新增，并确认覆盖 G-PERF-01（日志聚合）/G-DB-01/G-PERSIST-01（重启）/G-FUNC-01；只有在卡片实际补齐后才可声明已覆盖，不得保留不实覆盖声明。
   - 如 `gap-and-dependency-register.md` 中与 G-DB/G-PERSIST/G-FUNC 相关的 RISK 项下一步需更新指向，同步修正；不得改变既有分类（VERIFY_PENDING/FUNCTION_GAP/EXTERNAL_DEPENDENCY/TECHNICAL_DEBT）与处置口径。
   - 保持交付包为 7 份文件（PV-DB/PV-FUNC 为 `production-validation-plan.md` 内卡片标题，非新增文件）。

6. **不修改上游 4 文件**：`docs/requirements.md`、`tasks/requirement-analysis.md`、`tasks/claude-plan-P3-Release-01.md`、本任务单属 Claude Code 上游事实源，Codex 不得修改。本轮 F-11 不涉及 NFR 编号/阈值/门禁定义变更，无需上游修正。

7. **范围与安全边界不变**：仍遵守本任务单 §3/§4，不改业务代码、迁移、`k8s/prod`、密钥、证书、`perf/`/`security/`/`delivery/chaos-drill/`/`delivery/backup-restore/` 脚本、`delivery/acceptance-report.md` 及任何 P0-P2 历史结论；不执行任何生产/外部环境操作；不虚构测试结果、日期、执行者或审批。

### 8.4 F-11 验收与输出

完成后必须报告：

1. `PV-DB` 卡对 G-DB-01 的覆盖：空库迁移、旧库升级迁移、目标库设三方比对的具体步骤与 PASS/FAIL 判定位置。
2. `PV-FUNC` 卡对 46 FR 的分组方式与逐组验收规则（环境/输入/预期/PASS-FAIL/归档/签署）的填写位置；九大模块分组是否完整。
3. PV-PERF 日志聚合指标（写入吞吐/采集延迟/丢失率/积压/查询时延）与 PASS/FAIL 的补强位置。
4. PV-RESTORE 重启维度（主链路写入前后应用重启 + 事务边界 + 重启后一致性）步骤与 PASS/FAIL 的补强位置。
5. G-PERF-01/G-DB-01/G-PERSIST-01/G-FUNC-01 矩阵下一步与 `production-validation-plan.md` §3 校验记录的同步修改位置。
6. 交付包仍为 7 份文件（PV-DB/PV-FUNC 为卡片标题非新增文件）的确认。
7. §6 全部静态检查的真实命令、输出摘要和退出码（含 PV-DB/PV-FUNC 卡存在性、字段同构性、24 个唯一 NFR/27 条原子门禁口径一致性、`git diff --check`、范围核对）。
8. 明确当前总体状态仍为 `NOT_READY / BLOCKED`，不得因补齐计划而改成生产可上线。

F-11 完成后停止，不得自行执行审查报告中的其他 P2/P3 返工项（F-12~F-29 等）；下一轮范围由 Claude Code 复审后另行派发。
