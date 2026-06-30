# P2-02 性能压测报告模板

本表用于 P2-02 压测容量基线记录。当前工作区只完成压测体系、数据生成器和回归测试；未启动完整 6 服务 + Gateway + JMeter 环境，因此开发环境压测值标注为“本轮未实测”，不以虚构数字替代。按 `perf/runbook.md` 执行后，将 `.jtl` 汇总值回填到“实测值”列。

| NFR | 指标 | 标准 | 实测值 | 是否达标 | 测试方法 |
|---|---|---|---|---|---|
| NFR-P01 | 标准接口响应 | P50≤200ms/P95≤500ms/P99≤1s | 本轮未实测：完整服务与 JMeter 未启动；套件已修复为签名 invoke 场景 | 待环境补测判定 | JMeter `perf/jmeter/m5-performance.jmx`，场景 `Invoke Published Service` |
| NFR-P01 | 定制接口响应 | P50≤500ms/P95≤1s | 本轮未实测：需在定制路由服务发布后执行同一 invoke 场景 | 待环境补测判定 | JMeter 参数化 `serviceCode` 指向定制服务 |
| NFR-P02 | 并发 TPS | ≥1000TPS，峰值≥2000TPS | 本轮未实测：开发单节点不作为生产达标依据 | 待生产集群补测 | JMeter 阶梯加压，记录 TPS/P95/P99/错误率 |
| NFR-P03 | 批量传输 | 100万条/批，≥100MB/s，断点续传 | 数据生成器支持 `t_raw_data` 100万条直灌模拟；connector 真实批量读取未改造 | 部分满足压测准备，不等同生产达标 | `perf/datagen/LoadDataGenerator.java --table=raw_data --count=1000000` + 后续真实 connector 补测 |
| NFR-P04 | 接入吞吐 | 日均≥10亿条，单节点≥1万条/秒 | 本轮未实测：以 raw_data 直灌建立量级数据，真实接入链路需补测 | 待生产/专用接入环境补测 | 数据生成器准备数据 + JMeter `Trigger Ingest Test` |
| NFR-P05 | 加工吞吐 | 日均≥5亿条，加工延迟≤5分钟 | 本轮未实测：P2-02 未改变 ETL，需复用生产批处理压测 | 待环境补测判定 | ETL 批处理压测，采集加工延迟和吞吐 |
| NFR-P06 | 缓存 | 命中率≥90%，查询≤10ms，容量≥10TB | 开发单测覆盖热点命中率；10TB 容量需 Redis 集群压测 | 功能基线已覆盖，容量待生产验证 | `StorageServiceTest` + Redis 集群压测 |
| NFR-P07 | 查询 | 千万级≤2s，亿级≤5s | JMeter 增加带 `from/to` 的服务日志分区查询；数据生成器支持跨月调用日志 | 待大表数据补测判定 | `Query Service Logs By Range` + `EXPLAIN` 分区裁剪 |

## 回填要求

执行压测后至少回填：

- 每个 JMeter label 的 Samples、Error%、Throughput、P50、P95、P99。
- 数据规模：调用日志总量、查询月份、raw_data 行数、目录行数。
- 环境规格：服务副本数、JVM 参数、数据库/Redis/Kafka 规格。
- 监控摘要：CPU、Heap、GC、连接池、慢 SQL。
