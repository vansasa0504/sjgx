# Codex 代码修复任务清单（Backlog）

> 来源：`delivery/release-readiness/gap-and-dependency-register.md`（FUNCTION_GAP RISK-F-01~06 + TECHNICAL_DEBT RISK-T-01~08）+ `reviews/claude-review-P3-Release-01.md` §7.3/§8.7（F-25/F-26）
> 性质：需 Codex 实现的代码/脚本修复任务总览。本文件为 backlog，**正式派发时按批次生成 `tasks/codex-task-XX.md` + 独立分支 + Claude Code 审查**。
> 日期：2026-07-15
> 口径：完成代码修复 ≠ 可上线；须配合 PV 补测卡验证（`production-validation-plan.md`）与机构角色任务（`tasks/p3-institutional-tasks.md`）。总体仍 `NOT_READY / BLOCKED`。

---

## A. 功能缺口实现（FUNCTION_GAP，均 BLOCK）

| 批次 | RISK | 影响 FR/NFR | 缺口 | 修复范围（模块） | 前置 | 优先级 |
|---|---|---|---|---|---|---|
| A1 | RISK-F-01 | NFR-S01 | MFA 未实现 | `platform-auth` | 无 | P0 |
| A2 | RISK-F-03 | FR-304；NFR-S01 | 字段级/资源级 ABAC 未实现（IDOR 测试仅 RBAC） | `platform-auth` + `platform-pipeline` | 无 | P0 |
| A3 | RISK-F-04 | NFR-S01 | 数字证书认证未实现 | `platform-auth` | 无 | P0 |
| A4 | RISK-F-02 | NFR-S01/C02 | IAM/SSO 未实现/未联调 | `platform-auth`（适配器框架） | 机构提供 IAM/SSO 规范后真实联调 | P0（适配器可先实现） |
| A5 | RISK-F-05 | FR-205；NFR-P03/P04 | connector 批量读取能力未改造（batch/offset） | `platform-pipeline.ingest` | 无 | P1 |
| A6 | RISK-F-06 | FR-606；NFR-G01 | 销毁证明未接入生产生命周期调用链 | `platform-pipeline.storage.lifecycle` | 与 B2（proof_hash）联合 | P0 |

## B. 技术债代码修复（TECHNICAL_DEBT）

| 批次 | RISK | 影响 FR/NFR | 缺口 | 处置 | 修复范围 | 优先级 |
|---|---|---|---|---|---|---|
| B1 | RISK-T-02 | FR-305/804；NFR-S02 | 归档表无 id 单列唯一索引，影响审计防篡改 | BLOCK | `db/migration` + 归档表代码 | P0 |
| B2 | RISK-T-07 | FR-606；NFR-S02/G01 | proof_hash 约束、多实例 ID、事务/verify 时机遗留 | BLOCK | `platform-pipeline.storage` + 审计链 | P0（与 A6/B1 联合） |
| B3 | RISK-T-06 | NFR-S03/U01 | XSS 不改写 JSON body；Vite 构建警告 | BLOCK | `platform-ui` 输出编码 + 后端 | P0 |
| B4 | RISK-T-01 | NFR-C01/M01 | 分区维护依赖 MySQL INFORMATION_SCHEMA，达梦等价实现缺失 | MAY_WAIVE | 维护脚本/代码（达梦方言） | P1 |
| B5 | RISK-T-03 | FR-704/801；NFR-P07 | 账单/统计 `findAllByRange` 全量加载内存压力 | MAY_WAIVE | `platform-billing`（流式聚合/分页） | P1 |
| B6 | RISK-T-05 | FR-503；NFR-A02 | Redis 降级为 JVM 本地，多实例配额精度下降 | MAY_WAIVE | `platform-partner`（配额 fallback） | P1 |
| B7 | RISK-T-04 | NFR-P03/P07 | 数据生成器 MAX(id)+1 不可并行，raw_data 不跨月 | NON_BLOCKING | `perf` 数据生成工具 | P2 |

## C. 脚本修复（需解除 `tasks/codex-task-P3-Release-01.md` §4 禁改后派发）

> 来源审查报告 §7.3 AD-01（脚本误报通过风险）/ AD-19（监控 NA）/ AD-20（默认模块名）。脚本当前 `exit 0` 不得单独作为 PASS 证据。

| 批次 | RISK/项 | 脚本/文件 | 缺陷 | 修复 | 优先级 |
|---|---|---|---|---|---|
| C1 | RISK-T-08 | `delivery/chaos-drill/db-failover.sh` | `kubectl wait pod -l standby --for=Ready` 对已 Ready standby 立即返回 0；无实际主备提升/切流；打印 RTO | 加严格断言/失败硬退出（去 `\|\| true`）；真实提升/切流命令 | P0（PV-AVAIL 前必修） |
| C2 | RISK-T-08 | `delivery/chaos-drill/redis-down.sh` | `curl -fsS $HEALTH_URL \|\| true` 吞健康检查失败；回退日志 grep `\|\| true` | 失败硬退出；校验回退日志出现 | P0 |
| C3 | RISK-T-08 | `delivery/chaos-drill/kafka-outage.sh` | 回退 grep `\|\| true`；`kubectl scale \|\| true` 吞扩容失败 | 失败硬退出；校验回退与扩容成功 | P0 |
| C4 | RISK-T-08 | `delivery/chaos-drill/dual-active-switch.sh` | scale primary 0 后对在线 secondary 求状态立即返回；无流量切换/RPO 比对/数据一致性校验 | 真实切流；RPO 标记比对；数据一致性校验 | P0 |
| C5 | RISK-T-08 | `perf/monitor/collect-metrics.sh` | sed 正则要求 value 后跟 `}]`，多 measurement 指标（`jvm.gc.pause`/`http.server.requests`）返回 `},{...}` 失配写 `NA` | 修复多 measurement 采集；确保 GC/HTTP 指标非 NA | P0（PV-STABLE 前必修） |
| C6 | RISK-T-08 | `delivery/backup-restore/restore-db.sh` | `mysql < backup` 加载 0 行也 exit 0；逐表 `SELECT COUNT(*)` 只打印不比对；无源/目标哈希 | 源/目标计数与哈希一致性校验；空/部分恢复硬失败 | P0（PV-RESTORE 前必修） |
| C7 | F-26 | `delivery/chaos-drill/node-down.sh`、`dual-active-switch.sh`、`rolling-upgrade.sh`、`delivery/ops-manual.md` | 默认 deployment `platform-a`/`platform-b` 不存在（实际模块为 gateway/auth/partner/pipeline/quality/billing/common/ui） | 改真实模块名；避免 `kubectl NotFound` | P1 |
| C8 | F-25 | `delivery/stability-test-plan.md`、`perf/monitor/collect-metrics.sh` | BASE_URL 指向网关，网关无 HikariCP，DB 连接池指标恒 NA | BASE_URL 改指向有 DB 的服务（`platform-partner`/`platform-pipeline`）或分服务采集 | P1 |

---

## 优先级汇总

- **P0（上线阻断 BLOCK，须补测前/上线前修复）**：A1 MFA、A2 ABAC、A3 证书认证、A4 IAM/SSO 适配器、A6 销毁调用链、B1 归档索引、B2 proof_hash、B3 XSS、C1~C6 脚本误报修复
- **P1（重要，性能/可用性/兼容）**：A5 connector 批量、B4 达梦分区、B5 聚合优化、B6 Redis fallback、C7 默认模块名、C8 监控 BASE_URL
- **P2（低优先）**：B7 数据生成器并行化

## 派发建议（按依赖与模块分批）

每批独立 `tasks/codex-task-XX.md` + 独立分支 `ai/fix-XX` + Claude Code 对抗式审查：

| 批次 | 内容 | 模块 | 依赖 |
|---|---|---|---|
| 1 | A1 MFA + A2 ABAC + A3 证书认证 + A4 IAM/SSO 适配器 | `platform-auth`（集中） | A4 真实联调待机构规范 |
| 2 | A6 销毁调用链 + B1 归档索引 + B2 proof_hash | `platform-pipeline.storage` + `db/migration` + 审计 | 三项联合关闭 |
| 3 | C1~C6 脚本误报修复 + C7 默认模块名 + C8 监控 BASE_URL | `delivery/chaos-drill` + `perf/monitor` + `delivery/backup-restore` | 需新任务单显式解除原 §4 禁改 |
| 4 | A5 connector 批量 + B5 聚合优化 + B4 达梦分区 | `platform-pipeline.ingest` + `platform-billing` + 维护脚本 | A5 后方可 PV-PERF |
| 5 | B3 XSS 输出编码 | `platform-ui` + 后端 | 可与批次 1 并行 |
| 6 | B6 Redis fallback | `platform-partner` | PV-AVAIL 后决策是否实现 |
| 7 | B7 数据生成器并行化 | `perf` 工具 | 低优先 |

## 约束（沿用 CLAUDE.md / AGENTS.md）

- 不修改 `.env`、密钥、证书、`k8s/prod`、生产配置
- 最小改动，不进行无关重构，不引入大型依赖
- 必须补充或更新测试，完成后运行测试
- **批次 3 脚本修复需在新任务单显式解除原 §4 禁改范围**（原 §4 禁止改 `perf/`/`security/`/`delivery/chaos-drill/`/`delivery/backup-restore/` 脚本）
- 完成后输出修改文件、测试命令、测试结果、潜在风险
- 不签发正式上线批准

## 与机构角色任务的关系

- **A4 IAM/SSO**：适配器框架 Codex 可实现，真实联调待机构提供规范（F-05/RQ-01）
- **批次 3 脚本修复**：修复后须由机构角色（F-02 走查）确认 PV 卡可执行
- **所有代码修复**：完成后仍须 PV 补测卡（PROD_EQ）验证 + 机构角色签署，方可解锁门禁
- **不替代机构角色任务**（F-02 走查 / F-03 复核 / F-05 机构确认）

---

> 本清单为 backlog 总览。正式派发时，Claude Code 按批次生成具体 `tasks/codex-task-XX.md`，Codex 执行后由 Claude Code 审查 git diff + 测试结果，通过后合并 master。
