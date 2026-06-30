# P2-03 Codex 实现任务单 — 故障演练

> 主控：Claude Code　执行：Codex
> 依据：`tasks/claude-plan-P2-03.md`（第一性原理计划，权威）、`docs/requirements.md` §3.2
> 前置：P2-02 已合入 master（`b29ea40d`）
> 分支：`ai/p2-fault-drill`
> 日期：2026-06-30

---

## 0. 必读

1. `AGENTS.md`（职责边界）
2. `tasks/claude-plan-P2-03.md`（本任务第一性原理计划，权威）
3. `docs/requirements.md` §3.2（NFR-A01~A05）
4. `delivery/chaos-drill/`（5 脚本 + 模板）、`k8s/dev/failover-drill.sh`
5. `delivery/ops-manual.md`（Redis 降级描述，需对齐）
6. `platform-partner/.../consumer/RedisQuotaCounter.java`、`ConsumerService.java:97-121`（配额计数）
7. `platform-partner/.../consumer/QuotaCounter.java`、`LocalQuotaCounter.java`（降级 fallback）
8. `platform-pipeline/.../service/AsyncInvokeLogWriter.java:51-69`（日志降级链路）
9. `platform-pipeline/.../service/CircuitBreaker.java`、`RateLimiter.java`（已有故障恢复能力）
10. `platform-pipeline/.../RealDependenciesIT.java`（Testcontainers 模式参考）

---

## 1. 任务目标

证明系统在依赖故障下不丢数据、不崩溃、可恢复，并诚实区分"代码已验证"与"待生产演练"。补强应用层故障恢复缺口（Redis 降级、日志降级测试），用 Testcontainers 故障注入验证降级恢复（NFR-A02），完善 chaos 脚本套件（含灰度回滚），诚实标注生产 RPO/RTO 待演练。通过标准：**关键依赖故障可恢复（开发层验证 + 生产待演练清单）**。

**核心原则**：不在开发环境追求 RPO/RTO 时延达标（A03/A04），诚实外推；应用层降级能力必须补强并有测试。

---

## 2. 实现边界（严格遵守）

**范围决策（已确认）**：
- F-1 Redis 降级：**补降级 + 对齐文档**。
- F-3 connector 重试：**标注限制**，不改造 `AbstractSourceConnector`。
- F-4 故障注入 IT：**核心场景**（Kafka/Redis 不可用），不做全场景。
- chaos 脚本：审查修正现有 + 补灰度回滚，不引 Chaos Mesh。

**只做**：F-1 ~ F-8（见下）。
**不做**：
- 不引 Chaos Mesh（留生产 K8s）。
- 不改造集群部署、不改造 `AbstractSourceConnector`。
- 不修改 `.env`、密钥、生产配置、无关模块。
- 不在开发环境追求 RPO/RTO 时延达标。
- 不重构无关代码。

---

## 3. 任务清单

### F-1　RedisQuotaCounter 降级 — `platform-partner`

修复 Redis 不可用抛 CONSUMER-500 无降级的问题，对齐 `ops-manual.md` "Redis 不可用→DB fallback"：

1. **降级策略**：`RedisQuotaCounter` 不可用（`execute` 返回 null 或抛异常）时，降级到 fallback `QuotaCounter`（`LocalQuotaCounter` 或基于 `t_consumer_quota.used_requests` 的 DB 计数），记 warn 日志，**不抛 500**。
2. **实现方式**（Codex 自选其一）：
   - 方式 A：`RedisQuotaCounter` 包装 fallback counter，Redis 异常时委托 fallback。
   - 方式 B：`ConsumerService.consume` 捕获 Redis 异常后切 fallback。
   - 推荐方式 A，封装在 `RedisQuotaCounter` 内，`ConsumerService` 无感。
3. **降级语义**：降级模式计数精度可能下降（本地计数非分布式），恢复后 Redis 计数重建。注释标注。
4. **单测**：模拟 Redis 不可用（`redisTemplate.execute` 返回 null 或抛异常），验证降级到 fallback、不抛 500、配额计数继续工作。
5. **对齐 ops-manual**：`delivery/ops-manual.md` Redis 降级描述与实现一致（若实现是本地 fallback，文档写"降级本地计数"；若 DB，写"DB fallback"）。

### F-2　AsyncInvokeLogWriter 降级测试 — `platform-pipeline`

补 `AsyncInvokeLogWriter` Kafka→JDBC→localMirror 降级链路单测（当前无测试）：

1. **新增 `AsyncInvokeLogWriterTest`**：
   - Kafka 正常：验证 Kafka send 成功。
   - Kafka 失败 + JDBC repository 存在：验证降级写 JDBC（warn 日志）。
   - Kafka 失败 + 无 repository：验证抛 `IllegalStateException`。
   - 无 Kafka + repository 存在：验证直接写 JDBC。
   - 无 Kafka + 无 repository：验证写 localMirror。
2. 用 mock `KafkaTemplate`（抛异常模拟 Kafka 故障）+ 内存/真 JdbcTemplate。

### F-3　connector 接入重试（标注限制） — 不改代码

**不改造代码**。在故障演练报告（F-7）中明确标注：
- `AbstractSourceConnector.read` 失败直接抛 `IllegalStateException`，无自动重试/退避。
- connector 接入重试列为后续任务（建议方案：`read` 失败按退避策略重试 N 次，超时抛异常 + 告警）。

### F-4　故障注入集成测试 — `platform-pipeline` 或 `platform-partner` IT

新增 Testcontainers 故障注入 IT，验证 NFR-A02"故障注入测试"（核心场景）：

1. **Kafka 故障场景**：
   - Testcontainers 起 Kafka，应用用 `AsyncInvokeLogWriter` 写日志。
   - 模拟 Kafka 不可用（停止容器或 mock send 失败），验证降级写 JDBC。
   - 恢复 Kafka，验证恢复 Kafka 写入。
2. **Redis 故障场景**：
   - Testcontainers 起 Redis，应用用 `RedisQuotaCounter`。
   - 停止 Redis，验证降级到 fallback（不抛 500）。
   - 恢复 Redis，验证恢复 Redis 计数。
3. **测试门控**：`@EnabledIfEnvironmentVariable RUN_FAULT_INJECTION_IT=true` 或 `disabledWithoutDocker`，默认无 Docker 跳过。
4. **不做** DB/MinIO/RabbitMQ 全场景（留后续）。

### F-5　chaos 脚本审查修正 — `delivery/chaos-drill/`

审查修正现有 5 脚本：

1. **`db-failover.sh`**：审查达梦主备切换语法（`kubectl scale deployment -l app=dm-primary` + `wait standby Ready`），确认语法正确、标注"真实达梦主备待生产环境"。
2. **`redis-down.sh`**：验证点对齐 F-1 降级行为（Redis 不可用→fallback，非崩溃）。
3. **`kafka-outage.sh`**：验证点对齐 F-2 降级（Kafka 不可用→JDBC 落库）。
4. **`node-down.sh`、`dual-active-switch.sh`**：审查语法、确认 K8s 依赖标注。
5. 所有脚本 `bash -n` 语法检查通过。

### F-6　灰度回滚脚本 — `delivery/chaos-drill/rolling-upgrade.sh`

新增 NFR-A05 灰度/滚动升级回滚脚本：

1. 模拟滚动升级：`kubectl rollout`（或 docker-compose 滚动重启）。
2. 验证升级过程无中断（健康检查）。
3. 模拟回滚：`kubectl rollout undo`，验证回滚≤10min。
4. 脚本依赖 K8s，标注"待生产环境执行"。
5. `bash -n` 语法检查。

### F-7　故障演练报告 — `delivery/p2-03-report.md` + 填 `chaos-report-template.md`

1. 新增 `delivery/p2-03-report.md`：
   - **开发层已验证**：F-1 Redis 降级、F-2 日志降级、F-4 故障注入 IT、CircuitBreaker/RateLimiter（既有）。
   - **待生产 K8s 演练**：NFR-A03 单节点切换/集群恢复、A04 双活 RPO/RTO、A05 灰度回滚时延。
   - **限制说明**：F-3 connector 重试未改造；A01 99.95% 统计指标外推。
   - **NFR-A 对照表**：每个 A 指标的"代码/测试支撑 + 待生产验证"。
2. 填 `chaos-report-template.md`：开发层验证结果填入，生产 RPO/RTO 标"待生产演练"。

### F-8　文档对齐 — `delivery/ops-manual.md`

1. Redis 降级描述与 F-1 实现一致。
2. Kafka 故障描述与 F-2 降级一致。
3. connector 重试限制标注（对齐 F-3）。

---

## 4. 测试要求

1. **F-1 Redis 降级单测**：模拟 Redis 不可用，验证降级 fallback、不抛 500、计数继续。
2. **F-2 日志降级单测**：Kafka 失败→JDBC→localMirror 五种场景。
3. **F-4 故障注入 IT**：Testcontainers 停 Kafka/Redis，验证降级恢复（Docker 可用时）。
4. **F-5/F-6 脚本**：`bash -n` 语法检查。
5. **回归**：`mvn test -pl platform-partner,platform-pipeline,platform-common -am` 全绿（F-1 改配额路径需回归）；`mvn test` 全量无回归。
6. **测试命令**：
   ```bash
   mvn test -pl platform-partner,platform-pipeline,platform-common -am
   mvn test
   ```

---

## 5. 输出要求（完成后提交给 Claude Code 审查）

1. 修改/新增文件清单。
2. F-1 Redis 降级单测结果 + 降级语义说明。
3. F-2 日志降级单测结果（五场景）。
4. F-4 故障注入 IT 结果（Docker 可用时；不可用则标注）。
5. F-5/F-6 脚本 `bash -n` 语法检查证据。
6. 故障演练报告 + ops-manual 对齐说明。
7. 潜在风险与未实测项（生产 K8s RPO/RTO、connector 重试、全场景故障注入）。
8. 偏离说明。

---

## 6. 共性约束

- 不破坏 P0/P1/P2-01/P2-02 既有闭环。
- 不修改 `.env`、密钥、生产配置、`docs/`（delivery 下文档除外）、`tasks/`（本任务单除外）、`reviews/`、`k8s/prod/`、`security/`。
- 诚实标注：开发层验证不等于生产 RPO/RTO 达标，所有外推与限制必须明示。
- chaos 脚本不得含真实生产密钥/连接串。
- 上线前门禁：生产 K8s 故障演练、真实达梦主备、灰度回滚实测（本任务标注未实测，不替代上线门禁）。

---

## 7. 验收标准（对齐 claude-plan-P2-03 §9）

- [ ] RedisQuotaCounter 不可用降级，对齐 ops-manual，单测通过。
- [ ] AsyncInvokeLogWriter Kafka→JDBC→localMirror 降级单测通过（五场景）。
- [ ] 故障注入 IT（Testcontainers Kafka/Redis）验证降级恢复（Docker 可用时）。
- [ ] chaos 脚本套件完整（含灰度回滚），`bash -n` 语法通过。
- [ ] 报告诚实区分"开发层已验证 / 生产 RPO/RTO 待演练"，NFR-A 对照表完整。
- [ ] 文档与代码一致（ops-manual Redis/Kafka 降级）。
- [ ] `mvn test` 全绿，无回归。
