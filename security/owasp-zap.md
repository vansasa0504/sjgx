# OWASP ZAP 运行说明

当前开发环境未提供可访问的完整部署地址，M5 不虚构扫描结果。本文件用于 M6 外部环境执行。

## 目标

- 默认目标：`${ZAP_TARGET_URL:-http://localhost:8080}`
- 认证 Token：`${ZAP_AUTH_TOKEN}`
- 报告输出：`security/reports/zap-m5.html`

## 命令

```bash
docker run --rm -t \
  -v $(pwd)/security/reports:/zap/wrk \
  ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
  -t ${ZAP_TARGET_URL:-http://localhost:8080} \
  -r zap-m5.html \
  -J zap-m5.json
```

## 策略

- 覆盖 SQL 注入、XSS、CSRF、认证绕过、敏感信息泄露。
- 高危告警必须修复后复扫。
- 实测结果待 M6 外部环境填充。
