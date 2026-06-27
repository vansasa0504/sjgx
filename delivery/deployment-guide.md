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

> 生产禁止使用 docker-compose 中的默认口令。`MYSQL_ROOT_PASSWORD`、`SFTP_PASSWORD`、`MINIO_ROOT_PASSWORD` 等必须由机构密钥管理系统或部署平台注入。

## 本地服务启动顺序

开发环境建议先启动依赖，再按端口启动后端服务，最后启动前端：

```bash
mvn clean package
java -jar platform-auth/target/platform-auth-0.1.0-SNAPSHOT-exec.jar
java -jar platform-partner/target/platform-partner-0.1.0-SNAPSHOT-exec.jar
java -jar platform-pipeline/target/platform-pipeline-0.1.0-SNAPSHOT-exec.jar
java -jar platform-quality/target/platform-quality-0.1.0-SNAPSHOT-exec.jar
java -jar platform-billing/target/platform-billing-0.1.0-SNAPSHOT-exec.jar
java -jar platform-gateway/target/platform-gateway-0.1.0-SNAPSHOT-exec.jar
cd platform-ui && npm run dev
```

默认端口：gateway `8080`、auth `8081`、partner `8082`、pipeline `8083`、quality `8084`、billing `8085`、ui `5173`。

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

| 变量 | 作用 | 默认/示例 | 生产要求 |
|---|---|---|---|
| `SERVER_PORT` | 单服务端口 | gateway 8080，其它服务 8081~8085 | 按部署编排注入 |
| `NACOS_ADDR` | Nacos 地址 | `localhost:8848` | 使用集群地址 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址端口 | `localhost` / `6379` | 使用高可用 Redis |
| `REDIS_POOL_MAX_ACTIVE` | Redis 最大连接数 | `32` / gateway `64` | 按压测调优 |
| `REDIS_POOL_MAX_IDLE` | Redis 最大空闲连接 | `16` / gateway `32` | 按压测调优 |
| `REDIS_POOL_MIN_IDLE` | Redis 最小空闲连接 | `4` / gateway `8` | 按压测调优 |
| `REDIS_POOL_MAX_WAIT_MS` | Redis 获取连接等待 | `2000` | 按 SLA 调优 |
| `AUTH_DB_URL` / `AUTH_DB_USERNAME` / `AUTH_DB_PASSWORD` | 认证库连接 | 本地 MySQL `sjgx` / `root` / `root` | 密码必须外部注入 |
| `PARTNER_DB_URL` / `PARTNER_DB_USERNAME` / `PARTNER_DB_PASSWORD` | 合作方库连接 | 本地 MySQL `sjgx` / `root` / `root` | 密码必须外部注入 |
| `PIPELINE_DB_URL` / `PIPELINE_DB_USERNAME` / `PIPELINE_DB_PASSWORD` | 管道库连接 | 本地 MySQL `sjgx` / `root` / `root` | 密码必须外部注入 |
| `BILLING_DB_URL` / `BILLING_DB_USERNAME` / `BILLING_DB_PASSWORD` | 计费库连接 | 本地 MySQL `sjgx` / `root` / `root` | 密码必须外部注入 |
| `DB_TYPE` | 数据库类型 | `MYSQL` | 达梦填 `DM`，OceanBase 填 `OCEANBASE` |
| `DB_POOL_MAX_SIZE` | Hikari 最大连接数 | `30` | 按并发压测调优 |
| `DB_POOL_MIN_IDLE` | Hikari 最小空闲连接 | `5` | 按并发压测调优 |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | 数据库连接超时 | `30000` | 按网络 SLA 调优 |
| `DB_POOL_IDLE_TIMEOUT_MS` | 数据库空闲超时 | `600000` | 按数据库策略调优 |
| `FLYWAY_ENABLED` | 是否自动迁移 | `true`，gateway/quality 默认 `false` | 生产由发布流程控制 |
| `JWT_SECRET` | JWT 签名密钥 | `change-me-in-env` | 必须替换为强随机密钥 |
| `JWT_TTL_SECONDS` | Token 有效期 | `3600` | 按机构策略配置 |
| `PARTNER_CREDENTIAL_KEY` | 合作方凭证加密密钥 | `change-me-in-env` | 必须替换为强随机密钥 |
| `DATA_ASSET_SM4_KEY` | 数据资产 SM4 密钥 | `0123456789abcdef` | 必须由密钥系统注入 |
| `INGEST_HTTP_CONNECT_TIMEOUT_SECONDS` | HTTP 接入连接超时 | `3` | 按合作方 SLA 配置 |
| `INGEST_HTTP_REQUEST_TIMEOUT_SECONDS` | HTTP 接入请求超时 | `10` | 按合作方 SLA 配置 |
| `SENTINEL_INGEST_QPS` | 接入限流 | `100` | 按压测结果配置 |
| `SENTINEL_SERVICE_QPS` / `SENTINEL_SERVICE_INVOKE_QPS` | 服务调用限流 | `1000` | 按压测结果配置 |
| `SENTINEL_AUTH_QPS` | 认证接口限流 | `100` | 按压测结果配置 |
| `SENTINEL_DEFAULT_QPS` | 网关默认限流 | `500` | 按压测结果配置 |
| `MYSQL_ROOT_PASSWORD` | docker-compose MySQL root 密码 | `root` | 生产禁止默认值 |
| `MYSQL_DATABASE` | docker-compose 默认库 | `sjgx` | 按环境命名 |
| `SFTP_USER` / `SFTP_PASSWORD` | docker-compose SFTP 账号 | `demo` / `demo` | 生产禁止默认值 |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | docker-compose MinIO 账号 | `minioadmin` / `minioadmin` | 生产禁止默认值 |
| `IMAGE_TAG` | k8s/dev 镜像标签 | 部署时注入 | 使用制品库不可变标签 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 地址 | `localhost:9092` | 使用集群地址 |

## 生产口令检查清单

- 禁止使用 `root/root`、`demo/demo`、`minioadmin/minioadmin`、`change-me-in-env`、`0123456789abcdef`。
- 所有数据库、JWT、SM4、合作方凭证密钥必须由环境变量、K8s Secret 或机构密钥系统注入。
- 生产发布前执行 `delivery/acceptance-report.md` 中的安全与兼容性待验证项。

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
