# P2-02 第一性原理开发计划 — 压测容量

> 阶段：P2（生产强化）第二任务
> 依据：`docs/development-process-workflow.md` §3.3、`tasks/phase-task-checklist.md` §4、`docs/requirements.md` §3.1（NFR-P01~P07）、`perf/report-template.md`
> 前置：P2-01 已合入 master（`efc0dcb7`，分区表+查询优化就绪）
> 日期：2026-06-30
> 分支：`ai/p2-load-capacity`（建议）

---

## 1. 需求来源

### 1.1 任务口径

| 项 | 内容 |
|---|---|
| 编号 | P2-02 |
| 主题 | 压测容量 |
| 依赖 | P1 完成（已满足）、P2-01（分区表查询优化就绪） |
| 涉及模块 | `perf/`、`platform-pipeline`（connector 批量）、`platform-billing`/`platform-partner`（查询端点） |
| 输出 | JMeter/压测报告 |
| 通过标准 | **P95/P99、TPS、批量接入达标** |

### 1.2 性能指标基线（requirements.md §3.1）

| NFR | 指标 | 标准 |
|---|---|---|
| P01 | 接口响应 | 标准 P50≤200ms/P95≤500ms/P99≤1s；定制 P50≤500ms/P95≤1s |
| P02 | 并发 TPS | ≥1000TPS，峰值≥2000TPS，集群线性扩展 |
| P03 | 批量传输 | 单批次≥100万条，≥100MB/s，断点续传 |
| P04 | 接入吞吐 | 日均≥10亿条，单节点≥1万条/秒 |
| P05 | 加工吞吐 | 日均≥5亿条，加工延迟≤5分钟 |
| P06 | 缓存 | 命中率≥90%，查询≤10ms，容量≥10TB |
| P07 | 查询 | 千万级≤2s，亿级≤5s |

### 1.3 触发事实（调研发现）

1. **M5 压测脚本有骨架但不可用**：`perf/jmeter/m5-performance.jmx` 三场景（服务调用/批量接入/目录查询），但：
   - 批量接入路径错误：脚本用 `/api/v1/ingest/tasks/{id}/run`，实际端点是 `/api/v1/ingest/tasks/{id}/test`。
   - invoke 请求体固定 `{}`，无法通过签名校验（`SignatureUtil.verify`）。
   - `TOKEN` 变量已定义但 sampler 未设 `Authorization` header，管理端点会 401。
2. **实测值全部"待 M6 外部环境填充"**：`perf/report-template.md` 7 项指标无实测数据。
3. **数据生成器完全缺失**：100 万条批量、千万级查询数据准备无任何生成工具。
4. **connector 不支持批量读取**：`AbstractSourceConnector.read` 仅返回单条 payload，`offset>0` 返回空——NFR-P03（100万条/批）和 P04（接入吞吐）当前代码路径不具备能力。
5. **P2-01 分区裁剪仅 1 个端点可验证**：`GET /api/v1/services/{code}/logs?from&to` 带时间范围谓词；`/consumers/{id}/logs` 未改造（无 from/to），无法验证分区裁剪。
6. **目录查询无分页**：`catalog/search` 返回全量 List，千万级压测不理想。

---

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？

招采验收要求第三方性能测试报告证明 NFR-P01~P07 达标。但"达标"需要：生产规模环境（集群+大内存+国产库）、真实数据规模（10亿条）、可重复的压测方法。**本质问题不是"跑出 1000TPS 数字"，而是"建立可重复、可度量、可定位瓶颈的压测体系，并在当前环境跑出基线 + 明确达标所需的生产环境规模"**。

### 2.2 核心矛盾

招采指标（1000TPS/100万条批量/10亿条日均）面向生产集群；当前是单节点开发环境（MySQL 8.0 docker、JVM 2g）、无数据生成器、connector 不支持批量。直接追求"达标"不可行，也不诚实。

### 2.3 最小可行结果

分三层交付，层层递进、诚实标注：

1. **可执行的压测体系**：修复 JMeter 脚本（路径/header/签名体）使其能真正跑通；新增数据生成器（灌入调用日志/原始数据/目录数据）；扩展场景覆盖分区表查询。
2. **开发环境基线数据**：在单节点开发环境跑出各场景的 P50/P95/P99/TPS 实测基线，填入报告（明确标注"开发环境单节点，非生产规模"）。
3. **达标路径与外推**：对每个 NFR 给出"达标所需的生产环境规模（集群节点数/资源/数据分片）+ 线性扩展依据 + 待生产环境补测项"。

### 2.4 系统必须接收哪些输入？

- 修复后的 JMeter 脚本（含签名生成、token 注入、正确路径）。
- 数据生成器配置（数据量、分布、时间跨度）。
- 压测目标环境地址（开发/生产）。
- 监控指标采集（JVM/DB/连接池）。

### 2.5 系统必须产生哪些输出？

- 可重复运行的 JMeter 套件 + 数据生成器。
- 实测压测报告（开发环境基线 + 生产外推）。
- 瓶颈定位与调优建议。
- 达标差距分析与生产环境补测清单。

### 2.6 从输入到输出不可省略的处理过程

1. **修脚本**：JMeter 路径/header/请求体对齐实际端点；invoke 场景需生成签名（JSR223 脚本用 secret 做 HMAC）。
2. **建生成器**：批量灌入 `t_service_invoke_log`（跨月分区数据，验证 P07 查询）、`t_raw_data`、`t_data_catalog`，支撑千万级查询压测。
3. **补批量能力**：`AbstractSourceConnector.read` 支持真分页批量读取（或新增批量接入专用路径），否则 NFR-P03/P04 无法测。
4. **跑基线**：开发环境执行 JMeter，采集 P50/P95/P99/TPS + JVM/DB 监控。
5. **分析与外推**：单节点基线 → 集群线性扩展外推 → 标注达标差距。
6. **出报告**：填 `perf/report-template.md` 实测值 + 限制说明 + 生产补测清单。

### 2.7 哪些是核心能力？

- 可执行 JMeter 套件（修路径/header/签名）。
- 数据生成器（灌分区表大表数据）。
- 开发环境基线实测。
- 达标差距分析。

### 2.8 哪些是增强能力？

- connector 批量读取改造（支撑 P03/P04）——范围较大，可列子任务或标注限制。
- 集群压测（需 K8s 多副本环境）——属生产环境补测。
- 48h 稳定性测试——`collect-metrics.sh` 已有，需长时间运行环境。

### 2.9 当前代码库最小改动路径

- **改 `perf/jmeter/m5-performance.jmx`**：修路径、加 Authorization header、加签名 JSR223 前置脚本。
- **新增 `perf/datagen/`**：数据生成器（Java main 或 SQL 脚本），灌入跨月调用日志/原始数据/目录。
- **新增 `perf/jmeter/p2-query.jmx`**（或扩展原脚本）：分区表查询场景（`/services/{code}/logs?from&to`）。
- **改 `AbstractSourceConnector.read`**（可选，支撑 P03）：真批量分页——若范围过大则标注限制，用数据生成器直灌 `t_raw_data` 模拟批量接入结果。
- **填 `perf/report-template.md`**：实测值 + 限制 + 外推 + 补测清单。
- **新增 `perf/runbook.md`**：压测执行手册（环境准备/数据灌入/执行/采集/报告）。

### 2.10 如何测试？

- JMeter 脚本本地非压测模式（低并发）跑通，验证端点可达、签名通过、数据生成器灌入正确。
- 数据生成器灌入后，`COUNT(*)` 验证数据量与时间分布（跨月分区）。
- 分区裁剪验证：`EXPLAIN` 带时间范围查询命中分区（复用 P2-01 证据）。

### 2.11 如何验收？

- JMeter 套件可重复执行，开发环境基线数据填入报告。
- 每个 NFR 有"实测值（开发）+ 达标差距 + 生产补测路径"三要素，无"待填充"空白。
- 瓶颈有定位与调优建议。
- 诚实标注：开发环境单节点数据不等于生产达标，生产补测清单明确。

### 2.12 如何避免过度设计？

- **不追求在开发环境达标**：1000TPS/10亿条/100万条批量在单节点不可行也不必要，外推即可。
- **不自建压测框架**：复用 JMeter，不引 Gatling/k6 等新依赖。
- **不改造集群部署**：集群压测留生产环境，本任务只做单节点基线+外推。
- **connector 批量改造按需**：若改造范围过大，用数据生成器直灌模拟，标注限制。
- **不补全所有未改造端点**：`/consumers/{id}/logs` 加 from/to 可作小改进，但不强制改造所有查询。

---

## 3. 功能拆解

| 编号 | 任务 | 模块 | 说明 |
|---|---|---|---|
| F-1 | 修复 JMeter 脚本 | perf/jmeter | 修路径（/run→/test）、加 Authorization header、invoke 签名 JSR223 |
| F-2 | 数据生成器 | perf/datagen | 灌入跨月调用日志/原始数据/目录，支撑千万级查询 |
| F-3 | 分区表查询压测场景 | perf/jmeter | `/services/{code}/logs?from&to` 验证分区裁剪性能 |
| F-4 | connector 批量读取 | platform-pipeline | `AbstractSourceConnector.read` 真分页批量（支撑 P03/P04），或标注限制 |
| F-5 | 消费侧日志查询加时间范围 | platform-partner | `/consumers/{id}/logs` 加 from/to（小改进，对齐 P2-01） |
| F-6 | 开发环境基线压测 | perf/ | 跑各场景 P50/P95/P99/TPS + 监控 |
| F-7 | 压测报告 | perf/report-template.md | 实测值 + 限制 + 外推 + 补测清单 |
| F-8 | 压测 runbook | perf/runbook.md | 环境准备/数据灌入/执行/采集/报告流程 |

---

## 4. 影响模块

| 模块 | 改动类型 | 风险 |
|---|---|---|
| `perf/jmeter` | 修复+扩展脚本 | 低，纯压测脚本 |
| `perf/datagen` | 新增生成器 | 低，新代码 |
| `platform-pipeline.ingest` | connector 批量读取（可选） | 中，改动核心接入路径，需回归 |
| `platform-partner` | 消费侧 logs 加 from/to | 低，对齐 P2-01 |
| `perf/report-template.md` | 填实测值 | 低 |

---

## 5. 接口设计

### 5.1 JMeter invoke 签名

invoke 需 `apiKey+timestamp+nonce+body+signature`，signature 由 secret 做 HMAC-SHA256。JMeter 用 JSR223 前置脚本（Groovy）生成签名，secret 通过 `__P(secret)` 传入（压测专用凭证，非生产密钥）。

### 5.2 数据生成器

```text
perf/datagen/InvokeLogDataGenerator.java（或 SQL 脚本）
  --count=N --months=M --service=S
  灌入 t_service_invoke_log，时间跨 M 月（验证分区裁剪），service/consumer 分布
```

### 5.3 消费侧 logs 时间范围

`GET /api/v1/consumers/{id}/logs?from&to&page&size`，对齐服务侧 logs。

---

## 6. 数据结构

- 数据生成器输出：跨月分布的 `t_service_invoke_log`（每月 N 万条）、`t_raw_data`、`t_data_catalog`。
- 压测结果：JMeter `.jtl` + `perf/results/` 汇总。
- 监控：`collect-metrics.sh` 采集的 `m6-metrics.csv`。

---

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 开发环境无法跑出 1000TPS | 标注单节点基线，外推集群达标 |
| connector 批量改造范围过大 | F-4 降级为"数据生成器直灌+标注限制"，不阻断 |
| JMeter 签名生成失败 | runbook 给出 secret 配置与调试步骤 |
| 数据灌入耗时过长 | 生成器支持分批灌入、并行 |
| 生产环境不可用 | 报告标注"生产补测待环境就绪"，列补测清单 |

---

## 8. 测试策略

1. JMeter 脚本低并发冒烟：端点可达、签名通过、无 401/404。
2. 数据生成器验证：灌入后 `COUNT(*)` + 时间分布（跨月）+ `EXPLAIN` 分区裁剪。
3. connector 批量改造（若做）：单测验证 batchSize 分页读取。
4. 消费侧 logs from/to：MockMvc 测试时间过滤。
5. 回归：`mvn test` 全量（若改 connector/消费侧）。

---

## 9. Codex 实现边界

Codex 须在 `tasks/codex-task-P2-02.md` 中实现，且**仅限**：

1. F-1 修复 JMeter 脚本（路径/header/签名）。
2. F-2 数据生成器。
3. F-3 分区表查询压测场景。
4. F-4 connector 批量读取（或标注限制）。
5. F-5 消费侧 logs 加 from/to。
6. F-6 开发环境基线压测（跑出数据填报告）。
7. F-7 压测报告 + F-8 runbook。

**不得做**：
- 不引新压测框架（Gatling/k6）。
- 不改造集群部署。
- 不修改 `.env`/生产配置/密钥。
- 不在开发环境强行追求达标数字（诚实外推）。
- 不重构无关模块。

---

## 10. 验收标准

- [ ] JMeter 套件可重复执行，冒烟通过（无 401/404/签名失败）。
- [ ] 数据生成器可灌入跨月大表数据，`EXPLAIN` 验证分区裁剪。
- [ ] 开发环境基线数据填入报告（P50/P95/P99/TPS）。
- [ ] 每个 NFR 有"实测+差距+补测"三要素，无"待填充"空白。
- [ ] 瓶颈有定位与调优建议。
- [ ] runbook 可指导重复执行。
- [ ] 改动模块 `mvn test` 全绿（若改 connector/消费侧）。
- [ ] 诚实标注开发环境限制与生产补测清单。

---

## 11. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| connector 批量改造破坏接入链路 | 中 | 单测回归；可降级为标注限制 |
| 压测数据污染开发库 | 低 | 生成器支持独立 schema/清理 |
| 开发环境基线不达标被误读为"生产不达标" | 中 | 报告明确区分开发基线与生产指标 |
| JMeter 签名/鉴权复杂度高 | 低 | runbook 给调试步骤 |
| 生产环境不可用导致补测无法完成 | 中 | 标注待补测，不阻断 P2-02 框架交付 |

**回滚**：perf/ 脚本与报告改动可还原；connector/消费侧改动有单测守护，可回滚。

---

## 12. 下一步

本计划通过后，生成 `tasks/codex-task-P2-02.md`（Codex 实现任务单），按 F-1~F-8 拆解派发。

---

## 附：范围决策（已确认）

1. **connector 批量改造（F-4）**：✅ 降级为数据生成器直灌 `t_raw_data` 模拟批量接入结果 + 报告标注"connector 批量读取为后续任务"。不改造 `AbstractSourceConnector.read`，避免破坏接入链路。
2. **消费侧 logs from/to（F-5）**：✅ 纳入本次，对齐 P2-01，使消费侧日志也能验证分区裁剪。
3. **压测执行环境**：✅ 仅开发环境基线 + 集群线性扩展外推 + 生产补测清单。不准备 K8s 集群（当前不可行，会阻断交付）。
