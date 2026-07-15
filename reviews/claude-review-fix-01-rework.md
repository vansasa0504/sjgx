# Claude Code 返工复审结果 - 批次1 安全认证

> 审查阶段：批次1 返工复审（R-1~R-16）
> 审查日期：2026-07-15
> 审查范围：`ai/fix-01-security-auth` 分支，6 tracked modified + 19 未跟踪（含 6 新增类）
> 返工任务单：`tasks/codex-task-fix-01-rework.md`
> 原审查：`reviews/claude-review-fix-01.md`（8 项 P1）
> 审查方：Claude Code（主控）+ 独立对抗式子代理 fix01-rework-adversary（已回传结论）

---

## 1. 审查对象

**Tracked modified（6）**：`platform-auth/pom.xml`、`AuthService.java`、`application.yml`、`application-test.yml`、`JwtAuthFilter.java`、`platform-gateway/application.yml`

**未跟踪新增/修改（19）**：原 13 修改 + 6 新增（`AdvancedAuthRepository`、`CertificateTrustValidator`、`DisabledSsoAdapter`、`SsoConfiguration`、`AdvancedAuthEndpointSecurityTest`、`CertificateFixtures`、`PartnerAbacAttackPathTest`）

## 2. Git 状态

```text
分支：ai/fix-01-security-auth
未提交、未暂存；git diff --check -> 退出码 0
未动 .claude/worktrees/
实测：mvn test -pl platform-auth -> AdvancedAuthServiceTest 9/9、AdvancedAuthEndpointSecurityTest 3/3 全过，无回归
```

## 3. R-1~R-12 修复核验

| 项 | 状态 | 说明 |
|---|---|---|
| R-1 落库 | 部分通过 | MFA/cert 状态走 `AdvancedAuthRepository`+DB（t_user/t_user_certificate），重启不丢；**但 challenge 改无状态时引入 P1-RR1（JWT 嵌入）** |
| R-2 证书链 | 通过（单层） | `CertificateTrustValidator` PKIX+TrustStore+CRL，移除 `verify(ownPublicKey)`；CA 通过/自签名/过期/吊销拒。多层 CA 见 P2-3 |
| R-3 sm4Key | 通过 | 无默认 + 构造器 fail-fast（null/blank/"change-me-in-env" 抛 IllegalStateException，测试覆盖） |
| R-4 路由+白名单 | 通过 | Gateway 加 `/api/v1/auth/**`；JwtAuthFilter 仅 4 公开端点加白，bind 等仍鉴权（测试覆盖） |
| R-5 configureQuota | 通过 | 独立 `quotaAdministration` 切面强制 `system:update`，消费方自改 403（测试覆盖） |
| R-6 PoP | 通过 | certificateLogin 需 challengeId+signature，`verifyProof` 用证书公钥验签 |
| R-7 权限回查 | 通过 | `authService.permissionsFor` 实时查，CertificateState 无 permissions 快照 |
| R-8 SSO Mock | 通过（含 P2-1） | `SsoConfiguration` 默认 `DisabledSsoAdapter`（抛 AUTH-503/401）；MockSsoAdapter 固定 `sso-test-user`，不再信任 code 为 username。残留：env 可覆盖 mock-enabled |
| R-9 MFA 强制 | **被 P1-RR1 击败** | `requireMfa` 对 cert/SSO 未启用抛 AUTH-403（正确）；但 JWT 嵌入 challenge 致强制可绕过 |
| R-10 并发重放 | 通过 | `advanceCounter` SQL `WHERE username=? AND mfa_enabled=1 AND mfa_last_counter<?` 返回==1，DB 级原子（并发测试覆盖） |
| R-11 list 越权 | 通过 | `consumerList` 切面非管理员过滤为自己（测试覆盖） |
| R-12 CN 精确 | 通过 | `LdapName` 解析 CN，`equals` |

**R-2/3/4/5/6/7/8/10/11/12 实测通过；R-1 部分通过；R-9 被 P1 击败。**

## 4. 对抗式审查（核心）

### 4.1 存活 P1-RR1：MFA 绕过 - JWT 明文嵌入无状态 challenge（返工引入的回归）

**位置**：`AdvancedAuthService.java:71-75`（startChallenge）、`:194-198`（requireMfa）、`:212-229`（verifyChallenge）；`MfaLoginEnforcementAspect.java:19-23`

**事实链**（Claude Code + 子代理双重确认，追代码实证）：
- `requireMfa`/`MfaLoginEnforcementAspect` 先 `jwtUtil.issue(username, permissions, 3600)` 颁发**最终可用 JWT**（JwtUtil.parse 直接校验通过，无 "MFA pending" claim），再 `startChallenge(username, token)` 包装
- `startChallenge`：`payload = encode(username) + "." + encode(token) + "." + expires + "." + uuid`，返回 `payload + "." + sign(payload)`
- `encode(token) = Base64.getUrlEncoder().withoutPadding()`（base64url 明文嵌 parts[1]）
- `sign` 是 **HMAC-SHA256（MAC，非加密）**，只防篡改不防读取

**攻击路径**（追代码证实，非推测）：
1. 口令登录：钓鱼者有口令 -> `POST /auth/login` -> 颁 JWT -> MfaLoginEnforcementAspect 包成 challenge
2. 证书登录：持证书私钥者 -> `/api/v1/auth/login/cert`（PoP 通过）-> requireMfa 颁 JWT 包入 challenge
3. SSO 登录：ssoCallback -> requireMfa 同上
- 任意一种，客户端拿到 challenge 后：`challenge.split("\\.")[1]` -> `Base64.getUrlDecoder().decode` -> 得完整权限 JWT（3600s）-> 直接调受保护接口。**MFA 完全绕过**，R-9 强制形同虚设

**为何是返工引入的回归**：原实现（审查 P1-1/P3-N5a）用 `ConcurrentHashMap challenges`（服务端 challengeId->条目含 token），`completeChallenge` 走 `challenges.remove`--token 不出服务端。返工 R-1 选择"challenge 改无状态签名"以"重启不丢"，把 token 嵌入 challenge 返回客户端。**重启安全换来了 MFA 绕过**。当前 AdvancedAuthService 字段（line 36-37）已无 MFA challenges map，佐证改动。

**未实测**：子代理未写复现测试（只读）；surefire 现有用例均走 completeChallenge 取 token，无一例测"不解 MFA 直提 token"。

### 4.2 相邻预存在 P1-RR2：JWT_SECRET 默认弱密钥

**位置**：`platform-auth/src/main/resources/application.yml:29` -- `security.jwt.secret: ${JWT_SECRET:change-me-in-env}`

- 默认值 `change-me-in-env` 为已知串。部署未设 `JWT_SECRET` 时，JwtUtil 用此串 HMAC -> 攻击者已知密钥，可伪造任意权限 JWT（含 admin）
- git diff 证实此行返工**未触碰**（返工仅在其后追加 mfa/cert/sso 段），属预存在
- R-3 对 sm4Key 做了"无默认+fail-fast"，JWT secret 仍弱默认，硬化不一致

## 5. P2/P3 改进项

| # | 缺陷 | 位置 | 严重级 |
|---|---|---|---|
| P2-1 | SSO `mock-enabled` 可被环境变量覆盖（`SECURITY_SSO_MOCK_ENABLED=true`），无 @Profile/fail-fast；生产误设即激活 MockSsoAdapter（固定 `sso-test-user`，`test-valid-code`） | `SsoConfiguration:9-14`、`MockSsoAdapter:23` | P2 |
| P2-2 | 证书吊销默认不校验：`setRevocationEnabled(false)`，仅 CERT_CRL 显式配置时手动校验；OCSP 未实现；默认空 -> 吊销证书可登录 | `CertificateTrustValidator:53-58`、`application.yml:36` | P2 |
| P2-3 | 多层 CA 链不支持：`generateCertPath(List.of(certificate))` 仅放叶子；parse 单证书；root->intermediate->user 模式 PKIX 找不到路径，**所有合法证书被拒** | `CertificateTrustValidator:55`、`AdvancedAuthService.parse:259` | P2 |
| P3-1 | sm4Key 复用为 SM4 加密 + challenge HMAC 签名（不同派生），违反密钥分离 | `Service:234` | P3 |
| P3-2 | verifyProof 仅 RSA/EC；EdDSA 证书 fail-closed | `Service:244` | P3 |
| P3-3 | bindCertificate 用 sm4Key 加密公开 PEM，无害但无意义 | `Service:132` | P3 |
| P3-4 | certificateLogin 返回 `"MFA_REQUIRED:" + challenge` 字符串前缀契约，弱接口 | `Service:197` | P3 |
| P3-5 | consumerList 先 proceed 全量再过滤，性能（非泄露） | `Aspect:53` | P3 |
| P3-6 | AdvancedAuthRepository jdbc==null 静默回退内存 HashMap，misconfig 时静默退化为非持久化，无 fail-fast | `Repository:26,35,57` | P3 |
| P3-7 | AuthService.permissionsFor 与 AdvancedAuthRepository.permissions 重复，后者未调用 | `AuthService:84`、`Repository:106` | P3 |
| P3-8 | 审计时序（R-14 未做）：MfaLoginEnforcementAspect 先 proceed（已 AuditLogger.record("login")）再判 MFA，MFA 未完成仍记成功 | `MfaLoginEnforcementAspect:19` | P3 |
| P3-9 | GET /login/cert?fingerprint= 泄露绑定存在性（R-16） | Controller | P3 |

## 6. 安全与风险检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| MFA 绕过 | 不通过 | P1-RR1：JWT 嵌入 challenge，可提取绕过 MFA |
| 密钥管理 | 部分通过 | sm4Key 已 fail-fast；但 JWT_SECRET 仍弱默认（P1-RR2） |
| 证书认证 | 部分通过 | 单层 CA + PoP + 权限回查在；但吊销默认不校验（P2-2）、多层 CA 不支持（P2-3） |
| 越权 | 通过 | configureQuota 强制 administrative、list ABAC 过滤（R-5/R-11 修复） |
| 虚构结论 | 通过 | Codex 诚实，G-S01 仍 BLOCKED |

## 7. 审查结论

```text
✗ 需返工（第二轮） - 1 项存活 P1（P1-RR1 MFA 绕过，返工引入的回归）+ 1 项相邻 P1（P1-RR2 JWT_SECRET）+ P2/P3
- R-2/3/4/5/6/7/8/10/11/12 实测通过
- R-1 部分通过（落库在，但 challenge 无状态引入 P1）
- R-9 被 P1-RR1 击败
- G-S01 仍 BLOCKED
```

**理由**：R-9 的 MFA 强制被 P1-RR1（JWT 明文嵌入无状态 challenge）完全绕过-这是 MFA 设计要防御的核心场景（攻击者持第一因素口令/证书私钥，无第二因素）。该缺陷返工自身引入（原服务端 map 不外泄 token），且现有测试无一覆盖此攻击路径。存在存活 P1，按 CLAUDE.md §7.1 不得放行。

**重要口径**：返工修复了首轮 8 项 P1 中的大部分（R-2~R-8/R-10~R-12），质量较高；但 R-1 的"无状态 challenge"设计选择引入新 P1。即便第二轮返工通过，G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核；不签发正式上线批准。

## 8. 返工任务清单（第二轮）

### P1 阻断（必修）

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RR-1 | P1-RR1 MFA 绕过（返工回归） | challenge 不嵌 token：challenge 只含 `{username, expires, nonce}` 签名（HMAC）；`completeChallenge` 验 TOTP 通过后再 `jwtUtil.issue` 颁 token；或服务端存 token + 不透明 challenge ID（Redis/DB）。**补测试**：不解 MFA 直接从 challenge 提取 token 应失败 | 高 |
| RR-2 | P1-RR2 JWT_SECRET 默认弱密钥 | `application.yml` 移除默认 `change-me-in-env`，改 `${JWT_SECRET}`（无默认）+ 启动 fail-fast（同 R-3 sm4Key 模式） | 高 |

### P2 改进（建议同轮）

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RR-3 | P2-1 SSO mock-enabled 可覆盖 | `SsoConfiguration` 加 `@Profile("!prod")` 或生产 profile 下 mock-enabled=true 时 fail-fast | 中 |
| RR-4 | P2-2 证书吊销默认不校验 | 默认启用吊销校验（CRL/OCSP）；或显式标注"待机构 CA 配置"并保持 BLOCKED | 中 |
| RR-5 | P2-3 多层 CA 链 | `generateCertPath` 支持中间证书；`parse`/接口支持客户端发送证书链；补多层 CA 测试 | 中 |

### P3（可选，不阻断）

RR-6 sm4Key 密钥分离（SM4 加密与 HMAC 签名不同密钥派生）；RR-7 verifyProof EdDSA；RR-8 Repository jdbc==null fail-fast；RR-9 permissionsFor 去重；RR-10 审计时序（R-14）；RR-11 GET /login/cert 存在性（R-16）；RR-12 consumerList 性能；RR-13 MFA_REQUIRED 接口契约。

## 9. 建议提交信息（第二轮返工通过后；当前需返工，不应提交）

```text
feat(fix-01): security auth rework - MFA bypass fix + JWT secret hardening

- RR-1: challenge no longer embeds JWT; token issued only after TOTP verify
  (fixes MFA bypass regression introduced by stateless challenge)
- RR-2: JWT_SECRET mandatory env + fail-fast (no weak default)
- RR-3: SSO mock-enabled gated by profile/prod fail-fast
- RR-4: cert revocation enabled by default (CRL/OCSP)
- RR-5: multi-tier CA chain support
- tests: MFA-bypass attack path, multi-tier CA, revocation
- G-S01 still BLOCKED; overall NOT_READY (not approval)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 附：审查方法

- Claude Code 直接读核心文件（AdvancedAuthService/AdvancedAuthRepository/PartnerAbacAspect/tracked diff）逐项追踪
- 独立子代理 fix01-rework-adversary 全面审查 19 文件 + 实测 `mvn test`（surefire 9/9+3/3 通过）+ 追代码证实 P1 攻击链
- 结论为双方交叉验证后存活的缺陷。P1-RR1 攻击路径由代码直读确证（JWT 结构 + challenge 切分逻辑），未写复现测试（只读审查）
