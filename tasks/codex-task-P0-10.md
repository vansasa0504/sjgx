# Codex 执行任务 - P0-10：真实依赖 E2E

> 阶段：P0（上线阻断修复·收尾门禁）
> 任务编号：P0-10
> 分支：`ai/p0-e2e-real-deps`（已从 master 切出，master 已含 P0-01~P0-09）
> 依据：`tasks/codex-task-P0-10-e2e-real-deps.md`、`docs/development-process-workflow.md` §7.2、§9
> 前置：P0-01~P0-09 全部合入 master
> 日期：2026-06-29

---

## 1. 背景与目标

M7-D 的 10 步主链路基于内存仓储 + 部分依赖（仅 MySQL/Redis/Nacos），未验证真实 DB（JDBC 落库）、MQ（Kafka/RabbitMQ）、MinIO（对象存储）。M7-D 完成报告指出 Pipeline health=DOWN（RabbitMQ 未启动）。P0-03~P0-09 完成落库、事实源、审计防篡改、前端边界后，须用真实依赖重新验证 10 步主链路，作为 P0 阶段收尾门禁（流程文档 §9 持久化/安全/测试/合规门禁）。

**现状核查（2026-06-29）**：
- docker-compose 已配齐 MySQL8/Redis7/Nacos/Kafka/RabbitMQ/MinIO/SFTP/ES/XXL-Job。
- **MQ 代码存在但未装配**：`AsyncInvokeLogWriter` 有 Kafka 构造器，但 `DataServiceManager` 只走 `new AsyncInvokeLogWriter(jdbcTemplate)`（JDBC 路径），Kafka 路径未启用；`MqAdapter`(RabbitMQ) 无 Bean 装配，未注册到 IngestService。yml 无 kafka/rabbitmq 配置。
- **MinIO 未接入**：`ColdStorageStore` 只有 `LocalColdStorageStore`（本地文件），无 MinIO 客户端；storage 全手动 new，无 Spring Bean 装配。
- **Testcontainers 无依赖**：全新引入。现有 `M5EndToEndIntegrationTest` 是内存仓储单元级集成，非容器化。
- **无 E2E 脚本**：M7-D 证据是手动 curl 记录在报告，无 .sh 脚本。
- **jdbc profile**：靠 `@Autowired(required=false) JdbcTemplate` + 环境变量切换，yml 无 spring.profiles 配置。

**最小可行结果**：
1. Kafka（调用日志异步写）+ RabbitMQ（MQ 协议接入适配器）真实接通，yml 配置 + Bean 装配 + 启动验证。
2. MinIO 冷存储实现（MinioColdStorageStore），接真实 MinIO，Local 保留为无 MinIO 回退。
3. Testcontainers 集成测试覆盖真实依赖 profile 下主链路关键端点。
4. 10 步主链路 E2E 脚本（jdbc profile + 真实依赖），curl 证据。
5. 重启恢复验证（持久化门禁）。
6. trace_id 全链路 + audit hash 链 verify（合规/安全门禁）。

## 2. 范围

### 本次实现

#### 2.1 Kafka 真实化（调用日志异步）
- `AsyncInvokeLogWriter`：Kafka 路径装配——当 KafkaTemplate bean 存在时用 Kafka 构造器，否则回退 JDBC/内存。
- `application.yml`（pipeline）：补 `spring.kafka.bootstrap-servers` + topic 配置（`pipeline.invoke-log.topic`）。
- PipelineApplication 装配：`DataServiceManager` 在有 KafkaTemplate 时注入 Kafka 路径 AsyncInvokeLogWriter。
- 验证：invoke 调用日志经 Kafka 异步落 `t_service_invoke_log`（与 P0-05 事实源一致）。

#### 2.2 RabbitMQ 真实化（MQ 协议接入）
- `MqAdapter` 装配为 Bean（RabbitTemplate 存在时）。
- `application.yml`（pipeline）：补 `spring.rabbitmq.host/port/username/password/virtual-host`。
- IngestService 注册 MQ 协议适配器（或按需启用），验证 MQ 协议接入任务可拉取 RabbitMQ 消息。
- 解决 M7-D Pipeline health=DOWN（RabbitMQ 未启动→接通后 UP）。

#### 2.3 MinIO 冷存储
- 新增 `MinioColdStorageStore implements ColdStorageStore`：用 MinIO 客户端（`io.minio:minio` 依赖）put/get 对象。
- `application.yml`（pipeline）：补 MinIO endpoint/accessKey/secretKey/bucket（`storage.minio.*`）。
- storage Bean 装配：MinIO 配置存在时用 MinioColdStorageStore，否则回退 LocalColdStorageStore。
- LocalColdStorageStore 保留（无 MinIO 环境回退 + 测试用）。

#### 2.4 jdbc profile 默认化
- 各模块 `application.yml`：datasource 默认指向 docker-compose MySQL；flyway enabled=true（P0-01 已修复）。
- 启动命令用 `--spring.profiles.active=jdbc`（或环境变量）启用真实 DB。
- 内存回退保留（测试/无 DB 环境）。

#### 2.5 Testcontainers 集成测试
- 父 pom + platform-pipeline（或 platform-billing）pom 引入 testcontainers 依赖（mysql/kafka/rabbitmq/minio + junit-jupiter）。
- 新增 `*IT.java`：Testcontainers 启动 MySQL + Kafka + RabbitMQ + MinIO，jdbc profile 跑主链路关键端点：
  - partner create→submit→approve（持久化）
  - service invoke → 调用日志落库（Kafka 异步）
  - billing generate 从日志聚合
  - audit hash 链 verify（intact）
  - MinIO 冷存储 write→read
- 测试隔离（@Testcontainers + 临时容器），不依赖外部 docker-compose。

#### 2.6 10 步 E2E 脚本
- 新增 `tasks/e2e-p0.sh`：基于 M7-D 10 步（见 reviews/m7d-completion-report.md §F-03），改用 jdbc profile + 真实依赖，curl 序列：
  1. 登录 + permissions
  2. 合作方 create→interface→submit→approve→admit
  3. 接入任务 create→test→records→submit→approve
  4. 数据服务 register→define→test→publish→invoke（apiKey+签名）→logs
  5. 消费方 register→submit→approve→quota→audit→logs
  6. 数据质量 rule→check→issues→resolve
  7. 计费 rule→generate（从日志聚合）→confirm→stats
  8. 统计 dashboard→audit（trace_id 链路 + hash 链 verify）
  9. 系统 users→roles→permissions
  10. 权限校验：低权限用户越权 403
- 每步记录请求 + 响应（脱敏 token/secret）。

#### 2.7 重启恢复验证
- E2E 写入数据 → `docker compose restart` 后端 → 重新 GET 主链路数据 → 仍在（持久化门禁）。

### 不做

- 不做性能压测（P2-02）。
- 不做故障注入（P2-03）。
- 不重复 P0-03~P0-09 的单元/MockMvc 测试。
- 不改前端（P0-09 已闭环）。

## 3. 必读输入

- `AGENTS.md`、`docs/development-process-workflow.md` §7.2、§9
- `docs/implementation-gap-and-test-plan.md`
- `reviews/m7d-completion-report.md`（M7-D 10 步 E2E 基础，§F-03）
- `docker-compose.yml`、`tasks/dev-progress.md` §5（启动命令）
- P0-01~P0-09 任务单与审查报告
- `platform-pipeline/src/main/java/.../service/AsyncInvokeLogWriter.java`、`DataServiceManager.java`、`ingest/adapter/MqAdapter.java`、`storage/tier/*`、`ingest/PipelineApplication.java`

## 4. 需要修改的模块

| 模块 | 改动 |
|---|---|
| platform-pipeline | AsyncInvokeLogWriter Kafka 装配、MqAdapter Bean 装配、MinioColdStorageStore 新增 + storage Bean 装配、PipelineApplication 装配调整 |
| platform-*/src/main/resources | application.yml 补 kafka/rabbitmq/minio 配置 + jdbc profile 默认化 |
| pom.xml（父 + pipeline） | 引入 minio 客户端 + testcontainers 依赖 |
| platform-pipeline/src/test/it（新增） | Testcontainers 集成测试 *IT.java |
| tasks/ | 新增 e2e-p0.sh |

## 5. 数据库/API/前端影响

- **数据库**：真实 MySQL（jdbc profile），flyway 启用（V001~V016）。
- **API**：无新增；验证既有端点在真实依赖下行为。
- **前端**：无改动。
- **中间件**：Kafka（调用日志）、RabbitMQ（MQ 接入）、MinIO（冷存储）真实接通。

## 6. 必须补充的测试

- **Testcontainers 集成测试**（*IT.java）：MySQL + Kafka + RabbitMQ + MinIO 容器，jdbc profile 跑主链路关键端点（partner 持久化、service invoke+日志、billing 聚合、audit verify、MinIO 冷存储读写）。
- **10 步 E2E 脚本**：curl 序列，每步请求 + 响应证据（脱敏）。
- **重启恢复测试**：写数据 → restart → 数据仍在。
- **trace_id 全链路测试**：一次主链路按 trace_id 查全事件（P0-05 + P0-08 联合）。
- **hash 链 verify**：E2E 后调 `/stats/audit/verify` 通过。
- **Kafka 调用日志**：invoke 后日志经 Kafka 落 t_service_invoke_log。
- **MinIO 冷存储**：write→read 对象存在。

## 7. 验收命令

```bash
# 1. 启动全部依赖
docker compose up -d
# 2. 启动后端（jdbc profile + 真实 MQ/MinIO）
mvn -pl platform-auth spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=jdbc --server.port=8081"
# （其余服务同 dev-progress.md §5.4，启用 flyway + kafka/rabbitmq/minio）
# 3. 启动前端
cd platform-ui && npm run dev
# 4. E2E 10 步
bash tasks/e2e-p0.sh
# 5. Testcontainers 集成测试
mvn test -Dspring.profiles.active=jdbc -Dtest="*IT"
# 6. 全量回归
mvn test "-Dspring.profiles.active=jdbc"
npm run test:unit
# 7. 重启恢复
docker compose restart
# 重新 GET 主链路数据 → 仍在
```

## 8. M7 衔接

- **M7-D D2-01**：端到端基于内存 → 本任务改真实依赖。
- **M7-D 完成报告 §6**：Pipeline health=DOWN（RabbitMQ 未启动）→ 本任务接通 RabbitMQ。
- **M7-D D2-05 上线前项**：真实依赖验证属上线门禁，本任务闭环。
- 复用 M7-D 10 步 E2E，升级为 jdbc + 真实依赖版本。

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| 真实依赖启动不稳定（版本/端口/卷） | docker-compose 固定版本；Testcontainers 隔离；启动顺序 healthcheck |
| jdbc profile 暴露 P0-03 落库并发/事务问题 | 本任务发现则返工对应 P0 任务（流程文档 §10） |
| Kafka/RabbitMQ/MinIO 依赖引入范围大 | 仅 pipeline 模块引入；Bean 装配用 `@Autowired(required=false)` 回退；无对应依赖时退回 JDBC/Local |
| Testcontainers 在 CI/Windows 环境 Docker 不可用 | IT 测试用 `@EnabledIfEnvironmentVariable` 或 surefire 分离，Docker 不可用时跳过（标注，不阻断主测试） |
| AsyncInvokeLogWriter Kafka 路径与 JDBC 路径行为不一致 | Kafka 异步落库后仍写同一 t_service_invoke_log（P0-05 事实源）；测试验证最终一致 |
| **回滚** | jdbc profile 可切回 memory；MQ/MinIO Bean 缺失自动回退；docker-compose 可停服；新增依赖在父 pom 可移除 |

## 10. 完成判定

- [ ] Kafka 调用日志异步落库接通（invoke → t_service_invoke_log）。
- [ ] RabbitMQ MQ 协议接入接通，Pipeline health=UP。
- [ ] MinIO 冷存储实现 + Bean 装配，write→read 通过。
- [ ] jdbc profile 默认化，flyway V001~V016 真实迁移。
- [ ] Testcontainers 集成测试通过（*IT）。
- [ ] 10 步主链路 E2E 脚本走通，curl 证据。
- [ ] 重启恢复测试通过（持久化门禁）。
- [ ] trace_id 全链路 + hash 链 verify 通过（合规/安全门禁）。
- [ ] `mvn test` + `npm run test:unit` 全绿。
- [ ] 输出 P0 阶段验收材料：10 步证据 + 重启恢复证据 + Testcontainers 结果 + P0 总体结论。
- [ ] P0 全部门禁（功能/数据库/持久化/安全/测试/合规）达成，可进入 P1。

## 11. 实现边界（Codex 遵守）

1. MQ/MinIO 接通用 `@Autowired(required=false)` 回退，无依赖时不阻断启动（与既有 CatalogService/ApiCredentialRepository 模式一致）。
2. Testcontainers 依赖仅引入需要的（mysql/kafka/rabbitmq/minio + junit-jupiter），不引入全量。
3. minio 客户端用 `io.minio:minio`（官方），版本与 Spring Boot 3.2.5 兼容。
4. IT 测试与单元测试分离（surefire `<excludes>*IT</excludes>` 或 profile），Docker 不可用时跳过 IT 不阻断主测试。
5. E2E 脚本证据脱敏 token/secret，不写真实密钥。
6. 不改前端、不重复 P0-03~P0-09 单元测试、不做性能/故障注入。
7. 不改密钥/生产配置；不动无关模块。
8. AsyncInvokeLogWriter Kafka 路径须保证最终落 t_service_invoke_log（与 P0-05 事实源一致），不破坏既有 billing 聚合。
9. 必须补测试并全绿（IT 可条件跳过，但主测试全绿）。
10. 完成后输出修改文件、测试命令、测试结果、10 步 E2E 证据、重启恢复证据、P0 总体结论、潜在风险。

## 12. P0 收尾说明

本任务是 P0 阶段最后一个任务，完成后 P0 全部门禁达成，可进入 P1（增强）。输出材料须支撑 P0 阶段验收：
- 10 步主链路真实依赖证据
- 重启恢复（持久化门禁）
- trace_id + hash 链（合规/安全门禁）
- Testcontainers（测试门禁）
- P0-01~P0-10 总体结论（各任务闭环状态 + 上线前必做项汇总，如 P0-08 H-1 DB REVOKE runbook）
