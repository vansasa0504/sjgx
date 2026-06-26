# 升级回滚演练 - M6

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 目标

验证平台支持灰度发布、滚动升级和 10 分钟内回滚。当前开发环境无 Kubernetes 集群与真实流量，实测耗时待上线环境执行填充。

## 灰度发布流程

1. 准备新镜像：

```bash
docker build --build-arg MODULE=platform-gateway --build-arg PORT=8080 -t sjgx/platform-gateway:${NEW_TAG} .
```

2. 10% 灰度：

```bash
kubectl -n sjgx-dev set image deployment/platform-gateway platform-gateway=sjgx/platform-gateway:${NEW_TAG}
kubectl -n sjgx-dev scale deployment/platform-gateway --replicas=1
kubectl -n sjgx-dev rollout status deployment/platform-gateway --timeout=300s
```

3. 50% 灰度：

```bash
kubectl -n sjgx-dev scale deployment/platform-gateway --replicas=2
kubectl -n sjgx-dev rollout status deployment/platform-gateway --timeout=300s
```

4. 100% 发布：

```bash
kubectl -n sjgx-dev scale deployment/platform-gateway --replicas=4
kubectl -n sjgx-dev rollout status deployment/platform-gateway --timeout=300s
```

## 健康检查与流量验证

每个阶段执行：

```bash
curl -fsS http://gateway.sjgx.local/actuator/health
jmeter -n -t perf/jmeter/m5-performance.jmx \
  -JbaseUrl=http://gateway.sjgx.local \
  -JserviceThreads=10 \
  -JserviceLoops=100 \
  -JresultFile=perf/results/m6-canary.jtl
```

判定：

- `/actuator/health` 为 UP。
- 关键接口无 5xx。
- 错误率低于 0.1%。
- P95/P99 无突增。

## 自动回滚触发条件

- 健康检查失败。
- 5xx 错误率超过阈值。
- Pod CrashLoopBackOff。
- 关键接口鉴权、服务调用、目录查询异常。

触发命令：

```bash
NS=sjgx-dev DEPLOYMENT=platform-gateway bash delivery/rollback-timer.sh
```

目标：`ROLLBACK_SECONDS <= 600`。

## 实测记录

| 阶段 | 开始时间 | 结束时间 | 健康状态 | 5xx 错误率 | 结论 |
|---|---|---|---|---:|---|
| 10% | 待上线环境执行填充 | 待上线环境执行填充 | 待填充 | 待填充 | 待判定 |
| 50% | 待上线环境执行填充 | 待上线环境执行填充 | 待填充 | 待填充 | 待判定 |
| 100% | 待上线环境执行填充 | 待上线环境执行填充 | 待填充 | 待填充 | 待判定 |
| 回滚 | 待上线环境执行填充 | 待上线环境执行填充 | 待填充 | 待填充 | 待判定 |

## 偏差说明

`k8s/dev` 当前为双活模拟配置，M5 遗留的各微服务独立 Deployment 属上线环境细化项；本演练脚本以 `DEPLOYMENT` 参数适配 gateway/auth/partner/pipeline/quality/billing 任一服务。
