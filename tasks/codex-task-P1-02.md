# Codex 执行任务 - P1-02：目录治理

> 阶段：P1（验收增强）
> 任务编号：P1-02
> 分支：`ai/p1-catalog-lineage`（从 master 切出，master 已含 P1-01）
> 依据：`tasks/claude-plan-P1-02.md`（第一性原理计划，权威）、`docs/github-reference-functional-design.md` §6（DataHub）、`docs/development-process-workflow.md` §3.2 P1-02
> 前置：P0-07（目录申请）已合入 master
> 日期：2026-06-29

---

## 1. 背景与目标

P0-07 已闭环目录申请审批，但目录详情仅静态元信息，无血缘/质量摘要/使用统计。验收要求"目录详情可追溯"。t_data_service 无 catalog_id 关联，t_service_invoke_log/t_quality_check_result 无 catalog 直接关联。

借鉴 **DataHub** 元数据优先思想，用显式血缘表 + 独立质量摘要表 + 聚合使用统计，让目录详情成为资产/血缘/质量/使用的综合入口。

**最小可行结果**：
1. 血缘：目录资产上游（接入任务/合作方）+ 下游（数据服务）可查。
2. 质量摘要：最近质量分 + 问题数可查。
3. 使用统计：调用量 + 申请量可查。
4. 目录详情聚合端点（meta+lineage+quality+usage）。
5. 持久化重启可查。

## 2. 范围

### 本次实现

- **F-1 血缘表 + 仓储**：新增 `t_catalog_lineage(catalog_id, node_type, node_id, node_name, direction)` + JdbcCatalogLineageRepository + InMemoryCatalogLineageRepository。
- **F-2 血缘写入**：catalog 发布/服务注册时建立血缘（上游 INGEST_TASK/PARTNER，下游 DATA_SERVICE）。服务注册可选传 catalogId 建下游血缘。
- **F-3 质量摘要表 + 仓储**：新增 `t_catalog_quality_summary(catalog_id, score, issue_count, updated_at)` UNIQUE(catalog_id) + 仓储 upsert。
- **F-4 使用统计聚合**：调用量 = t_service_invoke_log 经 catalog→DATA_SERVICE 血缘 service_code 聚合 COUNT；申请量 = t_catalog_application 按 catalog_id 聚合 COUNT。
- **F-5 目录详情端点**：
  - `GET /api/v1/catalog/{id}/lineage`（catalog:view）
  - `GET /api/v1/catalog/{id}/quality-summary`（catalog:view）
  - `GET /api/v1/catalog/{id}/usage-summary`（catalog:view）
  - `GET /api/v1/catalog/{id}/detail`（catalog:view）聚合 meta+lineage+quality+usage
- **F-6 catalog↔service 关联**：DataServiceManager.register 或 CatalogService 提供绑定接口，血缘表记录 DATA_SERVICE 下游节点（不改 t_data_service 结构）。

### 不做

- 不做血缘图谱可视化（前端留后续）。
- 不做质量趋势历史（只取最近摘要）。
- 不做使用统计多维度趋势（只调用量+申请量）。
- 不改 t_data_service/t_ingest_task/t_quality_check_result 既有表结构（血缘/质量摘要用新表，最小侵入）。
- 不强行跨模块耦合（质量摘要独立表，CatalogService 提供 upsert）。
- 前端展示可选（后端端点先就绪，前端留后续）。

## 3. 必读输入

- `AGENTS.md`、`tasks/claude-plan-P1-02.md`（权威计划）
- `docs/github-reference-functional-design.md` §6（DataHub 血缘/质量摘要）
- `platform-pipeline/src/main/java/.../catalog/DataCatalogItem.java`、`CatalogService.java`、`CatalogController.java`、`CatalogApplicationRepository.java`
- `platform-pipeline/src/main/java/.../service/DataServiceManager.java`、`DataServiceDefinition.java`
- `platform-pipeline/src/main/java/.../ingest/IngestTask.java`
- `db/migration/V006__data_catalog.sql`、`V015__catalog_application.sql`、`V005__data_service.sql`、`V013__service_invoke_log_fact_source.sql`
- `platform-common/src/test/java/.../db/MigrationDialectCompatibilityTest.java`
- `platform-common/src/main/java/.../db/IdGenerator.java`

## 4. 需要修改的模块

| 文件 | 改动 |
|---|---|
| 新增 `CatalogLineage.java` | 血缘模型 record |
| 新增 `CatalogQualitySummary.java` | 质量摘要 record |
| 新增 `CatalogUsageSummary.java` | 使用统计 record |
| 新增 `CatalogLineageRepository.java` + Jdbc/InMemory 实现 | 血缘仓储 |
| 新增 `CatalogQualitySummaryRepository.java` + Jdbc/InMemory 实现 | 质量摘要仓储 |
| 新增 `CatalogGovernanceService.java` | 聚合血缘/质量/使用查询 |
| `CatalogService.java` | 发布时写血缘；upsertQualitySummary 接口 |
| `CatalogController.java` | lineage/quality-summary/usage-summary/detail 端点 |
| `PipelineApplication.java` | 新仓储 Bean 装配 |
| `db/migration/V018__catalog_governance.sql` + U018 | t_catalog_lineage + t_catalog_quality_summary |
| `db/migration-dm/V018__catalog_governance.sql` + U018 | 达梦版 |
| `MigrationDialectCompatibilityTest.java` | 纳入 V018 + 两表 CRUD |
| 新增 `CatalogLineageRepositoryJdbcTest.java`、`CatalogGovernanceServiceTest.java` | 持久化 + 聚合测试 |
| `PipelineModuleMockMvcTest.java` | lineage/quality-summary/usage-summary/detail MockMvc |

## 5. 数据库/API 影响

### 5.1 数据库
- 新增 `t_catalog_lineage(id, catalog_id, node_type, node_id, node_name, direction, created_at)` + idx_lineage_catalog。
- 新增 `t_catalog_quality_summary(id, catalog_id, score, issue_count, updated_at)` UNIQUE(catalog_id)。
- V018/U018 MySQL + 达梦双库，避开 ` LIMIT `/` TEXT`（DM）方言守护。

### 5.2 API
- `GET /api/v1/catalog/{id}/lineage` → List<CatalogLineage>
- `GET /api/v1/catalog/{id}/quality-summary` → CatalogQualitySummary（无则默认 score=0/issueCount=0）
- `GET /api/v1/catalog/{id}/usage-summary` → CatalogUsageSummary（调用量+申请量）
- `GET /api/v1/catalog/{id}/detail` → 聚合 meta+lineage+quality+usage

## 6. 实现细节约束

### 6.1 血缘模型
```java
public record CatalogLineage(long catalogId, String nodeType, long nodeId, String nodeName, String direction) {}
// nodeType: INGEST_TASK / PARTNER / DATA_SERVICE
// direction: UPSTREAM / DOWNSTREAM
```
- 上游：INGEST_TASK（接入任务）、PARTNER（合作方）。
- 下游：DATA_SERVICE（数据服务）。
- 血缘写入：catalog 发布或服务注册绑定时；catalog↔接入任务可通过 partnerId 关联或显式绑定。

### 6.2 catalog↔service 关联（不改服务表）
- DataServiceManager.register(serviceCode, name, routeKey) 保持不变。
- 新增 `CatalogService.bindService(catalogId, serviceCode, serviceName)` 或 `linkDataService`，写 t_catalog_lineage DATA_SERVICE DOWNSTREAM 节点。
- 使用统计时按血缘 DATA_SERVICE 节点的 node_name(serviceCode) 聚合 t_service_invoke_log。

### 6.3 质量摘要
```java
public record CatalogQualitySummary(long catalogId, double score, int issueCount, Instant updatedAt) {}
```
- t_catalog_quality_summary UNIQUE(catalog_id)，upsert by catalog_id。
- CatalogService 提供 `upsertQualitySummary(catalogId, score, issueCount)` 供质量模块或目录自算调用。
- 本任务不强制接入 quality 模块（避免跨模块耦合），提供 upsert 接口 + 查询；质量模块写入留后续或测试模拟。

### 6.4 使用统计（聚合现有，不建表）
```java
public record CatalogUsageSummary(long catalogId, long invokeCount, long applicationCount, Instant updatedAt) {}
```
- invokeCount：SELECT COUNT(*) FROM t_service_invoke_log WHERE service_code IN (该 catalog 的 DATA_SERVICE 血缘节点 service_code)。
- applicationCount：SELECT COUNT(*) FROM t_catalog_application WHERE catalog_id = ?。
- 无血缘/无数据则 0。

### 6.5 detail 聚合
- 一次性返回 DataCatalogItem(meta) + List<CatalogLineage> + CatalogQualitySummary + CatalogUsageSummary。
- 各部分缺失用默认值（空列表/0），不报错。

### 6.6 方言守护
- V018 SQL 不含 ` LIMIT `（带空格）、DM 不含 ` TEXT`/` TINYINT `。
- upsert 用先 UPDATE affected==0 再 INSERT（P0-03 模式）。
- 聚合 COUNT 用 SQL，避免全表内存。

## 7. 必须补充的测试

- **血缘持久化**：JdbcCatalogLineageRepositoryTest 写入上下游 → 查询返回；重启可查；upsert 去重。
- **质量摘要**：upsert → 查询最近；UNIQUE 约束；重启可查。
- **使用统计聚合**：调用日志（经血缘 service_code）+ 申请记录 → invokeCount + applicationCount 正确；无数据 0。
- **聚合端点**：CatalogGovernanceServiceTest detail 返回 meta+lineage+quality+usage。
- **MockMvc**：/lineage、/quality-summary、/usage-summary、/detail 200 + 权限 401/403。
- **三库迁移**：MigrationDialectCompatibilityTest 纳入 V018，t_catalog_lineage + t_catalog_quality_summary CRUD 通过。

## 8. 验收命令

```bash
mvn -pl platform-common install -DskipTests   # 若 common 改动
mvn test -pl platform-pipeline
mvn test                                       # 全量回归
```

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| catalog↔service 关联改动 DataServiceManager | 血缘表记录，不改服务表；CatalogService 提供 bindService |
| 使用统计经血缘映射复杂 | 按 DATA_SERVICE 血缘节点 service_code 聚合；无血缘 0 |
| 质量摘要跨模块 | 独立表 + upsert 接口，本任务不强制接 quality 模块 |
| upsert 三库兼容 | 先 UPDATE affected==0 再 INSERT |
| **回滚** | V018 有 U018；血缘/质量摘要是新增，移除不影响既有目录 |

## 10. 完成判定

- [ ] t_catalog_lineage + t_catalog_quality_summary（V018/U018 双库）。
- [ ] 血缘写入/查询（上下游）+ 持久化重启可查。
- [ ] 质量摘要 upsert/查询 + 持久化。
- [ ] 使用统计聚合（调用量 + 申请量）。
- [ ] /lineage、/quality-summary、/usage-summary、/detail 端点。
- [ ] 三库迁移测试纳入 V018。
- [ ] mvn test 全绿。
- [ ] 输出血缘设计 + 质量摘要 + 使用统计证据 + 潜在风险。

## 11. 实现边界（Codex 遵守）

1. 血缘用显式 t_catalog_lineage，不改 t_data_service/t_ingest_task 结构。
2. 质量摘要用独立 t_catalog_quality_summary，不强行关联 t_quality_check_result。
3. 使用统计聚合现有 t_service_invoke_log + t_catalog_application，不新建表。
4. V018/U018 MySQL + 达梦双库，避开方言守护（` LIMIT `、DM ` TEXT`/` TINYINT `）。
5. Repository 用 IdGenerator + upsert（先 UPDATE affected==0 再 INSERT），与 P0-03/P1-01 一致。
6. catalog↔service 关联用血缘表 DATA_SERVICE 节点，CatalogService 提供 bindService，不改 DataServiceManager 签名。
7. 质量摘要提供 upsert 接口，不强制接 quality 模块（测试模拟写入）。
8. 不做血缘可视化/质量趋势/使用趋势（留后续）。
9. 不改密钥/生产配置；不动无关模块；不跳过测试。
10. 完成后输出修改文件、测试命令、测试结果、血缘设计、质量摘要、使用统计证据、潜在风险。

## 12. 借鉴说明

借鉴 **DataHub** 元数据优先思想（目录作为资产/血缘/质量/使用综合入口）+ 显式血缘模型（raw_data→data_asset→catalog→service）。用 t_catalog_lineage 显式血缘 + t_catalog_quality_summary 独立摘要 + 聚合使用统计实现，最小侵入既有表。详见 `docs/github-reference-functional-design.md` §6。
