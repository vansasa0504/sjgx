# Codex 执行任务 - P0-01：Flyway 迁移修复

> 阶段：P0（上线阻断修复）
> 任务编号：P0-01
> 分支建议：`ai/p0-flyway-fix`
> 依据：`docs/development-process-workflow.md` §3.1 P0-01、§5.2 数据库开发规则、`docs/database-design.md`
> 日期：2026-06-27

---

## 1. 背景与目标

当前 `db/migration/` 已有 `V001~V010` + `U010`，但 M7-A 期间暴露多处兼容问题（如 V009 曾含 `ADD COLUMN IF NOT EXISTS`/`CREATE INDEX IF NOT EXISTS` MySQL 8.x 不支持，已临时修复）。需系统性核查全部迁移脚本，确保**空库可执行**、**旧库（M6 版本）可升级**、**目标结构与 `docs/database-design.md` 一致**，且每个 migration 有对应 rollback。

**最小可行结果**：在空库和 M6 旧库两个基线上，`flyway migrate` 全部成功，`flyway validate` 无偏差，结构与库设一致。

## 2. 范围

### 本次实现
- 核查并修正 `V001~V010` 中的方言冲突、缺 rollback、与库设不一致项。
- 补齐/修正 `U010` 及各 `U0xx` 回滚脚本，确保可对等回滚。
- 移除/替换单库方言（`AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`IF NOT EXISTS` 等通用脚本中的用法）。
- 修正 `V010__user_role_apikey.sql`（M7-A 为落表预留，需核对与 `database-design.md` 一致）。

### 不做
- 不做国产库方言拆分（P0-02）。
- 不改业务代码（P0-03）。
- 不引入新表（除非库设要求且经 Claude Code 确认）。

## 3. 必读输入

- `AGENTS.md`
- `docs/database-design.md`（目标结构权威）
- `docs/implementation-gap-and-test-plan.md`（已知差距）
- `db/migration/V001~V010`、`db/migration/U010`（现有脚本）
- `reviews/claude-review.md`（M7-A §4.3 关键修复记录 V009 兼容问题）

## 4. 需要修改的模块

- `db/migration/V0xx__*.sql`（修正）
- `db/migration/U0xx__*.sql`（补/修正 rollback）

## 5. 数据库/API/前端影响

- **数据库**：直接改动迁移脚本，影响所有环境的 schema。
- **API**：无。
- **前端**：无。

## 6. 必须补充的测试

- 空库迁移测试：`flyway:migrate` 干净库 → 成功 → `flyway:validate` 无偏差。
- 旧库升级测试：在 M6 基线快照上执行 V008+ → 成功。
- rollback 测试：`flyway:undo`（社区版不支持则手动 `U0xx` 验证）每条回滚可对等执行。
- 结构一致性断言：迁移后 `information_schema` 关键表/字段/索引与 `database-design.md` 比对。

> 测试方式：可用 Testcontainers MySQL 8.0，或 `docker exec sjgx-mysql-1` 双库脚本（参考 `tasks/dev-progress.md` §5.4）。

## 7. 验收命令

```bash
# 1. 空库迁移
docker exec sjgx-mysql-1 mysql -uroot -proot -e "DROP DATABASE IF EXISTS sjgx; CREATE DATABASE sjgx;"
mvn -pl platform-common flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/sjgx -Dflyway.user=root -Dflyway.password=root -Dflyway.locations=filesystem:db/migration
mvn -pl platform-common flyway:validate -Dflyway.url=... # 无偏差

# 2. 旧库升级（用 M6 快照恢复后执行同样 migrate）

# 3. 全量回归
mvn test
```

## 8. M7 衔接

- M7-A §4.3 已临时修复 V009 的 `IF NOT EXISTS` 问题，本任务做系统性核查，确保 V001~V010 全部无类似隐患。
- M7 期间启动均禁用 flyway（`--spring.flyway.enabled=false`，schema 手动迁移），本任务修复后应能启用 flyway 自动迁移（为 P0-10 真实依赖 E2E 铺路）。

## 9. 风险与回滚

- **风险**：迁移修正可能破坏已有开发库数据。控制：先在 Testcontainers/临时库验证，保留 `U0xx` 对等回滚。
- **回滚**：`U0xx` 脚本逐条回退；开发库可 `DROP DATABASE` 重建。
- **敏感约束**：不修改 `.env`、不写真实密钥到种子数据（`U010__seed_data.sql` 若含密码须为 BCrypt hash 且仅开发用）。

## 10. 完成判定

- 空库 + 旧库双迁移成功。
- `flyway:validate` 无偏差。
- 每个 V0xx 有对应 U0xx 且可对等执行。
- 结构与 `database-design.md` 一致。
- `mvn test` 全绿。
- 输出修改清单 + 迁移验证证据（命令输出）。
