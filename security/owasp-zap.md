# OWASP ZAP DAST 运行说明

P2-04 不在开发环境虚构 DAST 结果。ZAP 全扫描必须在可访问的部署测试环境执行，本文件和 `security/run-zap.sh` 仅提供参数化执行方式与扫描策略。

## 参数

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ZAP_TARGET_URL` | `http://localhost:8080` | 待扫描的部署测试环境地址 |
| `ZAP_AUTH_TOKEN` | 空 | 可选 Bearer token；需要认证扫描时填写测试账号 token |
| `ZAP_REPORT_DIR` | `security/reports` | 报告输出目录 |
| `ZAP_REPORT_BASENAME` | `zap-p2-04` | 报告文件名前缀 |

## 命令

```bash
ZAP_TARGET_URL=https://test.example.local \
ZAP_AUTH_TOKEN=replace-with-test-token \
bash security/run-zap.sh
```

脚本会生成：

- `security/reports/zap-p2-04.html`
- `security/reports/zap-p2-04.json`

## 扫描策略

基础扫描覆盖：

- 认证与授权：无 token、低权限 token、过期 token、越权访问。
- 注入类：SQL 注入、命令注入、路径遍历。
- XSS：查询参数、Header、常见表单字段；JSON body 需结合手工用例复核。
- CSRF：前后端分离 JWT/API Key 调用不依赖 Cookie Session，部署时复核响应头和跨站限制。
- 敏感信息泄露：响应、错误页、Header、静态资源。

建议认证扫描重点端点：

- `/api/v1/consumers/{id}/logs`
- `/api/v1/services/{serviceCode}/logs`
- `/api/v1/services/{serviceCode}/credentials`
- `/api/v1/catalog/{id}/preview`
- `/api/v1/ingest/tasks/{id}/check`

## 结果处置

- High/Critical：必须修复或提供可验证缓解措施后复扫。
- Medium：评估业务可利用性，形成修复计划。
- Low/Informational：记录并纳入安全基线。

P2-04 报告只记录 SCA 和开发单测实测结果；ZAP DAST 结果待部署环境补测后追加。
