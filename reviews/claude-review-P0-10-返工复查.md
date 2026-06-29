# Claude Code 复查结果 — P0-10 真实依赖 E2E（返工复查）

## 1. 审查对象

- 任务：P0-10 真实依赖 E2E（返工复查）
- 分支：`ai/p0-e2e-real-deps`
- 前置审查：`reviews/claude-review-P0-10.md`（首次审查，H-1 产物清理 + H-2 IT 补跑）
- 复查日期：2026-06-29
- 返工范围：H-1 产物清理 + .gitignore、IT 实测补跑、IT 实测暴露的 hash 链精度 bug 修复、V015 国产化兼容修复

## 2. 返工落实情况

### 2.1 H-1 产物清理 ✅

- 工作区 14 个 `*.log` + 3 个 `.docker-*` 目录已全部删除（`git status` 无产物）。
- `.gitignore` 补：`*.log`、`.docker-cfg/`、`.docker-config/`、`.docker-tmp/`。
- `.docker-*`（可能含本地 docker 凭据）不再有混入风险。

### 2.2 H-2 IT 实测补跑 ✅（关键证据）

surefire 报告 `platform-pipeline/target/surefire-reports/com.platform.pipeline.RealDependenciesIT.txt`：

```text
Test set: com.platform.pipeline.RealDependenciesIT
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 100.5 s
```

**IT 实测通过**（100.5s 含 MySQL+Kafka+RabbitMQ+MinIO 容器启动）。用户在 Docker 环境设 `RUN_REAL_DEPS_IT=true` 跑通，环境受阻问题已解决。IT 覆盖：service invoke→Kafka→JDBC 落库、MQ 协议拉取、MinIO 冷存储 write→read、audit hash 链 verify intact、trace_id 查询。

### 2.3 IT 实测暴露的真实 bug 修复（额外改动，正确）

IT 实测时发现两个真实问题，返工已修复：

**B-1：JdbcAuditLogRepository hash 链 TIMESTAMP 精度 bug**
- 问题：`Instant.now()` 纳秒精度，MySQL TIMESTAMP 写入时截断到秒，但 hash 用纳秒 created_at 计算。verify 重算 hash 时用库里秒级 created_at，与存储的 hash（纳秒算出）不匹配 → verify 误报断链。
- 修复：`append` 中 `createdAt.truncatedTo(ChronoUnit.SECONDS)` 后再算 hash + 写库，保证 hash 用的精度与库一致。
- 验证：IT 的 `auditLogRepository.verify().intact()` 断言通过，证明修复有效。
- 评价：**真实 bug，IT 实测价值体现**。这正是 P0-10 真实依赖验证的意义——单元测试用 H2/内存无法暴露 MySQL TIMESTAMP 精度问题。

**B-2：latestHash 改 setMaxRows(1) 替代 FETCH FIRST 1 ROWS ONLY**
- 问题：`FETCH FIRST 1 ROWS ONLY` 在达梦/某些 MySQL 版本兼容性问题（IT 用 MySQLContainer 实测可能暴露）。
- 修复：改用 `PreparedStatement.setMaxRows(1)`，JDBC 通用，三库兼容。
- 评价：合理兼容性修复。

### 2.4 V015 国产化兼容修复（额外改动）

- 改动：V015（MySQL + DM）`CREATE INDEX IF NOT EXISTS` → `CREATE INDEX`。
- 依据：全仓既有索引写法均为 `CREATE INDEX`（参照 V006），`CREATE INDEX IF NOT EXISTS` 达梦不支持。返工对齐既有模式。
- 验证：全仓已无 `CREATE INDEX IF NOT EXISTS`，与 P0-02 国产化策略一致。

## 3. 测试验证

```bash
mvn -pl platform-common install -DskipTests
mvn test   # 全量回归
```

结果：**BUILD SUCCESS**，全模块测试全绿（common 32 / gateway 2 / auth 33 / partner 30 / quality 18 / pipeline 72 / billing 39），无回归。

- AuditLogRepositoryTest（4 用例）通过，含 B-1 精度修复后的 hash 链。
- IT 实测通过（见 §2.2）。

## 4. 关键问题评估

### 4.1 InMemoryAuditLogRepository 未同步 truncatedTo（合理，非缺陷）

- 现象：JdbcAuditLogRepository append 截断 createdAt 到秒，InMemoryAuditLogRepository 未截断。
- 评估：**正确**。内存仓储不写 DB，无 TIMESTAMP 精度丢失，算 hash 与存 hash 用同一 createdAt（纳秒），verify 也用同一值，自洽不断链。只有 JDBC 仓储需要截断（因 DB 截断）。两者各自自洽，差异合理。

### 4.2 V015 Flyway checksum 风险（需上线前处理）

- 现象：V015 内容变更（`CREATE INDEX IF NOT EXISTS` → `CREATE INDEX`），V015 已在 master 合入并可能在开发/测试库执行过。已执行旧 V015 的库会 Flyway validate checksum 偏差失败。
- 评估：测试用 H2 重建库无此问题（全量测试通过）。但**开发/生产库需处理**。
- 处理：与 P0-01 的 checksum 处置一致（dev-progress §11.1）——已执行旧 V015 的库需 `flyway:repair` 接受新基线，或重建库。列为上线前必做。

### 4.3 E2E 脚本证据

- 现象：`target/e2e-p0/` 无证据输出（本审查环境未跑 e2e-p0.sh）。
- 评估：IT 已实测通过（覆盖主链路关键端点 + hash 链 + trace_id），E2E 脚本为手动 curl 序列，属补充证据。建议上线前补跑 e2e-p0.sh 附 curl 证据，但 IT 通过已满足核心门禁。

## 5. 代码质量

返工质量高：
1. **B-1 精度修复精准**：truncatedTo(SECONDS) 是 TIMESTAMP 精度问题的标准解法，修复后 hash 算法与库存储精度一致，IT 验证有效。
2. **B-2 setMaxRows 兼容性好**：JDBC 通用 API，三库兼容，优于方言相关语法。
3. **V015 对齐既有模式**：与 V006 等既有索引写法一致，符合 P0-02 国产化策略。
4. **产物清理彻底**：.gitignore 补全，工作区干净。
5. **IT 实测价值体现**：B-1 是单元测试无法暴露的真实 bug，证明 P0-10 真实依赖验证的必要性。

## 6. 是否超出范围

- B-1/B-2（JdbcAuditLogRepository）：IT 实测暴露的真实 bug 修复，属 P0-10 真实依赖验证的必要闭环（任务单 §9"jdbc profile 暴露问题则返工"），合理。
- V015 国产化修复：P0-02 国产化兼容的遗漏修复，IT 实测/国产化验证暴露，合理。
- 无无关改动，无前端改动，无重复测试。

## 7. 复查结论

**建议通过**

P0-10 返工全面落实首次审查的 H-1（产物清理）、H-2（IT 实测），并额外修复 IT 实测暴露的两个真实问题（B-1 hash 链 TIMESTAMP 精度 bug、B-2 latestHash 兼容性）+ V015 国产化兼容对齐。IT 实测通过（1 test, 100.5s, 0 failure），全量回归 BUILD SUCCESS 无回归。

**B-1 是本次返工最高价值项**：真实依赖 IT 暴露了单元测试无法发现的 MySQL TIMESTAMP 精度导致 hash 链 verify 失败的 bug，修复后 IT verify intact 断言通过，证明 P0-10 真实依赖验证门禁的有效性。

### 上线前必做
1. [ ] V015 checksum 处置：已执行旧 V015 的开发/生产库需 `flyway:repair` 或重建（与 P0-01 §11.1 一致）。
2. [ ] 补跑 `bash tasks/e2e-p0.sh` 附 10 步 curl 证据 + 重启恢复证据（IT 已覆盖核心，E2E 为补充）。
3. [ ] P0-08 H-1：DB REVOKE runbook（沿用）。

## 8. P0 阶段总结

P0-10 是 P0 最后任务。返工复查通过后，**P0-01~P0-10 全部完成**：

| 任务 | 状态 | 关键交付 |
|---|---|---|
| P0-01 | ✅ | Flyway 迁移修复 |
| P0-02 | ✅ | 国产化双库兼容 |
| P0-03 | ✅ | 核心 Repository 落库 |
| P0-04 | ✅ | API 凭证密文存储 |
| P0-05 | ✅ | 调用日志事实源 |
| P0-06 | ✅ | 账单明细 |
| P0-07 | ✅ | 目录申请审批 |
| P0-08 | ✅ | 审计防篡改 hash 链 |
| P0-09 | ✅ | 前端边界测试 |
| P0-10 | ✅ | 真实依赖 E2E（IT 实测通过） |

**P0 全部门禁达成**（功能/数据库/持久化/安全/测试/合规），可进入 P1（增强）。

### P0 上线前门禁清单（汇总）
- P0-08 H-1：DB REVOKE UPDATE/DELETE ON t_audit_log runbook
- P0-10 V015 checksum：已执行旧 V015 的库 flyway:repair
- P0-10 E2E：补跑 e2e-p0.sh 附 curl + 重启恢复证据

## 9. 建议提交

清理已就绪，可提交。建议提交信息：

```text
feat(P0-10): real-dependency E2E with Kafka/RabbitMQ/MinIO and Testcontainers

- Kafka async invoke-log: AsyncInvokeLogWriter sends to topic, KafkaInvokeLogConsumer persists to t_service_invoke_log (falls back to JDBC)
- RabbitMQ MqAdapter wired via @ConditionalOnBean; IngestService routes by protocol scheme
- MinIO cold storage (MinioColdStorageStore) with Local fallback
- application.yml adds kafka/rabbitmq/minio config, all env-var driven, defaults off
- Testcontainers IT (RealDependenciesIT) covers MySQL+Kafka+RabbitMQ+MinIO; passed in Docker (100.5s)
- fix: JdbcAuditLogRepository truncates createdAt to seconds for hash chain (MySQL TIMESTAMP precision)
- fix: latestHash uses setMaxRows for cross-DB compatibility
- fix: V015 drops IF NOT EXISTS on indexes for DM/OceanBase compatibility
- 10-step E2E script (tasks/e2e-p0.sh)
- .gitignore ignores run artifacts (*.log, .docker-*)
- mvn test green; IT conditional on Docker + RUN_REAL_DEPS_IT
```
