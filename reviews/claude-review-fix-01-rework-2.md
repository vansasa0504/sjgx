# Claude Code 返工复审结果 - 批次1 安全认证（第二轮返工）

> 审查阶段：fix-01 第二轮返工复审（RR-1~RR-5 必修 + RR-6~RR-13 可选）
> 审查日期：2026-07-15
> 审查范围：`ai/fix-01-security-auth` 分支，未提交改动 = 11 tracked modified + 19 untracked（含 12 新增类 + 2 SQL + 3 测试）
> 返工任务单：`tasks/codex-task-fix-01-rework-2.md`
> 前序审查：`reviews/claude-review-fix-01.md`（原始，8 项 P1）、`reviews/claude-review-fix-01-rework.md`（第 1 轮返工，1 项 P1 回归 + 1 相邻 P1）
> 审查方：Claude Code（主控，逐行追踪 + 独立实测）+ 独立对抗式子代理 fix01-rework-2-adversary（已回传结论，双方交叉验证）

---

## 1. 审查对象

**Tracked modified（11）**：
- `platform-common`：`JwtUtil.java`（+fail-fast）、`CommonSecurityConfiguration.java`（`${security.jwt.secret}` 无默认）、`JwtAuthFilter.java`（白名单 +4 公开端点）、`JwtUtilTest.java`（+弱密钥测试）
- `platform-auth`：`pom.xml`（+bcpkix-jdk18on test）、`AuthApplication.java`（移除弱默认 fallback）、`AuthService.java`（提取 `authenticate`/`permissionsFor`）、`application.yml`（jwt/mfa/cert/sso 配置 fail-fast）、`application-test.yml`（测试配置）
- `platform-gateway`：`JwtHeaderFilter.java`（构造器注入 secret）、`application.yml`（+`/api/v1/auth/**` 路由 + `security.jwt.secret`）

**Untracked 新增（19）**：
- `db/migration/V024__security_auth.sql` / `U024__security_auth.sql`（t_user 加 MFA 列 + t_user_certificate 表，可逆）
- `platform-auth`：`AdvancedAuthController`、`AdvancedAuthService`、`AdvancedAuthRepository`、`CertificateTrustValidator`、`Totp`、`SsoAdapter`/`MockSsoAdapter`/`DisabledSsoAdapter`、`SsoConfiguration`、`MfaLoginEnforcementAspect`
- `platform-auth` 测试：`AdvancedAuthServiceTest`（11）、`AdvancedAuthEndpointSecurityTest`（4）、`CertificateFixtures`
- `platform-partner`：`PartnerAbacAspect` + `PartnerAbacAttackPathTest`（2）+ `PartnerAbacMockMvcTest`（2）

## 2. Git 状态

```text
分支：ai/fix-01-security-auth（HEAD: fe92f870）
未提交、未暂存；未动 .claude/worktrees/、.env、证书、k8s/prod
独立实测：
  mvn -pl platform-auth,platform-partner,platform-common -am test
  -> BUILD SUCCESS
  platform-common 40（1 skipped）/ platform-auth 48 / platform-partner 37，0 failures 0 errors
```

> 未发现独立的 Codex 第二轮"阶段完成报告"文件（任务单 §7 要求 7 节输出）。本次审查以 git diff + 测试输出 + 任务单为依据。

## 3. 代码差异摘要

| 类别 | 变更 | 审查意见 |
|---|---|---|
| RR-1 MFA challenge | `startChallenge` 改 4 段 HMAC（username.expires.uuid.sig），不含 token；`completeChallenge` 验 TOTP 后才 `issue`；Aspect 不预颁 token | **核心 P1 回归已修复**，见 §4/§8 |
| RR-2 JWT_SECRET | `JwtUtil` 构造器 fail-fast；4 处配置 `${JWT_SECRET}` 无默认；gateway 同步 | 修复一致，测试覆盖 |
| RR-3 SSO mock | `SsoConfiguration` prod profile + mockEnabled 抛异常；yml 硬编码 `mock-enabled: false` | 部分修复，残留 profile 名硬编码（P2-2） |
| RR-4 证书吊销 | 无 CRL 时 `validate` fail-closed 抛 AUTH-401；`setRevocationEnabled(true)` + 手动 CRL | fail-closed 到位；OCSP 未实现（待机构 CA） |
| RR-5 多层 CA 链 | `parseChain` 多证书；`validate` 移除 trust anchor + `generateCertPath(整链)` | 修复，测试覆盖多层 CA |
| ABAC | `PartnerAbacAspect`：configureQuota 强制 `system:update`、list 非管理员过滤、interfaces 脱敏 | 与第 1 轮一致，测试覆盖 |
| SQL | V024 加列+建表+索引；U024 反向 DROP | 可逆 |

## 4. 需求满足情况（RR-1~RR-5 逐项核验）

| 返工项 | 是否满足 | 核查证据 |
|---|---|---|
| **RR-1** MFA challenge 不嵌 token | **是** | `startChallenge:73-77` payload=`encode(username).expires.UUID`，返回`payload+"."+sign`（4 段）；`completeChallenge:79-89` 先 `verifyChallenge`+`requiredMfa`+`Totp.verify`+`advanceCounter`，**通过后** `:88` 才 `jwtUtil.issue`；`MfaLoginEnforcementAspect:23-27` 改为先 `authenticate`（仅验密码、不 issue）再 `startChallenge`，不再预颁 token。`requireMfa:197-201` 返回 `"MFA_REQUIRED:"+challenge`（challenge 不含 token） |
| **RR-2** JWT_SECRET 强制注入 + fail-fast | **是** | `JwtUtil:18-20` 构造器 null/blank/`"change-me-in-env"` 抛 `IllegalStateException`；`CommonSecurityConfiguration:20` `@Value("${security.jwt.secret}")` 无默认；`application.yml:29` `${JWT_SECRET}` 无默认；`gateway/application.yml` 同步；`AuthApplication` 移除 `change-me-in-env` fallback；`JwtHeaderFilter` 改构造器注入。`JwtUtilTest` `missingOrWeakSecretFailsFastAtStartup` 覆盖 |
| **RR-3** SSO mock-enabled 生产 fail-fast | **部分** | `SsoConfiguration:15-17` `mockEnabled && prod profile` 抛异常；yml `mock-enabled: false` 硬编码。**残留**：仅按字面 `"prod"` 名判断，非 prod 名（production/prd/默认）+ `SECURITY_SSO_MOCK_ENABLED=true` 仍激活 MockSsoAdapter（P2-2） |
| **RR-4** 证书吊销默认校验 | **是（fail-closed）** | `CertificateTrustValidator:68-70` `crls.isEmpty()` 抛 AUTH-401；`:91` `setRevocationEnabled(true)`；`:78-83` 手动 CRL 检查每证书；`:89-90` CRL 注入 CertStore。**风险**：无 CRL 时证书认证全部阻断（用户已知）；OCSP 未实现（待机构 CA 联调） |
| **RR-5** 多层 CA 链支持 | **是** | `parseChain:262-275` `generateCertificates` 解析多 PEM；`validate:72-75` 移除链尾 trust anchor；`:86` `generateCertPath(整链)`；`bindCertificate`/`certificateLogin` 用整链 `validate`、取 `chain.get(0)` 做 CN/fingerprint。测试 `revocationStatusIsFailClosedAndMultiLevelCaChainPasses` 覆盖 root->intermediate->leaf |

> RR-6~RR-13（P3 可选）：RR-6 sm4Key 复用、RR-7 EdDSA、RR-8 jdbc==null 静默回退、RR-9 permissionsFor 重复、RR-10 审计时序、RR-11 GET /login/cert 存在性、RR-12 consumerList 性能、RR-13 MFA_REQUIRED 契约——**均未修复**，与返工单"可选不阻断"一致，见 §8 P3 项。

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 最小可行结果（修复 RR-1 回归 + RR-2 P1） | 符合 | 核心目标达成，未引入新 P1 |
| 最小改动 | 符合 | 仅触碰 auth/common/gateway/db，未改无关模块 |
| 避免过度设计 | 符合 | challenge 用 HMAC 无状态，未引入 Redis/新框架 |
| 可回滚 | 符合 | V024/U024 可逆；改动集中在新增类 + 配置 fail-fast |
| 配置外置 | 符合 | jwt/mfa/cert/sso 均占位符，无硬编码密钥 |

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 是否修改敏感文件 | 通过 | 无 .env/证书/k8s-prod；truststore 路径用 `${CERT_TRUSTSTORE}` 占位 |
| 是否修改 docs/tasks/reviews | 通过 | 仅本审查文件（Claude 职责）；未改 docs/tasks |
| 是否引入大型依赖 | 通过 | 仅 `bcpkix-jdk18on`（test scope，证书夹具用），无生产大型依赖 |
| 是否无关重构 | 通过 | `AuthService.authenticate` 提取是为 RR-1 复用，必要 |
| 是否超出 RR-1~RR-13 | 通过 | 改动均在返工单范围 |
| 既有已执行迁移 V001-V023 | 通过 | 仅新增 V024/U024，未改历史 |

## 7. 测试检查（独立实测，非仅信报告）

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `mvn -pl platform-auth,platform-partner,platform-common -am test` | 是 | **BUILD SUCCESS** | common 40（1 skipped）/ auth 48 / partner 37，0 failures |
| `AdvancedAuthServiceTest`（11） | 是 | 通过 | 含 `mfaChallengeCannotBeDecodedIntoBearerTokenWithoutSecondFactor`（攻击路径）、`restartWithJdbcStillRejectsOldTotpReplay`（重启防重放）、`concurrentUseOfSameTotpAllowsOnlyOne`（并发）、`revocationStatusIsFailClosedAndMultiLevelCaChainPasses`（RR-4/RR-5）、`missingOrWeakSm4KeyFailsFast`、`productionSsoRejectsArbitraryUsernameCodeAndMockConfiguration` |
| `AdvancedAuthEndpointSecurityTest`（4） | 是 | 通过 | 端到端 `passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp`：断言 challenge 4 段、不含 enrollmentToken、`jwt.parse(challenge)` 抛异常、`decode(parts[1])` 非 token、completeChallenge+TOTP 拿 token |
| `PartnerAbacAttackPathTest`/`PartnerAbacMockMvcTest` | 是 | 通过 | 配额越权 403、list 仅自身、cert 越权 403、credential 脱敏 `****` |
| Flyway V024 应用 | 是 | 通过 | `Successfully applied 24 migrations ... now at version v024` |

> **攻击路径覆盖**（RR-1 必补）：service 层 + endpoint 层双测试均断言 challenge 不含 token、parts[1] 非 JWT、jwt.parse 失败。覆盖到位。

## 8. 安全与风险检查（对抗式审查专节）

### 8.1 攻击面枚举

| 攻击面 | 入口 | 处理点 |
|---|---|---|
| MFA challenge | `/auth/login`（Aspect）、`/api/v1/auth/login/cert`、`/api/v1/auth/sso/callback` | `startChallenge`/`completeChallenge`/`verifyChallenge`/`sign` |
| MFA 绑定/解绑 | `/api/v1/auth/mfa/{bind,confirm,unbind}`（需 JWT） | `beginMfaBinding`/`confirmMfaBinding`/`unbindMfa` |
| 证书登录 | `/api/v1/auth/login/cert`（白名单免 JWT） | `certificateChallenge`/`certificateLogin`/`verifyProof` |
| SSO | `/api/v1/auth/sso/{redirect,callback}`（白名单） | `ssoRedirect`/`ssoCallback`/`SsoConfiguration` |
| 证书校验 | cert login/bind | `CertificateTrustValidator.validate` |
| 越权 | consumer detail/audit/logs/list/quota、partner interfaces | `PartnerAbacAspect` |
| 密钥 | sm4Key（SM4 加密 + HMAC 签名）、JWT_SECRET | 构造器 fail-fast |

### 8.2 构造反例及追踪结果

| 反例 | 追踪结果 | 存活？ |
|---|---|---|
| challenge `parts[1]` base64url 解码得 JWT（第 1 轮 P1-RR1 攻击） | **已反驳**：`parts[1]` 现为 `expires` 纯数字串，`jwt.parse(decode(parts[1]))` 必抛 AUTH-401（无 `.` 分隔的合法 JWT 结构）；测试双覆盖 | 否 |
| 伪造 challenge 换 token 无需 TOTP | **已反驳**：`completeChallenge:82-87` 必先 `Totp.verify`+`advanceCounter`；伪造 challenge 需 sm4Key（HMAC），且仍需 TOTP | 否 |
| 用 A 的 challenge 拿 B 权限 | **已反驳**：`:88` token 权限来自 `authService.permissionsFor(payload.username())`（DB 实时查）；username 由 HMAC 不可篡改 | 否 |
| CRL 缺失绕过吊销 | **已反驳**：`:68-70` `crls.isEmpty()` fail-closed；手动 CRL + `setRevocationEnabled(true)` + CertStore 三重 | 否 |
| base64url 注入 split | **已反驳**：base64url 字符集不含 `.`，UUID 含 `-` 不含 `.`，expires 纯数字，`split("\\.")` 严格 4 段 | 否 |
| `advanceCounter` 并发竞态 | **已反驳**：DB `UPDATE ... WHERE mfa_enabled=1 AND mfa_last_counter<?` 原子；内存 `synchronized`；测试覆盖 | 否 |
| `MessageDigest.isEqual` 时序 | **已反驳**：常数时间比较 | 否 |
| 整数溢出 expires | **已反驳**：`Long.parseLong` 越界抛 NumberFormatException->AUTH-401 | 否 |
| `isTrustAnchor` 移除逻辑被滥用 | **已反驳**：trust store 配置态，攻击者无法注入；空 store 让 PKIX 抛异常 fail-closed | 否 |
| cert/SSO 登录越权 | **已反驳**：cert username 来自 `binding.username()`（DB 按 fingerprint）；SSO 不信任 code 作 username（R-8） | 否 |

### 8.3 存活缺陷

#### P2-1【P2·改进】`unbindMfa` 不推进 TOTP counter（MFA 解绑重放）

- **位置**：`AdvancedAuthService.java:109-117`（`unbindMfa`），对比 `completeChallenge:85`（`advanceCounter`）、`confirmMfaBinding:105-106`（写 `lastCounter`）
- **事实链**（Claude + 子代理双重确认，逐行实证）：`unbindMfa` 调 `Totp.verify` 后仅判 `< 0`，**既不检查 `counter > lastCounter`，也不调 `advanceCounter`**。它是三个 TOTP 验证点中唯一不推进 counter 的。
- **攻击路径**：
  1. 攻击者获取受害者一次有效 JWT（须先完成 completeChallenge，即有过一次实时 TOTP 能力）；
  2. 截获受害者一次 TOTP code（即使该 code 已被 completeChallenge 消费，因 unbind 不查 lastCounter）；
  3. 在 code 仍处 ±1 窗口内（≤90s）调 `POST /api/v1/auth/mfa/unbind`（Bearer JWT）-> `Totp.verify` 通过 -> `clearMfa` 解绑；
  4. 受害者 JWT 过期后，攻击者凭第一因素密码 `POST /auth/login` -> `mfaEnabled==false` -> `joinPoint.proceed()` 直接颁 token，**MFA 被永久绕过**。
- **严重级评估**：P2（非 P1）。攻击前提较强（需 JWT + 截获 code + 密码，且 JWT 须先完成 MFA），不是"无第二因素直接绕过"。但 `advanceCounter` 重放防护在解绑场景失效，将一次性第二因素接触升级为永久 MFA 移除，削弱 MFA 持续性。
- **可复现性**：代码路径明确（`unbindMfa` 无 `advanceCounter` 调用，逐行确认），未写运行时复现测试（只读审查）。
- **修复建议**：`unbindMfa` 在 `Totp.verify` 后调 `repository.advanceCounter(username, counter)` 并校验返回 true（与 completeChallenge 一致），或显式检查 `counter > binding.lastCounter()`。改动约 3 行。

#### P2-2【P2·改进】SSO mock-enabled 仅按字面 "prod" profile 名 fail-fast

- **位置**：`SsoConfiguration.java:15`
- **事实链**：`if (mockEnabled && activeProfiles.contains("prod"))` 仅匹配字符串 `"prod"`。生产 profile 名为 `production`/`prd`/默认 + 环境变量 `SECURITY_SSO_MOCK_ENABLED=true`（误配/注入）时，MockSsoAdapter 被激活（固定 `sso-test-user`）。
- **缓解**：`requireMfa("sso-test-user")` 会因未绑 MFA 抛 AUTH-403 或 `permissionsFor` 因用户不存在抛 AUTH-401，故不直接颁 token；但 mock 路径在生产被激活本身是防护失效。
- **严重级**：P2（RR-3 部分修复残留）。
- **修复建议**：改为默认拒绝（白名单允许的 profile 才激活 mock），或显式列出生产 profile 名集合。

#### P3 项（建议上线前/专项，不阻断）

| # | 缺陷 | 位置 | 对应返工项 |
|---|---|---|---|
| P3-1 | challenge 非一次性消费（无状态 HMAC 固有，300s 内可多次换 token，需递增 TOTP counter；危害有限，需 TOTP secret） | `AdvancedAuthService:73-89` | 设计选择 |
| P3-2 | `requiredMfa` 不显式检查 `enabled`（仅查 binding 非空）；内存 fallback 路径 `advanceCounter` 无 `mfa_enabled=1` 守卫，未确认绑定用户可在内存模式换 token（生产 DB 路径有守卫，不受影响） | `:203-207`、`Repository:55-61` | RR-8 相关 |
| P3-3 | sm4Key 复用（SM4 加密 + HMAC 签名同密钥）；sm4Key 泄露 + DB 读访问可解密 MFA secret->伪造 challenge->绕过 MFA | `:234-243`、`Sm4Util` | RR-6 |
| P3-4 | `certificateChallenges.remove(challengeId, challenge)` 返回值未检查；需私钥且仍受 advanceCounter 约束 | `:181` | 新发现 |
| P3-5 | `/auth/**` 白名单过宽：`GET /auth/all-permissions` 免 JWT 枚举全量权限码（31 个）；`/auth/refresh`、`/auth/permissions` 无 Authorization 时 `parse(null)` -> NPE -> 500（**预存在**，非返工引入） | `JwtAuthFilter:29`、`AuthController:44-47` | 预存在 |
| P3-6 | 多实例部署 `ssoStates`/`certificateChallenges` 内存 map 不共享，SSO/cert 登录多实例下随机失败 | `:38-39` | 部署风险 |
| P3-7 | 审计时序：`authenticate:65` 内 `AuditLogger.record("login")` 在 MFA 完成前触发，MFA 未完成仍记登录成功 | `AuthService:65`、`MfaLoginEnforcementAspect:24` | RR-10 |
| P3-8 | `rotateCertificate` 非原子（revoke 后 bind 失败则旧证废新证未立）；`verifyProof` 不支持 EdDSA（fail-closed 不构成绕过） | `:149-152`、`:247-248` | RR-7 |
| P3-9 | `AdvancedAuthRepository.permissions:106` 与 `AuthService.permissionsFor:88` 重复，前者未被调用（死代码） | 两处 | RR-9 |
| P3-10 | `consumerList` 先 proceed 全量再过滤（性能，非泄露） | `PartnerAbacAspect:53` | RR-12 |

### 8.4 已知风险（用户列明，代码已正确处理为 fail-closed/待外部）

| 风险 | 代码处理 | 状态 |
|---|---|---|
| 机构 CRL/OCSP 未完成真实 CA 联调；无 CRL 时阻断证书认证 | `CertificateTrustValidator:68-70` fail-closed 抛 AUTH-401 | **已知并接受**：生产部署必须配置机构 CA 的 CRL（`${CERT_CRL}`），否则证书认证全部阻断。OCSP 未实现，待机构 CA 联调。属设计正确（fail-closed），非缺陷 |
| 真实 IAM/SSO 联调依赖外部规范 | `DisabledSsoAdapter` 默认抛 AUTH-503；`MockSsoAdapter` 仅非 prod + 显式启用 | **已知**：A4 真实联调待机构 IAM/SSO 规范（RQ-01） |
| G-S01 仍 BLOCKED | 见 §9 门禁口径 | **不变**：代码实现不解锁门禁 |

### 8.5 安全正向项

- RR-1 MFA challenge 四段 HMAC 不含 token，攻击路径代码+测试双覆盖
- RR-2 JWT_SECRET/sm4Key/truststore 三处 fail-fast，无弱默认
- RR-4 证书吊销 fail-closed + PKIX `setRevocationEnabled(true)` + 手动 CRL 三重
- RR-5 多层 CA 链支持，trust anchor 移除正确
- `advanceCounter` DB 原子条件更新（completeChallenge/confirmMfaBinding 路径）+ 并发测试
- ABAC：configureQuota 强制 `system:update`、list 非管理员过滤、interfaces credential 脱敏
- credential 仍 Sm4Util 加密存储；密码 BCrypt；TOTP secret SM4 加密

## 9. 审查结论

```text
✓ 通过（有条件） - 核心返工目标 RR-1~RR-5 达成，无存活 P1 阻断项
- RR-1（第 1 轮 P1 回归 MFA 绕过）真修复，challenge 不嵌 token，代码+测试双证实
- RR-2 JWT_SECRET fail-fast 一致，测试覆盖
- RR-3/RR-4/RR-5 均落实（RR-3 有 P2-2 残留，RR-4 fail-closed 到位但 OCSP 待联调）
- 测试独立实测全绿：common 40 + auth 48 + partner 37
- G-S01 仍 BLOCKED；本审查通过不代表生产放行
```

**理由**：第二轮返工的核心目标——修复第 1 轮引入的 P1-RR1 MFA 绕过回归——已正确达成。challenge 现为 `encode(username).expires.uuid.hmac` 四段无状态 HMAC，全程不含 token；`completeChallenge` 验 TOTP + `advanceCounter` 防重放后才 `jwtUtil.issue`；`MfaLoginEnforcementAspect` 不再预颁 token。子代理与主控独立逐行追踪 + 10 条反例全部反驳 + 攻击路径测试双覆盖（service/endpoint），**未发现存活 P1**，未引入新 P1 回归。RR-2（相邻 P1）亦一致修复。

**存活 P2 两项**（不阻断，建议合并前/上线前）：
- P2-1 `unbindMfa` 不推进 counter（MFA 解绑重放，可永久移除 MFA）。**因涉及 MFA 完整性且修复简单（约 3 行），建议合并前修复**。
- P2-2 SSO mock 仅按 "prod" 名防护（RR-3 残留）。

**重要口径**（与第 1 轮一致）：
1. 即便本次审查通过，**G-S01 仍 `BLOCKED`**（`delivery/release-readiness/release-gate-matrix.md:40` 确认），须 PV-SEC PROD_EQ 实测 + 安全负责人复核方可解锁，**不签发正式上线批准**。
2. 机构 CRL/OCSP 未完成真实 CA 联调；当前无 CRL 时证书认证 fail-closed 阻断（设计正确，非缺陷）。
3. 真实 IAM/SSO 联调仍依赖外部规范（A4 待机构提供）。
4. 本结果不代表生产放行。

## 10. 返工任务清单（第三轮 / 上线前）

### 建议合并前修复（P2，MFA 完整性）

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-01 | `unbindMfa` 不推进 TOTP counter（P2-1） | `unbindMfa` 在 `Totp.verify` 后调 `repository.advanceCounter(username, counter)` 并校验返回 true，或显式检查 `counter > binding.lastCounter()`；补"已消费 code 不可解绑 MFA"测试 | 中（合并前） |

### 上线前 / 专项（P2-P3）

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-02 | SSO mock 仅按 "prod" 名（P2-2） | 改默认拒绝或白名单 profile 集合；补非 prod 名的 mock 激活测试 | 中（上线前） |
| F-03 | sm4Key 复用（P3-3/RR-6） | SM4 加密与 HMAC 签名密钥分离（不同派生/不同密钥） | 低 |
| F-04 | `requiredMfa` 不显式检查 enabled + 内存 fallback 无守卫（P3-2/RR-8） | 显式断言 `binding.enabled()`；`AdvancedAuthRepository jdbc==null` 改 fail-fast 或显式标注测试专用 | 低 |
| F-05 | 审计时序（P3-7/RR-10） | MFA 完成后再记登录成功，或区分"第一因素通过/登录完成" | 低 |
| F-06 | `/auth/**` 白名单过宽（P3-5，预存在） | `/auth/all-permissions` 收紧或鉴权；`parse(null)` 防 NPE->500 | 低 |
| F-07 | 多实例 ssoStates/certificateChallenges 不共享（P3-6） | 生产多实例改 Redis 共享存储 | 低（多实例部署前） |
| F-08 | `verifyProof` 不支持 EdDSA（P3-8/RR-7） | 支持 EdDSA（或显式拒绝并文档化） | 低 |
| F-09 | `permissions` 死代码（P3-9/RR-9） | 删除未调用的 `AdvancedAuthRepository.permissions` 或去重 | 低 |
| F-10 | `certificateChallenges.remove` 返回值未检查（P3-4） | 检查返回值或用一次性语义 | 低 |
| F-11 | `rotateCertificate` 非原子（P3-8） | 事务化或先 bind 后 revoke | 低 |
| F-12 | `consumerList` 先全量后过滤（P3-10/RR-12） | 性能优化 | 低 |
| F-13 | GET /login/cert?fingerprint 存在性泄露（RR-11） | 统一返回 | 低 |
| F-14 | MFA_REQUIRED 字符串前缀契约（RR-13） | 改结构化响应 | 低 |
| F-15 | Codex 第二轮完成报告未输出 | 补 §7 七节完成报告（流程项） | 低 |

## 11. 建议提交信息

```text
feat(fix-01): security auth rework round 2 - MFA bypass fix + JWT secret hardening

- RR-1: challenge is now stateless HMAC (username.expires.uuid.sig) without
  token; JWT issued only after TOTP verify + advanceCounter (fixes MFA bypass
  regression introduced by stateless challenge in round 1)
- RR-2: JWT_SECRET mandatory env + JwtUtil fail-fast (no weak default);
  gateway in sync; AuthApplication no longer falls back to change-me-in-env
- RR-3: SSO mock-enabled gated by prod profile fail-fast
- RR-4: cert revocation fail-closed when no CRL; setRevocationEnabled(true)
- RR-5: multi-tier CA chain support (parseChain + trust-anchor removal)
- tests: MFA-bypass attack path (service+endpoint), multi-tier CA, revocation,
  concurrency; mvn test 125 green (common 40 + auth 48 + partner 37)
- G-S01 still BLOCKED; overall NOT_READY (not production approval)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 附：审查方法与返工历史

- **主控**：逐行追踪 AdvancedAuthService/Aspect/Controller/Repository/CertificateTrustValidator/SsoConfiguration/Totp/AuthService/JwtUtil/JwtAuthFilter + 全部测试 + SQL；独立实测 `mvn test` 三模块全绿。
- **独立子代理** fix01-rework-2-adversary：全面证伪，10 条反例全部反驳，新发现 P2-1（unbindMfa）+ P2-2（SSO profile 名）+ 多项 P3；双方交叉验证后存活的缺陷记入 §8.3。
- **门禁口径**：`delivery/release-readiness/release-gate-matrix.md:40` G-S01 = BLOCKED（安全负责人，独立实现后 PV-SEC，TBD）。

| 轮次 | 任务单 | 审查报告 | 结论 |
|---|---|---|---|
| 原始 | `tasks/codex-task-fix-01.md` | `reviews/claude-review-fix-01.md` | 需返工（8 P1） |
| 第 1 轮 | `tasks/codex-task-fix-01-rework.md`（R-1~R-16） | `reviews/claude-review-fix-01-rework.md` | 需返工（1 P1 回归 + 1 相邻 P1） |
| 第 2 轮 | `tasks/codex-task-fix-01-rework-2.md`（RR-1~RR-13） | 本文件 | **通过（有条件）**，无存活 P1；建议合并前修 F-01（unbindMfa） |

---

**下一步**：fix-01 第二轮返工**通过（有条件）**。建议合并前修复 F-01（`unbindMfa` 加 `advanceCounter`，MFA 解绑重放防护一致性，约 3 行 + 1 测试）；F-02~F-15 上线前/专项。修复 F-01 后可提交 + PR + 合并 master。**G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核方可解锁；A4 真实联调待机构 IAM/SSO 规范；不签发正式上线批准。**
