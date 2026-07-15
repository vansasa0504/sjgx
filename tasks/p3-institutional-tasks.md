# P3/Release-01 机构角色任务清单（非 Codex）

> 本清单按 CLAUDE.md 协作模式属机构角色/组织性任务，**Codex 不得执行**（不能替代签署、提供外部环境或机构决策）。供项目负责人派发至相应机构角色。
> 来源：`reviews/claude-review-P3-Release-01.md` §8.7 / §17.7
> 日期：2026-07-15
> **口径**：完成下列任务不等于平台可上线。正式准入须由有权机构角色在取得环境/授权/实测证据/审批流程后作出。

## 背景

P3/Release-01 上线验收准备包已交付 master（PR #1，commit `7d634477`）。F-10/F-11 双 P1 阻断已闭环；§8.5 P2/P3 跟进项文档修复已通过复审并合并（PR #2，commit `401c95a9`）。以下为需机构角色推进的任务。

## 任务清单

### F-02 补测卡走查与签署
- **执行角色**：性能 / 运维 / 安全 / 灾备负责人
- **任务**：对 11 张 PV 卡（PV-PERF/STABLE/AVAIL/SEC/ARCH/COMP/EXT/DB/RESTORE/FUNC/OPS）逐卡走查，确认前置/步骤/指标/PASS-FAIL/停止/回退/归档的可执行性，并签署
- **交付物**：逐卡走查签署记录（受控位置 `CONTROLLED-LOCATION-TBD`）
- **验收**：满足 G-RLS-03 / G-R05 最低证据
- **阻断**：未走查前 G-RLS-03 / G-R05 保持 `BLOCKED`

### F-03 差距分类复核
- **执行角色**：安全 / 架构 / 接口负责人
- **任务**：逐项确认 `delivery/release-readiness/gap-and-dependency-register.md` 分类（VERIFY_PENDING / FUNCTION_GAP / EXTERNAL_DEPENDENCY / TECHNICAL_DEBT）
- **交付物**：分类复核签署记录
- **验收**：满足 G-RLS-04
- **阻断**：未复核前 G-RLS-04 保持 `BLOCKED`

### F-05 RQ-01~06 机构确认
- **执行角色**：机构发布 / 安全 / 合规负责人
- **任务**：
  - **RQ-01** 生产等价环境（K8s / 达梦 / OceanBase / Redis / MQ / MinIO / 同城双活资源及变更窗口）
  - **RQ-02** 功能缺口上线策略（MFA / IAM/SSO / 字段 ABAC / 证书认证：阻断或书面豁免）
  - **RQ-03** 性能数据集 / SLO 统计窗口与第三方报告认可规则
  - **RQ-04** 等保三级 / ISO27001 测评机构、范围、材料模板
  - **RQ-05** 发布审批 / 豁免 / 回退的组织角色与签名流程
  - **RQ-06** P2-01 达梦分区维护等价实现与归档表索引等技术风险处置
- **交付物**：逐项机构确认材料（环境 / 授权 / 外部规范 / 发布制度 / 数据集 / 风险决策）
- **验收**：逐项解锁生产等价环境实测与外部联调
- **阻断**：未确认前相关门禁保持 `BLOCKED`

### F-25 / F-26 P2 脚本修复（需独立任务）
- **执行角色**：DBA / 运维 / 前端（独立任务，解除 `tasks/codex-task-P3-Release-01.md` §4 禁改后派发）
- **任务**：
  - **F-25**：`delivery/stability-test-plan.md` / `perf/monitor/collect-metrics.sh` BASE_URL 改指向有 DB 的服务（如 platform-partner / platform-pipeline），或分服务采集 hikaricp 指标
  - **F-26**：`delivery/chaos-drill/node-down.sh` / `dual-active-switch.sh` / `rolling-upgrade.sh` / `delivery/ops-manual.md` 默认 deployment `platform-a` / `platform-b` 改为真实模块名
- **交付物**：脚本修复 + 重跑相关补测卡验证
- **阻断**：P3 文档已由 F-06 标注风险；脚本未修复前相关 PV 卡执行可能误报通过

## 联系方式

真实姓名 / 电话 / 账号仅保存在 `CONTROLLED-LOCATION-TBD`，不入仓库。

---

**汇总**：F-02 / F-03 / F-05 为机构角色签署与确认任务（解锁 G-RLS-03 / G-RLS-04 / G-RLS-05 及生产等价实测）；F-25 / F-26 为 P2 脚本修复（独立任务）。Codex 可执行的 §8.5 文档修复已完成并合并 master（PR #2）。
