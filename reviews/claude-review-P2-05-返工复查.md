# Claude Code 返工复查 - P2-05 备份恢复

## 1. 复审对象

- 任务：P2-05 备份恢复（返工复查）
- 分支：`ai/p2-backup-restore`（仍未提交）
- 基线：`reviews/claude-review-P2-05.md`（2026-07-12，4 项 P2 + 6 项 P3，需返工）
- 复审日期：2026-07-12
- 复审规则：CLAUDE.md §7.1 对抗式审查（对修复本身证伪）
- 测试执行状态：Maven/IT 仍未补跑（报告 §5 仍标注未执行）；前端已绿。本复查基于静态追踪。

## 2. 返工项核对

| 返工项 | 状态 | 证据 |
|---|---|---|
| **P2-1** proofHash canonical 改长度前缀 | ✅ 已修复 | `DataLifecycleManager.proofHash` 改用 `lengthPrefixed`（`UTF-8 字节长度 + ":" + value`），消除 `\|` 分隔符碰撞；新增碰撞反例测试 `StorageServiceTest:153-157`，精确复现 `reason="x",objectKey="y\|T\|z"` vs `reason="x\|T\|y",objectKey="z"` 断言不等 |
| **P2-2** 标注未接入生产 | ✅ 已修复 | `p2-05-report.md:20,28` 明确"尚未接入生产生命周期销毁调用链"；`backup-restore-plan.md:58` 英文同义标注 |
| **P2-3** 备份补归档表 | ✅ 已修复 | `backup-db.sh:15,17` + `restore-db.sh:28,30` TABLES 补 `t_service_invoke_log_archive`、`t_audit_log_archive`；`backup-restore-plan.md:19,21` Key tables 同步；`runbook.md:11` 计数校验同步 |
| **P2-4** restore 空库校验 | ✅ 已修复（优于建议） | `restore-db.sh:21-24` DB_NAME 正则校验 + `:44-51` information_schema 查已有表，非 0 退出 65；`plan:11`、`runbook:41` 对齐。采用"拒绝非空库"而非 `--add-drop-table`，更安全（强制隔离库，避免误覆盖） |
| **P3-2** DB_NAME 正则 | ✅ 已修复 | `restore-db.sh:21` `^[A-Za-z_][A-Za-z0-9_]*$`。backup-db.sh 未加，但不拼接 DB_NAME 进 SQL，风险可接受 |
| **P3-3** MYSQL_PWD trap | ⚠️ 修复引入新缺陷 | 见 §3 **P1** |
| **测试补跑** | ❌ 未完成 | 报告 §5 仍标注 mvn/bash -n 未执行。硬门禁未满足 |
| P2-1/P2-4（建议项） | ✅ 已一并修复 | 见上 |
| P3-1/P3-4/P3-5/P3-6 | 未处理（记录项） | 不阻断 |

## 3. 新发现缺陷

### P1 阻断：backup-db.sh 丢失数据库密码传递

- **位置**：`delivery/backup-restore/backup-db.sh:8,30`
- **现象**：
  - 第 8 行 `DB_PASSWORD="${DB_PASSWORD:-}"` 读取环境变量到 `DB_PASSWORD`。
  - 第 30 行 `export MYSQL_PWD=""` 把 `MYSQL_PWD` 设为**空字符串**，而非 `${DB_PASSWORD}`。
  - `DB_PASSWORD` 变量被读取后全脚本再无引用（grep 确认仅第 8 行出现）。
- **对比**：`restore-db.sh:41` 正确写 `export MYSQL_PWD="${DB_PASSWORD}"`。两脚本不一致。
- **影响**：生产环境 DB 账户有密码时，`mysqldump` 以空密码连接，`Access denied`，`set -e` 下脚本退出，**备份文件为空或不生成**。NFR-A03 数据零丢失的备份能力在生产失效。`runbook:21` 还示例 `DB_PASSWORD='***'`，用户按文档设密码但脚本忽略，静默失败。
- **为何开发未发现**：开发/测试环境 `sjgx` 用户通常无密码，`MYSQL_PWD=""` 能连，备份成功--典型的"开发过、生产炸"。
- **根因推测**：修复 P3-3 加 `trap 'unset MYSQL_PWD' EXIT` 时，误将原 `export MYSQL_PWD="${DB_PASSWORD}"` 改为 `export MYSQL_PWD=""`。
- **修复**：第 30 行改回 `export MYSQL_PWD="${DB_PASSWORD}"`，保留 `trap`。
- **可复现**：设 `DB_PASSWORD=wrong`，对有密码的 MySQL 运行 `backup-db.sh`，应连接受拒；当前代码用空密码而非 `wrong`，行为与"忽略密码"一致。

### P3 提示（未阻断）

| 编号 | 项 | 说明 |
|---|---|---|
| P3-7（新） | backup-db.sh 未加 DB_NAME 正则 | 与 restore 不一致；backup 不拼接 DB_NAME 进 SQL，风险低，但为一致性建议补 |
| P3-1/P3-4/P3-5/P3-6 | 既有记录项 | 未处理，不阻断本次 |

## 4. 对抗式复审（对修复证伪）

| 反例 | 追踪 | 结论 |
|---|---|---|
| lengthPrefixed 仍有碰撞 | `len:value` 拼接，解析靠长度定界；value 含 `:`/`\|`/数字开头均自洽（如 value="5:abc" -> "5:5:abc"，按 len=5 取 "5:abc"）。两组不同字段值必产生不同 canonical | 已反驳，修复有效 |
| 碰撞测试假绿（clock.instant 变化） | MutableClock 未 advance，多次 `clock.instant()` 返回同一 Instant，`toString()` 一致；反例精确复现原碰撞对 | 已反驳，测试有效 |
| 空库校验可绕过 | information_schema 查 `table_schema='${DB_NAME}'`（正则已校验 DB_NAME 安全）+ `table_name IN (...)` 全部受管表；任一存在即拒。无法绕过 | 已反驳 |
| restore 校验与 backup TABLES 不一致 | 两脚本 TABLES 数组逐项一致（含归档表） | 已反驳 |
| trap 在 backup 引入密码丢失 | 见 §3 P1 | **存活 P1** |
| 报告标注仍误导 | report §2 表 + §3 限制说明 + plan §Audit Verification 三处一致标注"未接入生产" | 已反驳，修复有效 |
| 归档表备份但无数据验证 | 归档表纳入 dump + restore 计数；P2-01 enabled=false 时归档表空，计数=0 合理；生产开启后计数>0 可比对 | 已反驳 |

## 5. 需求/计划符合性

| 标准 | 复审结论 |
|---|---|
| 脚本套件完整，`bash -n` 通过 | 脚本完整；`bash -n` 未实测 ⚠；**backup-db.sh 密码 bug 阻断** |
| 审计链闭环 IT 通过 | IT 设计正确；未实测 ⚠ |
| 销毁证明 proof_hash + 测试 | proofHash 抗篡改已强化 + 碰撞测试；未实测 mvn ⚠；未接入生产已标注 ✓ |
| 方案 + runbook 完整 | 完整，归档表/空库校验/未接入生产均已对齐 ✓ |
| 报告诚实分层 | P2-2 已标注 ✓ |
| mvn + 前端全绿 | 前端已绿；mvn 未实测 ⚠ |

## 6. 复审结论

**需返工（1 项 P1 + 测试补跑）。** 上轮 4 项 P2 + P3-2/P3-3 均已修复且修复质量良好（P2-1 长度前缀自洽、P2-4 空库校验优于建议、P2-2/P2-3 标注与归档表到位）。但 P3-3 修复时**引入 P1 阻断**：`backup-db.sh` 丢失 `${DB_PASSWORD}` 传递，生产有密码时备份失败。叠加 Maven/IT 仍未补跑。

不可提交。须完成下列后复审：

### 返工任务清单（本轮）

1. **[必须/P1]** `backup-db.sh:30` 改 `export MYSQL_PWD="${DB_PASSWORD}"`（保留 `trap`），与 `restore-db.sh:41` 一致。
2. **[必须]** 标准开发环境执行 `bash -n delivery/backup-restore/*.sh` + `mvn test -pl platform-common,platform-pipeline` + `RUN_BACKUP_RESTORE_IT=true mvn test -pl platform-common -Dtest=BackupRestoreAuditChainIT`，附真实输出；更新 `p2-05-report.md §5` 验证状态表。
3. **[建议]** `backup-db.sh` 补 DB_NAME 正则（与 restore 一致）。
4. **[记录]** P3-1/P3-4/P3-5/P3-6/P3-7 后续改进。

### 已尝试反驳且未发现新增存活阻断项的说明

本轮对修复做了证伪：lengthPrefixed 碰撞消除、空库校验不可绕过、归档表一致、标注无误导--均尝试反驳后成立。**唯一存活阻断为 P1 密码丢失**（P3-3 修复引入），修复成本一行。化解 P1 + 测试补跑通过后可提交。

## 7. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| backup-db.sh 密码丢失致生产备份失败 | **高** | P1 修复一行 |
| Maven/IT 未实测 | 中 | 必须补跑 |
| DataLifecycleManager 未接入生产 | 低 | 已标注后续 |
| proofHash 碰撞 | 低 | 已长度前缀消除 |

**回滚**：脚本/Java 改动均可还原；V023 有 U023 双库回滚。
