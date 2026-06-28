# 外部数据采集平台开发流程化文档

> 生成日期：2026-06-27  
> 依据文档：`docs/github-reference-functional-design.md`、`docs/implementation-gap-and-test-plan.md`、`docs/detailed-requirements-design.md`、`docs/database-design.md`、`AGENTS.md`。  
> 目标：把整体设计方案转化为可执行、可交接、可验收的开发流程，明确阶段、角色、输入输出、门禁和返工规则。  
> 适用范围：P0 上线阻断修复、P1 验收增强、P2 生产强化，以及后续模块功能迭代。

## 1. 流程总览

本项目开发采用“设计先行、任务驱动、最小改动、测试闭环、审查验收”的流程。

```text
需求/设计输入
  -> Claude Code 生成任务单
  -> Codex 按任务单实现
  -> Codex 自测并输出结果
  -> Claude Code 审查 git diff 和测试结果
  -> 通过/返工/进入下一阶段
  -> 上线前门禁验收
```

## 2. 角色分工

| 角色 | 职责 | 产物 |
|---|---|---|
| Claude Code | 需求判断、方案拆解、任务生成、审查验收 | `tasks/*.md`、`reviews/*.md`、验收结论 |
| Codex | 按任务实现代码、补测试、运行测试、报告风险 | 代码改动、测试结果、风险说明 |
| 用户/项目负责人 | 决定业务范围、外部接口口径、上线标准 | 范围确认、接口确认、上线批准 |

关键约束：

1. Codex 不重新解释需求，只执行明确任务单。
2. Claude Code 不直接大规模写业务代码。
3. 任意阶段不得修改 `.env`、证书、生产配置、真实密钥。
4. 默认不提交主分支，审查通过后再建议提交。

## 3. 阶段划分

### 3.1 P0：上线阻断修复

P0 目标是让系统具备生产上线基本条件，优先解决事实源、持久化、安全、迁移和真实依赖验证。

| 编号 | 主题 | 输入 | 输出 | 通过标准 |
|---|---|---|---|---|
| P0-01 | Flyway 迁移修复 | `database-design.md`、现有 `db/migration` | 修正后的迁移脚本和 rollback | 空库/旧库迁移成功 |
| P0-02 | 国产库兼容 | MySQL/达梦/OceanBase 约束 | 方言策略或拆分脚本 | 三类库迁移验证通过 |
| P0-03 | 核心 Repository 落库 | 当前内存仓储 | JDBC Repository + profile 切换 | 重启后主链路数据不丢 |
| P0-04 | API 凭证安全 | `t_api_credential` 设计 | secret 密文/KMS 引用、轮换、禁用 | 表/日志/响应无明文 secret |
| P0-05 | 调用日志事实源 | `t_service_invoke_log` | trace/request_hash/error 字段和写入逻辑 | billing/stats/audit 可聚合 |
| P0-06 | 账单明细 | 计费设计 | `t_bill_item` + 从日志聚合账单 | 明细与总额一致 |
| P0-07 | 目录申请 | 目录设计 | `t_catalog_application` + 仓储 | 申请审批持久可查 |
| P0-08 | 审计防篡改 | `t_audit_log` 设计 | hash 链或 DB 权限策略 | 篡改可阻止或可发现 |
| P0-09 | 前端边界测试 | 现有 Vue 页面 | 空态/失败态/校验态/按钮断言 | Vitest 全绿 |
| P0-10 | 真实依赖 E2E | docker compose/Testcontainers | 10 步主链路脚本 | 真实 DB/Redis/MQ/MinIO 通过 |

### 3.2 P1：验收增强

P1 目标是让功能更完整、更接近招采验收口径。

| 编号 | 主题 | 输出 | 通过标准 |
|---|---|---|---|
| P1-01 | Connector 合约 | SourceConnector 规范和能力矩阵 | 每类协议 contract test 通过 |
| P1-02 | 目录治理 | 血缘、质量摘要、使用统计 | 目录详情可追溯 |
| P1-03 | 质量报告 | 报告持久化和导出 | 按合作方/资产/服务生成报告 |
| P1-04 | 监管报表 | 模板、生成、脱敏、回执 | 报文和回执入库 |
| P1-05 | 财务适配 | Finance/Purchase adapter | mock 成功/失败/重试通过 |

### 3.3 P2：生产强化

P2 目标是支撑性能、稳定性、安全和运维要求。

| 编号 | 主题 | 输出 | 通过标准 |
|---|---|---|---|
| P2-01 | 大表分区归档 | 分区/归档脚本和查询优化 | EXPLAIN 命中索引/分区 |
| P2-02 | 压测容量 | JMeter/压测报告 | P95/P99、TPS、批量接入达标 |
| P2-03 | 故障演练 | 故障注入和恢复记录 | 关键依赖故障可恢复 |
| P2-04 | 安全扫描 | SCA/渗透测试报告 | 无高危未处置 |
| P2-05 | 备份恢复 | 备份、恢复、审计校验方案 | 数据可恢复，审计链可校验 |

## 4. 单个任务开发流程

每个任务必须按照以下步骤推进。

### 4.1 任务准备

| 步骤 | 执行方 | 操作 | 产物 |
|---|---|---|---|
| 1 | Claude Code | 读取需求、设计、审核文档 | 需求理解 |
| 2 | Claude Code | 生成任务分析 | `tasks/requirement-analysis-*.md` |
| 3 | Claude Code | 生成实现计划 | `tasks/claude-plan-*.md` |
| 4 | Claude Code | 生成 Codex 任务单 | `tasks/codex-task-*.md` |
| 5 | Codex | 读取 AGENTS 和任务单 | 执行上下文 |

任务单必须包含：

1. 背景和目标。
2. 明确范围和不做事项。
3. 需要修改的模块。
4. 数据库/API/前端影响。
5. 必须补充的测试。
6. 验收命令。
7. 风险和回滚要求。

### 4.2 分支与工作区

建议每个任务使用独立分支：

```bash
git checkout -b ai/p0-repository-persistence
```

分支命名建议：

| 阶段 | 格式 | 示例 |
|---|---|---|
| P0 | `ai/p0-主题` | `ai/p0-flyway-jdbc` |
| P1 | `ai/p1-主题` | `ai/p1-catalog-lineage` |
| P2 | `ai/p2-主题` | `ai/p2-performance-hardening` |

## 5. 开发执行规范

### 5.1 通用编码规则

1. 优先复用现有 Controller、Service、Repository、DTO 风格。
2. 优先补 JDBC Repository，不直接删除内存实现；内存实现保留给 test/profile。
3. 迁移脚本必须可空库执行，也要考虑旧库升级。
4. 新增字段必须同步更新库设、Repository、测试数据和 MockMvc 断言。
5. 错误返回统一业务异常，响应中包含 `code/message/traceId`。
6. 写操作必须有权限码和审计事件。
7. 不引入 Keycloak/Airbyte/Kong/DataHub 等整套系统作为强依赖。

### 5.2 数据库开发规则

数据库改动必须遵循：

1. 先修正表设计冲突，再写代码依赖。
2. 每个 migration 有对应 rollback。
3. 避免通用脚本使用 `AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP` 等单库方言。
4. 大表如 `t_service_invoke_log/t_audit_log/t_raw_data` 必须考虑索引和分区。
5. 密钥字段只存密文、hash 或 KMS 引用。
6. 审计表默认只追加，不提供业务更新删除接口。

### 5.3 API 开发规则

API 改动必须包含：

1. Controller 方法。
2. 权限码。
3. 请求参数校验。
4. 业务异常码。
5. MockMvc 正常和异常路径。
6. 前端 API client 对齐。

状态码建议：

| 场景 | HTTP | 业务码示例 |
|---|---:|---|
| 未登录 | 401 | `AUTH-UNAUTHORIZED` |
| 权限不足 | 403 | `AUTH-FORBIDDEN` |
| 资源不存在 | 404 | `PARTNER-404` |
| 参数非法 | 400 | `VALIDATION-FAILED` |
| 状态非法 | 409 | `STATE-INVALID` |
| 系统异常 | 500 | `INTERNAL-ERROR` |

### 5.4 前端开发规则

前端页面改动必须覆盖：

1. 空列表态。
2. 加载失败态。
3. 表单校验态。
4. 权限不足态。
5. 状态按钮与后端状态机一致。
6. 操作按钮必须断言调用正确 API。

## 6. 模块开发流程

### 6.1 合作方管理流程

```text
表结构校验
  -> Partner/Interface/Event JDBC Repository
  -> 资源不存在和状态异常统一错误
  -> 凭证密文和轮换
  -> 健康检查和评级
  -> MockMvc + JDBC + 凭证泄露测试
```

完成标准：

- partner/interface/event 重启后不丢。
- 凭证不明文入库。
- 状态事件和审计日志能按 trace_id 查询。

### 6.2 外部数据接入流程

```text
Connector 合约
  -> 协议 connector 实现
  -> 格式转换统一输出
  -> offset/checkpoint 持久化
  -> 上线前置校验
  -> contract test + 恢复测试
```

完成标准：

- 每类协议有 check/read/checkpoint 测试。
- 中断后可从 checkpoint 恢复。
- 缺映射、缺规则、连接失败不能上线。

### 6.3 外部数据服务流程

```text
DataService JDBC Repository
  -> ApiCredential 密文仓储
  -> invoke 治理链
  -> 限流/熔断
  -> InvokeLog 落库
  -> 签名/限流/日志聚合测试
```

完成标准：

- 调用方不传 secret。
- 成功和失败调用都写日志。
- billing/stats/audit 均可复用调用日志。

### 6.4 数据目录流程

```text
Catalog JDBC Repository
  -> CatalogApplication 表和仓储
  -> preview 授权和脱敏
  -> lineage/quality/usage 摘要
  -> 申请审批测试
```

完成标准：

- 目录申请审批重启后仍可查。
- 预览未授权返回 403。
- 敏感字段脱敏，预览写审计。

### 6.5 消费方流程

```text
Consumer/Quota/Event 落库
  -> Redis 配额计数
  -> API Key 管理
  -> 授权范围
  -> 调用日志查询
```

完成标准：

- 并发扣减不超额。
- 旧 API Key 禁用后不可调用。
- 消费方可查询自身调用日志。

### 6.6 缓存存储流程

```text
ObjectStorageClient 抽象
  -> MinIO/S3 profile
  -> DataAsset/Marketplace/Lifecycle 落库
  -> 归档/恢复/销毁
  -> proof_hash 校验
```

完成标准：

- 冷数据对象可上传、恢复、销毁。
- 销毁证明可校验。
- 生命周期事件写审计。

### 6.7 计费流程

```text
BillingRule 落库
  -> BillItem 表
  -> 从 InvokeLog 聚合账单
  -> 异议/调整/确认状态机
  -> 财务适配器 mock
```

完成标准：

- 请求体 logs 不能作为账单事实源。
- 账单总额等于明细合计。
- 异议、调整、确认状态机受控。

### 6.8 统计监管流程

```text
StatsSnapshot 落库
  -> 指标定义
  -> 监管报表模板
  -> 报文脱敏/加密
  -> 回执状态
  -> trace_id 审计追溯
```

完成标准：

- dashboard 指标来自事实表。
- 监管报表可生成和下载。
- 一次主链路可按 trace_id 查询全事件。

### 6.9 数据质量流程

```text
QualityRule/Result/Issue 落库
  -> 规则套件
  -> 校验任务
  -> 问题闭环
  -> 质量报告和评分
```

完成标准：

- 六维规则覆盖完整。
- 最新结果按 checked_at 判断。
- 质量问题状态机和超时告警可测。

## 7. 测试流程

### 7.1 每次任务必须运行

| 类型 | 命令示例 | 要求 |
|---|---|---|
| 后端单测 | `mvn test` | 相关模块必须全绿 |
| 前端单测 | `npm run test:unit` | 改前端时必须全绿 |
| 构建 | `npm run build` | 改前端页面/路由时必须通过 |
| 迁移验证 | Flyway 空库/升级库 | 改 DB 时必须通过 |

### 7.2 P0 阶段专项测试

| 专项 | 覆盖 |
|---|---|
| JDBC 持久化 | CRUD、唯一约束、事务、重启恢复 |
| 真实依赖 E2E | DB、Redis、MQ、MinIO profile |
| 安全 | secret 泄露扫描、签名重放、鉴权绕过 |
| 审计 | hash 链、trace_id、禁止篡改 |

### 7.3 测试证据要求

Codex 完成后必须输出：

1. 测试命令。
2. 测试结果。
3. 失败项和原因。
4. 未运行测试及原因。
5. 残留风险。

## 8. 审查流程

### 8.1 Codex 完成后

Codex 必须提供：

1. 修改文件清单。
2. 功能实现摘要。
3. 数据库/API/前端影响。
4. 测试命令和结果。
5. 潜在风险和回滚方式。

### 8.2 Claude Code 审查

Claude Code 必须执行：

```bash
git status
git diff
```

并生成：

```text
reviews/claude-review-阶段-任务.md
```

审查内容：

1. 是否符合任务单。
2. 是否满足设计文档。
3. 是否超范围。
4. 是否改动敏感文件。
5. 是否有测试。
6. 是否有安全风险。
7. 是否可回滚。

### 8.3 结论类型

| 结论 | 含义 | 下一步 |
|---|---|---|
| 通过 | 满足任务和测试 | 可进入下一任务 |
| 有条件通过 | 非阻断残留 | 记录遗留，进入下一任务 |
| 返工 | 存在阻断问题 | 生成返工任务 |
| 不通过 | 方向错误或风险过高 | 回退或重新设计 |

## 9. 上线门禁

上线前必须全部满足：

| 门禁 | 标准 |
|---|---|
| 功能门禁 | 九大模块主链路可用，46 条 FR 有证据 |
| 数据库门禁 | 空库/升级库迁移成功，目标结构与库设一致 |
| 持久化门禁 | 主链路数据重启后不丢 |
| 安全门禁 | secret 密文、鉴权、签名、审计防篡改通过 |
| 测试门禁 | 单元、MockMvc、集成、E2E、前端边界全绿 |
| 性能门禁 | P95/P99、TPS、批量接入、日志聚合达标 |
| 合规门禁 | trace_id 可追溯，监管报表可生成 |
| 运维门禁 | 健康检查、日志、指标、告警、备份恢复可用 |

## 10. 返工流程

返工必须闭环：

```text
发现问题
  -> 记录编号
  -> 判断严重度
  -> 生成返工任务
  -> Codex 修复
  -> 运行回归测试
  -> Claude Code 二次审查
  -> 关闭问题或继续返工
```

返工记录格式：

| 字段 | 说明 |
|---|---|
| 问题编号 | 如 `P0-R01` |
| 严重度 | 高/中/低 |
| 所属模块 | partner/pipeline/billing 等 |
| 问题描述 | 具体行为和影响 |
| 修复要求 | 明确到代码或测试 |
| 验收方式 | 命令、接口、SQL 或页面 |
| 状态 | OPEN/FIXED/VERIFIED/CLOSED |

## 11. 文档交接规范

### 11.1 设计类文档

| 文档 | 用途 |
|---|---|
| `docs/detailed-requirements-design.md` | 需求详设 |
| `docs/database-design.md` | 数据库目标设计 |
| `docs/implementation-gap-and-test-plan.md` | 当前差距和测试计划 |
| `docs/github-reference-functional-design.md` | 高 star 参照功能方案 |
| `docs/development-process-workflow.md` | 开发流程化执行手册 |

### 11.2 任务类文档

建议后续按阶段生成：

```text
tasks/codex-task-P0-01-flyway.md
tasks/codex-task-P0-02-db-compat.md
tasks/codex-task-P0-03-repository-persistence.md
...
```

### 11.3 审查类文档

建议后续按任务生成：

```text
reviews/claude-review-P0-01-flyway.md
reviews/claude-review-P0-02-db-compat.md
reviews/claude-review-P0-03-repository-persistence.md
```

## 12. 开发节奏建议

### 12.1 推荐顺序

```text
P0-01/P0-02 数据库基础
  -> P0-03 核心仓储落库
  -> P0-04/P0-05 安全与调用日志
  -> P0-06/P0-07 账单和目录事实源
  -> P0-08 审计不可篡改
  -> P0-09 前端边界测试
  -> P0-10 真实依赖 E2E
  -> P1 验收增强
  -> P2 生产强化
```

### 12.2 任务拆分原则

1. 一个任务只关闭一个主问题。
2. 数据库迁移和业务代码可在同任务，但必须有迁移测试。
3. 不把 P0/P1/P2 混在一个任务里。
4. 每个任务都要可回滚。
5. 每个任务都要有明确“完成即停止”的边界。

## 13. 风险控制

| 风险 | 控制方式 |
|---|---|
| 迁移破坏已有库 | 空库 + 旧库升级双验证，保留 rollback |
| 内存仓储和 JDBC 行为不一致 | Repository contract test |
| 密钥泄露 | secret 扫描、日志脱敏测试、响应断言 |
| 账单口径错误 | 从 invoke log 聚合，明细和总额双校验 |
| 审计缺链路 | trace_id 全链路 E2E |
| 真实依赖不稳定 | Testcontainers/docker compose 固定版本 |
| 任务失控 | Claude Code 任务单限定范围，Codex 最小改动 |

## 14. 结论

后续开发应先按 P0 阶段关闭上线阻断项，再进入 P1 和 P2。每个开发任务必须经过“任务单 -> 实现 -> 测试 -> 审查 -> 返工/通过”的闭环。该流程文档与 `docs/github-reference-functional-design.md` 配套使用：前者回答“怎么组织开发”，后者回答“每个模块开发什么”。
