# P2-02 Codex 实现任务单 — 压测容量

> 主控：Claude Code　执行：Codex
> 依据：`tasks/claude-plan-P2-02.md`（第一性原理计划，权威）、`docs/requirements.md` §3.1
> 前置：P2-01 已合入 master（`efc0dcb7`），分区表+查询优化就绪
> 分支：`ai/p2-load-capacity`
> 日期：2026-06-30

---

## 0. 必读

1. `AGENTS.md`（职责边界）
2. `tasks/claude-plan-P2-02.md`（本任务第一性原理计划，权威）
3. `docs/requirements.md` §3.1（NFR-P01~P07）
4. `perf/jmeter/m5-performance.jmx`（现有脚本，需修复）
5. `perf/report-template.md`（报告模板，需填实测）
6. `perf/monitor/collect-metrics.sh`、`perf/jvm.args`（监控与调优）
7. `platform-pipeline/.../service/DataServiceController.java`（invoke 端点 + InvokeRequest）
8. `platform-pipeline/.../service/DataServiceManager.java:160-205`（invoke 签名验证）
9. `platform-common/.../security/SignatureUtil.java`（签名算法）
10. `platform-pipeline/.../ingest/IngestController.java`（接入端点）
11. `platform-partner/.../consumer/ConsumerController.java:73-79`、`ConsumerService.java:151-157`（消费侧 logs）
12. `platform-common/.../log/JdbcServiceInvokeLogRepository.java`（findByConsumer/findByRange）

---

## 1. 任务目标

建立可重复、可度量、可定位瓶颈的压测体系：修复 JMeter 脚本使其可执行，新增数据生成器灌入跨月大表数据，在开发环境跑出各场景 P50/P95/P99/TPS 基线，按集群线性扩展外推达标差距，诚实标注开发环境限制与生产补测清单。通过标准：**P95/P99、TPS、批量接入达标（开发基线+生产外推+补测清单）**。

**核心原则**：不追求在开发环境强行达标，诚实外推；每个 NFR 有"实测值（开发）+ 达标差距 + 生产补测路径"三要素，消除"待填充"空白。

---

## 2. 实现边界（严格遵守）

**范围决策（已确认）**：
- F-4 connector 批量改造：**降级**为数据生成器直灌 `t_raw_data` 模拟批量接入结果 + 标注限制。**不改造** `AbstractSourceConnector.read`。
- F-5 消费侧 logs from/to：**纳入**本次。
- 压测环境：**仅开发环境基线 + 生产外推**，不准备 K8s 集群。

**只做**：F-1 ~ F-8（见下）。
**不做**：
- 不引新压测框架（Gatling/k6），复用 JMeter。
- 不改造集群部署。
- 不改造 `AbstractSourceConnector.read`（F-4 降级）。
- 不修改 `.env`、密钥、生产配置、无关模块。
- 不在开发环境强行追求 1000TPS/10亿条达标数字。
- 不重构无关代码。

---

## 3. 任务清单

### F-1　修复 JMeter 脚本 — `perf/jmeter/m5-performance.jmx`

修复现有脚本三场景，使其可真正执行：

1. **批量接入路径**：`/api/v1/ingest/tasks/{taskId}/run` → `/api/v1/ingest/tasks/{id}/test`（`IngestController.java:60`）。
2. **Authorization header**：所有管理类端点（catalog/logs 查询）加 `Authorization: Bearer ${TOKEN}` header（`TOKEN` 变量已定义但未使用）。
3. **invoke 请求体 + 签名**：
   - `POST /api/v1/services/{serviceCode}/invoke` 请求体改为 `InvokeRequest` 字段：`consumerCode/apiKey/timestamp/nonce/params/signature`。
   - 新增 JSR223 前置脚本（Groovy）生成 `timestamp`（当前秒）、`nonce`（UUID）、`signature`。
   - 签名算法（`SignatureUtil.java:22-23,41-43`）：`HMAC-SHA256(secret, apiKey + "\n" + timestamp + "\n" + nonce + "\n" + body)`，hex 编码。`body` = `params` 字段值。
   - `secret` 通过 `__P(secret)` 传入（压测专用凭证，非生产密钥）。
   - 注意时间窗 300 秒（`SignatureUtil.java:14`），nonce 防重放（每次请求必须新 nonce）。
4. **参数化**：保留 `__P` 参数化，新增 `secret`、`consumerCode`、`apiKey`、`params`。

### F-2　数据生成器 — `perf/datagen/`

新增数据生成器（Java main 程序或 SQL 脚本，Codex 自选），灌入大表数据支撑压测：

1. **`t_service_invoke_log` 跨月数据**：灌入跨 3+ 月（如 2026-01~2026-06）的调用日志，每月 N 万条（可配，默认每月 5 万），`service_code`/`consumer_code` 分布多样，`created_at` 按月分布。用于：
   - NFR-P07 千万级查询压测（`/services/{code}/logs?from&to` 验证分区裁剪）。
   - NFR-P01/P02 服务调用日志写入压力。
2. **`t_raw_data` 批量数据**：灌入 100 万条模拟批量接入结果（F-4 降级方案，直灌模拟）。用于 NFR-P03 批量传输量级证明（标注"直灌模拟，非 connector 真实批量"）。
3. **`t_data_catalog` 目录数据**：灌入目录数据支撑查询压测（可选，量级适中）。
4. **生成器接口**：
   ```
   --table=invoke_log --months=6 --per-month=50000 --service=svc-risk
   --table=raw_data --count=1000000 --task-id=1
   ```
5. 支持独立 schema 或清理参数，避免污染开发库（`--clean` 选项或写独立测试库）。

### F-3　分区表查询压测场景 — 扩展 `perf/jmeter/m5-performance.jmx` 或新增 `perf/jmeter/p2-query.jmx`

新增分区表查询场景，验证 P2-01 分区裁剪性能：

1. **`GET /api/v1/services/{serviceCode}/logs?from={ISO8601}&to={ISO8601}&page=1&size=20`**：带时间范围谓词，命中分区裁剪。
2. 参数化 `from`/`to`（如查询某一个月的数据），`serviceCode`。
3. 加 `Authorization: Bearer ${TOKEN}` header。
4. 该场景用于 NFR-P07 查询性能（千万级数据下分区裁剪后 ≤2s 的验证）。

### F-4　connector 批量读取（降级） — 标注限制

**不改造代码**。在压测报告（F-7）中明确标注：
- `AbstractSourceConnector.read` 当前仅返回单条 payload，不支持批量分页读取。
- NFR-P03（100万条/批）和 P04（接入吞吐）的批量能力由数据生成器直灌 `t_raw_data` 模拟，**非 connector 真实批量**。
- connector 真实批量读取改造列为后续任务（建议方案：`read` 按 `batchSize` 分页拉取，offset 推进）。

### F-5　消费侧日志查询加时间范围 — `platform-partner`

对齐 P2-01，使消费侧日志也能验证分区裁剪：

1. **`ConsumerController.logs`**（`:73-79`）：加 `from`/`to` 参数（`Instant`，可选），透传 `ConsumerService.logs`。
2. **`ConsumerService.logs`**（`:151-157`）：加 `from`/`to` 参数，调 `invokeLogRepository.findByConsumerRange(consumerCode, from, to, page, size)`。
3. **`JdbcServiceInvokeLogRepository`**：新增 `findByConsumerRange(String consumerCode, Instant from, Instant to, int page, int size)`，复用 `queryFiltered`（已有 from/to 谓词逻辑）。
4. **测试**：MockMvc 验证 `/consumers/{id}/logs?from&to` 时间过滤生效；JDBC 测试验证 SQL 层 from/to 谓词。
5. 保留 `findByConsumer`（无 from/to）向后兼容，或改为内部委托 `findByConsumerRange(null,null)`。

### F-6　开发环境基线压测 — `perf/`

在开发环境执行压测，采集基线数据：

1. 环境准备：docker-compose 启 MySQL/Redis/Nacos，6 服务 + gateway，禁用 flyway 手动迁移 schema（沿用 `tasks/dev-progress.md` §5.4 启动方式）。
2. 数据灌入：用 F-2 生成器灌入跨月调用日志 + 100 万条 raw_data。
3. 执行 JMeter（F-1 + F-3 场景）：
   - 服务调用（标准接口）：测 P50/P95/P99/TPS。
   - 目录查询：测 P50/P95/P99。
   - 分区表日志查询（带 from/to）：测 P50/P95/P99，对比有/无时间范围。
4. 监控采集：`collect-metrics.sh` 采集 JVM/DB/连接池指标。
5. 输出：JMeter `.jtl` 结果 + 监控 CSV，汇总到报告。

> 若开发环境无法跑出有意义的数据（如服务起不来），标注"开发环境基线待补"，不伪造数据。

### F-7　压测报告 — `perf/report-template.md`（填实测）+ `perf/p2-02-report.md`

1. 填 `perf/report-template.md` 实测值列（开发环境基线）。
2. 新增 `perf/p2-02-report.md`，对每个 NFR 给出三要素：
   - **实测值（开发环境单节点）**：P50/P95/P99/TPS 实测。
   - **达标差距**：与 NFR 标准对比，是否达标。
   - **生产补测路径**：达标所需生产环境规模（集群节点数/资源/数据分片）+ 线性扩展依据 + 补测项。
3. 瓶颈定位与调优建议（基于监控数据）。
4. 限制说明：明确"开发环境单节点基线 ≠ 生产达标"，F-4 connector 批量限制，`/consumers/{id}/logs` 改造后可验证分区裁剪。
5. 生产补测清单：真实集群压测、48h 稳定性、真实 connector 批量、达梦/OceanBase 性能。

### F-8　压测 runbook — `perf/runbook.md`

编写可重复执行的压测手册：

1. 环境准备（中间件/服务启动/schema 迁移）。
2. 数据灌入（F-2 生成器命令）。
3. 压测执行（JMeter 命令 + 参数）。
4. 监控采集（`collect-metrics.sh`）。
5. 结果汇总与报告填写。
6. 清理（数据/库清理）。
7. 调试（签名失败/401/404 排查）。

---

## 4. 测试要求

1. **F-1 JMeter 冒烟**：低并发（1 线程 1 循环）跑通三场景，验证端点可达、签名通过、无 401/404。
2. **F-2 数据生成器验证**：灌入后 `COUNT(*)` 验证数据量 + 时间分布（跨月）+ `EXPLAIN` 带时间范围查询命中分区（复用 P2-01 证据方式）。
3. **F-5 消费侧 logs**：MockMvc 测试 `from`/`to` 时间过滤；JDBC 测试 `findByConsumerRange` SQL 谓词。
4. **回归**：`mvn test -pl platform-partner,platform-common -am` 全绿（F-5 改动）；`mvn test` 全量无回归。
5. **测试命令**：
   ```bash
   mvn test -pl platform-partner,platform-common -am
   mvn test
   ```

---

## 5. 输出要求（完成后提交给 Claude Code 审查）

1. 修改/新增文件清单。
2. JMeter 冒烟通过证据（低并发跑通截图/日志）。
3. 数据生成器灌入证据（COUNT + 时间分布 + EXPLAIN 分区裁剪）。
4. 开发环境基线数据（P50/P95/P99/TPS）填入报告。
5. F-5 测试结果（mvn 输出）。
6. 压测报告 + runbook。
7. 潜在风险与未实测项（生产集群、connector 真实批量、达梦性能）。
8. 偏离说明（若计划与实现有出入，必须说明原因）。

---

## 6. 共性约束

- 不破坏 P0/P1/P2-01 既有闭环（分区表、查询优化、审计防篡改、账单聚合）。
- 不修改 `.env`、密钥、生产配置、`docs/`（perf 下报告除外）、`tasks/`（本任务单除外）、`reviews/`、`k8s/prod/`、`delivery/`、`security/`。
- 诚实标注：开发环境基线不等于生产达标，所有外推与限制必须明示。
- 压测专用凭证（apiKey/secret）不得使用生产密钥，在 runbook 中说明。
- 上线前门禁：生产集群压测、48h 稳定性、真实 connector 批量、国产库性能（本任务标注未实测，不替代上线门禁）。

---

## 7. 验收标准（对齐 claude-plan-P2-02 §10）

- [ ] JMeter 套件可重复执行，冒烟通过（无 401/404/签名失败）。
- [ ] 数据生成器可灌入跨月大表数据，`EXPLAIN` 验证分区裁剪。
- [ ] 开发环境基线数据填入报告（P50/P95/P99/TPS）。
- [ ] 每个 NFR 有"实测+差距+补测"三要素，无"待填充"空白。
- [ ] 瓶颈有定位与调优建议。
- [ ] runbook 可指导重复执行。
- [ ] F-5 消费侧 logs from/to 改造 + 测试通过，`mvn test` 全绿。
- [ ] 诚实标注开发环境限制与生产补测清单。
