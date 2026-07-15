# Claude Code 审查结果 - P3/Release-01 上线验收准备

> 审查阶段：P3 / Release-01（P2 生产强化后的上线验收准备）
> 审查日期：2026-07-13（首次审查）；**2026-07-15 复验（F-10 返工后）**
> 审查范围：工作区未提交改动 = `docs/requirements.md`、`tasks/requirement-analysis.md`（P3 附录追加）+ 新增 `delivery/release-readiness/`（7 份 Markdown）+ 新增 `tasks/claude-plan-P3-Release-01.md`、`tasks/codex-task-P3-Release-01.md`
> 任务单：`tasks/codex-task-P3-Release-01.md`
> 计划：`tasks/claude-plan-P3-Release-01.md`
> 审查方：Claude Code（主控）
>
> **复审结论速览（2026-07-15，F-11 返工后）**：F-10（AD-04）与 F-11（AD-05）**两项 P1 阻断均已修复并通过复验**（含独立对抗式子代理第二意见证伪，未发现存活 P1/P2 阻断项）；F-12~F-29（AD-06~AD-23，P2/P3）为跟进条件，不阻断 P3 文档交付。**P3 文档任务复审：通过**；总体状态仍 `NOT_READY / BLOCKED`；不签发正式上线批准；G-RLS/G-R 门禁不自动转 PASS。详见本文 §16 F-11 复验记录。

---

## 1. 审查对象

| 类别 | 文件 | 新增/修改 |
|---|---|---|
| 需求附录 | `docs/requirements.md` | 修改（追加 §9~§16 P3 附录，+138 行） |
| 分析附录 | `tasks/requirement-analysis.md` | 修改（追加 §10~§17 P3 附录，+98 行） |
| 计划 | `tasks/claude-plan-P3-Release-01.md` | 新增 |
| 任务单 | `tasks/codex-task-P3-Release-01.md` | 新增 |
| 验收准备包 | `delivery/release-readiness/README.md` | 新增 |
| FR 证据索引 | `delivery/release-readiness/fr-evidence-index.md` | 新增 |
| 门禁矩阵 | `delivery/release-readiness/release-gate-matrix.md` | 新增 |
| 补测计划 | `delivery/release-readiness/production-validation-plan.md` | 新增 |
| 差距台账 | `delivery/release-readiness/gap-and-dependency-register.md` | 新增 |
| 准入清单 | `delivery/release-readiness/release-approval-checklist.md` | 新增 |
| 交付服务证据 | `delivery/release-readiness/delivery-service-evidence.md` | 新增 |

任务性质：**仅文档/证据盘点任务**，不实现业务功能、不运行生产或外部环境操作。

---

## 2. Git 状态

```text
git status --short
 M docs/requirements.md
 M tasks/requirement-analysis.md
?? delivery/release-readiness/
?? tasks/claude-plan-P3-Release-01.md
?? tasks/codex-task-P3-Release-01.md
?? .claude/worktrees/          # 审查子代理 worktree，非 Codex 产物，提交须排除
```

```text
git diff --check      -> 退出码 0（无空白错误；仅 LF→CRLF 转换提示，不影响退出码）
git diff --name-only  -> docs/requirements.md, tasks/requirement-analysis.md
git diff --stat       -> docs/requirements.md +138, tasks/requirement-analysis.md +98（均为纯追加）
```

- 已跟踪改动仅 2 个文档文件，均为纯追加 P3 附录，未改写原 FR/NFR 基线。
- 未跟踪新增为 `delivery/release-readiness/`（7 文档）与 2 份阶段任务文件。
- **`delivery/acceptance-report.md` 未被修改**（任务单 §3.2 默认不改，Codex 遵守）。
- `.claude/worktrees/` 为本次审查启动的子代理隔离工作树产物，**非 Codex 改动**，提交时须排除（见 §8 提示 R-04）。

---

## 3. 代码差异摘要

| 类别 | 变更 | 审查意见 |
|---|---|---|
| `docs/requirements.md` | 追加 P3 附录 §9~§16（RLS-01~06、NFR-R01~05、数据/接口/权限要求、验收标准、非范围） | 未改原 §1~§8 基线；P3 需求可开发/可测试/可验收/边界清晰 |
| `tasks/requirement-analysis.md` | 追加 P3 附录 §10~§17（来源、FR/NFR 提取、歧义 RQ-01~06、假设 RA-01~04、边界） | 未改原 §1~§9；歧义与假设标注完整 |
| `tasks/claude-plan-P3-Release-01.md` | 新增第一性原理计划 | 11 节齐全（来源/原理/拆解/影响/接口/数据/异常/测试/Codex 边界/验收/风险） |
| `tasks/codex-task-P3-Release-01.md` | 新增 Codex 任务单 | 允许/禁止范围、实现要求、测试要求、返工规则明确 |
| `delivery/release-readiness/*.md` | 新增 7 份验收准备包 | 证据分层、状态机、门禁矩阵、补测卡、差距台账、准入清单、服务证据齐备 |

差异范围与 `tasks/codex-task-P3-Release-01.md` §3.1「恰好新增 7 份」+ §3.3「已由 Claude Code 修改的 4 文件」完全一致，无越界。

---

## 4. 需求满足情况

### 4.1 P3 功能需求（RLS-01 ~ RLS-06）

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| RLS-01 | 46 FR 证据索引 | 是 | `fr-evidence-index.md` 按 9 模块分组，实测逐行计数 FR 行 = 46；每条含能力/证据 ID+路径/环境/状态/责任角色/下一步 |
| RLS-02 | 上线门禁矩阵 | 是 | `release-gate-matrix.md` 覆盖流程 §9 的 8 类门禁 + 补充兼容/文档/发布回退；每行含 Gate ID/来源/阈值/最低证据/现有证据/状态/阻断级/角色/到期日 |
| RLS-03 | 生产等价补测计划 | 是 | `production-validation-plan.md` 含 8 张补测卡（PV-PERF/STABLE/AVAIL/SEC/COMP/EXT/RESTORE/OPS），每卡含前置/数据/既有材料/步骤/采集归档/PASS-FAIL/停止/清理回退/角色状态 |
| RLS-04 | 差距与外部依赖台账 | 是 | `gap-and-dependency-register.md` 四分类（VERIFY_PENDING/FUNCTION_GAP/EXTERNAL_DEPENDENCY/TECHNICAL_DEBT）齐备；功能缺口未被误标为待测 |
| RLS-05 | 发布准入与豁免清单 | 是 | `release-approval-checklist.md` 含唯一决策规则/角色占位/发布前中后/回退/WAIVED 模板；当前结论 NOT_READY/BLOCKED |
| RLS-06 | 交付与服务证据 | 是 | `delivery-service-evidence.md` 覆盖五类文档/测试验收/四类培训/7×24/响应时限/月季巡检/三年运维/版本升级；合同性材料均 BLOCKED |

### 4.2 P3 非功能需求（NFR-R01 ~ R05）

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| NFR-R01 | 证据可追溯 | 是 | 每条证据含证据 ID/门禁 ID/来源路径/环境/角色/摘要；实测 33 个开发证据路径全部存在（见 §7 对抗式） |
| NFR-R02 | 验收真实性 | 是 | 无虚构生产结论；所有生产级门禁为 BLOCKED；README 与各文档校验记录明示未执行生产验证 |
| NFR-R03 | 角色与决策 | 是 | 门禁/豁免/发布清单均定义责任/审批/回退角色与期限占位 |
| NFR-R04 | 信息安全 | 是 | 实测敏感信息扫描 0 命中；仅用 `CONTROLLED-LOCATION-TBD` 占位 |
| NFR-R05 | 可执行性 | 是 | 补测卡含前置/步骤/指标/停止/回退/归档全字段 |

### 4.3 原 FR/NFR 口径未变

- 原 FR-101~FR-906（46 条）与原 NFR-P01~NFR-G01 未被改写阈值或编号。
- **NFR 计数口径确认**（见 §8 专节）：`docs/requirements.md` §3 实际唯一原 NFR 编号为 **24 个**（P01~P07=7、A01~A05=5、S01~S03=3、E01~E03=3、C01~C03=3、M01=1、U01=1、G01=1）。门禁矩阵通过拆分 NFR-P01（标准/定制 2 条）与 NFR-P06（命中率/查询时延/容量 3 条）的复合阈值，形成 **27 条原子门禁**（24+1+2=27），未新增或篡改 NFR 编号，未改变阈值。此口径在 `release-gate-matrix.md` §4 已如实标注并请 Claude Code 确认。**Claude Code 确认：以 24 个唯一原编号为准，27 为原子门禁细化口径，二者不矛盾，处理诚实，不构成偏离。**

---

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 第一性原理推导 | 是 | 计划 §2 从问题本质/最小结果/输入/输出/不可省略过程/核心增强区分/最小路径/避免过度设计逐层推导 |
| 最小可行结果 | 是 | 6 类核心交付物达成，未执行生产操作，未虚构结论 |
| 影响模块边界 | 是 | 计划 §4.1 明确只追加附录+新建 release-readiness 目录，业务代码/迁移/生产配置只读引用 |
| 接口设计 | 是 | 仅定义人工/受控证据接口，未新增应用 API/DB/消息/服务 |
| 数据结构 | 是 | 全为 Markdown 表格，未引入序列化格式或数据库 |
| 异常场景 | 是 | 计划 §5 列 9 类异常（证据失效/误当生产/冲突/无环境/未达标/破坏/敏感/规范缺失/口头豁免/回退未验证） |
| 测试策略 | 是 | 计划 §6 以可追溯性/结构完整性/诚实性为重，未要求 Maven/npm |
| Codex 实现边界 | 是 | 计划 §7.1/7.2 允许/禁止清单与 codex-task 一致 |
| 验收标准 | 是 | 计划 §8 八项验收标准达成 |
| 风险与回滚 | 是 | 计划 §9 风险表 + 文档回滚方式（删除目录/还原附录） |
| 避免过度设计 | 是 | 未引入发布平台/工作流引擎/CI 插件/新依赖 |

---

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 只实现任务单列出任务 | 通过 | 仅 7 份 P3 文档 + Claude 已改的 4 文件未动 |
| 不重新解释需求 | 通过 | P3 附录引用原需求，未改写 FR/NFR 结论 |
| 不覆盖 Claude 需求判断 | 通过 | requirements/analysis 附录为 Claude 生成，Codex 未改 |
| 最小改动 | 通过 | 纯追加 + 新建目录，无重构 |
| 不修改密钥/生产配置 | 通过 | 无 .env/证书/k8s-prod/连接串改动 |
| 不修改无关模块 | 通过 | 无业务代码/测试/前端/迁移/构建配置改动 |
| 不进行无关重构 | 通过 | acceptance-report.md 未改 |
| 不引入大型依赖 | 通过 | 仅 Markdown 文档 |
| 补充/更新测试 | 通过 | 任务为文档任务，静态自检（git diff --check/路径/覆盖/状态机/敏感信息）已执行并记录 |
| 完成后运行测试 | 通过 | 静态自检结果真实记录；任务明确不要求 Maven/npm |
| 输出修改文件/命令/结果/风险 | 通过 | 各文档含校验记录小节 |
| 未执行生产/外部操作 | 通过 | README §6 与各校验记录明示未执行压测/DAST/SCA/演练/恢复/联调/发布 |

---

## 7. 对抗式审查（Adversarial Review）

### 7.1 攻击面枚举

本任务为文档/证据任务，攻击面集中于：①状态机真实性（是否有无证 PASS / 字段不全 WAIVED）；②证据可追溯性（引用路径是否真实存在）；③覆盖完整性（46 FR / 24 NFR / RLS / NFR-R 是否全映射）；④分类正确性（功能缺口是否伪装为待测）；⑤范围真实性（diff 是否仅文档、是否改敏感文件、是否改历史结论）；⑥敏感信息泄露；⑦虚构生产结论；⑧NFR 计数口径。

### 7.2 构造的反例及追踪结果

| 反例 | 验证方式 | 结果 |
|---|---|---|
| 存在无证 PASS（DEV 证据被升格为生产 PASS） | 逐行扫矩阵状态列 | **已反驳**：所有门禁状态均为 BLOCKED，无 PASS；README 明示 DEV 证据不可单独使生产门禁 PASS |
| 存在字段不全的 WAIVED | 检查 WAIVED 记录 | **已反驳**：当前无 WAIVED；准入清单 §7 模板字段全 TBD；台账显式声明"当前无 WAIVED" |
| FR-705/FR-803 被误标为已联调 PASS | 查 fr-evidence-index | **已反驳**：两者均为 `EXTERNAL_DEPENDENCY/BLOCKED`，明确"仅 Mock/规范未提供" |
| 功能缺口（MFA/IAM/SSO/ABAC/证书）被标为"仅待测试" | 查 gap 台账分类 | **已反驳**：RISK-F-01~04 为 FUNCTION_GAP，与 VERIFY_PENDING 严格区分 |
| 引用路径失效（虚构证据文件） | PowerShell 遍历 33 个开发证据路径 + 仓库相对路径 | **已反驳**：MISSING_DEV_EVIDENCE 为空，33/33 存在；BROKEN_PATHS 为空 |
| 状态枚举越界（出现 PASS/FAIL/BLOCKED/WAIVED 之外值） | 正则匹配反引号状态 | **已反驳**：UNEXPECTED_BACKTICK_STATUS 为空 |
| 覆盖不全（漏 NFR/RLS/NFR-R） | 全量关键字匹配 | **已反驳**：MISSING_COVERAGE 为空，24 NFR + RLS-01~06 + NFR-R01~05 全覆盖 |
| 敏感信息泄露（密码/令牌/连接串/私钥） | 正则扫描全 7 文档 | **已反驳**：SENSITIVE_MATCHES=0 |
| git diff --check 空白错误 | 执行命令 | **已反驳**：退出码 0 |
| diff 越界（改业务代码/迁移/生产配置） | git diff --name-only | **已反驳**：仅 2 个文档追加；acceptance-report.md 未改 |
| 虚构生产结论（声称执行了压测/DAST/演练） | 读各文档校验记录 | **已反驳**：均明示"未执行"，结论 NOT_READY/BLOCKED |
| commit 基线虚构 | git log 核对 | **已反驳**：README 称基线 d8d9f4af，与 git log 最近提交一致 |
| FR 行数不足 46 | 逐行正则计数 | **已反驳**：FR_ROWS=46 |
| WAIVED 到期自动恢复机制缺失 | 查 README §5 | **已反驳**：明确"WAIVED 到期自动恢复 BLOCKED" |
| 开发证据作者单方批准豁免 | 查 README §5 / 准入清单 | **已反驳**：明确"开发证据作者不能单方批准自己的豁免" |

### 7.3 独立子代理交叉验证发现（已由 Claude Code 逐行复核确认）

一个独立审查子代理（ac574717b3a150d8a）完成并返回发现：P2 阶段已有的 `delivery/chaos-drill/`、`perf/monitor/`、`delivery/backup-restore/` 脚本存在"误报通过"风险--会在未真实完成验证时打印 RTO/completed 并以 exit 0 退出。Claude Code 已逐行复核 4 个关键脚本，**发现全部属实**：

| 脚本 | 行 | 属实缺陷 | 误报后果 |
|---|---|---|---|
| `delivery/chaos-drill/db-failover.sh` | 19-20, 29 | `kubectl wait pod -l standby --for=Ready` 对早已 Ready 的 standby 立即返回 0；line 20 仅 echo "真实提升命令环境相关"，无实际主备提升/切流命令；line 29 打印 RTO_SECONDS | 操作者据 exit 0 + RTO 把 G-A03/A04（RPO≤5min/RTO≤30min）误标 PASS，而真实 DB 故障切换从未发生 |
| `delivery/chaos-drill/redis-down.sh` | 14, 16 | `curl -fsS $HEALTH_URL \|\| true` 吞掉健康检查失败；`grep "falling back to local quota counter" \|\| true` 在回退日志未出现时仍继续；line 23 打印 RTO | Redis 宕机把应用打挂、本地配额回退从未触发，演练仍"通过"，G-A02 误标 PASS |
| `delivery/chaos-drill/kafka-outage.sh` | 15, 23 | `grep "falling back to JDBC" \|\| true` 吞掉回退缺失；`kubectl scale ... \|\| true` 吞掉消费者扩容失败；line 26 打印 RTO | 调用日志回退 JDBC 能力从未验证，G-A02 误标 PASS |
| `delivery/chaos-drill/dual-active-switch.sh` | 14-16, 23 | scale primary 到 0 后对一直在线的 secondary 求 rollout status 立即返回；无流量切换、无 RPO 标记比对、无数据一致性校验；line 23 打印 RTO | G-A04（同城双活 RPO/RTO）可被误标 PASS 而无真实切换 |
| `perf/monitor/collect-metrics.sh` | 30 | sed 正则要求 value 后紧跟 `}]`，多 measurement 指标（`jvm.gc.pause`、`http.server.requests`）返回 `[{...},{...}]`，value 后是 `},{` 不匹配，静默写 NA | 48h CSV 全程"UP"但 GC/HTTP 关键指标列为 NA，操作者据"无宕机"把 NFR-A01 标 PASS，而内存泄漏/性能下降趋势数据缺失 |
| `delivery/backup-restore/restore-db.sh` | 53-69 | `mysql < backup` 加载 0 行也 exit 0；逐表 `SELECT COUNT(*)` 只打印不比对源；line 69 打印 "DB restore completed"，无源/目标计数或哈希校验 | 空/部分恢复被误报通过，G-PERSIST-01 误标 PASS（而 plan:112 要求"表/对象计数与哈希"） |

**归属判定**：这些脚本是 P2-02/P2-03/P2-05 产物，**非 P3 Codex 本次新增**；`tasks/codex-task-P3-Release-01.md` §4 明确禁止修改 `delivery/chaos-drill/`、`perf/`、`delivery/backup-restore/` 下的脚本，Codex 只能引用。因此这**不是 Codex 本次改动缺陷**，而是 P3 补测计划引用工具时的**风险暴露点**：`production-validation-plan.md` 把这些脚本列为"既有材料"却未标注其误报倾向，未来机构按补测卡执行时，脚本可能 exit 0 + 打印 RTO，操作者据此把 G-A01/A02/A03/A04/PERSIST-01 误标 PASS--恰恰违背 P3 防虚假准入的核心目标（NFR-R02 验收真实性、NFR-R05 可执行性的隐含"产出真实结论"要求）。

**正向项**：`restore-db.sh:48-51` 有目标库非空拒绝（exit 65），防覆盖式恢复；`collect-metrics.sh` 的 `jvm.memory.used`、`hikaricp.connections.active` 单 measurement 指标可正常采集（仅多 measurement 指标 NA）。

**第二个子代理（abc47cf907282a369）另返回 5 条候选问题，Claude Code 逐条证伪结果：**

| 候选 | 摘要 | 证伪结果 |
|---|---|---|
| 1 | 各文档"校验记录"为叙述，未留痕实际命令/输出/退出码 | **存活 P2**（F-07）：属实。校验记录确为叙述（"本次仅进行了仓库本地静态检查"），违反 codex-task §7.4"实际执行的静态检查命令、完整/足够的真实输出摘要和退出码"与 NFR-R01；G-R04"现有证据=本次静态检查记录"实为空指。要求补命令+输出摘要+退出码留痕 |
| 2 | NFR 24 vs 27 口径不一致 | **已处理**（F-01）：见 §8，本次审查已确认口径，不重复 |
| 3 | plan/task blockquote 行尾随空格，git diff --check 对未跟踪文件不可见但提交后报 | **存活 P3，已修复**（F-08）：属实。`tasks/claude-plan-P3-Release-01.md` 与 `tasks/codex-task-P3-Release-01.md` 的 line 3-5 blockquote 行有尾随两空格（markdown 硬换行）。**系 Claude Code 自身文档缺陷**（codex-task §3.3 明示 Codex 不得改这两份），已由 Claude Code 清理尾随空格，内容完整 |
| 4 | WAIVED 到期"自动恢复 BLOCKED"仅靠人工，无 CI 强制 | **已反驳/降级**：codex-task §6 明确"不新增长期维护的检查脚本或依赖"，禁止建 CI；当前 0 条 WAIVED；准入清单已要求人工复核与到期日字段。属组织流程而非代码缺陷，不作为返工项 |
| 5 | FR-505、FR-606 证据=整份报告无章节锚定，粒度弱于其余 44 条 | **存活 P3**（F-09）：属实。FR-505 证据 `delivery/acceptance-report.md`、FR-606 证据 `delivery/p2-05-report.md` 均为整份报告无章节锚定。边界合规（codex-task F-2 允许"可定位的报告引用"）但弱于 NFR-R01"可定位到版本化文件/原始输出"的粒度要求；建议补章节/行锚定 |

**第三个子代理（abf1f84387e489bc4）返回核心阻断发现（P1，已由 Claude Code 事实链核实）：**

| 发现 | 事实链 | 严重级 |
|---|---|---|
| NFR-E01~E03 补测卡缺失 + 自检虚假声称已覆盖 | ①门禁矩阵 `release-gate-matrix.md:43-45` 列 G-E01/E02/E03，状态 BLOCKED，下一步引用"PV-PERF/AVAIL""接入演练""容量验证"；②差距台账 `gap-and-dependency-register.md:20` RISK-V-12 = NFR-E01~E03 VERIFY_PENDING，下一步"评审并执行相应卡片"；③但 `production-validation-plan.md` 的 8 张 PV 卡覆盖字段（line 17/32/47/62/77/92/107/122）逐张核实均不含 NFR-E01/E02/E03；④§3 校验记录 `line 135` 却声称"并补充 E01~E03、外部联调、恢复和流程门禁" | **P1 阻断** |

**违反**：RLS-03（为未实测 NFR 形成可执行补测计划）--NFR-E01~E03 是未实测 NFR，无补测卡；NFR-R05（补测计划含未实测项的前置/步骤/指标/停止/回退/归档）--E01~E03 无卡；NFR-R02（验收真实性：不得编造不实覆盖声明）--§3 line 135 虚假声称已覆盖 E01~E03。门禁矩阵 G-E01~E03 的"下一步"引用的补测卡不存在，引用悬空。

**第四个子代理（af61b1cd03dcc7f22）返回系统性补测卡覆盖缺口（4 P1 + 2 P2，已由 Claude Code 核实 `development-process-workflow.md` §9 原文 line 402-415）：**

§9 上线门禁原文确认 8 类门禁要求。对照 P3 补测卡，发现以下覆盖缺口（与 AD-04 同性质：RLS-03/NFR-R05 违反）：

| 候选 | 门禁 | 缺口 | 严重级 |
|---|---|---|---|
| 1 | G-PERF-01（§9 性能门禁含"日志聚合达标"） | PV-PERF 采集指标无日志聚合维度（写入吞吐/采集延迟/丢失率/积压/查询时延），无法证实日志聚合达标 | P1 |
| 2 | G-DB-01（§9 数据库门禁：空库/升级库迁移成功，目标结构与库设一致） | 无专用补测卡；PV-COMP 仅覆盖 G-C01/G-C03 兼容，未要求空库+旧库升级+目标库设三方比对 | P1 |
| 3 | G-PERSIST-01（§9 持久化门禁：主链路数据重启后不丢） | PV-RESTORE 步骤为备份/恢复/审计链，无"主链路写入前后应用重启+事务边界+重启后数据一致性"专门 PASS/FAIL | P1 |
| 4 | G-FUNC-01（§9 功能门禁：九大模块主链路+46 FR 有证据） | 无逐 FR 生产等价验收补测卡；FR 索引"下一步"为自然语言，无环境/输入/预期/PASS-FAIL/归档/签署规则 | P1 |
| 5 | NFR-R01/§12 证据元数据 | 证据表缺采集日期/采集角色/完整性校验/版本关联；FR 证据缺 Gate ID 映射 | P2 |
| 6 | RLS-02/R03 到期日 | 矩阵与台账"下一步/到期日"几乎全 TBD，无可跟踪目标日期/里程碑/逾期升级 | P2（部分待机构） |

**另：子代理 ab6e3defb78183edb（429 截断）报告准入规则漏洞，已由 Claude Code 核实属实**：`release-approval-checklist.md` §1 决策规则第 2 条仅捕获"发布处置为 BLOCK 的 BLOCKED"，但 `gap-and-dependency-register.md` RISK-T-01~T-07 为 `MAY_WAIVE + BLOCKED`（技术债，可申请豁免但未获批），未被规则 2 捕获，也未在规则中明确是否阻塞。MAY_WAIVE+BLOCKED 条目处于“可申请豁免未获批”状态，应阻塞直到获批 WAIVED 或关闭，否则可能让技术债滑过准入。属 P2 改进。

**第六个子代理（abcf4a35535e924b7）独立复核确认 AD-04 P1，并补充 3 项 P2（已由 Claude Code 核实）：**

| 候选 | 位置 | 事实 | 严重级 |
|---|---|---|---|
| 2 | `docs/requirements.md:359`（§15.3 第3条） | 补测范围列"P01~P07、A01~A05、S01~S03、C01~C03、M01/U01/G01"=21 条，**漏 NFR-E01~E03**，与同文件 §3 的 24 条不一致 | P2（属 F-10 上游修正范围） |
| 4 | `release-approval-checklist.md:36` | 发布前清单"性能/可用性"行写"P/A/C/M/U/G 补测"，**漏 E（扩展性）**，仅靠门禁矩阵项间接捕获 G-E01~E03；若 G-E 被改 PASS 而清单未独立复核 E，发布前清单无法独立拦截未验 E 系列 | P2 |
| 3 | 多处 | 27/21/24 三处不一致（plan/task/analysis "27 条"、requirements §15.3 "21 条"、matrix "24 唯一"），属 F-01 + F-10 口径修正范围 | P2（已含） |

**第七个子代理（ae6dce0bd1df56bad）审查补测卡可执行性**：结论"未发现高置信缺陷"--逐卡核字段齐全、阈值准确（对照 technical-requirements §3）、统计时间口径明确、无虚假 PASS/WAIVED。**与 AD-04/AD-05 不矛盾**：该子代理核的是"已存在的 8 张卡"的字段/阈值，未对"NFR-E01~E03 是否有卡覆盖"做语义核验，故未发现 E 系列缺口。其正向结论可作为佐证：现有 8 张卡本身质量合格，问题仅在覆盖范围（缺 E 系列卡 + §9 多门禁维度不全）。

**第八个子代理（a1ec74267222bb5c8）返回 2 条高置信缺陷（已由 Claude Code 核实）：**

| 候选 | 位置 | 事实 | 与已有发现关系 |
|---|---|---|---|
| 1 | `release-approval-checklist.md:8` 决策规则2 | 仅拦"发布处置=BLOCK"的 BLOCKED，不覆盖 MAY_WAIVE 技术债；RISK-T-01~T-07 处置 MAY_WAIVE/NON_BLOCKING 且无状态字段，清单不拦截；具体：RISK-T-07（proof_hash，NFR-S02）、RISK-T-06（XSS不改写JSON，NFR-S03）、RISK-T-02（归档表无id索引，NFR-S02）、RISK-T-05（Redis降级，配额精度）可在未修复未豁免下随发布提交 | **强化 AD-06/F-12**：同源，但补充了具体技术债条目与其影响的安全门禁（G-S02/G-S03） |
| 2 | `gap-and-dependency-register.md:54` RISK-T-07 | RISK-T-07 影响 NFR-S02/G01（审计不可篡改），处置 MAY_WAIVE；同台账 RISK-V-07（行15）同影响 NFR-S02/G01 却处置 BLOCK--同范畴处置矛盾；proof_hash 约束遗留使销毁证明哈希在约束/事务/verify时机层面可被绕过 | **新发现 AD-08**（P2 改进）：处置分类一致性；**关键缓解**：RISK-T-07 标注"与 RISK-F-06 联合处理"，RISK-F-06 为 FUNCTION_GAP/BLOCK，已间接阻断该风险，故非 P1 |

**第九个子代理（a8ddeb7a141261b13）返回 6 条候选（Claude Code 逐条核实）：**

| 候选 | 位置 | 事实 | 核实结果 |
|---|---|---|---|
| 1 | `fr-evidence-index.md:52` FR-505 | 证据 E-FR-505-01 指向 `acceptance-report.md`，但该文件 `:19` 把 FR-501~505 合并为一行"消费方生命周期、配额拦截、审计测试"，**无"质量与性能反馈"可定位内容**，形同空引用 | **属实，归 F-09 强化**（P2）：FR-505 状态 BLOCKED、下一步已注"补可定位证据"，状态诚实；但证据当前空引用，违反 NFR-R01，须补具体证据或显式标"无开发证据" |
| 2 | G-U01/G-E01~E03 无补测卡 | G-U01 下一步未指 PV 卡；G-E01~E03 下一步指 PV-PERF/AVAIL 但覆盖字段不含 | **已含于 AD-04/AD-05**（P1） |
| 3 | `fr-evidence-index.md:60` FR-603 | 证据仅 Sm4UtilTest+DesensitizeUtilTest，**传输加密/TLS 子项无代码证据**（Glob/grep 仓库无 TLS 测试）；证据列把"传输加密"与 SM4/脱敏并列易误读 | **属实，新发现 AD-09**（P2）：TLS 本是 RISK-V-06 待验证项，状态诚实；但 FR-603 证据列须显式标注"传输加密=无开发证据，待 PROD_EQ 核查"避免误读 |
| 4 | NFR-E01~E03 映射断裂 | 同候选2 | **已含于 AD-04** |
| 5 | G-ROLLBACK-01 与 G-A05 重复映射 NFR-A05 | 均映射 NFR-A05 回滚≤10min | **部分成立，归 P3 提示**：二者来源不同（G-ROLLBACK-01=流程§9发布回退门禁，G-A05=原NFR原子门禁），plan §3 F-3 要求并存，非重复错误；子代理"计数不一致"判断有误（G-ROLLBACK-01 属流程补充门禁非27原子NFR门禁）；可加注说明关系 |
| 6 | G-COMPLY-01 未回引 PV-EXT | 下一步"提供规范、完成联调和合规审查"，未指 PV-EXT，而 PV-EXT 覆盖字段含 G-COMPLY-01 | **属实，P3 提示**：风格不一致，影响走查可追溯性 |

**第十个子代理（a7a49a3750008460d）返回 4 条缺陷（Claude Code 逐条核实）：**

| 缺陷 | 位置 | 事实 | 核实结果 |
|---|---|---|---|
| 1 | 24 vs 27 口径跨文档漂移 | plan:53/73/245、codex-task:260/288 写"27"，requirements §3 实际 24 唯一 | **已含于 F-01/F-10** |
| 2 | NFR-E01~E03 无补测卡 | G-E01~E03 BLOCKED 但无 E 专属卡；plan:86/120/294、codex-task:156-161/187-189、requirements:282/359 均不含 E | **已含于 AD-04/F-10** |
| 3 | plan 内部"六份 vs 七份"自相矛盾 | `plan:98` 写"六份"（列6项不含README），但 `plan:282`"7份"、§4.4 文件表(7行含README)、`codex-task:55`"7份"、实际交付 7 份 | **属实，新发现 AD-10**（Claude 自身文档缺陷）：**已由 Claude Code 修复** plan:98 "六份"→"七份（含README）" |
| 4 | FR-505/606/705/803 证据粒度弱 | 整份报告无章节锚点 | **已含于 F-09/AD-03** |

**第十一个子代理（a1cfeae735450cead）返回 6 条已验证缺陷（Claude Code 逐条核实）：**

| 缺陷 | 位置 | 事实 | 核实结果 |
|---|---|---|---|
| 1 | §15.3 漏 NFR-E01~E03 | requirements:359/plan:294 列 P/A/S/C/M/U/G 不含 E | **已含于 F-10** |
| 2 | RLS-03 §10.3 第2条补测分类漏 E/M/U/G | requirements:282 处理逻辑第2条只列 P/A/S/C | **属实，归 F-10 上游修正范围**：与 §15.3（含M/U/G）和交付物（PV-OPS/PV-SEC 覆盖 M/U/G）口径不一致 |
| 3 | "27 条"与 24 唯一矛盾 | analysis:100/232 | **已含于 F-01** |
| 4 | fr-evidence-index 表头"环境"列填的是 Evidence class | 表头第4列"环境"，填值全为 DEV_TEST/DEV_REVIEW/EXTERNAL_DEPENDENCY；plan §4.2.1 明确 Environment=DEV/PROD_EQ/PROD/N/A，Evidence class=DEV_TEST/... | **属实，新发现 AD-11**（P2）：字段语义错配，违反 NFR-R01 与 §14 环境分层；应拆为"证据类"+"环境"两列或修正表头/填值 |
| 5 | 准入规则引用门禁矩阵不存在的"发布处置"字段 | checklist:8 引用"发布处置"，但门禁矩阵 3 张表（行7/23/55）无此列，该字段只在 gap 台账 | **属实，新发现 AD-12**（P2）：准入规则不可仅凭矩阵机械执行，须交叉查台账；MAY_WAIVE/NON_BLOCKING 技术债与矩阵"阻断"语义冲突 |
| 6 | PV-PERF 前置未含 RISK-F-05 | PV-PERF:18 前置只列环境/授权；但 RISK-F-05=FUNCTION_GAP/BLOCK（connector 批量能力未改造），G-P03 要求"补能力后 PV-PERF"；PV-PERF:19 要求 100万真实 connector 批次，执行时能力未实现必失败；PV-SEC:63 已有"功能缺口已实现"前置先例 | **属实，新发现 AD-13**（P2，可执行性）：违反 NFR-R05 前置条件完整性 |

**第十二个子代理（a0f2bdef6089da64e）返回 6 项候选（Claude Code 逐条核实）：** 原始技术要求 -> requirements.md -> 矩阵的逐级收敛中丢失 4 类原始要求 + "27 条"归因 + G-ROLLBACK-01 来源。

| 候选 | 位置 | 事实 | 核实结果 |
|---|---|---|---|
| 1 | "27 条"归因错误 | matrix:71-72 称 27 来自 P01/P06 拆分，但 analysis:96-98 的 27 含实施/培训/运维（非NFR） | **已含于 F-01**：口径差异已记录 |
| 2 | ISO27001 资质无门禁/无台账条目 | technical-req:147 含 ISO27001；requirements:229/analysis:182 明确属供应商资质非软件功能（决定合理），但 release-readiness 全包无 ISO27001 条目跟踪 | **属实，新发现 AD-17**（P3 提示）：决定合理但交付包缺差距台账条目标注"ISO27001 属供应商资质，需机构/供应商提供证书" |
| 3 | 国产中间件维度缺失 | technical-req:125、analysis:90 含"国产中间件"；requirements NFR-C01、matrix G-C01、PV-COMP 省略 | **属实，新发现 AD-14**（P2）：PV-COMP:79 提"中间件版本"但未标"国产中间件"为独立兼容维度；目标环境用国产中间件可能部署失败 |
| 4 | §3.3 安全子项被丢弃（24h修复/定期检测报告） | technical-req:113 含"漏洞扫描与安全基线检查、定期输出安全检测报告、高危漏洞24h修复"；requirements:210 第6.4节含24h但矩阵 G-S03/PV-SEC PASS/FAIL 未含24h时限与定期报告交付物 | **属实，新发现 AD-15**（P3 提示）：PV-SEC 仅"无未处置严重/高危"，无24h时限与定期检测报告强制 |
| 5 | G-C01 未保留 SUNDB 可选标注 | requirements NFR-C01 标"SUNDB可选"，但 matrix G-C01:46、PV-COMP 仅"达梦/OceanBase"省略 SUNDB；analysis:90 列三DB未标可选 | **属实，新发现 AD-16**（P3 提示）：三方口径不一，若机构选 SUNDB 无测试项 |
| 6 | G-ROLLBACK-01 来源"发布要求"不可定位 | matrix:19 来源"NFR-A05；发布要求"，未指向具体文档章节，其余门禁均引用可定位来源 | **属实，新发现 AD-18**（P3 提示）：违反 NFR-R01 追溯，应标 §5.3 或流程 §9 |

**第十三个子代理（a23d8b1885cfae3eb）跨文件追踪返回 6 条（Claude Code 逐条核实）：** 候选1~4 已含于 AD-04/AD-01；候选5/6 新发现。

| 候选 | 位置 | 事实 | 核实结果 |
|---|---|---|---|
| 1 | NFR-E01~E03 补测卡悬空+虚假覆盖 | production-validation-plan:135 声称覆盖 E01~E03，8 张 PV 卡均不含 | **已含于 AD-04/F-10** |
| 2 | collect-metrics.sh sed 不匹配多 measurement | jvm.gc.pause/http.server.requests 返回多 measurements，sed 失配写 NA | **已含于 AD-01/F-06** |
| 3 | restore-db.sh 无源/目标计数或哈希校验即 exit 0 | 空/部分恢复也 exit 0 打印 completed | **已含于 AD-01/F-06** |
| 4 | chaos 脚本吞失败+RTO 误报 | db-failover/redis-down/kafka-outage/dual-active-switch 均 \|\| true 吞失败 | **已含于 AD-01/F-06** |
| 5 | 稳定性监控指向网关，网关无 HikariCP，DB连接池指标恒 NA | stability-test-plan:34 BASE_URL=网关、:48 采集 hikaricp；网关 pom 无 jdbc/datasource/hikari 依赖（实测 grep 全空），/actuator/metrics/hikaricp.connections.active 404->NA | **属实，新发现 AD-19**（P2）：PV-STABLE 48h 内 DB连接池耗尽采集无感知，应改 BASE_URL 指向有 DB 的服务或分服务采集 |
| 6 | chaos 脚本与 ops-manual 默认指向不存在的 deployment | node-down.sh:5/dual-active-switch.sh:5-6/rolling-upgrade.sh:5/ops-manual:23-24 默认 platform-a/platform-b；实际模块为 gateway/auth/partner/pipeline/quality/billing/common/ui（实测 ls + Dockerfile ARG MODULE=platform-gateway），无 platform-a/b | **属实，新发现 AD-20**（P2）：node-down.sh 在 set -euo pipefail 下 kubectl scale deployment/platform-a NotFound 非零退出，演练未开始即失败 |

**第十七个子代理（aa1bf79bd1efd71b5）聚焦"上线决策逻辑"返回 6 条（Claude Code 逐条核实）：** 候选1/3/4 已含于 AD-06/AD-04/AD-08；候选2/5/6 为准入算法新发现。

| 候选 | 位置 | 事实 | 核实结果 |
|---|---|---|---|
| 1 | 准入规则只拦 BLOCK 处置的 BLOCKED | checklist:8 规则2 | **已含于 AD-06/F-12** |
| 2 | 矩阵行3"任一未豁免阻断 BLOCKED 均不准入" vs checklist行8"仅拦 BLOCK 处置"口径不一致 | matrix:3 覆盖所有 BLOCKED；checklist:8 自命唯一规则却只覆盖 BLOCK 处置，MAY_WAIVE/NON_BLOCKING 但仍 BLOCKED 的项（RISK-T-01~T-07）被漏 | **属实，新发现 AD-21**（P2）：两文档对同一批 BLOCKED 给出不同准入口径，宽松路径可放行未实测项 |
| 3 | NFR-E01~E03 虚假覆盖 | production-validation-plan:135 | **已含于 AD-04/F-10** |
| 4 | RISK-T-07 MAY_WAIVE 与 RISK-V-07 BLOCK 矛盾 | gap:54 vs gap:15 | **已含于 AD-08/F-14** |
| 5 | 准入规则未把"回退决策人未指定/回退触发条件未确认"列为独立不准入条件 | checklist:7-10 规则1-4 只检查 FAIL+未豁免阻断 BLOCKED，不检查回退决策人（行23 BLOCKED/机构待指定）与触发条件；行75 仅绑定 G-ROLLBACK-01 计时证据 | **属实，新发现 AD-22**（P2）：其他门禁全关后若回退决策人仍待指定，规则不触发不准入，违反 NFR-A05 与 CLAUDE.md §7.1 回退路径未验证拦截 |
| 6 | G-RLS-05 现有证据指向 checklist 本身，形成自证循环 | matrix:61 G-RLS-05 现有证据=`release-approval-checklist.md`；checklist 填完整后其存在即被当 G-RLS-05 PASS 证据，绕过 RISK-X-05（机构发布制度未取得） | **属实，新发现 AD-23**（P2）：自证循环，G-RLS-05 须以机构发布管理制度为证据，不得以 checklist 自身为证 |

### 7.4 未实测的反例（明示）

- **门禁矩阵 PASS 最低证据规则无实例验证**：当前无任何 PASS，故"PASS 必须环境匹配原始证据"规则无法用实例验证，仅规则文本存在。标注为未实测，但因无 PASS 即无虚假准入风险，不构成阻断。
- **补测卡可执行性未由执行角色走查**：任务单 §6.4 要求"执行角色逐卡走查"，当前仅 Codex 自检 + Claude 文本审查，未由性能/运维/安全/灾备负责人实际走查。标注未实测，对应门禁 G-RLS-03/G-R05 仍 BLOCKED，处理诚实。
- **G-RLS-04 差距分类未由安全/架构负责人复核**：分类由 Codex 基于 P2 报告作出，未经独立角色确认。标注未实测，门禁仍 BLOCKED。

### 7.5 存活缺陷

经上述证伪尝试，**发现 1 项存活 P1 阻断项（AD-04，见 §7.3 第三个子代理发现）**。存活项为：

| 编号 | 缺陷 | 严重级 | 归属 |
|---|---|---|---|
| AD-04 | NFR-E01~E03 补测卡缺失 + §3 自检虚假声称已覆盖（见 §7.3 第三个子代理） | **P1 阻断** | P3 文档可修复（新增 PV-ARCH 补测卡 + 修正 §3 line 135） |
| AD-05 | §9 门禁补测卡系统性覆盖缺口：G-PERF-01 日志聚合、G-DB-01、G-PERSIST-01 重启维度、G-FUNC-01 逐 FR（见 §7.3 第四个子代理） | **P1 阻断** | P3 文档可修复（补齐/补强对应 PV 卡 + 证据元数据） |
| AD-06 | 准入规则未捕获 MAY_WAIVE+BLOCKED 条目（见 §7.3） | P2 改进 | release-approval-checklist.md §1 规则补强 |
| AD-07 | 发布前清单"性能/可用性"行漏列 E（扩展性）独立检查（见 §7.3 第六子代理候选4） | P2 改进 | release-approval-checklist.md:36 补 E 系列独立检查 |
| AD-08 | RISK-T-07 影响审计不可篡改（NFR-S02）却处置 MAY_WAIVE，与同范畴 RISK-V-07（BLOCK）矛盾（见 §7.3 第八子代理候选2） | P2 改进 | gap 台账复核 RISK-T-07/T-02/T-06 等安全相关技术债处置分类；RISK-F-06 BLOCK 已间接缓解 |
| AD-09 | FR-603 证据列把"传输加密/TLS"与 SM4/脱敏并列，但 TLS 子项无代码证据（见 §7.3 第九子代理候选3） | P2 改进 | fr-evidence-index.md:60 显式标注"传输加密=无开发证据，待 PROD_EQ 核查" |
| AD-10 | plan:98"六份"与 plan:282/§4.4/codex-task:55/实际交付"七份"矛盾（见 §7.3 第十子代理缺陷3） | P3 提示（已修复） | Claude Code 已修正 plan:98 为"七份（含README）" |
| AD-11 | fr-evidence-index 表头"环境"列填的是 Evidence class（DEV_TEST/DEV_REVIEW/EXTERNAL_DEPENDENCY），非 Environment 枚举（DEV/PROD_EQ/PROD/N/A），plan §4.2.1 明确二者不同（见 §7.3 第十一子代理缺陷4） | P2 改进 | 拆为"证据类"+"环境"两列或修正表头/填值 |
| AD-12 | 准入规则2引用门禁矩阵不存在的"发布处置"字段，该字段只在 gap 台账（见 §7.3 第十一子代理缺陷5） | P2 改进 | 准入规则补交叉查台账说明，或矩阵补"发布处置"列 |
| AD-13 | PV-PERF 前置未含 RISK-F-05（connector 批量能力改造完成），执行时能力未实现必失败（见 §7.3 第十一子代理缺陷6） | P2 改进 | PV-PERF:18 补前置"RISK-F-05 connector 批量能力改造完成" |
| AD-14 | 原始 §3.5/分析含"国产中间件"，但 requirements NFR-C01/matrix G-C01/PV-COMP 省略（见 §7.3 第十二子代理候选3） | P2 改进 | NFR-C01/G-C01/PV-COMP 补"国产中间件"独立兼容维度 |
| AD-15 | §3.3 高危漏洞24h修复时限与定期安全检测报告交付物在矩阵 G-S03/PV-SEC PASS/FAIL 未强制（见 §7.3 第十二子代理候选4） | P3 提示 | G-S03/PV-SEC 补24h时限与定期报告交付物 |
| AD-16 | G-C01/PV-COMP 省略 SUNDB 可选标注，三方口径不一（见 §7.3 第十二子代理候选5） | P3 提示 | G-C01/PV-COMP 补"SUNDB 可选"并标可选不测或补测 |
| AD-17 | ISO27001 资质认证在 release-readiness 全包无差距台账条目跟踪（见 §7.3 第十二子代理候选2） | P3 提示 | gap 台账补条目"ISO27001 属供应商资质，需机构/供应商提供证书" |
| AD-18 | G-ROLLBACK-01 来源"发布要求"不可定位（见 §7.3 第十二子代理候选6） | P3 提示 | matrix:19 来源改为具体文档章节（§5.3 或流程 §9） |
| AD-19 | 稳定性监控 BASE_URL 指向网关，网关无 HikariCP，DB连接池指标恒 NA（见 §7.3 第十三子代理候选5） | P2 改进 | stability-test-plan/collect-metrics BASE_URL 改指向有 DB 的服务或分服务采集 hikaricp |
| AD-20 | chaos 脚本与 ops-manual 默认指向不存在的 deployment（platform-a/platform-b），实际模块为 gateway/auth/partner/pipeline/quality/billing/common/ui（见 §7.3 第十三子代理候选6） | P2 改进 | chaos 脚本/ops-manual 默认值改为真实模块名（如 platform-partner/pipeline） |
| AD-21 | 矩阵行3"任一未豁免阻断 BLOCKED 均不准入" vs checklist行8"仅拦 BLOCK 处置"口径不一致，宽松路径可放行 MAY_WAIVE/NON_BLOCKING 但仍 BLOCKED 的项（见 §7.3 第十七子代理候选2） | P2 改进 | checklist §1 规则2 对齐矩阵行3，或矩阵补"发布处置"列并统一两文档口径 |
| AD-22 | 准入规则未把"回退决策人未指定/回退触发条件未确认"列为独立不准入条件（见 §7.3 第十七子代理候选5） | P2 改进 | checklist §1 补规则"回退决策人未指定或回退触发条件未确认：不准入" |
| AD-23 | G-RLS-05 现有证据指向 checklist 本身，形成自证循环，可绕过 RISK-X-05（见 §7.3 第十七子代理候选6） | P2 改进 | G-RLS-05 现有证据改为 `CONTROLLED-LOCATION-TBD`（机构发布管理制度），不得以 checklist 自身为证 |
| AD-01 | P3 补测计划引用的 6 个 P2 脚本存在“误报通过”风险（见 §7.3），P3 文档未标注 | **P2 改进** | P3 文档可修复（不动 P2 脚本本身） |
| AD-02 | 各文档校验记录为叙述，未留痕实际命令/输出/退出码（见 §7.3 候选1） | **P2 改进** | P3 文档可修复 |
| AD-03 | FR-505/606 证据为整份报告无章节锚定，粒度弱（见 §7.3 候选5） | **P3 提示** | P3 文档可修复 |
| 其他 | P2/P3 级改进或待机构确认项（见 §10） | P3 提示 | - |

**AD-01 不阻断 P3 文档交付通过**：当前所有相关门禁仍 BLOCKED，未发生已发生的虚假 PASS（NFR-R02 未被违反）。但属"可修复的真实性风险"，必须在补测执行前修复--否则未来按补测卡执行时可能产生虚假 PASS，违背 P3 核心目标。

**AD-04 为存活 P1 阻断项，触发“需返工”**：门禁矩阵 G-E01~E03 BLOCKED、差距台账 RISK-V-12 指向“评审并执行相应卡片”，但补测卡不存在且 §3 line 135 虚假声称已覆盖，违反 RLS-03/NFR-R05/NFR-R02。必须返工：新增 PV-ARCH 补测卡覆盖 NFR-E01~E03（架构扩展性：微服务/容器/弹性、SDK/新接口≤3工作日/80%可视化/开放API/插件、分布式/PB级/多引擎），并修正 §3 line 135 使其与实际卡片一致。AD-01~AD-03 为 P2/P3 改进项，建议与 AD-04 一并返工。

### 7.6 对"建议通过"结论的反驳尝试

- "为什么不应通过？"→ 候选理由：①NFR 口径 24 vs 27 不一致；②P3 文档门禁（G-RLS/G-R）自身仍 BLOCKED；③P3 补测卡引用的 P2 脚本有误报通过风险（AD-01，见 §7.3）；④NFR-E01~E03 补测卡缺失且 §3 自检虚假声称已覆盖（AD-04，见 §7.3）；⑤§9 多门禁补测卡系统性覆盖缺口（AD-05）+ 准入规则 MAY_WAIVE+BLOCKED 漏洞（AD-06）。
  - 反驳 ①：口径已由 Codex 如实标注并请 Claude 确认，未新增/篡改编号或阈值，不构成虚假证据；本次审查已确认口径。
  - 反驳 ②：G-RLS/G-R 的 BLOCKED 是设计如此——P3 文档交付的门禁须由 Claude Code 最终验收后才能转 PASS，验收前为 BLOCKED 是诚实的，不是缺陷。本次审查（且当前存在 AD-04 P1）**不授权任何 G-RLS/G-R 门禁转 PASS**。即使 AD-04 修复后，G-RLS-03/G-R05 仍须执行角色逐卡走查，G-RLS-05 仍须发布管理制度和有权角色确认；G-RLS-01/02、G-R02 也须在返工后重新独立审查，分别以其矩阵定义的最低证据判定。
  - 反驳 ③ **部分成立**：AD-01（见 §7.3）是真实的可修复风险。但因当前门禁仍 BLOCKED、未发生已发生的虚假 PASS（NFR-R02 未被违反），且修复在 P3 文档范围内可行（标注风险 + 新增 TECHNICAL_DEBT，不动 P2 脚本），故不构成"需返工"的阻断项，列为 P2 改进项（F-06），要求在补测执行前修复。此反驳使结论附带一项改进条件。
  - 反驳 ④ **不成立（确凿 P1 阻断）**：AD-04 事实链经 Claude Code 亲自核实（门禁矩阵 G-E01~E03 BLOCKED + 差距台账 RISK-V-12 指向“评审并执行相应卡片” + 8 张 PV 卡覆盖字段均不含 NFR-E01~E03 + §3 line 135 虚假声称“并补充 E01~E03”），违反 RLS-03/NFR-R05/NFR-R02，构成存活 P1 阻断项。按 CLAUDE.md §7.1 产出要求第 5 条“若发现存活 P1 阻断项，结论改为需返工，不得通过”，结论由“通过”改为“需返工”。
  - 反驳 ⑤ **不成立（确凿 P1 阻断）**：AD-05 经 Claude Code 核实 `development-process-workflow.md` §9 原文（line 402-415），G-PERF-01（日志聚合）、G-DB-01（空库/升级库迁移）、G-PERSIST-01（重启后不丢）、G-FUNC-01（46 FR）的补测卡缺失或步骤不全，违反 RLS-03/NFR-R05，与 AD-04 同性质 P1，强化“需返工”结论。AD-06 准入规则漏洞属 P2，一并返工。AD-10（plan 六/七份矛盾）系 Claude 文档缺陷已修复，不影响 P1 结论。
- “还差什么？”→ 差机构正式发布审批、生产等价环境实测、外部规范提供、组织角色确认，以及补测卡对引用脚本误报风险的标注（AD-01，见 §7.3）——前三者属 P3 明确“不在本次范围”的内容（requirements §16），AD-01 属 P3 文档范围内可修复的改进项。

**结论：已尝试反驳，发现 2 项存活 P1 阻断项（AD-04：NFR-E01~E03 补测卡缺失 + §3 虚假声称；AD-05：§9 多门禁补测卡系统性覆盖缺口）+ 4 项存活改进项（AD-01/02/03 P2/P3、AD-06 准入规则漏洞 P2，见 §7.3）。按 CLAUDE.md §7.1 产出要求第 5 条，结论为“需返工”。返工核心：补齐 PV-ARCH（NFR-E01~E03）+ G-DB-01/G-FUNC-01 专用补测卡 + G-PERF-01 日志聚合指标 + G-PERSIST-01 重启维度步骤，修正 §3 line 135，并补强准入规则与证据元数据。**

---

## 8. NFR 计数口径确认（Codex 提请确认项）

| 项 | 事实 |
|---|---|
| `docs/requirements.md` §3 唯一原 NFR 编号 | **24 个**：P01~P07(7)、A01~A05(5)、S01~S03(3)、E01~E03(3)、C01~C03(3)、M01(1)、U01(1)、G01(1) |
| 任务材料（plan/task）措辞 | 称"27 条原 NFR" |
| 矩阵处理 | 拆分 NFR-P01→2 条（标准/定制）、NFR-P06→3 条（命中率/查询时延/容量），形成 27 条原子门禁；未新增编号、未改阈值 |
| 矩阵 §4 标注 | 如实记录 24 唯一 vs 27 原子差异，请 Claude 确认 |
| **Claude Code 确认** | **以 24 个唯一原编号为正式口径；27 为原子门禁细化（24 + P01 拆 1 + P06 拆 2 = 27），二者不矛盾。Codex 处理诚实，未虚构编号/阈值，不构成偏离。** |
| 改进建议 | P3 提示：建议后续将 `tasks/claude-plan-P3-Release-01.md` §2.3 与 `tasks/codex-task-P3-Release-01.md` §6.4 的"27 条"措辞修正为"24 个唯一 NFR 编号，拆分为 27 条原子门禁"，以消除 plan/task 与 requirements 之间的口径歧义。属文档一致性优化，非阻断。 |

---

## 9. 测试检查

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `git diff --check` | 是 | 通过（退出码 0） | 仅 LF→CRLF 转换提示，不影响退出码 |
| 7 份 P3 文档存在性 | 是 | 通过 | DOC_COUNT=7，MISSING 为空 |
| FR 行数核对 | 是 | 通过 | FR_ROWS=46，覆盖 46 条 FR |
| 状态枚举核对 | 是 | 通过 | 仅 PASS/FAIL/BLOCKED/WAIVED，无越界值 |
| 覆盖性核对（24 NFR + RLS-01~06 + NFR-R01~05 + 流程 §9 门禁） | 是 | **不通过** | 编号关键词匹配 MISSING_COVERAGE 为空，但语义核验发现：①NFR-E01~E03 无 PV 补测卡且 §3 虚假声称已覆盖（AD-04/P1）；②§9 门禁 G-PERF-01 日志聚合 / G-DB-01 / G-PERSIST-01 重启 / G-FUNC-01 逐 FR 补测卡缺失或步骤不全（AD-05/P1） |
| 仓库相对路径核对 | 是 | 通过 | BROKEN_PATHS 为空 |
| 开发证据路径核对（33 条） | 是 | 通过 | MISSING_DEV_EVIDENCE 为空 |
| 敏感信息扫描 | 是 | 通过 | SENSITIVE_MATCHES=0 |
| 范围核对 `git diff --name-only` | 是 | 通过 | 仅 2 个文档追加，无业务代码/迁移/生产配置 |
| Maven/npm/压测/DAST/演练/恢复/联调/发布 | 否（任务不要求） | N/A | 任务单 §6 明确不要求且不得声称执行；Codex 未执行并如实记录 |

> 注：后台启动的独立审查子代理多数因 429 限流失败，但 14 个子代理完成并返回对抗式发现：ac574717b3a150d8a（P2 脚本误报通过风险，见 §7.3 AD-01，逐行复核 4 脚本属实）、abc47cf907282a369（5 条候选，见 §7.3 第二表，逐条证伪后存活 F-07/F-09、已修复 F-08、已处理 F-01、已反驳候选4）、abf1f84387e489bc4（**P1 阻断 AD-04**：NFR-E01~E03 补测卡缺失 + §3 虚假声称，见 §7.3，事实链已核实）、af61b1cd03dcc7f22（**P1 阻断 AD-05** + P2 AD-06：§9 多门禁补测卡覆盖缺口 + 准入规则漏洞，见 §7.3，已核实 workflow §9 原文）、abcf4a35535e924b7（独立复核确认 AD-04 P1，补充 P2 候选2/4：requirements §15.3 漏列 E + 发布前清单漏 E，已核实）；另有 ab6e3defb78183edb 因 429 截断但 partial result 报告准入规则漏洞（已并入 AD-06）；ae6dce0bd1df56bad 核现有 8 卡字段/阈值合格、无虚假 PASS/WAIVED，与 AD-04/AD-05 不矛盾）、a1ec74267222bb5c8（强化 AD-06 + 新发现 AD-08：RISK-T-07 影响审计不可篡改却处置 MAY_WAIVE，与 RISK-V-07 BLOCK 矛盾，见 §7.3，RISK-F-06 BLOCK 间接缓解）、a8ddeb7a141261b13（新发现 AD-09：FR-603 TLS 子项无代码证据；强化 F-09：FR-505 证据空引用；候选5/6 P3 提示，见 §7.3）、a7a49a3750008460d（新发现 AD-10：plan:98"六份 vs 七份"矛盾，Claude 已修复；缺陷1/2/4 已含于 AD-04/F-01/F-09，见 §7.3）、a1cfeae735450cead（新发现 AD-11：fr-evidence-index"环境"列填 Evidence class；AD-12：准入规则引用不存在的"发布处置"字段；AD-13：PV-PERF 前置漏 RISK-F-05；缺陷1/3 已含于 F-10/F-01，见 §7.3）、a0f2bdef6089da64e（新发现 AD-14：国产中间件维度缺失；AD-15：24h/定期报告未强制；AD-16：SUNDB可选标注缺失；AD-17：ISO27001无跟踪条目；AD-18：G-ROLLBACK-01来源不可定位；候选1已含F-01，见 §7.3）、a23d8b1885cfae3eb（新发现 AD-19：稳定性监控 DB连接池指标恒 NA（网关无 HikariCP）；AD-20：chaos 脚本/ops-manual 默认 deployment platform-a/b 不存在；候选1~4 已含 AD-04/AD-01，见 §7.3）、ab462705955bcfe65（独立确认 AD-04 根因 requirements §15.3 漏列 E；候选2~6 已含 AD-01/02/13/03/01，见 §7.3）、a46c01295b238f67a（独立确认范围干净/基线属实/无虚假；27 vs 24 根因在 requirement-analysis:100 基线，见 §7.3）、aa1bf79bd1efd71b5（新发现 AD-21：矩阵 vs checklist 准入口径不一致；AD-22：回退决策链未列为独立不准入条件；AD-23：G-RLS-05 自证循环；候选1/3/4 已含 AD-06/04/08，见 §7.3）。其余核对由 Claude Code 直接执行，证据为本审查中实际命令输出。

---

## 10. 安全与风险检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 敏感信息泄露 | 通过 | 无真实账号/令牌/密码/连接串/内部 URL/个人信息/生产导出/漏洞细节；仅 `CONTROLLED-LOCATION-TBD` 占位 |
| 虚构生产结论 | 通过 | 所有生产级门禁 BLOCKED；无虚构性能/RPO-RTO/48h/DAST/等保/国产化/联调结果 |
| 修改敏感文件 | 通过 | 无 .env/证书/k8s-prod/生产配置改动 |
| 修改历史结论 | 通过 | acceptance-report.md 及 P0-P2 报告/审查未改；P3 仅引用 |
| 引入依赖 | 通过 | 仅 Markdown，无新依赖 |
| 范围越界 | 通过 | diff 仅文档追加 |
| 安全缺口误标 | 通过 | MFA/IAM/SSO/ABAC/证书/SCA/DAST/TLS/等保/审计留存均未标 PASS |

### 存活改进项（P2/P3 级，非阻断）

| 编号 | 问题 | 严重级 | 说明 |
|---|---|---|---|
| R-01 | NFR 口径措辞不一致 | P3 提示 | plan/task 写"27 条"，requirements 为 24 唯一；建议修正措辞（见 §8） |
| R-02 | G-RLS/G-R 门禁当前 BLOCKED | P3 提示 | 设计如此；本审查不授权转 PASS。返工后仍须按各门禁矩阵定义的最低证据独立判定 |
| R-03 | 补测卡未由执行角色走查 | P2 改进 | G-RLS-03/G-R05 仍 BLOCKED，须性能/运维/安全/灾备负责人逐卡走查 |
| R-04 | `.claude/worktrees/` 未跟踪目录 | P3 提示 | 审查子代理产物，提交时须排除（`.gitignore` 已含 `.claude/`？提交前确认） |
| R-05 | RQ-01~06 待机构确认 | P2 改进 | 环境/授权/外部规范/发布流程/性能数据集/技术风险处置均待机构，门禁保持 BLOCKED |
| R-06 | 补测卡引用的 P2 脚本误报通过风险（AD-01） | P2 改进 | 见 §7.3；须在 production-validation-plan.md 标注风险 + gap 台账新增 TECHNICAL_DEBT，补测执行前修复 |
| R-07 | 校验记录未留痕命令/输出/退出码（AD-02） | P2 改进 | 见 §7.3 候选1；各文档校验记录补实际命令+输出摘要+退出码 |
| R-08 | plan/task 尾随空格 | P3 提示（已修复） | 见 §7.3 候选3；Claude Code 已清理两份任务文档 blockquote 行尾随空格 |
| R-09 | FR-505/606 证据粒度弱（AD-03） | P3 提示 | 见 §7.3 候选5；补章节/行锚定 |
| R-10 | 证据元数据不完整（缺采集日期/角色/完整性/版本，FR 证据缺 Gate ID） | P2 改进 | 见 §7.3 第四子代理候选5；补元数据字段 |
| R-11 | 到期日全 TBD，无逾期升级 | P2 改进（部分待机构） | 见 §7.3 第四子代理候选6；机构确认后填目标日期 |
| R-12 | 发布前清单漏 E 系列独立检查（AD-07） | P2 改进 | 见 §7.3 第六子代理候选4；补 E 系列复核 |
| R-13 | G-ROLLBACK-01 与 G-A05 并存关系未注明（第九子代理候选5） | P3 提示 | 设计如此（流程门禁 vs NFR 原子门禁），可加注说明非重复 |
| R-14 | G-COMPLY-01 未回引 PV-EXT（第九子代理候选6） | P3 提示 | 风格不一致，补回引 |

---

## 11. 审查结论

```text
✗ 需返工 - 存活 P1 阻断项 AD-04（NFR-E01~E03 补测卡缺失 + §3 虚假声称）+ AD-05（§9 多门禁补测卡系统性覆盖缺口：G-PERF-01 日志聚合 / G-DB-01 / G-PERSIST-01 重启 / G-FUNC-01 逐 FR）
```

**理由**：

1. **需求覆盖**：46 FR、24 唯一 NFR（细化 27 原子门禁）、流程 §9 门禁、RLS、NFR-R 均有矩阵条目；但 RLS-03/NFR-R05/NFR-R02 **未满足**--AD-04 证明 NFR-E01~E03 无可执行补测卡且 §3 虚假声称已覆盖。
2. **计划符合**：第一性原理计划 11 节齐全，最小可行结果达成，未执行生产操作。
3. **任务边界**：未越界——diff 仅 2 个文档追加 + 7 份新建 P3 文档；无业务代码/迁移/生产配置/敏感文件/历史结论改动；acceptance-report.md 未改。
4. **测试**：git diff --check 通过；路径/状态机/敏感信息等静态自检通过；但覆盖性自检**不通过**--NFR-E01~E03 补测卡缺失却被 §3 声称已覆盖（AD-04）。任务本身不要求 Maven/npm，Codex 未执行并如实记录。
5. **安全与真实性**：无敏感信息泄露，无安全缺口误标 PASS；但存在 AD-04 的虚假**覆盖声明**（非虚构生产指标），须返工修正。
6. **对抗式**：构造 15 类结构反例全部被反驳（路径存在/覆盖完整/状态合规/分类正确/无敏感/无虚构/diff 仅文档）；4 个独立子代理交叉验证另发现存活改进项 AD-01（P2 脚本误报通过风险）、AD-02（校验记录未留痕命令/输出/退出码）、AD-03（FR-505/606 证据粒度弱），均由 Claude Code 逐条证伪核实（见 §7.3）；另有 F-08（plan/task 尾随空格，系 Claude 文档缺陷）已由 Claude Code 修复；**发现 2 项存活 P1 阻断项 AD-04 + AD-05**（NFR-E01~E03 补测卡缺失 + §3 虚假声称；§9 多门禁补测卡系统性覆盖缺口，见 §7.3 第三/四子代理），按 CLAUDE.md §7.1 第 5 条结论为需返工；AD-01/02/03 为 P2/P3 改进项，AD-06 为 P2 改进；对“建议通过”本身的反驳尝试发现 AD-01 部分成立，已列为 F-06 改进条件，AD-02 列为 F-07。
7. **NFR 口径**：Codex 提请确认的 24 vs 27 口径已由 Claude Code 确认（24 唯一为正式口径，27 为原子细化，未新增/篡改编号阈值，处理诚实）。

**重要口径声明**：
- 本审查结论为“需返工”：P3 验收准备包文档结构基本合规（RLS-01~06/NFR-R01~05 覆盖、范围干净、无敏感信息），但存在 2 项存活 P1 阻断项（AD-04 NFR-E01~E03 补测卡缺失 + §3 虚假声称；AD-05 §9 多门禁补测卡系统性覆盖缺口），须返工修复后方可作为机构正式发布审批的输入。
- **需返工 ≠ 可上线**。即便返工通过，门禁矩阵总状态仍为 `NOT_READY / BLOCKED`，存在大量待生产等价环境实测、外部规范提供、机构角色确认的阻断项。正式上线批准须由有权机构角色在取得环境/授权/实测证据/审批流程后作出（requirements §16.4）。
- **本审查不授权任何 G-RLS/G-R 门禁转 PASS**。即使 AD-04 修复后，G-RLS-03/G-R05 仍须执行角色逐卡走查，G-RLS-05 仍须发布管理制度和有权角色确认；G-RLS-01/02、G-R02 也须在返工后重新独立审查，分别以其矩阵定义的最低证据判定。

---

## 12. 返工任务清单

本次存在 2 项 P1 阻塞性返工（F-10/AD-04 NFR-E01~E03 补测卡缺失、F-11/AD-05 §9 多门禁补测卡系统性覆盖缺口），须修复后方可提交。另有 P2/P3 改进/跟进项：

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-10 | NFR-E01~E03 在需求/计划/任务/补测卡的系统性遗漏 + §3 自检虚假声称（AD-04，P1 阻断） | **先由 Claude Code 修正上游文档**：①`docs/requirements.md` 的 RLS-03（§10.3）与 P3 验收标准 §15.3 显式补入 NFR-E01~E03；②`tasks/requirement-analysis.md` P3 附录同步补入 E01~E03，并将“27 条原 NFR”改为“24 个唯一 NFR 编号、27 条原子门禁”；③`tasks/claude-plan-P3-Release-01.md` 的输入/测试/验收映射补入 E01~E03并更正 24/27 口径；④修订 `tasks/codex-task-P3-Release-01.md` F-3/F-4/§6 覆盖清单，显式要求 E01~E03，并更正 24/27 口径。**再由 Codex 按修订任务补交付包**：在 `production-validation-plan.md` 新增 PV-ARCH 补测卡覆盖 NFR-E01（微服务/容器/弹性/水平扩展）、NFR-E02（SDK/新接口≤3工作日/80%可视化/开放API/插件）、NFR-E03（分布式/PB级/多引擎）；卡片须含前置/数据或演练样本/既有材料/步骤/采集归档/PASS-FAIL/停止/清理回退/角色状态（与其他 PV 卡同构）；同步修正 `release-gate-matrix.md` G-E01~E03 的下一步、`gap-and-dependency-register.md` RISK-V-12 的卡片引用、以及 `production-validation-plan.md` §3 line 135 的虚假覆盖声明。违反 RLS-03/NFR-R05/NFR-R02 | **高（P1 阻断，必修）** |
| F-11 | §9 门禁补测卡系统性覆盖缺口（AD-05，P1 阻断） | 补齐/补强以下补测卡：①G-PERF-01 在 PV-PERF 增日志聚合指标（写入吞吐/采集延迟/丢失率/积压/查询时延）与 PASS/FAIL；②G-DB-01 新增专用补测卡（空库+旧库升级+目标库设三方比对）；③G-PERSIST-01 在 PV-RESTORE 增“主链路写入前后应用重启+事务边界+重启后数据一致性”PASS/FAIL；④G-FUNC-01 新增逐 FR 生产等价验收卡（环境/输入/预期/PASS-FAIL/归档/签署）。同步修正矩阵对应门禁下一步与 §3 覆盖声明。违反 RLS-03/NFR-R05 | **高（P1 阻断，必修）** |
| F-12 | 准入规则 MAY_WAIVE+BLOCKED 漏洞（AD-06） | 在 `release-approval-checklist.md` §1 决策规则补：MAY_WAIVE+BLOCKED 条目须视为阻断，直至获批 WAIVED（字段完整）或关闭，不得默认放行 | 中（P2 改进） |
| F-13 | 发布前清单漏 E 系列独立检查（AD-07） | 在 `release-approval-checklist.md:36` "性能/可用性"行补 NFR-E01~E03（扩展性：弹性扩缩容/SDK 接入时效/PB 级多引擎）的独立发布前复核检查，不只靠门禁矩阵间接捕获 | 中（P2 改进） |
| F-14 | RISK-T-07 等安全相关技术债处置分类复核（AD-08） | 在 `gap-and-dependency-register.md` 复核 RISK-T-07（proof_hash，NFR-S02）、RISK-T-02（归档表索引，NFR-S02）、RISK-T-06（XSS，NFR-S03）等影响安全/审计防篡改的技术债处置：MAY_WAIVE 是否合理，或应升级为 BLOCK（须修复或严格豁免）；与 RISK-V-07（BLOCK）的处置矛盾统一 | 中（P2 改进） |
| F-01 | NFR 口径措辞不一致 | 将 `tasks/claude-plan-P3-Release-01.md` §2.3 与 `tasks/codex-task-P3-Release-01.md` §6.4 的"27 条"修正为"24 个唯一 NFR 编号，拆分为 27 条原子门禁" | 低（P3 提示） |
| F-02 | 补测卡走查 | 由性能/运维/安全/灾备负责人对 PV-PERF/STABLE/AVAIL/SEC/COMP/EXT/RESTORE/OPS 逐卡走查并签署 | 中（P2 改进，机构角色到位后） |
| F-03 | G-RLS-04 差距分类复核 | 安全/架构/接口负责人逐项确认差距台账分类 | 中（P2 改进） |
| F-04 | 提交时排除 .claude/worktrees/ | 提交前确认 `.gitignore` 含 `.claude/` 或手动排除该未跟踪目录 | 低（P3 提示，提交前） |
| F-05 | RQ-01~06 机构确认 | 由机构明确环境/授权/外部规范/发布流程/性能数据集/技术风险处置 | 中（P2 改进，机构驱动） |
| F-06 | 补测卡标注 P2 脚本误报风险（AD-01） | 在 `production-validation-plan.md` 的 PV-AVAIL/PV-STABLE/PV-RESTORE 卡片"既有材料"处标注：引用的 `db-failover.sh`/`redis-down.sh`/`kafka-outage.sh`/`dual-active-switch.sh`/`collect-metrics.sh`/`restore-db.sh` 存在误报通过风险（见 §7.3），执行前须先修复脚本（加严格断言/源-目标计数与哈希校验/失败硬退出）或由执行角色人工核验原始证据；并在 `gap-and-dependency-register.md` 新增 TECHNICAL_DEBT 条目（RISK-T-08）。仅改 P3 文档，不动 P2 脚本本身 | 中（P2 改进，补测执行前必修） |
| F-07 | 校验记录补命令/输出/退出码（AD-02） | 各文档“校验记录”小节补充实际执行的静态检查命令、输出摘要、退出码（git diff --check 退出码、路径核对、覆盖核对、敏感信息扫描命令及结果）；满足 codex-task §7.4 与 NFR-R01 | 中（P2 改进） |
| F-08 | plan/task 尾随空格（已修复） | Claude Code 已清理 `tasks/claude-plan-P3-Release-01.md` 与 `tasks/codex-task-P3-Release-01.md` 的 blockquote 行尾随空格；提交时确认 `git diff --cached --check` 通过 | 低（P3 提示，已修复） |
| F-09 | FR-505/606 证据补章节锚定（AD-03）；**FR-505 证据空引用强化**（AD-09/第九子代理候选1） | 在 `fr-evidence-index.md` 的 FR-505、FR-606 行将整份报告引用改为具体章节/段落锚定；**FR-505 特别**：`acceptance-report.md:19` 将 FR-501~505 合并描述，无"质量与性能反馈"可定位内容，须补具体证据或显式标"无开发证据/BLOCKED，待 PROD_EQ 反馈闭环原始证据" | 低（P3 提示，FR-505 空引用部分升 P2） |
| F-15 | FR-603 传输加密子项无代码证据标注（AD-09） | 在 `fr-evidence-index.md:60` FR-603 证据列显式标注"传输加密/TLS=无开发代码证据，待 PROD_EQ 配置核查（RISK-V-06）"，避免与 SM4/脱敏并列误读为三项均有开发覆盖 | 中（P2 改进） |
| F-16 | plan:98"六份"与"七份"矛盾（AD-10，已修复） | Claude Code 已修正 `tasks/claude-plan-P3-Release-01.md:98` 为"七份（含README）"，与 §4.4 文件表、codex-task:55、实际交付一致 | 低（P3 提示，已修复） |
| F-17 | fr-evidence-index 表头"环境"列字段语义错配（AD-11） | 在 `fr-evidence-index.md` 将表头"环境"列拆为"证据类"（Evidence class）+"环境"（Environment=DEV/PROD_EQ/PROD/N/A）两列，或修正表头与填值使其匹配 plan §4.2.1 枚举；满足 NFR-R01 与 §14 环境分层 | 中（P2 改进） |
| F-18 | 准入规则引用门禁矩阵不存在的"发布处置"字段（AD-12） | 在 `release-approval-checklist.md:8` 补说明"发布处置须交叉查 `gap-and-dependency-register.md`"，或在门禁矩阵补"发布处置"列；统一 MAY_WAIVE/NON_BLOCKING 与"阻断"语义 | 中（P2 改进） |
| F-19 | PV-PERF 前置未含 RISK-F-05（AD-13） | 在 `production-validation-plan.md` PV-PERF:18 前置补"RISK-F-05 connector 批量读取能力改造完成（FUNCTION_GAP 关闭）"，与 PV-SEC:63"功能缺口已实现"前置先例一致；满足 NFR-R05 前置完整性 | 中（P2 改进） |
| F-20 | 国产中间件兼容维度缺失（AD-14） | 在 NFR-C01/G-C01/PV-COMP 补"国产中间件"独立兼容维度（如东方通/金蝶应用服务器或消息中间件），满足原始 §3.5 | 中（P2 改进） |
| F-21 | §3.3 高危漏洞24h/定期检测报告未强制（AD-15） | 在 G-S03/PV-SEC PASS/FAIL 补"高危漏洞24h内修复"时限与"定期安全检测报告"交付物，满足原始 §3.3 | 低（P3 提示） |
| F-22 | SUNDB 可选标注缺失（AD-16） | 在 G-C01/PV-COMP 补"SUNDB 可选"，标可选不测或补测，统一三方口径 | 低（P3 提示） |
| F-23 | ISO27001 资质无跟踪条目（AD-17） | 在 `gap-and-dependency-register.md` 补 EXTERNAL_DEPENDENCY 条目"ISO27001 资质认证属供应商资质，需机构/供应商提供证书（非软件功能）" | 低（P3 提示） |
| F-24 | G-ROLLBACK-01 来源"发布要求"不可定位（AD-18） | 在 `release-gate-matrix.md:19` 将 G-ROLLBACK-01 来源"发布要求"改为具体文档章节（§5.3 运维服务或流程 §9），满足 NFR-R01 | 低（P3 提示） |
| F-25 | 稳定性监控 DB连接池指标恒 NA（AD-19） | 在 `delivery/stability-test-plan.md` 与 `perf/monitor/collect-metrics.sh` 将 BASE_URL 改指向有 DB 的服务（如 platform-partner/pipeline），或分服务采集 hikaricp 指标；确保 PV-STABLE 48h 能感知 DB 连接池耗尽 | 中（P2 改进） |
| F-26 | chaos 脚本/ops-manual 默认 deployment 不存在（AD-20） | 将 `delivery/chaos-drill/node-down.sh`/`dual-active-switch.sh`/`rolling-upgrade.sh` 与 `delivery/ops-manual.md` 的默认值 platform-a/platform-b 改为真实模块名（如 platform-partner/platform-pipeline），避免 kubectl NotFound | 中（P2 改进） |
| F-27 | 准入规则口径不一致（AD-21） | 在 `release-approval-checklist.md` §1 规则2 对齐 `release-gate-matrix.md` 行3"任一未豁免阻断 BLOCKED 均不准入"，或在矩阵补"发布处置"列并统一两文档口径；确保 MAY_WAIVE/NON_BLOCKING 但仍 BLOCKED 的项（RISK-T-01~T-07）被拦截 | 中（P2 改进） |
| F-28 | 回退决策链未列为独立不准入条件（AD-22） | 在 `release-approval-checklist.md` §1 补规则"回退决策人未指定或回退触发条件未由有权角色确认：不准入"，满足 NFR-A05 与 CLAUDE.md §7.1 回退路径拦截 | 中（P2 改进） |
| F-29 | G-RLS-05 自证循环（AD-23） | 在 `release-gate-matrix.md:61` 将 G-RLS-05 现有证据由 `release-approval-checklist.md` 改为 `CONTROLLED-LOCATION-TBD`（机构发布管理制度），明确 checklist 自身不得作为 G-RLS-05 PASS 证据，避免绕过 RISK-X-05 | 中（P2 改进） |

---

## 13. 建议提交信息（返工 F-10 通过后使用；当前结论为需返工，不应直接提交）

```text
docs(P3-Release-01): release-readiness evidence pack and P3 requirements appendix

- append P3 appendix to docs/requirements.md (RLS-01~06, NFR-R01~05)
- append P3 analysis to tasks/requirement-analysis.md (RQ-01~06, RA-01~04)
- add tasks/claude-plan-P3-Release-01.md (first-principles plan)
- add tasks/codex-task-P3-Release-01.md (codex task boundary)
- add delivery/release-readiness/: README, fr-evidence-index, release-gate-matrix,
  production-validation-plan, gap-and-dependency-register, release-approval-checklist,
  delivery-service-evidence
- 46 FR indexed, 24 unique NFR (27 atomic gates via P01/P06 split, no new IDs)
- all release gates BLOCKED; overall status NOT_READY (preparation pack, not approval)
- static checks: git diff --check clean, paths/coverage/status-enum/sensitive verified
- no business code/migration/prod-config/sensitive-file changes
- rework: add PV-ARCH validation card covering NFR-E01~E03 (fix AD-04 P1 blocker)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 14. 下一步（返工流程）

1. **Claude Code 先修正 F-10 上游需求/计划/任务文档（P1 阻断）**：在 requirements/analysis/plan/codex-task 的 RLS-03、验收/映射/覆盖清单补 NFR-E01~E03，并统一“24 个唯一 NFR / 27 条原子门禁”口径；据此更新 Codex 任务单。
2. **Codex 再执行修订任务单**：①新增 PV-ARCH（NFR-E01~E03）（F-10）；②补齐/补强 G-PERF-01 日志聚合、G-DB-01、G-PERSIST-01 重启、G-FUNC-01 逐 FR 补测卡（F-11）；③修正 matrix/gap/validation-plan 对应引用与 §3 line 135 虚假覆盖声明；④补强准入规则（F-12）、发布前清单 E（F-13）、安全技术债处置（F-14）、FR-603 TLS 标注（F-15）、FR-505 空引用（F-09）、证据元数据（R-10）、校验记录留痕（F-07）、fr-evidence-index 字段语义（F-17）、准入规则字段引用（F-18）、PV-PERF 前置补 RISK-F-05（F-19）、国产中间件维度（F-20）、24h/定期报告（F-21）、SUNDB 可选（F-22）、ISO27001 跟踪条目（F-23）、G-ROLLBACK-01 来源（F-24）；⑤在 P3 文档（gap 台账/补测卡）标注 AD-19/AD-20 风险（脚本/手册默认值与监控 BASE_URL 问题，属 P2 脚本/文档修复范围，须由后续独立任务处理，P3 仅标注）；⑥补强准入算法：F-27（口径对齐）、F-28（回退决策链独立不准入）、F-29（G-RLS-05 证据改受控位置）；返工后重新提交 Claude Code 审查。
2. **返工时可一并推进** F-06（P2 脚本误报风险标注）、F-07（校验记录留痕命令/输出/退出码）、F-09（FR-505/606 锚定）；F-08（尾随空格）已由 Claude Code 修复。
3. **返工通过后方可提交** P3 改动（建议提交信息见 §13；提交前排除 `.claude/worktrees/`，确认 `git diff --cached --check` 通过）。
4. **返工后不自动转 PASS**：G-RLS-03/G-R05 仍须执行角色逐卡走查；G-RLS-05 仍须发布管理制度和有权角色确认；其余 G-RLS/G-R 门禁也须以矩阵定义的最低证据重新独立审查判定。
5. 推进 F-02/F-03（补测卡走查 + 差距分类复核），待机构角色到位。
6. 推进 F-05（RQ-01~06 机构确认），逐项解锁生产等价环境实测与外部联调。
7. **不签发正式上线批准**。正式准入须在机构提供环境、授权、实测证据与审批流程后，由有权角色作出。

---

## 15. 复验记录（F-10 返工后，2026-07-15）

> 复验范围：Codex 按 `tasks/codex-task-P3-Release-01.md` §8.1 完成的 F-10 返工（新增 `PV-ARCH` 补测卡覆盖 NFR-E01~E03，并同步修正矩阵/台账/清单/校验记录与 24/27 口径）。复验目标为确认 F-10（AD-04）是否真正闭环，并核对 F-11 及其余返工项是否仍未执行。复验未执行任何生产/外部环境操作。

### 15.1 Git 状态（复验时点）

```text
git status --short
 M docs/requirements.md
 M tasks/requirement-analysis.md
?? .claude/worktrees/                      # 复验子代理 worktree，非 Codex 产物，提交须排除
?? delivery/release-readiness/             # 7 份 P3 文档（含 F-10 新增 PV-ARCH 卡）
?? reviews/claude-review-P3-Release-01.md  # 本审查文件
?? tasks/claude-plan-P3-Release-01.md
?? tasks/codex-task-P3-Release-01.md
```

```text
git diff --check  -> 退出码 0（仅 LF->CRLF 转换提示，不影响退出码）
git diff --name-only -> docs/requirements.md, tasks/requirement-analysis.md（均为纯追加 P3 附录，未改原 FR/NFR 基线）
git diff --stat  -> docs/requirements.md +138, tasks/requirement-analysis.md +100（纯追加）
```

- 已跟踪改动仍为 2 个文档文件，纯追加 P3 附录；**未改写原 FR/NFR 基线**。
- **`delivery/acceptance-report.md` 未被修改**（codex-task §3.2 默认不改，Codex 遵守；复验精确确认：`git status --short -- delivery/acceptance-report.md` 输出为空）。
- 无 `platform-*` 业务代码、`db/migration`、`k8s/prod`、`.env`、`.pem`/`.key`/`.crt` 改动（范围干净）。
- `.claude/worktrees/` 为复验子代理隔离工作树产物，**非 Codex 改动**，提交时须排除。

### 15.2 F-10（AD-04）返工逐项核验

| F-10 要求（codex-task §8.1） | 复验位置 | 结果 |
|---|---|---|
| 1. 新增独立 `PV-ARCH` 卡覆盖 G-E01~E03/NFR-E01~E03，E01=微服务/Docker/K8s/弹性/水平扩展无中断；E02=SDK/开放API/插件、新接口≤3工作日、80%可视化配置（含计时起止/样本分母/覆盖率算法/业务签署）；E03=分布式/PB级/关系型/NoSQL/时序/文件多引擎 | `production-validation-plan.md:71-84`（PV-ARCH 卡）；步骤/计时与 PASS/FAIL 按 E01/E02/E03 分项 | **通过**：E01/E02/E03 分项覆盖，E02 明确定义"分母=窗口内经业务签署满足统一准入条件的全部常规接入需求、分子=无需代码开发仅可视化配置完成并验收通过的需求、覆盖率=分子/分母×100%、排除项逐项签署不得事后删除"，口径严谨 |
| 2. PV-ARCH 与其他 PV 卡同构，含覆盖/前置/授权/窗口/数据/拓扑/版本/资源/既有材料/步骤/计时/采集归档/PASS-FAIL/停止条件/清理回退/执行复核角色/初始 BLOCKED | `production-validation-plan.md:72-84` | **通过**：11 个字段齐全，与 PV-PERF/STABLE/AVAIL/SEC/COMP/EXT/RESTORE/OPS 同构；状态初始 `BLOCKED` |
| 3. PV-ARCH 明确"既有材料仅作设计与执行输入，不替代 PROD_EQ 原始结果或经批准容量证据" | `production-validation-plan.md:78` | **通过**：防"设计文档替代实测"显式声明 |
| 4. `release-gate-matrix.md` G-E01~E03 下一步指向 PV-ARCH（不再悬空"PV-PERF/AVAIL/接入演练/容量验证"） | `release-gate-matrix.md:42-44` | **通过**：G-E01→"执行 PV-ARCH E01"；G-E02→"执行 PV-ARCH E02"；G-E03→"执行 PV-ARCH E03" |
| 5. `gap-and-dependency-register.md` RISK-V-12 下一步指向 PV-ARCH | `gap-and-dependency-register.md:19` | **通过**：RISK-V-12 下一步="执行 production-validation-plan.md 的 PV-ARCH E01~E03" |
| 6. `release-approval-checklist.md` 发布前补测复核显式含 E01~E03 | `release-approval-checklist.md:35` | **通过**："性能/可用性/扩展性"行="P/A/E/C/M/U/G 补测完成并复核；E01~E03 须逐项完成独立 PV-ARCH 卡并取得目标环境证据/业务签署" |
| 7. PV-ARCH 实际补齐后 §3 校验记录方可写"已覆盖 E01~E03"，否则标缺口 | `production-validation-plan.md:148` | **通过**：§3 现为"已覆盖 24 个唯一 NFR 编号，拆分为 27 条原子门禁；其中 NFR-E01~E03 由独立 PV-ARCH 卡分别覆盖..."--**因 PV-ARCH 卡实际存在，该声明真实，原 §3 line 135 虚假声称已消除** |
| 8. 七份文档 NFR 数量表述统一为"24 个唯一 NFR 编号，拆分为 27 条原子门禁" | 全 7 文档 | **通过**：`rg "24 个唯一 NFR"` 与 `rg "27 条原子门禁"` 均 7/7 命中；`rg "27 条" -v "原子门禁"` 为空，无孤立"27 条"误用；`rg "27 条原 NFR"` 为 0 |
| 9. 不修改 `docs/requirements.md`/`tasks/requirement-analysis.md`/`tasks/claude-plan-P3-Release-01.md`/本任务单（上游事实源，Claude Code 已改） | 上游 4 文件 | **通过**：上游 4 文件已由 Claude Code 按 F-10 上游修正范围更新（RLS-03 §10.3 含 E01~E03、§15.3 含 E01~E03、analysis §10~§17 含 E01~E03 与 24/27 口径）；Codex 未改这 4 文件 |

**F-10 结论：AD-04 P1 阻断项已闭环。** PV-ARCH 卡真实存在、同构、覆盖 NFR-E01~E03 且定义严谨；矩阵/台账/清单/校验记录交叉引用一致；24/27 口径全文档统一；§3 虚假覆盖声明消除。满足 codex-task §8.2 输出要求 1~3。

### 15.3 对抗式复验（F-10 反例追踪）

| 反例 | 验证方式 | 结果 |
|---|---|---|
| PV-ARCH 卡形同虚设（字段空/敷衍） | 逐字段读 `production-validation-plan.md:72-84` | **已反驳**：11 字段齐全，E01/E02/E03 步骤与 PASS/FAIL 分项，E02 覆盖率算法与排除项规则严谨 |
| E02"新接口≤3工作日/80%可视化"无计时起止/分母/算法 | 检查 E02 步骤与采集 | **已反驳**：明确"从完整需求与测试数据可用、计时启动获业务确认起计，到开发完成、自动化/人工验收通过并由业务验收人签署止"；分母/分子/排除项逐项签署不得删除 |
| E03"PB级容量"用设计文档替代实测 | 检查 E03 既有材料与 PASS/FAIL | **已反驳**：既有材料声明"仅作设计与执行输入，不替代 PROD_EQ 原始结果或经批准容量证据"；PASS/FAIL 要求"经批准、假设可追溯的 PB 级容量证据"，设计文档不能 PASS |
| G-E01~E03 下一步仍悬空或泛化 | 读矩阵行 42-44 | **已反驳**：三行均精确指向 PV-ARCH E01/E02/E03 |
| RISK-V-12 下一步仍"评审并执行相应卡片"（无指向） | 读台账行 19 | **已反驳**：明确指向 PV-ARCH E01~E03 |
| §3 校验记录仍虚假声称已覆盖 E01~E03（PV-ARCH 不存在时） | 读 §3 line 148 | **已反驳**：PV-ARCH 卡实际存在，§3 声明与事实一致，虚假声称已消除 |
| 24/27 口径仍有漂移（"27 条原 NFR"残留） | `rg "27 条原 NFR"` 全 7 文档 + 2 任务文件 | **已反驳**：0 命中 |
| F-10 越界改了上游 4 文件或 P2 脚本 | `git status --short` + `rg` 范围检查 | **已反驳**：上游 4 文件由 Claude Code 改；Codex 仅改 7 份 P3 文档；P2 脚本/perf/security 未动 |
| F-10 引入虚构执行结果/日期/审批 | 读 PV-ARCH 卡 | **已反驳**：状态初始 BLOCKED，版本/日期/执行者均 TBD，"本次未执行任何卡片" |

### 15.4 F-11 及其余返工项状态核验

| 编号 | 内容 | 复验状态 | 说明 |
|---|---|---|---|
| F-11 / AD-05（**P1 阻断**） | §9 多门禁补测卡系统性覆盖缺口：G-PERF-01 日志聚合、G-DB-01、G-PERSIST-01 重启维度、G-FUNC-01 逐 FR | **未执行** | 复验确认：`production-validation-plan.md` 仍仅 9 张 PV 卡（PV-PERF/STABLE/AVAIL/SEC/ARCH/COMP/EXT/RESTORE/OPS），**无 PV-DB 专用卡、无 PV-FUNC 逐 FR 卡**；PV-PERF 采集指标仍无"日志聚合"维度（`rg "日志聚合\|写入吞吐\|采集延迟"` 0 命中）；G-PERSIST-01 在 PV-RESTORE 仍为备份/恢复/审计链步骤，无"主链路写入前后应用重启+事务边界+重启后数据一致性"专门 PASS/FAIL；G-FUNC-01 下一步仍为自然语言"执行 46 FR 生产等价验收"，无逐 FR 环境/输入/预期/PASS-FAIL/归档/签署卡。**仍为存活 P1 阻断项** |
| F-12 / AD-06（P2） | 准入规则 MAY_WAIVE+BLOCKED 漏洞 | 未执行 | `release-approval-checklist.md:7` 规则 2 仍仅拦"发布处置为 BLOCK 的 BLOCKED"，RISK-T-01~T-07（MAY_WAIVE+BLOCKED）未捕获 |
| F-13 / AD-07（P2） | 发布前清单漏 E 独立检查 | **已由 F-10 部分覆盖** | `release-approval-checklist.md:35` 已含 E01~E03 独立 PV-ARCH 检查（F-10 顺带修复），但 F-13 原属 AD-07，现随 F-10 闭环 |
| F-14 / AD-08（P2） | RISK-T-07 等安全技术债处置分类复核 | 未执行 | `gap-and-dependency-register.md:53` RISK-T-07 仍 MAY_WAIVE，与 RISK-V-07（BLOCK）矛盾未统一 |
| F-15 / AD-09（P2） | FR-603 TLS 子项无代码证据标注 | 未执行 | `fr-evidence-index.md` FR-603 证据列未显式标注"传输加密=无开发证据" |
| F-16 / AD-10（P3，已修复） | plan:98 六/七份矛盾 | 已修复（首轮） | F-10 未涉及，首轮已修 |
| F-17 / AD-11（P2） | fr-evidence-index"环境"列字段语义错配 | 未执行 | 表头"环境"列仍填 Evidence class |
| F-18 / AD-12（P2） | 准入规则引用不存在的"发布处置"字段 | 未执行 | `release-approval-checklist.md:7` 仍引用矩阵无此字段 |
| F-19 / AD-13（P2） | PV-PERF 前置未含 RISK-F-05 | 未执行 | `production-validation-plan.md:17` 前置未含"connector 批量能力改造完成" |
| F-20 / AD-14（P2） | 国产中间件兼容维度缺失 | 未执行 | NFR-C01/G-C01/PV-COMP 未补国产中间件 |
| F-21~F-24 / AD-15~18（P3） | 24h/定期报告、SUNDB可选、ISO27001、G-ROLLBACK-01 来源 | 未执行 | - |
| F-25 / AD-19（P2，P2 脚本范围） | 稳定性监控 BASE_URL 指网关致 hikariCP NA | 未执行（属 P2 脚本，P3 仅标注） | P3 文档未标注该风险 |
| F-26 / AD-20（P2，P2 脚本范围） | chaos 脚本/ops-manual 默认 platform-a/b 不存在 | 未执行（属 P2 脚本/手册，P3 仅标注） | P3 文档未标注该风险 |
| F-27 / AD-21（P2） | 矩阵 vs checklist 准入口径不一致 | 未执行 | - |
| F-28 / AD-22（P2） | 回退决策链未列为独立不准入条件 | 未执行 | `release-approval-checklist.md` §1 未补该规则 |
| F-29 / AD-23（P2） | G-RLS-05 自证循环 | 未执行 | `release-gate-matrix.md:61` G-RLS-05 现有证据仍为 checklist 自身 |
| F-06 / AD-01（P2） | 补测卡标注 P2 脚本误报风险 | 未执行 | PV-AVAIL/STABLE/RESTORE"既有材料"未标注脚本误报倾向 |
| F-07 / AD-02（P2） | 校验记录补命令/输出/退出码 | 未执行 | 各文档校验记录仍为叙述，未留痕实际命令/输出/退出码 |
| F-09 / AD-03（P3，FR-505 部分 P2） | FR-505/606 证据补章节锚定 | 未执行 | - |

### 15.5 复验静态检查

| 检查项 | 命令 | 结果 |
|---|---|---|
| `git diff --check` | 直接执行 | 通过（退出码 0；仅 LF->CRLF 提示） |
| 七文档存在性 | `ls delivery/release-readiness/*.md` | 通过（7 份齐全） |
| PV-ARCH 卡存在 | `rg "^### PV-ARCH" production-validation-plan.md` | 通过（line 72） |
| PV-ARCH 覆盖 NFR-E01~E03 | `rg "NFR-E01~E03" production-validation-plan.md` | 通过（覆盖行 + §3 校验记录） |
| 24/27 口径统一 | `rg "24 个唯一 NFR"` + `rg "27 条原子门禁"` | 通过（7/7 双命中；无孤立"27 条原 NFR"） |
| F-11 未执行（无 PV-DB/PV-FUNC 卡） | `rg "^### PV-DB\|^### PV-FUNC"` | 通过（无此卡，确证 F-11 未执行） |
| F-11 未执行（PV-PERF 无日志聚合） | `rg "日志聚合\|写入吞吐\|采集延迟"` | 通过（0 命中，确证 F-11 未执行） |
| acceptance-report.md 未改 | `git status --short -- delivery/acceptance-report.md` | 通过（输出为空） |
| 范围越界 | `git status --short \| rg "platform-\|db/migration\|k8s/prod\|\.env\|\.pem\|\.key\|\.crt"` | 通过（无越界） |
| 业务代码/迁移/生产配置/密钥改动 | 同上 | 通过（范围干净） |
| Maven/npm/压测/DAST/演练/恢复/联调/发布 | 未运行（任务不要求） | N/A |

### 15.6 复验结论

```text
✗ 仍需返工 - F-10（AD-04 P1 阻断）已修复并通过复验；但 F-11（AD-05 P1 阻断：§9 多门禁补测卡系统性覆盖缺口）未执行，仍为存活 P1 阻断项；F-12~F-29（AD-06~AD-23，P2/P3 改进项）亦未处理。总体状态 NOT_READY / BLOCKED。
```

**理由**：

1. **F-10 闭环**：NFR-E01~E03 现由独立 PV-ARCH 卡真实覆盖（同构、定义严谨），矩阵/台账/清单/校验记录交叉引用一致，24/27 口径全文档统一，§3 虚假覆盖声明消除。AD-04 P1 阻断项**已消除**。
2. **F-11 仍存活**：§9 流程门禁的 G-PERF-01（日志聚合达标）、G-DB-01（空库/升级库迁移三方比对）、G-PERSIST-01（主链路数据重启后不丢的重启维度）、G-FUNC-01（46 FR 逐条生产等价验收）仍无对应可执行补测卡或步骤不全，违反 RLS-03/NFR-R05。复验确证 `production-validation-plan.md` 无 PV-DB、PV-FUNC 卡，PV-PERF 无日志聚合指标，PV-RESTORE 无重启维度。**AD-05 仍为存活 P1 阻断项**，按 CLAUDE.md §7.1 第 5 条结论仍为"需返工"。
3. **P2/P3 改进项未处理**：F-06~F-29（AD-01/02/03/06~23）均未执行；其中 F-12/F-14/F-15/F-17/F-18/F-19/F-20/F-27/F-28/F-29（P2）涉及准入规则漏洞、字段语义、前置完整性、国产中间件、自证循环等，须在 F-11 返工时一并修复；F-25/F-26 属 P2 脚本/手册范围，P3 仅标注风险。
4. **范围与真实性**：复验确认 diff 仍仅 2 文档追加 + 7 份 P3 文档（PV-ARCH 为 F-10 新增内容，写入既有 production-validation-plan.md 非新增第 8 文件）；无业务代码/迁移/生产配置/密钥/历史结论改动；acceptance-report.md 未改；无敏感信息；无虚构执行结果。诚实性达标。
5. **NFR 口径**：F-10 已将全文档统一为"24 个唯一 NFR 编号，拆分为 27 条原子门禁"，与首轮审查 §8 确认一致。
6. **不签发上线批准**：即便 F-11 及全部 P2 修复后，门禁矩阵总状态仍为 `NOT_READY / BLOCKED`，存在大量待生产等价环境实测、外部规范提供、机构角色确认的阻断项。本复验不授权任何 G-RLS/G-R 门禁转 PASS。

### 15.7 复验返工任务清单（F-10 后剩余）

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-11 | §9 多门禁补测卡系统性覆盖缺口（AD-05，P1 阻断，仍未执行） | ①PV-PERF 增日志聚合指标（写入吞吐/采集延迟/丢失率/积压/查询时延）与 PASS/FAIL；②新增 PV-DB 专用补测卡（空库+旧库升级+目标库设三方比对）；③PV-RESTORE 增"主链路写入前后应用重启+事务边界+重启后数据一致性"PASS/FAIL；④新增 PV-FUNC 逐 FR 生产等价验收卡（环境/输入/预期/PASS-FAIL/归档/签署）；同步修正矩阵对应门禁下一步与 §3 覆盖声明 | **高（P1 阻断，必修）** |
| F-12/F-27/F-28/F-18 | 准入规则系列漏洞（AD-06/21/22/12，P2） | checklist §1 规则补：MAY_WAIVE+BLOCKED 视为阻断至获批 WAIVED；回退决策人未指定/触发条件未确认不准入；交叉查台账说明"发布处置"；对齐矩阵行 3 | 中（P2） |
| F-29 | G-RLS-05 自证循环（AD-23，P2） | matrix:61 G-RLS-05 现有证据改为 `CONTROLLED-LOCATION-TBD`（机构发布管理制度），不得以 checklist 自身为证 | 中（P2） |
| F-14 | RISK-T-07 等安全技术债处置分类复核（AD-08，P2） | gap 台账复核 RISK-T-07/T-02/T-06 处置，与 RISK-V-07（BLOCK）矛盾统一 | 中（P2） |
| F-15 | FR-603 TLS 子项无代码证据标注（AD-09，P2） | fr-evidence-index FR-603 证据列显式标注"传输加密=无开发证据，待 PROD_EQ 核查" | 中（P2） |
| F-17 | fr-evidence-index"环境"列字段语义错配（AD-11，P2） | 拆为"证据类"+"环境"两列或修正表头/填值 | 中（P2） |
| F-19 | PV-PERF 前置未含 RISK-F-05（AD-13，P2） | PV-PERF:17 前置补"RISK-F-05 connector 批量能力改造完成" | 中（P2） |
| F-20 | 国产中间件兼容维度缺失（AD-14，P2） | NFR-C01/G-C01/PV-COMP 补国产中间件独立维度 | 中（P2） |
| F-06 | 补测卡标注 P2 脚本误报风险（AD-01，P2） | PV-AVAIL/STABLE/RESTORE"既有材料"标注脚本误报倾向；gap 台账新增 RISK-T-08 | 中（P2，补测执行前必修） |
| F-07 | 校验记录补命令/输出/退出码（AD-02，P2） | 各文档校验记录补实际命令+输出摘要+退出码 | 中（P2） |
| F-25/F-26 | 稳定性监控/chaos 脚本默认值问题（AD-19/20，P2，P2 脚本范围） | P3 文档标注风险；脚本/手册本身由后续独立任务修复 | 中（P2，P3 仅标注） |
| F-09/F-21~F-24 | FR-505/606 锚定、24h/定期报告、SUNDB可选、ISO27001、G-ROLLBACK-01 来源（P3） | 见 §12 原表 | 低（P3） |

### 15.8 下一步

1. **派发 F-11（P1 阻断）**：在 `tasks/codex-task-P3-Release-01.md` 末尾追加 §8.3 F-11 返工任务单，要求 Codex 补 PV-DB 专用卡、PV-FUNC 逐 FR 卡、PV-PERF 日志聚合指标、PV-RESTORE 重启维度步骤，并同步矩阵门禁下一步与 §3 覆盖声明。
2. **F-11 返工时一并推进 P2 改进项**：F-12/F-14/F-15/F-17/F-18/F-19/F-20/F-27/F-28/F-29（准入规则系列、字段语义、前置完整性、国产中间件、自证循环）可在同一轮返工修复；F-06/F-07/F-09/F-21~F-24（P2/P3）顺带处理；F-25/F-26 属 P2 脚本/手册，P3 仅标注风险，脚本修复由后续独立任务承担。
3. **F-11 通过复验后方可提交** P3 改动（提交前排除 `.claude/worktrees/`，确认 `git diff --cached --check` 通过）。
4. **返工后不自动转 PASS**：G-RLS-03/G-R05 仍须执行角色逐卡走查；G-RLS-05 仍须发布管理制度和有权角色确认；其余 G-RLS/G-R 门禁须以矩阵定义的最低证据重新独立审查判定。
5. **不签发正式上线批准**。正式准入须在机构提供环境、授权、实测证据与审批流程后，由有权角色作出。

---

## 16. F-11 复验记录（F-11 返工后，2026-07-15）

> 复验范围：Codex 按 `tasks/codex-task-P3-Release-01.md` §8.3 完成的 F-11 返工（PV-PERF 补日志聚合、新增 PV-DB 三方比对卡、PV-RESTORE 补重启维度、新增 PV-FUNC 46 FR 卡，同步矩阵/台账/§3）。复验目标：确认 F-11（AD-05 P1）是否闭环。复验含 Claude Code 直接逐项追踪 + 独立对抗式子代理第二意见证伪。未执行任何生产/外部操作。

### 16.1 F-11 返工逐项核验

| F-11 子项（codex-task §8.3） | 复验位置 | 结果 |
|---|---|---|
| 1. PV-PERF 补日志聚合（G-PERF-01） | `production-validation-plan.md:49-50` | 通过：采集/归档增日志五项指标（写入吞吐/采集延迟/丢失率/积压/查询时延）；PASS/FAIL 增日志聚合判定；阈值未批准则 BLOCKED（诚实）；未弱化既有 P95/P99/TPS/批量/缓存/查询阈值 |
| 2. 新增 PV-DB 卡（G-DB-01） | `:144-157` | 通过：三方比对（①空库②旧库升级③目标库设定义），9 维（schema/表/列/类型/默认值/主外键/唯一约束/索引/分区）；明确"单一路径或 DEV Testcontainers 不得使门禁 PASS"；11 字段同构；初始 `BLOCKED` |
| 3. PV-RESTORE 补重启维度（G-PERSIST-01） | `:158-171` | 通过：重启维度独立于备份恢复；事务边界样本（已提交/未提交/回滚）+ T-R0/T-R1 基线；明确"不得以备份恢复或 DEV 重启替代" |
| 4. 新增 PV-FUNC 卡（G-FUNC-01） | `:12-39` | 通过：九模块分组覆盖 46 FR（4+5+5+5+5+6+5+5+6=46），每组含 PROD_EQ 输入/预期/PASS-FAIL/归档/签署；FR-705/803 按 `EXTERNAL_DEPENDENCY` 不得 PASS |
| 5. 同步矩阵/台账/§3 | matrix:9-14；gap:19/48/49；plan §3:190 | 通过：G-FUNC->PV-FUNC、G-DB->PV-DB 三方比对、G-PERSIST->PV-RESTORE 重启维度、G-PERF->PV-PERF 含5指标；RISK-V-11->PV-RESTORE 重启、RISK-T-01/T-02->PV-DB；§3 如实反映无虚假声明 |
| 6. 不改上游4文件/脚本/acceptance-report | git status | 通过：上游4文件未改（requirements/analysis 仅 Claude 追加附录；plan/codex-task 未被 Codex 篡改，§8.3/§8.4 完整）；acceptance-report 未改；db/migration/perf/security/chaos-drill/backup-restore 脚本未改 |
| 7. 交付包仍7份 | `ls` | 通过：PV-DB/PV-FUNC 为 `production-validation-plan.md` 内卡片标题，非新增文件 |

### 16.2 对抗式证伪（Claude Code 直接 + 独立子代理第二意见）

| 反例 | 验证 | 结果 |
|---|---|---|
| PV-FUNC 漏/重 FR，缺字段，FR-705/803 误 PASS | 逐模块数 46；分组表 5 列齐；卡/矩阵/索引三处均 EXTERNAL_DEPENDENCY/BLOCKED | 已反驳 |
| PV-DB 三方比对不可执行/降级/缺维度 | 三方在步骤；9 维覆盖；防降级声明在 | 已反驳 |
| PV-RESTORE 重启维度未独立/缺事务边界/无基线 | "独立于备份恢复"；三类事务边界样本；T-R0/R1 | 已反驳 |
| PV-PERF 日志聚合未入采集/PASS-FAIL 或弱化阈值 | 5 指标在采集与 PASS/FAIL；"任一既有 NFR 阈值不满足即 FAIL"未弱化 | 已反驳 |
| §3 虚假覆盖声明 | 4 声明逐条比对卡内容均属实；明示未执行 | 已反驳 |
| 交叉引用错指 | 矩阵4门禁+台账RISK项均指向正确PV卡 | 已反驳 |
| 卡片不同构 | PV-DB/PV-FUNC 11字段齐，初始 BLOCKED | 已反驳 |
| 越界改上游/脚本/历史结论 | diff 仅2文档追加；脚本/db/migration未改；acceptance-report未改 | 已反驳 |
| 虚构结果/敏感信息 | 全 BLOCKED；日期/执行者 TBD；敏感扫描0 | 已反驳 |

**独立对抗式子代理第二意见结论**：F-11 已闭环，未发现存活 P1/P2 阻断项；仅 4 条 P3 表述精度提示（PV-DB PASS/FAIL 9维可显式列出、旧库样本代表性最低构成可补、台账无 G-FUNC-01 专用 RISK-V 项属既有结构、PV-RESTORE 应用重启未区分滚动/整体），均非阻断，与 Claude Code 直接核验一致。

### 16.3 范围与真实性

- diff 仍仅 `docs/requirements.md`(+138)、`tasks/requirement-analysis.md`(+100) 追加 P3 附录；`delivery/release-readiness/` 7 份文档（F-11 改动写入既有 `production-validation-plan.md`/`release-gate-matrix.md`/`gap-and-dependency-register.md`，未新增文件）。
- `delivery/acceptance-report.md` 未改；P0-P2 历史/脚本未改；无敏感信息；无虚构结果；`git diff --check` 退出码 0。
- 24/27 口径 7/7 一致；`fr-evidence-index.md` 仍 46 FR 未被破坏；`release-approval-checklist.md` E01~E03 仍在。
- 11 张 PV 卡齐全（原9 + PV-ARCH + PV-DB + PV-FUNC），均初始 `BLOCKED`。

### 16.4 静态检查

| 检查 | 结果 |
|---|---|
| `git diff --check` | 通过（exit 0） |
| 11 张 PV 卡存在 | 通过 |
| PV-FUNC 46 FR 九模块分组 | 通过（4+5+5+5+5+6+5+5+6=46） |
| 矩阵4门禁下一步指向正确PV卡 | 通过 |
| 24/27 口径7/7 | 通过 |
| 敏感信息 | 0 命中 |
| 范围越界 | 无 |
| 上游4文件未改 | 通过 |
| Maven/npm/压测/DAST/演练/恢复/联调/发布 | 未运行（任务不要求），如实记录 |

### 16.5 复审结论

```text
✓ P3 文档任务复审通过（F-10/AD-04 + F-11/AD-05 两项 P1 阻断均已消除；含独立对抗式子代理第二意见证伪，未发现存活 P1/P2 阻断项）
- 总体状态仍 NOT_READY / BLOCKED（所有生产级门禁仍 BLOCKED，未执行任何生产验证）
- 附改进条件：F-12~F-29（P2/P3）为跟进项，不阻断 P3 文档交付，但准入算法类（F-12/F-27/F-28/F-29）须在补测执行前/正式发布决策前修复
- 不签发正式上线批准
```

**理由**：

1. **需求覆盖**：RLS-01~06/NFR-R01~05 覆盖达标；§9 全部门禁（含 G-PERF-01 日志聚合/G-DB-01/G-PERSIST-01 重启/G-FUNC-01 46 FR）现均有可执行补测卡，F-11 P1 阻断消除。
2. **任务边界**：未越界；diff 仅文档追加；上游4文件/脚本/`acceptance-report.md`/P0-P2 历史结论未改。
3. **测试**：`git diff --check` 通过；静态自检全通过；任务不要求 Maven/npm，Codex 未执行并如实记录。
4. **安全与真实性**：无敏感信息；无虚构；所有门禁 `BLOCKED`；§3 无虚假覆盖声明；防降级声明（单一路径/DEV/Testcontainers/备份恢复/DEV 重启不得 PASS）齐备。
5. **对抗式**：10 类反例全部反驳；独立子代理第二意见确认无存活 P1/P2。已尝试反驳"通过"本身，未发现存活阻断项。

**重要口径声明**：
- **P3 文档任务通过 ≠ 可上线**。门禁矩阵总状态仍 `NOT_READY / BLOCKED`，存在大量待生产等价环境实测、外部规范提供、机构角色确认的阻断项。正式上线批准须由有权机构角色在取得环境/授权/实测证据/审批流程后作出（requirements §16.4）。
- **本复审不授权任何 G-RLS/G-R 门禁转 PASS**。G-RLS-03/G-R05 仍须执行角色逐卡走查；G-RLS-05 仍须发布管理制度和有权角色确认；其余 G-RLS/G-R 门禁须以矩阵定义的最低证据重新独立审查判定。

### 16.6 剩余 P2/P3 跟进项（非阻断，不阻塞 P3 文档交付）

| 类别 | 编号 | 处置建议 |
|---|---|---|
| 准入算法（补测执行前必修） | F-12/F-27/F-28/F-29 | MAY_WAIVE+BLOCKED 拦截、矩阵与清单口径对齐、回退决策链独立不准入、G-RLS-05 自证循环改受控位置 |
| 补测卡精度 | F-06/F-07/F-19 | 脚本误报风险标注、校验记录留痕命令/输出/退出码、PV-PERF 前置补 RISK-F-05 |
| 字段/证据 | F-09/F-14/F-15/F-17 | FR-505/606 锚定、RISK-T-07 处置分类、FR-603 TLS 标注、fr-evidence-index 环境列语义 |
| 兼容/合规 | F-20/F-21~F-24 | 国产中间件、24h/定期报告、SUNDB 可选、ISO27001 跟踪、G-ROLLBACK-01 来源 |
| P2 脚本范围 | F-25/F-26 | 稳定性监控 BASE_URL、chaos 脚本默认值（P3 仅标注风险，脚本由后续独立任务修复） |

### 16.7 下一步

1. **可建议提交** P3 改动（F-10/F-11 双 P1 闭环后）。提交前排除 `.claude/worktrees/`，确认 `git diff --cached --check` 通过。
2. **不自动转 PASS**：G-RLS-03/G-R05 仍须执行角色逐卡走查；G-RLS-05 仍须发布管理制度和有权角色确认；其余 G-RLS/G-R 门禁须以矩阵定义的最低证据重新独立审查判定。
3. **推进 P2 跟进项**：建议在补测执行前处理准入算法类（F-12/F-27/F-28/F-29），可由 Claude Code 派发新一轮 Codex 任务或由机构角色处理；其余 P2/P3 视优先级跟进。
4. **不签发正式上线批准**。正式准入须在机构提供环境、授权、实测证据与审批流程后，由有权角色作出。
