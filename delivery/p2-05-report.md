# P2-05 备份恢复完成报告

## 1. 开发闭环

| 项 | 结果 | 证据 |
|---|---|---|
| DB 备份恢复脚本 | 已补充 | `delivery/backup-restore/backup-db.sh`、`restore-db.sh` |
| MinIO 备份恢复脚本 | 已补充 | `backup-minio.sh`、`restore-minio.sh` |
| 恢复后审计链校验 | 已补充门控 IT | `BackupRestoreAuditChainIT`，`RUN_BACKUP_RESTORE_IT=true` 时执行 |
| 销毁证明 proof_hash | 已补充 | V023 迁移、`DataLifecycleManager.proofHash`、生命周期 JDBC 写入测试 |
| 运维方案 | 已补充 | `backup-restore-plan.md`、`runbook.md`、`ops-manual.md` |

## 2. NFR 对照

| 要求 | 本次交付 | 生产待验证 |
|---|---|---|
| NFR-A03 数据零丢失 | 备份后恢复关键表，恢复后计数校验，审计链 verify | 真实故障恢复演练和数据库主备一致性 |
| NFR-A04 RPO/RTO | 方案要求全量每日 + 增量/日志 5 分钟级，runbook 记录 RPO/RTO | 机构数据库备份系统、异地复制、实际 RTO <= 30min 演练 |
| NFR-S02 审计 >= 3 年 | 审计哈希链可校验，P2-01 归档机制可开启，恢复后 verify | 生产 `enabled=true`、DB 权限/触发器禁止 UPDATE/DELETE、3 年留存实测 |
| 销毁证明 | `DataLifecycleManager` 已实现并单测验证写入 `operator/reason/object_key/proof_hash` | 尚未接入生产生命周期销毁调用链；接入后再做生产抽样和合规审批记录归档 |

## 3. 限制说明

- 开发闭环使用 Testcontainers + JDBC 导出恢复路径，生产仍应使用机构数据库物理备份/快照或厂商工具。
- MinIO 脚本使用 `mc mirror`，生产需启用桶版本、生命周期策略、对象锁和异地复制。
- Redis 精确重建不在 P2-05 范围；P2-03 已覆盖故障降级，恢复后按缓存重建或周期窗口重置处理。
- `t_raw_data` 纳入脚本和计数校验，但完整读写路径验证仍是独立缺口。
- 销毁证明落库能力已实现并有单测覆盖，但当前生产代码没有生命周期销毁入口调用 `DataLifecycleManager`，生产接入属于后续任务。

## 4. 生产补测清单

1. 在真实达梦/OceanBase 备份系统执行全量 + 增量恢复演练。
2. 记录真实 RPO/RTO，验证 RPO <= 5min、RTO <= 30min。
3. 在生产等价环境开启审计归档，验证 >= 3 年留存策略和查询能力。
4. 对 `t_audit_log` 落地数据库层 UPDATE/DELETE 禁止策略。
5. 对 MinIO 桶版本、生命周期、异地复制做恢复抽样。
6. 抽样校验销毁证明 `proof_hash` 与审批/操作记录一致。

## 5. 当前环境验证状态

| 命令 | 状态 | 说明 |
|---|---|---|
| `git diff --check` | 通过 | 仅有 Git CRLF 提示，无空白错误 |
| `bash -n delivery/backup-restore/*.sh` | 通过 | 使用 Git for Windows 自带 Bash 检查 4 个脚本，退出码 0 |
| `mvn test -pl platform-common,platform-pipeline` | 通过 | Maven 3.9.16；120 个测试通过，0 失败、0 错误 |
| `RUN_BACKUP_RESTORE_IT=true mvn test -pl platform-common -Dtest=BackupRestoreAuditChainIT` | 通过 | Docker 29.4.2 + MySQL 8 Testcontainer；1 个闭环 IT 通过 |
| `cd platform-ui && npm run test:unit` | 通过 | 12 个测试文件、88 个测试通过；提权后解决 Vite 临时文件写入权限问题，输出含 profile/jsdom 噪声但退出码为 0 |

## 6. 建议验收命令

```bash
bash -n delivery/backup-restore/*.sh
mvn test -pl platform-common,platform-pipeline
RUN_BACKUP_RESTORE_IT=true mvn test -pl platform-common -Dtest=BackupRestoreAuditChainIT
```

全量回归仍建议执行：

```bash
mvn test
cd platform-ui && npm run test:unit
```
