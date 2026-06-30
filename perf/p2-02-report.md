# P2-02 压测容量报告

## 1. 本轮交付

- 修复 `perf/jmeter/m5-performance.jmx`：`/run` 改为 `/test`，管理端点加 `Authorization`，服务调用按 `SignatureUtil` 生成 HMAC-SHA256 签名。
- 新增分区查询压测场景：`GET /api/v1/services/{serviceCode}/logs?from&to&page&size`。
- 新增 `perf/datagen/LoadDataGenerator.java`：支持跨月 `t_service_invoke_log`、百万级 `t_raw_data` 和目录数据生成。
- 消费侧日志接口新增 `from/to`：`GET /api/v1/consumers/{id}/logs?from&to&page&size`。
- connector 批量读取按计划降级：本任务不改 `AbstractSourceConnector.read`，以数据生成器直灌模拟批量量级。

## 2. 开发环境基线

当前工作区未启动完整的 Gateway、各业务服务、MySQL/Redis/Nacos 与 JMeter，因此本轮没有可采信的 P50/P95/P99/TPS 实测值。报告不填虚构数字；可按 `perf/runbook.md` 在开发压测环境执行后回填 `perf/report-template.md`。

已完成的可验证基线是：

| 项目 | 结果 |
|---|---|
| JMeter 套件 | XML 已修复并参数化，待目标服务启动后低并发冒烟 |
| 数据规模准备 | 生成器支持每月 5 万调用日志默认值、100 万 raw_data 默认值 |
| 分区查询准备 | 服务日志和消费日志均支持 `from/to` 时间谓词 |
| 批量接入限制 | 真实 connector 批量读取未改造，需后续任务补足 |

## 3. NFR 差距与补测路径

| NFR | 开发实测 | 差距 | 生产补测路径 |
|---|---|---|---|
| P01 标准/定制响应 | 本轮未实测 | 缺少完整服务 + JMeter 冒烟数据 | 1/3/5 副本阶梯压测 `Invoke Published Service`，记录 P50/P95/P99，错误率需 <0.1% |
| P02 并发 TPS | 本轮未实测 | 单节点开发环境不能代表 1000/2000TPS | 生产同规格集群阶梯加压，验证线性扩展；服务副本、DB 连接池和 Redis 连接池同步扩容 |
| P03 批量传输 | raw_data 可直灌 100 万量级 | 非 connector 真实批量，无法证明断点续传吞吐 | 后续改造 connector batchSize/offset 后，用 100 万条文件/API 数据源补测 ≥100MB/s 与断点续传 |
| P04 接入吞吐 | 本轮未实测 | `Trigger Ingest Test` 仍受单条 connector 能力限制 | 专用接入环境压测 HTTP/文件/消息多数据源，按单节点和集群分别计算条/秒 |
| P05 加工吞吐 | 本轮未实测 | P2-02 未触发 ETL 压测 | 使用真实 ETL pipeline 灌入 5 亿/日等比例数据，采集延迟 P95≤5分钟 |
| P06 缓存 | 单测覆盖热点命中逻辑 | 缺少 10TB Redis 集群容量与查询延迟数据 | Redis Cluster 容量压测，热点 key 命中率≥90%，查询 P95≤10ms |
| P07 查询 | 查询场景与数据生成器就绪 | 缺少千万/亿级数据实测 | 生成跨月日志，分别压测带/不带 `from/to`；用 `EXPLAIN` 保存分区裁剪证据 |

## 4. 瓶颈预判与调优建议

- 服务调用：优先观察 API 签名校验、限流器、调用日志异步写入队列和 DB 插入耗时。
- 查询：服务日志查询必须带 `created_at` 范围，否则无法利用 P2-01 分区裁剪。
- 批量数据：当前 connector 不是批量分页模型，P03/P04 真实达标依赖后续 batchSize/offset 改造。
- 数据库：压测前确认 `idx_invoke_log_consumer_created`、服务日志时间范围索引和分区表已生效。
- JVM：使用 `perf/jvm.args`，同时采集 Heap、GC pause、线程数和连接池等待时间。

## 5. 生产补测清单

- JMeter 低并发冒烟：无 401/404/签名失败。
- JMeter 阶梯压测：1、3、5、8 副本记录吞吐线性扩展。
- 100 万真实 connector 批量接入：含中断恢复。
- 达梦/OceanBase 双库性能：同一数据规模下跑服务日志范围查询。
- Redis 集群容量：热点命中率、10ms 查询延迟和容量水位。
- 48h 稳定性：复用 `perf/monitor/collect-metrics.sh` 采集长稳数据。

## 6. 结论

P2-02 已建立可重复的压测输入、脚本和报告结构；本工作区没有执行生产等价压测，不能宣称 NFR-P01~P07 已达标。下一步按 runbook 在完整开发/生产压测环境执行，回填实测值并由 Claude Code 审查验收。
