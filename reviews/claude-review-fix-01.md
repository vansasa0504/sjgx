# Claude Code 审查结果 - 批次1 安全认证修复

> 审查阶段：代码修复批次1（RISK-F-01/02/03/04，A1 MFA / A2 ABAC / A3 证书 / A4 SSO）
> 审查日期：2026-07-15
> 审查范围：`ai/fix-01-security-auth` 分支，13 个未提交新文件
> 任务单：`tasks/codex-task-fix-01.md`
> 计划：`tasks/claude-plan.md` §4.3/§4.5.2
> 审查方：Claude Code（主控）+ 独立对抗式子代理 fix01-adversary（第二意见，已回传结论）
> Codex 自评：声明为"待返工草稿，不能声明任务完成"

---

## 1. 审查对象

| 类别 | 文件 | 新增 |
|---|---|---|
| A1 MFA | `platform-auth/.../Totp.java` | ✓ |
| A1 MFA | `platform-auth/.../MfaLoginEnforcementAspect.java` | ✓ |
| A1/A3/A4 | `platform-auth/.../AdvancedAuthService.java` | ✓ |
| 接口 | `platform-auth/.../AdvancedAuthController.java` | ✓ |
| 配置 | `platform-auth/.../AdvancedAuthBeanConfiguration.java` | ✓ |
| A4 SSO | `platform-auth/.../SsoAdapter.java`、`MockSsoAdapter.java` | ✓ |
| 测试 | `platform-auth/.../AdvancedAuthServiceTest.java` | ✓ |
| A2 ABAC | `platform-partner/.../PartnerAbacAspect.java` | ✓ |
| 测试 | `platform-partner/.../PartnerAbacMockMvcTest.java` | ✓ |
| DDL | `db/migration/V024__security_auth.sql`、`U024__security_auth.sql` | ✓ |

## 2. Git 状态

```text
分支：ai/fix-01-security-auth
git status：13 个未跟踪新文件（??），无 tracked modified
未提交、未暂存；未改动 .claude/worktrees/
git diff --check -> 退出码 0（仅 LF/CRLF 警告）
```

## 3. 代码差异摘要

- **A1 MFA**：RFC 6238 TOTP（HmacSHA1，±1 窗口，SecureRandom 密钥，SM4 加密存储）；MFA 绑定/challenge/重放检查；`MfaLoginEnforcementAspect` 拦截 login 返回 challenge
- **A2 ABAC**：`PartnerAbacAspect` 资源所有权（consumerOwnership）+ 字段脱敏（credentialField）
- **A3 证书**：bind/revoke/rotate/login，fingerprint 唯一，证书加密存储
- **A4 SSO**：`SsoAdapter` 接口 + `MockSsoAdapter`（redirect/callback）
- **DDL**：V024 加 `t_user.mfa_secret_cipher/mfa_enabled/mfa_last_counter` + `t_user_certificate` 表；U024 回滚

## 4. 需求满足情况（A1-A4）

| 项 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| A1 MFA | TOTP 二步认证、重放拒绝、SM4 加密 | 部分 | RFC 6238 实现正确；但内存存储致重启重放失效（P1-1）、并发重放（P2-N3）、MFA 可选未强制（P2-7） |
| A2 ABAC | 资源所有权 + 字段脱敏 + 水平越权 | 部分 | 切面基本正确；但 configureQuota 允许消费方自改配额（P1-5）、list 未覆盖（P2-N4） |
| A3 证书 | 证书认证 + 生命周期 | 不满足 | verify 逻辑反转只接受自签名（P1-2）、无私钥 PoP（P1-N1）、权限快照致撤销失效（P1-N2）、缺 CA 链/CRL/OCSP |
| A4 SSO | SSO 适配器框架 | 部分 | 接口框架在；但 Mock 作生产默认 + 任意 username JWT（P1-8）；真实联调待外部（已知） |

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 第一性原理/最小改动 | 是 | 聚焦 platform-auth + partner，未无关重构 |
| 技术栈 | 是 | Spring Security/AOP/MyBatis 范围；TOTP RFC 6238；SM4 复用 |
| 安全基线 | 部分 | SM4 加密、脱敏在；但密钥默认弱、证书校验错误 |
| 可回滚 | 是 | U024 回滚脚本存在 |

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 只实现任务单 A1-A4 | 通过 | 未越界到其他批次 |
| 不改 .env/密钥/k8s-prod | 通过 | 无敏感文件改动 |
| 不改既有 SQL 迁移 | 通过 | V024 新版本，不改既有 |
| 不改 docs/tasks/reviews | 通过 | 仅代码 + DDL + 测试 |
| 最小改动 | 通过 | 未无关重构 |
| 补充测试 | 通过 | 37+35 测试通过，83.93% 覆盖（但核心攻击路径未测，见 §8） |
| 未执行生产/外部操作 | 通过 | Codex 自评诚实，声明待返工草稿 |

## 7. 测试检查

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `mvn test -pl platform-auth` | 是（Codex） | 37 通过，0 失败 | happy path 覆盖 |
| `mvn test -pl platform-partner -am` | 是（Codex） | 35 通过，0 失败 | own-vs-other 越权 + 脱敏 |
| JaCoCo 行覆盖率 | 是 | 83.93% | **覆盖的是 happy path 与非安全代码** |
| Flyway V001-V024 | 是 | 通过 | 迁移执行 |

**未覆盖的核心攻击路径**（子代理核实）：并发重放、CA 证书被拒、默认 sm4Key、SSO 任意 username 伪造、无 PoP、降权后证书权限残留、路由/白名单不可达、configureQuota 自改配额、list 全量枚举。**覆盖率数字不构成安全证据。**

## 8. 对抗式审查（Adversarial Review）

### 8.1 攻击面枚举

安全认证代码，攻击面：①MFA 流程绕过/重放；②ABAC 切面绕过/越权；③证书认证伪造/绕过；④SSO 伪造/接管；⑤密钥管理；⑥路由可达性；⑦权限撤销；⑧并发；⑨测试覆盖真实性。

### 8.2 存活 P1 阻断项（8 项）

| # | 缺陷 | 位置 | 事实链 | 严重级 |
|---|---|---|---|---|
| P1-1 | MFA/证书/SSO state 内存存储 + V024 死代码 | `AdvancedAuthService:29-32` | ConcurrentHashMap 存 mfa/challenges/certificates/ssoStates，无 JdbcTemplate，从不落库；V024 表定义但全代码库无读写。重启 -> mfa_lastCounter 丢失 -> TOTP 重放防护失效；证书绑定/SSO state 全丢。Flyway 标记已执行给人"持久化就绪"假象 | P1 |
| P1-2 | 证书 verify 逻辑反转 | `AdvancedAuthService:167` | `certificate.verify(certificate.getPublicKey())` 用证书自身公钥验签 = 只接受自签名；CA 签发证书 verify(自身公钥) 抛 CertificateException -> 合法 CA 证书被拒。与"机构信任链"需求相反，无 TrustStore/CA 加载（openssl 实证） | P1 |
| P1-3 | sm4Key 默认弱密钥 | `AdvancedAuthService:35` | `@Value("...:change-me-in-env")` 默认值；`Sm4Util.normalizeKey` SHA-256 截 16 字节 -> 确定性已知密钥。未配置时所有 MFA secret 用已知弱密钥加密 = 等同明文。无 fail-fast | P1 |
| P1-4 | auth 路由未加 + 白名单缺失 | Gateway `application.yml:19`；`JwtAuthFilter:28-33`；`CommonSecurityConfiguration:37` | Gateway 谓词 `Path=/auth/**,...` 不含 `/api/v1/auth/**` -> 网关 404；JwtAuthFilter 白名单仅 `/auth/**`，过滤器注册到 `/*` -> 直接访问 `/api/v1/auth/login/mfa`、`/login/cert`、`/sso/redirect`、`/sso/callback` 401。整个 MFA/Cert/SSO 流程部署态不可达 | P1 |
| P1-5 | ABAC configureQuota 自改配额 | `PartnerAbacAspect:25-36`；`ConsumerController:52-56`；`ConsumerService:81-95` | configureQuota 是写操作设 maxRequests/warnThreshold；consumerOwnership 对其应用"非管理员仅访问自己" -> 消费方可把自己的 maxRequests 设 Long.MAX_VALUE -> 配额/用量上限绕过，影响计费 | P1 |
| P1-N1 | 证书登录无私钥 PoP | `AdvancedAuthController:38-41`；`AdvancedAuthService:120-126` | certLogin 只接收 PEM（公开证书），无 challenge/签名验证调用方持私钥。任何获得 bound 证书 PEM 者（日志/备份/无 TLS MITM）可登录该用户 | P1 |
| P1-N2 | 证书绑定权限快照 -> 撤销绕过 | `AdvancedAuthService:104,125` | bindCertificate 存 `principal.permissions()` 快照；certificateLogin 用快照颁 JWT，不回查当前权限。管理员降权/撤销后仍可凭证书持续获旧权限（直到显式吊销证书） | P1 |
| P1-8 | SSO Mock 生产默认 + 任意 username JWT | `AdvancedAuthService:38`；`MockSsoAdapter:18-22`；`AdvancedAuthService:134-138` | 生产构造器硬编码 `new MockSsoAdapter`；callback 把 `code` 直接当 username（`code.startsWith("mock:")?...:code`）；ssoCallback 颁发 `jwtUtil.issue(username, Set.of(), 3600)`。攻击链：`GET /sso/redirect` 取 state -> `GET /sso/callback?code=admin&state=...` -> admin JWT -> `/mfa/bind` 对 admin 绑 MFA -> 账号接管。当前因 P1-4 不可达，但一旦 P1-4 整段加白即激活 | P1 |

### 8.3 存活 P2/P3 项

| # | 缺陷 | 位置 | 严重级 |
|---|---|---|---|
| P2-7 | MFA 可选未强制；且 cert/SSO 登录不经 `MfaLoginEnforcementAspect`，恒单因素 | `MfaLoginEnforcementAspect:16,20` | P2 合规 |
| P2-N3 | MFA 重放检查用 synchronized 块外捕获的陈旧 binding 引用，并发下同一 TOTP 可被两 challenge 消费 | `AdvancedAuthService:61,66-69` | P2 |
| P2-N4 | ABAC 未覆盖 `ConsumerController.list`，非管理员可全量枚举消费方 code/name/bizLine/status | `PartnerAbacAspect:25-28` vs `ConsumerController:38-44` | P2 |
| P3-CN | CN `contains("CN="+username)` 子串匹配；certificateLogin expectedUsername=null 跳过 CN，不产生越权，仅弱化绑定校验 | `AdvancedAuthService:168,122` | P3 |
| P3-N5a | completeChallenge 验码前 `challenges.remove` -> 截获 challengeId 者可一次错误猜测耗尽 challenge（DoS） | `AdvancedAuthService:58` | P3 |
| P3-N5b | MfaLoginEnforcementAspect 先 proceed（已颁 JWT + AuditLogger.record("login")）再判 MFA -> MFA 未完成仍审计为成功登录 | `MfaLoginEnforcementAspect:19` | P3 审计失真 |
| P3-N5c | SSO callback 为 GET，code/state 走 URL -> 代理/访问日志可能记录 | `AdvancedAuthController:64-67` | P3 |
| P3-NPE | ~~ABAC consumers.find(id) NPE~~ **已反驳**：ConsumerService.find 抛 CONSUMER-404，不返回 null。Claude Code 初判 NPE，子代理追代码反驳，纠正。遗留：不存在 id 返 404、存在但不属己返 403 -> 可枚举资源存在性（P3 信息泄露） | `ConsumerService:134-140` | P3 |

### 8.4 对"建议通过"的反驳

- 8 项 P1 中 P1-2/P1-3/P1-4/P1-8 为一经部署即激活或可被直接利用的阻断项；P1-1 致重启后重放防护失效；P1-5 致配额绕过；P1-N1/N2 致证书登录失真。
- 核心攻击路径（并发重放、CA 被拒、默认 sm4Key、SSO 伪造、无 PoP、降权残留、路由不可达、configureQuota 自改）**无测试覆盖**。
- **结论：需返工**。按 CLAUDE.md §7.1 第 5 条，存在存活 P1 阻断项，不得通过。

## 9. 安全与风险检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 敏感信息泄露 | 部分通过 | 无硬编码密钥明文；但 sm4Key 默认弱密钥（P1-3）、日志/审计时序问题（P3-N5） |
| 认证绕过 | 不通过 | SSO 任意 username（P1-8）、证书无 PoP（P1-N1）、MFA 重启重放（P1-1） |
| 越权 | 不通过 | configureQuota 自改配额（P1-5）、list 全量枚举（P2-N4）、权限快照撤销失效（P1-N2） |
| 加密 | 部分通过 | SM4 加密在；但密钥弱（P1-3）、证书 verify 错误（P1-2） |
| 虚构生产结论 | 通过 | Codex 诚实声明待返工草稿，G-S01 仍 BLOCKED |

## 10. 审查结论

```text
✗ 需返工 - 8 项存活 P1 阻断项（P1-1/2/3/4/5/8 + P1-N1/N2）+ P2/P3 改进项
- 总体状态仍 NOT_READY / BLOCKED；G-S01 仍 BLOCKED
- Codex 自评诚实（声明待返工草稿），但代码不可作为完成交付
```

**重要口径**：即便返工通过，G-S01 仍须 PV-SEC PROD_EQ 验证 + 安全负责人复核方可解锁；A4 真实联调待机构 IAM/SSO 规范。不签发正式上线批准。

## 11. 返工任务清单

### P1 阻断（必修）

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| R-1 | P1-1 内存存储 + V024 死代码 | MFA/证书/SSO state 落库：接入 V024 表（JdbcTemplate/Repository 读写 mfa_secret_cipher/mfa_last_counter/t_user_certificate），重启不丢 lastCounter；或回退删除 V024 避免死迁移假象 | 高 |
| R-2 | P1-2 证书 verify 反转 | 移除 `verify(ownPublicKey)`；实现真实证书链校验（TrustStore/CA 公钥加载），支持 CA 签发证书；补 CA 证书正反例测试 | 高 |
| R-3 | P1-3 sm4Key 默认弱密钥 | sm4Key 强制环境注入（无默认值）+ fail-fast（未配置禁用启动）；application.yml 不写明文密钥 | 高 |
| R-4 | P1-4 路由 + 白名单 | Gateway 加 `/api/v1/auth/**` 路由；JwtAuthFilter **仅**对公开端点（login/mfa、login/cert、sso/redirect、sso/callback）加白，bind/confirm/unbind/revoke/rotate 仍需鉴权（不得整段加白，防 P1-8 激活） | 高 |
| R-5 | P1-5 configureQuota 越权 | configureQuota 从 consumerOwnership 切面移除，或对写操作强制 administrative（system:update）；补消费方自改配额被拒测试 | 高 |
| R-6 | P1-N1 证书无 PoP | certLogin 增加 challenge/签名验证调用方持私钥（私钥占有证明）；补无 PoP 拒绝测试 | 高 |
| R-7 | P1-N2 权限快照 | certificateLogin 回查用户当前权限（不使用 bind 时快照）；补降权后证书权限失效测试 | 高 |
| R-8 | P1-8 SSO Mock 生产默认 | MockSsoAdapter 不得作为生产默认装配：加 `@ConditionalOnProperty`/Profile 开关，生产降级为仅测试或抛异常；修复 R-4 时不得整段加白 | 高 |

### P2/P3 改进

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| R-9 | P2-7 MFA 可选未强制 | 按等保三级要求评估强制 MFA 策略；cert/SSO 登录纳入 MFA 强制切面 | 中 |
| R-10 | P2-N3 并发重放 | completeChallenge 在 synchronized 块内重读 map 或用 ConcurrentHashMap.merge 原子更新 lastCounter；补并发重放测试 | 中 |
| R-11 | P2-N4 list 越权 | ConsumerController.list 加 ABAC 过滤（非管理员仅返回自己的）；或限制返回字段 | 中 |
| R-12 | P3-CN | CN 精确匹配（X500Principal 解析 CN，equals 而非 contains） | 低 |
| R-13 | P3-N5a | completeChallenge 验码通过后再 remove challenge，防 DoS 耗尽 | 低 |
| R-14 | P3-N5b | MfaLoginEnforcementAspect 调整审计时序：MFA 完成后再审计登录成功 | 低 |
| R-15 | P3-N5c | SSO callback 改 POST，code/state 走 body | 低 |
| R-16 | P3 信息泄露 | 统一不存在/无权限返回（防资源枚举） | 低 |

## 12. 建议提交信息（返工通过后使用；当前结论为需返工，不应提交）

```text
feat(fix-01): security auth - MFA/ABAC/cert/SSO with hardening

- A1 MFA: RFC6238 TOTP, SM4-encrypted secret, replay check (persisted)
- A2 ABAC: resource ownership + field masking (configureQuota hardened)
- A3 cert: CA chain validation, private-key PoP, live permission lookup
- A4 SSO: adapter framework (mock non-production)
- hardening: persisted MFA state, fail-fast sm4Key, gateway routing,
  cert PoP, permission refresh, mock-SSO gated
- tests: attack-path coverage (replay/CA/PoP/privilege/replay-concurrent)
- G-S01 still BLOCKED; overall NOT_READY (not approval)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 附：审查方法说明

- Claude Code 直接读 6 个核心文件（Totp/V024/AdvancedAuthService/PartnerAbacAspect/MfaLoginEnforcementAspect/AdvancedAuthController/MockSsoAdapter）逐项追踪。
- 独立对抗式子代理 fix01-adversary 全面审查 13 文件 + openssl 实证 + 测试覆盖评估，回传结构化结论（含对 Claude Code NPE 判断的反驳纠正）。
- 结论为双方交叉验证后存活的缺陷。未实测项（mvn 实跑）已标注。
