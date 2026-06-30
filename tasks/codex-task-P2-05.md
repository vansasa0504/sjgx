# P2-05 Codex 实现任务单 — 备份恢复

> 主控：Claude Code　执行：Codex
> 依据：`tasks/claude-plan-P2-05.md`（第一性原理计划，权威）、`docs/requirements.md` §3.2/§3.3
> 前置：P2-04 已合入 master（`0d466685`）；P0-08/P2-01 已满足
> 分支：`ai/p2-backup-restore`
> 日期：2026-06-30

---

## 0. 必读

1. `AGENTS.md`（职责边界）
2. `tasks/claude-plan-P2-05.md`（本任务第一性原理计划，权威）
3. `docs/requirements.md` §3.2（NFR-A03/A04）、§3.3（NFR-S02 审计≥3年）
4. `delivery/ops-manual.md`（备份恢复章节，待完善）
5. `docs/database-design.md`（数据保留/归档/销毁证明设计）
6. `platform-common/.../audit/JdbcAuditLogRepository.java`（verify 哈希链校验）
7. `platform-billing/.../StatsController.java:126-130`（`/api/v1/stats/audit/verify` 端点）
8. `platform-pipeline/.../storage/lifecycle/DataLifecycleManager.java`（纯内存，待补 proof_hash + DB 写入）
9. `db/migration/V007__quality_storage.sql:79-84`（t_lifecycle_record 仅 4 列）
10. `platform-pipeline/.../RealDependenciesIT.java`（Testcontainers 模式参考）

---

## 1. 任务目标

证明数据丢失后可从备份恢复，且恢复后审计链完整可验证，合规留存/销毁有据可查。备份恢复脚本（mysqldump+mc）+ 恢复后审计链校验闭环测试（Testcontainers）+ 销毁证明 proof_hash 补全 + 方案文档。通过标准：**数据可恢复，审计链可校验（开发闭环已验证 / 生产备份待外部）**。

**核心原则**：开发环境用 Testcontainers 验证"备份→恢复→审计链校验"闭环逻辑；生产异地备份待外部系统诚实标注；销毁证明补全设计缺口。

---

## 2. 实现边界（严格遵守）

**范围决策（已确认）**：
- 恢复后审计链校验：Testcontainers 闭环。
- 销毁证明 proof_hash：纳入本次。
- 备份工具：mysqldump + mc，不引专业工具。
- 生产异地灾备/Redis 重建/审计留存生产开启：标注待生产。

**只做**：F-1 ~ F-7（见下）。
**不做**：
- 不引专业备份工具（Velero/Bacula）。
- 不做真实异地灾备（待生产）。
- 不修改 `.env`、真实密钥、生产配置。
- 不补 t_raw_data 读写路径（独立缺口）。
- 不重构无关模块。

---

## 3. 任务清单

### F-1　DB 备份恢复脚本 — `delivery/backup-restore/`

1. **`backup-db.sh`**：mysqldump 逻辑备份关键表（t_service_invoke_log、t_audit_log、t_bill、t_bill_item、t_finance_sync_record、t_raw_data、t_data_catalog、t_user、t_api_credential 等）：
   - `--single-transaction`（一致性快照）+ `--routines` + `--no-tablespaces`。
   - 参数化：DB 连接（`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`）、输出路径（`BACKUP_DIR`）、文件名时间戳。
   - 输出 `backup-YYYYMMDD-HHMMSS.sql`。
2. **`restore-db.sh`**：从 dump 恢复：
   - 参数化备份文件路径。
   - 恢复后执行校验：`SELECT COUNT(*)` 关键表 + 调用审计链 verify（提示）。
3. `bash -n` 语法检查。
4. 标注"生产用机构数据库方案时替换为物理备份/快照"。

### F-2　MinIO 备份恢复脚本 — `delivery/backup-restore/`

1. **`backup-minio.sh`**：`mc mirror` 归档桶到备份目标 + 桶版本策略提示：
   - 参数化：`MINIO_ENDPOINT/MINIO_ACCESS_KEY/MINIO_SECRET_KEY`、源桶、备份目标。
2. **`restore-minio.sh`**：从备份恢复到 MinIO 桶。
3. `bash -n` 语法检查。
4. 标注"生产用 MinIO 桶版本 + 生命周期策略 + 异地复制"。

### F-3　恢复后审计链校验闭环测试 — `platform-common` 或 `platform-billing` IT

新增 Testcontainers 测试，验证"备份→恢复→审计链可校验"闭环：

1. **Testcontainers MySQL 8.0**：起容器 + 跑迁移（V001~V023）。
2. **灌入审计哈希链数据**：用 `JdbcAuditLogRepository.append` 灌入 N 条哈希链（跨批，>2000 条触发分批 verify）。
3. **备份**：用 `mysqldump` 或 JDBC 导出 `t_audit_log`（+ 关键表）到临时文件。
4. **破坏/清库**：`DELETE FROM t_audit_log` 或重建库。
5. **恢复**：从备份恢复 `t_audit_log`。
6. **校验**：
   - `JdbcAuditLogRepository.verify()` 返回 `intact()`。
   - 计数一致（备份前 count == 恢复后 count）。
   - 哈希链跨批连续（>2000 条覆盖 P2-01 分批）。
7. **门控**：`@EnabledIfEnvironmentVariable RUN_BACKUP_RESTORE_IT=true` + `disabledWithoutDocker`，默认跳过。
8. **若用 mysqldump 不可用**：降级用 JDBC `SELECT * INTO` 导出 + `INSERT` 恢复，标注"生产用 mysqldump"。

### F-4　销毁证明 proof_hash 补全 — `db/migration/V023` + `platform-pipeline`

1. **V023 迁移**（MySQL + 达梦双库 `db/migration/V023__lifecycle_proof.sql`、`db/migration-dm/V023__...`）：
   ```sql
   ALTER TABLE t_lifecycle_record ADD COLUMN operator VARCHAR(64);
   ALTER TABLE t_lifecycle_record ADD COLUMN reason VARCHAR(256);
   ALTER TABLE t_lifecycle_record ADD COLUMN proof_hash VARCHAR(64);
   ALTER TABLE t_lifecycle_record ADD COLUMN object_key VARCHAR(256);
   ```
   - 历史行允许 NULL（加列），新销毁必填。
   - U023 回滚（DROP COLUMN）。
2. **`DataLifecycleManager` 改造**：
   - 销毁（DESTROY）时生成 `proof_hash = sha256(asset_code + '|' + action + '|' + operator + '|' + reason + '|' + operatedAt + '|' + object_key)`。
   - 写入 `t_lifecycle_record`（补 JDBC 写入，当前纯内存——参考 P0-03 双写或直接 JDBC，Codex 自选）。
   - 保留内存 `events` 兼容既有测试。
3. **`LifecycleEvent`/record 扩展**：加 operator/reason/proof_hash/object_key 字段。
4. **`MigrationDialectCompatibilityTest`**：纳入 V023 + 断言 t_lifecycle_record 新列存在。

### F-5　销毁证明测试 — `platform-pipeline` test

1. **proof_hash 生成与校验**：
   - `DataLifecycleManager` 销毁资产 → 生成 proof_hash → 写入 t_lifecycle_record。
   - 校验：相同输入生成相同 proof_hash；篡改任一字段 proof_hash 变化。
2. **JDBC 写入测试**：销毁记录持久化到 t_lifecycle_record（重启可读）。
3. **既有 DataLifecycleManager 测试回归**（内存模式兼容）。

### F-6　备份恢复方案 + runbook — `delivery/backup-restore/`

1. **`backup-restore-plan.md`**：
   - DB 备份策略（全量+增量、频率、保留期、mysqldump/生产物理备份）。
   - Redis 备份（可重建，降级已有 P2-03）。
   - MinIO 备份（桶版本+生命周期+异地复制）。
   - 审计日志≥3年留存（P2-01 归档机制，生产 enabled=true）。
   - 恢复后校验流程（计数 + 审计链 verify + 销毁证明）。
   - NFR-A03/A04/S02 对照。
2. **`runbook.md`**：备份执行、恢复执行、恢复后校验、演练流程、清理。
3. **`delivery/ops-manual.md`** 备份恢复章节对齐（替换"按机构数据库方案"为具体方案+脚本引用）。

### F-7　P2-05 报告 — `delivery/p2-05-report.md`

1. **开发闭环已验证**：F-3 恢复后审计链校验、F-5 销毁证明、既有 verify 端点。
2. **待生产外部系统**：真实异地灾备、机构数据库方案、MinIO 异地复制、审计留存 enabled=true、DB REVOKE 落地。
3. **NFR-A/S 对照表**：A03 数据零丢失、A04 RPO/RTO、S02 审计≥3年/销毁证明。
4. **限制说明**：t_raw_data 无读写路径、Redis 重建待补、生产备份待外部。
5. **生产补测清单**：真实备份恢复演练、异地灾备 RPO/RTO、审计 3 年留存实测、销毁证明生产校验。

---

## 4. 测试要求

1. **F-1/F-2 脚本**：`bash -n` 语法检查。
2. **F-3 恢复后审计链校验**：Testcontainers 备份→恢复→verify intact + 计数一致（Docker 可用时）。
3. **F-4 V023 迁移**：`MigrationDialectCompatibilityTest` 纳入 V023。
4. **F-5 销毁证明**：proof_hash 生成/校验 + JDBC 持久化测试。
5. **回归**：`mvn test` 全量（V023 + DataLifecycleManager 改动）+ 前端 `npm run test:unit`。
6. **测试命令**：
   ```bash
   mvn test
   cd platform-ui && npm run test:unit
   bash -n delivery/backup-restore/*.sh
   ```

---

## 5. 输出要求（完成后提交给 Claude Code 审查）

1. 修改/新增文件清单。
2. F-1/F-2 脚本 `bash -n` 证据。
3. F-3 恢复后审计链校验测试结果（Docker 可用时；否则标注）。
4. F-4 V023 迁移 + MigrationDialectCompatibilityTest 结果。
5. F-5 销毁证明测试结果（proof_hash 生成/校验）。
6. 备份恢复方案 + runbook + ops-manual 对齐。
7. P2-05 报告。
8. 潜在风险与未实测项（生产异地灾备、Redis 重建、审计留存实测）。
9. 偏离说明。

---

## 6. 共性约束

- 不破坏 P0/P1/P2-01~P2-04 既有闭环。
- 不修改 `.env`、真实密钥、生产配置、`docs/`（delivery 下文档除外）、`tasks/`（本任务单除外）、`reviews/`、`k8s/prod/`、`security/`。
- 国产化：V023 MySQL + 达梦双库，`MigrationDialectCompatibilityTest` 守护，避开方言禁忌。
- 诚实标注：开发闭环不等于生产灾备达标，所有外推与限制必须明示。
- 备份脚本不得含真实密钥/连接串，全部参数化。
- 上线前门禁：生产备份恢复演练、异地灾备 RPO/RTO、审计 3 年留存实测、销毁证明生产校验（本任务标注未实测，不替代上线门禁）。

---

## 7. 验收标准（对齐 claude-plan-P2-05 §9）

- [ ] DB/MinIO 备份恢复脚本套件完整，`bash -n` 通过。
- [ ] 恢复后审计链校验闭环测试通过（恢复后 verify intact + 计数一致）。
- [ ] 销毁证明 proof_hash 补全（V023 + DataLifecycleManager）+ 测试通过。
- [ ] 备份恢复方案 + runbook 完整，ops-manual 对齐。
- [ ] 报告诚实分层：开发闭环已验证 / 生产备份待外部 / 审计留存待生产开启。
- [ ] `mvn test` + 前端测试全绿，无回归。
