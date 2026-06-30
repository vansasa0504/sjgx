# P2-02 压测 Runbook

## 1. 环境准备

1. 启动中间件：
   ```bash
   docker-compose up -d mysql redis nacos xxl-job
   ```
2. 启动后端服务和 Gateway，确认根路径为 `http://localhost:8080` 或通过 `-JbaseUrl` 指定。
3. 执行 Flyway 迁移，确认 `t_service_invoke_log`、`t_raw_data`、`t_data_catalog`、分区表和索引已创建。
4. 创建压测服务、消费方和 API 凭证，记录：
   - `serviceCode`
   - `consumerCode`
   - `apiKey`
   - `secret`
   - 管理端 Token

## 2. 数据灌入

编译生成器：

```bash
javac -encoding UTF-8 perf/datagen/LoadDataGenerator.java
```

灌入跨月调用日志：

```bash
java -cp ".;mysql-connector-j-8.3.0.jar" perf.datagen.LoadDataGenerator --url=jdbc:mysql://localhost:3306/sjgx --user=root --password=root --table=invoke_log --months=6 --per-month=50000 --service=svc-risk --clean
```

灌入 100 万条 raw_data：

```bash
java -cp ".;mysql-connector-j-8.3.0.jar" perf.datagen.LoadDataGenerator --url=jdbc:mysql://localhost:3306/sjgx --user=root --password=root --table=raw_data --count=1000000 --task-id=1 --partner-id=1 --clean
```

灌入目录数据：

```bash
java -cp ".;mysql-connector-j-8.3.0.jar" perf.datagen.LoadDataGenerator --url=jdbc:mysql://localhost:3306/sjgx --user=root --password=root --table=catalog --count=10000 --clean
```

验证数据：

```sql
SELECT COUNT(*) FROM t_service_invoke_log WHERE service_code = 'svc-risk';
SELECT DATE_FORMAT(created_at, '%Y-%m'), COUNT(*) FROM t_service_invoke_log GROUP BY DATE_FORMAT(created_at, '%Y-%m');
SELECT COUNT(*) FROM t_raw_data WHERE task_id = 1;
EXPLAIN SELECT * FROM t_service_invoke_log WHERE service_code = 'svc-risk' AND created_at >= '2026-03-01' AND created_at < '2026-04-01' ORDER BY created_at DESC LIMIT 20;
```

## 3. JMeter 冒烟

低并发先跑 1 线程 1 循环：

```bash
jmeter -n -t perf/jmeter/m5-performance.jmx -l perf/results/p2-02-smoke.jtl -JbaseUrl=http://localhost:8080 -Jtoken=<TOKEN> -JserviceCode=svc-risk -JconsumerCode=consumer-perf-0 -JapiKey=<API_KEY> -Jsecret=<SECRET> -JserviceThreads=1 -JserviceLoops=1 -JingestThreads=1 -JingestLoops=1 -JcatalogThreads=1 -JcatalogLoops=1 -JlogThreads=1 -JlogLoops=1
```

冒烟通过标准：

- 无 401。
- 无 404。
- invoke 无 `bad signature`、`timestamp expired`、`replay request`。
- `.jtl` 中 `success=true`。

## 4. 阶梯压测

示例：

```bash
jmeter -n -t perf/jmeter/m5-performance.jmx -l perf/results/p2-02-100t.jtl -JbaseUrl=http://localhost:8080 -Jtoken=<TOKEN> -JserviceCode=svc-risk -JconsumerCode=consumer-perf-0 -JapiKey=<API_KEY> -Jsecret=<SECRET> -JserviceThreads=100 -JserviceLoops=200 -JcatalogThreads=50 -JcatalogLoops=100 -JlogThreads=50 -JlogLoops=100
```

生产集群按 100、300、500、1000、2000 并发阶梯执行，每阶梯保留 `.jtl`。

## 5. 监控采集

```bash
bash perf/monitor/collect-metrics.sh
```

压测期间同步记录：

- CPU/内存/磁盘 IO。
- JVM Heap、GC pause、线程数。
- 数据库连接池等待、慢 SQL。
- Redis 命中率和延迟。

## 6. 结果汇总

从 JMeter Summary/Aggregate Report 汇总：

- Samples
- Error %
- Throughput
- P50/P95/P99
- 平均响应时间

回填 `perf/report-template.md`，并将生产差距、瓶颈和补测项同步到 `perf/p2-02-report.md`。

## 7. 清理

使用生成器的 `--clean` 参数清理压测范围数据，或手工执行：

```sql
DELETE FROM t_service_invoke_log WHERE service_code = 'svc-risk';
DELETE FROM t_raw_data WHERE task_id = 1;
DELETE FROM t_data_catalog WHERE catalog_code LIKE 'perf-%';
```

## 8. 常见问题

- 401：检查 `-Jtoken` 是否是管理端 Bearer token，且有 catalog/service/ingest 权限。
- bad signature：确认 `apiKey`、`secret` 与服务凭证一致，`params` 与签名 body 相同。
- replay request：每次 invoke 必须生成新 nonce；当前 JSR223 会自动生成。
- timestamp expired：检查压测机与服务端时间差是否超过 300 秒。
- 404：检查 `serviceCode`、`taskId`、路由前缀和 Gateway 转发配置。
