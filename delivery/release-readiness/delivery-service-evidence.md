# 产品交付与服务承诺证据清单

> 仓库文档“存在”不等于已被机构验收，也不能证明合同/组织承诺已履行。当前交付服务总状态为 `BLOCKED`。

## 1. 产品文档与测试验收材料

| Evidence ID | 原始要求 | 当前可引用材料 | 状态 | 责任角色 | 还需的机构/供应商非代码证据 | 下一步 |
|---|---|---|---|---|---|---|
| E-DOC-01 | 系统架构文档与版本一致 | `delivery/system-architecture.md` | BLOCKED | 架构/交付负责人 | 版本审查记录、机构验收签署、更新日期与受控位置 | 对候选版本逐章复核并签署 |
| E-DOC-02 | 部署手册完整可操作 | `delivery/deployment-guide.md` | BLOCKED | 运维/交付负责人 | PROD_EQ 走查记录、机构模板验收 | 按批准环境演练并修订差异 |
| E-DOC-03 | 运维手册完整可操作 | `delivery/ops-manual.md` | BLOCKED | 运维/交付负责人 | 监控/告警/故障/巡检演练记录与签署 | 执行 PV-OPS |
| E-DOC-04 | 开发手册支持二次开发 | `delivery/dev-guide.md` | BLOCKED | 开发/交付负责人 | 开发培训、SDK/扩展演练和验收记录 | 组织开发人员走查 |
| E-DOC-05 | 用户操作手册与实际功能一致 | `delivery/user-guide.md` | BLOCKED | 产品/交付负责人 | 业务角色操作走查、截图版本核对、验收签署 | 执行易用性验收 |
| E-TEST-01 | 功能测试报告、覆盖 100%、核心通过且无高危缺陷 | `delivery/acceptance-report.md`、`delivery/p1-acceptance-summary.md` | BLOCKED | 测试负责人 | 候选版本 CI、PROD_EQ 46 FR 报告、缺陷清单和业务签署 | 完成 G-FUNC/G-TEST |
| E-TEST-02 | 性能与 48h 稳定性验收报告 | `perf/p2-02-report.md`、`delivery/p2-03-report.md` | BLOCKED | 性能/运维负责人 | PROD_EQ/第三方原始性能与 48h 报告 | 执行 PV-PERF/PV-STABLE |
| E-TEST-03 | 安全、兼容、恢复和合规材料 | `security/p2-04-report.md`、`delivery/p2-05-report.md` | BLOCKED | 安全/合规/灾备负责人 | SCA/DAST/等保/国产化/恢复/合规受控报告 | 执行相关补测卡 |

## 2. 培训证据

| Evidence ID | 原始要求 | 当前可引用材料 | 状态 | 责任角色 | 还需的机构/供应商非代码证据 | 下一步 |
|---|---|---|---|---|---|---|
| E-TRN-01 | 管理员培训 | `delivery/user-guide.md`、`delivery/ops-manual.md` | BLOCKED | 培训负责人 | 课程/讲师资质、通知、签到、现场+线上记录、考核和反馈 | 制定≤3工作日计划并确认名单 |
| E-TRN-02 | 运维人员培训 | `delivery/ops-manual.md`、`delivery/deployment-guide.md` | BLOCKED | 培训/运维负责人 | 环境实操、故障/回退考核、签到与成绩 | 与 PV-OPS/AVAIL 联动演练 |
| E-TRN-03 | 业务用户培训 | `delivery/user-guide.md` | BLOCKED | 培训/业务负责人 | 分角色现场+线上培训、操作考核和反馈 | 冻结课程与验收标准 |
| E-TRN-04 | 开发人员/二次开发培训 | `delivery/dev-guide.md`、`delivery/system-architecture.md` | BLOCKED | 培训/开发负责人 | SDK/插件/质量规则等实操、考核和材料版本 | 组织二次开发演练 |
| E-TRN-05 | 版本更新持续培训 | 五类产品文档 | BLOCKED | 产品/培训负责人 | 更新通知、差异课程、参训/考核记录 | 纳入版本发布流程 |

## 3. 支持、SLA、巡检和升级服务

| Evidence ID | 原始要求 | 当前可引用材料 | 状态 | 责任角色 | 还需的机构/供应商非代码证据 | 下一步 |
|---|---|---|---|---|---|---|
| E-SVC-01 | 7×24 技术支持和专属服务团队 | `delivery/ops-manual.md`（仅运维指导） | BLOCKED | 服务交付负责人 | 已签合同/SOW、服务台、值守表、升级链和受控联系方式 | 供应商提交并由机构确认 |
| E-SVC-02 | 响应≤30min、一般解决≤4h、重大解决≤24h | 无仓库履约证据；`CONTROLLED-LOCATION-TBD` | BLOCKED | 服务经理 | SLA 定义、分级、计时口径、服务台报表和演练记录 | 冻结 SLA 并执行演练 |
| E-SVC-03 | 每月≥1次远程巡检 | `delivery/ops-manual.md` 巡检清单 | BLOCKED | 运维服务负责人 | 年度计划、月度工单/报告、问题与优化闭环 | 建立受控巡检计划 |
| E-SVC-04 | 每季度≥1次现场巡检 | `delivery/ops-manual.md` 巡检清单 | BLOCKED | 运维服务负责人 | 现场计划、签到/报告、优化建议与关闭记录 | 机构确认现场安排 |
| E-SVC-05 | 终验后至少三年原厂免费运维 | 无仓库合同证据；`CONTROLLED-LOCATION-TBD` | BLOCKED | 商务/交付负责人 | 已签合同、起算条件、范围、排除项和原厂承诺 | 采购/法务确认 |
| E-SVC-06 | 免费小版本/补丁升级，大版本优惠支持 | `delivery/upgrade-rollback-drill.md`（仅技术演练说明） | BLOCKED | 产品/服务负责人 | 版本政策、维护窗口、支持范围、报价/合同条款 | 冻结版本服务政策 |
| E-SVC-07 | 项目组织、≤6个月计划、周报/月报和关键人员 | 无完整仓库受控证据；`CONTROLLED-LOCATION-TBD` | BLOCKED | 项目/交付负责人 | 实施方案、组织/资历确认、里程碑、周报/月报和变更记录 | 机构项目治理确认 |

## 4. 交付签署占位

每项正式签署至少记录：候选版本、材料版本、更新日期、提交角色、验收角色、受控材料编号和结论。真实人员/联系方式不写入仓库。当前所有签署位置均为 `CONTROLLED-LOCATION-TBD`，不得据此标 PASS。

## 5. 校验记录

- NFR 基线口径统一为 24 个唯一 NFR 编号，拆分为 27 条原子门禁；本清单保留 NFR-M01/U01 对应的交付与服务证据边界。

- 已定位五类现有产品文档，并覆盖测试/验收、四类培训、现场+线上、考核、7×24、响应/解决时限、月度远程/季度现场巡检、三年原厂运维和版本升级服务。
- 合同、培训记录、服务台、值守和履约材料无法从仓库验证，均保持 `BLOCKED`，未虚构供应商承诺或审批。
- 本次仅检查文件存在和清单字段；未执行培训、服务演练、巡检或机构签署。
