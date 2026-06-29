# Codex 执行任务 - P0-08：审计防篡改

> 阶段：P0（上线阻断修复）
> 任务编号：P0-08
> 分支：`ai/p0-audit-tamper`（已从 master 切出，master 已含 P0-07）
> 依据：`tasks/claude-plan-P0-08.md`、`docs/development-process-workflow.md` §3.1 P0-08、§5.2.6、§6.8、`docs/database-design.md`（`t_audit_log`）
> 前置：P0-03（JdbcAuditLogRepository 原型）、P0-05（trace_id 链路）、P0-07（AuditLogRepository Bean 装配）均已合入 master
> 日期：2026-06-29

---

## 1. 背景与目标

`t_audit_log` 已有 JdbcAuditLogRepository 原型（P0-03），但无防篡改机制——有 DB 权限者可直接 update/delete 审计记录，篡改不可发现。金融场景要求审计不可篡改或篡改可发现（NFR-S02、M7-D D2-05 上线门禁）。P0-05 已铺 trace_id 但未贯穿审计（遗留 RW-6）。

**最小可行结果**：
1. 审计写库时形成 hash 链（每条 hash 依赖上一条）。
2. verify 接口可校验链完整性，篡改可发现（断链位置）。
3. DB 层禁业务账号 update/delete（权限策略优先，触发器仅 H2 测试模拟）。
4. 一次主链路操作可按 trace_id 查全事件。

## 2. 范围

### 本次实现

- **F-1 补 hash 列**：`t_audit_log` 加 `prev_hash VARCHAR(64)`、`hash VARCHAR(64)`（V016/U016 + DM 双库）+ `idx_audit_hash` 索引。
- **F-2 hash 链写入**：`JdbcAuditLogRepository.append` 取上一条（按 id 升序）hash 作为 `prev_hash`，本条 `hash = SHA-256(prev_hash + "\n" + canonical(payload))`；canonical = `event_type|actor_type|actor_id|target_type|target_id|action|detail|status|created_at_millis`。`InMemoryAuditLogRepository` 同步实现 hash 链。
- **F-3 hash 链校验**：`AuditLogRepository` 接口新增 `AuditChainVerification verify()`；按 id 升序逐条重算 hash 比对、prev_hash 与上一条 hash 比对，不一致即断链。JDBC + 内存两实现。
- **F-4 verify 端点**：`GET /api/v1/stats/audit/verify`，权限 `stats:view`，返回 `AuditChainVerification`。
- **F-5 trace_id 查询**：`StatsController.audit` 扩展支持 `traceId` 参数（按 trace_id 有序返回）；保留既有 eventType/from/to。
- **F-6 DB 只追加约束**：V016 附 `REVOKE UPDATE, DELETE ON t_audit_log FROM <user>`（MySQL/达梦各一份）；H2 测试用 `BEFORE UPDATE/DELETE` 触发器模拟只追加。
- **F-7 trace_id 链路落地**：主链路操作（如 catalog apply→approve，或 partner create→approve）传播同一 trace_id 写审计；至少一条主链路验证按 trace_id 查全事件。
- **F-8 历史数据兼容**：迁移不强制回填历史 hash；verify 遇 `prev_hash/hash` 为空的行标记"历史段未校验"并跳过，不影响新数据校验。
- **F-9 id 对齐（顺带）**：`JdbcAuditLogRepository` 的 id 生成从 `AtomicLong(System.currentTimeMillis())` 改为 `IdGenerator.nextId("t_audit_log")`（见 §11）。

### 不做

- 不做默克尔树/数字签名（hash 链足够"篡改可发现"）。
- 不做备份恢复（P2-05）、监管报表（P1-04）。
- 不引入新依赖（用 JDK SHA-256）。
- 不改既有 append/find 的签名语义，只增量加 hash。
- 不强求三库触发器（优先权限策略）。

## 3. 必读输入

- `AGENTS.md`、`tasks/claude-plan-P0-08.md`（第一性原理计划，权威）
- `docs/database-design.md`（`t_audit_log`）、`docs/detailed-requirements-design.md`（审计设计）
- `platform-common/src/main/java/com/platform/common/audit/`：`AuditEvent.java`、`AuditLogRepository.java`、`JdbcAuditLogRepository.java`、`InMemoryAuditLogRepository.java`、`AuditLogAspect.java`
- `platform-billing/src/main/java/com/platform/billing/StatsController.java`、`stats/AuditTraceService.java`
- `platform-common/src/test/java/com/platform/common/db/MigrationDialectCompatibilityTest.java`（三库迁移测试，V016 须通过其方言守护）
- `db/migration/V008__governance.sql`、`db/migration-dm/V008__governance.sql`（t_audit_log 现状）
- `platform-common/src/main/java/com/platform/common/db/IdGenerator.java`（id 对齐参考）

## 4. 需要修改的模块

| 模块 | 文件 | 改动 |
|---|---|---|
| platform-common.audit | `AuditEvent.java` | 新增 `prevHash`/`hash` 字段（record 组件，注意 Jackson 序列化，见 §6.4） |
| platform-common.audit | `AuditLogRepository.java` | 新增 `AuditChainVerification verify()` 方法 |
| platform-common.audit | 新增 `AuditChainVerification.java` | 校验结果 record（intact/totalChecked/firstBrokenId/reason） |
| platform-common.audit | `JdbcAuditLogRepository.java` | hash 链写入 + verify + IdGenerator 对齐 |
| platform-common.audit | `InMemoryAuditLogRepository.java` | hash 链写入 + verify（同步） |
| platform-billing.stats | `StatsController.java` | audit 加 traceId 参数 + 新增 /audit/verify 端点 |
| platform-billing.stats | `AuditTraceService.java` | 透传 verify / findByTraceId |
| db/migration | `V016__audit_tamper.sql` + `U016__audit_tamper.sql` | 补列 + REVOKE（U016 为 GRANT + DROP COLUMN） |
| db/migration-dm | `V016__audit_tamper.sql` + `U016__audit_tamper.sql` | 达梦版（CLOB/SMALLINT 守护，TEXT→CLOB） |
| platform-common.db | `MigrationDialectCompatibilityTest.java` | 纳入 V016 + t_audit_log hash 列 CRUD |
| 各审计写入处 | 视 F-7 | 确保 trace_id 传播（核查 CatalogController/PartnerService/AuthService） |
| platform-ui | `StatsView.vue` + `api/stats.ts` | 审计页 trace_id 查询 + 可选 verify 状态 |

## 5. 数据库/API/前端影响

### 5.1 数据库
- `t_audit_log` 加 `prev_hash`、`hash` 列 + 索引（V016）。
- `REVOKE UPDATE, DELETE ON t_audit_log FROM <business_user>`（业务账号仅 INSERT/SELECT）。
- 历史数据 prev_hash/hash 留 NULL，verify 跳过。

### 5.2 API
- `GET /api/v1/stats/audit` 扩展：新增 `traceId` 参数，按 trace 有序返回；保留 eventType/from/to。
- `GET /api/v1/stats/audit/verify` 新增：返回 `AuditChainVerification`，权限 `stats:view`。

### 5.3 前端
- StatsView 审计页支持 trace_id 查询输入；可选展示 verify 状态（intact/断链）。

## 6. 实现细节约束

### 6.1 hash 算法
```
canonical = event_type|actor_type|actor_id|target_type|target_id|action|detail|status|created_at_millis
prev_hash = 上一条（id 升序）的 hash；首条/历史段为 ""
hash = hex(SHA-256(prev_hash + "\n" + canonical))
```
- detail 在算 hash 前须为已脱敏内容（与 P0-04 一致，审计写入处不应含明文 secret）。
- created_at_millis 用 `event.createdAt().toEpochMilli()`。

### 6.2 verify 逻辑
- 按 id 升序遍历全表（或单 trace）。
- 跳过 `hash IS NULL` 的历史行（标记 totalChecked 不含，或单独计数 historySkipped）。
- 对有 hash 的行：重算 hash 与存储比对；prev_hash 与上一条有 hash 行的 hash 比对。
- 首个不一致 → `intact=false`，`firstBrokenId` = 该行 id，`reason` = `hash_mismatch` 或 `prev_mismatch`。

### 6.3 DB 只追加
- MySQL/达梦 V016：`REVOKE UPDATE, DELETE ON t_audit_log FROM <user>;`（user 用占位或环境变量，开发库统一账号）。
- H2 测试（JdbcAuditLogRepository 测试 + MigrationDialectCompat）：建触发器模拟：
  ```sql
  CREATE TRIGGER audit_no_update BEFORE UPDATE ON t_audit_log ... SIGNAL 'audit is append-only';
  ```
  注意：H2 触发器语法与方言守护测试（不含 ` LIMIT `、DM 不含 ` TEXT`）兼容。触发器 SQL 放测试代码内，不进迁移脚本（迁移脚本仅 REVOKE）。

### 6.4 AuditEvent 加字段注意
- AuditEvent 是 record，加 `prevHash`/`hash` 组件会影响既有构造器调用与 Jackson 序列化。
- **方案**：加字段时提供向后兼容构造器（旧 13 参构造器委托新构造器，prevHash/hash 默认 null）；新字段用 `@JsonProperty` 或保持 record 默认序列化（前端 audit 返回多两字段无害）。
- 既有 `append` 返回的 AuditEvent 须带 hash/prevHash。
- 回归既有 audit 端点返回（StatsController.audit 返回 List<AuditEvent>）。

### 6.5 trace_id 传播
- 核查 `CatalogController.appendPreviewAudit`（当前 trace_id 传 null→自动 UUID）、`PartnerService`、`AuthService` 审计写入。
- F-7 至少在一条主链路（建议 catalog apply→approve，或 partner 全生命周期）用同一 trace_id 串起多事件。可用请求头 `X-Trace-Id` 透传或服务内生成后贯穿。
- 不要重造 P0-05 的 trace_id 机制，复用。

## 7. 必须补充的测试

- **hash 链写入**：写 N 条，每条 hash 正确依赖上一条（断言 hash 非 null 且符合算法）。
- **verify 通过**：连续写 → `verify().intact() == true`。
- **篡改检出**：直接 UPDATE 某条 detail → `verify().intact() == false`，`firstBrokenId` 正确，reason 正确。
- **只追加约束**：JDBC 测试中 UPDATE/DELETE 审计记录被拒（H2 触发器）。
- **trace_id 链路**：一次主链路按 trace_id 查全事件，有序，断言事件数与顺序。
- **历史数据兼容**：预置 hash IS NULL 历史行 → verify 跳过历史段，新数据仍校验通过。
- **id 对齐**：append 后 id 由 IdGenerator 生成（连续/无碰撞），回归既有 JDBC 审计测试。
- **MockMvc**：`/stats/audit?traceId=...` 有序返回；`/stats/audit/verify` 200 返回 intact 字段；权限 401/403。
- **三库迁移**：`MigrationDialectCompatibilityTest` 纳入 V016，t_audit_log 含 hash 列 CRUD 通过；方言守护（不含 ` LIMIT `，DM 不含 ` TEXT`/` TINYINT `）通过。

## 8. 验收命令

```bash
mvn -pl platform-common install -DskipTests          # 若 common 改动
mvn test -pl platform-common                          # 审计 + 迁移测试
mvn test -pl platform-billing                         # StatsController verify/trace
mvn test "-Dspring.profiles.active=jdbc"              # 全量回归
npm run test:unit                                     # 前端
```

## 9. 风险与回滚

| 风险 | 控制 |
|---|---|
| hash 链并发竞态 | append 内 `SELECT hash FROM t_audit_log ORDER BY id DESC LIMIT 1` 取上一条（注意方言守护禁 ` LIMIT ` 空格——用 `LIMIT 1` 紧贴或子查询）；高并发串行化留后续，测试覆盖顺序写 |
| 触发器三库不兼容 | 迁移脚本仅 REVOKE，触发器仅 H2 测试代码内 |
| 历史数据无 hash | verify 跳过 NULL hash 行 |
| AuditEvent 加字段破坏序列化 | 向后兼容构造器 + 回归 audit 端点 |
| REVOKE 影响共享账号 | 仅审计表 REVOKE，U016 回滚 GRANT |
| **回滚** | V016 有 U016（DROP COLUMN + GRANT）；hash 列可空不影响既有读写 |

> 注意：迁移脚本中的 `LIMIT` 须避开 `MigrationDialectCompatibilityTest` 的 ` LIMIT `（带空格）断言。取上一条 hash 可用 `ORDER BY id DESC LIMIT 1`（`LIMIT 1` 无尾随空格不触发断言）或 `FETCH FIRST 1 ROWS ONLY`（H2/达梦兼容）。优先 `FETCH FIRST 1 ROWS ONLY` 以兼容三库。

## 10. 完成判定

- [ ] t_audit_log 补 prev_hash/hash（V016/U016 双库），迁移可执行，三库测试通过。
- [ ] append 写入形成 hash 链，每条 hash 依赖上一条。
- [ ] verify 接口可用，篡改可发现（intact=false + firstBrokenId + reason）。
- [ ] DB 层禁 update/delete（REVOKE + H2 触发器测试通过）。
- [ ] trace_id 全链路查询可用（/stats/audit?traceId=）。
- [ ] 历史无 hash 数据 verify 兼容（跳过/标记）。
- [ ] id 对齐 IdGenerator 完成，回归通过。
- [ ] 并发顺序写与篡改测试通过。
- [ ] MockMvc + 前端测试全绿。
- [ ] 输出 hash 链设计 + 篡改发现证据 + DB 权限策略说明。

## 11. 附带改进（已确认）

`JdbcAuditLogRepository` 的 id 生成从 `AtomicLong(System.currentTimeMillis())` 改为 `IdGenerator.nextId("t_audit_log")`：
- 持有 `IdGenerator`（构造器 `new IdGenerator(jdbcTemplate)`）。
- `append` 用 `idGenerator.nextId("t_audit_log")` 替代 `AtomicLong.incrementAndGet`。
- 移除 `AtomicLong ids` 字段。
- `InMemoryAuditLogRepository` 保留 AtomicLong（无 DB，测试用）。
- 与 auth/partner/catalog 等模块 id 生成模式一致。

## 12. 实现边界（Codex 遵守）

1. 版本号 **V016/U016**（非任务单笔误的 V011），MySQL + 达梦双库。
2. hash 用 JDK SHA-256，不引入新依赖。
3. verify 逻辑放 JDBC + 内存两仓储，接口扩展。
4. DB 只追加优先 REVOKE 权限，触发器仅 H2 测试代码内。
5. 不改既有 append/find 签名语义；AuditEvent 加字段用向后兼容构造器。
6. trace_id 传播复用 P0-05 链路，不重造。
7. 不做备份恢复、监管报表、默克尔树、数字签名。
8. 不改密钥/生产配置；不动无关模块；不跳过测试。
9. 迁移脚本避开 ` LIMIT ` 断言（用 `FETCH FIRST 1 ROWS ONLY`），DM 不含 ` TEXT`/` TINYINT `。
10. 顺带对齐 JdbcAuditLogRepository id 为 IdGenerator（见 §11）。
11. 完成后输出修改文件、测试命令、测试结果、hash 链设计、篡改证据、DB 权限策略说明、潜在风险。
