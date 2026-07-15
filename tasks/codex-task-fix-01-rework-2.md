# Codex 返工任务 - 批次1 安全认证（第二轮）

> 主控：Claude Code　执行：Codex
> 分支：`ai/fix-01-security-auth`（同分支第 2 轮返工）
> 日期：2026-07-15
> 任务性质：代码返工（修复第 1 轮返工引入的 P1 回归 + 相邻 P1 + P2）
> 来源：`reviews/claude-review-fix-01-rework.md` §8
> 原返工单：`tasks/codex-task-fix-01-rework.md`（第 1 轮，R-1~R-16）
> 审查结论：需返工，1 项 P1（P1-RR1 MFA 绕过，返工回归）+ 1 项相邻 P1（P1-RR2 JWT_SECRET）
> 返工次数：第 2 轮（CLAUDE.md 限 3 次，剩余 1 次）

---

## 1. 任务目标

修复第 1 轮返工引入的 **MFA 绕过 P1 回归** + 相邻 **JWT_SECRET P1** + P2 关键项，使代码可作为完成交付。

**关键背景**：第 1 轮 R-1 改 challenge 无状态（重启不丢）时把 JWT 嵌入 challenge 返回客户端，引入 MFA 绕过（攻击者 `challenge.split(".")[1]` base64url 解码即得 JWT）。本轮修复此回归-**challenge 不嵌 token**，同时保持重启不丢。

## 2. 需求依据（必读）

1. `reviews/claude-review-fix-01-rework.md`（第 1 轮返工复审，§4 P1-RR1/RR2 + §8 返工清单）
2. `reviews/claude-review-fix-01.md`（原审查）
3. `tasks/codex-task-fix-01-rework.md`（第 1 轮返工单）
4. `tasks/codex-task-fix-01.md`（原任务单）
5. `AGENTS.md`、`docs/requirements.md` §6

## 3. 允许修改范围

- `platform-auth/src/main/java`、`src/main/resources`、`src/test`
- `platform-common/src/main/java`（JwtUtil 如需 RR-2 fail-fast）
- `platform-gateway`（如需）
- `db/migration`（如需新版本）

## 4. 禁止修改范围

- `.env`/`.env.*`、密钥、证书、`k8s/prod`
- `docs/`、`tasks/`（本返工单除外）、`reviews/`
- P0-P2 历史审查/阶段报告结论
- `perf/`、`security/`、`delivery/chaos-drill/`、`delivery/backup-restore/` 脚本
- 既有已执行 SQL 迁移 V001-V023（V024 可修正）
- 真实账号/令牌/连接串

---

## 5. 返工任务

### P1 阻断（必修）

#### RR-1 MFA challenge 不嵌 token（修复返工回归）

- **现状**：`AdvancedAuthService.startChallenge:71-75` 把 `encode(token)` 明文嵌 challenge `parts[1]`，`sign` 是 HMAC-SHA256（只防篡改不防读取）-> 攻击者 `challenge.split("\\.")[1]` base64url 解码得完整权限 JWT，**MFA 完全绕过**。`requireMfa:194-198`/`MfaLoginEnforcementAspect:19-23` 先 `jwtUtil.issue` 颁最终 JWT 再包 challenge。
- **修复**（任选其一，推荐 A）：
  - **方案 A（推荐）**：challenge 只含 `{username, expires, nonce}` 签名（HMAC，**不含 token**）；`completeChallenge` 验 TOTP 通过后**再** `jwtUtil.issue` 颁 token。`requireMfa`/`MfaLoginEnforcementAspect` 不再预颁 token，改为返回 challenge；登录流程改为：首因素认证 -> 返回 challenge -> 客户端用 challenge+TOTP 调 completeChallenge -> 通过后颁 token。
  - **方案 B**：服务端存 token（Redis/DB），challenge 用不透明 ID；`completeChallenge` 凭 ID 取 token。
  - **方案 C**：对 token 对称加密（SM4）嵌 challenge（非仅 MAC）。
- **保持重启不丢**：方案 A 无状态 challenge 不含 token，重启不影响（challenge 短期 300s，重启丢失进行中 challenge 可接受，用户重新登录）。
- **补测试**：不解 MFA 直接从 challenge 提取 token 应失败（攻击路径覆盖）；现有 completeChallenge 流程不回归。

#### RR-2 JWT_SECRET 强制注入 + fail-fast

- **现状**：`application.yml:29` `security.jwt.secret: ${JWT_SECRET:change-me-in-env}` 默认弱密钥（预存在，返工未触碰）。与 R-3 sm4Key 硬化不一致。
- **修复**：移除默认，改 `${JWT_SECRET}`（无默认）；启动 fail-fast（未配置禁用启动，同 R-3 模式）。JwtUtil 或配置类检查 secret 非 null/blank/"change-me-in-env"。
- **补测试**：未配置 JWT_SECRET 启动失败。

### P2 改进（建议同轮）

#### RR-3 SSO mock-enabled 生产 fail-fast

- **现状**：`SsoConfiguration:9-14` `@Value("${security.sso.mock-enabled:false}")` 可被 `SECURITY_SSO_MOCK_ENABLED=true` 覆盖；生产误设即激活 MockSsoAdapter（固定 `sso-test-user`，`test-valid-code`）。
- **修复**：`@Profile("!prod")` 或生产 profile 下 `mock-enabled=true` 时 fail-fast。

#### RR-4 证书吊销默认校验

- **现状**：`CertificateTrustValidator:58` `setRevocationEnabled(false)`，仅 `CERT_CRL` 显式配置时手动校验；OCSP 未实现；默认空 -> 吊销证书可登录。
- **修复**：默认启用吊销校验（CRL/OCSP）；或显式标注"待机构 CA 配置"并保持 BLOCKED（文档说明，不放宽）。

#### RR-5 多层 CA 链支持

- **现状**：`CertificateTrustValidator:55` `generateCertPath(List.of(certificate))` 仅放叶子；`AdvancedAuthService.parse:259` 单证书；root->intermediate->user 模式 PKIX 找不到路径，**所有合法证书被拒**。
- **修复**：`generateCertPath` 支持中间证书；`parse`/接口支持客户端发送证书链（PEM 含多证书）；补多层 CA 测试。

### P3（可选，不阻断）

| # | 问题 | 修复 |
|---|---|---|
| RR-6 | sm4Key 复用（SM4 加密 + HMAC 签名） | 密钥分离（不同派生/不同密钥） |
| RR-7 | verifyProof 仅 RSA/EC | 支持 EdDSA |
| RR-8 | Repository jdbc==null 静默回退内存 | misconfig fail-fast |
| RR-9 | AuthService.permissionsFor 与 Repository.permissions 重复 | 去重 |
| RR-10 | 审计时序（R-14 未做） | MFA 完成后再审计登录成功 |
| RR-11 | GET /login/cert?fingerprint= 泄露存在性（R-16） | 统一返回 |
| RR-12 | consumerList 先 proceed 全量再过滤 | 性能优化 |
| RR-13 | certificateLogin 返回 "MFA_REQUIRED:" 前缀契约 | 改结构化响应 |

---

## 6. 测试要求

- **必补攻击路径**（RR-1）：不解 MFA 直接从 challenge 提取 token 应失败（断言 challenge 不含可解码 JWT）
- RR-2：未配置 JWT_SECRET 启动失败
- RR-5：多层 CA 链通过
- 现有测试不回归（`mvn test -pl platform-auth,platform-partner -am` 全过）
- 新增返工代码覆盖率 ≥80%，**攻击路径必须覆盖**

## 7. 完成后输出

1. RR-1 修复方案（A/B/C）+ challenge 不嵌 token 的实现说明 + 登录流程调整
2. RR-2~RR-5 修改位置
3. RR-6~RR-13（如修复）
4. 攻击路径测试清单与结果（通过数/失败数/覆盖率/失败明细）
5. 偏离说明（无偏离明示）
6. 潜在风险（如 CRL/OCSP 待机构 CA、SSO 真实联调待外部）
7. 明确 G-S01 仍 `BLOCKED`，不声明生产通过

## 8. 返工规则

1. 只修复 RR-1~RR-5（+可选 RR-6~RR-13），不额外改动
2. **第 2 轮返工，剩余 1 次**（CLAUDE.md 限 3 次）
3. 完成后 Claude Code 对抗式复审（含独立子代理第二意见）
4. 通过后方可提交 + PR + 合并 master
5. G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核；A4 真实联调待机构 IAM/SSO 规范
6. 不签发正式上线批准

---

## 附录：返工历史

| 轮次 | 任务单 | 审查报告 | 结论 |
|---|---|---|---|
| 原始 | `tasks/codex-task-fix-01.md` | `reviews/claude-review-fix-01.md` | 需返工（8 P1） |
| 第 1 轮 | `tasks/codex-task-fix-01-rework.md`（R-1~R-16） | `reviews/claude-review-fix-01-rework.md` | 需返工（1 P1 回归 + 1 相邻 P1） |
| 第 2 轮 | 本文件（RR-1~RR-13） | 待复审 | - |
