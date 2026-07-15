# P3 / Release-01 上线验收准备包

> **不是正式上线批准。当前总状态：`NOT_READY / BLOCKED`。** 只有全部阻断门禁关闭，或取得字段完整且经有权角色批准的书面豁免后，才可提交机构正式发布审批。

## 1. 基线、范围与非范围

- P0-P2 基线 commit：`d8d9f4af`（本次通过 `git log -1 --oneline` 核实）。
- 工作分支：`ai/p3-release-readiness`。
- 范围：整理 46 条 FR 证据、上线门禁、生产等价补测、差距与依赖、准入/豁免、交付和服务证据。
- 非范围：业务功能、测试、迁移、脚本或生产配置修改；压测、DAST/SCA、故障注入、恢复、外部联调和发布执行；正式审批。

## 2. 状态与证据规则

状态只能使用：

- `PASS`：最低证据的环境、范围、原始输出和复核要求全部匹配。
- `FAIL`：已在目标环境执行，且阈值、数据校验或必备控制不满足。
- `BLOCKED`：功能、环境、授权、外部规范、原始证据或审批缺失。
- `WAIVED`：已有完整书面材料，至少含风险、影响范围、批准角色、受控材料编号、到期日、补齐任务、监控措施和回退触发条件。

证据分层：

| 层级 | 含义 | 可单独使生产门禁 PASS |
|---|---|---|
| `DEV_TEST` | 开发单测、Mock、Testcontainers、脚本静态检查 | 否 |
| `DEV_REVIEW` | 代码审查、开发阶段报告、实现核对 | 否 |
| `PROD_EQ_TEST` | 获授权的生产等价环境原始测试结果 | 仅当目标门禁明确接受且复核通过 |
| `PROD_RECORD` | 生产变更、运行、监控或演练受控记录 | 仅当门禁要求匹配且复核通过 |
| `THIRD_PARTY` | 第三方性能、安全、合规或认证材料 | 仅当范围、版本和机构认可规则匹配 |
| `EXTERNAL_DEPENDENCY` | 外部规范、环境、审批、合同或联调材料 | 否；缺失时为 `BLOCKED` |

开发单测、Mock、脚本语法检查、代码审查和 Testcontainers 验证均不能单独推导生产验收 `PASS`。本包当前没有 `WAIVED`，也没有正式生产准入结论。

## 3. 导航

1. [FR 证据索引](fr-evidence-index.md)
2. [上线门禁矩阵](release-gate-matrix.md)
3. [生产等价环境补测计划](production-validation-plan.md)
4. [差距与外部依赖台账](gap-and-dependency-register.md)
5. [发布准入与风险豁免清单](release-approval-checklist.md)
6. [交付与服务证据清单](delivery-service-evidence.md)

## 4. 事实源

需求和流程以 `docs/requirements.md`、`docs/technical-requirements.md`、`docs/development-process-workflow.md` 为准；历史事实主要来自 `delivery/acceptance-report.md`、`delivery/p1-acceptance-summary.md`、`perf/p2-02-report.md`、`delivery/p2-03-report.md`、`security/p2-04-report.md`、`delivery/p2-05-report.md` 及对应 `reviews/claude-review-P2-*.md`。冲突时使用最新 Claude Code 审查和可定位原始输出，不能美化为 PASS。

## 5. 更新、复审和敏感信息边界

- 更新者须记录证据 ID、环境、版本、采集角色、日期和受控位置；生产敏感原件保存在机构认可系统，仓库只记脱敏摘要或 `CONTROLLED-LOCATION-TBD`。
- 任何证据路径失效、环境不匹配或复核失败，相关状态立即恢复为 `BLOCKED` 或 `FAIL`。
- `WAIVED` 到期自动恢复 `BLOCKED`；开发证据作者不能单方批准自己的豁免。
- 禁止写入真实账号、令牌、密码、连接串、内部 URL、个人联系方式、生产导出、漏洞细节、密钥或证书内容。
- 每次更新后重新执行文件/引用、覆盖、状态机、敏感信息、范围和 `git diff --check` 检查，并由 Claude Code 最终验收。

## 6. 校验记录

- 本次仅进行了仓库本地静态检查；未执行 Maven/npm、压测、DAST/SCA、故障演练、恢复、外部联调或发布。
- NFR 基线口径为 24 个唯一 NFR 编号，拆分为 27 条原子门禁；矩阵通过拆分 NFR-P01、NFR-P06 的复合阈值形成原子门禁，未虚构新编号。
