# P2-03 故障演练报告

## 1. 本轮交付

- `RedisQuotaCounter` 增加 Redis 不可用 fallback：`execute` 返回 `null` 或抛异常时降级到本 JVM 本地计数，不再抛 `CONSUMER-500`。
- `AsyncInvokeLogWriter` 补齐 Kafka 降级单测：Kafka 正常、Kafka 失败落 JDBC、Kafka 失败且无 repository 抛错、无 Kafka 落 JDBC、无 Kafka/无 repository 落 localMirror。
- 新增门控故障注入 IT：`RedisFaultInjectionIT`、`KafkaFaultInjectionIT`，默认不运行，`RUN_FAULT_INJECTION_IT=true` 时使用 Testcontainers 验证停依赖后的降级恢复。
- 修正 chaos 脚本验证点：Redis 对齐本地 fallback，Kafka 对齐 JDBC 落库，DB 脚本标注真实主备提升需生产环境确认。
- 新增 `delivery/chaos-drill/rolling-upgrade.sh` 支撑 NFR-A05 灰度/滚动升级与回滚演练。
- 更新 `delivery/ops-manual.md` 与 `delivery/chaos-drill/chaos-report-template.md`，避免文档继续声称不准确的 DB fallback。

## 2. NFR-A 对照

| NFR | 标准 | 开发层支撑 | 待生产验证 |
|---|---|---|---|
| A01 系统可用性 | ≥99.95%，年停机≤4.38h | 应用层降级、熔断、限流与日志兜底测试 | 48h 稳定性、年度可用性统计、监控告警闭环 |
| A02 服务可用性 | 核心服务≥99.99%，单服务故障不影响整体 | Redis/Kafka 故障降级单测；门控 Testcontainers IT；既有 CircuitBreaker/RateLimiter 测试 | 生产服务副本故障注入、依赖故障演练 |
| A03 故障恢复 | 单节点≤30s，集群≤5min，数据零丢失 | `node-down.sh`、`db-failover.sh` 脚本语法检查 | K8s 集群实测切换时间、数据库主备一致性 |
| A04 容灾 | 同城双活，RPO≤5min，RTO≤30min | `dual-active-switch.sh` 与 `k8s/dev/failover-drill.sh` 演练脚本 | 同城双活生产环境 RPO/RTO、复制延迟、数据校验 |
| A05 升级 | 灰度/滚动无中断，回滚≤10min | `rolling-upgrade.sh` 脚本语法检查 | 生产 K8s 灰度发布、健康检查、回滚计时 |

## 3. 开发层已验证能力

| 能力 | 证据 |
|---|---|
| Redis 故障不导致配额接口 500 | `ConsumerServiceTest` 覆盖 Redis 返回 `null` 与抛异常时 fallback |
| Redis 故障期间仍执行配额上限 | 本地 fallback 继续计数，超过 maxRequests 仍抛 `CONSUMER-429` |
| Kafka 故障时调用日志可落 JDBC | `AsyncInvokeLogWriterTest.fallsBackToJdbcWhenKafkaSendFails` |
| Kafka 未启用时仍可落 JDBC 或 localMirror | `AsyncInvokeLogWriterTest` 覆盖直接 JDBC 与 localMirror |
| 熔断/限流基础能力 | `DataServiceManagerTest` 既有 CircuitBreaker/RateLimiter 测试 |

## 4. 限制说明

- Redis fallback 是本 JVM 本地计数，不是分布式强一致计数。故障期间可能出现多实例配额精度下降；Redis 恢复后重新使用 Redis 计数。
- `AbstractSourceConnector.read` 本轮不改造。当前接入失败会抛异常并依赖 checkpoint 保留进度；自动重试/退避列为后续任务。
- 本轮未在生产 K8s、达梦/OceanBase 主备、同城双活环境执行 RPO/RTO 实测，不宣称 A03/A04/A05 已生产达标。
- 门控故障注入 IT 需要 Docker；默认 `mvn test` 不运行，避免普通开发环境不稳定。

## 5. 生产演练清单

1. 执行 `delivery/chaos-drill/node-down.sh`，记录单节点切换时间与集群恢复时间。
2. 执行 `delivery/chaos-drill/db-failover.sh`，用真实达梦/OceanBase 主备命令验证 RPO/RTO 和数据零丢失。
3. 执行 `delivery/chaos-drill/redis-down.sh`，确认配额 fallback 日志、业务可用性和恢复后 Redis 计数。
4. 执行 `delivery/chaos-drill/kafka-outage.sh`，确认调用日志 JDBC fallback、Kafka 恢复后 backlog 排空。
5. 执行 `delivery/chaos-drill/dual-active-switch.sh`，记录双活切换 RPO/RTO。
6. 执行 `delivery/chaos-drill/rolling-upgrade.sh`，记录升级健康检查和回滚耗时。

## 6. 结论

P2-03 已补强开发环境可验证的关键依赖降级能力，并完善故障演练脚本与报告模板。生产级 RPO/RTO、同城双活和灰度回滚时延仍需在上线环境执行后由 Claude Code 审查验收。
