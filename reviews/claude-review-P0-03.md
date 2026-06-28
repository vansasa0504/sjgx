# Claude Code 审查结果 — P0-03 核心 Repository 落库

## 1. 审查对象

- 任务：P0-03 核心 Repository 落库
- 分支：`ai/p0-repository-persistence`
- 任务单：`tasks/codex-task-P0-03-repository-persistence.md`
- 审查日期：2026-06-28
- 前置：P0-01/P0-02 已通过并合入 master
- 改动范围：6 模块 service/application 落库改造 + 4 个新增 JdbcRepository + 1 个 contract test + V011/U011 迁移

## 2. Git 状态

改动**全部未提交**（工作区）：

```text
 M platform-auth/AuthService.java
 M platform-billing/BillingApplication.java
 M platform-partner/PartnerApplication.java
 M platform-partner/PartnerService.java
 M platform-partner/consumer/ConsumerService.java
 M platform-pipeline/catalog/CatalogService.java
 M platform-pipeline/ingest/IngestService.java
 M platform-pipeline/ingest/PipelineApplication.java
 M platform-pipeline/service/DataServiceDefinition.java
 M platform-pipeline/service/DataServiceManager.java
 M platform-quality/QualityApplication.java
?? platform-billing/bill/JdbcBillRepository.java
?? platform-billing/rule/JdbcBillingRuleRepository.java
?? platform-billing/stats/JdbcStatsSnapshotRepository.java
?? platform-billing/it/RepositoryContractTest.java
?? platform-quality/rule/JdbcQualityRuleRepository.java
?? db/migration/V011__add_package_allowance.sql
?? db/migration-dm/V011__add_package_allowance.sql
?? db/migration/U011__billing_rule_package_allowance.sql
?? db/rollback/U011__billing_rule_package_allowance.sql
```

未触及：`.env`、密钥、证书、生产配置。未引入新依赖。无大批量删除。

## 3. 代码差异摘要

### 3.1 落库机制（与任务单要求的偏差，重点）
任务单 §2 明确要求：**`@Profile("jdbc")`/`@Profile("memory")` + `application.yml` 默认 jdbc、`application-test.yml` 用 memory**。

实际实现采用**不同机制**：`@Autowired(required=false) JdbcTemplate` + `jdbcTemplate != null` 三元判断（`useDb()`）。测试 `application-test.yml` 排除 `DataSourceAutoConfiguration` → 无 JdbcTemplate bean → 回退内存。

- billing：`BillingApplication` 的 `@Bean` 工厂方法用 `jdbcTemplate != null ? new JdbcXxx : new InMemoryXxx`，干净。
- auth/partner/ingest/service/consumer/catalog：**Service 内部双写**（既写内存 map 又写 DB，读优先 DB）。这是与任务要求"JDBC 实现与内存实现二选一"的显著架构偏差。

### 3.2 落库覆盖（任务 §2 表逐项）
| 模块 | 落库 | 实现方式 | contract test |
|---|---|---|---|
| auth | ✅ t_user/t_role/t_permission | AuthService 内 JdbcTemplate，未拆 UserRepository/RoleRepository 接口 | ❌ 无 |
| partner | ✅ t_partner/t_partner_interface/t_partner_event | PartnerService 双写 | ❌ 无 |
| ingest | ✅ t_ingest_task | IngestService 双写 | ❌ 无 |
| service | ✅ t_data_service | DataServiceManager 双写 | ❌ 无 |
| catalog | ✅ t_data_catalog | CatalogService 双写 | ❌ 无 |
| consumer | ✅ t_consumer | ConsumerService 双写 | ❌ 无 |
| quality | ✅ t_quality_rule | 新增 JdbcQualityRuleRepository | ❌ 无 |
| billing bill/rule/stats | ✅ | 新增 JdbcBillRepository/JdbcBillingRuleRepository/JdbcStatsSnapshotRepository | ✅ 仅这 3 个 |
| audit | ✅ | 复用 JdbcAuditLogRepository | — |

**只有 billing 3 个仓储有 contract test**；auth/partner/ingest/service/consumer/quality 共 7 个模块无 contract test。

### 3.3 V011/U011 迁移
- V011：`ALTER TABLE t_billing_rule ADD COLUMN package_allowance BIGINT NOT NULL DEFAULT 0`（通用 + dm 对等）。
- U011 对等回滚 `DROP COLUMN`。
- **孤岛再现**：`db/migration/U011` 与 `db/rollback/U011` 重复（P0-01 D-2 / P0-02 RW-5 未解决的同一问题）。
- `package_allowance` 支持 BY_PACKAGE 计费模型，任务 §2 说"不做 bill_item 深化（P0-06）"，加此列属边界扩展，但最小且可回滚，可接受。

### 3.4 衔接 P0-01
- ✅ `t_user.status` 显式写 `ACTIVE`（RW-8 落地）。
- ✅ `permission_code` 用 `VARCHAR(128)` 列。
- ✅ 密码 BCrypt（`PASSWORD_ENCODER.encode`）。
- ⚠️ 种子初始化：`AuthService.bootstrapAdmin()` 在 `jdbcTemplate != null` 时插入 t_permission 全权限码 + admin 用户（密码 `AUTH_BOOTSTRAP_ADMIN_PASSWORD` 环境变量，默认 `admin123`），未提供 catalog/demo API Key 种子。

### 3.5 D2-03 异常修复
✅ `PartnerController.detail` 改 `BusinessException("PARTNER-404", ...)`。

## 4. 需求满足情况

依据 `tasks/codex-task-P0-03-repository-persistence.md` §2 与 §10：

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| R1 | 核心仓储均有 JDBC 实现 + profile 切换 | ⚠️ | JDBC 实现齐备；但**未用 @Profile 切换**，改用 JdbcTemplate 注入判断（偏离任务要求，机制可行但非约定） |
| R2 | AuthService 拆出 UserRepository/RoleRepository 接口 | ❌ | 未拆分，直接在 AuthService 内 JdbcTemplate |
| R3 | profile 配置 application.yml 默认 jdbc / application-test.yml memory | ❌ | 无 `spring.profiles.active` 配置；靠排除 DataSourceAutoConfiguration 实现 memory 回退 |
| R4 | 每个仓储 contract test 双实现共用 | ❌ | 仅 billing 3 个；7 个模块缺失 |
| R5 | 重启恢复测试 | ❌ | 未实现、无证据 |
| R6 | M7 MockMvc 在 jdbc profile 回归全绿 | ❌ | 现有 MockMvc 测试均走 memory 路径（无 JdbcTemplate）；jdbc 路径无回归 |
| R7 | PartnerController.detail 异常修正 | ✅ | 已改 BusinessException |
| R8 | mvn test 全绿（两 profile） | ⚠️ | memory 路径全绿（billing30/auth25/partner23）；jdbc profile 未单独跑 |
| R9 | 输出落库清单 + contract test 结果 + 重启恢复证据 | ❌ | dev-progress 无 P0-03 章节，无任何证据记录 |
| R10 | 密码 BCrypt、不写明文密钥 | ⚠️ | BCrypt ✅；但 bootstrapAdmin 默认 `admin123` 明文环境变量兜底，生产风险 |
| R11 | 衔接 P0-01 种子数据正确 | ⚠️ | admin/t_permission 种子有；catalog/demo API Key 种子缺失（P0-10 E2E 前置） |

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 最小可行结果（生产 jdbc 重启不丢 / 测试 memory 不变 / contract 一致） | ⚠️ | 重启不丢：DB 读取优先，理论可行但无测试证明；contract 一致：仅 billing |
| 不删除内存实现 | ✅ | 保留 |
| 不做 P0-05/06/07/08 深化 | ✅ | 仅基础 CRUD |
| 缺字段补 V011+U011 不改既有列 | ✅ | V011/U011 对等 |
| 不写明文密钥 | ⚠️ | bootstrapAdmin 默认 admin123 |

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 是否超出 codex-task 范围 | 否 | 改动均在落库范围内 |
| 是否无关重构 | 否 | |
| 是否修改敏感文件 | 否 | |
| 是否引入大型依赖 | 否 | |
| 是否补/更新测试 | **部分** | 仅 billing contract test；其余模块无 |
| 是否拆分 AuthService 接口 | ❌ | 任务明确要求未做 |

## 7. 测试检查

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `mvn -pl platform-billing,platform-auth,platform-partner -am test` | Claude 审查时运行 | ✅ 全绿 | billing30/auth25/partner23，含新增 RepositoryContractTest 3/3；**但均走 memory 路径**（测试排除 DataSourceAutoConfiguration） |
| auth/partner jdbc 路径测试 | 未运行 | ❌ 缺失 | AuthServiceTest/PartnerServiceTest 无 JdbcTemplate/Flyway，jdbc 落库代码无覆盖 |
| jdbc profile MockMvc 回归 | 未运行 | ❌ 缺失 | 任务 §6 要求 |
| 重启恢复测试 | 未运行 | ❌ 缺失 | 任务 §6 要求 |
| 全量 mvn test（两 profile） | 未运行 | ⚠️ | 仅 memory 路径抽样 |

## 8. 安全与风险检查

1. **`nextId` 用 `MAX(id)+1`（高风险）**：auth/partner/ingest/service/catalog/consumer 全部用 `SELECT COALESCE(MAX(id),0)+1` 生成主键。**并发下会主键冲突**（两个并发请求读到相同 MAX 值）。生产环境多实例/多线程下必现。应改用数据库序列、`AUTO_INCREMENT`/`IDENTITY`（但与国产库兼容需评估）、或应用层发号器。这是上线阻断级缺陷。
2. **`bootstrapAdmin` 默认 admin123（高风险）**：`System.getenv().getOrDefault("AUTH_BOOTSTRAP_ADMIN_PASSWORD", "admin123")`。若生产未设环境变量，将以 admin/admin123 初始化管理员。且 `catch(Exception ignored)` 吞掉所有异常，初始化失败静默无感知。应：无环境变量时拒绝启动或强制改密，且不吞异常。
3. **auth/partner 等 jdbc 路径无测试（高风险）**：落库 SQL 未经验证即可能上线。如 IngestService INSERT 与 UPDATE 列不对称（INSERT 无 qualityRules/sync_mode，UPDATE 有），可能字段缺失。
4. **双写一致性（中风险）**：Service 同时写内存 map 和 DB。重启后内存 map 空、DB 有数据，依赖"读优先 DB"恢复；但任何遗漏 `useDb()` 分支的读路径会读到空 map。 PartnerService 已统一走 DB 读，但需逐一核查所有读路径。
5. **达梦 CLOB 写入（中风险）**：`mapping_config`/`rule_config`/`payload`/`detail` 为 CLOB，H2 memory 测试不覆盖达梦真实 CLOB 语义（P0-02 已列清单，P0-03 未验证）。
6. **`partner_code="PARTNER-"+id`（中风险）**：基于 MAX(id)+1 生成，并发下可能重复，违反 UNIQUE。
7. **无安全红线违反**：未改 .env/密钥/证书；未连生产库；BCrypt 存储正确。

## 9. 审查结论

### 需要返工

落库工作量大、方向正确（7 模块 + 4 JdbcRepository + V011 + D2-03 修复 + billing contract test），现有 memory 测试全绿。但存在**上线阻断级缺陷**与**任务要求大面积未达成**：
- `nextId` 并发不安全（阻断级）；
- bootstrapAdmin 默认弱密码 + 吞异常（阻断级安全风险）；
- 任务要求的 `@Profile` 切换、AuthService 接口拆分、各模块 contract test、jdbc profile MockMvc 回归、重启恢复测试**均未实现**；
- auth/partner 等 7 模块 jdbc 路径零测试覆盖；
- 无任何证据文档（dev-progress 无 P0-03 章节）。

未触及"暂不通过"红线（无敏感文件、无盗版依赖、无大批量删除），但缺陷严重，必须返工。

## 10. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RW-1 | nextId 并发不安全 | 替换 `MAX(id)+1`：评估用 `AUTO_INCREMENT`/`IDENTITY`（核对达梦/OB 兼容）或应用层发号器（雪花/UUID）；至少保证并发插入不冲突 | 阻断 |
| RW-2 | bootstrapAdmin 默认弱密码 + 吞异常 | 移除 `admin123` 默认值：未设环境变量时拒绝启动或强制首次改密；`bootstrapAdmin` 不吞异常，失败应日志告警 | 阻断 |
| RW-3 | auth/partner/ingest/service/consumer/quality/quality jdbc 路径零测试 | 为各模块 jdbc 落库路径补集成测试（H2 MySQL 模式 + Flyway 迁移，参考 RepositoryContractTest 模式），覆盖 CRUD/查询/状态流转 | 高 |
| RW-4 | AuthService 未拆 UserRepository/RoleRepository 接口 | 按任务 §2 拆分接口，JdbcUserRepository/JdbcRoleRepository 实现，便于 contract test 与后续维护 | 高 |
| RW-5 | 缺重启恢复测试 | 加集成测试：jdbc 路径写入 → 重建上下文/重查 DB → 数据仍在 | 高 |
| RW-6 | 缺 jdbc profile MockMvc 回归 | 任务 §6 要求 M7 MockMvc 在 jdbc profile 回归；至少补 1 个模块的 jdbc MockMvc 集成测试 | 中 |
| RW-7 | 双写一致性核查 | 逐一核查 auth/partner/ingest/service/consumer 所有读路径均走 DB（useDb 分支），消除内存 map 与 DB 不一致隐患 | 中 |
| RW-8 | IngestService INSERT/UPDATE 列不对称 | 核对 t_ingest_task 全字段，INSERT 应包含 sync_mode/schedule_cron/mapping_config/rule_config/quality_rules（或确认字段可空），与 UPDATE 对齐 | 中 |
| RW-9 | profile 切换机制 | 要么补 `@Profile`/`spring.profiles.active` 满足任务约定，要么在 dev-progress 说明用 JdbcTemplate 注入判断替代 profile 的理由与等价性 | 中 |
| RW-10 | 缺证据文档 | dev-progress 新增 P0-03 章节：落库清单、contract test 结果、重启恢复证据、未实测说明 | 中 |
| RW-11 | U011 孤岛 | 删除 `db/rollback/U011` 或统一 U0xx 存放位置（与 P0-01 D-2 / P0-02 RW-5 一并定夺） | 低 |
| RW-12 | catalog/demo API Key 种子 | 衔接 P0-10：补 catalog 示例与测试 API Key 种子（应用层 DataInitializer，正确表名/列宽） | 低 |

## 11. 建议提交信息

暂不建议提交。完成 RW-1~RW-5（阻断/高）后复验，再考虑提交。建议提交信息：

```text
feat(P0-03): persist core repositories to JDBC with memory fallback

- add JdbcBill/BillingRule/StatsSnapshot/QualityRule repositories;
  wire BillingApplication beans to pick jdbc vs in-memory by JdbcTemplate
  presence
- persist auth/partner/ingest/data-service/catalog/consumer via
  JdbcTemplate in existing services (DB-read priority for restart recovery)
- add V011 package_allowance to t_billing_rule (+U011 rollback, dm parity)
- fix PartnerController.detail to throw BusinessException PARTNER-404
- bootstrap admin user/permissions into t_user/t_permission (BCrypt)
- add RepositoryContractTest for billing jdbc vs memory parity

TODO before merge: concurrent-safe ids, bootstrap password hardening,
jdbc-path tests for auth/partner/ingest/service/consumer/quality,
restart-recovery test, AuthService User/RoleRepository split.
```

---

**审查结论**：需要返工（RW-1~RW-2 为阻断级，RW-3~RW-5 为高，RW-6~RW-10 为中，RW-11~RW-12 为低）。
**是否需要 Codex 返工**：是。
**是否建议提交**：暂不提交，待 RW-1~RW-5 完成并复验后再议。
