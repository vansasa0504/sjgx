# 运维手册

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 监控指标

| 类别 | 指标 |
|---|---|
| 健康 | `/actuator/health` |
| JVM | `jvm.memory.used`, `jvm.gc.pause` |
| HTTP | `http.server.requests` |
| 数据库 | `hikaricp.connections.active`, 慢 SQL 日志 |
| 缓存 | Redis 命中率、连接数、内存 |
| 业务 | `t_service_invoke_log` 调用量、错误率、响应耗时 |

48h 稳定性采集使用 `perf/monitor/collect-metrics.sh`。

## 日志查看

```bash
kubectl -n sjgx-dev logs deployment/platform-a --tail=200
kubectl -n sjgx-dev logs deployment/platform-b --tail=200
```

审计日志在 `t_audit_log`，服务调用日志在 `t_service_invoke_log`。

## 常见故障

| 故障 | 定位 | 处理 |
|---|---|---|
| 网关 5xx | 查看 gateway 日志、下游 Pod readiness | 回滚或扩容异常服务 |
| DB 连接耗尽 | 查看 Hikari 指标与慢 SQL | 扩大连接池或优化 SQL |
| Redis 不可用 | 查看 Redis Pod 与 `Redis quota counter unavailable` 降级日志 | 恢复 Redis；故障期间消费方配额降级为本 JVM 本地计数，恢复后 Redis 计数重建 |
| Kafka 积压或不可用 | 查看 consumer lag、pipeline `Kafka invoke-log write failed` 日志与 billing 日志 | 恢复 Kafka，确认调用日志已通过 JDBC fallback 落库，必要时扩容消费者 |
| 合作方接入端点不可达 | 查看接入任务日志、connector check 结果与合作方网络 | 当前 connector 失败会抛错并保留 checkpoint；自动重试/退避列为后续增强 |
| 质量校验大量失败 | 查询质量结果和工单 | 暂停接入任务，修正规则或源数据 |

## 巡检清单

| 周期 | 内容 |
|---|---|
| 每日 | Pod 状态、错误率、DB 连接池、Redis/Kafka 状态、审计写入 |
| 每周 | 慢 SQL、磁盘容量、账单生成、监管报表导出 |
| 每月 | 备份恢复演练、密钥轮换检查、故障演练复盘 |

## 备份恢复

- 数据库：按机构数据库方案执行全量 + 增量备份，恢复后校验 `t_raw_data`、`t_service_invoke_log`、`t_audit_log` 计数。
- Redis：关键缓存可重建；消费方配额在 Redis 故障时降级为本地计数，故障恢复后重新使用 Redis 计数。
- MinIO：归档数据按桶启用版本与生命周期策略。
