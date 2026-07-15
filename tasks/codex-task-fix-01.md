# Codex 执行任务 - 批次1 安全认证修复

> 主控：Claude Code　执行：Codex
> 分支：`ai/fix-01-security-auth`
> 日期：2026-07-15
> 任务性质：代码修复（功能缺口实现）。来源 `tasks/codex-code-fix-backlog.md` 批次1（RISK-F-01/02/03/04）。
> 范围：`platform-auth` 安全认证能力补齐。**不执行生产/外部环境操作**。

---

## 1. 任务目标

补齐安全认证功能缺口（NFR-S01），使平台满足等保三级认证权限要求：

- **A1** MFA 多因素认证（RISK-F-01）
- **A2** 字段级/资源级 ABAC（RISK-F-03）
- **A3** 数字证书认证（RISK-F-04）
- **A4** IAM/SSO 适配器框架（RISK-F-02；真实联调待机构规范）

**实现后仍须 PV-SEC 补测卡（PROD_EQ）验证 + 安全负责人复核，方可解锁 G-S01 门禁。本任务不签发正式上线批准。**

## 2. 需求依据（必读）

1. `AGENTS.md`
2. `docs/requirements.md` §6 权限与安全要求（MFA / IAM-SSO / RBAC+ABAC / OAuth2 / API Key / 证书）
3. `docs/technical-requirements.md` §3.3 安全
4. `tasks/requirement-analysis.md` §6 权限安全日志
5. `tasks/claude-plan.md` §4.3 技术选型 / §4.5.2 认证鉴权接口
6. `delivery/release-readiness/gap-and-dependency-register.md` RISK-F-01~04
7. `reviews/claude-review-P2-04.md`（安全扫描，IDOR/ABAC 缺口）
8. `tasks/codex-code-fix-backlog.md`（本批次来源）

## 3. 允许修改范围

- `platform-auth/src/main/java`、`src/main/resources`、`src/test`
- `platform-common/src/main/java`（如需共享安全工具/注解/ABAC 基础）
- `platform-pipeline`、`platform-partner` 等**仅限**接入新的权限注解/ABAC 切面（不重构业务逻辑）
- `db/migration`（如需用户/MFA/证书表 DDL，用**新版本号**，不改既有迁移）
- `k8s/dev`（如需本地开发配置）
- 不得引入 Flowable/Activiti 等大型工作流引擎；MFA 用标准 TOTP（RFC 6238）；证书用 Java KeyStore/BouncyCastle

## 4. 禁止修改范围

- `.env`/`.env.*`、密钥、证书、生产配置、`k8s/prod`
- `docs/`、`tasks/`（本任务单除外，不覆盖 Claude Code 的需求/计划判断）、`reviews/`
- P0-P2 历史审查报告与阶段报告中的结论
- `perf/`、`security/`、`delivery/chaos-drill/`、`delivery/backup-restore/` 脚本
- 既有 SQL 迁移脚本（只新增版本，不修改已执行迁移）
- 真实账号/令牌/连接串（用占位符 `${ENV_VAR}`）

## 5. 实现要求

### A1 MFA 多因素认证（RISK-F-01，NFR-S01）

- 登录流程：账号密码 -> 首步认证 -> MFA 二步（TOTP）-> 颁发 JWT
- 接口：`POST /api/v1/auth/login/mfa`（claude-plan §4.5.2 已列）
- 实现：TOTP 生成/校验（RFC 6238），MFA 密钥加密存储（复用 `Sm4Util`），MFA 绑定/解绑流程
- 用户表加 `mfa_secret`（加密）、`mfa_enabled` 字段
- 异常：MFA 未绑定 / 校验失败 / TOTP 重放（时间窗口）

### A2 字段级/资源级 ABAC（RISK-F-03，FR-304；NFR-S01）

- 现状（P2-04 审查）：仅 RBAC，IDOR 测试为 RBAC，无资源所有权/字段级控制
- 实现：扩展 `@RequirePermission` 或新增 `@DataScope`/`@FieldAccess` 注解 + AOP 切面
  - **资源级**：数据所有权校验（消费方只能访问自己的配额/日志/申请）
  - **字段级**：按角色/属性脱敏或过滤字段（如合作方 `credential` 仅管理员可见，复用 `DesensitizeUtil`）
- 水平越权测试：低权限用户访问他人资源 -> 403 拒绝

### A3 数字证书认证（RISK-F-04，NFR-S01）

- 接口：`POST /api/v1/auth/login/cert` 或双向 TLS 证书认证
- 实现：证书校验（CN / 有效期 / 吊销 / 证书链），证书绑定用户
- 证书生命周期：绑定 / 吊销 / 轮换
- 异常：证书过期 / 吊销 / 未绑定 / 伪造

### A4 IAM/SSO 适配器框架（RISK-F-02，NFR-S01/C02）

- 现状：未实现/未联调，机构 IAM/SSO 规范未提供（RISK-X）
- 实现：`SsoAdapter` 接口 + 默认 Mock 实现（待机构规范后替换）
- 接口：`GET /api/v1/auth/sso/redirect`、`GET /api/v1/auth/sso/callback`（claude-plan §4.5.2）
- 框架可配置（Issuer/ClientId/Secret 占位符），真实联调待机构规范
- **不伪造联调通过**；保持 RISK-F-02 为 `FUNCTION_GAP`/`BLOCKED` 直到真实联调

## 6. 测试要求

- **A1**：MFA 绑定/校验成功/失败、TOTP 重放拒绝、MFA 密钥加密存储验证、登录二步流程
- **A2**：水平越权拒绝（低权限访问他人资源）、字段级脱敏/过滤、ABAC 注解正反例
- **A3**：证书认证成功/失败（过期/吊销/未绑定/伪造）、证书生命周期
- **A4**：SSO 适配器框架单测（Mock）、redirect/callback 流程、配置占位符
- 整体：权限矩阵测试（角色 × 资源 × 字段），未授权/越权/过期 Token
- 覆盖率：新增代码 ≥80%，核心路径 100%
- 阶段完成后运行 `mvn test -pl platform-auth`（及相关模块）并输出结果

## 7. 完成后输出

1. 修改/新增文件清单
2. A1/A2/A3/A4 逐项实现说明（类/接口职责）
3. 测试命令与结果（通过数/失败数/覆盖率/失败明细）
4. DDL 变更（如有，标注新版本号）
5. 偏离计划说明（无偏离则明示）
6. 潜在风险与遗留问题（如 A4 真实联调待机构规范）
7. 明确当前 G-S01 仍 `BLOCKED`，不得因代码实现声明生产通过

## 8. 返工规则

1. Claude Code 审查 `git diff` + 测试结果后判定通过/返工
2. 返工只修复清单项，不额外改动
3. 同一批次返工不超过 3 次
4. 完成后停止，不自行执行 backlog 其他批次

---

## 附录：与门禁/补测卡的关系

- 实现 A1-A4 后，G-S01 仍 `BLOCKED`（须 PV-SEC PROD_EQ 验证 + 安全负责人复核）
- A4 真实联调待机构 IAM/SSO 规范（F-05 / RQ-01）
- 不签发正式上线批准。正式准入须由有权机构角色在取得环境/授权/实测证据/审批流程后作出。
