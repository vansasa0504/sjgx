# Claude Code 复查结果 — P1-04 监管报表（返工复查）

## 1. 审查对象

- 任务：P1-04 监管报表（返工复查）
- 分支：`ai/p1-quality-report`
- 前置审查：`reviews/claude-review-P1-04.md`（2 个 P2 存活：P2-1 apiKey 明文、P2-2 审计 traceId 不串联；3 个 P3）
- 复查日期：2026-06-29
- 返工范围：P2-1 + P2-2 全部修复 + 测试同步强化

## 2. 返工落实情况

### 2.1 P2-1 apiKey 明文进非个人信息报文 ✅
- **修复**：`RegulatoryReportService.detail(ReportType, ServiceInvokeLog)` **移除 apiKey 字段**（原 `detail.put("apiKey", maskApiKey(...))` 删除），监管报文 details 不再含 apiKey；连带删除 `maskApiKey` 方法（无残留死代码）。
- **效果**：COMPLIANCE/DATA_SOURCE/PERSONAL_INFO 三类报文 content 均不含 apiKey 字段，明文 apiKey（DataServiceManager 写入 invoke log）不再随报文持久化/导出/报送。比"全类型脱敏"更彻底——直接移除无业务意义字段。
- **测试同步**：
  - `RegulatoryReportServiceTest`：PERSONAL_INFO 与 COMPLIANCE 两处新增 `assertFalse(content.contains("apiKey"))` + `assertFalse(content.contains("api-secret"))`。
  - `BillingModuleMockMvcTest.regulatoryReportGenerateListDetailAndExportWithMasking`：generate 响应 content 与 export 响应体均断言 `not(containsString("apiKey"))` + `not(containsString("api-key-secret"))` + `not(containsString("CONSUMER-SECRET"))`。

### 2.2 P2-2 审计 traceId 不串联 ✅
- **修复**：`generate` 入口生成单一 `auditTraceId = UUID.randomUUID()`（第43行），GENERATE 与 SUBMIT 两次 `appendAudit` **共用同一 traceId**；`appendAudit` 签名增加 `traceId` 参数透传，不再传 null 触发 AuditEvent 各自生成 UUID。
- **效果**：一次报告生成产生两个审计事件 traceId 一致，`audit?traceId=` 可串联同一次生成的 GENERATE + SUBMIT 事件，审计链路贯通。
- **测试同步**：`RegulatoryReportServiceTest.generatePersistsSubmittedReceiptAndMasksPersonalInfo` 新增断言 `assertEquals(generateEvents.get(0).traceId(), submitEvents.get(0).traceId())`。

## 3. 测试验证

```bash
mvn -pl platform-common install -DskipTests
mvn test   # 全量回归
```

结果：**BUILD SUCCESS**，全模块测试全绿：
- common 32 / gateway 2 / auth 33 / partner 30 / quality 35 / pipeline 113 / **billing 46**
- RegulatoryReportServiceTest 3 + RegulatoryReportRepositoryJdbcTest 1 + BillingModuleMockMvcTest 23（含监管报表 3）全绿
- **无回归**。返工未破坏既有断言（COMPLIANCE 仍含 CONSUMER-1、summary 计数仍正确）。

## 4. 对抗式复查（验证返工不引入新缺陷）

| 反例 | 追踪结果 | 存活？ |
|---|---|---|
| 移除 apiKey 是否破坏报文结构 | detail() 仍含 traceId/serviceCode/consumerCode/partnerCode/status，ObjectMapper 序列化结构完整；MockMvc export 断言含 reportType | ❌ 已反驳 |
| apiKey 字段移除后 PERSONAL_INFO 脱敏是否完整 | consumerCode(maskMiddle)/traceId(maskHeadTail) 仍脱敏；apiKey 既已移除无残留；测试断言不含 CONSUMER-SECRET + C***T 掩码存在 | ❌ 已反驳 |
| maskApiKey 残留死代码 | 已删除，无残留方法 | ❌ 已反驳 |
| traceId 串联是否两事件一致 | generate 入口单 UUID，两次 appendAudit 共用；测试断言相等 | ❌ 已反驳 |
| appendAudit traceId 参数是否被正确消费 | AuditEvent 构造器 `requireNonNullElseGet(traceId, UUID)`，传入非 null 时用传入值，不再随机 | ❌ 已反驳 |
| traceId 串联是否影响其他模块审计 | 仅 RegulatoryReportService 改 appendAudit 签名，其他模块审计独立，全量回归通过 | ❌ 已反驳 |
| 移除 apiKey 是否影响 P0-05 事实源 | 仅报文构建移除字段，invoke log 表/apiKey 列不变，事实源完整 | ❌ 已反驳 |
| 返工是否引入新 P3 | REGULATORY-500 映射/路由歧义/FAILED submittedAt 为既有 P3，返工未触及，未新增 | ❌ 已反驳 |

**未发现返工引入的新存活缺陷**。

## 5. 既有 P3 状态（本次返工未处理，不阻断）

- P3-1：REGULATORY-500 序列化失败回落 400（GlobalExceptionHandler 无 500 码特判）—— 既有，罕见，留后续。
- P3-2：`/reports` 靠 `params="type"`/`"!type"` 路由歧义 —— 既有，功能正确，留后续。
- P3-3：FAILED 也设 submittedAt=now() —— 既有，语义瑕疵，留后续。
- P3-4：content 明文存储，SM4 加密待 Q-05 —— 框架阶段可接受。
- P3-5：达梦 CLOB 路径未实测 —— 靠脚本分离守护。

## 6. 复查结论

**建议通过**

P1-04 返工全面落实 2 个 P2 缺陷：
- **P2-1**：监管报文移除 apiKey 字段（比"全类型脱敏"更彻底，直接消除无业务意义的凭证标识暴露），三类型报文均不含 apiKey，明文 apiKey 不再随导出/报送对外暴露。
- **P2-2**：审计 traceId 串联（generate 入口单一 UUID，GENERATE + SUBMIT 共用），`audit?traceId=` 可追溯同一次报告生成全事件。

全量回归 BUILD SUCCESS 无回归，测试断言同步强化（apiKey 不存在 + traceId 相等）。对抗式复查未发现返工引入新缺陷，既有 P3 均为罕见/语义瑕疵，不阻断。

## 7. 后续可选（不阻断）

- P3-1：GlobalExceptionHandler 加 `endsWith("500")`→INTERNAL_SERVER_ERROR 特判（与 P1-03 P3 同类，可一并处理）。
- P3-2：新列表端点用独立路径或废弃旧 `/reports?type=` xlsx 端点。
- P3-3：FAILED 时 submittedAt 置 null。
- P3-4：content SM4 加密存储（待 Q-05）。
- 前端：StatsView 监管报表演示。

## 8. 建议提交

返工已就绪，可提交。建议提交信息：

```text
feat(P1-04): regulatory report with persistence, masking, and receipt

- t_regulatory_report (V020/U020 MySQL+DM) with report_type/content/status/receipt
- RegulatoryReportService generates from invoke log, aggregates by COMPLIANCE/DATA_SOURCE/PERSONAL_INFO, persists
- PERSONAL_INFO masks consumerCode/traceId; apiKey field omitted from all reports (no credential exposure)
- serialize via ObjectMapper; submit via adapter, receipt persisted (SUBMITTED/FAILED), failure does not throw
- endpoints: POST /stats/reports/generate (billing:run), GET /stats/reports, /stats/reports/{id}, /stats/reports/{id}/export (stats:view)
- single audit traceId links GENERATE+SUBMIT events; REGULATORY-404 maps to NOT_FOUND
- mvn test green; regulatory reporting standard pending Q-05 (mock framework)
```

## 9. 流程提示

P1-03 + P1-04 改动仍叠加在 `ai/p1-quality-report` 未提交工作区。建议：
1. 提交 P1-03（`feat(P1-03)`）合并 master；
2. 提交 P1-04（`feat(P1-04)`）合并 master；
3. P1-05 财务适配从 master 切 `ai/p1-finance-adapter`，避免多任务叠加。
