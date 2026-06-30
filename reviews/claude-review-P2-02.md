# Claude Code 审查结果 — P2-02 压测容量

## 1. 审查对象

- 任务：P2-02 压测容量
- 分支：`ai/p2-load-capacity`（改动在工作区未提交，含已提交的计划文档 commit `bfa855a1`）
- 任务单：`tasks/codex-task-P2-02.md`，计划：`tasks/claude-plan-P2-02.md`
- 审查日期：2026-06-30
- 改动范围：JMeter 脚本修复、数据生成器、消费侧 logs from/to（F-5）、压测报告、runbook、报告模板
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（F-5 涉及查询路径改造，必做）

## 2. Git 状态

7 文件修改 + 4 文件新增（`perf/datagen/LoadDataGenerator.java`、`perf/datagen/README.md`、`perf/p2-02-report.md`、`perf/runbook.md`），与任务单 F-1~F-8 对齐，无越界模块改动。

## 3. 常规审查

| 项 | 结论 |
|---|---|
| F-1 JMeter 修复 | 路径 `/run`→`/test`、HeaderManager 加 Authorization、invoke JSR223 签名（HMAC-SHA256 hex）、参数化 secret/apiKey/consumerCode/params/from/to。✓ |
| F-2 数据生成器 | `LoadDataGenerator` 支持 invoke_log（跨月）/raw_data（100万）/catalog，分批 executeBatch，`--clean` 选项。✓ |
| F-3 分区查询场景 | `Query Service Logs By Range` 带 from/to/page/size。✓ |
| F-4 connector 批量降级 | 不改代码，报告标注限制。✓ |
| F-5 消费侧 logs from/to | `ConsumerController`+`ConsumerService`+`findByConsumerRange` 改造，向后兼容。✓ |
| F-6 开发基线压测 | **未执行**，报告诚实标注"本轮未实测"。⚠ 偏离 |
| F-7 压测报告 | `p2-02-report.md` 三要素（实测/差距/补测）+ 限制说明。✓ |
| F-8 runbook | 环境准备/灌入/冒烟/阶梯/监控/清理/调试。✓ |

## 4. 对抗式审查

### 4.1 攻击面枚举

1. JMeter 签名 body 与服务端验证 body 是否一致（签名能否通过）。
2. F-5 `findByConsumerRange` 是否真带时间谓词、null 安全、向后兼容。
3. 数据生成器 id 生成是否冲突、时间分布是否真跨月。
4. 报告是否伪造达标数字。
5. F-6 未执行是否构成阻断。

### 4.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| JMeter 签名 body 不匹配 | `DataServiceController.invoke:95-96` 把 `request.params()` 作为 body 传 `SignatureUtil.verify`；JSR223 `body=PARAMS`，canonical=`apiKey\ntimestamp\nnonce\nbody`，与 `SignatureUtil.canonical:41-43` 一致；HMAC-SHA256 hex 一致 | 已反驳 |
| nonce 重放 | JSR223 每次生成 UUID nonce；`SignatureUtil.usedNonces` 内存去重；多线程各自 nonce | 已反驳 |
| timestamp 过期 | JSR223 用 `System.currentTimeMillis()/1000`，300s 窗口；runbook §8 说明时钟差 | 已反驳 |
| findByConsumerRange 无时间谓词 | 复用 `queryFiltered`，from/to 经 null 检查加 `created_at >= ? AND created_at < ?` 谓词（:107-114）；测试 `findByConsumerRange("c2", from, to)` 验证过滤 | 已反驳 |
| F-5 向后兼容破坏 | `ConsumerService.logs(id,page,size)` 重载委托 `logs(id,null,null,page,size)`；`findByConsumer` 保留 | 已反驳 |
| Instant 参数解析失败 | `@RequestParam(required=false) Instant from`，Spring 按 ISO-8601 解析；MockMvc 测试用 `2026-06-27T23:59:59Z` 通过 | 已反驳 |
| 数据生成器 id 冲突 | `nextId=SELECT MAX(id)+1`，单连接单线程离线工具，非生产并发；`setAutoCommit(false)` | 已反驳（压测工具，P3-1） |
| 跨月分布不真实 | `generateInvokeLogs` 每月 `base.plusMonths(m)` + `plusDays(i%28)`，真跨月；`log_day` 由 `created.toString().substring(0,10)` 派生正确 | 已反驳 |
| raw_data 时间不跨月 | `generateRawData` `created=now.minusSeconds(count-i)` 集中当前窗口，不跨月分区；但 P2-01 未要求 raw_data 分区裁剪测试 | 已反驳（设计可接受） |
| 报告伪造达标 | 报告明确"本轮未实测，不填虚构数字"，模板"本轮未实测"标注，结论"不能宣称 NFR-P01~P07 已达标" | 已反驳（诚实） |
| F-6 未执行阻断 | 任务单 F-6 要求开发基线，但环境未启动；报告诚实标注待 runbook 执行回填。压测框架交付完整，基线数据待环境就绪 | 存活 P2（偏离，不阻断框架交付） |

### 4.3 存活缺陷

**无 P1 阻断。** 1 项 P2 改进 + 2 项 P3 提示：

#### P2 改进（1 项）

**P2-1 F-6 开发环境基线压测未执行**
- 任务单 F-6 要求"开发环境执行压测，采集基线数据填报告"。报告 `p2-02-report.md:13` 明确"当前工作区未启动完整的 Gateway、各业务服务、MySQL/Redis/Nacos 与 JMeter，因此本轮没有可采信的 P50/P95/P99/TPS 实测值"。
- 评估：这是诚实偏离而非伪造。压测体系（脚本+生成器+runbook+报告结构）已完整交付，基线数据待环境就绪后按 runbook 执行回填。
- 处理：不阻断 P2-02 框架合入。建议在合入后、环境就绪时按 runbook 执行一次冒烟+基线压测，回填 `report-template.md` 实测值列，作为 P2-02 的实测闭环（可单独跟踪，不阻断 P2-03 启动）。

#### P3 提示（2 项，不阻断）

- **P3-1**：`LoadDataGenerator.nextId` 用 `SELECT MAX(id)+1`（与 P0-03 RW-1 修复的生产并发不安全模式同类）。压测单线程离线工具场景低风险，但若并行运行多生成器实例会 id 冲突。建议 runbook 标注"生成器不可并行运行"，或改用数据库序列。
- **P3-2**：`generateRawData` 的 `created_at` 集中在当前窗口不跨月，无法验证 `t_raw_data` 分区裁剪。P2-01 未要求 raw_data 分区裁剪测试，但若后续需验证，生成器应支持 `--months` 跨月分布。

### 4.4 对"建议通过"的反驳

- 为何不应通过？F-6 基线压测未执行，P2-02 是否算"完成"？→ 压测体系（脚本可执行+生成器+runbook+报告结构+F-5 改造+测试）已完整交付，F-6 实测基线待环境就绪，报告诚实标注不伪造。P2-02 通过标准是"P95/P99、TPS、批量接入达标"，达标判定本就需生产环境（计划 §2.3 已明确开发环境不追求达标），框架交付满足"可重复执行+诚实外推+补测清单"。
- 签名是否真能通过？→ 已追 `DataServiceController`+`SignatureUtil`+JSR223 三方一致，body=params。
- F-5 是否破坏既有消费侧 logs？→ 向后兼容重载 + 测试全绿。
- 反驳未发现存活 P1 阻断，结论成立。

## 5. 测试验证

```text
mvn test -pl platform-common,platform-partner -am
- platform-common: Tests run: 39, Failures: 0, Errors: 0, Skipped: 1
    （Skipped 1 = LargeTablePartitionIntegrationTest，P2-01 遗留 assumeTrue）
- platform-partner: Tests run: 30, Failures: 0, Errors: 0
BUILD SUCCESS
```

F-5 测试覆盖：
- `JdbcServiceInvokeLogRepositoryTest`：`findByConsumerRange` 时间过滤验证。
- `ConsumerControllerTest`：MockMvc `/consumers/{id}/logs?from&to` 端到端，旧 trace-old 被时间窗口排除、trace-consumer 命中。

既有测试无回归。

## 6. 未实测项

1. JMeter 冒烟与阶梯压测（F-6，环境未启动）。
2. 数据生成器真实灌入（runbook 命令未执行验证）。
3. 签名 invoke 端到端（需服务启动 + 真实凭证）。
4. 生产集群压测、达梦/OceanBase 性能、48h 稳定性（生产补测清单）。

以上均为环境依赖项，P2-02 框架已就绪，待环境执行。

## 7. 审查结论

**建议通过（附 P2 跟进项）。**

- 压测体系完整交付：JMeter 脚本修复（路径/header/签名）、数据生成器、分区查询场景、F-5 消费侧 logs from/to、报告、runbook。
- F-5 改造正确，向后兼容，测试全绿。
- 报告诚实标注"本轮未实测，不伪造达标"，符合任务单诚实原则。
- 对抗式审查未发现存活 P1 阻断。
- F-6 基线压测未执行属诚实偏离（P2-1），不阻断框架合入，待环境就绪按 runbook 回填。

## 8. 返工/跟进清单

### 非阻断跟进（P2）

1. **P2-1**：环境就绪后按 `perf/runbook.md` 执行 JMeter 冒烟 + 开发基线压测，回填 `perf/report-template.md` 实测值列，闭环 F-6。可单独跟踪，不阻断 P2-03。

### 可选改进（P3，不阻断合入）

2. **P3-1**：runbook 标注"生成器不可并行运行"，或 `nextId` 改用数据库序列。
3. **P3-2**：`generateRawData` 支持跨月分布，便于后续验证 raw_data 分区裁剪。

返工改动可提交 `ai/p2-load-capacity` 并合并 master；P2-1 基线回填作为独立跟进项。
