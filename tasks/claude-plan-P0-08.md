# 第一性原理开发计划 — P0-08 审计防篡改

> 任务编号：P0-08
> 分支：`ai/p0-audit-tamper`（已从 master 切出，master 已含 P0-07）
> 依据：`tasks/codex-task-P0-08-audit-tamper.md`、`docs/development-process-workflow.md` §3.1 P0-08、§5.2.6、§6.8、`docs/database-design.md`（`t_audit_log`）
> 前置：P0-03（JdbcAuditLogRepository 原型）、P0-05（trace_id 链路）、P0-07（AuditLogRepository Bean 装配）均已合入 master
> 日期：2026-06-29

---

## 1. 需求来源

- **技术要求**：审计日志（NFR-S02）留存≥3 年、不可篡改；金融监管要求审计防篡改或篡改可发现。
- **M7-D D2-05 上线门禁**：审计防篡改属上线安全门禁，未闭环则阻断上线。
- **M7 现状**：`t_audit_log` 已有 JdbcAuditLogRepository 原型，但无防篡改机制——有 DB 权限者可直接 update/delete 审计记录，篡改不可发现。
- **P0-05 遗留 RW-6**：trace_id 贯穿 audit 未落地，留 P0-08。

## 2. 第一性原理分析

### 2.1 用户真正要解决的问题是什么？
审计日志的**可信性**：当审计记录被事后篡改时，系统必须能发现篡改（最好能阻止），否则审计失去监管价值。

### 2.2 最小可行结果是什么？
1. 审计写库时形成可验证的 hash 链（每条记录的 hash 依赖上一条）。
2. 提供校验接口验证链完整性，篡改任何一条都能检出断链。
3. DB 层阻止业务账号 update/delete 审计表（权限策略优先，触发器补充）。
4. 一次主链路操作可按 trace_id 查全事件。

### 2.3 系统必须接收哪些输入？
- 审计事件（AuditEvent：trace_id/event_type/actor/action/detail/...）写入。
- 校验请求（可选范围：全表或按 trace_id）。

### 2.4 系统必须产生哪些输出？
- 持久化的审计记录（含 prev_hash + hash）。
- 链完整性校验结果（通过/断链，断链位置）。
- 按 trace_id 有序的事件列表。

### 2.5 从输入到输出之间不可省略的处理过程？
1. 写入前取"上一条记录的 hash"作为本条 prev_hash。
2. 计算本条 hash = SHA-256(prev_hash + canonical(payload) + created_at + 固定字段）。
3. 持久化 prev_hash/hash。
4. 校验时按 id 顺序遍历，逐条重算 hash，与存储 hash 比对，prev_hash 与上一条 hash 比对，不一致即断链。
5. DB 层用权限策略 revoke update/delete（业务账号仅 insert/select）。

### 2.6 哪些是核心能力？
- hash 链写入（append 时算链）。
- hash 链校验（verify）。
- DB 只追加约束。
- trace_id 查询。

### 2.7 哪些是增强能力？
- 按范围/分页校验（核心只做全表或单 trace）。
- 前端链状态展示（可选）。
- 历史无 hash 数据的迁移修复（核心只做占位 + 跳过）。

### 2.8 当前代码库中的最小改动路径？
- `t_audit_log` 补 `prev_hash`/`hash` 两列（V016/U016，**非任务单笔误的 V011**）。
- `JdbcAuditLogRepository.append`：取上一条 hash → 算本条 hash → 写入。
- 新增 `verify()` 方法 + `GET /api/v1/stats/audit/verify` 端点。
- `StatsController.audit` 扩展支持 `traceId` 参数。
- DB 权限策略：V016 附 `REVOKE UPDATE,DELETE` 语句（MySQL/达梦各写一份）。
- 内存仓储同步实现 hash 链（测试可用）。

### 2.9 如何测试？
- hash 链：写 N 条 → verify 通过；篡改某条 payload → verify 报断链。
- 只追加：UPDATE/DELETE 审计记录被 DB 拒绝（H2 测试用触发器模拟，因 H2 无权限模型）。
- trace_id 链路：一次主链路按 trace_id 查全事件。
- MockMvc：/stats/audit?traceId=... 有序返回；/stats/audit/verify 200。

### 2.10 如何验收？
见 §10 验收标准。

### 2.11 如何避免过度设计？
- 不做默克尔树/数字签名（hash 链足够满足"篡改可发现"）。
- 不做备份恢复（P2-05）。
- 不做监管报表（P1-04）。
- 不引入新依赖（用 JDK SHA-256）。
- 不重写既有 append/find 逻辑，只增量加 hash 计算。
- 触发器三库不兼容风险高 → **优先 DB 用户权限策略**，触发器仅作 H2 测试模拟，不强求三库触发器。

## 3. 功能拆解

| 编号 | 功能 | 说明 |
|---|---|---|
| F-1 | t_audit_log 补 hash 列 | V016/U016 + DM 版，加 `prev_hash VARCHAR(64)`、`hash VARCHAR(64)`，含索引 |
| F-2 | hash 链写入 | JdbcAuditLogRepository.append 取上一条 hash 算本条 hash；canonical payload 规范化 |
| F-3 | hash 链校验 | 新增 `verify()` 返回链状态（通过/断链位置）；内存仓储同步实现 |
| F-4 | verify 端点 | `GET /api/v1/stats/audit/verify`，权限 `stats:view` |
| F-5 | trace_id 查询 | StatsController.audit 扩展 `traceId` 参数，按 trace_id 有序返回 |
| F-6 | DB 只追加约束 | V016 REVOKE UPDATE/DELETE（MySQL/达梦）；H2 测试用触发器模拟只追加 |
| F-7 | trace_id 链路落地 | 主链路操作（如 catalog apply→approve）传播同一 trace_id 写审计 |
| F-8 | 历史数据兼容 | 迁移为历史行补 `prev_hash=''`/`hash` 占位；verify 跳过无 hash 段或标记 |

## 4. 影响模块

| 模块 | 改动 |
|---|---|
| `platform-common.audit` | AuditEvent 补 prevHash/hash（或独立 AuditHashRecord）；JdbcAuditLogRepository hash 链 + verify；InMemoryAuditLogRepository 同步；AuditLogRepository 接口加 verify |
| `platform-billing.stats` | StatsController.audit 加 traceId 参数；新增 /audit/verify 端点；AuditTraceService 加 verify/findByTraceId 透传 |
| `db/migration` + `db/migration-dm` | V016/U016 补列 + REVOKE 权限 |
| 各模块审计写入处 | 确保 trace_id 传播（P0-05 已铺路，核查是否全覆盖） |
| 前端 StatsView | 审计页支持 trace_id 查询；可选展示 verify 状态 |

## 5. 接口设计

### 5.1 仓储接口扩展

```java
public interface AuditLogRepository {
    AuditEvent append(AuditEvent event);
    List<AuditEvent> findByTraceId(String traceId);
    List<AuditEvent> findByActor(String actorType, String actorId);
    List<AuditEvent> findByEventType(String eventType, Instant from, Instant to);
    // 新增
    AuditChainVerification verify();                    // 全链校验
    AuditChainVerification verifyByTrace(String traceId); // 单 trace 校验（可选）
}
```

### 5.2 校验结果模型

```java
public record AuditChainVerification(
    boolean intact,           // 链是否完整
    long totalChecked,        // 校验条数
    long firstBrokenId,       // 首个断链 id（0 表示无）
    String reason             // 断链原因（hash_mismatch / prev_mismatch）
) {}
```

### 5.3 API

| 端点 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/api/v1/stats/audit` | GET | stats:view | 扩展：支持 `traceId` 参数，按 trace 有序返回；保留 eventType/from/to |
| `/api/v1/stats/audit/verify` | GET | stats:view | 返回 AuditChainVerification |

## 6. 数据结构

### 6.1 t_audit_log 补列（V016）

```sql
ALTER TABLE t_audit_log ADD COLUMN prev_hash VARCHAR(64);
ALTER TABLE t_audit_log ADD COLUMN hash VARCHAR(64);
CREATE INDEX idx_audit_hash ON t_audit_log(hash);
-- 历史 NULL 数据 verify 时跳过（prev_hash/hash 为空视为历史段）
```

### 6.2 hash 算法

```
canonical = event_type | actor_type | actor_id | target_type | target_id | action | detail | status | created_at_millis
hash = SHA-256(prev_hash + "\n" + canonical)  -> hex 64
```

- prev_hash：上一条（按 id 升序）的 hash；首条/历史段为空串。
- detail 在算 hash 前需脱敏（与 P0-04 一致，不含明文 secret）。

### 6.3 只追加约束

- MySQL/达梦：`REVOKE UPDATE, DELETE ON t_audit_log FROM <business_user>`（业务账号仅 INSERT/SELECT）。
- H2 测试：建 `BEFORE UPDATE/DELETE` 触发器抛错模拟只追加（因 H2 无 REVOKE 效果）。

## 7. 异常场景

| 场景 | 处理 |
|---|---|
| 并发写入 prev_hash 竞态 | 写入串行化：append 内 `SELECT ... ORDER BY id DESC LIMIT 1 FOR UPDATE` 取上一条（MySQL/达梦）；或单写线程。H2 无行锁则容忍，测试覆盖顺序写 |
| 历史数据无 hash | verify 跳过 prev_hash/hash 为空的行，标记为"历史段未校验"，不影响新数据校验 |
| 触发器三库不兼容 | 优先权限策略，触发器仅 H2 测试用，不强求三库 |
| 校验大表性能 | 核心做全表校验；可选按 trace_id 范围校验。大表优化留后续 |
| verify 期间有新写入 | 校验取快照（按当前 id 最大值），新写入不在本次校验范围 |

## 8. 测试策略

| 测试 | 覆盖 |
|---|---|
| hash 链写入 | 写 N 条，每条 hash 正确依赖上一条 |
| verify 通过 | 连续写 → verify intact=true |
| 篡改检出 | 直接 UPDATE 某条 payload → verify intact=false，firstBrokenId 正确 |
| 只追加约束 | UPDATE/DELETE 审计记录被拒（H2 触发器；JDBC 测试） |
| trace_id 链路 | 一次主链路（catalog apply→approve）按 trace_id 查全事件，有序 |
| 历史数据兼容 | 预置无 hash 历史行 → verify 跳过历史段，新数据仍校验 |
| MockMvc | /stats/audit?traceId= 有序返回；/stats/audit/verify 200 返回 intact |
| 三库迁移 | MigrationDialectCompatibilityTest 纳入 V016，t_audit_log CRUD + hash 列 |

## 9. Codex 实现边界

1. 版本号用 **V016/U016**（非任务单 V011），MySQL + 达梦双库。
2. hash 用 JDK SHA-256，不引入新依赖。
3. verify 逻辑放 JdbcAuditLogRepository + InMemoryAuditLogRepository，接口扩展。
4. DB 只追加优先权限策略（REVOKE），触发器仅 H2 测试模拟。
5. 不改既有 append/find 的签名语义，只增量加 hash；AuditEvent 加 prevHash/hash 需评估序列化影响（Jackson 字段可见性）。
6. trace_id 传播复用 P0-05 链路，不重造。
7. 不做备份恢复、监管报表、默克尔树、数字签名。
8. 不改密钥/生产配置；不动无关模块。
9. 必须补测试并全绿。
10. **顺带对齐**：`JdbcAuditLogRepository` 的 id 生成改为 `IdGenerator.nextId("t_audit_log")`，与全仓一致（见 §12）。
11. 输出 hash 链设计 + 篡改发现证据 + DB 权限策略说明。

## 10. 验收标准

- [ ] t_audit_log 补 prev_hash/hash（V016/U016 双库），迁移可执行。
- [ ] append 写入形成 hash 链，每条 hash 依赖上一条。
- [ ] verify 接口可用，篡改可发现（intact=false + firstBrokenId）。
- [ ] DB 层禁 update/delete（权限策略，H2 触发器模拟测试通过）。
- [ ] trace_id 全链路查询可用（/stats/audit?traceId=）。
- [ ] 历史无 hash 数据 verify 兼容（跳过/标记）。
- [ ] 并发写入与篡改测试通过。
- [ ] MockMvc + 前端测试全绿。
- [ ] 三库迁移测试纳入 V016。

## 11. 风险与回滚

| 风险 | 控制 |
|---|---|
| hash 链并发竞态 | 行锁串行取上一条；测试覆盖顺序写 |
| 触发器三库不兼容 | 优先 REVOKE 权限，触发器仅 H2 测试 |
| 历史数据无 hash | 占位 + verify 跳过历史段 |
| AuditEvent 加字段影响序列化 | 评估 Jackson 字段可见性，加 @JsonIgnore 或默认值，回归既有 audit 返回 |
| REVOKE 在共享账号环境影响 | 开发库用统一账号，REVOKE 仅针对审计表；回滚用 U016 GRANT |
| **回滚** | V016 有 U016；权限/触发器可回退；hash 列可空，不影响既有读写 |

## 12. 附带改进（已确认执行）

`JdbcAuditLogRepository` 当前用 `AtomicLong(System.currentTimeMillis())` 生成 id，未对齐 P0-03 统一的 `IdGenerator`。**P0-08 改造 append 时顺带对齐为 IdGenerator**（用户 2026-06-29 确认）。

- **做法**：`JdbcAuditLogRepository` 持有 `IdGenerator`（`new IdGenerator(jdbcTemplate)`），`append` 用 `idGenerator.nextId("t_audit_log")` 替代 `AtomicLong.incrementAndGet`，与 auth/partner/catalog 等模块一致。
- **理由**：与全仓 id 生成模式一致，消除 id 碰撞隐患；append 本就要改（加 hash），增量成本低。
- **约束**：仅改 id 生成，不扩大到其他逻辑；InMemoryAuditLogRepository 保留 AtomicLong（无 DB，测试用）。
- **测试**：现有 JDBC 审计测试回归 + hash 链测试覆盖新 id 路径。
