# Claude Code 审查结果 — P1-04 监管报表

## 1. 审查对象

- 任务：P1-04 监管报表
- 分支：`ai/p1-quality-report`（P1-04 改动叠加在 P1-03 未提交工作区上，未按建议切 `ai/p1-regulatory-report` 分支）
- 任务单：`tasks/codex-task-P1-04.md`，计划：`tasks/claude-plan-P1-04.md`
- 审查日期：2026-06-29
- 前置：P0-05（invoke log 事实源）已合入 master；P1-03 审查通过
- 改动范围：t_regulatory_report（V020/U020 双库）、RegulatoryReportRecord/Repository（JDBC+内存）、RegulatoryReportService（生成/脱敏/报送/查询/导出）、4 个监管报表端点、MockRegulatoryReportingAdapter Bean 装配、审计事件、GlobalExceptionHandler 404 映射（P1-03 附带，本次复用）
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查

## 2. Git 状态

P1-04 改动叠加在 P1-03 工作区（同分支未切），P1-04 新增/修改：

```text
 M platform-billing/.../BillingApplication.java          # 报告仓储/服务/adapter Bean 装配
 M platform-billing/.../StatsController.java             # generate/list/detail/export 端点 + parseInstant
 M platform-billing/test/.../BillingModuleMockMvcTest.java  # 监管报表 MockMvc（+TestConfig 注入内存仓储/adapter）
 M platform-billing/test/.../StatsControllerTest.java    # 构造器对齐新依赖
?? db/migration/V020__regulatory_report.sql + U019/U020  # MySQL + 达梦双库
?? platform-billing/.../report/RegulatoryReportRecord.java
?? platform-billing/.../report/RegulatoryReportRepository.java + Jdbc/InMemory
?? platform-billing/.../report/RegulatoryReportService.java
?? platform-billing/test/.../report/RegulatoryReportServiceTest.java
?? platform-billing/test/.../report/RegulatoryReportRepositoryJdbcTest.java
```

> 注：`GlobalExceptionHandler` 404 映射统一（`endsWith("404") && !startsWith("AUTH")`）属 P1-03 P2-3 修复，P1-04 的 REGULATORY-404 自动受益返 404。`MigrationDialectCompatibilityTest` 同时纳入 V019（P1-03）+ V020（P1-04）。

## 3. 测试验证

```bash
mvn -pl platform-common install -DskipTests
mvn test   # 全量回归
```

结果：**BUILD SUCCESS**，全模块全绿：
- common 32 / gateway 2 / auth 33 / partner 30 / quality 35 / pipeline 113 / **billing 46**（+7：RegulatoryReportServiceTest 3 + RegulatoryReportRepositoryJdbcTest 1 + MockMvc 监管报表 3）
- Flyway 迁移成功应用到 v020（quality_report + regulatory_report），**无回归**。

### 3.1 测试覆盖
- `RegulatoryReportServiceTest`：生成持久化+脱敏+回执（SUBMITTED）、COMPLIANCE 不脱敏+FAILED 回执、时间过滤+非法类型 400。
- `RegulatoryReportRepositoryJdbcTest`：save/updateSubmission/readBack 重启可查（新仓储实例）。
- `BillingModuleMockMvcTest`：generate/list/detail/export 200 + 脱敏断言（content 不含 CONSUMER-SECRET、含 C***T）+ 401/403 + detail 404；TestConfiguration 注入内存仓储 + mock adapter。
- `MigrationDialectCompatibilityTest`：t_regulatory_report INSERT + content（Clob/字符串）+ receipt_no 回读。

## 4. 需求符合性

| 需求项（codex-task §2） | 实现情况 | 结论 |
|---|---|---|
| F-1 报告表 + 仓储 | t_regulatory_report + Jdbc/InMemory RegulatoryReportRepository | ✅ |
| F-2 报文生成 | RegulatoryReportService.generate 聚合 invoke log + ObjectMapper 序列化 + 持久化 | ✅ |
| F-3 脱敏 | PERSONAL_INFO 类 consumerCode/apiKey/traceId 掩码；COMPLIANCE/DATA_SOURCE 不脱敏 | ⚠️ 见 P2-1（apiKey） |
| F-4 报送回执 | 调用 adapter.submit → updateSubmission（SUBMITTED/FAILED），失败不抛异常 | ✅ |
| F-5 报告查询 | list（按 reportType）+ detail（REGULATORY-404） | ✅ |
| F-6 导出 | GET /stats/reports/{id}/export 返回 content Blob + Content-Disposition | ✅ |
| F-7 端点 | generate(billing:run)/list/detail/export(stats:view) | ✅ |
| F-8 审计 | 生成/报送写 AuditEvent | ⚠️ 见 P2-2（traceId 不串联） |

## 5. 与 claude-plan 对齐

- **独立报告表**：t_regulatory_report，不重构既有 `ReportGenerator`/`/stats/reports?type=` 即时 xlsx 端点（新增 `/reports/generate` 链路），符合最小侵入。
- **报文序列化**：ObjectMapper + JavaTimeModule + 禁用 WRITE_DATES_AS_TIMESTAMPS，借鉴 P1-03 P2-1，未手写 JSON 拼接。✅
- **parseInstant 校验**：捕获 DateTimeException 抛 REGULATORY-400，借鉴 P1-03 P2-2。✅
- **脱敏**：PERSONAL_INFO 类三字段掩码，工具方法 maskMiddle/maskAll/maskHeadTail。✅（但 apiKey 范围见 P2-1）
- **报送失败回退**：adapter 抛异常或 success=false → status=FAILED + message，不向上抛，回执入库。✅
- **Q-05 待定**：mock 报送框架，真实对接/SM4 加密留后续，标注清晰。✅

## 6. 对抗式审查（CLAUDE.md §7.1）

### 6.1 攻击面枚举

| 攻击面 | 类型 |
|---|---|
| generate 端点（reportType/from/to 外部输入） | 输入校验/注入 |
| 报文序列化（ObjectMapper） | 注入/破坏 |
| 脱敏逻辑（PERSONAL_INFO 掩码） | 脱敏完整性 |
| 报送适配器失败回退 | 失败处理 |
| updateSubmission UPDATE | 持久化/并发 |
| export Blob 内容 | 内容正确性/泄露 |
| detail REGULATORY-404 HTTP 映射 | HTTP 映射 |
| 审计事件 traceId | 审计链路 |
| `/reports` 路由（type/!type 双映射） | 路由歧义 |
| content 大字段方言 | 持久化/迁移 |
| 并发生成 | 重复（快照） |
| V020 回滚 | 可逆 |

### 6.2 构造反例与追踪结果

| 反例 | 追踪结果 | 存活？ |
|---|---|---|
| **越权**：viewer 调 generate | `@RequirePermission("billing:run")`，viewer 无→403；MockMvc 验证 | ❌ 已反驳 |
| **generate 无 token** | 401；MockMvc 验证 | ❌ 已反驳 |
| **list 无 token** | 401；MockMvc 验证 | ❌ 已反驳 |
| **detail 不存在** | 抛 REGULATORY-404，`endsWith("404") && !startsWith("AUTH")`→404；MockMvc 断言 404+code | ❌ 已反驳 |
| **非法 reportType** | parseType 抛 REGULATORY-400；测试 timeRangeFiltersLogsAndInvalidTypeReturnsBusinessError 验证 | ❌ 已反驳 |
| **非法 from/to** | parseInstant 捕获 DateTimeException→REGULATORY-400 | ❌ 已反驳 |
| **JSON 注入**：reportType/字段含双引号 | ObjectMapper 序列化自动转义，无手写拼接 | ❌ 已反驳 |
| **PERSONAL_INFO 脱敏遗漏**：consumerCode/apiKey/traceId 明文 | maskMiddle/maskAll/maskHeadTail 掩码；MockMvc 断言 content 不含 CONSUMER-SECRET | ❌ 已反驳（PERSONAL_INFO 维度） |
| **apiKey 明文进 COMPLIANCE 报文** | `detail()` 对 COMPLIANCE/DATA_SOURCE 不脱敏，`log.apiKey()`（明文，DataServiceManager.writeInvokeLog 写入）聚合进 content 持久化+导出+报送 | ⚠️ 存活→P2-1 |
| **审计 traceId 串联**：GENERATE 与 SUBMIT 两次 appendAudit 各传 traceId=null | AuditEvent 构造器 `requireNonNullElseGet(traceId, UUID)` 各自生成不同 UUID，两事件 traceId 不同，无法用 trace_id 串联同一次生成 | ⚠️ 存活→P2-2 |
| **REGULATORY-500 映射**：serialize 失败抛 REGULATORY-500 | `endsWith("500")` 不在特判列表→回落 BAD_REQUEST（400），500 码语义不符 | ⚠️ P3-1 |
| **`/reports` 路由歧义**：客户端调新列表误传 `?type=COMPLIANCE` | `params="type"` 命中旧 xlsx 端点，返回 GeneratedReport(Path) 而非列表 | ⚠️ P3-2（设计略脆，非功能错误） |
| **FAILED 设 submittedAt** | updateSubmission 无条件设 submittedAt=now()，失败也记提交时间 | ⚠️ P3-3（语义瑕疵） |
| **export 内容泄露**：PERSONAL_INFO export | export 返回 detail().content()，content 已脱敏；MockMvc 断言不含 CONSUMER-SECRET | ❌ 已反驳 |
| **updateSubmission 返回 null 传播**：Jdbc `findById(id).orElse(null)` | 极端 id 不存在返回 null→generate 返回 null→Result.ok(null)；但 save 刚成功，无删除接口，实际不触发 | ❌ 已反驳（理论竞态） |
| **并发 generate 重复** | 每次 save 新 id（IdGenerator 原子），无 upsert | ❌ 已反驳 |
| **content 大字段方言**：MySQL LONGTEXT / 达梦 CLOB | 双库脚本分离；MigrationDialectCompatibilityTest 覆盖 H2 LONGTEXT + Clob 读取；达梦 CLOB 路径靠脚本守护（与既有一致） | ❌ 已反驳（达梦未实测，标注） |
| **V020 回滚**：U020 | `DROP TABLE IF EXISTS t_regulatory_report` | ❌ 已反驳 |
| **InMemory vs JDBC 一致性**：save/updateSubmission | InMemory updateSubmission 设 submittedAt=now()，JDBC 同；save 时 submittedAt=null 一致 | ❌ 已反驳 |
| **maskMiddle 单字符/空** | length<=2→charAt(0)+"***"；null/blank→原值（空） | ❌ 已反驳 |
| **list 非法 reportType**：`?reportType=BAD` | service.list 非空调 parseType→REGULATORY-400 | ❌ 已反驳 |

### 6.3 存活缺陷

**P2-1（apiKey 明文进 COMPLIANCE/DATA_SOURCE 监管报文，安全/脱敏）**
- 位置：`RegulatoryReportService.detail(ReportType, ServiceInvokeLog)`，`detail.put("apiKey", maskApiKey(type, log.apiKey()))`，而 `maskApiKey` 仅 PERSONAL_INFO 返回 `"***"`，其余类型返回原值。
- 事实链：`DataServiceManager.writeInvokeLog`（platform-pipeline）把调用方传入的明文 `apiKey` 写入 `t_service_invoke_log.api_key`（P0-05 既有行为）→ `JdbcServiceInvokeLogRepository.findAll` 回读明文 → 监管报文 `detail()` 对 COMPLIANCE/DATA_SOURCE 不脱敏 → 明文 apiKey 进入 `content` → 持久化 `t_regulatory_report.content` + 导出 Blob + 报送 adapter。
- 反例：生成 COMPLIANCE 报文 → content 含 `"apiKey":"<明文调用方凭证标识>"`，导出/报送对外暴露。
- 影响：apiKey 是调用方凭证标识（非 secret，泄露不能直接签名），但属敏感身份凭证，监管报文对外报送本不应含此字段（无业务意义），扩大暴露面。NFR-S02 要求敏感数据脱敏。
- 严重级：**P2**（apiKey 非 secret，不阻断功能；但属脱敏范围设计不足，监管报送场景建议优先修复）。
- 修复建议：监管报文 details **移除 apiKey 字段**（监管报表无此业务需求），或所有报表类型对 apiKey 脱敏。注意：任务单原文只要求 PERSONAL_INFO 脱敏，此为**任务单设计遗漏**，Codex 严格按任务单实现，非实现偏离。

**P2-2（审计事件 traceId 不串联，审计链路）**
- 位置：`RegulatoryReportService.appendAudit`，两次调用（GENERATE/SUBMIT）均传 `traceId=null`；`AuditEvent` 构造器 `Objects.requireNonNullElseGet(traceId, UUID::randomUUID)` 各自生成不同 UUID。
- 反例：一次 generate 产生两个审计事件（REGULATORY_REPORT_GENERATE + REGULATORY_REPORT_SUBMIT），traceId 不同，无法用 `audit?traceId=` 串联同一次报告生成。
- 影响：F-8 要求"含 trace_id 链路追溯"，当前只能靠 `targetType=REGULATORY_REPORT + targetId=reportId` 关联，trace_id 链路断裂。
- 严重级：**P2**（审计可查，但 trace_id 串联能力打折）。
- 修复建议：generate 入口生成单一 traceId，两次 appendAudit 共用，或 SUBMIT 事件复用 GENERATE 的 traceId。

### 6.4 反驳"建议通过"结论

尝试反驳"应通过"：
- 安全（越权/无 token/JSON 注入/PERSONAL_INFO 脱敏）反例已反驳。
- **P2-1（apiKey 明文）存活**——但 apiKey 非 secret、invoke log 已存明文（P0-05 既有）、不阻断功能正确性、无数据损坏、无鉴权突破；属脱敏范围设计不足，监管报送场景建议修复但不阻断验收框架。
- **P2-2（traceId 不串联）存活**——审计事件可查（targetId 关联），仅 trace_id 串联缺失，非阻断。
- P3-1~P3-3 存活——均为语义/设计瑕疵，非阻断。
- **未发现存活 P1 阻断项**：无数据损坏、无安全突破、无鉴权绕过、核心功能（生成/持久化/脱敏/回执/查询/导出）正确。
- 结论维持"建议通过"，P2-1/P2-2 列为返工改进项（P2-1 建议优先，涉及对外报送脱敏）。

## 7. 代码质量

### 7.1 优点
1. **最小侵入**：独立 t_regulatory_report + 新 `/reports/generate` 链路，不重构既有 `ReportGenerator`/`/stats/reports?type=` xlsx 端点。
2. **复用 P1-03 经验**：ObjectMapper 序列化（P2-1）、parseInstant 校验（P2-2）、export Blob 模式、JdbcRepository 持久化模板，一致性高。
3. **报文结构化**：summary（invokeCount/successCount/failCount）+ details，LinkedHashMap 保序，JSON 可读。
4. **报送失败回退**：adapter 抛异常或 success=false → FAILED + message 入库，不向上抛，回执不丢。
5. **持久化重启可查**：JdbcRegulatoryReportRepository + IdGenerator，RepositoryJdbcTest 验证新实例查回。
6. **脱敏工具完整**：maskMiddle/maskAll/maskHeadTail 处理 null/短串边界。
7. **测试覆盖全**：生成/脱敏/回执/持久化/查询/导出/MockMvc 401/403/404 + TestConfig 注入内存仓储与自定义 adapter 覆盖 FAILED 分支。
8. **审计落库**：GENERATE/SUBMIT 写 AuditEvent（虽 traceId 不串联，见 P2-2）。
9. **方言守护**：MySQL LONGTEXT / 达梦 CLOB 双库脚本，避开 ` LIMIT `/DM ` TEXT`/` TINYINT `。

### 7.2 其他问题
- P2-1/P2-2 见 §6.3。
- **P3-1**：REGULATORY-500 序列化失败回落 400（与 P1-03 P3 同类，GlobalExceptionHandler 无 500 码特判）。
- **P3-2**：`/reports` 靠 `params="type"`/`params="!type"` 区分旧 xlsx 端点与新列表端点，设计略脆；客户端误传 `?type=` 命中旧端点。建议新列表用独立路径（如 `/reports/list`）或旧端点废弃，但当前功能正确。
- **P3-3**：FAILED 也设 submittedAt=now()，语义略不符（失败不应有提交时间）。
- **P3-4**：content 明文存储（脱敏后），SM4 加密待 Q-05（任务单明确不做，框架阶段可接受）。
- **P3-5**：达梦 CLOB 路径未实测（靠脚本分离守护，与既有各表一致）。

## 8. 是否超出任务范围
- `StatsControllerTest` 改动：构造器对齐新依赖（regulatoryReportService），属合理伴随。
- `GlobalExceptionHandler`/`GlobalExceptionHandlerTest`/`MigrationDialectCompatibilityTest`：P1-03 附带 + V020 纳入，合理伴随。
- 无前端改动（任务单明确"前端留后续"）。
- 无大型依赖引入。

## 9. 是否过度设计
未发现过度设计。独立报告表 + 维度生成 + 脱敏 + 报送回执 + 导出为验收必要；未做真实监管对接/SM4 加密/模板配置/定时报送（符合"不做"清单）。回执字段内联到报告表（不拆 t_regulatory_receipt），最小表数。

## 10. 安全风险
- ✅ generate billing:run / list/detail/export stats:view，权限正确。
- ✅ SQL 参数化（save/updateSubmission/findById/findByType），无 SQL 注入。
- ✅ ObjectMapper 序列化，无 JSON 注入。
- ✅ PERSONAL_INFO 类 consumerCode/apiKey/traceId 脱敏，MockMvc 断言不含明文。
- ⚠️ P2-1：COMPLIANCE/DATA_SOURCE 报文 content 含明文 apiKey，对外报送扩大凭证标识暴露面。
- ⚠️ P2-2：审计 traceId 不串联（非安全，审计链路能力）。
- 无明文 secret 入库（apiKey 是凭证标识非 secret；secret 不进报文）。

## 11. 审查结论

**建议通过**（2 个 P2 改进项不阻断）

P1-04 达成全部最小可行结果：报文持久化（t_regulatory_report）、按 COMPLIANCE/DATA_SOURCE/PERSONAL_INFO 类型 + 时间范围生成、PERSONAL_INFO 脱敏、报送回执入库（SUBMITTED/FAILED）、列表/详情查询、导出 Blob、持久化重启可查、审计事件。billing 46 + 全量回归 BUILD SUCCESS 无回归。代码质量良好（最小侵入、复用 P1-03 经验、报送失败回退、测试覆盖全）。

**对抗式审查**：枚举 22 个攻击面，2 个 P2 存活（apiKey 明文进非个人信息报文、审计 traceId 不串联），3 个 P3 存活（500 映射、路由歧义、FAILED 设 submittedAt）。均为脱敏范围/审计链路/语义瑕疵，不影响核心功能正确性、无数据损坏、无安全突破、无 P1 阻断。**未发现存活 P1 阻断项**，维持"建议通过"。

**重点改进**：P2-1（监管报文移除 apiKey 或全类型脱敏）建议优先，涉及对外报送脱敏；P2-2（审计 traceId 串联）次之。

## 12. 返工任务清单

无强制返工。P2 改进（不阻断，建议优先 P2-1）：

1. [ ] **P2-1**：监管报文 details 移除 `apiKey` 字段（监管报表无此业务需求），或所有报表类型对 apiKey 脱敏（maskApiKey 不再限定 PERSONAL_INFO）。补测试：COMPLIANCE 报文 content 不含明文 apiKey。
2. [ ] **P2-2**：generate 入口生成单一 traceId，GENERATE 与 SUBMIT 两次 appendAudit 共用，使 `audit?traceId=` 可串联同一次报告生成。补测试：两事件 traceId 一致。
3. [ ] P3-1：GlobalExceptionHandler 加 500 码特判（`endsWith("500")`→INTERNAL_SERVER_ERROR），使 REGULATORY-500 返 500（沿用 P1-03 P3）。
4. [ ] P3-2：新列表端点用独立路径（如 `/reports/list`）或废弃旧 `/reports?type=` xlsx 端点，消除 params 路由歧义。
5. [ ] P3-3：FAILED 时 submittedAt 置 null（失败不应有提交时间）。
6. [ ] P3-4：content SM4 加密存储（待 Q-05 监管标准明确后实施）。
7. [ ] 前端：StatsView 监管报表演示（后端端点已就绪）。

## 13. 建议提交

P1-04 可提交（建议先修复 P2-1 再提交，或作为后续返工）。建议提交信息：

```text
feat(P1-04): regulatory report with persistence, masking, and receipt

- t_regulatory_report (V020/U020 MySQL+DM) with report_type/content/status/receipt
- RegulatoryReportService generates from invoke log, aggregates by COMPLIANCE/DATA_SOURCE/PERSONAL_INFO, persists
- PERSONAL_INFO masks consumerCode/apiKey/traceId; serialize via ObjectMapper
- submit via RegulatoryReportingAdapter, receipt persisted (SUBMITTED/FAILED), failure does not throw
- endpoints: POST /stats/reports/generate (billing:run), GET /stats/reports, /stats/reports/{id}, /stats/reports/{id}/export (stats:view)
- export returns content Blob with Content-Disposition attachment
- REGULATORY-404 maps to NOT_FOUND; generate/submit audit events
- mvn test green; regulatory reporting standard pending Q-05 (mock framework)
```

## 14. 流程提示

P1-04 改动叠加在 P1-03 未提交工作区（分支 `ai/p1-quality-report`，未切 `ai/p1-regulatory-report`）。建议：
1. 先提交 P1-03（`feat(P1-03)`）并合并 master；
2. 再提交 P1-04（`feat(P1-04)`）并合并 master；
3. 后续 P1-05 财务适配从 master 切 `ai/p1-finance-adapter` 分支，避免多任务改动叠加同一工作区。
