# 开发手册

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 模块结构

根 Maven 工程包含 `platform-common`、`platform-gateway`、`platform-auth`、`platform-partner`、`platform-quality`、`platform-pipeline`、`platform-billing`。前端位于 `platform-ui`。

## 开发规范

- Java 17，优先复用现有 Service/Repository/状态机模式。
- 不修改 `.env`、证书、`docs/`、`tasks/`、`reviews/`、`k8s/prod/`。
- SQL 走迁移脚本，新增 V00x 时同步 U00x。
- 外部配置使用 `${ENV_VAR}`，不硬编码真实密钥。
- 达梦/OceanBase 兼容避免手写 `LIMIT` 与数据库专有 JSON 类型。

## 新增协议适配器

实现 `platform-pipeline` 中的 `ProtocolAdapter`：

- 示例：`HttpAdapter`
- DB 示例：`DbAdapter`，只允许单条只读 SELECT
- 测试：参考 `ProtocolAdaptersTest`

## 新增格式转换器

实现 `FormatConverter`，参考 `JsonConverter`、XML/CSV/Excel 转换器。大文件格式应采用流式解析，避免一次性加载。

## 新增质量规则

在 `platform-quality` 增加 `QualityRule` 实现，并用 `QualityRuleConfig` 配置字段、阈值和维度。六维规则已有完整性、准确性、一致性、及时性、有效性、唯一性实现。

## 新增计费模型

在 `platform-billing` 扩展 `BillingModel` 与 `BillingRuleEngine`。账单生成入口为 `BillGenerator`，XXL-Job 调度入口为 `BillGeneratorJobHandler.billGenerate`。

## 新增统计任务

统计聚合入口为 `StatsAggregator`，XXL-Job 调度入口为 `StatsAggregatorJobHandler.statsAggregate`。统计快照用于大屏 `DashboardService`。

## 测试

```bash
mvn test
cd platform-ui
npm run test:unit
```
