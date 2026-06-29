# 第一性原理开发计划 — P1-03 质量报告

> 任务编号：P1-03
> 分支：`ai/p1-quality-report`（从 master 切出，master 已含 P1-02）
> 依据：`tasks/claude-plan-P1.md`、`docs/github-reference-functional-design.md` §11（Great Expectations）、`docs/development-process-workflow.md` §3.2 P1-03
> 前置：P0-03（质量落库）已合入 master
> 日期：2026-06-29

---

## 1. 需求来源

- **技术要求**：数据质量管理（FR-901~906）要求按合作方/类型/时间生成质量报告。验收要求"按合作方/资产/服务生成报告"。
- **验收口径**：P1-03 通过标准"报告持久化和导出"。
- **参考**：Great Expectations 的 Expectation/Validation Result/Data Docs 思路——规则可复用、校验可追溯、结果可报告。
- **现状缺口**：
  - `/reports` 端点即时从内存 `checkExecutor.history()` 计算，**无持久化**（重启丢失）。
  - 报告仅按 targetType(=batchNo) 维度，**无合作方/资产/服务维度**。
  - **无导出**能力。
  - t_quality_check_result 表无 target_type 列（targetType 复用 batchNo）。

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？
质量报告可持久化、可按维度（合作方/资产/服务）生成、可导出，让验收方确认质量结果可追溯、可交付。

### 2.2 最小可行结果
1. 质量报告持久化（t_quality_report）。
2. 按维度（PARTNER/ASSET/SERVICE）+ 维度值生成报告。
3. 报告导出（Blob 下载）。
4. 报告列表/详情查询。

### 2.3 系统必须接收哪些输入？
- 报告维度（PARTNER/ASSET/SERVICE）+ 维度值（合作方 id/资产 id/服务 code）。
- 时间范围（可选）。
- 质量检查结果（check history 或 t_quality_check_result）。

### 2.4 系统必须产生哪些输出？
- QualityReportRecord：维度/维度值/检查数/通过数/失败数/失败率/评分/生成时间，持久化。
- 导出文件（JSON/CSV Blob）。

### 2.5 不可省略的处理过程？
1. 按维度+维度值过滤 check history。
2. 聚合统计（检查数/通过/失败/失败率/评分）。
3. 持久化报告记录。
4. 导出序列化为 Blob。

### 2.6 哪些是核心能力？
- 报告持久化（t_quality_report）。
- 维度化生成（PARTNER/ASSET/SERVICE）。
- 导出。

### 2.7 哪些是增强能力？
- 报告模板配置（本任务固定维度）。
- 报告定时生成（留后续）。
- Data Docs 式可视化（留后续）。

### 2.8 最小改动路径？
- **新增 t_quality_report 表**（dimension/dimension_value/check_count/pass_count/fail_count/fail_rate/score/generated_at）。
- **新增 QualityReportRepository**（JDBC+内存）+ QualityReportService（生成/查询/导出）。
- **维度映射**：check history 的 targetType(=batchNo) 解析维度，或报告生成时显式传维度+维度值过滤 check history。
- **导出**：QualityReport 序列化 JSON/CSV，Blob 下载（参考 P0-09 StatsView 导出模式）。
- **端点**：POST /reports/generate（生成+持久化）、GET /reports（列表）、GET /reports/{id}（详情）、GET /reports/{id}/export（导出）。

### 2.9 如何测试？
- 生成：写 check history → POST /reports/generate{dimension,value} → 持久化 + 返回统计。
- 维度过滤：不同维度值生成不同报告。
- 持久化：重启可查。
- 导出：GET /reports/{id}/export 返回 Blob 内容。
- MockMvc：生成/列表/详情/导出 200 + 权限 401/403。

### 2.10 如何验收？
报告持久化 + 按合作方/资产/服务生成 + 导出，重启可查。

### 2.11 如何避免过度设计？
- 不做报告模板配置（固定维度）。
- 不做定时生成（手动触发）。
- 不做 Data Docs 可视化（前端留后续）。
- 不改 t_quality_check_result 既有结构（报告独立表聚合）。
- 导出用 Blob（不接外部报表系统）。

## 3. 功能拆解

| 编号 | 功能 | 说明 |
|---|---|---|
| F-1 | 报告表 + 仓储 | t_quality_report + JdbcQualityReportRepository + 内存实现 |
| F-2 | 报告生成 | QualityReportService.generate(dimension, value, from, to) 聚合 check history + 持久化 |
| F-3 | 维度化 | PARTNER/ASSET/SERVICE 维度 + 维度值过滤 |
| F-4 | 报告查询 | 列表（按维度/时间）+ 详情 |
| F-5 | 导出 | GET /reports/{id}/export Blob（JSON/CSV） |
| F-6 | 端点 | generate/list/detail/export，权限 quality:view（生成复用 quality:run 或 quality:view） |

## 4. 影响模块

| 模块 | 改动 |
|---|---|
| platform-quality.report | 新增 QualityReportRecord/QualityReportRepository/JdbcQualityReportRepository/InMemoryQualityReportRepository/QualityReportService |
| platform-quality.QualityController | 新增 generate/list/detail/export 端点 |
| platform-quality.QualityApplication | 新仓储 Bean 装配 |
| db/migration + db/migration-dm | V019 t_quality_report + U019 |
| platform-common.db | MigrationDialectCompatibilityTest 纳入 V019 |
| platform-ui | QualityView 报告生成/导出入口（可选，后端先就绪） |

## 5. 接口设计

### 5.1 模型

```java
public record QualityReportRecord(
    long id,
    String dimension,         // PARTNER/ASSET/SERVICE
    String dimensionValue,    // 合作方id/资产id/服务code
    int checkCount,
    int passCount,
    int failCount,
    double failRate,
    double score,
    Instant generatedAt) {}
```

### 5.2 API

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `POST /api/v1/quality/reports/generate` | POST | quality:run | 生成+持久化（body: dimension/value/from/to） |
| `GET /api/v1/quality/reports` | GET | quality:view | 列表（按 dimension/时间过滤） |
| `GET /api/v1/quality/reports/{id}` | GET | quality:view | 详情 |
| `GET /api/v1/quality/reports/{id}/export` | GET | quality:view | 导出 Blob |

## 6. 数据结构

### 6.1 t_quality_report（V019）

```sql
CREATE TABLE t_quality_report (
    id BIGINT PRIMARY KEY,
    dimension VARCHAR(32) NOT NULL,       -- PARTNER/ASSET/SERVICE
    dimension_value VARCHAR(128) NOT NULL,
    check_count INT NOT NULL,
    pass_count INT NOT NULL,
    fail_count INT NOT NULL,
    fail_rate DECIMAL(5,4) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    generated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_quality_report_dimension ON t_quality_report(dimension, dimension_value);
```

### 6.2 维度过滤
- check history 的 targetType(=batchNo) 当前是 batch 编码。报告生成时按 dimension+dimensionValue 过滤：
  - PARTNER：batchNo 含合作方编码，或显式传 partner 维度值匹配。
  - ASSET/SERVICE：同理。
- 最小方案：报告生成接受 dimension+dimensionValue，过滤 check history 中 targetType(=batchNo) equals 或 contains dimensionValue；无匹配则统计为 0 但仍持久化（记录生成动作）。

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 无 check history | 生成 0 统计报告，仍持久化 |
| 维度值无匹配 | 统计 0，持久化 |
| 报告不存在 | 404 |
| 导出失败 | 500 + 错误信息 |
| 并发生成 | 各自持久化（每次生成一条记录，不去重） |

## 8. 测试策略

| 测试 | 覆盖 |
|---|---|
| 生成持久化 | check history → generate → 返回统计 + 持久化 |
| 维度过滤 | 不同 dimensionValue 生成不同统计 |
| 重启可查 | JDBC 生成 → 新仓储查回 |
| 导出 | export 返回 Blob 内容（JSON/CSV） |
| 列表/详情 | 按 dimension 过滤；详情 404 |
| MockMvc | generate/list/detail/export 200 + 权限 401/403 |
| 三库迁移 | MigrationDialectCompatibilityTest 纳入 V019 |

## 9. Codex 实现边界

1. 报告用独立 t_quality_report 表，不改 t_quality_check_result 既有结构。
2. V019/U019 MySQL + 达梦双库，避开 ` LIMIT `/` TEXT`（DM）方言守护。
3. Repository 用 IdGenerator + INSERT（报告每次生成一条，不去重）。
4. 维度过滤基于 check history targetType(=batchNo)，不强行改 QualityCheckResult 结构。
5. 导出用 Blob（JSON/CSV），参考 P0-09 StatsView 导出模式，不接外部报表系统。
6. 不做报告模板/定时生成/Data Docs 可视化。
7. 不改密钥/生产配置；不动无关模块。
8. 必须补测试并全绿。
9. 输出报告设计 + 维度 + 导出证据。

## 10. 验收标准

- [ ] t_quality_report（V019/U019 双库）。
- [ ] 报告生成持久化（PARTNER/ASSET/SERVICE 维度）。
- [ ] 报告列表/详情查询。
- [ ] 导出 Blob。
- [ ] 持久化重启可查。
- [ ] 三库迁移测试纳入 V019。
- [ ] mvn test 全绿。
- [ ] 输出报告设计 + 维度 + 导出证据。

## 11. 风险与回滚

| 风险 | 控制 |
|---|---|
| 维度过滤与 check history targetType 语义 | targetType=batchNo，报告按 dimensionValue 匹配 batchNo；无匹配 0 统计 |
| 导出 Blob 在 jsdom/测试环境 | 后端导出，测试断言内容非真实下载 |
| 并发生成重复 | 每次生成一条记录，不去重（报告是时间快照） |
| **回滚** | V019 有 U019；报告独立表，移除不影响既有质量功能 |

## 12. 借鉴说明

借鉴 **Great Expectations** 的 Validation Result/Data Docs 思路：校验结果可追溯、可报告。用 t_quality_report 持久化报告记录 + 维度化生成 + 导出，最小侵入既有 t_quality_check_result。详见 `docs/github-reference-functional-design.md` §11。
