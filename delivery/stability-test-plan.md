# 稳定性测试方案 - M6

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 范围

对外部数据采集平台执行 48h 混合负载稳定性测试，覆盖服务调用、批量接入、目录查询和后台治理聚合。当前开发环境不具备 48h 压测资源，实测值待上线环境执行填充。

## 负载模型

复用 `perf/jmeter/m5-performance.jmx`：

```bash
jmeter -n -t perf/jmeter/m5-performance.jmx \
  -JbaseUrl=http://gateway.sjgx.local \
  -JserviceThreads=50 \
  -JingestThreads=10 \
  -JcatalogThreads=30 \
  -JserviceLoops=-1 \
  -JingestLoops=-1 \
  -JcatalogLoops=-1 \
  -JresultFile=perf/results/m6-48h.jtl
```

执行时长由调度器或外层 `timeout 48h` 控制。

## 监控采集

使用 `perf/monitor/collect-metrics.sh` 定时采样：

```bash
BASE_URL=http://gateway.sjgx.local \
OUT_FILE=perf/results/m6-metrics.csv \
INTERVAL_SECONDS=30 \
DURATION_SECONDS=172800 \
bash perf/monitor/collect-metrics.sh
```

采集指标：

| 指标 | 来源 |
|---|---|
| JVM Heap | `/actuator/metrics/jvm.memory.used` |
| GC 暂停 | `/actuator/metrics/jvm.gc.pause` + GC 日志 |
| TPS/P95/P99/错误率 | JMeter JTL |
| DB 连接池 | `/actuator/metrics/hikaricp.connections.active` |
| Redis 命中率 | 应用缓存指标或 Redis INFO |

## 通过判定

- 无宕机，无 Pod 非预期重启。
- Heap 基线稳定，按 `perf/monitor/heap-trend-analysis.md` 未发现持续泄漏。
- GC 正常，无持续恶化。
- TPS 波动小于 10%。
- 错误率小于 0.1%。
- 核心接口 P95/P99 无持续劣化。

## 结果记录

| 项目 | 实测值 | 状态 |
|---|---:|---|
| 宕机次数 | 待上线环境 48h 执行填充 | 待判定 |
| Heap 趋势 | 待上线环境 48h 执行填充 | 待判定 |
| GC 情况 | 待上线环境 48h 执行填充 | 待判定 |
| TPS 波动 | 待上线环境 48h 执行填充 | 待判定 |
| 错误率 | 待上线环境 48h 执行填充 | 待判定 |
