# Claude Code 返工复审结果 - 批次1 安全认证（第三轮返工）

> 审查阶段：fix-01 第三轮返工复审（F-01 必修 + F-02 建议同轮）
> 审查日期：2026-07-16
> 审查范围：`ai/fix-01-security-auth` 分支，未提交改动 = 11 tracked modified + 19 untracked（累积 round1~3，本轮增量聚焦 F-01/F-02）
> 返工任务单：`tasks/codex-task-fix-01-rework-3.md`
> 前序审查：`reviews/claude-review-fix-01.md`（原始，8 P1）、`reviews/claude-review-fix-01-rework.md`（第 1 轮，1 P1 回归 + 1 相邻 P1）、`reviews/claude-review-fix-01-rework-2.md`（第 2 轮，通过有条件；存活 P2-1/P2-2）
> 审查方：Claude Code（主控，逐行追踪 + 独立实测全量回归）

---

## 1. 审查对象

本轮（第 3 轮）增量改动聚焦两项 P2，未触碰已通过的 RR-1~RR-5 逻辑：

| 文件 | 类别 | 本轮改动 |
|---|---|---|
| `AdvancedAuthService.java` | F-01 | `unbindMfa`（:109-121）在 `Totp.verify` 后加 `repository.advanceCounter(username, counter)`，返回 false 抛 `AUTH-401 "MFA replay rejected"`，再 `clearMfa` |
| `SsoConfiguration.java` | F-02 | 改白名单 `Set.of("test","dev")`；`mockAllowed = activeProfiles.length>0 && allMatch`；`mockEnabled && !mockAllowed` 抛 `IllegalStateException` |
| `AdvancedAuthServiceTest.java` | 测试 | +1 新测 `consumedTotpCannotUnbindMfaButNewTotpCan`（攻击路径）；扩写 `productionSsoRejects...`（+production/default/dev 分支断言） |
| `AdvancedAuthEndpointSecurityTest.java` | 测试 | `passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp` 末尾 unbind 改用 `Totp.generate(secret, counter+1)`（新窗口 code，避开防重放） |

> 其余 tracked modified（JwtUtil/CommonSecurityConfiguration/JwtAuthFilter/AuthService/AuthApplication/gateway JwtHeaderFilter 等）与 untracked 新增类（CertificateTrustValidator/Totp/SsoAdapter*/MfaLoginEnforcementAspect/PartnerAbacAspect/V024-U024）均为 round 1/2 既有改动，第 2 轮已逐项验收；本轮未改动（见 §6 范围核查）。

## 2. Git 状态

```text
分支：ai/fix-01-security-auth（HEAD: fe92f870，仅文档提交；安全认证代码全在工作区未提交）
未提交、未暂存；未动 .env、证书、k8s/prod
未发现独立的 Codex 第三轮"阶段完成报告"文件（任务单 §7 要求；第 2 轮亦缺，属流程项，见 §8 观察 O-1）
独立实测：
  mvn test（全 7 后端模块）-> BUILD SUCCESS，338 测试全绿（0 failures 0 errors）
```

## 3. 代码差异摘要（本轮增量）

| 类别 | 变更 | 审查意见 |
|---|---|---|
| F-01 unbindMfa 防重放 | `Totp.verify` 得 counter(>=0) 后调 `advanceCounter`，失败抛 AUTH-401，再 clearMfa | **与 completeChallenge:85 一致**，MFA 解绑重放防护闭环；DB 路径 `mfa_enabled=1` 在 unbind 时仍成立（clearMfa 在后），可工作 |
| F-02 SSO mock 白名单 | 默认拒绝，仅 `test/dev` profile 全命中才允许 mock-enabled | 比旧黑名单（仅匹配字面 "prod"）严格更安全；空 profile / production / prd / 默认 + env 注入一律 fail-fast |
| 测试 | +1 攻击路径测试 + 现有 unbind 改新 code + SSO profile 多分支断言 | 攻击路径覆盖到位；现有测试配套调整正确 |

## 4. 需求满足情况（F-01/F-02 逐项核验）

| 返工项 | 是否满足 | 核查证据 |
|---|---|---|
| **F-01** unbindMfa 加 TOTP counter 防重放（P2-1，必修） | **是** | `AdvancedAuthService.unbindMfa:109-121`：`Totp.verify`->`counter<0` 抛 invalid code->`!advanceCounter(username,counter)` 抛 "MFA replay rejected"->`clearMfa`。与 `completeChallenge:85`、`confirmMfaBinding:105-106` 三处 TOTP 验证点防重放一致。方案 A（复用 advanceCounter）落地。 |
| **F-01 配套** 现有测试不回归 | **是** | `AdvancedAuthEndpointSecurityTest:99-103` unbind 改 `Totp.generate(secret, counter+1)`（confirm 用 counter-1、complete 用 counter、unbind 用 counter+1，严格递增 > lastCounter），加防重放后不 break。 |
| **F-01 攻击路径测试** | **是** | `AdvancedAuthServiceTest.consumedTotpCannotUnbindMfaButNewTotpCan:60-81`：①completeChallenge 消费 code C 后，用同一 code C 解绑 -> 抛 AUTH-401 "MFA replay rejected" 且 `mfaEnabled` 仍 true；②新 code（counter>C）解绑成功、`mfaEnabled==false`；③既有正常路径不回归。三项齐备。 |
| **F-02** SSO mock 不依赖单一 "prod" profile 名（P2-2，建议同轮） | **是** | `SsoConfiguration:12-25`：`MOCK_ALLOWED_PROFILES=Set.of("test","dev")`；`mockAllowed = activeProfiles.length>0 && Arrays.stream(all).allMatch(set::contains)`；`mockEnabled && !mockAllowed` 抛 IllegalStateException。方案 A（默认拒绝/白名单）落地。 |
| **F-02 测试** | **是** | `AdvancedAuthServiceTest.productionSsoRejects...:237-275`：prod / production / 默认 profile + mockEnabled=true 均 fail-fast；默认+mockEnabled=false 返回 DisabledSsoAdapter；test / dev + mockEnabled=true 返回 MockSsoAdapter。覆盖完整。 |

## 5. 开发计划符合情况

| 检查项 | 是否符合 | 说明 |
|---|---|---|
| 最小可行结果（修 P2-1 + 同轮 P2-2） | 符合 | 两项 P2 均修复，未引入新 P1/P2 |
| 最小改动 | 符合 | 仅 unbindMfa（+3 行）+ SsoConfiguration（白名单重写）+ 测试 |
| 避免过度设计 | 符合 | 复用既有 advanceCounter，未引入 Redis/新框架；白名单用 `Set.of` |
| 可回滚 | 符合 | V024/U024 可逆；改动集中在两个方法 + 测试 |
| 不动 RR-1~RR-5 已验收逻辑 | 符合 | startChallenge/completeChallenge/verifyChallenge/sign/requireMfa/Aspect/CertValidator/JwtUtil 逐行比对与第 2 轮验收态一致（见 §6） |
| 配置外置 | 符合 | jwt/mfa/cert/sso 仍占位符；yml `mock-enabled: false` 硬编码（round 2 既有，非本轮） |

## 6. Codex 任务边界检查

| 检查项 | 结果 | 说明 |
|---|---|---|
| 是否修改敏感文件 | 通过 | 无 .env/证书/k8s-prod；truststore/crl 仍 `${ENV}` 占位 |
| 是否修改 docs/tasks/reviews | 通过 | 仅新增本审查文件（Claude 职责）+ rework-3 任务单；未改 docs |
| 是否引入大型依赖 | 通过 | 无新增依赖（bcpkix-jdk18on 为 round 2 test scope，本轮未动） |
| 是否无关重构 | 通过 | unbindMfa 复用 advanceCounter、SsoConfiguration 改白名单，均为必要最小改动 |
| 是否超出 F-01/F-02 | 通过 | 本轮增量仅两项；其余为 round 1/2 既有（已验收） |
| 是否动 RR-1~RR-5 | 通过 | 逐行核对：challenge 仍 4 段 HMAC 不含 token；completeChallenge 仍先 verify+advanceCounter 后 issue；Aspect 仍不预颁 token；JwtUtil 仍 fail-fast；CertValidator 仍 fail-closed+多层 CA——均与第 2 轮验收态一致，未回归 |
| 既有已执行迁移 V001-V023 | 通过 | 仅 V024/U024（round 2 既有），未改历史；H2 集成测试 Flyway 成功应用 24 migrations |

## 7. 测试检查（独立实测，非仅信声明）

| 测试命令 | 是否运行 | 结果 | 说明 |
|---|---|---|---|
| `mvn -pl platform-auth,platform-partner,platform-common -am test` | 是 | **BUILD SUCCESS** | common 40（1 skipped）/ auth 49 / partner 37，0 failures |
| `mvn test`（全 7 后端模块） | 是 | **BUILD SUCCESS** | 338 测试全绿：common 40 / gateway 2 / auth 49 / partner 37 / pipeline 122 / quality 35 / billing 53；0 failures 0 errors |
| `AdvancedAuthServiceTest`（12） | 是 | 通过 | 含 F-01 新测 `consumedTotpCannotUnbindMfaButNewTotpCan`（攻击路径：已消费 code 不可解绑、新 code 可解绑）、F-02 扩写 `productionSsoRejects...`（prod/production/default/dev/test 全分支）；并保留 RR-1 攻击路径 `mfaChallengeCannotBeDecoded...`、`restartWithJdbcStillRejectsOldTotpReplay`、`concurrentUseOfSameTotpAllowsOnlyOne` |
| `AdvancedAuthEndpointSecurityTest`（4） | 是 | 通过 | `passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp`：unbind 改 counter+1 后仍 200；challenge 4 段、不含 enrollmentToken、jwt.parse 抛异常断言不变 |
| Flyway V024 | 是 | 通过 | H2 集成测试 `Successfully applied 24 migrations ... now at version v024` |

> **攻击路径覆盖**：F-01 service 层直接断言（replay 抛 AUTH-401 + MFA 仍 bound + 新 code 解绑）；F-02 profile 多分支 fail-fast/激活断言。覆盖到位。
> **计数变化**：auth 48（round 2）-> 49（round 3，+1 攻击路径测试），`AdvancedAuthServiceTest` 11->12。符合预期。

## 8. 安全与风险检查（对抗式审查专节）

### 8.1 攻击面枚举（本轮聚焦）

| 攻击面 | 入口 | 处理点 |
|---|---|---|
| MFA 解绑 | `/api/v1/auth/mfa/unbind`（需 JWT） | `unbindMfa` -> `Totp.verify` -> `advanceCounter` -> `clearMfa` |
| SSO mock 激活 | 启动期 `SsoConfiguration`（profile + env） | `mockAllowed` 白名单判定 |

### 8.2 构造反例及追踪结果

| 反例 | 追踪结果 | 存活？ |
|---|---|---|
| **F-01**：completeChallenge 消费 code C 后，用同一 code C 解绑（重放） | **已反驳**：`unbindMfa` 调 `advanceCounter(username, C)`；DB `WHERE mfa_enabled=1 AND mfa_last_counter<C` 命中 0 行->返回 false->抛 "MFA replay rejected"；内存 `counter<=lastCounter`->false。`clearMfa` 不执行，MFA 仍 enabled。测试 `consumedTotpCannotUnbindMfaButNewTotpCan` 实证 | 否 |
| **F-01**：advanceCounter 的 `mfa_enabled=1` 在 unbind 时失效？ | **已反驳**：clearMfa 在 advanceCounter 之后（:120 在 :117 后），unbind 时 MFA 仍 enabled，DB UPDATE 可命中 | 否 |
| **F-01**：并发竞态（两请求同 code 解绑） | **已反驳**：DB `UPDATE...WHERE mfa_last_counter<?` 原子；内存 `synchronized`；第一请求成功后第二请求 `counter<=lastCounter` 抛 AUTH-401 | 否 |
| **F-01**：整数溢出/负 counter | **已反驳**：`Totp.verify` 返回 -1 被 `counter<0` 拦截；有效 counter 为 epoch/30（大正 long），无溢出 | 否 |
| **F-01**：内存与 DB 的 enabled 守卫不一致（解绑未确认绑定） | **预存在 P3-2（非本轮引入）**：内存 advanceCounter 不查 enabled，可解绑 enabled=false 的休眠 secret；DB 路径 `mfa_enabled=1` 守卫会 fail-closed。对已启用 MFA 的解绑防重放（F-01 目标）两条路径一致；休眠 secret 解绑不构成 MFA 绕过（无活跃 MFA）。round 3 未触碰、不阻断，留 P3 | 否（P3，非本轮） |
| **F-02**：生产 profile 名为 `production`/`prd`/默认 + `SECURITY_SSO_MOCK_ENABLED=true` 注入 | **已反驳**：白名单 `Set.of("test","dev")`，非 test/dev 的任意 profile（含 production/prd/默认/空）-> `mockAllowed=false` -> 抛 IllegalStateException。测试 prod/production/default 三分支实证 | 否 |
| **F-02**：混合 profile（如 test+prod）激活 mock | **已反驳**：`allMatch` 要求全部 profile 在白名单；test+prod 中 prod 不在集合->false->抛异常。保守阻断 | 否 |
| **F-02**：空 activeProfiles 导致 allMatch 空真值返回 true | **已反驳**：`activeProfiles.length>0 &&` 前置守卫，空 profile 短路为 `mockAllowed=false` -> mockEnabled=true 抛异常。默认拒绝正确 | 否 |
| **F-02**：攻击者运行时控制 profile | **已反驳**：Spring profile 为启动期 `spring.profiles.active`/env，非请求可控 | 否 |
| **F-02**：test profile 下 mock 误激活到生产 | **已反驳**：生产不部署 `@ActiveProfiles("test")`；yml `mock-enabled: false`（prod）硬编码；白名单仅在显式 test/dev profile + mock-enabled 双条件满足才激活 | 否 |

### 8.3 存活缺陷

**本轮（F-01/F-02）：无存活 P1/P2。** 两项 P2 全部修复且攻击路径测试覆盖。

**P3 项（建议上线前/专项，不阻断本轮，与 round 2 §8.3 一致，本轮未处理）**：

| # | 缺陷 | 位置 | 状态 |
|---|---|---|---|
| P3-1 | challenge 非一次性消费（无状态 HMAC 固有，300s 窗口需递增 TOTP） | `AdvancedAuthService:73-89` | 设计选择，留 P3 |
| P3-2 | `requiredMfa` 不显式检查 enabled；内存 advanceCounter 无 `mfa_enabled=1` 守卫（DB 路径有） | `:203-207`、`Repository:55-61` | 留上线前（F-04） |
| P3-3 | sm4Key 复用（SM4 加密 + HMAC 签名同密钥） | `:234-243` | 留上线前（F-03） |
| P3-5 | `/auth/**` 白名单过宽（`/auth/all-permissions` 免 JWT 枚举；`parse(null)` NPE->500，预存在） | `JwtAuthFilter:29`、`AuthController` | 留上线前（F-06） |
| P3-6 | 多实例 ssoStates/certificateChallenges 内存 map 不共享 | `:38-39` | 留多实例部署前（F-07） |
| P3-7 | 审计时序：authenticate 内 AuditLogger 在 MFA 完成前触发 | `AuthService:65` | 留上线前（F-05） |
| P3-8 | rotateCertificate 非原子；verifyProof 不支持 EdDSA | `:149-152`、`:247-248` | 留上线前（F-08/F-11） |
| P3-9 | `AdvancedAuthRepository.permissions` 死代码 | `Repository:106` | 留清理（F-09） |

> 上述 P3 均为 round 2 已记录、任务单 §8.7 明确"不纳入本轮，留上线前/专项"。本轮不处理符合范围约定。

### 8.4 已知风险（用户列明，代码已正确处理）

| 风险 | 代码处理 | 状态 |
|---|---|---|
| 机构 CRL/OCSP 未完成真实 CA 联调；无 CRL 时阻断证书认证 | `CertificateTrustValidator` fail-closed 抛 AUTH-401 | **已知并接受**（设计正确，非缺陷） |
| 真实 IAM/SSO 联调依赖外部规范 | `DisabledSsoAdapter` 默认抛 AUTH-503；MockSsoAdapter 仅 test/dev + 显式启用 | **已知**：A4 待机构 IAM/SSO 规范 |
| G-S01 仍 BLOCKED | 见 §9 门禁口径 | **不变**：代码实现不解锁门禁 |

### 8.5 安全正向项

- F-01：unbindMfa 防重放与 completeChallenge/confirmMfaBinding 三点一致，已消费 code 不可解绑，攻击路径测试实证
- F-02：SSO mock 默认拒绝 + 白名单，封堵 env 注入/非 prod 名/混合 profile/空 profile 全部激活路径
- RR-1~RR-5（round 2 验收）逻辑完好：challenge 四段 HMAC 不含 token、JWT_SECRET fail-fast、cert 吊销 fail-closed、多层 CA 链——本轮未回归
- advanceCounter DB 原子条件更新 + synchronized 内存 + 并发测试（round 2 既有，仍绿）
- credential/TOTP secret 仍 Sm4Util 加密存储；密码 BCrypt

## 9. 审查结论

```text
✓ 通过（无条件） - F-01/F-02 两项 P2 全部修复，攻击路径覆盖，全量回归 338 测试全绿
- 无存活 P1；无存活 P2（round 2 的 P2-1/P2-2 本轮消除）
- RR-1~RR-5 已验收逻辑未回归
- 可提交 + PR + 合并 master
- G-S01 仍 BLOCKED；本审查通过不代表生产放行
- P3 项 F-03~F-15 留上线前/专项（任务单 §8.7 明确不纳入本轮）
```

**理由**：第三轮返工核心目标——修复第 2 轮存活的 P2-1（unbindMfa 解绑重放）+ 同轮 P2-2（SSO mock profile 名）——均正确达成。

1. **F-01**：`unbindMfa` 在 `Totp.verify` 后调 `advanceCounter(username, counter)`，返回 false 抛 "MFA replay rejected"，与 `completeChallenge`/`confirmMfaBinding` 三处 TOTP 防重放一致。DB 路径 `mfa_enabled=1` 在 unbind 时仍成立（clearMfa 在后），可工作。攻击路径测试 `consumedTotpCannotUnbindMfaButNewTotpCan` 实证：已消费 code 解绑被拒且 MFA 仍 bound，新 code 解绑成功。配套调整 `passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp` 的 unbind 改用 counter+1 新窗口 code，不 break。
2. **F-02**：`SsoConfiguration` 改白名单 `Set.of("test","dev")` + 默认拒绝，封堵 production/prd/默认/空/混合 profile + env 注入全部 mock 激活路径。测试 prod/production/default/test/dev 五分支断言到位。

对抗式审查对 F-01/F-02 各构造 4-5 条反例（重放/竞态/溢出/enabled 守卫/profile 名/混合/空/请求可控），**全部反驳**，未发现存活阻断项。

独立实测全量 `mvn test`：**338 测试全绿（0 failures 0 errors）**，用户"完整回归"声明成立。范围纪律：本轮增量仅 F-01/F-02 + 测试，RR-1~RR-5 逐行比对与第 2 轮验收态一致，未回归。

**重要口径**（与第 1/2 轮一致）：
1. 即便本次审查通过，**G-S01 仍 `BLOCKED`**（`delivery/release-readiness/release-gate-matrix.md:40`），须 PV-SEC PROD_EQ 实测 + 安全负责人复核方可解锁，**不签发正式上线批准**。
2. 机构 CRL/OCSP 未完成真实 CA 联调；无 CRL 时证书认证 fail-closed 阻断（设计正确，非缺陷）。
3. 真实 IAM/SSO 联调仍依赖外部规范（A4 待机构提供）。
4. 本结果不代表生产放行。

## 10. 返工任务清单（上线前 / 专项，P3，不阻断本轮）

| # | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| F-03 | sm4Key 复用（P3-3/RR-6） | SM4 加密与 HMAC 签名密钥分离 | 低（上线前） |
| F-04 | `requiredMfa` 不显式检查 enabled + 内存 fallback 无守卫（P3-2/RR-8） | 显式断言 `binding.enabled()`；`jdbc==null` 改 fail-fast 或标注测试专用 | 低（上线前） |
| F-05 | 审计时序（P3-7/RR-10） | MFA 完成后再记登录成功 | 低（上线前） |
| F-06 | `/auth/**` 白名单过宽（P3-5，预存在） | `/auth/all-permissions` 收紧；`parse(null)` 防 NPE->500 | 低（上线前） |
| F-07 | 多实例 ssoStates/certificateChallenges 不共享（P3-6） | 生产多实例改 Redis 共享存储 | 低（多实例部署前） |
| F-08 | `verifyProof` 不支持 EdDSA（P3-8/RR-7） | 支持 EdDSA 或显式拒绝并文档化 | 低（上线前） |
| F-09 | `permissions` 死代码（P3-9/RR-9） | 删除未调用方法或去重 | 低（清理） |
| F-10 | `certificateChallenges.remove` 返回值未检查（P3-4） | 检查返回值或一次性语义 | 低 |
| F-11 | `rotateCertificate` 非原子（P3-8） | 事务化或先 bind 后 revoke | 低 |
| F-12 | `consumerList` 先全量后过滤（P3-10/RR-12） | 性能优化 | 低 |
| F-13 | GET /login/cert?fingerprint 存在性泄露（RR-11） | 统一返回 | 低 |
| F-14 | MFA_REQUIRED 字符串前缀契约（RR-13） | 改结构化响应 | 低 |
| F-15 | Codex 第三轮完成报告未输出 | 补 §7 七节完成报告（流程项） | 低（流程） |

> 本轮无新增返工项（F-01/F-02 闭环）。上表为 P3 上线前/专项清单，与 round 2 §10 一致延续。

## 11. 建议提交信息

```text
fix(fix-01): security auth rework round 3 - unbind MFA replay guard + SSO mock whitelist

- F-01: unbindMfa now calls advanceCounter after Totp.verify and rejects
  replayed codes (AUTH-401), aligning with completeChallenge/confirmMfaBinding;
  closes MFA-unbind replay that could permanently disable MFA
- F-02: SsoConfiguration switches to allow-list (test/dev only) with default
  deny; blocks mock activation under production/prd/default/empty/mixed profiles
  and SECURITY_SSO_MOCK_ENABLED env injection
- tests: add consumedTotpCannotUnbindMfaButNewTotpCan (attack path), expand SSO
  profile branch coverage; adjust existing unbind to fresh counter+1 code
- verified: mvn test 338 green (common 40 / gateway 2 / auth 49 / partner 37 /
  pipeline 122 / quality 35 / billing 53), 0 failures
- RR-1~RR-5 verified logic untouched; G-S01 still BLOCKED (not production approval)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

## 附：审查方法与返工历史

- **主控**：逐行追踪 `AdvancedAuthService.unbindMfa`/`completeChallenge`/`confirmMfaBinding`/`SsoConfiguration`/`AdvancedAuthRepository.advanceCounter`/`Totp` + 全部测试；独立实测 `mvn test` 全 7 模块 338 测试全绿。
- **对抗式**：对 F-01（重放/竞态/溢出/enabled 守卫/内存-DB 一致性）与 F-02（profile 名/混合/空/请求可控/env 注入）各构造反例，全部反驳；未发现存活 P1/P2。
- **门禁口径**：`delivery/release-readiness/release-gate-matrix.md:40` G-S01 = BLOCKED（安全负责人，独立实现后 PV-SEC，TBD）。

| 轮次 | 任务单 | 审查报告 | 结论 |
|---|---|---|---|
| 原始 | `tasks/codex-task-fix-01.md` | `reviews/claude-review-fix-01.md` | 需返工（8 P1） |
| 第 1 轮 | `tasks/codex-task-fix-01-rework.md`（R-1~R-16） | `reviews/claude-review-fix-01-rework.md` | 需返工（1 P1 回归 + 1 相邻 P1） |
| 第 2 轮 | `tasks/codex-task-fix-01-rework-2.md`（RR-1~RR-13） | `reviews/claude-review-fix-01-rework-2.md` | 通过（有条件），存活 P2-1/P2-2 |
| 第 3 轮 | `tasks/codex-task-fix-01-rework-3.md`（F-01 + F-02） | 本文件 | **通过（无条件）**，无存活 P1/P2 |

---

**下一步**：fix-01 第三轮返工**通过（无条件）**。F-01/F-02 两项 P2 全部修复、攻击路径覆盖、全量回归 338 测试全绿、RR-1~RR-5 未回归。**可提交 + PR + 合并 master**。G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核方可解锁；A4 真实联调待机构 IAM/SSO 规范；P3 项 F-03~F-15 留上线前/专项；**不签发正式上线批准**。
