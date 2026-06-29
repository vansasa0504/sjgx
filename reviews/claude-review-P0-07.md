# Claude Code 审查结果 — P0-07 目录申请

## 1. 审查对象

- 任务：P0-07 目录申请（catalog application）
- 分支：`ai/p0-catalog-application`（已正确建分支）
- 任务单：`tasks/codex-task-P0-07-catalog-application.md`
- 审查日期：2026-06-29
- 前置：P0-03 通过（Catalog 仓储已落库），P0-05 invoke log 事实源已建立
- 改动范围：新增 `t_catalog_application` 表（V015/U015 + DM 双库）、CatalogApplication 模型 + Repository（JDBC/内存）、CatalogController apply/approve/preview 真实化与授权、CatalogService preview 脱敏 + 移除生产种子、DataServiceManager 补 partner_code 授权、PipelineApplication 装配新 Bean、前端 CatalogView preview 展示 + API 类型对齐

## 2. Git 状态

改动全部未提交（工作区，分支 `ai/p0-catalog-application`）：

```text
 M platform-pipeline/.../catalog/CatalogController.java
 M platform-pipeline/.../catalog/CatalogService.java
 M platform-pipeline/.../ingest/PipelineApplication.java
 M platform-pipeline/.../service/DataServiceManager.java
 M platform-pipeline/test/.../PipelineModuleMockMvcTest.java
 M platform-pipeline/test/.../catalog/CatalogControllerTest.java
 M platform-pipeline/test/.../catalog/CatalogServiceTest.java
 M platform-pipeline/test/.../service/DataServiceManagerTest.java
 M platform-ui/src/api/catalog.ts
 M platform-ui/src/views/CatalogView.vue
?? db/migration/V015__catalog_application.sql
?? db/migration/U015__catalog_application.sql
?? db/migration-dm/V015__catalog_application.sql
?? db/migration-dm/U015__catalog_application.sql
?? platform-pipeline/.../catalog/CatalogApplication.java
?? platform-pipeline/.../catalog/CatalogApplicationRepository.java
?? platform-pipeline/.../catalog/InMemoryCatalogApplicationRepository.java
?? platform-pipeline/.../catalog/JdbcCatalogApplicationRepository.java
?? platform-pipeline/test/.../catalog/CatalogApplicationRepositoryJdbcTest.java
```

## 3. 测试验证

### 3.1 后端（platform-pipeline）

```bash
mvn -pl platform-common install -DskipTests   # 重建 common（本地 jar 过期会导致 NoSuchMethodError，非本次缺陷）
mvn -pl platform-pipeline test
```

结果：**BUILD SUCCESS，Tests run: 68, Failures: 0, Errors: 0, Skipped: 0**

- `CatalogApplicationRepositoryJdbcTest`：2 通过（持久化重启可查 + 仅 PENDING 可流转）
- `CatalogControllerTest`：2 通过（列表/申请/审批 + 状态机单向流转）
- `CatalogServiceTest`：3 通过（多维查询 + 生产构造器无种子 + preview 脱敏与 stats）
- `PipelineModuleMockMvcTest`：21 通过（含新增 apply/approve/preview 200/401/403 + 审计 + 已授权申请人可预览）
- `DataServiceManagerTest`：12 通过（含新增 approved grant 填充 partner_code）
- Flyway 15 个迁移全部成功应用，V015 catalog_application 正常建表

### 3.2 前端（platform-ui）

```bash
npm run test:unit
```

结果：**11 文件 / 35 测试全部通过**

### 3.3 测试结论

测试覆盖任务单 §6 全部要求：持久化重启可查、preview 授权 403、脱敏、预览审计、MockMvc 200/401/403、partner_code 闭环。测试全绿。

## 4. 需求符合性

| 需求项（codex-task §2） | 实现情况 | 结论 |
|---|---|---|
| `t_catalog_application` 建表 + 回滚 | V015/U015 + DM 双库，字段 id/catalog_id/applicant/reason/scope/status/approver/created_at/approved_at 齐全，含 3 个索引 | ✅ |
| Repository JDBC + 内存实现 | JdbcCatalogApplicationRepository（IdGenerator 主键）+ InMemoryCatalogApplicationRepository（ConcurrentHashMap + compute 原子流转） | ✅ |
| CatalogService 移除生产种子 | 无参构造器不再预置 DEMO；测试改为 `productionConstructorDoesNotSeedCatalog` 断言空 | ✅ |
| apply/approve 接持久化 + 状态机 | create/approve/reject，PENDING→APPROVED/REJECTED，已审批再操作抛 CATALOG_APP-409 | ✅ |
| preview 真实化（sample+stats+qualityReport） | CatalogService.preview 生成单行样例 + fieldCount/sampleCount/format/updateFrequency stats + 质量报告文案 | ✅ |
| preview 未授权 403 | canPreview：无 `catalog:approve` 且非已批准申请人 → BusinessException AUTH-403 | ✅ |
| 敏感字段脱敏 | isSensitiveField 覆盖 credential/secret/password/token/api_key/apikey/idcard/identity/person_id/phone/mobile → ***MASKED*** | ✅ |
| 预览写审计 | appendPreviewAudit 写 CATALOG_PREVIEW 事件（actor/ip/ua/sampleSize/catalogCode） | ✅ |
| 前端 preview 展示 sample table + stats + qualityReport | el-table 动态列 + el-descriptions stats + el-alert qualityReport | ✅ |
| 前端 apply/approve 对齐持久化返回 | catalog.ts 新增 CatalogApplication/CatalogPreview 类型 | ✅ |

## 5. 与 claude-plan / 最小可行结果对齐

- **最小可行结果**（申请/审批落库重启可查；preview 真实 sample + 未授权 403 + 脱敏；生产无种子）：全部达成。
- **M7-A F-05**（preview 为桩）：已闭环，preview 返回真实样例。
- **M7-D D2-02**（生产代码种子）：已移除（注：HEAD 版本无参构造器实际已无种子，本次以测试固化该不变量，无回归）。
- **M7-C R-03**（审批绑定当前 item）：申请 id 来自后端持久化返回，已对齐。
- **DataServiceManager partner_code 闭环**：审批通过后 `grantCatalogPartner(scope, applicant, partnerCode)` 写入内存映射，invoke log 的 partner_code 不再恒为 null。闭环 P0-05 遗留 TODO。

## 6. 代码质量与安全

### 6.1 优点

1. **状态机原子性**：内存仓储用 `ConcurrentHashMap.compute` 保证流转原子；JDBC 仓储 UPDATE 带 `WHERE id=? AND status='PENDING'` 守卫，防止并发重复审批污染状态。
2. **授权分层清晰**：`catalog:approve` 权限或已批准申请人二选一，principal 为空直接拒绝，符合最小权限。
3. **脱敏清单合理**：覆盖凭证/密钥/个人标识/联系方式等常见敏感字段，与 database-design 敏感标注一致。
4. **审计留痕**：preview 写审计事件，满足 NFR-S02 数据访问行为全量记录。
5. **依赖装配健壮**：PipelineApplication 按 jdbcTemplate 是否存在切换 JDBC/内存实现，与既有 CatalogService/ApiCredentialRepository 模式一致；无 DB 时不抛错。
6. **DDL 双库一致**：MySQL 与达梦 V015/U015 仅 `IF NOT EXISTS` 差异，符合国产化兼容要求；V015 为正确递增版本号（V011~V014 已被占用，任务单中"V011"为笔误，Codex 正确选用 V015）。

### 6.2 发现的问题

#### P2（建议改进，不阻断合入）

**F-1：reject 驳回能力仅在仓储层实现，未暴露 API/UI 端点**
- 现象：`CatalogApplicationRepository` 提供 `reject`，且 `CatalogApplication.REJECTED` 状态存在，但 `CatalogController` 仅暴露 `approve`，无 `reject` 端点；前端无驳回按钮。
- 影响：审批人无法通过 API/UI 驳回申请，状态机的 REJECTED 分支实际不可达（仅测试中直接调仓储验证）。
- 评估：codex-task §2"本次实现"仅列 apply/approve，§5 API 清单也未列 reject，故未超出任务范围；但 requirements.md FR-404 将"申请审批驳回"列为异常场景，后续应补。属可接受的 P0 最小范围，建议在后续任务补 reject 端点。

**F-2：JdbcCatalogApplicationRepository.transit 非原子读-改-写，竞态下返回值可能误导**
- 现象：先 `findById` 读状态判断 PENDING，再 `UPDATE ... WHERE id=? AND status='PENDING'`。UPDATE 有状态守卫不会污染数据，但未校验 `update` 影响行数；若两线程并发审批同一申请，后到线程的 UPDATE 影响 0 行，却仍返回"APPROVED"对象。
- 影响：极低概率并发审批场景下，调用方拿到与库不一致的返回值（库未变更但返回 APPROVED）。
- 评估：审批为低并发人工操作，且数据不会被错误流转，风险可接受。建议后续校验影响行数，0 行时抛 CATALOG_APP-409。

**F-3：approve 中 grantCatalogPartner 在 requireItem 之前已落库审批**
- 现象：`approve` 先 `applicationRepository.approve`（已持久化 APPROVED），再 `requireItem(approved.catalogId())` 取目录项做授权；若目录已被删除，审批已成功但 grant 抛 CATALOG-404，接口返回 500 而审批状态已变更。
- 影响：目录被删的边界场景下，审批与授权不一致。
- 评估：目录删除为极边界场景，P0 范围可接受。建议后续将 requireItem 前置或在 grant 失败时不影响审批结果。

**F-4：dev profile 种子数据未补**
- 现象：codex-task §6 要求"dev profile 启动有种子数据（从 SQL）"，但全仓无 `INSERT INTO t_data_catalog` 种子 SQL（V010 为 user_role_apikey，非 catalog 种子）。
- 影响：dev 环境启动后目录为空，需手动 add。
- 评估：HEAD 版本本就无种子，本次无回归；任务单该项为"或测试夹具"的二选一，测试夹具（PipelineModuleMockMvcTest.createCatalogItem）已覆盖。不阻断。

#### P3（提示）

- `grantCatalogPartner` 的 partnerCode 实际传入 `String.valueOf(item.partnerId())`（合作方 ID 而非 code），语义略有拉伸；但作为 invoke log partner_code 占位闭环可接受，且有单测验证。
- `CatalogController` 保留单参构造器（`new InMemoryCatalogApplicationRepository(), null, null`）供单元测试，4 参构造器标注 `@Autowired`，Spring 装配正确，无歧义。

## 7. 是否超出 codex-task 范围

- **DataServiceManager.grantCatalogPartner + writeInvokeLog 改动**：超出 §2"本次实现"字面清单，但属 P0-05 遗留 TODO（partner_code 暂留 null，注明 P0-07 补充）的闭环，且 §9 风险与回滚未禁止。属合理衔接，非无关重构。可接受。
- 其余改动均落在 catalog 模块、迁移、前端、测试范围内，无无关模块改动，无大型依赖引入，无密钥/生产配置修改。

## 8. 是否过度设计

未发现过度设计。preview 样例为单行合成数据（不直连外部数据源，符合 §9 风险控制）；脱敏为关键字匹配的轻量实现；仓储接口方法集（create/approve/reject/findById/findByApplicant/hasApproved）均为当前或可预见使用，无冗余。

## 9. 安全风险

- ✅ preview 未授权返回 403，不泄露未授权资产数据。
- ✅ 敏感字段脱敏，样例不包含真实凭证/个人标识。
- ✅ preview 写审计，可追溯。
- ✅ 申请/审批 actor 来自 JWT principal，未授权不可操作。
- ✅ 无 SQL 注入（参数化查询，IdGenerator 表名不可外部输入）。
- 无敏感信息入日志/文档。

## 10. 审查结论

**建议通过（有 P2 改进建议，不阻断合入）**

P0-07 达成全部最小可行结果与验收标准：目录申请/审批落库重启可查；preview 返回真实 sample + 未授权 403 + 敏感字段脱敏 + 审计留痕；生产代码无种子；partner_code 闭环。后端 68 测试、前端 35 测试全绿。改动符合最小改动路径，无过度设计，无安全风险，无范围外无关改动。

P2 建议（F-1~F-4）可在后续任务跟进，不作为本次返工阻断项。

## 11. 返工任务清单

无强制返工。后续可选改进（不阻断本次合入）：

1. [ ] 补 `POST /api/v1/catalog/applications/{id}/reject` 端点 + 前端驳回按钮，使 REJECTED 状态可达（对齐 FR-404）。
2. [ ] `JdbcCatalogApplicationRepository.transit` 校验 UPDATE 影响行数，0 行抛 CATALOG_APP-409。
3. [ ] `CatalogController.approve` 将 `requireItem` 前置，避免目录缺失时审批与授权不一致。
4. [ ] （可选）补 dev profile catalog 种子 SQL，便于本地启动演示。
