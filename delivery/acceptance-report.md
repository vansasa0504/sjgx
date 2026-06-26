# 验收报告 - M6

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 说明

本报告对照 `docs/requirements.md` 第 7 节。当前开发环境可验证项以本地测试为准；48h 稳定性、故障演练、升级回滚、达梦/OceanBase 实测、ZAP 扫描、性能压测实测均标注为待上线环境执行，不编造数据。

## 功能验收

| FR | 模块 | 覆盖情况 | 测试/证据 |
|---|---|---|---|
| FR-101~104 | 合作方管理 | 已实现 | `platform-partner` 生命周期/接口凭证/质量监控骨架测试 |
| FR-201~205 | 外部数据接入 | 已实现 | `platform-pipeline` 协议适配、格式转换、同步、断点续传测试 |
| FR-301~305 | 外部数据服务 | 已实现 | 服务生命周期、签名鉴权、限流/日志测试 |
| FR-401~405 | 数据目录 | 已实现 | 目录、元信息、检索、申请流程代码与前端页面 |
| FR-501~505 | 消费方管理 | 已实现 | 消费方生命周期、配额拦截、审计测试 |
| FR-601~606 | 缓存存储再利用 | 已实现基础闭环 | 缓存、分级、ETL、集市、生命周期测试 |
| FR-701~705 | 计费管理 | 已实现，外部财务对接为 Mock | `BillingGovernanceTest`、账单状态流转、适配器测试 |
| FR-801~805 | 统计监管 | 已实现，监管报送为 Mock | 统计聚合、报表生成、Dashboard 测试 |
| FR-901~906 | 数据质量 | 已实现 | 六维规则、校验、工单、报告、评分测试 |

功能覆盖率：开发实现覆盖 46 条 FR；最终覆盖率待 Claude Code 结合代码审查确认。  
用例覆盖率：开发环境单元/集成/前端测试可运行；精确覆盖率待 CI 覆盖率工具统计。  
核心通过率：以本次 `mvn test`、`npm run test:unit` 结果为准。

## 性能验收

| NFR | 要求 | 状态 |
|---|---|---|
| NFR-P01 | 接口响应 P50/P95/P99 | 待上线压测，脚本 `perf/jmeter/m5-performance.jmx` |
| NFR-P02 | 1000TPS/峰值2000TPS | 待上线压测 |
| NFR-P03 | 100万条/100MB/s/断点续传 | 待上线压测 |
| NFR-P04 | 日均10亿/单节点1万条秒 | 待上线压测 |
| NFR-P05 | 加工延迟 <=5min | 待上线压测 |
| NFR-P06 | 缓存命中率/查询/容量 | 待上线压测 |
| NFR-P07 | 千万/亿级查询 | 待上线压测 |

报告模板：`perf/report-template.md`。M6 稳定性方案：`delivery/stability-test-plan.md`。

## 可用性验收

| NFR | 要求 | 状态 |
|---|---|---|
| NFR-A01 | 系统可用性 99.95% | 待上线 48h 稳定性执行 |
| NFR-A02 | 核心服务 99.99% | 待上线故障演练 |
| NFR-A03 | 节点切换/集群恢复 | 待执行 `delivery/chaos-drill/node-down.sh` |
| NFR-A04 | 同城双活 RPO/RTO | 待执行 `dual-active-switch.sh` |
| NFR-A05 | 灰度升级回滚 | 待执行 `delivery/rollback-timer.sh` |

## 安全验收

| NFR | 要求 | 状态 |
|---|---|---|
| NFR-S01 | MFA/IAM/RBAC+ABAC/OAuth2/API Key | RBAC/API Key/签名已实现，MFA/IAM 联调待外部系统 |
| NFR-S02 | TLS/SM4/脱敏/审计 | SM4、脱敏、审计代码已实现，TLS 与不可篡改留存策略待上线配置复核 |
| NFR-S03 | SQL注入/XSS/CSRF/防刷 | SQL 白名单、XSS 过滤、Sentinel 配置已实现，ZAP 扫描待上线执行 |

安全材料：`security/owasp-zap.md`、`security/manual-pentest-checklist.md`。

## 兼容性验收

| NFR | 要求 | 状态 |
|---|---|---|
| NFR-C01 | 国产 OS/DB/X86/ARM | `national-db` profile 与报告已提供，达梦/OceanBase/麒麟/UOS 实测待执行 |
| NFR-C02 | 外部系统对接 | 财务/监管适配器 Mock，真实接口待采购方规范 |
| NFR-C03 | 浏览器/移动端 | 前端单测通过，跨浏览器实测待上线测试 |

## 文档验收

| 文档 | 路径 | 状态 |
|---|---|---|
| 系统架构文档 | `delivery/system-architecture.md` | 已提供 |
| 部署手册 | `delivery/deployment-guide.md` | 已提供 |
| 运维手册 | `delivery/ops-manual.md` | 已提供 |
| 开发手册 | `delivery/dev-guide.md` | 已提供 |
| 用户操作手册 | `delivery/user-guide.md` | 已提供 |

## 代码质量验收

- 符合 `docs/requirements.md`、`tasks/claude-plan.md`、`tasks/codex-task-M6-execute.md` 的 M6 范围。
- M5 遗留 P-01M5、P-02M5、P-03M5、P-05M5、P-06M5、P-08M5 已修复。
- P-04M5、P-07M5 属上线环境验证或部署细化项，已在本报告标注待执行。
- 未修改 `.env`、证书、`docs/`、`tasks/`、`reviews/`、`k8s/prod/`。

## 总体结论

开发环境可交付材料已齐备，测试命令执行结果见 Codex 阶段完成报告。最终验收仍需上线环境完成以下项目后由 Claude Code 判定：

- 48h 稳定性实测。
- 5 类故障演练实测。
- 灰度升级与回滚实测。
- NFR-P01~P07 性能压测实测。
- 达梦/OceanBase 与国产 OS 实测。
- OWASP ZAP 扫描与等保相关复核。
