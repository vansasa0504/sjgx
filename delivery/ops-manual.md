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
| Redis 不可用 | 查看 Redis Pod 与应用降级日志 | 恢复 Redis，确认 DB fallback |
| Kafka 积压 | 查看 consumer lag 与 billing 日志 | 恢复 Kafka，扩容消费者 |
| 质量校验大量失败 | 查询质量结果和工单 | 暂停接入任务，修正规则或源数据 |

## 巡检清单

| 周期 | 内容 |
|---|---|
| 每日 | Pod 状态、错误率、DB 连接池、Redis/Kafka 状态、审计写入 |
| 每周 | 慢 SQL、磁盘容量、账单生成、监管报表导出 |
| 每月 | 备份恢复演练、密钥轮换检查、故障演练复盘 |

## 备份恢复

- 数据库：按机构数据库方案执行全量 + 增量备份，恢复后校验 `t_raw_data`、`t_service_invoke_log`、`t_audit_log` 计数。
- Redis：关键缓存可重建，故障时允许穿透 DB。
- MinIO：归档数据按桶启用版本与生命周期策略。
