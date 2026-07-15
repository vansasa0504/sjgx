# Codex 返工任务 - 批次1 安全认证修复

> 主控：Claude Code　执行：Codex
> 分支：`ai/fix-01-security-auth`（同分支返工，13 文件未提交）
> 日期：2026-07-15
> 任务性质：代码返工（修复审查发现的 P1 阻断 + P2 关键项）
> 来源：`reviews/claude-review-fix-01.md` §11 返工清单
> 原任务单：`tasks/codex-task-fix-01.md`
> 审查结论：需返工，8 项 P1 阻断（P1-1/2/3/4/5/8 + P1-N1/N2）

---

## 1. 任务目标

修复批次1安全认证的 **8 项 P1 阻断 + 3 项 P2 关键**，使代码可作为完成交付。**仍须 PV-SEC PROD_EQ 验证 + 安全负责人复核方可解锁 G-S01；不签发正式上线批准。**

## 2. 需求依据（必读）

1. `reviews/claude-review-fix-01.md`（审查报告，含对抗式发现 + §11 返工清单）
2. `tasks/codex-task-fix-01.md`（原任务单 A1-A4）
3. `tasks/codex-code-fix-backlog.md`（backlog）
4. `AGENTS.md`、`docs/requirements.md` §6、`tasks/claude-plan.md` §4.5.2

## 3. 允许修改范围

- `platform-auth/src/main/java`、`src/main/resources`、`src/test`
- `platform-common/src/main/java`（如需共享安全工具）
- `platform-partner/src/main/java`（`PartnerAbacAspect`）、`src/test`
- `platform-gateway/src/main/resources`（路由配置，R-4）
- `db/migration`（V024 修正或回退 + 新版本，R-1）
- `k8s/dev`（如需本地配置）

## 4. 禁止修改范围

- `.env`/`.env.*`、密钥、证书、`k8s/prod`
- `docs/`、`tasks/`（本返工单除外）、`reviews/`
- P0-P2 历史审查/阶段报告结论
- `perf/`、`security/`、`delivery/chaos-drill/`、`delivery/backup-restore/` 脚本
- 既有已执行 SQL 迁移 V001-V023（V024 可修正或回退，因尚未生产执行）
- 真实账号/令牌/连接串（用占位符 `${ENV_VAR}`）

---

## 5. 返工任务

### P1 阻断（必修）

#### R-1（P1-1）MFA/证书/SSO state 落库 + V024 接入
- **现状**：`AdvancedAuthService:29-32` 用 ConcurrentHashMap 存 mfa/challenges/certificates/ssoStates，V024 表全代码库无读写。重启 -> `mfa_lastCounter` 丢失 -> TOTP 重放防护失效；证书绑定丢失。Flyway 标记 V024 已执行给人"持久化就绪"假象。
- **修复**：接入 V024 表（JdbcTemplate/Repository）：
  - `t_user.mfa_secret_cipher`/`mfa_enabled`/`mfa_last_counter`：MFA 绑定状态与重放 counter 持久化
  - `t_user_certificate`：证书绑定持久化
  - `ssoStates` 可保留内存（短期 state），但 `challenge` 须持久化或改无状态（如签名 token）
- 重启后 lastCounter 不丢，重放防护持续生效
- 若不落库，则回退删除 V024（避免死迁移假象），但 MFA 重放须用其他持久化方式
- **补测试**：重启后旧 TOTP 码重放拒绝

#### R-2（P1-2）证书链校验
- **现状**：`AdvancedAuthService:167` `certificate.verify(certificate.getPublicKey())` 只接受自签名，拒绝 CA 签发证书（openssl 实证）。
- **修复**：移除 `verify(ownPublicKey)`；实现真实证书链校验：
  - 加载 TrustStore/CA 公钥（配置 `${security.cert.truststore}` 占位符）
  - 验证证书由信任 CA 签发（PKIX/CertPath）
  - 保留 `checkValidity`（有效期）
  - 补 CRL/OCSP（或标注待机构 CA 配置）
- **补测试**：CA 签发证书通过、自签名拒绝、过期/吊销拒绝

#### R-3（P1-3）sm4Key 强制注入
- **现状**：`AdvancedAuthService:35` 默认 `"change-me-in-env"`；`Sm4Util.normalizeKey` SHA-256 截 16 -> 确定性已知密钥，MFA secret 等同明文。
- **修复**：移除默认值，`@Value("${security.mfa.sm4-key}")`（无默认）；启动 fail-fast（未配置抛异常禁用启动）；application.yml 不写明文（用 `${ENV}` 占位）
- **补测试**：未配置 sm4Key 启动失败

#### R-4（P1-4）路由 + 白名单
- **现状**：Gateway `application.yml:19` 谓词不含 `/api/v1/auth/**` -> 网关 404；`JwtAuthFilter:28-33` 白名单仅 `/auth/**`，过滤器注册 `/*` -> 直接访问 `/api/v1/auth/login/mfa`、`/login/cert`、`/sso/redirect`、`/sso/callback` 401。
- **修复**：
  - Gateway `application.yml` 加 `/api/v1/auth/**` 路由
  - `JwtAuthFilter` **仅**对公开端点加白：`/api/v1/auth/login/mfa`、`/login/cert`、`/sso/redirect`、`/sso/callback`
  - `bind`/`confirm`/`unbind`/`revoke`/`rotate` 仍需鉴权（**不得整段加白**，防 R-8 激活）
- **补测试**：公开端点可达、bind 等未授权拒绝

#### R-5（P1-5）configureQuota 越权
- **现状**：`PartnerAbacAspect:25-36` consumerOwnership 对 configureQuota 应用"非管理员仅访问自己" -> 消费方可把 `maxRequests` 设 `Long.MAX_VALUE` 绕过配额（影响计费）。
- **修复**：configureQuota 从 consumerOwnership 切面移除，或对写操作强制 `administrative`（`system:update`）；消费方不可改自己配额
- **补测试**：消费方自改配额被拒（403）

#### R-6（P1-N1）证书私钥 PoP
- **现状**：`certLogin` 只接收 PEM（公开证书），无 challenge/签名验证调用方持私钥。任何获 bound 证书 PEM 者（日志/备份/无 TLS MITM）可登录。
- **修复**：certLogin 增加私钥占有证明：服务端发 challenge -> 客户端用私钥签名 -> 服务端用证书公钥验签
- **补测试**：无 PoP 拒绝、有 PoP 通过

#### R-7（P1-N2）权限实时回查
- **现状**：`bindCertificate:104` 存 `principal.permissions()` 快照；`certificateLogin:125` 用快照颁 JWT，不回查。管理员降权/撤销后仍可凭证书获旧权限。
- **修复**：`certificateLogin` 回查用户当前权限（`AuthService` 查 `t_user_permission`），不用快照
- **补测试**：降权后证书权限失效

#### R-8（P1-8）SSO Mock 非生产默认
- **现状**：`AdvancedAuthService:38` 生产构造器硬编码 `new MockSsoAdapter`；`MockSsoAdapter:18-22` callback 把 `code` 当 username -> `/sso/callback?code=admin` 获 admin JWT -> 绑 MFA 接管账号。当前因 R-4 不可达，但整段加白即激活。
- **修复**：
  - `MockSsoAdapter` 加 `@ConditionalOnProperty`/Profile（仅 dev/test）；生产无真实 Adapter 时 fail-fast 或抛异常
  - `ssoCallback` 不得直接信任 `code` 为 username（真实 SSO 用 code 换 token，从 token 取 username）
- **补测试**：生产无 Mock 装配、SSO 任意 username 拒绝

### P2 关键（顺带修复）

#### R-9（P2-7）MFA 强制策略
- 评估等保三级强制 MFA 要求；`cert`/`SSO` 登录纳入 MFA 强制切面（`MfaLoginEnforcementAspect` 扩展或新切面，当前 `execution(* AuthController.login(..))` 只拦账号密码）
- 若强制：未启用 MFA 用户拒绝登录或强制绑定流程

#### R-10（P2-N3）并发重放
- `completeChallenge:61,66-69` 在 synchronized 块外捕获 binding，两线程持同一引用读陈旧 lastCounter -> 同一 TOTP（30s 窗口）可被两 challenge 并发消费
- 修复：synchronized 块内重读 map，或用 `ConcurrentHashMap.merge` 原子更新 lastCounter
- **补测试**：并发同 TOTP 拒绝第二个

#### R-11（P2-N4）list 越权
- `PartnerAbacAspect` 未覆盖 `ConsumerController.list`，非管理员可全量枚举消费方 code/name/bizLine/status
- 修复：`list` 加 ABAC 过滤（非管理员仅返回自己的），或限制返回字段
- **补测试**：非管理员 list 仅返回自己

### P3（可选，建议但不阻断本轮）

| # | 问题 | 修复 |
|---|---|---|
| R-12 | CN `contains` 子串匹配（`AdvancedAuthService:168`） | X500Principal 解析 CN，`equals` 而非 `contains` |
| R-13 | completeChallenge 验码前 remove challenge（DoS） | 验码通过后再 remove |
| R-14 | MfaLoginEnforcementAspect 先 proceed+审计再判 MFA | MFA 完成后再审计登录成功 |
| R-15 | SSO callback GET，code/state 走 URL | 改 POST，参数走 body |
| R-16 | 不存在 id 返 404、无权限返 403 -> 资源枚举 | 统一返回（防存在性探测） |

---

## 6. 测试要求

当前 83.93% 覆盖 happy path，**核心攻击路径基本未测**。返工必须补充攻击路径测试：

| 返工项 | 必测攻击路径 |
|---|---|
| R-1 | 重启后旧 TOTP 码重放拒绝 |
| R-2 | CA 证书通过 / 自签名拒绝 / 过期 / 吊销 |
| R-3 | 未配置 sm4Key 启动失败 |
| R-4 | 公开端点可达 + bind 等未授权拒绝 |
| R-5 | 消费方自改配额拒绝 |
| R-6 | 无 PoP 拒绝 / 有 PoP 通过 |
| R-7 | 降权后证书权限失效 |
| R-8 | 生产无 Mock + SSO 任意 username 拒绝 |
| R-10 | 并发同 TOTP 拒绝第二个 |
| R-11 | 非管理员 list 仅自己 |

- 新增返工代码覆盖率 ≥80%，**攻击路径必须覆盖**
- 运行 `mvn test -pl platform-auth,platform-partner -am` 输出结果

## 7. 完成后输出

1. R-1~R-8 逐项修改位置与说明
2. R-9~R-11 修改位置（如修复）；R-12~R-16（如顺带）
3. 攻击路径测试清单与结果（通过数/失败数/覆盖率/失败明细）
4. DDL 变更（V024 修正或新版本号）
5. 偏离说明（无偏离明示）
6. 潜在风险（如 CRL/OCSP 待机构 CA、SSO 真实联调待外部）
7. 明确 G-S01 仍 `BLOCKED`，不声明生产通过

## 8. 返工规则

1. 只修复 R-1~R-11（+ 可选 R-12~R-16），不额外改动
2. 同一返工不超过 3 次
3. 完成后 Claude Code 对抗式复审（含独立子代理第二意见）
4. 通过后方可提交 + PR + 合并 master
5. G-S01 仍须 PV-SEC PROD_EQ + 安全负责人复核；A4 真实联调待机构 IAM/SSO 规范
6. 不签发正式上线批准

---

## 附录：与原任务单/审查报告的对应

- 原任务单 `tasks/codex-task-fix-01.md` A1-A4 对应审查报告 §4 满足情况
- 返工项 R-1~R-16 对应审查报告 §11 返工清单
- 审查报告 `reviews/claude-review-fix-01.md` §8 对抗式发现（含子代理 fix01-adversary 结论）
