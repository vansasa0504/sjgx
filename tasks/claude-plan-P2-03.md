# P2-03 第一性原理开发计划 — 故障演练

> 阶段：P2（生产强化）第三任务
> 依据：`docs/development-process-workflow.md` §3.3、`tasks/phase-task-checklist.md` §4、`docs/requirements.md` §3.2（NFR-A01~A05）、`delivery/chaos-drill/`、`k8s/dev/`
> 前置：P2-02 已合入 master（`b29ea40d`）
> 日期：2026-06-30
> 分支：`ai/p2-fault-drill`（建议）

---

## 1. 需求来源

### 1.1 任务口径

| 项 | 内容 |
|---|---|
| 编号 | P2-03 |
| 主题 | 故障演练 |
| 依赖 | P2-02（已满足） |
| 涉及模块 | `delivery/chaos-drill/`、`platform-pipeline`（熔断/降级）、`platform-partner`（Redis 降级）、`platform-common`（日志兜底） |
| 输出 | 故障注入和恢复记录 |
| 通过标准 | **关键依赖故障可恢复** |

### 1.2 可用性指标基线（requirements.md §3.2）

| NFR | 指标 | 标准 |
|---|---|---|
| A01 | 系统可用性 | ≥99.95%，年停机≤4.38h |
| A02 | 服务可用性 | 核心≥99.99%，单服务故障不影响整体 |
| A03 | 故障恢复 | 单节点切换≤30s，集群恢复≤5min，数据零丢失 |
| A04 | 容灾 | 同城双活，RPO≤5min，RTO≤30min |
| A05 | 升级 | 灰度/滚动升级无中断，回滚≤10min |

### 1.3 触发事实（调研发现）

1. **chaos 脚本全依赖 K8s**：`delivery/chaos-drill/` 5 脚本 + `k8s/dev/failover-drill.sh` 均用 `kubectl scale`，开发环境（docker-compose）不可执行；实测值全"待填充"。
2. **应用层故障恢复能力有代码但存在缺口**：
   - ✅ CircuitBreaker 三态（CLOSED/OPEN/HALF_OPEN）+ 单测。
   - ✅ RateLimiter 滑动窗口 + 单测。
   - ✅ FinanceSyncService 手动重试 + 单测。
   - ⚠️ AsyncInvokeLogWriter Kafka→JDBC→localMirror 降级链路**无单测**。
   - ⚠️ **RedisQuotaCounter 不可用无降级**（抛 CONSUMER-500），与 `ops-manual.md` 声称的"DB fallback"不一致——**文档与代码不一致**。
   - ⚠️ **connector 接入无自动重试/退避**（异常直接抛）。
3. **无任何故障注入集成测试**：`RealDependenciesIT` 仅验证正常路径，不模拟依赖宕机。
4. **NFR-A 时延/RPO/RTO 全部待生产 K8s**：A01~A05 的时延类指标无开发环境验证路径。
5. **NFR-A05 灰度回滚无脚本无测试**。

---

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？

招采验收要求"关键依赖故障可恢复"+ NFR-A01~A05 可用性指标达标。"可恢复"有两层含义：①应用层在依赖故障时降级/兜底/重试不崩溃（代码能力）；②基础设施故障时切换/恢复满足 RPO/RTO（运维能力）。**本质问题：证明系统在依赖故障下不丢数据、不崩溃、可恢复，并诚实区分"代码已验证"与"待生产演练"**。

### 2.2 核心矛盾

通过标准"关键依赖故障可恢复"的 RPO/RTO 部分（A03/A04）需 K8s 集群+国产库主备，当前开发环境不可行。但应用层降级/熔断/兜底能力是"可恢复"的代码基础，且存在缺口（Redis 不降级、日志降级无测试、connector 无重试）——这些是开发环境可验证且必须补强的。

### 2.3 最小可行结果

分三层交付：

1. **补强应用层故障恢复缺口**（开发环境可验证）：
   - RedisQuotaCounter 不可用降级（对齐 ops-manual 文档，或修正文档）。
   - AsyncInvokeLogWriter Kafka→JDBC 降级单测。
   - connector 接入重试/退避（可选，范围较大）。
2. **故障注入集成测试**（Testcontainers，开发环境可验证）：
   - 模拟 Kafka/Redis/DB 不可用，验证应用降级/兜底/恢复。
   - 这是 NFR-A02"故障注入测试"的开发环境落地。
3. **chaos 脚本完善 + 报告**（待生产 K8s）：
   - 现有 5 脚本审查修正（如 db-failover 的达梦主备语法）。
   - 补 NFR-A05 灰度回滚脚本。
   - 报告诚实标注"开发层已验证 / 生产 RPO/RTO 待演练"。

### 2.4 系统必须接收哪些输入？

- 应用层降级/重试代码改造点。
- Testcontainers 故障注入测试用例。
- chaos 脚本修正 + 灰度回滚脚本。
- 压测/演练环境（开发 docker-compose / 生产 K8s）。

### 2.5 系统必须产生哪些输出？

- 补强后的应用层降级代码 + 单测。
- 故障注入集成测试（Testcontainers）+ 证据。
- 完善的 chaos 脚本套件（含灰度回滚）。
- 故障演练报告（开发层验证 + 生产待演练清单）。

### 2.6 从输入到输出不可省略的处理过程

1. **补 Redis 降级**：`RedisQuotaCounter` 不可用时降级到本地计数或 DB，而非抛 500；对齐 ops-manual 文档。
2. **补日志降级测试**：`AsyncInvokeLogWriter` Kafka 失败→JDBC→localMirror 链路单测。
3. **补 connector 重试**（可选）：`AbstractSourceConnector.read` 失败重试+退避。
4. **故障注入集成测试**：Testcontainers 起 Kafka/Redis/MySQL，主动停止依赖，验证应用降级与恢复。
5. **审查 chaos 脚本**：修正 db-failover 达梦语法、补灰度回滚脚本。
6. **出报告**：开发层验证结果 + 生产 RPO/RTO 待演练清单。

### 2.7 哪些是核心能力？

- 应用层降级/兜底/重试补强（Redis 降级、日志降级测试）。
- 故障注入集成测试（Testcontainers）。
- chaos 脚本套件完善。

### 2.8 哪些是增强能力？

- connector 接入重试/退避（范围较大，可降级标注）。
- K8s 集群真实演练（待生产环境）。
- Chaos Mesh 集成（待生产 K8s）。

### 2.9 当前代码库最小改动路径

- **改 `RedisQuotaCounter`**：不可用降级（本地计数或 DB），对齐 ops-manual。
- **新增 `AsyncInvokeLogWriterTest`**：Kafka→JDBC→localMirror 降级单测。
- **新增故障注入 IT**（Testcontainers）：Kafka/Redis/DB 故障场景。
- **审查/修正 `delivery/chaos-drill/` 脚本**：db-failover 达梦语法、redis-down 验证点。
- **新增 `delivery/chaos-drill/rolling-upgrade.sh`**：NFR-A05 灰度回滚。
- **填 `chaos-report-template.md` + 新增 `delivery/p2-03-report.md`**：开发层验证 + 生产待演练。

### 2.10 如何测试？

- Redis 降级单测：模拟 Redis 不可用，验证降级到本地/DB。
- 日志降级单测：模拟 Kafka 失败，验证 JDBC 落库 + localMirror 兜底。
- 故障注入 IT：Testcontainers 停 Kafka/Redis，验证应用降级与恢复。
- chaos 脚本：语法检查（`bash -n`），生产环境执行待 K8s。

### 2.11 如何验收？

- 应用层降级缺口补强 + 单测通过。
- 故障注入 IT 在开发环境通过（依赖 Docker）。
- chaos 脚本套件完整（含灰度回滚），语法正确。
- 报告诚实区分"开发层已验证 / 生产 RPO/RTO 待演练"。
- 文档与代码一致（ops-manual Redis 降级描述对齐）。

### 2.12 如何避免过度设计？

- **不在开发环境追求 RPO/RTO 达标**：A03/A04 时延类外推生产，诚实标注。
- **不引 Chaos Mesh**：开发环境用 Testcontainers 故障注入，Chaos Mesh 留生产 K8s。
- **connector 重试按需**：若范围过大，标注限制留后续。
- **不改造集群部署**：K8s 演练留生产。
- **不补全所有 NFR-A**：A01 99.95% 是统计指标，不做开发验证，外推即可。

---

## 3. 功能拆解

| 编号 | 任务 | 模块 | 说明 |
|---|---|---|---|
| F-1 | RedisQuotaCounter 降级 | platform-partner | 不可用降级本地/DB，对齐 ops-manual |
| F-2 | AsyncInvokeLogWriter 降级测试 | platform-pipeline | Kafka→JDBC→localMirror 单测 |
| F-3 | connector 接入重试（可选） | platform-pipeline | read 失败重试+退避，或标注限制 |
| F-4 | 故障注入集成测试 | platform-pipeline/partner IT | Testcontainers 停 Kafka/Redis/DB，验证降级恢复 |
| F-5 | chaos 脚本审查修正 | delivery/chaos-drill | db-failover 达梦语法、redis-down 验证点 |
| F-6 | 灰度回滚脚本 | delivery/chaos-drill | NFR-A05 rolling-upgrade.sh |
| F-7 | 故障演练报告 | delivery | 开发层验证 + 生产待演练清单 |
| F-8 | 文档对齐 | delivery/ops-manual | Redis 降级行为与代码一致 |

---

## 4. 影响模块

| 模块 | 改动类型 | 风险 |
|---|---|---|
| `platform-partner.consumer` | RedisQuotaCounter 降级 | 中，改配额计数路径，需回归 |
| `platform-pipeline.service` | AsyncInvokeLogWriter 测试 + connector 重试（可选） | 低-中 |
| `platform-pipeline/partner IT` | 新增故障注入 IT | 低，新测试 |
| `delivery/chaos-drill` | 脚本修正 + 灰度回滚 | 低，脚本 |
| `delivery/ops-manual` | 文档对齐 | 低 |

---

## 5. 接口设计

### 5.1 RedisQuotaCounter 降级

```java
// Redis 不可用（返回 null 或异常）时，降级到本地计数或 DB 配额表，记 warn 日志，不抛 500
// 对齐 ops-manual.md "Redis 不可用→DB fallback"
```

### 5.2 故障注入 IT

```text
Testcontainers 起 Kafka/Redis/MySQL
  - 停 Kafka：验证 AsyncInvokeLogWriter 降级 JDBC
  - 停 Redis：验证 RedisQuotaCounter 降级
  - 停 DB：验证应用异常处理（不崩溃）
  - 恢复依赖：验证应用恢复
```

---

## 6. 异常场景

| 场景 | 处理 |
|---|---|
| Redis 降级后配额精度下降 | 标注"降级模式本地计数，恢复后同步" |
| Testcontainers 故障注入不稳 | 标注 IT 可跳过，附手动复现步骤 |
| connector 重试范围过大 | F-3 降级为标注限制 |
| K8s 集群不可用 | chaos 脚本语法检查，生产演练待环境 |
| 灰度回滚脚本无法验证 | 标注待生产 K8s 演练 |

---

## 7. 测试策略

1. F-1 Redis 降级单测：模拟 Redis 不可用，验证降级。
2. F-2 日志降级单测：Kafka 失败→JDBC→localMirror。
3. F-4 故障注入 IT：Testcontainers 停依赖，验证降级恢复（`@EnabledIfEnvironmentVariable` 或 `disabledWithoutDocker`）。
4. F-5/F-6 chaos 脚本 `bash -n` 语法检查。
5. 回归：`mvn test` 全量（Redis 降级改配额路径需回归）。

---

## 8. Codex 实现边界

Codex 须在 `tasks/codex-task-P2-03.md` 中实现，且**仅限**：

1. F-1 RedisQuotaCounter 降级 + 单测。
2. F-2 AsyncInvokeLogWriter 降级单测。
3. F-3 connector 重试（或标注限制）。
4. F-4 故障注入 IT（Testcontainers）。
5. F-5 chaos 脚本审查修正。
6. F-6 灰度回滚脚本。
7. F-7 报告 + F-8 文档对齐。

**不得做**：
- 不引 Chaos Mesh（留生产 K8s）。
- 不改造集群部署。
- 不修改 `.env`/生产配置/密钥。
- 不在开发环境追求 RPO/RTO 达标（诚实外推）。
- 不重构无关模块。

---

## 9. 验收标准

- [ ] RedisQuotaCounter 不可用降级，对齐 ops-manual，单测通过。
- [ ] AsyncInvokeLogWriter Kafka→JDBC→localMirror 降级单测通过。
- [ ] 故障注入 IT（Testcontainers）验证降级恢复（Docker 可用时）。
- [ ] chaos 脚本套件完整（含灰度回滚），`bash -n` 语法通过。
- [ ] 报告诚实区分"开发层已验证 / 生产 RPO/RTO 待演练"。
- [ ] 文档与代码一致（ops-manual Redis 降级）。
- [ ] `mvn test` 全绿，无回归。

---

## 10. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| Redis 降级改配额计数破坏既有逻辑 | 中 | 单测回归；保留 LocalQuotaCounter 路径 |
| connector 重试改造范围过大 | 中 | F-3 降级标注限制 |
| 故障注入 IT 不稳定 | 中 | `disabledWithoutDocker` + 手动复现步骤 |
| K8s 集群不可用导致 chaos 无法实测 | 中 | 语法检查 + 标注待生产 |
| 文档与代码不一致被误读 | 中 | F-8 显式对齐 |

**回滚**：应用层降级改动有单测守护；chaos 脚本与报告可还原。

---

## 11. 下一步

本计划通过后，生成 `tasks/codex-task-P2-03.md`，按 F-1~F-8 拆解派发。

---

## 附：范围决策（已确认）

1. **RedisQuotaCounter 降级（F-1）**：✅ 补降级（不可用降级到本地/DB 计数）+ 对齐 ops-manual 文档。修复文档与代码不一致。
2. **connector 接入重试（F-3）**：✅ 标注限制，不改造 `AbstractSourceConnector`，报告标注"接入重试为后续任务"。
3. **故障注入 IT（F-4）**：✅ 核心场景——Testcontainers 模拟 Kafka/Redis 不可用，验证应用降级与恢复。覆盖 NFR-A02 故障注入测试。不做全场景（DB/MinIO/RabbitMQ 留后续）。
4. **chaos 脚本**：审查修正现有 + 补灰度回滚，不引 Chaos Mesh。
