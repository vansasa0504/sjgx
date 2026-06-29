# Codex 执行任务 - P1-03：质量报告

> 阶段：P1（验收增强）
> 任务编号：P1-03
> 分支：`ai/p1-quality-report`（从 master 切出，master 已含 P1-02）
> 依据：`tasks/claude-plan-P1-03.md`（第一性原理计划，权威）、`docs/github-reference-functional-design.md` §11（Great Expectations）、`docs/development-process-workflow.md` §3.2 P1-03
> 前置：P0-03（质量落库）已合入 master
> 日期：2026-06-29

---

## 1. 背景与目标

现有 `/reports` 端点即时从内存 `checkExecutor.history()` 计算，无持久化（重启丢失）、无合作方/资产/服务维度、无导出。t_quality_check_result 无 target_type 列（targetType 复用 batchNo）。验收要求"按合作方/资产/服务生成报告"+ 持久化 + 导出。

借鉴 **Great Expectations** 的 Validation Result/Data Docs 思路（校验可追溯、可报告），用独立 t_quality_report 表持久化 + 维度化生成 + 导出，最小侵入既有结构。

**最小可行结果**：
1. 质量报告持久化（t_quality_report）。
2. 按维度（PARTNER/ASSET/SERVICE）+ 维度值生成报告。
3. 报告列表/详情查询。
4. 导出（Blob）。
5. 持久化重启可查。

## 2. 范围

### 本次实现

- **F-1 报告表 + 仓储**：新增 `t_quality_report(dimension, dimension_value, check_count, pass_count, fail_count, fail_rate, score, generated_at)` + JdbcQualityReportRepository + InMemoryQualityReportRepository。
- **F-2 报告生成**：QualityReportService.generate(dimension, dimensionValue, from, to) 聚合 check history（按维度值过滤 batchNo/targetType）+ 持久化，返回 QualityReportRecord。
- **F-3 维度化**：PARTNER/ASSET/SERVICE 维度，dimensionValue 为合作方 id/资产 id/服务 code；过滤 check history 中 targetType(=batchNo) equals/contains dimensionValue。
- **F-4 报告查询**：list（按 dimension/时间过滤）+ detail（by id）。
- **F-5 导出**：GET /reports/{id}/export 返回 Blob（JSON 或 CSV），含报告字段。
- **F-6 端点**：
  - `POST /api/v1/quality/reports/generate`（quality:run）body: {dimension, dimensionValue, from?, to?}
  - `GET /api/v1/quality/reports`（quality:view）query: dimension/from/to
  - `GET /api/v1/quality/reports/{id}`（quality:view）
  - `GET /api/v1/quality/reports/{id}/export`（quality:view）

### 不做

- 不做报告模板配置（固定 PARTNER/ASSET/SERVICE 维度）。
- 不做定时生成（手动触发）。
- 不做 Data Docs 可视化（前端留后续）。
- 不改 t_quality_check_result 既有结构（报告独立表聚合）。
- 不接外部报表系统（导出用 Blob）。
- 前端展示可选（后端端点先就绪）。

## 3. 必读输入

- `AGENTS.md`、`tasks/claude-plan-P1-03.md`（权威计划）
- `docs/github-reference-functional-design.md` §11（Great Expectations）
- `platform-quality/src/main/java/.../report/QualityReport.java`（现有即时报告）
- `platform-quality/src/main/java/.../executor/QualityCheckExecutor.java`、`QualityCheckResult.java`（check history）
- `platform-quality/src/main/java/.../QualityController.java`、`QualityApplication.java`
- `db/migration/V007__quality_storage.sql`（t_quality_check_result 现状）
- `platform-common/src/test/java/.../db/MigrationDialectCompatibilityTest.java`
- `platform-common/src/main/java/.../db/IdGenerator.java`
- P0-09 StatsView Blob 导出模式（参考）

## 4. 需要修改的模块

| 文件 | 改动 |
|---|---|
| 新增 `QualityReportRecord.java` | 报告模型 record |
| 新增 `QualityReportRepository.java` + Jdbc/InMemory 实现 | 报告仓储 |
| 新增 `QualityReportService.java` | 生成/查询/导出 |
| `QualityController.java` | generate/list/detail/export 端点 |
| `QualityApplication.java` | 新仓储 Bean 装配 |
| `db/migration/V019__quality_report.sql` + U019 | t_quality_report |
| `db/migration-dm/V019__quality_report.sql` + U019 | 达梦版 |
| `MigrationDialectCompatibilityTest.java` | 纳入 V019 + t_quality_report CRUD |
| 新增 `QualityReportServiceTest.java`、`QualityReportRepositoryJdbcTest.java` | 生成/持久化/导出测试 |
| `QualityModuleMockMvcTest.java` | generate/list/detail/export MockMvc |

## 5. 数据库/API 影响

### 5.1 数据库
- 新增 `t_quality_report(id, dimension, dimension_value, check_count, pass_count, fail_count, fail_rate, score, generated_at)` + idx_quality_report_dimension。
- V019/U019 MySQL + 达梦双库，避开 ` LIMIT `/` TEXT`（DM）方言守护。

### 5.2 API
- `POST /api/v1/quality/reports/generate`（quality:run）→ QualityReportRecord
- `GET /api/v1/quality/reports`（quality:view）→ List<QualityReportRecord>
- `GET /api/v1/quality/reports/{id}`（quality:view）→ QualityReportRecord
- `GET /api/v1/quality/reports/{id}/export`（quality:view）→ Blob（JSON/CSV）

## 6. 实现细节约束

### 6.1 报告模型
```java
public record QualityReportRecord(
    long id, String dimension, String dimensionValue,
    int checkCount, int passCount, int failCount,
    double failRate, double score, Instant generatedAt) {}
```
- dimension: PARTNER/ASSET/SERVICE
- score: 由 failRate 派生（如 100 - failRate*100，或六维加权，本任务用简单派生 100*(1-failRate)）

### 6.2 生成逻辑
- QualityReportService 注入 QualityCheckExecutor（取 history）或 QualityCheckResultRepository。
- generate(dimension, dimensionValue, from, to)：
  1. 过滤 history：targetType(=batchNo) equals 或 contains dimensionValue；可选时间范围。
  2. 聚合：checkCount=匹配数，passCount=passed=true 数，failCount=失败数，failRate=平均失败率。
  3. score = 100*(1-failRate)。
  4. 持久化（INSERT，每次生成一条）。
  5. 返回 QualityReportRecord。
- 无匹配则 0 统计，仍持久化（记录生成动作）。

### 6.3 导出
- export(id)：查报告 → 序列化 JSON（或 CSV）→ Blob 响应。
- 后端用 `ResponseEntity<byte[]>` 或 `Resource` 返回，Content-Disposition attachment。
- 测试断言响应内容含报告字段，非真实文件下载。

### 6.4 方言守护
- V019 SQL 不含 ` LIMIT `（带空格）、DM 不含 ` TEXT`/` TINYINT `。
- 报告每次生成 INSERT 一条（不去重），无 upsert 竞态。

## 7. 必须补充的测试

- **生成持久化**：check history 写入 → generate → 返回统计 + 持久化；JDBC 重启可查。
- **维度过滤**：不同 dimensionValue 生成不同统计；无匹配 0 统计仍持久化。
- **导出**：export 返回内容含报告字段（dimension/score/failRate）。
- **列表/详情**：按 dimension 过滤；详情不存在 404。
- **MockMvc**：generate/list/detail/export 200 + 权限 401/403。
- **三库迁移**：MigrationDialectCompatibilityTest 纳入 V019，t_quality_report CRUD 通过。

## 8. 验收命令

```bash
mvn -pl platform-common install -DskipTests   # 若 common 改动
mvn test -pl platform-quality
mvn test                                       # 全量回归
```

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| 维度过滤与 targetType(batchNo) 语义 | 按 dimensionValue equals/contains batchNo；无匹配 0 统计 |
| 导出 Blob 测试 | 后端返回内容，断言字段非真实下载 |
| 并发生成重复 | 每次生成一条记录（时间快照），不去重 |
| **回滚** | V019 有 U019；报告独立表，移除不影响既有质量功能 |

## 10. 完成判定

- [ ] t_quality_report（V019/U019 双库）。
- [ ] 报告生成持久化（PARTNER/ASSET/SERVICE 维度）。
- [ ] 报告列表/详情查询 + 持久化重启可查。
- [ ] 导出 Blob。
- [ ] 三库迁移测试纳入 V019。
- [ ] mvn test 全绿。
- [ ] 输出报告设计 + 维度 + 导出证据 + 潜在风险。

## 11. 实现边界（Codex 遵守）

1. 报告用独立 t_quality_report，不改 t_quality_check_result 既有结构。
2. V019/U019 MySQL + 达梦双库，避开方言守护（` LIMIT `、DM ` TEXT`/` TINYINT `）。
3. Repository 用 IdGenerator + INSERT（每次生成一条，不去重）。
4. 维度过滤基于 check history targetType(=batchNo)，不改 QualityCheckResult 结构。
5. 导出用 Blob（JSON/CSV），后端返回内容，不接外部报表系统。
6. 不做报告模板/定时生成/Data Docs 可视化。
7. 不改密钥/生产配置；不动无关模块；不跳过测试。
8. 完成后输出修改文件、测试命令、测试结果、报告设计、维度、导出证据、潜在风险。

## 12. 借鉴说明

借鉴 **Great Expectations** 的 Validation Result/Data Docs 思路（校验可追溯、可报告）。用 t_quality_report 持久化 + 维度化生成 + 导出，最小侵入既有 t_quality_check_result。详见 `docs/github-reference-functional-design.md` §11。
