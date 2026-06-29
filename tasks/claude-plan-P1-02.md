# 第一性原理开发计划 — P1-02 目录治理

> 任务编号：P1-02
> 分支：`ai/p1-catalog-lineage`（从 master 切出，master 已含 P1-01）
> 依据：`tasks/claude-plan-P1.md`、`docs/github-reference-functional-design.md` §6（DataHub 元数据/血缘/质量摘要）、`docs/development-process-workflow.md` §3.2 P1-02
> 前置：P0-07（目录申请）已合入 master
> 日期：2026-06-29

---

## 1. 需求来源

- **技术要求**：数据目录访问（FR-401~405）要求目录多维分类、元信息管理、血缘可追溯、质量摘要、使用统计。P0-07 已闭环目录申请审批，但目录详情仅静态元信息，无血缘/质量/使用追溯。
- **验收口径**：P1-02 通过标准"目录详情可追溯"——血缘、质量摘要、使用统计。
- **参考**：DataHub 的元数据优先思想（目录作为资产/字段/血缘/质量/使用/权限/申请审批综合入口）。
- **现状缺口**：
  - DataCatalogItem 仅静态元信息，无血缘/质量摘要/使用统计。
  - t_data_service 无 catalog_id 关联，血缘 `catalog→service` 缺直接关联。
  - t_service_invoke_log 有 service_code/consumer_code/created_at，可做使用统计，但无 catalog 直接关联。
  - t_quality_check_result 按 batch_no，无 catalog 关联。
  - 无 t_catalog_lineage 表。

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？
目录详情可追溯：从目录资产能追溯到上游（接入任务/合作方/raw_data）、下游（数据服务）、质量（最近质量分/问题数）、使用（调用量/申请量），让验收方确认资产全链路可查。

### 2.2 最小可行结果
1. 血缘：目录资产的上游（接入任务/合作方）+ 下游（数据服务）可查。
2. 质量摘要：目录资产最近质量分 + 问题数可查。
3. 使用统计：目录资产调用量 + 申请量可查。
4. 目录详情端点聚合血缘/质量/使用。

### 2.3 系统必须接收哪些输入？
- 目录资产 id。
- 血缘关系（catalog ↔ 接入任务/服务，显式或推断）。

### 2.4 系统必须产生哪些输出？
- CatalogLineage：上下游节点列表。
- CatalogQualitySummary：最近质量分 + 问题数。
- CatalogUsageSummary：调用量 + 申请量。
- 目录详情聚合（meta + lineage + quality + usage）。

### 2.5 不可省略的处理过程？
1. 血缘：建立 catalog → 接入任务（上游）/ catalog → 服务（下游）关联。
2. 质量摘要：按 catalog 关联质量结果聚合（最近质量分 + open 问题数）。
3. 使用统计：按 catalog 关联调用日志（调用量）+ 申请记录（申请量）。
4. 聚合端点：目录详情一次性返回血缘/质量/使用。

### 2.6 哪些是核心能力？
- 血缘关系建模（t_catalog_lineage）。
- 质量摘要聚合。
- 使用统计聚合。
- 目录详情聚合端点。

### 2.7 哪些是增强能力？
- 血缘可视化（前端图谱，留后续）。
- 质量趋势历史（本任务只取最近）。
- 使用统计多维度（本任务调用量+申请量，趋势留后续）。

### 2.8 最小改动路径？
- **新增 t_catalog_lineage 表**（catalog_id, node_type, node_id, node_name, direction）显式血缘，避免改既有表结构（DataHub 思路）。
- **catalog↔service 关联**：DataServiceDefinition 新增 catalogId 字段（注册时绑定），或血缘表记录。优先血缘表（不改服务表结构，最小侵入）。
- **质量摘要**：t_quality_check_result 无 catalog 关联，通过 IngestTask.batch_no 或新增 catalog 关联。最小方案：血缘表关联 catalog↔ingest_task，质量按 task 的 batch 聚合；或目录资产直接挂质量分（CatalogService 维护最近质量分字段）。**采用**：新增 t_catalog_quality_summary（catalog_id, score, issue_count, updated_at）由质量模块写入或目录聚合。
- **使用统计**：调用日志按 service_code 聚合，经 catalog↔service 血缘映射到 catalog；申请量按 catalog_id 从 t_catalog_application 聚合。
- **聚合端点**：GET /api/v1/catalog/{id}/lineage、/quality-summary、/usage-summary，或合并 /detail。

### 2.9 如何测试？
- 血缘：注册服务绑定 catalog → 查 lineage 返回上下游。
- 质量摘要：写入质量结果 → 查 quality-summary 返回最近分 + 问题数。
- 使用统计：调用日志 + 申请记录 → 查 usage-summary 返回调用量 + 申请量。
- 聚合端点：/detail 返回血缘+质量+使用。
- 持久化：重启可查。

### 2.10 如何验收？
目录详情可追溯（血缘+质量+使用），持久化重启可查。

### 2.11 如何避免过度设计？
- 不做血缘图谱可视化（前端留后续）。
- 不做质量趋势历史（只取最近）。
- 不做使用统计多维度趋势（只调用量+申请量）。
- 血缘用显式表，不改既有 t_data_service/t_ingest_task 结构（最小侵入）。
- 质量摘要用独立表，不强行关联 t_quality_check_result（避免跨模块耦合）。

## 3. 功能拆解

| 编号 | 功能 | 说明 |
|---|---|---|
| F-1 | 血缘表 + 仓储 | t_catalog_lineage + JdbcCatalogLineageRepository + 内存实现 |
| F-2 | 血缘写入 | catalog 发布时建立上游（接入任务/合作方）+ 下游（服务）血缘 |
| F-3 | 质量摘要表 + 仓储 | t_catalog_quality_summary + 仓储，记录最近质量分 + 问题数 |
| F-4 | 使用统计聚合 | 按 catalog 聚合调用量（经 service 血缘）+ 申请量（t_catalog_application） |
| F-5 | 目录详情端点 | GET /catalog/{id}/lineage、/quality-summary、/usage-summary、/detail 聚合 |
| F-6 | catalog↔service 关联 | 服务注册时可选绑定 catalogId，或血缘表记录下游 |

## 4. 影响模块

| 模块 | 改动 |
|---|---|
| platform-pipeline.catalog | 新增 CatalogLineage/CatalogQualitySummary/CatalogUsageSummary 模型 + Repository（JDBC+内存）+ 聚合 Service |
| platform-pipeline.catalog.CatalogController | 新增 lineage/quality-summary/usage-summary/detail 端点 |
| platform-pipeline.catalog.CatalogService | 发布/质量更新时写血缘/质量摘要 |
| db/migration + db/migration-dm | V018 t_catalog_lineage + t_catalog_quality_summary + U018 |
| platform-common.db | MigrationDialectCompatibilityTest 纳入 V018 |
| platform-ui | CatalogView 详情抽屉展示血缘/质量/使用（可选，后端先就绪） |

## 5. 接口设计

### 5.1 模型

```java
public record CatalogLineage(long catalogId, String nodeType, long nodeId, String nodeName, String direction) {}
// nodeType: INGEST_TASK/PARTNER/DATA_SERVICE; direction: UPSTREAM/DOWNSTREAM

public record CatalogQualitySummary(long catalogId, double score, int issueCount, Instant updatedAt) {}

public record CatalogUsageSummary(long catalogId, long invokeCount, long applicationCount, Instant updatedAt) {}
```

### 5.2 API

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `GET /api/v1/catalog/{id}/lineage` | GET | catalog:view | 上下游血缘 |
| `GET /api/v1/catalog/{id}/quality-summary` | GET | catalog:view | 最近质量分 + 问题数 |
| `GET /api/v1/catalog/{id}/usage-summary` | GET | catalog:view | 调用量 + 申请量 |
| `GET /api/v1/catalog/{id}/detail` | GET | catalog:view | 聚合 meta+lineage+quality+usage |

## 6. 数据结构

### 6.1 t_catalog_lineage（V018）

```sql
CREATE TABLE t_catalog_lineage (
    id BIGINT PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    node_type VARCHAR(32) NOT NULL,      -- INGEST_TASK/PARTNER/DATA_SERVICE
    node_id BIGINT NOT NULL,
    node_name VARCHAR(128),
    direction VARCHAR(16) NOT NULL,      -- UPSTREAM/DOWNSTREAM
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_lineage_catalog ON t_catalog_lineage(catalog_id);
```

### 6.2 t_catalog_quality_summary（V018）

```sql
CREATE TABLE t_catalog_quality_summary (
    id BIGINT PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    issue_count INT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(catalog_id)   -- 每目录一条最近摘要
);
```

### 6.3 使用统计（不新建表，聚合现有）
- 调用量：t_service_invoke_log 按 service_code 聚合 COUNT，经血缘 catalog→DATA_SERVICE 映射。
- 申请量：t_catalog_application 按 catalog_id 聚合 COUNT。

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 目录无血缘 | 返回空列表，不报错 |
| 目录无质量摘要 | 返回默认（score=0/issueCount=0）或空 |
| 目录无使用 | 返回 0 |
| 服务未绑定 catalog | 使用统计该服务不计入 |
| 血缘重复写入 | upsert（catalog_id+node_type+node_id+direction 唯一） |
| 质量摘要并发更新 | upsert by catalog_id |

## 8. 测试策略

| 测试 | 覆盖 |
|---|---|
| 血缘写入/查询 | 注册服务绑定 catalog → lineage 返回下游；接入任务 → 上游 |
| 质量摘要 | 写入质量分 → quality-summary 返回最近分 + 问题数 |
| 使用统计 | 调用日志 + 申请记录 → usage-summary 返回调用量 + 申请量 |
| 聚合端点 | /detail 返回 meta+lineage+quality+usage |
| 持久化 | JDBC 重启可查 |
| MockMvc | lineage/quality-summary/usage-summary/detail 200 + 权限 401/403 |
| 三库迁移 | MigrationDialectCompatibilityTest 纳入 V018 |

## 9. Codex 实现边界

1. 血缘用显式 t_catalog_lineage 表，不改 t_data_service/t_ingest_task 结构（最小侵入）。
2. 质量摘要用独立 t_catalog_quality_summary 表，不强行关联 t_quality_check_result（避免跨模块耦合）。
3. 使用统计聚合现有 t_service_invoke_log（经血缘映射）+ t_catalog_application，不新建表。
4. V018/U018 MySQL + 达梦双库，避开 ` LIMIT `/` TEXT`（DM）方言守护。
5. Repository 用 IdGenerator + upsert，与 P0-03/P1-01 一致。
6. catalog↔service 关联通过血缘表 DATA_SERVICE 节点记录，服务注册时可选传 catalogId 建血缘。
7. 不做血缘图谱可视化、质量趋势、使用统计趋势（留后续）。
8. 不改密钥/生产配置；不动无关模块。
9. 必须补测试并全绿。
10. 输出血缘设计 + 质量摘要 + 使用统计 + 证据。

## 10. 验收标准

- [ ] t_catalog_lineage + t_catalog_quality_summary（V018/U018 双库）。
- [ ] 血缘写入/查询（上下游）。
- [ ] 质量摘要（最近分 + 问题数）。
- [ ] 使用统计（调用量 + 申请量）。
- [ ] /lineage、/quality-summary、/usage-summary、/detail 端点。
- [ ] 持久化重启可查。
- [ ] 三库迁移测试纳入 V018。
- [ ] mvn test 全绿。
- [ ] 输出血缘设计 + 质量摘要 + 使用统计证据。

## 11. 风险与回滚

| 风险 | 控制 |
|---|---|
| catalog↔service 关联改动 DataServiceManager | 用血缘表记录，不改服务表；服务注册可选传 catalogId |
| 使用统计经血缘映射复杂 | 调用量按 catalog 的 DATA_SERVICE 血缘节点 service_code 聚合；无血缘则 0 |
| 质量摘要跨模块（quality 写入 catalog） | 用独立表，CatalogService 提供 upsert 接口，quality 模块调用或目录自算 |
| upsert 三库兼容 | 先 UPDATE affected==0 再 INSERT（P0-03 模式） |
| **回滚** | V018 有 U018；血缘/质量摘要是新增，移除不影响既有目录功能 |

## 12. 借鉴说明

借鉴 **DataHub** 的元数据优先思想：目录作为资产/字段/血缘/质量/使用/权限/申请审批综合入口。血缘模型（raw_data→data_asset→catalog→service）用显式 t_catalog_lineage 表实现，质量摘要/使用统计作为目录详情聚合维度。详见 `docs/github-reference-functional-design.md` §6。
