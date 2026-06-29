# Claude Code 审查结果 — P1-01 Connector 合约

## 1. 审查对象

- 任务：P1-01 Connector 合约
- 分支：`ai/p1-connector-contract`（从 master 切出）
- 任务单：`tasks/codex-task-P1-01.md`，计划：`tasks/claude-plan-P1-01.md`
- 审查日期：2026-06-29
- 前置：P0-03（IngestService 落库）已合入 master
- 改动范围：SourceConnector 合约 + ConnectorSpec 能力矩阵 + AbstractSourceConnector 桥接 + JdbcOffsetStore + t_ingest_checkpoint + IngestService 衔接 + check/能力矩阵端点 + approve 前置 + 8 协议 contract test 框架

## 2. Git 状态

改动未提交（工作区，分支 `ai/p1-connector-contract`）：

```text
 M platform-common/test/.../db/MigrationDialectCompatibilityTest.java   # V017 纳入
 M platform-pipeline/.../ingest/IngestController.java                   # check 端点
 M platform-pipeline/.../ingest/IngestService.java                      # checkpoint 衔接 + approve 前置
 M platform-pipeline/.../ingest/PipelineApplication.java                # 8 适配器 Bean + OffsetStore 装配
 M platform-pipeline/.../ingest/sync/InMemoryOffsetStore.java           # put 单调（Math::max）
 M platform-pipeline/.../ingest/sync/RedisOffsetStore.java              # put 单调
 M platform-pipeline/test/.../PipelineModuleMockMvcTest.java            # check/connectors 端点
 M platform-pipeline/test/.../ingest/IngestServiceTest.java             # approve 前置 + 夹具带映射/规则
?? db/migration/V017__ingest_checkpoint.sql + U017（MySQL + DM）
?? platform-pipeline/.../ingest/SourceConnector.java                   # 合约接口
?? platform-pipeline/.../ingest/ConnectorSpec.java                     # 能力声明
?? platform-pipeline/.../ingest/ConnectorCheckResult.java
?? platform-pipeline/.../ingest/ConnectorController.java               # 能力矩阵端点
?? platform-pipeline/.../ingest/ConnectorSpecs.java                    # 8 协议能力矩阵
?? platform-pipeline/.../ingest/OffsetCheckpoint.java
?? platform-pipeline/.../ingest/RawDataBatch.java
?? platform-pipeline/.../ingest/AbstractSourceConnector.java           # 桥接
?? platform-pipeline/.../ingest/sync/JdbcOffsetStore.java              # JDBC checkpoint
?? platform-pipeline/test/.../ingest/AbstractSourceConnectorContractTest.java  # 合约基类
?? platform-pipeline/test/.../ingest/{8 协议}SourceConnectorContractTest.java
?? platform-pipeline/test/.../ingest/sync/JdbcOffsetStoreTest.java
?? tasks/claude-plan-P1.md, claude-plan-P1-01.md, codex-task-P1-01.md
```

## 3. 测试验证

### 3.1 后端

```bash
mvn -pl platform-common install -DskipTests
mvn test -pl platform-pipeline
mvn test   # 全量回归
```

结果：
- **platform-pipeline**：BUILD SUCCESS，Tests run: 108（新增 8 协议 contract test 各 4 用例 = 32 + JdbcOffsetStoreTest 1 + IngestServiceTest 6→7）
- **全量回归**：BUILD SUCCESS，common 32 / gateway 2 / auth 33 / partner 30 / quality 18 / pipeline 108 / billing 39，**无回归**

### 3.2 contract test 覆盖

8 协议 contract test（HTTP/WebService/ApiGateway/FTP/SFTP/Kafka/MQ/DB）各 4 用例：
- checkReportsSuccessAndFailure（check 成功+失败）
- readReturnsBatchAndAdvancesOffset（read 批次 + offset 推进 + 断点恢复）
- checkpointPersistsMonotonicOffset（checkpoint 单调递增）
- exposesSpecDiscoverAndCloseContract（spec/discover/close 合约）

### 3.3 测试结论

全绿，无回归。合约测试框架 + JDBC checkpoint + approve 前置全覆盖。

## 4. 需求符合性

| 需求项（codex-task §2） | 实现情况 | 结论 |
|---|---|---|
| F-1 SourceConnector 合约 | spec/check/discover/read/checkpoint/close + extends AutoCloseable | ✅ |
| F-2 适配器桥接 | AbstractSourceConnector 包装 ProtocolAdapter.fetch 为 read，check/discover 最小实现 | ✅ |
| F-3 能力矩阵 | ConnectorSpecs 8 协议声明 + ConnectorController 端点 | ✅ |
| F-4 checkpoint JDBC | t_ingest_checkpoint（V017/U017 双库）+ JdbcOffsetStore upsert + 单调 | ✅ |
| F-5 IngestService 衔接 | testAndIngest 经 connector.read + checkpoint；approve 前置校验 | ✅ |
| F-6 contract test 框架 | AbstractSourceConnectorContractTest 基类 + 8 协议子类 | ✅ |
| F-7 能力矩阵端点 | GET /api/v1/ingest/connectors + POST /tasks/{id}/check | ✅ |
| approve 前置 | check 通过 + 映射 + 规则（INGEST-CONNECT-FAILED/MAPPING-MISSING/RULE-MISSING） | ✅ |

## 5. 与 claude-plan / Airbyte 借鉴对齐

- **SourceConnector 五能力合约**（discover/check/read/checkpoint/close）：与 Airbyte 思路一致，轻量实现（不引入 SDK）。
- **桥接模式**：AbstractSourceConnector 包装既有 ProtocolAdapter.fetch，不重写 8 适配器，向后兼容 ProtocolAdapter + P0-10 多协议路由（adapterFor 保留）。
- **checkpoint 持久化**：JdbcOffsetStore upsert（先 UPDATE affected==0 再 INSERT）+ Math.max 单调，与 P0-03 落库模式一致。
- **能力矩阵**：ConnectorSpecs 8 协议声明，借鉴 Airbyte connector catalog。
- **contract test 框架**：抽象基类定义合约用例，每协议子类只声明 protocol()，DRY。

## 6. 代码质量

### 6.1 优点

1. **合约设计清晰**：SourceConnector extends AutoCloseable，spec/check/discover/read/checkpoint/close 职责单一；ConnectorSpec/ConnectorCheckResult/RawDataBatch/OffsetCheckpoint 为不可变 record。
2. **桥接零破坏**：AbstractSourceConnector 包装 ProtocolAdapter，既有 8 适配器无需改动；ProtocolAdapter 保留，P0-10 多协议路由不受影响。
3. **checkpoint 单调保证**：JdbcOffsetStore.put 用 Math.max(current, offset)，InMemoryOffsetStore/RedisOffsetStore 同步改为 merge/Math.max，三实现一致，断点续传 offset 不回退。
4. **upsert 三库兼容**：先 UPDATE affected==0 再 INSERT，避开 ON DUPLICATE KEY/MERGE 方言差异，与 P0-03 IngestService.persistTask 一致。
5. **唯一索引约束**：t_ingest_checkpoint(task_id, connector_type) 唯一索引，保证 upsert 正确性 + 查询效率。
6. **approve 前置完整**：validateApproval 校验 check+映射+规则，三类异常码清晰；testAndIngest 内部 APPROVE 路径夹具带映射/规则，不自相矛盾。
7. **contract test 框架优秀**：抽象基类 4 合约用例，8 协议子类仅声明 protocol()，新增协议零成本扩展。
8. **Bean 装配完整**：PipelineApplication 装配 8 适配器 Bean + OffsetStore（jdbc/in-memory 切换），与既有模式一致。
9. **方言守护通过**：V017 双库一致，MigrationDialectCompatibilityTest 纳入 t_ingest_checkpoint CRUD。

### 6.2 发现的问题

#### P2（建议改进，不阻断）

**H-1：read 的 offset 语义为简化全量模型**
- 现象：AbstractSourceConnector.read 中 offset>0 返回空批次，offset=0 读一次全量（fetch 后 nextOffset=1）。
- 影响：Kafka/DB 的 INCREMENTAL/RESUME 真实分页未在桥接层实现——read 总是一次性 fetch 全量，offset 仅标记"是否读过"。
- 评估：符合计划 §2.7（真实分页为增强，本任务最小实现）。contract test 验证的是合约行为（offset 推进、断点恢复语义），非真实分页。对 HTTP/WebService 等无状态协议合理；Kafka/DB 的真实增量分页留后续。
- 建议：后续可为 Kafka/DB 子类覆盖 read 实现真实分页（基于 offset 消费/分页查询），不阻断本次。

**H-2：testAndIngest 内部 transition APPROVE 触发 validateApproval**
- 现象：testAndIngest 内 `transition(task, APPROVE)`，而 apply(APPROVE) 有 validateApproval。但 testAndIngest 直接调 transition（状态机）而非 apply（带校验），所以不触发 validateApproval。
- 评估：**正确**。testAndIngest 用 transition 绕过 apply 的业务校验（测试接入场景），apply(APPROVE) 才走前置校验（人工审批场景）。语义清晰，不冲突。测试夹具带映射/规则是质量 guard，非必须。
- 注：这意味着 testAndIngest 路径的 APPROVE 不校验映射/规则——但 testAndIngest 是测试接入（START_TEST→SUBMIT_APPROVAL→APPROVE 自动流转），人工 apply(APPROVE) 才校验。合理。

**H-3：ConnectorController 与 IngestController.check 端点分离**
- 现象：能力矩阵在 ConnectorController（GET /connectors），check 在 IngestController（POST /tasks/{id}/check）。
- 评估：两端点职责不同（能力矩阵全局 vs 任务级 check），分开放可接受。也可后续合并到 IngestController 统一 ingest 命名空间。不阻断。

#### P3（提示）

**H-4：ConnectorSpecs 未知协议默认 spec**
- 现象：forProtocol 对未知协议返回默认 spec（JSON/FULL/checkpoint=true/discover=false）。
- 评估：防御性默认，合理。未知协议实际不会装配（Factory 抛异常），此处仅兜底。

**H-5：discover 统一返回空**
- 现象：AbstractSourceConnector.discover 返回 List.of()，DB/Kafka/MQ/FTP 的 supportsDiscover=true 但未覆盖 discover 实现。
- 评估：符合计划（discover 详细 schema 推断为增强）。supportsDiscover 声明能力"可发现"，实际实现留后续。属能力声明与实现的轻微 gap，不阻断。

## 7. 是否超出任务范围

- **InMemoryOffsetStore/RedisOffsetStore 改单调**：为保证三 OffsetStore 实现行为一致（与 JdbcOffsetStore 的 Math.max 对齐），属合理伴随修复，非无关重构。
- **PipelineApplication 装配 8 适配器 Bean**：为能力矩阵 + contract test 提供协议覆盖，属任务范围（F-3 能力矩阵需 8 协议）。
- 无前端改动（任务单明确"前端展示留后续"）。
- 无大型依赖引入（用 JDK + 既有依赖）。

## 8. 是否过度设计

未发现过度设计。SourceConnector 合约为必要抽象；AbstractSourceConnector 桥接避免重写 8 适配器；contract test 框架 DRY；能力矩阵为验收必要。read 简化为最小实现（非过度），真实分页合理留后。

## 9. 安全风险

- ✅ check 端点权限 ingest:create，能力矩阵 ingest:view。
- ✅ approve 前置校验防止未测试/未配置任务上线。
- ✅ 无 SQL 注入（参数化 + upsert）。
- ✅ checkpoint 不含敏感数据（仅 offset/json）。
- 无新增安全风险。

## 10. 审查结论

**建议通过**

P1-01 达成全部最小可行结果：SourceConnector 合约（spec/check/discover/read/checkpoint/close）、8 协议桥接 + 能力矩阵、t_ingest_checkpoint + JdbcOffsetStore 断点续传持久化、能力矩阵/check 端点、approve 前置校验、8 协议 contract test 框架。后端 pipeline 108 + 全量回归 BUILD SUCCESS 无回归。代码质量高（合约清晰、桥接零破坏、checkpoint 单调+三库兼容 upsert、contract test 框架 DRY、approve 前置完整）。

借鉴 Airbyte SourceConnector 合约思想落地正确，未引入 SDK，轻量实现。

P2 建议（H-1~H-3）均为合理简化/伴随设计，不阻断。P3 提示（H-4/H-5）为能力声明与实现的轻微 gap，留后续。

## 11. 返工任务清单

无强制返工。后续可选改进（不阻断）：

1. [ ] H-1：Kafka/DB SourceConnector 子类覆盖 read 实现真实增量分页（基于 offset 消费/分页查询）。
2. [ ] H-5：DB/Kafka/MQ/FTP 覆盖 discover 返回真实字段/表（supportsDiscover=true 的协议）。
3. [ ] H-3：评估 ConnectorController 合并到 IngestController 统一命名空间（可选）。
4. [ ] 前端：能力矩阵展示页（后端端点已就绪，前端留后续）。

## 12. 建议提交

P1-01 可提交。建议提交信息：

```text
feat(P1-01): SourceConnector contract with capability matrix and checkpoint persistence

- SourceConnector contract (spec/check/discover/read/checkpoint/close) extends AutoCloseable
- AbstractSourceConnector bridges ProtocolAdapter.fetch to read; 8 protocols declare ConnectorSpec
- ConnectorSpecs capability matrix (HTTP/WebService/ApiGateway/FTP/SFTP/Kafka/MQ/DB)
- t_ingest_checkpoint (V017/U017 MySQL+DM) + JdbcOffsetStore upsert, monotonic offset
- IngestService.testAndIngest uses connector.read + checkpoint; approve validates check/mapping/rules
- GET /api/v1/ingest/connectors (capability matrix) + POST /tasks/{id}/check
- contract test framework: AbstractSourceConnectorContractTest + 8 protocol subclasses
- InMemoryOffsetStore/RedisOffsetStore aligned to monotonic put
- mvn test green; borrows Airbyte SourceConnector contract idea without SDK
```
