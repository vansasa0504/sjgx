# Heap 趋势分析方法 - M6

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 目标

用于 48h 稳定性测试期间判断 JVM Heap 是否存在持续泄漏风险。当前开发环境不执行 48h 实测，趋势结论待上线环境填充。

## 数据来源

- `perf/monitor/collect-metrics.sh` 采集 `/actuator/metrics/jvm.memory.used`。
- JVM GC 日志由 `perf/jvm.args` 中的 `-Xlog:gc*` 输出。
- JMeter 结果使用 `perf/jmeter/m5-performance.jmx` 的 JTL 文件。

## 判定方法

1. 按 5 分钟窗口聚合 Heap used。
2. 标记每次 Full GC 或老年代明显回收后的 Heap 基线。
3. 对比连续多轮 GC 后的基线，如果基线持续抬升且无法回落，判定为疑似泄漏。
4. 结合 TPS、P95/P99、错误率判断是否存在性能衰减。

## 通过标准

- 48h 内无 JVM 进程退出。
- 多轮 GC 后 Heap 基线不持续上涨。
- GC 暂停未出现持续恶化。
- TPS 波动小于 10%。
- 错误率小于 0.1%。

## 待上线环境填充

| 项目 | 实测值 | 结论 |
|---|---:|---|
| 起始 Heap 基线 | 待上线环境执行填充 | 待判定 |
| 结束 Heap 基线 | 待上线环境执行填充 | 待判定 |
| Full GC 次数 | 待上线环境执行填充 | 待判定 |
| 最大 GC 暂停 | 待上线环境执行填充 | 待判定 |
| TPS 波动 | 待上线环境执行填充 | 待判定 |
