# Claude Code 最终复查 - P2-05 备份恢复

## 1. 复审对象

- 任务：P2-05 备份恢复（最终复查）
- 分支：`ai/p2-backup-restore`（仍未提交）
- 基线：`reviews/claude-review-P2-05-返工复查.md`（2026-07-12，1 项 P1 + 测试补跑）
- 复审日期：2026-07-12

## 2. 返工项核对

| 返工项 | 状态 | 证据 |
|---|---|---|
| **P1** backup-db.sh 密码传递 | ✅ 已修复 | `backup-db.sh:35` `export MYSQL_PWD="${DB_PASSWORD}"`，与 `restore-db.sh:41` 一致；`DB_PASSWORD` 第 8 行读取、第 35 行使用，链路完整 |
| **P3-7** backup-db.sh DB_NAME 正则 | ✅ 已修复 | `backup-db.sh:13-16` `^[A-Za-z_][A-Za-z0-9_]*$`，在 mysqldump 前校验，与 restore 一致 |
| **测试补跑** | ✅ 已完成 | 报告 §5 已更新：`bash -n` 通过（Git for Windows Bash）；`mvn test -pl platform-common,platform-pipeline` 通过（Maven 3.9.16，120 测试）；`RUN_BACKUP_RESTORE_IT=true ... BackupRestoreAuditChainIT` 通过（Docker 29.4.2 + MySQL 8 Testcontainer，1 闭环 IT）；前端 88 测试通过 |

## 3. 对抗式最终证伪

| 反例 | 追踪 | 结论 |
|---|---|---|
| MYSQL_PWD 仍丢密码 | grep 确认第 35 行 `${DB_PASSWORD}`；环境变量传值不受特殊字符影响 | 已反驳 |
| DB_NAME 正则过严拒合法库名 | 不允许连字符库名（如 `sjgx-restore`）；但 runbook 用 `sjgx_restore`（下划线）符合；保守安全选择，生产若需连字符库名再放宽 | 已反驳（记 P3，不阻断） |
| trap 失败时未 unset | `trap 'unset MYSQL_PWD' EXIT` 在 mysql 成功/失败均触发 | 已反驳 |
| 测试数字虚报 | 120 = 上轮 pipeline 119 + 新增 lifecycle 1，合理；IT 1 个闭环；前端 88 与上轮一致 | 已反驳 |
| 两脚本不一致 | backup `:35` / restore `:41` 均为 `${DB_PASSWORD}` + trap；TABLES/正则一致 | 已反驳 |
| P2-1~P2-4 回归 | lengthPrefixed/归档表/空库校验/标注均未在本次改动中回退 | 已反驳 |

## 4. 结论

**通过。** 上轮 P1（backup-db.sh 密码丢失）已修复，P3-7 一并修复，测试补跑全绿（mvn 120 + IT 1 + 前端 88 + bash -n 4 脚本）。两脚本密码传递/DB_NAME 校验/trap 一致。

### 遗留项（不阻断提交）

- P3-1：proof_hash DB 层无 NOT NULL（应用层强制，生产 REVOKE 后可补检查约束）
- P3-4：IdGenerator 多实例并发（生产未触发，接入调用链时确保单例）
- P3-5：persist 无事务（内存 events 污染，不影响 DB）
- P3-6：restore verify 调用时机（runbook 顺序正确，脚本可选）
- P3-7 衍生：DB_NAME 正则不含连字符（保守安全，按需放宽）
- 生产门禁（报告 §4）：真实达梦/OceanBase 备份演练、RPO/RTO 实测、审计 3 年留存 enabled=true、DB REVOKE UPDATE/DELETE、MinIO 异地复制、销毁证明生产接入与抽样

### 建议提交

P2-05 开发闭环已达成且诚实分层，可提交。建议 commit message：

```
feat(P2-05): backup/restore scripts, audit-chain restore IT, lifecycle destroy proof
```

提交后合入 master 前确认 CI 绿。
