# Codex 执行任务 - P0-02：国产库兼容

> 阶段：P0（上线阻断修复）
> 任务编号：P0-02
> 分支建议：`ai/p0-db-compat`
> 依据：`docs/development-process-workflow.md` §3.1 P0-02、§5.2、`docs/database-design.md`、`national-db-compat-report.md`
> 前置：P0-01 通过
> 日期：2026-06-27

---

## 1. 背景与目标

生产目标库为达梦/OceanBase 双适配（开发用 MySQL 8.0）。M5/M6 已交付 `national-db-compat-report.md` 与初步兼容方案，但未做三类库实际迁移验证。本任务确保同一套迁移脚本（或方言拆分脚本）在 MySQL 8.0、达梦 8、OceanBase 三个库上均能迁移成功且行为一致。

**最小可行结果**：三类库空库迁移成功，主链路 CRUD 在三库上行为一致（Repository contract test 通过）。

## 2. 范围

### 本次实现
- 核查 `V001~V010` 中 MySQL 专有语法，制定方言策略：
  - 策略 A：通用 ANSI SQL（优先）；
  - 策略 B：方言拆分（`db/migration/mysql/`、`db/migration/dm/`、`db/migration/oceanbase/` + flyway `locations` profile 切换）。
- 替换/拆分 `AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`LIMIT`、`ON DUPLICATE KEY UPDATE`、`JSON_*` 等差异点。
- MyBatis-Plus 分页方言配置（已用，需核对三库 dialect）。
- 补国产库迁移验证测试。

### 不做
- 不做性能优化（P2-01）。
- 不改业务逻辑（仅 SQL/方言层）。

## 3. 必读输入

- `AGENTS.md`
- `docs/database-design.md`
- `national-db-compat-report.md`（M5/M6 产出，已知差异清单）
- `docs/implementation-gap-and-test-plan.md`
- `db/migration/V001~V010`

## 4. 需要修改的模块

- `db/migration/`（方言拆分或 ANSI 化）
- `platform-common`（如需方言相关的 DbAdapter/分页配置）
- `pom.xml`（如需达梦/OceanBase JDBC 驱动依赖，须确认许可证，不得引入盗版驱动）

## 5. 数据库/API/前端影响

- **数据库**：迁移脚本方言化，影响三库 schema 一致性。
- **API**：无（分页方言对接口透明）。
- **前端**：无。

## 6. 必须补充的测试

- MySQL 8.0 迁移 + contract test（基线，P0-01 已有）。
- 达梦 8 迁移 + contract test（Testcontainers 或docker镜像，若无合法镜像则用 H2 兼容模式模拟 + 人工验证清单）。
- OceanBase MySQL 模式迁移 + contract test（OceanBase 兼容 MySQL 协议，可复用 MySQL 测试 + 专属性差异点人工核对）。
- 三库行为一致性断言：同一 CRUD 序列在三库结果相同。

> 若达梦/OceanBase 镜像不可用，须在完成报告中明确说明"未实测部分"并列出待上线验证清单（流程文档 §7.3 测试证据要求）。

## 7. 验收命令

```bash
# MySQL（基线）
mvn -pl platform-common flyway:migrate -Dflyway.url=jdbc:mysql://...
# 达梦 / OceanBase（按策略）
mvn -pl platform-common flyway:migrate -Dflyway.url=jdbc:dm://... -Dflyway.locations=filesystem:db/migration/dm
# 全量回归（MySQL）
mvn test
```

## 8. M7 衔接

- M7-A 审查 S-04 提到"用户/角色内存实现"，落表依赖本任务的国产库兼容（P0-03 将接 V010 表，须三库可迁移）。
- M7 期间未做国产库实测，本任务补齐。

## 9. 风险与回滚

- **风险**：达梦/OceanBase 驱动许可证、镜像不可用、JSON/时间类型差异。控制：优先 ANSI SQL；驱动缺失时用兼容模式 + 人工清单。
- **回滚**：方言拆分不影响 MySQL 基线（`locations` 默认仍指向通用目录）；可恢复单目录脚本。
- **敏感约束**：不引入盗版/未授权 JDBC 驱动；驱动坐标须在完成报告中标注许可证。

## 10. 完成判定

- 三类库迁移策略确定并落地（ANSI 化或拆分）。
- MySQL + OceanBase 实测迁移通过；达梦若无镜像则列出待验证清单。
- Repository contract test 在已实测库上通过。
- `mvn test` 全绿。
- 输出方言差异处理清单 + 三库迁移证据。
