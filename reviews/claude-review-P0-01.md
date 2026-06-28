# Claude Code 审查结果 — P0-01 Flyway 迁移修复

## 1. 审查对象

- 任务：P0-01 Flyway 迁移修复
- 分支：`ai/p0-flyway-fix`
- 任务单：`tasks/codex-task-P0-01-flyway.md`
- 审查日期：2026-06-28
- 改动范围：`db/migration/V001~V010`（修正）、`db/migration/U001~U010`（补/修正回滚）、删除 `db/migration/U010__seed_data.sql`
- 注：保留 `reviews/claude-review.md`（M7-A 历史），本审查独立成文。

## 2. Git 状态

所有改动均处于工作区未提交状态（无新 commit）。

```text
D  db/migration/U010__seed_data.sql              # 旧的"伪回滚+种子数据"脚本被删除
 M db/migration/V001__init_schema.sql            # 去除 CREATE TABLE IF NOT EXISTS
 M db/migration/V002__partner.sql                # 同上
 M db/migration/V003__ingest.sql                 # 同上
 M db/migration/V004__consumer.sql               # 同上
 M db/migration/V005__data_service.sql           # 同上
 M db/migration/V006__data_catalog.sql           # 同上
 M db/migration/V008__governance.sql             # 同上 + 文件末尾补换行
 M db/migration/V010__user_role_apikey.sql       # 重大重构：IF NOT EXISTS 重建 → ALTER 演进
?? db/migration/U001~U010__*.sql                 # 新增全部 V0xx 对应回滚脚本
```

未改动：`V007`、`V009`（V009 的 `IF NOT EXISTS` 已在 M7-A 修复）。
未触及业务代码、`.env`、密钥、证书、生产配置。无大型依赖引入。无无关文件删除。

## 3. 代码差异摘要

### 3.1 V001~V008：移除 `CREATE TABLE IF NOT EXISTS`
统一改为 `CREATE TABLE`。这是 Flyway 重复执行语义下更严谨的写法（迁移应幂等由版本号保证，而非 `IF NOT EXISTS` 掩盖偏差）。改动机械、范围可控。

### 3.2 V010：从"重建"重构为"演进"（核心改动）
旧 V010 用 `CREATE TABLE IF NOT EXISTS t_user/t_role ...` 重建身份表，但 V001 已创建 `t_user/t_role`，导致：
- `IF NOT EXISTS` 使 V010 对 `t_user/t_role` 实际为 no-op，`updated_at/created_at` 列永远不会被添加；
- 旧 V010 的 `t_role` 仅有 `name UNIQUE`、无 `code`，与 V001 的 `t_role(code, name)` 冲突；
- 使用 `AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP` 单库方言；
- `t_user_permission.permission_code VARCHAR(64)` 与 `t_permission.code VARCHAR(128)` 不一致。

新 V010 改为：
- `ALTER TABLE t_user ADD COLUMN updated_at`、`ALTER TABLE t_role ADD COLUMN created_at`（演进既有表）；
- 新建 `t_user_permission`/`t_role_permission`/`t_user_role`/`t_api_credential`，`permission_code VARCHAR(128)`，去掉外键（与库设 §5.1 "国产库跨字段外键兼容复杂时可由应用层校验"一致，利于 P0-02 国产库适配）；
- 用 `CREATE UNIQUE INDEX`/`CREATE INDEX` 替代内联约束，跨方言更稳。

### 3.3 U001~U010：补齐对等回滚
每个 U0xx 严格按 V0xx 的逆序 `DROP TABLE`/`DROP INDEX`/`DROP COLUMN`。U010 逆序：先 DROP 四张新表，再 `ALTER TABLE t_role DROP COLUMN created_at`、`ALTER TABLE t_user DROP COLUMN updated_at`，与 V010 完全对等。

### 3.4 删除 U010 旧种子数据
旧 `U010__seed_data.sql` 实为种子数据（admin 用户、demo `api-key`/`secret`、catalog DEMO），且本身有 bug（写 `t_data_catalog_item` 而非 `t_data_catalog`、`permission_code VARCHAR(64)`）。本次整体删除，未在迁移层提供替代种子。

## 4. 需求满足情况

依据 `tasks/codex-task-P0-01-flyway.md` §2 范围与 §10 完成判定：

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| R1 | 核查修正 V001~V010 方言冲突 | ✅ | 已移除 `IF NOT EXISTS`、`AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP` |
| R2 | 补齐/修正 U0xx 对等回滚 | ✅ | U001~U010 全部新增，逆序对等（已 MySQL 实测） |
| R3 | V010 与 database-design.md 一致 | ✅ | 实测列/索引与库设完全一致（见 §5） |
| R4 | 空库 `flyway migrate` 成功 | ⚠️ | SQL 在 MySQL 8.0 全量执行成功，但**未通过 Flyway 引擎验证**（无 flyway-maven-plugin，Codex 未提供 migrate/validate 输出） |
| R5 | 旧库（M6 基线）可升级 | ❌ | 未提供 M6 旧库升级证据；且改 V001~V009 内容会触发 Flyway checksum 偏差，未给出 repair 方案 |
| R6 | `flyway validate` 无偏差 | ❌ | 无证据；既有库因脚本内容变更必然 checksum 偏差 |
| R7 | 每个 V0xx 有对应 U0xx 且可对等执行 | ✅ | 全部配对，U010 已实测可回滚 |
| R8 | 结构与 database-design.md 一致 | ✅ | 人工逐表核对一致（建议补结构断言测试） |
| R9 | `mvn test` 全绿 | ⚠️ | 已跑 `platform-billing`（含 H2 迁移测试 V001~V009）通过；全量 `mvn test` 未由 Codex 提供证据，本次未全量回归 |
| R10 | 输出修改清单 + 迁移验证证据 | ❌ | 未见 Codex 的迁移验证命令输出/证据文件 |

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 最小可行结果（空库+旧库双迁移、validate 无偏差） | ⚠️ | 空库 SQL 成功；旧库/validate 未达标 |
| 仅改 `db/migration/V0xx`、`U0xx` | ✅ | 严格限定迁移脚本 |
| 不做国产库方言拆分（P0-02） | ✅ | 未越界 |
| 不改业务代码（P0-03） | ✅ | 未越界 |
| 不引入新表（除非库设要求） | ✅ | V010 新表均为库设定义的身份/凭证表 |
| 不写真实密钥到种子数据 | ✅ | 种子已删除；旧种子的 BCrypt hash 仅为开发占位 |
| 结构与库设一致 | ✅ | t_user(id,username,password_hash,status,created_at,updated_at)、t_role(id,code,name,created_at)、t_permission(id,code VARCHAR(128),name)、t_user_permission/role_permission(permission_code VARCHAR(128)+uk+idx)、t_user_role(uk+idx)、t_api_credential(全字段+idx) 均与 `docs/database-design.md` §5.1/§5.5 一致 |

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 是否超出 codex-task 范围 | 否 | 改动均在 §4 列出的迁移模块内 |
| 是否无关重构 | 否 | V010 重构是任务明确要求（"修正 V010，核对与库设一致"） |
| 是否修改敏感文件 | 否 | 未触及 `.env`/密钥/证书/生产配置 |
| 是否引入大型依赖 | 否 | 无 |
| 是否删除大量无关文件 | 否 | 仅删 1 个有 bug 的旧种子脚本 |
| 是否补/更新测试 | **部分** | 未新增迁移测试；既有 `h2RunsMigrationsThroughV009` 仅覆盖到 V009，**V010 无任何自动化覆盖** |

## 7. 测试检查

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `mvn -pl platform-billing -am test -Dtest=M5EndToEndIntegrationTest` | Claude 审查时运行 | ✅ 通过（3 tests, 0 fail） | 内含 `h2RunsMigrationsThroughV009`，在 H2(MySQL 模式) 直接执行 V001~V009 SQL，证明 `IF NOT EXISTS` 移除后语法仍可执行 |
| MySQL 8.0 容器空库应用 V001~V010 | Claude 审查时运行 | ✅ 通过 | 30 张表全部创建；t_user.updated_at / t_role.created_at（ALTER）生效；uk_user_perm/idx_user_perm_user 索引正确 |
| MySQL 8.0 容器执行 U010 回滚 | Claude 审查时运行 | ✅ 通过 | t_user_permission 等 4 表被 DROP，t_user.updated_at / t_role.created_at 被回退，与 V010 对等 |
| V010 专项迁移测试 | 未运行 | ❌ 缺失 | 既有迁移测试停在 V009，V010（最重改动）无覆盖 |
| `flyway:migrate` / `flyway:validate` | 未运行 | ❌ 缺失 | 无 `flyway-maven-plugin` 配置；测试 `application-test.yml` 排除 `FlywayAutoConfiguration`；Codex 未提供 Flyway 引擎级证据 |
| M6 旧库升级测试 | 未运行 | ❌ 缺失 | 任务 §6/§7 要求，未提供 |
| 结构一致性断言测试 | 未运行 | ❌ 缺失 | 任务 §6 要求，未提供（仅人工核对） |
| 全量 `mvn test` | 未运行 | ⚠️ | Codex 未提供；本次仅抽样回归 billing 模块 |

## 8. 安全与风险检查

1. **Flyway checksum 偏差（高风险，运营层面）**：V001~V009 内容变更（去 `IF NOT EXISTS`）与 V010 重写都会改变 Flyway 校验和。任何已记录这些版本的既有库（含开发库）执行 `flyway validate` 必然报偏差，`migrate` 也会被阻断。任务本身就要求改脚本，此风险不可避免，但 Codex 未给出 `flyway repair` 或重建库的处置说明。**必须在合入前明确：既有开发库需 `flyway repair` 更新校验和，或 `DROP DATABASE` 重建。**

2. **行尾符漂移（中风险）**：Git 提示 `LF will be replaced by CRLF`。Flyway checksum 基于文件字节计算，跨 OS（Windows 检出 CRLF / Linux CI 检出 LF）会导致同一脚本 checksum 不一致，进而 validate 偏差。建议加 `.gitattributes` 强制 `db/migration/*.sql` 为 LF。

3. **种子数据被删除，下游依赖未衔接（中风险）**：旧 U010 提供 admin 用户、demo `api-key/secret`、catalog DEMO。删除后：
   - 登录不受影响（`AuthService` 有 `fallbackUsers("admin","admin123")` 兜底）；
   - `M5EndToEndIntegrationTest` 自管凭证，不受影响；
   - 但 **P0-10 真实依赖 E2E** 及任何依赖种子 `api-key`/catalog DEMO 的环境会断。gap 文档将"种子表名修正"划归 **P0-03**，故此处删除合理，但构成对 P0-03 的隐式依赖，必须在 P0-03 以正确表名（`t_data_catalog`）、正确列宽（`permission_code VARCHAR(128)`）重新提供种子（建议改为应用层 `DataInitializer` 或 repeatable 迁移，而非放进 `U0xx` 回滚脚本）。

4. **`t_user.status NOT NULL` 无默认值（低风险，跨任务）**：库设 §5.1 注明 status "建议默认 ACTIVE"，V001 为 `VARCHAR(32) NOT NULL` 无默认；而 `AuthService.createUser` 的 `INSERT INTO t_user (username, password_hash)` 未写 status → 会触发 NOT NULL 违反。此为既有问题、非本次引入，属 P0-03 持久化范围。但与"迁移与库设一致"相关，建议 P0-03 同步处理（迁移加 `DEFAULT 'ACTIVE'` 或应用层写入）。

5. **V010 去除外键**：与库设"可由应用层校验"一致，利于 P0-02，属有意为之，非风险。

6. **无安全风险**：未写真实密钥；未连生产库；未改敏感配置。

## 9. 审查结论

### 需要返工

迁移脚本本身的修复方向正确、SQL 质量良好，且经 MySQL 8.0 + H2 双引擎实测可执行、可回滚、结构与库设一致。但**未满足任务 §6/§10 的测试与验证证据要求**：V010 无自动化覆盖、无 Flyway 引擎级 migrate/validate 证据、无旧库升级证据、无结构断言测试，且未提示 checksum 偏差处置。这些问题可修复，未触及"暂不通过"红线（无敏感文件改动、无大批量删除、无高风险依赖）。

## 10. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RW-1 | V010 无测试覆盖 | 扩展 `h2RunsMigrationsThroughV009` 至 V010（或新增迁移测试），断言 t_user.updated_at / t_role.created_at 存在、4 张新表与 uk/idx 索引建立 | 高 |
| RW-2 | 缺 Flyway 引擎级证据 | 配置 `flyway-maven-plugin`（或在某模块加 `@SpringBootTest` 启用 Flyway 的 Testcontainers/H2 迁移测试），输出 `migrate` 成功 + `validate` 无偏差证据 | 高 |
| RW-3 | 缺旧库升级证据 | 提供 M6 基线快照上执行 V008+ 的升级证据，或明确说明基线获取方式与结果 | 中 |
| RW-4 | 缺结构一致性断言 | 在迁移测试中对 t_user/t_role/t_permission/t_user_permission/t_role_permission/t_user_role/t_api_credential 关键列与索引做 `information_schema` 断言 | 中 |
| RW-5 | checksum 偏差未处置 | 在 `tasks/dev-progress.md` 或任务输出中写明：既有开发库需 `flyway repair` 或重建；给出命令 | 中 |
| RW-6 | 种子数据删除的下游依赖 | 在 P0-03 任务单中显式登记"以正确表名/列宽重新提供 admin/api-key/catalog 种子（应用层初始化或 repeatable 迁移）"，并标注为 P0-10 E2E 前置 | 中 |
| RW-7 | 行尾符漂移 | 新增 `.gitattributes`：`db/migration/*.sql text eol=lf`，避免跨 OS checksum 漂移 | 低 |
| RW-8 | t_user.status 无默认值 | 协调 P0-03：迁移补 `DEFAULT 'ACTIVE'` 或应用层写入 status，满足库设与 NOT NULL | 低 |

## 11. 建议提交信息

暂不建议提交。完成 RW-1~RW-5（高/中优先级）后，建议提交信息：

```text
fix(P0-01): harden Flyway migrations for MySQL 8.0 compatibility

- drop CREATE TABLE IF NOT EXISTS / AUTO_INCREMENT / ON UPDATE CURRENT_TIMESTAMP
  from V001-V008 for strict Flyway semantics and cross-dialect portability
- rework V010 from IF-NOT-EXISTS rebuild to ALTER-based evolution; align
  identity/credential tables with database-design.md (permission_code VARCHAR(128),
  app-layer validation instead of FKs)
- add U001-U010 symmetric rollback scripts (verified on MySQL 8.0)
- remove broken U010 seed data (wrong table name t_data_catalog_item); seed
  reinstatement tracked under P0-03
- cover V010 in migration smoke test; document flyway repair for existing dev DBs

Verified: V001-V010 apply clean on MySQL 8.0; U010 rollback symmetric;
h2RunsMigrationsThroughV009 green.
```

---

**审查结论**：需要返工（RW-1~RW-5 为必做项，RW-6~RW-8 为协调/建议项）。
**是否需要 Codex 返工**：是。
**是否建议提交**：暂不提交，待 RW-1~RW-5 完成并复验后再提交。
