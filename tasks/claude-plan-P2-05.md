# P2-05 第一性原理开发计划 — 备份恢复

> 阶段：P2（生产强化）第五任务（末任务）
> 依据：`docs/development-process-workflow.md` §3.3、`tasks/phase-task-checklist.md` §4、`docs/requirements.md` §3.2/§3.3（NFR-A03/A04/S02）、`delivery/ops-manual.md`
> 前置：P2-04 已合入 master（`0d466685`）；P0-08（审计防篡改）、P2-01（分区归档）已满足
> 日期：2026-06-30
> 分支：`ai/p2-backup-restore`（建议）

---

## 1. 需求来源

### 1.1 任务口径

| 项 | 内容 |
|---|---|
| 编号 | P2-05 |
| 主题 | 备份恢复 |
| 依赖 | P0-08（审计防篡改） |
| 涉及模块 | `delivery/`、`k8s/`、`platform-common`（审计校验）、`platform-pipeline`（生命周期/销毁证明） |
| 输出 | 备份、恢复、审计校验方案 |
| 通过标准 | **数据可恢复，审计链可校验** |

### 1.2 触发事实（调研发现）

1. **"审计链可校验"已基本达成**：`JdbcAuditLogRepository.verify()` 分批哈希链校验（P0-08+P2-01）+ `GET /api/v1/stats/audit/verify` 端点 + 4 个 verify 单测（含篡改检测、跨批 2505 条）。
2. **"数据可恢复"基本未达成**：仓库内**无任何备份/恢复脚本**（无 mysqldump/snapshot/restore），ops-manual 仅有文字描述"按机构数据库方案"，巡检列"备份恢复演练"但无脚本。
3. **无"备份→恢复→verify 审计链"端到端闭环测试**——这是 P2-05 的核心可补缺口。
4. **销毁证明 proof_hash 设计有实现无**：`t_lifecycle_record` 建表仅 4 列（id/asset_code/action/operated_at），缺 `operator/reason/proof_hash/object_key`；docs 多处提到但生产代码无实现。
5. **P2-01 归档表同库**：`t_*_archive` 在同一 DB，非异地备份，不构成灾难恢复备份。
6. **P2-03 故障演练是服务级**（故障切换 RPO/RTO），P2-05 是数据级（备份恢复+完整性校验），两者互补不可替代。
7. **审计 3 年留存策略无执行证据**：P2-01 `@Scheduled` 默认 `enabled=false`，DB 层 REVOKE 未落地。

### 1.3 P2-05 vs P2-03 区别

| 维度 | P2-03 故障演练 | P2-05 备份恢复 |
|---|---|---|
| 层级 | 服务级（故障切换） | 数据级（备份恢复） |
| 关注 | 服务不崩溃、可切换、RPO/RTO 时延 | 数据丢失后可从备份恢复、恢复后审计链完整 |
| 产物 | chaos 脚本（node-down/db-failover/...） | 备份恢复脚本 + 恢复后校验 |

---

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？

招采验收要求"数据可恢复，审计链可校验"+ NFR-A03 数据零丢失 + NFR-S02 审计≥3年留存。**本质：证明数据丢失后可从备份恢复，且恢复后审计链完整可验证，合规留存/销毁有据可查**。

### 2.2 核心矛盾

真实备份依赖外部系统（机构数据库方案、MinIO 桶版本、异地存储），开发环境无法完全达成。但"备份→恢复→审计链校验"的**闭环逻辑**可在开发环境用 Testcontainers/本地 DB 真实验证；销毁证明 proof_hash 可补全实现；备份恢复脚本可编写（待生产环境执行）。

### 2.3 最小可行结果

1. **备份恢复脚本**：DB 逻辑备份（mysqldump 或 SQL 导出）+ 恢复脚本 + MinIO 桶版本策略脚本，参数化、可重复执行（待生产环境）。
2. **恢复后审计链校验闭环**：Testcontainers/本地 DB "备份→破坏→恢复→verify 审计链 intact"端到端测试（开发环境可验证）。
3. **销毁证明补全**：`t_lifecycle_record` 加 `operator/reason/proof_hash/object_key` + 销毁证明生成与校验。
4. **备份恢复方案文档**：DB/Redis/MinIO 备份恢复策略 + 演练 runbook + 诚实标注生产待外部系统。
5. **诚实标注**：开发层闭环已验证 / 生产备份待外部系统 / 审计留存待生产开启。

### 2.4 系统必须接收哪些输入？

- 备份恢复脚本配置（DB 连接、MinIO 桶、备份路径）。
- 恢复后校验用例（审计链 verify、数据计数）。
- 销毁证明配置（operator、reason、proof_hash 算法）。
- 备份目标环境（开发本地 / 生产外部系统）。

### 2.5 系统必须产生哪些输出？

- 备份恢复脚本套件（DB dump/restore + MinIO 版本）。
- 恢复后审计链校验测试（端到端闭环）。
- 销毁证明（proof_hash）补全 + 测试。
- 备份恢复方案文档 + runbook。
- 诚实分层报告（开发闭环 / 生产待外部）。

### 2.6 从输入到输出不可省略的处理过程

1. **备份脚本**：`delivery/backup-restore/backup-db.sh`（mysqldump 逻辑备份关键表）+ `backup-minio.sh`（mc mirror/版本）。
2. **恢复脚本**：`restore-db.sh`（从 dump 恢复）+ `restore-minio.sh`。
3. **恢复后校验闭环**：Testcontainers 起 DB → 灌数据 + 审计哈希链 → 备份 → 破坏/清库 → 恢复 → `verify()` 审计链 intact + 数据计数一致。
4. **销毁证明补全**：`t_lifecycle_record` 加字段（V023 迁移）+ `DataLifecycleManager` 生成 proof_hash + 校验。
5. **方案文档**：`delivery/backup-restore/backup-restore-plan.md` + `runbook.md`。
6. **报告**：开发闭环验证 + 生产待外部 + NFR-A/S 对照。

### 2.7 哪些是核心能力？

- 备份恢复脚本（DB + MinIO）。
- 恢复后审计链校验闭环测试（开发可验证）。
- 销毁证明 proof_hash 补全。

### 2.8 哪些是增强能力？

- 真实异地灾难恢复（待生产外部系统）。
- Redis 数据重建脚本（降级已有，重建待补）。
- 审计 3 年留存定时归档生产开启（P2-01 已就绪，待生产 enabled=true）。
- DB 层 REVOKE UPDATE/DELETE 落地（待生产 runbook）。

### 2.9 当前代码库最小改动路径

- **新增 `delivery/backup-restore/`**：backup-db.sh、restore-db.sh、backup-minio.sh、restore-minio.sh、runbook.md、backup-restore-plan.md。
- **新增恢复后审计链校验测试**：`platform-common` 或 `platform-billing` Testcontainers IT，"备份→恢复→verify"闭环。
- **V023 迁移**：`t_lifecycle_record` 加 `operator/reason/proof_hash/object_key`（MySQL + 达梦）。
- **改 `DataLifecycleManager`**：生成 proof_hash（销毁证据哈希）+ 写入 t_lifecycle_record。
- **新增销毁证明测试**：proof_hash 生成与校验。
- **新增 `delivery/p2-05-report.md`**：开发闭环 + 生产待外部 + NFR 对照。

### 2.10 如何测试？

- 备份恢复脚本：`bash -n` 语法检查；本地 DB 冒烟（dump→restore→计数）。
- 恢复后审计链校验：Testcontainers 灌审计数据→备份→清库→恢复→`verify()` intact + 计数一致。
- 销毁证明：proof_hash 生成→校验一致性。
- 回归：`mvn test` 全量（V023 迁移 + DataLifecycleManager 改动）。

### 2.11 如何验收？

- 备份恢复脚本套件完整，`bash -n` 通过，本地冒烟成功。
- 恢复后审计链校验闭环测试通过（恢复后 verify intact）。
- 销毁证明 proof_hash 补全 + 测试通过。
- 方案文档 + runbook 完整。
- 报告诚实分层：开发闭环已验证 / 生产备份待外部系统 / 审计留存待生产开启。
- `mvn test` 全绿，无回归。

### 2.12 如何避免过度设计？

- **不在开发环境做真实异地灾备**：用本地 DB/Testcontainers 验证闭环逻辑，异地备份待生产。
- **不引入专业备份工具**（Velero/Bacula）：用 mysqldump + mc 基础工具，专业方案待生产。
- **不实现 Redis 完整重建**：降级已有（P2-03），重建脚本标注待补。
- **不强制审计 3 年留存生产开启**：P2-01 已就绪机制，标注待生产 enabled=true。
- **不补全 t_raw_data 读写路径**：独立缺口，本任务只做备份恢复 + 销毁证明。

---

## 3. 功能拆解

| 编号 | 任务 | 模块 | 说明 |
|---|---|---|---|
| F-1 | DB 备份恢复脚本 | delivery/backup-restore | mysqldump 关键表 + restore + 语法检查 |
| F-2 | MinIO 备份恢复脚本 | delivery/backup-restore | mc mirror/版本策略 + restore |
| F-3 | 恢复后审计链校验闭环测试 | platform-common/billing IT | Testcontainers 备份→恢复→verify intact |
| F-4 | 销毁证明 proof_hash 补全 | db/migration V023 + platform-pipeline | t_lifecycle_record 加字段 + DataLifecycleManager 生成 proof_hash |
| F-5 | 销毁证明测试 | platform-pipeline test | proof_hash 生成与校验 |
| F-6 | 备份恢复方案 + runbook | delivery/backup-restore | DB/Redis/MinIO 策略 + 演练手册 |
| F-7 | P2-05 报告 | delivery | 开发闭环 + 生产待外部 + NFR-A/S 对照 |

---

## 4. 影响模块

| 模块 | 改动类型 | 风险 |
|---|---|---|
| `delivery/backup-restore/` | 新增脚本+文档 | 低，脚本 |
| `platform-common/billing` IT | 新增恢复后校验测试 | 低，新测试 |
| `db/migration` + `db/migration-dm` | V023 t_lifecycle_record 加字段 | 低，加列 |
| `platform-pipeline.lifecycle` | DataLifecycleManager proof_hash | 低-中，改生命周期 |
| `delivery/` | 报告 | 低 |

---

## 5. 接口设计

### 5.1 备份恢复脚本

```bash
# backup-db.sh
mysqldump --single-transaction --routines sjgx t_service_invoke_log t_audit_log t_bill ... > backup.sql

# restore-db.sh
mysql sjgx < backup.sql
# 恢复后校验：计数 + 审计链 verify
```

### 5.2 恢复后审计链校验

```text
Testcontainers MySQL
  灌入审计哈希链数据
  mysqldump 备份
  DROP/清库
  mysql 恢复
  verify() -> intact
  计数一致
```

### 5.3 销毁证明

```sql
-- V023
ALTER TABLE t_lifecycle_record ADD COLUMN operator VARCHAR(64);
ALTER TABLE t_lifecycle_record ADD COLUMN reason VARCHAR(256);
ALTER TABLE t_lifecycle_record ADD COLUMN proof_hash VARCHAR(64);
ALTER TABLE t_lifecycle_record ADD COLUMN object_key VARCHAR(256);
```

```java
// DataLifecycleManager: 销毁时生成 proof_hash = sha256(asset_code + action + operator + reason + operated_at + object_key)
```

---

## 6. 异常场景

| 场景 | 处理 |
|---|---|
| 开发环境无 mysqldump/MinIO | 脚本语法检查 + Testcontainers 用 SQL 导出替代；标注生产用真实工具 |
| 恢复后审计链不 intact | 测试应捕获；恢复操作不应改审计行内容（哈希链基于内容） |
| 销毁证明 proof_hash 算法 | sha256(资产+操作+操作人+原因+时间+对象)，可校验 |
| V023 迁移历史数据 | 加列允许 NULL，历史行 proof_hash=NULL，新销毁必填 |
| 生产备份依赖外部系统 | 标注待外部，开发层验证闭环逻辑 |

---

## 7. 测试策略

1. F-1/F-2 脚本：`bash -n` 语法检查；本地 DB 冒烟（如可用）。
2. F-3 恢复后审计链校验：Testcontainers 备份→恢复→verify intact + 计数一致。
3. F-4/F-5 销毁证明：proof_hash 生成→校验；DataLifecycleManager 销毁写 t_lifecycle_record。
4. F-4 V023 迁移：`MigrationDialectCompatibilityTest` 纳入 V023。
5. 回归：`mvn test` 全量 + 前端 `npm run test:unit`。

---

## 8. Codex 实现边界

Codex 须在 `tasks/codex-task-P2-05.md` 中实现，且**仅限**：

1. F-1 DB 备份恢复脚本。
2. F-2 MinIO 备份恢复脚本。
3. F-3 恢复后审计链校验闭环测试（Testcontainers）。
4. F-4 销毁证明 proof_hash 补全（V023 + DataLifecycleManager）。
5. F-5 销毁证明测试。
6. F-6 备份恢复方案 + runbook。
7. F-7 P2-05 报告。

**不得做**：
- 不引专业备份工具（Velero/Bacula）。
- 不做真实异地灾备（待生产）。
- 不修改 `.env`/真实密钥/生产配置。
- 不补 t_raw_data 读写路径（独立缺口）。
- 不重构无关模块。

---

## 9. 验收标准

- [ ] DB/MinIO 备份恢复脚本套件完整，`bash -n` 通过。
- [ ] 恢复后审计链校验闭环测试通过（恢复后 verify intact + 计数一致）。
- [ ] 销毁证明 proof_hash 补全（V023 + DataLifecycleManager）+ 测试通过。
- [ ] 备份恢复方案 + runbook 完整。
- [ ] 报告诚实分层：开发闭环已验证 / 生产备份待外部 / 审计留存待生产开启。
- [ ] `mvn test` + 前端测试全绿，无回归。

---

## 10. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| V023 迁移破坏 t_lifecycle_record 既有数据 | 低 | 加列允许 NULL；MigrationDialectCompatibilityTest 守护 |
| DataLifecycleManager proof_hash 改动破坏生命周期 | 中 | 单测回归 |
| 恢复后审计链不 intact | 中 | 测试捕获；恢复不改审计内容 |
| Testcontainers 备份恢复测试不稳定 | 中 | 门控 + 手动复现步骤 |
| 生产备份依赖外部系统 | 中 | 标注待外部，开发层验证闭环 |

**回滚**：脚本可还原；V023 有 U023 回滚；DataLifecycleManager 改动有单测守护。

---

## 11. 下一步

本计划通过后，生成 `tasks/codex-task-P2-05.md`，按 F-1~F-7 拆解派发。

---

## 附：范围决策（已确认）

1. **恢复后审计链校验闭环**：✅ Testcontainers 闭环——灌审计哈希链→备份→清库→恢复→verify intact + 计数一致。
2. **销毁证明 proof_hash**：✅ 纳入本次——V023 补 t_lifecycle_record 字段 + DataLifecycleManager 生成 proof_hash + 测试。
3. **备份脚本工具**：✅ mysqldump（DB）+ mc（MinIO），不引专业工具。
4. **生产异地灾备/Redis 重建/审计留存生产开启**：✅ 标注待生产，开发层验证闭环逻辑。
