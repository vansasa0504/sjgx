# 第一性原理开发计划 — P1-01 Connector 合约

> 任务编号：P1-01
> 分支：`ai/p1-connector-contract`（从 master 切出，master 已含 P0-01~P0-10）
> 依据：`tasks/claude-plan-P1.md`、`docs/github-reference-functional-design.md` §4（Airbyte SourceConnector）、`docs/development-process-workflow.md` §3.2 P1-01
> 前置：P0-03（IngestService 落库）已合入 master
> 日期：2026-06-29

---

## 1. 需求来源

- **技术要求**：外部数据接入（FR-201~205）要求多协议规范化接入、断点续传、上线管控（approve 前完成连接测试/字段映射/质量规则）。
- **验收口径**：P1-01 通过标准是"每类协议 contract test 通过"，证明接入规范化。
- **参考**：Airbyte 的 SourceConnector 合约（discover/check/read/checkpoint/close）。
- **现状缺口**：
  - `ProtocolAdapter` 接口仅 `protocol()+fetch()`，无能力声明（discover/check/checkpoint/close）。
  - 8 个适配器（HTTP/WebService/ApiGateway/FTP/SFTP/Kafka/MQ/DB）各自实现 fetch，无统一合约规范。
  - OffsetStore 仅有 InMemory/Redis，**无 JDBC 持久化**（重启丢失 checkpoint）。
  - 无能力矩阵，验收无法证明各协议支持的能力。
  - 无 contract test 框架（现有 ProtocolAdaptersTest 是集成式，非合约式）。

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？
接入规范化：不同协议的数据源差异收敛到统一合约，调度只处理状态/offset/批次，验收方能确认每类协议都遵循同一规范且能力可查。

### 2.2 最小可行结果
1. SourceConnector 合约（discover/check/read/checkpoint/close）。
2. 8 个适配器实现合约 + 能力矩阵声明。
3. checkpoint JDBC 持久化（t_ingest_checkpoint + JdbcOffsetStore），重启可恢复。
4. 每类协议 contract test 通过。

### 2.3 系统必须接收哪些输入？
- 连接配置（endpoint + 协议参数）。
- 同步模式（FULL/INCREMENTAL/REALTIME/SCHEDULED/RESUME）。
- 已有 checkpoint（断点续传）。

### 2.4 系统必须产生哪些输出？
- discover：连接测试结果 + 可用字段/表。
- read：标准记录批次（RawDataBatch）。
- checkpoint：持久化的 offset。
- 能力矩阵：每协议支持的能力清单。

### 2.5 不可省略的处理过程？
1. check：连接测试（验证 endpoint 可达 + 凭证有效）。
2. discover：发现可用字段/表（HTTP 无 schema 时返回空/默认）。
3. read：按 checkpoint 读取批次。
4. checkpoint：读写 offset，JDBC 持久化。
5. close：释放资源。

### 2.6 哪些是核心能力？
- SourceConnector 合约接口。
- checkpoint JDBC 持久化（断点续传重启可恢复）。
- contract test 框架。
- 能力矩阵。

### 2.7 哪些是增强能力？
- discover 返回详细 schema（HTTP 类无 schema，返回最小）。
- 能力矩阵可视化端点。
- Redis OffsetStore 已有，本任务补 JDBC 即可。

### 2.8 最小改动路径？
- **新增 SourceConnector 接口**（不破坏 ProtocolAdapter，向后兼容）：声明 spec()/check()/discover()/read()/checkpoint()/close()。
- 现有 ProtocolAdapter 适配为 SourceConnector（fetch → read 的简化），用默认实现桥接，避免重写 8 个适配器。
- 新增 `t_ingest_checkpoint` 表 + `JdbcOffsetStore`（与 P0-03 落库模式一致）。
- 新增 `ConnectorSpec`（能力声明）+ 能力矩阵查询。
- contract test 抽象基类，每协议子类覆盖 check/read/checkpoint/失败。

### 2.9 如何测试？
- contract test 框架：抽象基类定义合约用例（check 通过/失败、read 返回批次、checkpoint 写读、断点恢复 offset 单调递增、close 释放）。
- 每类协议子类提供测试夹具（HTTP 用内嵌 HttpServer，Kafka 用 MockConsumer，MQ 用 RabbitTemplate mock，DB/FTP/SFTP 用已有测试模式）。
- JDBC checkpoint：写 offset → 新建 JdbcOffsetStore → 读回一致 + 重启恢复。
- 上线前置：缺映射/缺规则/连接失败不能 approve（核查现有 IngestService 是否已有，有则补断言）。

### 2.10 如何验收？
每类协议 contract test 通过 + checkpoint JDBC 持久化重启恢复 + 能力矩阵可查。

### 2.11 如何避免过度设计？
- 不引入 Airbyte 全量 SDK，只借鉴合约思想，用 JDK + 既有依赖实现。
- discover 对无 schema 协议（HTTP）返回最小结果，不强造 schema。
- 不重写 8 适配器的 fetch 逻辑，用桥接适配为 SourceConnector。
- 不做 connector marketplace/动态加载（P2 范围）。
- checkpoint 表只存 task_id/connector_type/offset_value/checkpoint_json，最小字段。

## 3. 功能拆解

| 编号 | 功能 | 说明 |
|---|---|---|
| F-1 | SourceConnector 合约接口 | spec/check/discover/read/checkpoint/close + ConnectorSpec 能力声明 |
| F-2 | 适配器桥接 | 现有 ProtocolAdapter 适配为 SourceConnector（read=fetch，check/discover 最小实现） |
| F-3 | 能力矩阵 | 每协议 ConnectorSpec 声明（协议/格式/同步模式/断点续传/限流），查询端点 |
| F-4 | checkpoint JDBC 持久化 | t_ingest_checkpoint 表 + JdbcOffsetStore，重启恢复 |
| F-5 | IngestService 衔接 | testAndIngest 经 SourceConnector.read + checkpoint；approve 前置校验（check 通过） |
| F-6 | contract test 框架 | 抽象基类 + 每协议子类覆盖 check/read/checkpoint/失败/恢复 |
| F-7 | 能力矩阵端点 | GET /api/v1/ingest/connectors 返回能力矩阵（权限 ingest:view） |

## 4. 影响模块

| 模块 | 改动 |
|---|---|
| platform-pipeline.ingest | 新增 SourceConnector/ConnectorSpec/ConnectorCheckResult/OffsetCheckpoint/RawDataBatch；适配器桥接；JdbcOffsetStore |
| platform-pipeline.ingest.sync | OffsetStore 新增 JDBC 实现 |
| platform-pipeline.ingest | IngestService 衔接 checkpoint + approve 前置 |
| platform-pipeline.ingest.PipelineApplication | JdbcOffsetStore Bean 装配 |
| db/migration + db/migration-dm | V017 t_ingest_checkpoint + U017 |
| platform-pipeline/test | contract test 框架 + 各协议子类 + JdbcOffsetStore 测试 |
| platform-common.db | MigrationDialectCompatibilityTest 纳入 V017 |

## 5. 接口设计

### 5.1 SourceConnector 合约

```java
public interface SourceConnector {
    ConnectorSpec spec();                          // 能力声明
    ConnectorCheckResult check(URI endpoint);      // 连接测试
    List<String> discover(URI endpoint);           // 发现字段/表（无 schema 返回空）
    RawDataBatch read(URI endpoint, long offset, int batchSize);  // 按批次读取
    long checkpoint(long taskId, long offset);     // 持久化 offset，返回新 offset
    void close();                                   // 释放资源
    String protocol();                              // 协议标识（兼容既有）
}
```

### 5.2 ConnectorSpec（能力矩阵）

```java
public record ConnectorSpec(
    String protocol,
    List<String> formats,           // 支持格式 JSON/XML/CSV/Excel
    List<String> syncModes,         // FULL/INCREMENTAL/REALTIME/SCHEDULED/RESUME
    boolean supportsCheckpoint,     // 断点续传
    boolean supportsDiscover        // 是否可发现 schema
) {}
```

### 5.3 API

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `GET /api/v1/ingest/connectors` | GET | ingest:view | 返回能力矩阵（各协议 ConnectorSpec） |
| `POST /api/v1/ingest/tasks/{id}/check` | POST | ingest:create | 连接测试（check），approve 前置 |

## 6. 数据结构

### 6.1 t_ingest_checkpoint（V017）

```sql
CREATE TABLE t_ingest_checkpoint (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    connector_type VARCHAR(32) NOT NULL,
    offset_value BIGINT NOT NULL,
    checkpoint_json VARCHAR(512),
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_checkpoint_task ON t_ingest_checkpoint(task_id);
```

- task_id + connector_type 唯一确定一个 checkpoint（upsert）。
- offset_value 单调递增，断点续传从该值恢复。

### 6.2 能力矩阵（内存）

```text
HTTP        | JSON/XML/CSV | FULL/INCREMENTAL/SCHEDULED/RESUME | checkpoint=YES | discover=NO
WebService  | XML/JSON      | FULL/SCHEDULED                     | checkpoint=YES | discover=NO
ApiGateway  | JSON          | FULL/REALTIME                      | checkpoint=YES | discover=NO
FTP/SFTP    | CSV/Excel     | FULL/SCHEDULED                     | checkpoint=YES | discover=YES
Kafka       | JSON          | REALTIME/INCREMENTAL               | checkpoint=YES | discover=YES
MQ          | JSON          | REALTIME                           | checkpoint=YES | discover=YES
DB          | JSON/CSV      | FULL/INCREMENTAL/SCHEDULED/RESUME  | checkpoint=YES | discover=YES
```

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| check 失败 | approve 前置校验拒绝（INGEST-CONNECT-FAILED） |
| 缺字段映射 | approve 前置拒绝（INGEST-MAPPING-MISSING） |
| 缺质量规则 | approve 前置拒绝（INGEST-RULE-MISSING） |
| read 失败 | 抛异常 + 不写 checkpoint（保证下次从上次 checkpoint 恢复） |
| checkpoint 并发 | upsert 原子，单 task 串行 |
| 断点续传 offset 回退 | checkpoint 只增不减，回退忽略 |
| 无 schema 协议 discover | 返回空 list，不报错 |

## 8. 测试策略

| 测试 | 覆盖 |
|---|---|
| contract test 抽象基类 | 定义合约用例模板（check/read/checkpoint/失败/恢复） |
| 每协议 contract test | HTTP（HttpServer）、WebService、ApiGateway、FTP、SFTP、Kafka（MockConsumer）、MQ（RabbitTemplate mock）、DB |
| JDBC checkpoint | 写 offset → 新建 store → 读回一致；重启恢复 offset 单调递增 |
| 能力矩阵端点 | MockMvc GET /connectors 返回 8 协议能力 |
| check 端点 | MockMvc 连接测试 200 + 失败 4xx |
| approve 前置 | check 未通过/缺映射/缺规则 → 不能 approve |
| 三库迁移 | MigrationDialectCompatibilityTest 纳入 V017，t_ingest_checkpoint CRUD |

## 9. Codex 实现边界

1. 不引入 Airbyte SDK 或新大型依赖，用 JDK + 既有依赖实现合约。
2. 不重写 8 适配器的 fetch 逻辑，用桥接（默认 SourceConnector 实现包装 ProtocolAdapter.fetch 为 read）。
3. checkpoint 表用 V017/U017（MySQL + 达梦双库），避开 ` LIMIT `/` TEXT`（DM）方言守护。
4. JdbcOffsetStore 与 P0-03 落库模式一致（IdGenerator + upsert）。
5. 不破坏 P0-10 刚接通的 IngestService 多协议路由（adapterFor 保留）。
6. approve 前置校验若 IngestService 已有则补断言，无则新增。
7. discover 对无 schema 协议返回空，不强造。
8. contract test 抽象基类 + 每协议子类，复用现有 ProtocolAdaptersTest 的夹具模式。
9. 不改密钥/生产配置；不动无关模块。
10. 必须补测试并全绿，输出合约设计 + 能力矩阵 + contract test 证据。

## 10. 验收标准

- [ ] SourceConnector 合约接口（spec/check/discover/read/checkpoint/close）。
- [ ] 8 协议适配器实现合约 + 能力矩阵声明。
- [ ] t_ingest_checkpoint 表（V017/U017 双库）+ JdbcOffsetStore，重启恢复。
- [ ] 能力矩阵端点 GET /api/v1/ingest/connectors。
- [ ] check 端点 + approve 前置校验。
- [ ] 每类协议 contract test 通过。
- [ ] 三库迁移测试纳入 V017。
- [ ] mvn test + 前端（无前端改动）全绿。
- [ ] 输出合约设计 + 能力矩阵 + contract test 证据。

## 11. 风险与回滚

| 风险 | 控制 |
|---|---|
| SourceConnector 接口扩展破坏既有 ProtocolAdapter 用法 | 桥接模式，ProtocolAdapter 保留，SourceConnector 是上层合约；既有测试回归 |
| 8 协议 contract test 夹具复杂（FTP/SFTP/DB） | 复用现有 ProtocolAdaptersTest 夹具模式；DB 用 H2，FTP/SFTP 用既有 mock |
| checkpoint 并发写 | upsert 原子 + 单 task 串行 |
| discover 对 HTTP 类无意义 | 返回空，不报错，不强造 schema |
| **回滚** | V017 有 U017；SourceConnector 是新增接口，移除不影响既有；JdbcOffsetStore 缺失回退 InMemory |

## 12. 借鉴说明

本任务借鉴 **Airbyte** 的 SourceConnector 合约思想（discover/check/read/checkpoint/close 五能力 + offset/checkpoint 持久化），但不引入 Airbyte SDK，用 JDK + 既有依赖实现轻量合约。能力矩阵借鉴 Airbyte 的 connector catalog 概念。详见 `docs/github-reference-functional-design.md` §4。
