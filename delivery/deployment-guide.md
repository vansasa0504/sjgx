# 部署手册

版本：0.1.0  
日期：2026-06-26  
维护者：Codex

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Maven | 3.9+ |
| Node.js | 20+ |
| Docker | 24+ |
| Kubernetes | 1.26+ |

## 本地构建

```bash
mvn clean package
cd platform-ui
npm install
npm run build
```

## 本地依赖

```bash
docker compose up -d
```

`docker-compose.yml` 提供 Nacos、Redis、MySQL、XXL-Job 等开发依赖。

## 后端镜像

```bash
docker build --build-arg MODULE=platform-gateway --build-arg PORT=8080 -t sjgx/platform-gateway:dev .
docker run -e SERVER_PORT=8080 -p 8080:8080 sjgx/platform-gateway:dev
```

`ARG MODULE` 可切换 `platform-auth`、`platform-partner`、`platform-pipeline`、`platform-quality`、`platform-billing`。

## Kubernetes 开发部署

```bash
kubectl apply -f k8s/dev/namespace.yaml
kubectl apply -f k8s/dev/configmap.yaml
kubectl apply -f k8s/dev/deployment-platform-a.yaml
kubectl apply -f k8s/dev/deployment-platform-b.yaml
kubectl apply -f k8s/dev/service.yaml
```

生产部署配置不在本任务范围，`k8s/prod/` 禁止由 Codex 修改。

## 配置项

| 变量 | 作用 | 默认/示例 |
|---|---|---|
| `SERVER_PORT` | Spring Boot 服务端口 | `8080` |
| `DB_TYPE` | 数据库方言选择 | `mysql` / `dm` / `oceanbase` |
| `SPRING_DATASOURCE_URL` | 数据库 JDBC URL | 环境注入 |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户 | 环境注入 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | 环境注入 |
| `SPRING_DATA_REDIS_HOST` | Redis 地址 | 环境注入 |
| `NACOS_SERVER_ADDR` | Nacos 地址 | 环境注入 |
| `SENTINEL_QPS` | 限流阈值 | 环境注入 |

## 数据库迁移

迁移脚本位于 `db/migration/V001__init_schema.sql` 至 `V009__perf_and_compat.sql`，回滚脚本位于 `db/rollback/`。开发期使用 H2/MySQL 模式验证，达梦/OceanBase 实测待上线环境执行。

## 国产数据库驱动

`pom.xml` 的 `national-db` profile 包含 OceanBase 驱动与达梦 provided 依赖：

```bash
mvn test -Pnational-db
```

达梦驱动通常需由机构制品库或镜像挂载提供，不应提交真实驱动包或密钥。

## 前端部署

```bash
cd platform-ui
npm run build
docker build -t sjgx/platform-ui:dev .
```
