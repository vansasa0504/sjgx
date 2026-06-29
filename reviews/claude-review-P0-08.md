# Claude Code 审查结果 — P0-08 审计防篡改

## 1. 审查对象

- 任务：P0-08 审计防篡改
- 分支：`ai/p0-audit-tamper`（从 master 切出，master 已含 P0-07）
- 任务单：`tasks/codex-task-P0-08.md`，计划：`tasks/claude-plan-P0-08.md`
- 审查日期：2026-06-29
- 前置：P0-03（JdbcAuditLogRepository 原型）、P0-05（trace_id 链路）、P0-07（AuditLogRepository Bean 装配）均已合入 master
- 改动范围：t_audit_log 补 hash 列（V016/U016 双库）、AuditHashing 工具、AuditEvent 补字段、JdbcAuditLogRepository hash 链 + verify + IdGenerator 对齐、InMemoryAuditLogRepository 同步、StatsController verify 端点 + traceId 查询、CatalogController apply/approve 写审计 + trace_id 透传、前端 StatsView verify 按钮 + traceId 过滤

## 2. Git 状态

改动全部未提交（工作区，分支 `ai/p0-audit-tamper`）：

```text
 M platform-billing/.../StatsController.java
 M platform-billing/.../stats/AuditTraceService.java
 M platform-billing/test/.../StatsControllerTest.java
 M platform-common/.../audit/AuditEvent.java
 M platform-common/.../audit/AuditLogRepository.java
 M platform-common/.../audit/InMemoryAuditLogRepository.java
 M platform-common/.../audit/JdbcAuditLogRepository.java
 M platform-common/test/.../audit/AuditLogRepositoryTest.java
 M platform-common/test/.../db/MigrationDialectCompatibilityTest.java
 M platform-pipeline/.../catalog/CatalogController.java
 M platform-pipeline/test/.../PipelineModuleMockMvcTest.java
 M platform-ui/src/api/stats.ts
 M platform-ui/src/views/StatsView.vue
 M platform-ui/src/views/__tests__/m7c-pages.test.ts
?? db/migration/V016__audit_tamper.sql + U016
?? db/migration-dm/V016__audit_tamper.sql + U016
?? platform-common/.../audit/AuditChainVerification.java
?? platform-common/.../audit/AuditHashing.java
?? tasks/claude-plan-P0-08.md, tasks/codex-task-P0-08.md
```

## 3. 测试验证

### 3.1 后端

```bash
mvn -pl platform-common install -DskipTests   # 重建 common（本地 jar 过期会 NoSuchMethodError，非本次缺陷）
mvn test -pl platform-common
mvn test -pl platform-billing,platform-pipeline
```

结果：
- **platform-common**：BUILD SUCCESS，Tests run: 32（AuditLogRepositoryTest 4 用例：hash 链写入/篡改检出/历史跳过/H2 触发器只追加）
- **platform-pipeline**：BUILD SUCCESS，Tests run: 72（PipelineModuleMockMvcTest 23→24，新增 trace_id 链路用例）
- **platform-billing**：BUILD SUCCESS，Tests run: 39（StatsControllerTest 新增 trace 查询 + verify）
- 三库迁移测试 MigrationDialectCompatibilityTest 通过，V016 hash 列 CRUD + 类型断言纳入

### 3.2 前端

```bash
npm run test:unit
```

结果：**11 文件 / 37 测试全部通过**

### 3.3 测试结论

全绿，无回归。hash 链、篡改检出、历史兼容、只追加约束、trace_id 链路、verify 端点均覆盖。

## 4. 需求符合性

| 需求项（codex-task §2） | 实现情况 | 结论 |
|---|---|---|
| F-1 补 hash 列 | V016/U016 双库，prev_hash/hash VARCHAR(64) + idx_audit_hash | ✅ |
| F-2 hash 链写入 | AuditHashing.hash(prevHash, event)，prev_hash 取 latestHash（FETCH FIRST 1 ROWS ONLY）；JDBC+内存双实现 | ✅ |
| F-3 hash 链校验 | verify() 按 id 升序逐条比对，返回 intact/firstBrokenId/reason | ✅ |
| F-4 verify 端点 | `GET /api/v1/stats/audit/verify`，权限 stats:view | ✅ |
| F-5 trace_id 查询 | StatsController.audit 加 traceId 参数，trace 优先返回 | ✅ |
| F-6 DB 只追加约束 | **REVOKE 未执行（仅注释），H2 触发器测试覆盖只追加语义** | ⚠️ 见 §6.2 |
| F-7 trace_id 链路落地 | CatalogController apply/approve 写审计，X-Trace-Id 头透传；MockMvc 验证按 trace 查 2 事件 | ✅ |
| F-8 历史数据兼容 | verify 跳过 hash IS NULL 行，totalChecked 不含历史段 | ✅ |
| F-9 id 对齐 | JdbcAuditLogRepository 改用 IdGenerator.nextId("t_audit_log")，移除 AtomicLong | ✅ |

## 5. 与 claude-plan 对齐

- **最小可行结果**（hash 链 + verify + 只追加 + trace_id 查询）：F-1~F-5、F-7~F-9 达成；F-6 部分达成（见 §6.2）。
- **hash 算法**：`SHA-256(prev_hash + "\n" + canonical)`，canonical 含 event_type/actor/target/action/detail/status/created_at_millis，与计划一致。
- **并发竞态**：JdbcAuditLogRepository.append 用 `synchronized` 串行化，比计划的行锁更简单可靠（单实例内），符合计划 §7"单写线程"方向。
- **避开 ` LIMIT ` 守护**：用 `FETCH FIRST 1 ROWS ONLY` 取 latestHash，三库兼容且不触发方言断言，正确落实任务单 §9 提示。
- **AuditEvent 加字段**：用向后兼容 13 参构造器委托 15 参构造器（prevHash/hash 默认 null），回归既有 audit 端点序列化，正确。
- **id 对齐**：顺带完成（已确认决策），与全仓模式一致。
- **M7-D D2-05 上线门禁**：审计防篡改 hash 链 + verify 闭环；只追加约束需部署 runbook 配合（见 §6.2）。
- **P0-05 遗留 RW-6**：trace_id 贯穿 audit 落地（catalog apply/approve），闭环。

## 6. 代码质量与安全

### 6.1 优点

1. **AuditHashing 独立工具类**：hash 算法集中，JDBC/内存/测试复用，canonical 规范化清晰。
2. **AuditChainVerification 语义明确**：intact/totalChecked/firstBrokenId/reason，工厂方法 intact()/broken()，前端可直接消费。
3. **并发安全**：JDBC append `synchronized` 保证 latestHash→计算→写入原子，消除 prev_hash 竞态；内存用 CopyOnWriteArrayList + 末尾取 hash（单线程测试场景足够）。
4. **历史兼容优雅**：verify 遇 NULL hash 行重置 previousHash="" 并跳过，新链从首个有 hash 行重新开始，不误报历史段断链。
5. **只追加测试用 H2 Trigger**：NoMutationTrigger 在 UPDATE/DELETE 时抛 SQLException，验证只追加语义，且触发器仅测试代码内（不进迁移脚本），符合任务单约束。
6. **trace_id 透传**：X-Trace-Id 头方案，apply/approve 两请求带同一 traceId 即可串链路，MockMvc 验证 2 事件有序。
7. **三库迁移守护**：MigrationDialectCompatibilityTest 纳入 V016，断言 hash 列类型（CHARACTER VARYING）+ CRUD，DM 不含 TEXT/TINYINT 守护通过。
8. **id 对齐**：JdbcAuditLogRepository 用 IdGenerator，消除 AtomicLong(System.currentTimeMillis()) 的 id 碰撞隐患。

### 6.2 发现的问题

#### P1（需关注，建议部署前补强）

**H-1：F-6 DB 只追加约束未在迁移层实际落地**
- 现象：V016/U016 中 `REVOKE UPDATE, DELETE ON t_audit_log FROM <business_user>` **以注释形式存在**，未实际执行。理由（注释说明）："concrete database user is environment-specific"。
- 影响：生产环境若仅靠应用层（Repository 无 update/delete 方法）+ hash 链 verify，则**篡改仍可发生但可发现**（hash 链检出），而非"篡改可阻止"。任务单 F-6 要求"DB 层禁 update/delete（权限策略优先）"，当前 DB 层未禁。
- 评估：
  - hash 链 verify 已满足"篡改可发现"的核心目标（NFR-S02 最低要求）。
  - 但"DB 层禁 update/delete"是任务单明确项，未落地属偏离。Codex 的理由（DB 用户环境相关）合理——REVOKE 需要具体用户名，迁移脚本无法通用化。
  - H2 触发器测试覆盖了只追加语义，但仅在测试库，生产无对应约束。
- 建议：**不阻断合入**，但需在部署 runbook 中明确要求对应用 DB 账号执行 REVOKE，并在 dev-progress/审查记录中标注为上线前必做项。或后续补一个可选的迁移脚本（读取环境变量用户名执行 REVOKE）。

#### P2（建议改进，不阻断）

**H-2：内存仓储 verify 与 JDBC verify 的并发语义不对称**
- 现象：JDBC append `synchronized`，内存 append 未同步（CopyOnWriteArrayList 的 add 原子，但"取末尾 hash + 计算 + add"非原子）。
- 影响：内存仓储并发 append 时，两条可能取到同一 prevHash，导致链分叉。内存仓储仅用于测试/无 DB 回退，实际并发风险低。
- 评估：内存仓储为测试用，单线程测试通过。生产走 JDBC（已 synchronized）。可接受，建议内存 append 也 synchronized 保持一致。

**H-3：verify 全表扫描性能**
- 现象：`verify()` 执行 `SELECT * FROM t_audit_log ORDER BY id` 全表加载到内存逐条校验。
- 影响：审计表 ≥3 年留存（NFR-S02），大表 verify 性能差。
- 评估：verify 为低频运维操作（手动触发校验链），非高频接口。任务单 §7 已列"大表优化留后续"。可接受，建议后续支持按 id 范围/分页 verify。

**H-4：trace_id 链路仅覆盖 catalog apply→approve**
- 现象：F-7 trace_id 贯穿只在 CatalogController apply/approve 落地，PartnerService/AuthService 等其他审计写入处未统一 trace_id 透传。
- 影响：其他主链路（如 partner 全生命周期）按 trace_id 查不全事件。
- 评估：任务单 F-7 要求"至少一条主链路验证"，catalog 已满足最低要求。全链路覆盖属增强，可后续铺开。
- 注：CatalogController.preview 的审计（appendPreviewAudit）仍用 null traceId（自动 UUID），未与 apply/approve 串。属一致性小瑕疵。

#### P3（提示）

**H-5：detail 脱敏依赖写入方**
- hash canonical 包含 detail，若写入方传入明文 secret 则 hash 含明文（虽不可逆，但 detail 列本身会存明文）。
- 评估：任务单 §6.1 要求"detail 算 hash 前须已脱敏"，但 P0-08 未强制脱敏校验，依赖各写入方自律（与 P0-04 一致）。CatalogController 写入的 detail 为 catalogId/scope/applicant，无敏感信息。可接受。

**H-6：verify 端点无分页/范围参数**
- 现象：`/audit/verify` 无参数，始终全表校验。
- 评估：与 H-3 同源，低频运维可接受。

## 7. 是否超出任务范围

- **CatalogController apply/approve 写审计**：F-7 trace_id 链路落地的必要伴随，属任务范围。
- **id 对齐 IdGenerator**：任务单 §11 明确要求（已确认决策），属范围。
- **前端 verify 按钮 + traceId 过滤**：任务单 §4 列入，属范围。
- 无无关模块改动，无大型依赖引入，无密钥/生产配置修改。

## 8. 是否过度设计

未发现过度设计。AuditHashing/AuditChainVerification 为必要抽象；hash 链为最小可行防篡改；未做默克尔树/数字签名（符合"不做"清单）。verify 全表扫描为最小实现，性能优化合理留后。

## 9. 安全风险

- ✅ hash 链使审计篡改可发现（核心目标达成）。
- ✅ Repository 无 update/delete 方法（应用层只追加）。
- ✅ verify 检出篡改（hash_mismatch/prev_mismatch），返回断链位置。
- ✅ trace_id 全链路可追溯（catalog 链路）。
- ✅ 无 SQL 注入（参数化 + FETCH FIRST 1 ROWS ONLY）。
- ⚠️ DB 层未 REVOKE，生产篡改"可发现但不可阻止"——需部署 runbook 补 REVOKE（H-1）。
- ✅ detail 无明文 secret（catalog 写入内容安全）。
- 无敏感信息入日志。

## 10. 审查结论

**建议通过（H-1 需部署前补强，不阻断合入）**

P0-08 达成核心目标：审计 hash 链写入、verify 校验篡改可发现、trace_id 全链路查询、id 对齐、三库迁移。后端 common 32 + pipeline 72 + billing 39 + 前端 37 测试全绿，无回归。代码质量高（AuditHashing 集中、synchronized 并发安全、历史兼容优雅、向后兼容构造器）。

**唯一偏离**：F-6 DB 只追加约束未在迁移层落地（REVOKE 仅注释），Codex 理由（DB 用户环境相关）合理。hash 链 verify 已满足"篡改可发现"的 NFR-S02 最低要求，但"DB 层禁 update/delete"需部署 runbook 配合执行 REVOKE，列为上线前必做项。

P2 建议（H-2~H-4）可在后续任务跟进，不阻断本次合入。

## 11. 返工任务清单

无强制返工。上线前必做 + 后续可选改进：

### 上线前必做（H-1）
1. [ ] 部署 runbook 增加对应用 DB 账号执行 `REVOKE UPDATE, DELETE ON t_audit_log FROM <app_user>`，并在 dev-progress 标注为上线门禁项。或补可选迁移脚本（读环境变量用户名执行 REVOKE）。

### 后续可选（不阻断）
2. [ ] H-2：InMemoryAuditLogRepository.append 加 synchronized，与 JDBC 一致。
3. [ ] H-3/H-6：verify 支持按 id 范围/分页校验，应对大表性能。
4. [ ] H-4：trace_id 透传铺开到 PartnerService/AuthService 等其他审计写入处；CatalogController.preview 审计也接 trace_id。
5. [ ] H-5：评估审计 detail 写入脱敏校验（可选）。

## 12. 建议提交

P0-08 可提交。建议提交信息：

```text
feat(P0-08): audit log hash chain with tamper detection and trace_id linkage

- t_audit_log adds prev_hash/hash columns (V016/U016, MySQL + DM)
- append forms SHA-256 hash chain (prev_hash + canonical payload); synchronized for concurrency safety
- verify() detects tampering (hash_mismatch/prev_mismatch) with first broken id; skips legacy NULL-hash rows
- GET /stats/audit/verify endpoint (stats:view); /stats/audit supports traceId query
- catalog apply/approve write audit with X-Trace-Id propagation for full-trace retrieval
- JdbcAuditLogRepository id generation aligned to IdGenerator
- append-only enforced at DAO layer; H2 trigger test covers mutation rejection
- DB REVOKE for update/delete deferred to deployment runbook (env-specific user)
- frontend StatsView audit-chain verify button + traceId filter
```
