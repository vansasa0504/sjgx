# Codex 执行任务 - P0-03：核心 Repository 落库

> 阶段：P0（上线阻断修复）
> 任务编号：P0-03
> 分支建议：`ai/p0-repository-persistence`
> 依据：`docs/development-process-workflow.md` §3.1 P0-03、§5.1、§6.1~6.9、`docs/database-design.md`
> 前置：P0-01 通过（迁移脚本可用）
> 日期：2026-06-27

---

## 1. 背景与目标

M7-A/B/C/D 全程使用 8 个 `InMemory*Repository`（bill/billingRule/statsSnapshot/auditLog/dataAsset/marketplace/qualityRule/qualityWeight）+ AuthService 内存实现（用户/角色）。重启即丢，无法上线。本任务把核心仓储改为 JDBC 实现，通过 Spring profile 切换（`jdbc` 生产 / `memory` test），内存实现保留给测试。`V010__user_role_apikey.sql` 已建 `t_user/t_role/t_permission` 表，本任务接表。

**最小可行结果**：生产 profile（`jdbc`）下，主链路数据重启后不丢；测试 profile（`memory`）行为不变；contract test 保证两实现行为一致。

## 2. 范围

### 本次实现（按模块，逐个落库）

| 模块 | 内存仓储 | 目标 JDBC 表 | 优先级 |
|---|---|---|---|
| auth | AuthService 内存 | `t_user`/`t_role`/`t_permission` | 高（M7-A F-07） |
| partner | Partner/Interface/Event 内存 | `t_partner`/`t_partner_interface`/`t_partner_event` | 高 |
| pipeline.ingest | IngestTask 内存 | `t_ingest_task` | 高 |
| pipeline.service | DataService 内存 | `t_data_service` | 高 |
| pipeline.catalog | Catalog 内存 | `t_data_catalog` | 中（P0-07 深化） |
| partner.consumer | Consumer 内存 | `t_consumer` | 中 |
| quality | InMemoryQualityRuleRepository 等 | `t_quality_rule`/`t_quality_check`/`t_quality_issue` | 中 |
| billing | InMemoryBillRepository/InMemoryBillingRuleRepository | `t_bill`/`t_billing_rule` | 中（P0-06 深化 bill_item） |
| billing.stats | InMemoryStatsSnapshotRepository | `t_stats_snapshot` | 低 |
| pipeline.storage | InMemoryDataAsset/Marketplace | `t_data_asset`/`t_marketplace` | 低 |
| common.audit | JdbcAuditLogRepository 已有原型 | `t_audit_log` | 已部分（P0-08 深化） |

- 每个仓储：新增 `JdbcXxxRepository` 实现，`@Profile("jdbc")`；内存实现加 `@Profile("memory")` 或 `@ConditionalOnProperty`。
- AuthService 拆出 `UserRepository`/`RoleRepository` 接口，JDBC 实现接 `t_user/t_role`（密码 BCrypt，权限码关联表）。
- 衔接 P0-01：旧 `U010__seed_data.sql` 已删除；本任务须用正确事实源重新提供开发/测试初始化数据（admin 用户、必要权限码、测试 API Key、示例 `t_data_catalog` 数据）。初始化可采用应用层 `DataInitializer` 或 repeatable migration，必须使用 `t_data_catalog` 正确表名、`permission_code VARCHAR(128)` 列宽，且不得写入生产真实密钥。
- 衔接 P0-01：`t_user.status` 为 `NOT NULL`；JDBC 写入用户时必须显式写入 `ACTIVE` 等状态，或补最小迁移设置默认值，避免 `AuthService.createUser` 落库失败。
- profile 配置：`application.yml` 默认 `jdbc`，`application-test.yml` 用 `memory`。

### 不做
- 不做调用日志事实源（P0-05）、账单明细（P0-06）、目录申请（P0-07）、审计防篡改（P0-08）的深化——本任务只做基础 CRUD 落库。
- 不删除内存实现（保留给 test）。

## 3. 必读输入

- `AGENTS.md`、`docs/database-design.md`、`docs/detailed-requirements-design.md`
- 现有 `InMemory*Repository`（8 个）、`JdbcAuditLogRepository`（原型参照）
- `db/migration/V010__user_role_apikey.sql`（用户/角色表结构）
- `reviews/claude-review.md`（M7-A F-07、M7-D D2-03）

## 4. 需要修改的模块

- `platform-auth`（AuthService 拆分 + UserRepository/RoleRepository JDBC）
- `platform-partner`（Partner/Consumer 仓储 JDBC）
- `platform-pipeline`（Ingest/DataService/Catalog 仓储 JDBC）
- `platform-quality`（Quality 仓储 JDBC）
- `platform-billing`（Bill/BillingRule/Stats 仓储 JDBC）
- `platform-common`（profile 配置、JdbcTemplate bean）
- 各模块 `application.yml` / `application-test.yml`

## 5. 数据库/API/前端影响

- **数据库**：复用 V001~V010 已建表；如发现字段缺失须补 V011 + U011（最小必要）。
- **API**：接口不变；`PartnerController.detail` 对不存在资源改抛 `BusinessException("PARTNER-404",...)` → 400/404（修复 M7-D D2-03）。
- **前端**：无。

## 6. 必须补充的测试

- **Repository contract test**：每个仓储接口写一套 contract test，`memory` 与 `jdbc` 两实现共用，验证 CRUD/唯一约束/事务/分页行为一致（流程文档 §7.2、§13）。
- **重启恢复测试**：`jdbc` profile 下写入数据 → 重启应用 → 数据仍在。
- **MockMvc 回归**：M7-D 的 69 个 MockMvc 测试在 `jdbc` profile 下仍全绿（用 Testcontainers MySQL 或 H2 MySQL 模式）。
- **AuthService 落表测试**：创建用户/角色 → 重启 → 仍可登录、权限码正确。

## 7. 验收命令

```bash
# memory profile（基线，行为不变）
mvn test -Dspring.profiles.active=memory

# jdbc profile（Testcontainers MySQL）
mvn test -Dspring.profiles.active=jdbc

# 重启恢复（手动或集成测试）
# 1. jdbc profile 启动，POST /api/v1/partners 新建
# 2. 重启应用
# 3. GET /api/v1/partners 仍返回该记录
```

## 8. M7 衔接

- **M7-A F-07**：用户/角色内存不落表 → 本任务接 `t_user/t_role`。
- **M7-D D2-03**：`PartnerController.detail` 抛 IllegalArgumentException → 改 BusinessException。
- **M7-D D2-05**：内存仓储遗留 → 本任务系统性落库。
- 落库后 M7 的 MockMvc 测试须在 `jdbc` profile 回归（为 P0-10 真实依赖 E2E 铺路）。

## 9. 风险与回滚

- **风险**：内存与 JDBC 行为不一致（分页、排序、唯一约束）。控制：contract test 双实现共用。
- **风险**：V010 表结构与业务字段不完全匹配。控制：缺字段补 V011 + U011，不改既有列。
- **回滚**：profile 切回 `memory` 即恢复 M7 行为；V011 有 U011 对等回滚。
- **敏感约束**：密码 BCrypt 存储；不写明文密钥；`spring.profiles.active` 由环境变量注入，不硬编码生产。

## 10. 完成判定

- 核心仓储（auth/partner/ingest/service/consumer/quality/billing）均有 JDBC 实现 + profile 切换。
- contract test `memory`/`jdbc` 双通过。
- 重启恢复测试通过（主链路数据不丢）。
- M7 MockMvc 在 `jdbc` profile 回归全绿。
- `PartnerController.detail` 异常类型修正。
- `mvn test` 全绿（两 profile）。
- 输出落库清单 + contract test 结果 + 重启恢复证据。
