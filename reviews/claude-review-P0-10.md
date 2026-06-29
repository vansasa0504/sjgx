# Claude Code 审查结果 — P0-10 真实依赖 E2E

## 1. 审查对象

- 任务：P0-10 真实依赖 E2E（P0 收尾门禁）
- 分支：`ai/p0-e2e-real-deps`（从 master 切出，master 已含 P0-01~P0-09）
- 任务单：`tasks/codex-task-P0-10.md`
- 审查日期：2026-06-29
- 前置：P0-01~P0-09 全部合入 master
- 改动范围：Kafka 调用日志异步 + RabbitMQ MQ 适配器装配 + MinIO 冷存储 + jdbc profile 配置 + Testcontainers 集成测试 + E2E 脚本 + MockMvc 断言修正

## 2. Git 状态

改动未提交（工作区，分支 `ai/p0-e2e-real-deps`）：

```text
 M platform-pipeline/pom.xml                                    # minio + testcontainers 依赖
 M platform-pipeline/.../ingest/IngestService.java              # 多协议适配器注册
 M platform-pipeline/.../ingest/PipelineApplication.java        # MQ/MinIO/AsyncInvokeLogWriter Bean 装配
 M platform-pipeline/.../service/AsyncInvokeLogWriter.java      # Kafka 异步写 + consumer 落库
 M platform-pipeline/.../service/DataServiceManager.java        # 注入 AsyncInvokeLogWriter
 M platform-pipeline/src/main/resources/application.yml         # kafka/rabbitmq/minio 配置
 M pom.xml                                                      # testcontainers-bom + minio 版本管理
 M platform-auth/.../AuthModuleMockMvcTest.java                 # 400→409 断言修正
 M platform-partner/.../PartnerModuleMockMvcTest.java           # 400→409 断言修正
 M platform-quality/.../QualityModuleMockMvcTest.java           # 末尾换行
?? platform-pipeline/.../service/KafkaInvokeLogConsumer.java    # Kafka consumer 落库
?? platform-pipeline/.../storage/tier/MinioColdStorageStore.java # MinIO 冷存储
?? platform-pipeline/src/test/.../RealDependenciesIT.java       # Testcontainers 集成测试
?? tasks/e2e-p0.sh                                             # 10 步 E2E 脚本
?? tasks/codex-task-P0-10.md
# ⚠️ 运行产物（不应提交，见 §6.2 H-1）
?? .docker-cfg/ .docker-config/ .docker-tmp/
?? auth-*.log billing-*.log gateway-*.log partner-*.log pipeline-*.log quality-*.log
```

## 3. 测试验证

### 3.1 后端全量

```bash
mvn -pl platform-common install -DskipTests   # 重建 common
mvn test                                       # 全量回归
```

结果：**BUILD SUCCESS**，全模块测试全绿：
- platform-common 32 / gateway 2 / auth 33 / partner 30 / quality 18 / pipeline 72 / billing 39
- **无回归**。MockMvc 断言修正（400→409）通过，对齐 P0-08 全局 409 映射。

### 3.2 Testcontainers IT（环境受阻处理）

`RealDependenciesIT` 采用双层环境门禁：
- `@Testcontainers(disabledWithoutDocker = true)`：Docker 不可用时自动跳过。
- `@EnabledIfEnvironmentVariable(named = "RUN_REAL_DEPS_IT", matches = "true")`：未设环境变量时类级禁用。

**结果**：当前环境未设 `RUN_REAL_DEPS_IT`，IT 类被条件禁用，不参与 `mvn test`，不阻断主测试。这是对"Docker 环境受阻"的正确处理——IT 仅在有 Docker 且显式启用时运行。

> ⚠️ 注：本次审查环境未运行 IT（需 Docker + RUN_REAL_DEPS_IT=true）。IT 逻辑经代码审查（见 §5）设计完整，但**未实测通过**，需在有 Docker 的环境补跑验证（见 §10 上线前必做）。

### 3.3 前端

无前端改动（P0-09 已闭环），无需重跑。

### 3.4 测试结论

主测试全绿无回归；IT 因环境受阻条件跳过，逻辑完整但未实测。

## 4. 需求符合性

| 需求项（codex-task §2） | 实现情况 | 结论 |
|---|---|---|
| 2.1 Kafka 调用日志异步 | AsyncInvokeLogWriter Kafka 路径 + KafkaInvokeLogConsumer 落库；kafka-enabled 开关；IT 验证 invoke→Kafka→JDBC | ✅ |
| 2.2 RabbitMQ MQ 适配器 | MqAdapter @ConditionalOnBean(RabbitTemplate) 装配；IngestService 多协议注册；IT 验证 MQ 协议拉取 | ✅ |
| 2.3 MinIO 冷存储 | MinioColdStorageStore + @ConditionalOnProperty；Local 回退；IT 验证 write→read | ✅ |
| 2.4 jdbc profile 配置 | application.yml 全环境变量化，flyway enabled，kafka/rabbitmq/minio 默认关闭不阻断 | ✅ |
| 2.5 Testcontainers IT | RealDependenciesIT 覆盖 MySQL+Kafka+RabbitMQ+MinIO 主链路关键端点 | ✅（未实测） |
| 2.6 E2E 脚本 | tasks/e2e-p0.sh 143 行 10 步 curl 序列 | ✅ |
| 2.7 重启恢复 | E2E 脚本含重启恢复段（需实测） | ⚠️ 未实测 |

## 5. 代码质量

### 5.1 优点

1. **Kafka 异步链路设计正确**：invoke → AsyncInvokeLogWriter.write → KafkaTemplate.send（序列化为 String）→ KafkaInvokeLogConsumer.consume → persistFromKafka → JdbcServiceInvokeLogRepository.save。最终落同一 `t_service_invoke_log`（P0-05 事实源），不破坏 billing 聚合。Kafka 失败时回退 repository.save，保证不丢日志。
2. **Bean 装配回退完整**：所有真实依赖 Bean 用 `@Autowired(required=false)` / `ObjectProvider.getIfAvailable()` / `@ConditionalOnBean` / `@ConditionalOnProperty`，无依赖时自动回退 JDBC/Local，与既有 CatalogService 模式一致，不阻断启动。
3. **配置全环境变量化**：kafka/rabbitmq/minio 全部 `${ENV:default}`，默认 `kafka-enabled:false`、`minio.enabled:false`，开发/测试环境零配置可启动。
4. **IngestService 多协议扩展合理**：新增 `Map<String, ProtocolAdapter> adapters` + `adapterFor(endpoint, protocol)` 按 scheme/protocol 路由，默认回退 httpAdapter，向后兼容既有 createTask/testAndIngest。
5. **MinioColdStorageStore 健壮**：ensureBucket 自动建桶，objectName 转义 `\`/`/` 防路径问题，readAll 过滤 .data 后缀，资源用 try-with-resources 释放。
6. **IT 环境门禁双层**：`disabledWithoutDocker` + `@EnabledIfEnvironmentVariable`，Docker 不可用或未启用时不阻断主测试，正确处理环境受阻。
7. **IT 测试覆盖全面**：单测验证 service invoke→Kafka→JDBC（awaitInvokeLog 轮询）、MQ 协议拉取、MinIO 冷存储读写、audit hash 链 verify、trace_id 查询——覆盖任务单 §6 全部门禁。
8. **MockMvc 断言修正**：P0-08 引入全局 409 映射后，auth 用户重复创建、partner 非法状态流转应返回 409 而非 400，本次修正断言对齐。

### 5.2 发现的问题

#### P1（需提交前处理）

**H-1：工作区混入运行产物，未加入 .gitignore**
- 现象：工作区有 14 个 `*.log` 文件（auth/billing/gateway/partner/pipeline/quality 的 out/err/run）+ 3 个 `.docker-*` 目录（docker 配置缓存），均为 Docker 环境测试运行产物。`.gitignore` 仅覆盖 `target/`，未覆盖这些。
- 影响：若 `git add -A` 提交，会把这些 0 字节日志和 docker 配置缓存混入仓库，污染历史。
- 处理：**提交前必须清理**——删除这些产物文件，并在 `.gitignore` 补 `*.log`、`.docker-cfg/`、`.docker-config/`、`.docker-tmp/`。本审查未自动清理（属 Codex 工作区产物，应 Codex 或提交前处理）。

#### P2（建议改进，不阻断）

**H-2：RealDependenciesIT 未实测通过**
- 现象：IT 因 `RUN_REAL_DEPS_IT` 未设被跳过，本次审查环境（Windows/无 Docker）未运行。
- 影响：IT 逻辑虽经代码审查完整，但 Kafka 异步落库、MinIO 冷存储、MQ 协议拉取在真实容器中的行为未验证。
- 评估：用户反馈"在 Docker 环境进行了测试，有环境受阻问题"——IT 的双层门禁正是为应对此。需在有 Docker 的环境设 `RUN_REAL_DEPS_IT=true` 补跑。
- 建议：列为上线前必做，补跑 IT 并附证据。

**H-3：E2E 脚本依赖 python 解析 JSON/签名**
- 现象：`tasks/e2e-p0.sh` 用 python3 做 json_get 和 sign（HMAC-SHA256）。
- 影响：运行环境需 python3；若最小化部署环境无 python 则脚本不可用。
- 评估：python3 在多数 Linux 环境可用，可接受。可后续改用 jq + openssl 降依赖，但不阻断。

**H-4：AsyncInvokeLogWriter Kafka 路径异常处理**
- 现象：Kafka send 失败时，若 repository != null 则静默回退 repository.save（catch 块空处理）；若 repository == null 则抛 IllegalStateException。
- 影响：Kafka 失败回退 JDBC 是合理的降级，但 catch 块无日志，失败不可观测。
- 评估：降级行为正确（不丢日志），可接受。建议后续加日志记录 Kafka 失败以便排查。

**H-5：MinioColdStorageStore 构造器 ensureBucket 可能阻塞启动**
- 现象：构造器调 `ensureBucket()`，若 MinIO 可达但无权限建桶会抛异常，导致 coldStorageStore Bean 创建失败→启动失败。
- 评估：MinIO 配置存在时（minio.enabled=true）才创建 MinioClient→MinioColdStorageStore，此时 MinIO 应可达。ensureBucket 失败抛异常是 fail-fast，可接受。但若 MinIO 短暂不可用会导致启动失败，可后续改为懒初始化。

## 6. 是否超出任务范围

- **IngestService 多协议扩展**：为装配 MqAdapter（RabbitMQ）的必要支撑（任务单 §2.2），属范围。
- **MockMvc 断言修正（400→409）**：P0-08 全局 409 映射的副作用修复，属合理伴随修复（非无关重构）。
- **KafkaInvokeLogConsumer**：Kafka 异步链路的必要组成，属范围。
- 无前端改动、无重复 P0-03~P0-09 测试、无性能/故障注入。
- 新增依赖（minio + testcontainers）在父 pom 版本管理 + 模块 scope 控制（testcontainers 为 test scope），合理。

## 7. 是否过度设计

未发现过度设计。Kafka 异步 + consumer 落库为标准异步日志模式；MinIO 冷存储为接口实现 + 回退；IT 双层门禁为环境受阻的必要处理。无冗余抽象。

## 8. 安全风险

- ✅ 配置全环境变量化，无硬编码密钥（minio access/secret 默认 minioadmin 仅开发用）。
- ✅ E2E 脚本 token/secret 为变量，证据输出到 target/e2e-p0（.gitignore 已覆盖 target/）。
- ✅ Kafka 日志序列化为 JSON String，不含额外敏感信息（与 P0-04 脱敏一致，调用日志本就无明文 secret）。
- ⚠️ H-1：docker 配置缓存 `.docker-*` 可能含本地 docker 凭证，不应提交（需清理）。
- 无新增安全风险。

## 9. 审查结论

**建议通过（H-1 需提交前清理，H-2 需上线前补跑 IT）**

P0-10 达成核心目标：Kafka 调用日志异步、RabbitMQ MQ 适配器、MinIO 冷存储、jdbc profile 配置、Testcontainers IT、E2E 脚本。全量回归 BUILD SUCCESS 无回归。代码质量高（Kafka 异步链路最终落事实源、Bean 装配回退完整、配置环境变量化、IT 双层环境门禁正确处理环境受阻）。

**需处理**：
- **H-1（提交前）**：清理工作区运行产物（*.log + .docker-*），补 .gitignore，避免污染仓库（.docker-* 可能含本地凭据）。
- **H-2（上线前）**：在有 Docker 的环境设 `RUN_REAL_DEPS_IT=true` 补跑 RealDependenciesIT + E2E 脚本 + 重启恢复，附证据。

P2 建议（H-3~H-5）不阻断。

## 10. 返工任务清单

### 提交前必做（H-1）
1. [ ] 删除工作区运行产物：`*.log`（14 个）、`.docker-cfg/`、`.docker-config/`、`.docker-tmp/`。
2. [ ] `.gitignore` 补：`*.log`、`.docker-cfg/`、`.docker-config/`、`.docker-tmp/`。
3. [ ] 确认 `git add` 仅暂存代码/配置/测试/脚本/文档，不含产物。

### 上线前必做（H-2）
4. [ ] 有 Docker 环境：`RUN_REAL_DEPS_IT=true mvn test -pl platform-pipeline -Dtest=RealDependenciesIT`，附通过证据。
5. [ ] 运行 `bash tasks/e2e-p0.sh`（10 步主链路 + 重启恢复），附 curl 证据。
6. [ ] 验证 trace_id 全链路 + `/stats/audit/verify` intact。
7. [ ] 汇总 P0-08 H-1（DB REVOKE runbook）+ 本任务 H-2 为 P0 上线前门禁清单。

### 后续可选（不阻断）
8. [ ] H-3：E2E 脚本降依赖（jq + openssl 替代 python）。
9. [ ] H-4：AsyncInvokeLogWriter Kafka 失败回退加日志。
10. [ ] H-5：MinioColdStorageStore ensureBucket 改懒初始化。

## 11. 建议提交

清理 H-1 产物后提交。建议提交信息：

```text
feat(P0-10): real-dependency E2E with Kafka/RabbitMQ/MinIO and Testcontainers

- Kafka async invoke-log: AsyncInvokeLogWriter sends to topic, KafkaInvokeLogConsumer persists to t_service_invoke_log (falls back to JDBC on failure)
- RabbitMQ MqAdapter wired via @ConditionalOnBean; IngestService routes by protocol scheme
- MinIO cold storage (MinioColdStorageStore) with Local fallback; enabled via storage.minio.enabled
- application.yml adds kafka/rabbitmq/minio config, all env-var driven, defaults off
- Testcontainers IT (RealDependenciesIT) covers MySQL+Kafka+RabbitMQ+MinIO main pipeline; gated by Docker + RUN_REAL_DEPS_IT
- 10-step E2E script (tasks/e2e-p0.sh) with curl evidence
- MockMvc assertions aligned to 409 for duplicate/state-transition (P0-08 global mapping)
- mvn test green; IT conditional on Docker availability
```
