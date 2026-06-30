# Claude Code 审查结果 — P2-03 故障演练

## 1. 审查对象

- 任务：P2-03 故障演练
- 分支：`ai/p2-fault-drill`（改动在工作区未提交，含已提交计划 commit `c39d661f`）
- 任务单：`tasks/codex-task-P2-03.md`，计划：`tasks/claude-plan-P2-03.md`
- 审查日期：2026-06-30
- 改动范围：Redis 降级、日志降级测试、故障注入 IT、chaos 脚本修正+灰度回滚、报告、ops-manual 对齐
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（F-1 改配额计数路径 + 降级/兜底场景，必做）

## 2. Git 状态

9 文件修改 + 5 文件新增（`rolling-upgrade.sh`、`p2-03-report.md`、`RedisFaultInjectionIT`、`AsyncInvokeLogWriterTest`、`KafkaFaultInjectionIT`），与任务单 F-1~F-8 对齐，无越界模块改动。

## 3. 常规审查

| 项 | 结论 |
|---|---|
| F-1 RedisQuotaCounter 降级 | try/catch + null 检查均降级 `LocalFallbackQuotaCounter`（ConcurrentHashMap.merge 原子），429 语义保留，降级语义注释。✓ |
| F-2 AsyncInvokeLogWriter 降级测试 | 五场景单测（Kafka 正常/失败落 JDBC/无 repo 抛错/无 Kafka 落 JDBC/无两者落 localMirror）+ ObjectMapper JavaTimeModule。✓ |
| F-3 connector 重试 | 不改代码，报告标注限制。✓ |
| F-4 故障注入 IT | RedisFaultInjectionIT + KafkaFaultInjectionIT，Testcontainers，门控 `RUN_FAULT_INJECTION_IT`。✓ |
| F-5 chaos 脚本修正 | db-failover 达梦主备标注+恢复、redis-down 对齐 fallback、kafka-outage 对齐 JDBC 落库。✓ |
| F-6 灰度回滚脚本 | `rolling-upgrade.sh` 滚动升级+回滚计时。✓ |
| F-7 报告 | `p2-03-report.md` NFR-A 对照 + 开发层验证 + 生产待演练 + 限制说明。✓ |
| F-8 ops-manual 对齐 | Redis/Kafka 降级描述与代码一致，connector 限制标注。✓ |

## 4. 对抗式审查

### 4.1 攻击面枚举

1. F-1 Redis 降级是否真不抛 500、429 语义是否保留、并发安全。
2. F-1 降级后 DB 同步（used_requests）是否与 fallback 计数不一致。
3. F-4 Redis IT `stop`+`start` 后计数断言是否正确（容器重启数据清空？）。
4. F-4 Kafka IT 降级路径是否真触发。
5. F-2 日志降级五场景是否真覆盖。
6. chaos 脚本语法与验证点对齐。
7. 文档与代码一致性。
8. 降级精度（多实例本地计数）。

### 4.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| Redis 异常仍抛 500 | `incrementAndCheck` try/catch RuntimeException → fallback；null → fallback；仅 result<0 抛 429 | 已反驳 |
| fallback 并发不安全 | `LocalFallbackQuotaCounter` 用 `ConcurrentHashMap.merge(consumerId, 1L, Long::sum)` 原子操作 | 已反驳 |
| 429 超额语义丢失 | fallback `next>maxRequests` 抛 CONSUMER-429；Redis `result<0` 抛 429；测试 `redisQuotaCounterFallsBackWhenRedisReturnsNull` 第三次断言抛异常 | 已反驳 |
| 降级后 DB used_requests 不一致 | `ConsumerService.consume:117` 用 fallback 的 next 写 DB；降级期本地计数写入 DB，Redis 恢复后 Redis 计数从 0 重建——精度下降但已标注（代码注释 + 报告 §4） | 已反驳（已标注限制） |
| Redis IT line 37 返回 1 错误 | line 30 INCR 101→1；stop+start 容器重启数据清空（无持久化）；line 37 新 counter INCR 101→1。Testcontainers stop/start 行为正确 | 已反驳 |
| Kafka IT 降级未触发 | `kafka.stop()` 后 `kafkaTemplate.send` 抛异常 → `AsyncInvokeLogWriter.write:56-61` catch 降级 JDBC；测试断言 `repository.findByRange total=1` | 已反驳 |
| 日志降级五场景缺口 | `AsyncInvokeLogWriterTest` 五测试覆盖 Kafka 正常/失败落 JDBC/无 repo 抛错/无 Kafka 落 JDBC/无两者落 localMirror | 已反驳 |
| chaos 脚本语法错 | `bash -n` 7 脚本全 OK | 已反驳 |
| redis-down 验证点未对齐 F-1 | 改为 grep `Redis quota counter unavailable\|falling back to local quota counter`，对齐 fallback 日志 | 已反驳 |
| ops-manual 仍声称 DB fallback | 改为"本地计数"对齐 F-1 实现；Kafka 对齐 JDBC fallback；connector 限制标注 | 已反驳 |
| 降级精度多实例 | fallback 是 JVM 本地，多实例配额精度下降；报告 §4 + 代码注释明示 | 已反驳（已标注） |
| IT 默认跳过形同虚设 | `@EnabledIfEnvironmentVariable(RUN_FAULT_INJECTION_IT=true)` + `disabledWithoutDocker`，与 P2-01 PartitionIT 同模式；报告说明门控运行命令 | 已反驳（P3-1，同既有模式） |

### 4.3 存活缺陷

**无 P1 阻断、无 P2 改进。** 2 项 P3 提示（不阻断）：

- **P3-1**：`RedisFaultInjectionIT`/`KafkaFaultInjectionIT` 默认 `RUN_FAULT_INJECTION_IT` 门控跳过，普通 `mvn test` 不运行。与 P2-01 `LargeTablePartitionIntegrationTest` 同模式（`assumeTrue`/`@EnabledIfSystemProperty`）。建议 CI 加 `RUN_FAULT_INJECTION_IT=true` 周期执行，或文档明确门控运行命令（报告已说明）。
- **P3-2**：`LocalFallbackQuotaCounter` 是 JVM 本地计数，多实例部署时降级期配额精度下降（可能超额）。已在代码注释 + 报告 §4 标注，符合诚实原则。生产若需强一致降级，可改为 DB 计数 fallback（留后续）。

### 4.4 对"建议通过"的反驳

- 为何不应通过？F-1 降级是否真修复文档代码不一致？→ `RedisQuotaCounter` 降级到本地 fallback，ops-manual 改为"本地计数"，一致。
- 降级是否会引入配额超额风险？→ 单实例不超额（429 保留），多实例降级期精度下降已标注，属已知限制非阻断。
- 故障注入 IT 是否真验证降级？→ Redis IT stop→fallback→start→恢复，Kafka IT stop→JDBC 降级→start→恢复，设计正确（待 Docker 环境实跑，门控合理）。
- 反驳未发现存活 P1/P2 阻断，结论成立。

## 5. 测试验证

```text
mvn test -pl platform-partner,platform-pipeline,platform-common -am
- platform-common:  Tests run: 39, Failures: 0, Errors: 0, Skipped: 1
- platform-partner: Tests run: 32, Failures: 0, Errors: 0  （+2 Redis 降级单测）
- platform-pipeline:Tests run: 118,Failures: 0, Errors: 0  （+5 AsyncInvokeLogWriter 单测）
BUILD SUCCESS
```

`bash -n` 7 个 chaos/failover 脚本全通过。故障注入 IT 默认门控跳过（待 `RUN_FAULT_INJECTION_IT=true` + Docker）。

## 6. 未实测项

1. 故障注入 IT（Redis/Kafka）实跑（需 Docker + `RUN_FAULT_INJECTION_IT=true`）。
2. 生产 K8s chaos 脚本实测（node-down/db-failover/redis-down/kafka-outage/dual-active/rolling-upgrade 的 RPO/RTO）。
3. 真实达梦/OceanBase 主备切换。
4. 同城双活 RPO/RTO。
5. 灰度回滚≤10min 实测。

以上均为环境依赖项，P2-03 开发层验证已就绪，待环境执行。

## 7. 审查结论

**建议通过。**

- F-1 Redis 降级修复文档代码不一致，429 语义保留，并发安全，降级语义标注。
- F-2 日志降级五场景单测完整，Instant 序列化修复。
- F-4 故障注入 IT 设计正确（待门控实跑）。
- F-5/F-6 chaos 脚本修正 + 灰度回滚，`bash -n` 全通过，验证点对齐。
- F-7/F-8 报告 + ops-manual 诚实区分开发层/生产，文档代码一致。
- 对抗式审查未发现存活 P1/P2 阻断。
- 2 项 P3 提示（IT 门控、降级精度）不阻断。

## 8. 后续建议（非阻断）

1. P3-1：CI 周期执行 `RUN_FAULT_INJECTION_IT=true` 故障注入 IT，或文档明确门控运行。
2. P3-2：生产若需强一致配额降级，fallback 改 DB 计数（当前本地计数已标注限制）。
3. 上线前门禁：生产 K8s chaos 演练、真实达梦主备、同城双活 RPO/RTO、灰度回滚实测。

返工改动可提交 `ai/p2-fault-drill` 并合并 master。
