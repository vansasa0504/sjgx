# 阶段任务总览清单

> 生成日期：2026-06-27
> 依据：`docs/development-process-workflow.md`（流程化执行手册）、`docs/database-design.md`、`docs/implementation-gap-and-test-plan.md`、`docs/detailed-requirements-design.md`、`reviews/claude-review.md`（M7-A/B/C/D 审查结论）
> 用途：把流程文档 §3 的 P0/P1/P2 阶段任务展开为可派发、可验收的任务条目，标注与已审查 M7 遗留项的衔接，作为后续逐个生成 `tasks/codex-task-Px-yy-*.md` 的索引与全局视图。
> 协作模式：Claude Code 生成任务单 → Codex 实现 → Claude Code 审查 → 返工/通过（见 `CLAUDE.md` §3）。

---

## 1. 全局说明

### 1.1 阶段定位

| 阶段 | 目标 | 任务数 | 阻断级别 |
|---|---|---|---|
| P0 | 上线阻断修复：事实源、持久化、安全、迁移、真实依赖 | 10 | 阻断上线 |
| P1 | 验收增强：功能完整、贴近招采验收口径 | 5 | 阻断验收 |
| P2 | 生产强化：性能、稳定性、安全、运维 | 5 | 阻断生产 |

### 1.2 任务单结构

每个 P0 任务单均遵循 `docs/development-process-workflow.md` §4.1 的 7 项要求：

1. 背景与目标。
2. 范围（本次实现 / 不做）。
3. 必读输入。
4. 需要修改的模块。
5. 数据库 / API / 前端影响。
6. 必须补充的测试。
7. 验收命令。

在上述基础上，每个 P0 任务单额外增加 **M7 衔接** 与 **风险与回滚** 两节，确保任务可派发、可验收、可回滚。

### 1.3 现状基线（M7 收尾后）

- **后端**：11 个 Controller + JWT + `@RequirePermission` 已就绪，154 测试全绿（含 69 MockMvc）。
- **前端**：10 个功能化页面 + 基础设施，35 测试全绿。
- **迁移脚本**：`db/migration/V001~V010` + `U010` 已存在（含 `t_user/t_role/t_api_credential` 表结构）。
- **仓储现状**：8 个 `InMemory*Repository`（bill/billingRule/statsSnapshot/auditLog/dataAsset/marketplace/qualityRule/qualityWeight）；`JdbcAuditLogRepository` 已有原型；`ApiCredentialRepository` 接口已存在。
- **端到端**：M7-D 已验证 10 步主链路（基于内存仓储 + 部分依赖），但未用真实 DB/Redis/MQ/MinIO profile。

### 1.4 M7 遗留项（来自历次审查，P0 须吸收）

| M7 遗留 | 来源 | 对应 P0 任务 |
|---|---|---|
| 用户/角色内存实现不落表 | M7-A F-07、M7-D D2-05 | P0-03 |
| `/invoke` secret 由调用方 body 明文传入 | M7-A F-08、M7-D D2-05 | P0-04 |
| consumer logs 返回空（无调用日志仓储） | M7-A F-06 | P0-05 |
| billing 缺 `GET /api/v1/billing/stats` 端点 | M7-A F-04 | P0-06 |
| catalog preview 为桩（返回空 sample） | M7-A F-05 | P0-07（部分） |
| 前端边界测试缺失（空态/失败态/校验态） | M7-D D2-01 | P0-09 |
| 前端状态流转断言、BillingView confirm 反馈、IngestView partnerId select、StatsView 假导出 | M7-C R-05/R-06/R-07、M7-D D2-04 | P0-09 |
| CatalogService 种子数据在生产代码 | M7-D D2-02 | P0-07 |
| PartnerController.detail 抛 IllegalArgumentException 非 BusinessException | M7-D D2-03 | P0-03（顺带） |
| 端到端基于内存，未验证真实 DB/Redis/MQ/MinIO | M7-D D2-01 | P0-10 |

---

## 2. P0 阶段任务（上线阻断修复）

| 编号 | 主题 | 依赖 | 涉及模块 | 核心产出 | 验收标准 | M7 衔接 | 任务单 |
|---|---|---|---|---|---|---|---|
| P0-01 | Flyway 迁移修复 | — | db/migration | 修正 V001~V010 冲突 + 对应 rollback | 空库/旧库迁移成功，结构与库设一致 | — | `codex-task-P0-01-flyway.md` |
| P0-02 | 国产库兼容 | P0-01 | db/migration、platform-common | 方言策略或拆分脚本 | MySQL/达梦/OceanBase 三库迁移通过 | — | `codex-task-P0-02-db-compat.md` |
| P0-03 | 核心 Repository 落库 | P0-01 | platform-auth/partner/pipeline/quality/billing | JDBC Repository + profile 切换，内存实现保留给 test | 重启后主链路数据不丢 | M7-A F-07（用户落表）、M7-D D2-03（detail 异常） | `codex-task-P0-03-repository-persistence.md` |
| P0-04 | API 凭证安全 | P0-03 | platform-pipeline.service | secret 密文/KMS 引用、轮换、禁用；invoke 不接收 secret | 表/日志/响应无明文 secret | M7-A F-08（invoke secret 明文） | `codex-task-P0-04-api-credential.md` |
| P0-05 | 调用日志事实源 | P0-03 | platform-pipeline.service、platform-common | `t_service_invoke_log` trace/request_hash/error 字段 + 写入逻辑 | billing/stats/audit 可从日志聚合 | M7-A F-06（consumer logs 空） | `codex-task-P0-05-invoke-log.md` |
| P0-06 | 账单明细 | P0-05 | platform-billing | `t_bill_item` + 从日志聚合账单；补 `/billing/stats` | 明细合计=总额 | M7-A F-04（billing /stats） | `codex-task-P0-06-bill-item.md` |
| P0-07 | 目录申请 | P0-03 | platform-pipeline.catalog | `t_catalog_application` + 仓储；preview 真实化；移除种子数据 | 申请审批持久可查，preview 授权脱敏 | M7-A F-05、M7-D D2-02 | `codex-task-P0-07-catalog-application.md` |
| P0-08 | 审计防篡改 | P0-03 | platform-common.audit | hash 链或 DB 权限策略 | 篡改可阻止或可发现 | — | `codex-task-P0-08-audit-tamper.md` |
| P0-09 | 前端边界测试 | — | platform-ui | 空态/失败态/校验态/按钮断言 | Vitest 全绿 | M7-D D2-01/D2-04、M7-C R-05/R-06/R-07 | `codex-task-P0-09-frontend-boundary.md` |
| P0-10 | 真实依赖 E2E | P0-01~P0-08 | 全模块 | docker-compose/Testcontainers 10 步主链路脚本 | 真实 DB/Redis/MQ/MinIO 通过 | M7-D D2-01（端到端真实化） | `codex-task-P0-10-e2e-real-deps.md` |

### 2.1 关键设计决策

- **执行顺序**：P0-01 → P0-02 先夯实数据库基础；P0-03 完成核心仓储落库；P0-04/P0-05 处理安全与调用日志事实源；P0-06/P0-07 建立账单和目录事实源；P0-08 补齐审计不可篡改；P0-09 前端边界测试可并行；P0-10 真实依赖 E2E 作为 P0 收尾门禁。
- **依赖链**：P0-04/P0-05/P0-06/P0-07/P0-08 均依赖 P0-03 的持久化基础；P0-06 依赖 P0-05 的调用日志事实源；P0-08 依赖 P0-05 产出的 `trace_id`；P0-10 依赖 P0 全部后端阻断项完成。
- **P1/P2 暂列条目**：当前采用“总览 + P0 展开”的粒度。P1/P2 在总览中列出但不展开任务单，待 P0/P1 收尾后再生成，避免上游任务未冻结导致后续返工。

### P0 推荐执行顺序（流程文档 §12.1）

```text
P0-01 → P0-02（数据库基础）
  → P0-03（核心仓储落库）
  → P0-04 / P0-05（安全与调用日志事实源）
  → P0-06 / P0-07（账单和目录事实源）
  → P0-08（审计不可篡改）
  → P0-09（前端边界测试，可与 P0-03~08 并行）
  → P0-10（真实依赖 E2E，收尾）
```

> P0-09 不依赖后端改动，可与 P0-03~P0-08 并行推进。P0-10 须在 P0-01~P0-08 完成后作为 P0 收尾门禁。

---

## 3. P1 阶段任务（验收增强）

> P1 任务单在 P0 收尾后按需生成（流程文档 §11.2）。此处先列条目，待 P0 通过后展开。

| 编号 | 主题 | 依赖 | 涉及模块 | 核心产出 | 验收标准 |
|---|---|---|---|---|---|
| P1-01 | Connector 合约 | P0-03 | platform-pipeline.ingest | SourceConnector 规范 + 能力矩阵 + contract test | 每类协议 contract test 通过 |
| P1-02 | 目录治理 | P0-07 | platform-pipeline.catalog | 血缘、质量摘要、使用统计 | 目录详情可追溯 |
| P1-03 | 质量报告 | P0-03 | platform-quality | 报告持久化 + 导出 | 按合作方/资产/服务生成报告 |
| P1-04 | 监管报表 | P0-05 | platform-billing.stats | 模板、生成、脱敏、回执 | 报文和回执入库 |
| P1-05 | 财务适配 | P0-06 | platform-billing | Finance/Purchase adapter | mock 成功/失败/重试通过 |

---

## 4. P2 阶段任务（生产强化）

> P2 任务单在 P1 通过后按需生成。

| 编号 | 主题 | 依赖 | 涉及模块 | 核心产出 | 验收标准 |
|---|---|---|---|---|---|
| P2-01 | 大表分区归档 | P0-01、P0-05 | db/migration、platform-common | 分区/归档脚本 + 查询优化 | EXPLAIN 命中索引/分区 |
| P2-02 | 压测容量 | P1 完成 | perf/ | JMeter 压测报告 | P95/P99、TPS、批量接入达标 |
| P2-03 | 故障演练 | P2-02 | delivery/、k8s/ | 故障注入和恢复记录 | 关键依赖故障可恢复 |
| P2-04 | 安全扫描 | P0-04、P0-08 | security/ | SCA/渗透测试报告 | 无高危未处置 |
| P2-05 | 备份恢复 | P0-08 | delivery/、k8s/ | 备份、恢复、审计校验方案 | 数据可恢复，审计链可校验 |

---

## 5. 任务单生成状态

| 阶段 | 总数 | 已生成 | 文件 |
|---|---|---|---|
| P0 | 10 | 10 | `tasks/codex-task-P0-01-flyway.md` ~ `P0-10-e2e-real-deps.md` |
| P1 | 5 | 0 | 待 P0 收尾后生成 |
| P2 | 5 | 0 | 待 P1 通过后生成 |

---

## 6. 通用执行规则（所有任务单共用）

1. **分支**：每个任务独立分支 `ai/p0-主题`（流程文档 §4.2）。
2. **必读输入**：`AGENTS.md`、`docs/detailed-requirements-design.md`、`docs/database-design.md`、对应任务单。
3. **不改**：`.env`、证书、生产配置、密钥、`docs/`、`tasks/`（本任务单除外）、`reviews/`、`k8s/prod/`、`delivery/`、`perf/`、`security/`。
4. **最小改动**：复用既有 Controller/Service/Repository 风格；内存实现保留给 test/profile，不直接删除。
5. **测试闭环**：`mvn test` + `npm run test:unit` 全绿；改 DB 须空库+旧库双验证；改前端须 `npm run build` 通过。
6. **错误规范**：统一业务异常，响应含 `code/message/traceId`；状态码 401/403/404/400/409/500（流程文档 §5.3）。
7. **审查产物**：Codex 完成后输出修改清单/影响/测试结果/风险；Claude Code 生成 `reviews/claude-review-Px-yy-*.md`。
8. **返工**：闭环流程（流程文档 §10），同一任务返工不超过 3 次。

---

## 7. 上线门禁对照（流程文档 §9）

P0 全部通过后须满足：功能门禁（九大模块主链路 + 46 FR）、数据库门禁（空库/升级库迁移）、持久化门禁（重启不丢）、安全门禁（secret 密文/鉴权/签名/审计防篡改）、测试门禁（单元+MockMvc+集成+E2E+前端边界全绿）。性能/合规/运维门禁在 P1/P2 完成。
