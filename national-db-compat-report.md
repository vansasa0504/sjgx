# 国产数据库兼容性审查报告 - M5

本报告为静态审查 + H2(MySQL mode) 开发验证结果。达梦/OceanBase 实例未在当前开发环境提供，实测留待 M6。

## 驱动依赖

- OceanBase 驱动放入 Maven `national-db` profile：`com.oceanbase:oceanbase-client`。
- 达梦驱动放入 Maven `national-db` profile：`com.dameng:DmJdbcDriver18`，`provided`，部署时需机构制品库或镜像挂载。

## SQL 审查

| 脚本 | 结论 | 处理 |
|---|---|---|
| V001 初始化 | 标准类型，兼容 | 无需修改 |
| V002 合作方 | VARCHAR/BIGINT/TIMESTAMP，兼容 | 无需修改 |
| V003 接入 | `mapping_config`、`rule_config`、`t_raw_data.payload` 当前为 TEXT；部署达梦时按方言映射为 CLOB，避免 JSON 专有类型 | 无需修改 |
| V004 消费方 | 标准类型，兼容 | 无需修改 |
| V005 服务调用 | 新增 V009 `response_size`，补高频查询索引 | 已修复 |
| V006 数据目录 | `fields_json` 使用 CLOB，应用层解析，规避达梦 JSON 差异 | 已确认 |
| V007 质量存储 | JSON-like 字段使用 CLOB/VARCHAR | 已确认 |
| V008 治理审计 | CLOB detail，索引齐全 | 已确认 |
| V009 性能兼容 | response_size + invoke/audit 索引 | 新增 |

## 方言约束

- 不手写分页 `LIMIT`，分页交给 MyBatis-Plus 方言与 `${DB_TYPE}` 配置切换。
- `CURRENT_TIMESTAMP` 达梦/OceanBase 均支持。
- DB 直连接入限制为只读 SELECT，避免多语句和写操作方言风险。

## 待 M6 外部环境验证

- 达梦 DM8 全量 Flyway 迁移。
- OceanBase MySQL 模式全量 Flyway 迁移。
- 全量 Maven 集成测试分别指向达梦/OceanBase。
- 麒麟/UOS、X86/ARM 容器运行兼容性。
