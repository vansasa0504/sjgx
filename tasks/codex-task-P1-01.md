# Codex 执行任务 - P1-01：Connector 合约

> 阶段：P1（验收增强）
> 任务编号：P1-01
> 分支：`ai/p1-connector-contract`（从 master 切出，master 已含 P0-01~P0-10）
> 依据：`tasks/claude-plan-P1-01.md`（第一性原理计划，权威）、`docs/github-reference-functional-design.md` §4（Airbyte SourceConnector）、`docs/development-process-workflow.md` §3.2 P1-01
> 前置：P0-03（IngestService 落库）已合入 master
> 日期：2026-06-29

---

## 1. 背景与目标

现有 `ProtocolAdapter` 接口仅 `protocol()+fetch()`，无能力声明（discover/check/checkpoint/close）；8 个协议适配器无统一合约规范；OffsetStore 仅有 InMemory/Redis，**无 JDBC 持久化**（重启丢 checkpoint）；无能力矩阵；无 contract test 框架。验收要求"每类协议 contract test 通过"。

借鉴 **Airbyte** 的 SourceConnector 合约思想（discover/check/read/checkpoint/close + offset 持久化），用 JDK + 既有依赖实现轻量合约，不引入 Airbyte SDK。

**最小可行结果**：
1. SourceConnector 合约接口（spec/check/discover/read/checkpoint/close）+ ConnectorSpec 能力声明。
2. 8 协议适配器桥接实现合约 + 能力矩阵。
3. t_ingest_checkpoint 表 + JdbcOffsetStore，断点续传重启可恢复。
4. 能力矩阵端点 + check 端点 + approve 前置校验。
5. 每类协议 contract test 通过。

## 2. 范围

### 本次实现

- **F-1 SourceConnector 合约**：新增接口 `spec()/check(URI)/discover(URI)/read(URI,long,int)/checkpoint(long,long)/close()/protocol()`。
- **F-2 适配器桥接**：现有 ProtocolAdapter.fetch 桥接为 SourceConnector.read；提供默认抽象实现 `AbstractSourceConnector` 包装 fetch，check/discover 提供最小实现（HTTP 类 discover 返回空）。
- **F-3 能力矩阵**：每协议 `ConnectorSpec`（protocol/formats/syncModes/supportsCheckpoint/supportsDiscover）；8 协议声明。
- **F-4 checkpoint JDBC**：新增 `t_ingest_checkpoint`（V017/U017 双库）+ `JdbcOffsetStore implements OffsetStore`（upsert by task_id+connector_type，IdGenerator）。
- **F-5 IngestService 衔接**：testAndIngest 经 SourceConnector.read + checkpoint 持久化；approve 前置校验（check 通过 + 有映射 + 有规则，否则 INGEST-CONNECT-FAILED/INGEST-MAPPING-MISSING/INGEST-RULE-MISSING）。
- **F-6 contract test 框架**：抽象基类 `AbstractSourceConnectorContractTest` 定义合约用例（check 通过/失败、read 返回批次、checkpoint 写读、断点恢复 offset 单调递增、close 释放）；每协议子类提供夹具。
- **F-7 能力矩阵端点**：`GET /api/v1/ingest/connectors` 返回能力矩阵（权限 ingest:view）；`POST /api/v1/ingest/tasks/{id}/check` 连接测试（权限 ingest:create）。

### 不做

- 不引入 Airbyte SDK 或新大型依赖。
- 不重写 8 适配器的 fetch 逻辑（桥接包装）。
- 不做 connector marketplace/动态加载（P2）。
- 不做 discover 详细 schema 推断（HTTP 类返回空）。
- 不改前端（能力矩阵端点后端先就绪，前端展示留后续）。

## 3. 必读输入

- `AGENTS.md`、`tasks/claude-plan-P1-01.md`（权威计划）
- `docs/github-reference-functional-design.md` §4（Airbyte SourceConnector 合约）
- `platform-pipeline/src/main/java/.../ingest/ProtocolAdapter.java`、`adapter/*`（8 适配器 + Factory）
- `platform-pipeline/src/main/java/.../ingest/sync/OffsetStore.java`、`InMemoryOffsetStore.java`、`SyncStrategy.java`
- `platform-pipeline/src/main/java/.../ingest/IngestService.java`、`IngestTask.java`
- `platform-pipeline/src/test/java/.../ingest/adapter/ProtocolAdaptersTest.java`（夹具模式参考）
- `db/migration/V003__ingest.sql`（t_ingest_task/t_raw_data 现状）
- `platform-common/src/test/java/.../db/MigrationDialectCompatibilityTest.java`（方言守护）
- `platform-common/src/main/java/.../db/IdGenerator.java`（落库 id 模式）

## 4. 需要修改的模块

| 文件 | 改动 |
|---|---|
| 新增 `SourceConnector.java` | 合约接口 |
| 新增 `ConnectorSpec.java` | 能力声明 record |
| 新增 `ConnectorCheckResult.java` | check 结果 record（ok/message） |
| 新增 `RawDataBatch.java` | read 批次结果 record（records/nextOffset） |
| 新增 `OffsetCheckpoint.java` | checkpoint 模型 |
| 新增 `AbstractSourceConnector.java` | 桥接 ProtocolAdapter.fetch 为 read，默认 check/discover |
| 8 适配器 | 各自声明 ConnectorSpec（或 Factory 统一声明） |
| 新增 `JdbcOffsetStore.java`（sync 包） | OffsetStore JDBC 实现 |
| `IngestService.java` | 衔接 checkpoint + approve 前置校验 |
| `PipelineApplication.java` | JdbcOffsetStore Bean 装配 |
| `IngestController.java` | GET /connectors + POST /tasks/{id}/check 端点 |
| `db/migration/V017__ingest_checkpoint.sql` + U017 | t_ingest_checkpoint 表 |
| `db/migration-dm/V017__ingest_checkpoint.sql` + U017 | 达梦版 |
| `MigrationDialectCompatibilityTest.java` | 纳入 V017 + t_ingest_checkpoint CRUD |
| 新增 `AbstractSourceConnectorContractTest.java` + 8 协议子类 | contract test 框架 |
| 新增 `JdbcOffsetStoreTest.java` | checkpoint JDBC 持久化 + 重启恢复 |

## 5. 数据库/API 影响

### 5.1 数据库
- 新增 `t_ingest_checkpoint(id, task_id, connector_type, offset_value, checkpoint_json, updated_at)` + idx_checkpoint_task。
- V017/U017 MySQL + 达梦双库，避开 ` LIMIT `/` TEXT`（DM）方言守护。

### 5.2 API
- `GET /api/v1/ingest/connectors`（ingest:view）→ 能力矩阵 List<ConnectorSpec>。
- `POST /api/v1/ingest/tasks/{id}/check`（ingest:create）→ ConnectorCheckResult。
- approve 前置：check 未通过/缺映射/缺规则 → 409/400 拒绝。

## 6. 实现细节约束

### 6.1 SourceConnector 合约
```java
public interface SourceConnector {
    ConnectorSpec spec();
    ConnectorCheckResult check(URI endpoint);
    List<String> discover(URI endpoint);                    // 无 schema 返回空 list
    RawDataBatch read(URI endpoint, long offset, int batchSize);
    long checkpoint(long taskId, long offset);              // 持久化，返回新 offset
    void close();
    String protocol();
}
```

### 6.2 桥接（AbstractSourceConnector）
- 包装 `ProtocolAdapter.fetch` 为 `read`：fetch 全量后按 offset/batchSize 切片返回 RawDataBatch（nextOffset 推进）。
- `check`：尝试 fetch 最小数据或连接，成功 ok=true，失败 ok=false+message。
- `discover`：默认返回空 list（HTTP/WebService/ApiGateway）；DB/Kafka/MQ/FTP 子类可覆盖返回字段/表。
- `checkpoint`：委托 OffsetStore.put(taskId, offset)。
- `close`：默认空，子类按需覆盖（HTTP 无资源，Kafka 关 consumer 等）。

### 6.3 能力矩阵（ConnectorSpec）
```text
HTTP        formats=[JSON,XML,CSV]    syncModes=[FULL,INCREMENTAL,SCHEDULED,RESUME] checkpoint=true discover=false
WebService  formats=[XML,JSON]        syncModes=[FULL,SCHEDULED]                    checkpoint=true discover=false
ApiGateway  formats=[JSON]            syncModes=[FULL,REALTIME]                      checkpoint=true discover=false
FTP         formats=[CSV,Excel]       syncModes=[FULL,SCHEDULED]                     checkpoint=true discover=true
SFTP        formats=[CSV,Excel]       syncModes=[FULL,SCHEDULED]                     checkpoint=true discover=true
Kafka       formats=[JSON]            syncModes=[REALTIME,INCREMENTAL]               checkpoint=true discover=true
MQ          formats=[JSON]            syncModes=[REALTIME]                           checkpoint=true discover=true
DB          formats=[JSON,CSV]        syncModes=[FULL,INCREMENTAL,SCHEDULED,RESUME]  checkpoint=true discover=true
```

### 6.4 JdbcOffsetStore
- `put(key, offset)`：upsert `t_ingest_checkpoint` by task_id+connector_type（key 编码两者）。
- `get(key)`：SELECT offset_value，无则 0。
- IdGenerator 生成 id（与 P0-03 一致）。
- 与 InMemoryOffsetStore 接口一致，PipelineApplication 按 jdbcTemplate 存在切换。

### 6.5 approve 前置校验
- IngestService.apply(TEST→PENDING_APPROVAL 之后的 approve，或单独 approve 事件）：校验 check 通过 + fieldMapping 非空 + qualityRules 非空。
- 若 IngestService 已有部分校验，补全 + 抛 INGEST-CONNECT-FAILED/INGEST-MAPPING-MISSING/INGEST-RULE-MISSING。
- 不破坏既有状态机。

### 6.6 方言守护
- V017 SQL 不含 ` LIMIT `（带空格）、DM 不含 ` TEXT`/` TINYINT `。
- upsert 用 `INSERT ... ON DUPLICATE KEY UPDATE`（MySQL）+ 达梦等效（MERGE 或先 UPDATE 后 INSERT），参考 P0-03 IngestService.persistTask 的 upsert 模式。

## 7. 必须补充的测试

- **contract test 框架**：`AbstractSourceConnectorContractTest` 定义抽象用例：
  - check 成功返回 ok=true
  - check 失败（坏 endpoint）返回 ok=false+message
  - read 返回 RawDataBatch，nextOffset 推进
  - checkpoint 写后读一致
  - 断点恢复：read(offset=N) 从 N 继续，offset 单调递增
  - close 不抛异常
- **每协议子类**：HTTP（HttpServer）、WebService、ApiGateway、FTP、SFTP、Kafka（MockConsumer）、MQ（RabbitTemplate mock）、DB（H2），复用 ProtocolAdaptersTest 夹具模式。
- **JdbcOffsetStoreTest**：put→get 一致；新建 store（同 jdbc）→ 读回（重启恢复）；offset 单调。
- **MockMvc**：GET /connectors 200 返回 8 协议能力（ingest:view）；POST /tasks/{id}/check 200 + 失败 4xx；权限 401/403。
- **approve 前置**：check 未通过/缺映射/缺规则 → 拒绝（409/400）。
- **三库迁移**：MigrationDialectCompatibilityTest 纳入 V017，t_ingest_checkpoint CRUD 通过。

## 8. 验收命令

```bash
mvn -pl platform-common install -DskipTests   # 若 common 改动
mvn test -pl platform-pipeline                 # contract test + JDBC checkpoint
mvn test                                       # 全量回归
```

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| SourceConnector 接口破坏既有 ProtocolAdapter | 桥接模式，ProtocolAdapter 保留；既有 ProtocolAdaptersTest 回归 |
| 8 协议 contract test 夹具复杂 | 复用 ProtocolAdaptersTest 夹具；DB 用 H2，FTP/SFTP/Kafka/MQ 用既有 mock |
| upsert 三库不兼容 | 参考 P0-03 IngestService.persistTask 的 upsert 模式（先 UPDATE affected==0 再 INSERT） |
| checkpoint 并发 | upsert 原子 + 单 task 串行 |
| **回滚** | V017 有 U017；SourceConnector 新增接口移除不影响既有；JdbcOffsetStore 缺失回退 InMemory |

## 10. 完成判定

- [ ] SourceConnector 合约接口 + ConnectorSpec/ConnectorCheckResult/RawDataBatch/OffsetCheckpoint。
- [ ] 8 协议适配器桥接实现合约 + 能力矩阵声明。
- [ ] t_ingest_checkpoint（V017/U017 双库）+ JdbcOffsetStore，重启恢复。
- [ ] GET /api/v1/ingest/connectors + POST /tasks/{id}/check 端点。
- [ ] approve 前置校验（check/映射/规则）。
- [ ] 每类协议 contract test 通过（抽象基类 + 8 子类）。
- [ ] JdbcOffsetStore 持久化 + 重启恢复测试通过。
- [ ] 三库迁移测试纳入 V017。
- [ ] mvn test 全绿。
- [ ] 输出合约设计 + 能力矩阵 + contract test 证据 + 潜在风险。

## 11. 实现边界（Codex 遵守）

1. 不引入 Airbyte SDK 或新大型依赖，用 JDK + 既有依赖。
2. 不重写 8 适配器 fetch 逻辑，用 AbstractSourceConnector 桥接包装。
3. checkpoint 表 V017/U017 MySQL + 达梦双库，避开方言守护（` LIMIT `、DM ` TEXT`/` TINYINT `）。
4. JdbcOffsetStore 用 IdGenerator + upsert（先 UPDATE affected==0 再 INSERT），与 P0-03 一致。
5. 不破坏 P0-10 IngestService 多协议路由（adapterFor 保留）。
6. discover 对无 schema 协议返回空，不强造。
7. approve 前置校验不破坏既有状态机。
8. contract test 抽象基类 + 每协议子类，复用 ProtocolAdaptersTest 夹具。
9. 不改密钥/生产配置；不动无关模块；不跳过测试。
10. 完成后输出修改文件、测试命令、测试结果、合约设计、能力矩阵、contract test 证据、潜在风险。

## 12. 借鉴说明

借鉴 **Airbyte** SourceConnector 合约（discover/check/read/checkpoint/close + offset 持久化）与 connector catalog（能力矩阵），用轻量实现，不引入 SDK。详见 `docs/github-reference-functional-design.md` §4。
