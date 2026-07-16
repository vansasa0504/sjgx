# Codex 返工任务 - 批次1 安全认证（第三轮）

> 主控：Claude Code　执行：Codex
> 分支：`ai/fix-01-security-auth`（同分支第 3 轮返工）
> 日期：2026-07-15
> 任务性质：代码返工（修复第 2 轮审查存活的 1 项 P2 + 1 项同轮 P2）
> 来源：`reviews/claude-review-fix-01-rework-2.md` §8.3 / §10
> 前序返工单：`tasks/codex-task-fix-01-rework.md`（第 1 轮 R-1~R-16）、`tasks/codex-task-fix-01-rework-2.md`（第 2 轮 RR-1~RR-13）
> 第 2 轮审查结论：✓ 通过（有条件）- 无存活 P1，核心 RR-1（MFA 绕过）真修复；存活 2 项 P2
> 返工次数：**第 3 轮（最后一次，CLAUDE.md 限 3 次）**

---

## 1. 任务目标

修复第 2 轮审查存活的 **P2-1（unbindMfa 解绑重放，必修）** + 同轮 **P2-2（SSO mock profile 名，建议同修）**，使 MFA 重放防护在解绑场景一致、生产 mock 防护不依赖单一 profile 名。修复后代码可作为完成交付合并。

**关键背景**：第 2 轮 RR-1 已正确修复 MFA challenge 不嵌 token（核心 P1 回归已消除，无存活 P1）。本轮只收尾 2 项 P2 安全改进，**不触碰已通过的 RR-1~RR-5 逻辑**，避免引入回归。

## 2. 需求依据（必读）

1. `reviews/claude-review-fix-01-rework-2.md`（第 2 轮返工复审，§8.3 P2-1/P2-2 + §10 F-01/F-02）
2. `reviews/claude-review-fix-01-rework.md`（第 1 轮返工复审）
3. `reviews/claude-review-fix-01.md`（原始审查）
4. `tasks/codex-task-fix-01-rework-2.md`（第 2 轮返工单）
5. `AGENTS.md`、`docs/requirements.md` §6（权限与安全要求）

## 3. 允许修改范围

- `platform-auth/src/main/java/com/platform/auth/AdvancedAuthService.java`（unbindMfa）
- `platform-auth/src/main/java/com/platform/auth/SsoConfiguration.java`（mock profile 防护）
- `platform-auth/src/test/java/com/platform/auth/`（补测试 + 同步现有测试）
- `platform-auth/src/main/resources/application.yml` / `application-test.yml`（如 SSO profile 配置需调整）

## 4. 禁止修改范围

- `.env`/`.env.*`、密钥、证书、`k8s/prod`
- `docs/`、`tasks/`（本返工单除外）、`reviews/`
- **已通过的 RR-1~RR-5 逻辑**：`startChallenge`/`completeChallenge`/`verifyChallenge`/`sign`/`requireMfa`/`MfaLoginEnforcementAspect`/`CertificateTrustValidator`/`JwtUtil` fail-fast 等**不得改动**（第 2 轮已验收）
- P0-P2 历史审查/阶段报告结论；既有已执行 SQL 迁移 V001-V024
- 真实账号/令牌/连接串
- P3 项（F-03~F-15）**不纳入本轮**，留上线前/专项

---

## 5. 返工任务

### P2 必修

#### F-01 unbindMfa 加 TOTP counter 防重放（P2-1）

- **现状**：`AdvancedAuthService.unbindMfa`（约 `:109-117`）调 `Totp.verify` 后仅判 `< 0`，**既不检查 `counter > lastCounter`，也不调 `advanceCounter`**。它是 `completeChallenge`（`:85` 调 `advanceCounter`）、`confirmMfaBinding`（`:105-106` 写 `lastCounter`）三个 TOTP 验证点中唯一不推进 counter 的。
- **攻击路径**（第 2 轮审查 §8.3 P2-1，Claude + 子代理双重确认）：攻击者持有受害者一次有效 JWT（须先完成 completeChallenge）+ 截获一次 TOTP code（即使已被 completeChallenge 消费，因 unbind 不查 lastCounter），在 code ±1 窗口内调 `POST /api/v1/auth/mfa/unbind` -> `Totp.verify` 通过 -> `clearMfa` 解绑 -> 受害者 JWT 过期后凭密码 `POST /auth/login`（`mfaEnabled==false`）直接颁 token，**MFA 被永久绕过**。
- **修复**（任选其一，推荐 A）：
  - **方案 A（推荐，复用 advanceCounter，与 completeChallenge 一致）**：`unbindMfa` 在 `Totp.verify` 得 `counter`（`>=0`）后，调 `repository.advanceCounter(username, counter)`，返回 `false` 则抛 `BusinessException("AUTH-401", "MFA replay rejected")`，返回 `true` 再 `clearMfa`。注：`advanceCounter` 的 SQL 带 `WHERE mfa_enabled=1`，unbind 时 MFA 仍 enabled（clearMfa 在后），故可工作；advanceCounter 写入的 `lastCounter` 随后被 `clearMfa` 重置为 `-1`，不影响正确性。
  - **方案 B（显式检查）**：`Totp.verify` 得 `counter` 后，`if (counter <= binding.lastCounter()) throw new BusinessException("AUTH-401", "MFA replay rejected");` 再 `clearMfa`。不调 advanceCounter，仅校验。
- **保持重启不丢**：两方案均不改 challenge 无状态结构，不影响 RR-1。
- **关键配套（务必同步）**：现有测试 `AdvancedAuthEndpointSecurityTest.passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp` 末尾 unbind 用 `Totp.generate(secret, counter)`（counter 已被 confirm 用过、completeChallenge 用 `counter+1` 消费），**加防重放后该 unbind 会因 `counter <= lastCounter(counter+1)` 抛 AUTH-401，测试 break**。必须将该 unbind 改用新窗口 code（如 `Totp.generate(secret, counter + 2)` 或重新取 `Instant.now().getEpochSecond()/30`），使其 `> lastCounter`。
- **补测试**（攻击路径覆盖，必补）：
  1. `completeChallenge` 用 code（counter C）成功后，用**同一 code C** 调 `unbindMfa` 应抛 `AUTH-401`（"MFA replay rejected"）-- 断言 MFA 仍未解绑（`mfaEnabled` 仍 true）；
  2. 用**新 code**（counter > C）调 `unbindMfa` 成功，断言 `mfaEnabled==false`；
  3. 既有 `unbindMfa` 正常路径不回归。

### P2 建议同轮

#### F-02 SSO mock-enabled 防护不依赖单一 "prod" profile 名（P2-2）

- **现状**：`SsoConfiguration.java:15` `if (mockEnabled && activeProfiles.contains("prod"))` 仅匹配字面 `"prod"`。生产 profile 名为 `production`/`prd`/默认 + 环境变量 `SECURITY_SSO_MOCK_ENABLED=true`（误配/注入）时，MockSsoAdapter 被激活（固定 `sso-test-user`）。
- **缓解现状**：`requireMfa("sso-test-user")` 会因未绑 MFA 抛 AUTH-403 或 `permissionsFor` 抛 AUTH-401，故不直接颁 token；但 mock 路径在生产被激活本身是防护失效。
- **修复**（任选其一，推荐 A）：
  - **方案 A（默认拒绝，推荐）**：改为"仅显式白名单 profile（如 `test`/`dev`）才允许 mock-enabled=true"，其余 profile（含默认/未知/生产）一律 fail-fast 或回落 DisabledSsoAdapter。例：`Set.of("test","dev").containsAll(activeProfiles)` 才允许 mock，否则 mock-enabled=true 抛异常。
  - **方案 B（黑名单扩展）**：显式列出生产 profile 名集合 `Set.of("prod","production","prd")`，命中且 mockEnabled 抛异常。
- **保持测试不回归**：`application-test.yml` `mock-enabled: true` + `@ActiveProfiles("test")` 必须仍激活 MockSsoAdapter（test 在白名单）。现有测试 `productionSsoRejectsArbitraryUsernameCodeAndMockConfiguration`（prod + mockEnabled 抛异常）必须仍通过。
- **补测试**：非白名单 profile（如 `production` 或默认）+ mockEnabled=true 应 fail-fast 或不激活 mock（断言抛异常或返回 DisabledSsoAdapter）。

---

## 6. 测试要求

- **F-01 必补攻击路径测试**：已消费 code 不可解绑 MFA（见 §5 F-01 补测试 3 项）
- **F-02 补测试**：非白名单 profile + mockEnabled 防护（见 §5 F-02）
- **现有测试不回归**：`mvn -pl platform-auth,platform-partner,platform-common -am test` 全过（第 2 轮基线：common 40 + auth 48 + partner 37）
- **RR-1 攻击路径测试不回归**：`mfaChallengeCannotBeDecodedIntoBearerTokenWithoutSecondFactor`、`passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp`（unbind 部分按 §5 F-01 配套调整后）必须仍绿
- 新增返工代码覆盖率 ≥80%，**攻击路径必须覆盖**

## 7. 完成后输出

1. F-01 修复方案（A/B）+ unbindMfa 防重放实现说明 + 现有测试调整说明
2. F-02 修复方案（A/B）+ profile 防护实现说明
3. 攻击路径测试清单与结果（通过数/失败数/覆盖率/失败明细）
4. 偏离说明（无偏离明示；若 F-02 未同轮修，明示原因并记入上线前）
5. 潜在风险（如 CRL/OCSP 待机构 CA、SSO 真实联调待外部）
6. 明确 G-S01 仍 `BLOCKED`，不声明生产通过

## 8. 返工规则

1. **只修复 F-01（必修）+ F-02（建议同轮）**，不额外改动，不动 RR-1~RR-5 已验收逻辑
2. **第 3 轮返工，最后一次**（CLAUDE.md 限 3 次）
3. 完成后 Claude Code 对抗式复审（含独立子代理第二意见）
4. 通过后方可提交 + PR + 合并 master
5. **G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核**；A4 真实联调待机构 IAM/SSO 规范
6. **不签发正式上线批准**
7. P3 项（F-03~F-15，含 sm4Key 密钥分离/EdDSA/审计时序/`/auth/**` 白名单收紧/多实例共享存储等）不纳入本轮，留上线前/专项

---

## 附录：返工历史

| 轮次 | 任务单 | 审查报告 | 结论 |
|---|---|---|---|
| 原始 | `tasks/codex-task-fix-01.md` | `reviews/claude-review-fix-01.md` | 需返工（8 P1） |
| 第 1 轮 | `tasks/codex-task-fix-01-rework.md`（R-1~R-16） | `reviews/claude-review-fix-01-rework.md` | 需返工（1 P1 回归 + 1 相邻 P1） |
| 第 2 轮 | `tasks/codex-task-fix-01-rework-2.md`（RR-1~RR-13） | `reviews/claude-review-fix-01-rework-2.md` | 通过（有条件），无存活 P1；建议合并前修 F-01 |
| 第 3 轮 | 本文件（F-01 必修 + F-02 同轮） | 待复审 | - |

---

## 附录：启动提示词（粘贴给 Codex）

```
你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md §6
3. reviews/claude-review-fix-01-rework-2.md（§8.3 P2-1/P2-2 + §10 F-01/F-02）
4. tasks/codex-task-fix-01-rework-3.md（本返工单）

当前执行阶段：fix-01 第 3 轮返工（最后一次）。
请只执行本返工单 §5 列出的 F-01（必修）+ F-02（建议同轮）。

执行规则：
1. 只修复 F-01/F-02，不额外改动；不动已通过的 RR-1~RR-5 逻辑。
2. 不重新解释需求，不覆盖 Claude Code 的审查判断。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。
4. 不修改 .env、密钥、证书、生产配置、CLAUDE.md/AGENTS.md/docs/tasks/reviews。
5. 必须补充或更新测试（F-01 攻击路径必补），完成后运行 mvn test。
6. 测试不通过不得声明完成。
7. 注意 F-01 配套：passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp 的 unbind 部分须改为新 code。
8. 完成后输出返工完成报告（本返工单 §7）。
9. 不做最终验收，最终验收由 Claude Code 完成。
10. 明确 G-S01 仍 BLOCKED，不声明生产通过。
```
