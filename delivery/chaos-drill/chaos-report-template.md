# 故障演练报告模板 - P2-03

版本：0.1.0  
日期：2026-06-30  
维护者：Codex

## 说明

本模板区分两类证据：

- 开发层验证：单元测试或门控故障注入 IT 已证明应用不会因依赖故障直接崩溃。
- 生产演练：切换时间、RPO、RTO、数据丢失等必须在 K8s + 国产数据库主备环境执行后填写，当前不得预填。

| 场景 | 脚本 | 开发层验证 | 切换时间 | RPO | RTO | 数据丢失 | 是否达标 | 备注 |
|---|---|---|---:|---:|---:|---|---|---|
| 节点宕机 | `node-down.sh` | 脚本语法检查 | 待生产演练 | 待生产演练 | 待生产演练 | 待生产演练 | 待判定 | 单节点切换目标 <=30s |
| DB 主从切换 | `db-failover.sh` | 脚本语法检查；真实主备命令待环境确认 | 待生产演练 | 待生产演练 | 待生产演练 | 待生产演练 | 待判定 | RPO<=5min，RTO<=30min |
| Redis 故障 | `redis-down.sh` | `RedisQuotaCounter` 降级到本地 fallback；门控 IT 可验证停 Redis | 待生产演练 | 待生产演练 | 待生产演练 | 待生产演练 | 待判定 | 降级期本地计数，恢复后 Redis 计数重建 |
| Kafka 故障 | `kafka-outage.sh` | `AsyncInvokeLogWriter` Kafka 失败后 JDBC 落库；门控 IT 可验证停 Kafka | 待生产演练 | 待生产演练 | 待生产演练 | 待生产演练 | 待判定 | 调用日志不因 Kafka 故障丢失 |
| 双活切换 | `dual-active-switch.sh` | 脚本语法检查 | 待生产演练 | 待生产演练 | 待生产演练 | 待生产演练 | 待判定 | 同城双活切换 |
| 灰度回滚 | `rolling-upgrade.sh` | 脚本语法检查 | 待生产演练 | 不适用 | 待生产演练 | 不适用 | 待判定 | 回滚目标 <=10min |

## 执行证据

- 单元测试：`mvn test -pl platform-partner,platform-pipeline,platform-common -am`
- 故障注入 IT：`RUN_FAULT_INJECTION_IT=true mvn test -pl platform-partner,platform-pipeline -Dtest=*FaultInjectionIT`
- 脚本语法：`bash -n delivery/chaos-drill/*.sh`
- kubectl 事件：待生产环境执行填充
- 应用日志：待生产环境执行填充
- 数据一致性 SQL：待生产环境执行填充
- 监控截图或导出：待生产环境执行填充
