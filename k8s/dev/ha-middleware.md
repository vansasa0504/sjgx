# 双活中间件配置要点 - dev

## Redis

- 推荐 Redis Cluster 或 Redis Sentinel，地址通过 `${REDIS_HOST}` / `${REDIS_PORT}` 注入。
- 关键指标：主从复制延迟、failover 时间、缓存命中率。

## 数据库

- 达梦/OceanBase 均需主备或多副本部署，应用只使用 `${*_DB_URL}` 注入连接串。
- RPO/RTO 在 M6 外部环境通过故障切换脚本与数据库复制监控确认。

## Kafka

- 多机房复制建议使用 MirrorMaker 2 或厂商等价能力。
- 关键 Topic：`service-invoke-logs`、接入完成事件、质量告警事件。
- 配置项：`${KAFKA_BOOTSTRAP_SERVERS}`、`${KAFKA_REPLICATION_FACTOR}`、`${KAFKA_MIN_ISR}`。
