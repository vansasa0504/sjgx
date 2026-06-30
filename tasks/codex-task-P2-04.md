# P2-04 Codex 实现任务单 — 安全扫描

> 主控：Claude Code　执行：Codex
> 依据：`tasks/claude-plan-P2-04.md`（第一性原理计划，权威）、`docs/requirements.md` §3.3
> 前置：P2-03 已合入 master（`3e80f693`）；P0-04/P0-08 已满足
> 分支：`ai/p2-security-scan`
> 日期：2026-06-30

---

## 0. 必读

1. `AGENTS.md`（职责边界）
2. `tasks/claude-plan-P2-04.md`（本任务第一性原理计划，权威）
3. `docs/requirements.md` §3.3（NFR-S01~S03）
4. `security/owasp-zap.md`、`security/manual-pentest-checklist.md`（现有安全产物）
5. `pom.xml`（根依赖）、各模块 `pom.xml`、`platform-ui/package.json`（SCA 扫描对象）
6. `platform-common/.../web/XssFilter.java`、`security/SignatureUtil.java`、`audit/AuditLogAspect.java`
7. `platform-pipeline/.../ingest/adapter/DbAdapter.java`（SQL 注入防护）
8. `platform-partner/.../consumer/ConsumerController.java`、`platform-pipeline/.../service/DataServiceController.java`（IDOR 测试目标）
9. 各模块 MockMvc 测试（越权 403 既有模式参考）

---

## 1. 任务目标

主动发现并处置漏洞，确保"无高危未处置"。SCA 真实扫描组件 CVE（开发环境可执行，必须真实跑出报告），补强应用安全测试缺口（IDOR 越权），完善 DAST 脚本与渗透清单，诚实标注 DAST/等保待部署与 MFA/SSO/ABAC 功能缺口。通过标准：**无高危未处置（SCA 高危处置 + DAST/等保待部署标注）**。

**核心原则**：SCA 必须真实执行产出报告，不虚构；应用漏洞 DAST 待部署环境诚实标注；功能缺口（MFA/SSO/ABAC）显式标注留后续。

---

## 2. 实现边界（严格遵守）

**范围决策（已确认）**：
- SCA：dependency-check-maven + npm audit，不引 Trivy。
- IDOR：补 MockMvc 测试。
- XSS body：标注限制，不改造 XssFilter。
- MFA/SSO/ABAC：标注缺口，不实现。
- DAST/等保：标注待部署/第三方，不跑 ZAP 全扫描。

**只做**：F-1 ~ F-7（见下）。
**不做**：
- 不实现 MFA/SSO/ABAC。
- 不引 Trivy/Snyk 等额外 SCA 工具。
- 不做等保三级测评（第三方）。
- 不修改 `.env`、真实密钥、生产配置。
- 不在开发环境跑 ZAP 全扫描。
- 不改造 XssFilter 覆盖 body。
- 不重构无关模块。

---

## 3. 任务清单

### F-1　引入 SCA 工具 — `pom.xml` + `platform-ui/package.json`

1. **后端**：根 `pom.xml` 加 `org.owasp:dependency-check-maven` 插件（最新稳定版 9.x）：
   - 配置 `formats=HTML,JSON`、`outputDirectory=security/reports`。
   - `failBuildOnCVSS` 设为 9（高危 CVSS≥9 阻断构建）——但放在独立 profile（如 `security`）避免普通构建被阻断。
   - NVD API key 支持环境变量外置（`NVD_API_KEY`，可选，无 key 也能跑但慢）。
2. **前端**：`platform-ui/package.json` scripts 加 `"audit": "npm audit --audit-level=high"`。
3. **不引入** Trivy/Snyk。

### F-2　跑 SCA + 高危处置 — `security/reports/`

1. 执行 `mvn dependency-check:check -Psecurity`（或插件直接调用）+ `cd platform-ui && npm audit`。
2. 收集漏洞报告：
   - `security/reports/dependency-check-report.html`、`.json`。
   - `security/reports/npm-audit.txt`（或 json）。
3. **高危处置**（CVSS≥7）：
   - 有补丁：升级依赖版本（如 Spring Boot 3.2.5→3.2.x 最新补丁），全量回归。
   - 无补丁：标注缓解措施（如禁用功能、配置限制）。
   - 误报：标注 `suppression` 或说明。
4. **国产驱动特殊处理**：达梦/oceanbase 驱动 CVE 库覆盖弱，标注"需厂商确认"。
5. **若 SCA 工具因 NVD 库下载失败**：标注"需联网更新 NVD"，提供本地缓存或降级手动核实方案，不虚构结果。
6. 产出 `security/reports/sca-summary.md`：漏洞列表 + CVSS + 处置状态（已升级/缓解/误报/待厂商）。

### F-3　IDOR 越权测试 — `platform-partner`/`platform-pipeline` test

补水平越权测试（现有仅"无 token/无权限码→403"，无"低权限访问他人资源"）：

1. **场景设计**：
   - 用户 A（admin 或高权限）创建 consumer=1、service=svc-A。
   - 用户 B（低权限，如 viewer 角色）持有效 token。
   - B 用自己的 token 访问 A 的资源：`GET /api/v1/consumers/1/logs`、`GET /api/v1/services/svc-A/logs`。
   - 断言：403（权限不足）或数据隔离（不返回 A 的数据）。
2. **实现**：MockMvc，构造两个用户的 token，验证 B 不能访问 A 的资源。
3. **若发现真越权**：修复 Controller/Service（加资源所有权校验）+ 测试覆盖，升级为返工项。
4. **覆盖范围**：至少 consumer logs、service logs 两个端点。

### F-4　XSS body 标注限制 — 不改代码

**不改造 XssFilter**。在 `security/manual-pentest-checklist.md` 和 `security/p2-04-report.md` 中明确标注：
- `XssFilter` 当前覆盖 parameter/header，未覆盖 request body（JSON payload 不转义）。
- 缓解：前端输出转义 + JSON 由 Jackson 反序列化（非 HTML 上下文，XSS 风险较低）。
- 后续增强：扩展 XssFilter 覆盖 body 或在输出层转义，列为后续任务。

### F-5　DAST 脚本完善 — `security/`

1. **`security/owasp-zap.md`** 更新：
   - 参数化目标 URL、认证 token、报告路径。
   - 标注"待部署环境执行，开发环境不跑全扫描"。
   - 补扫描策略（覆盖端点列表、认证方式）。
2. **`security/manual-pentest-checklist.md`** 更新：
   - 各项标注"开发验证（单测）"vs"待部署（DAST）"。
   - 新增 IDOR 行（F-3 已覆盖，标"开发验证"）。
   - XSS body 限制行（F-4）。
3. **新增 `security/run-zap.sh`**（可选）：ZAP 容器化扫描脚本，`bash -n` 语法检查，待部署执行。

### F-6　配置安全 — `.env.example` + 弱默认值审查

1. **新增 `.env.example`**：列出所有需环境变量外置的配置项（`JWT_SECRET`、`*_DB_PASSWORD`、`DATA_ASSET_SM4_KEY`、`API_CREDENTIAL_SM4_KEY`、`MINIO_SECRET_KEY` 等），标注"生产必须覆盖，不得使用默认值"。
2. **弱默认值审查**：在 `security/p2-04-report.md` 列出所有弱默认值（`JWT_SECRET=change-me-in-env`、`DATA_ASSET_SM4_KEY` 默认、docker-compose 弱口令），标注"开发用，生产必须覆盖"。
3. **不修改** `.env`（不存在）、真实密钥、生产配置——仅提供模板与审查清单。

### F-7　安全扫描报告 — `security/p2-04-report.md`

1. **SCA 实测**：dependency-check + npm audit 漏洞列表 + 处置状态（已升级/缓解/误报/待厂商）。
2. **DAST 待部署**：ZAP 待部署环境，渗透清单状态。
3. **NFR-S 对照表**：
   - S01（MFA/IAM/SSO/RBAC+ABAC）：RBAC 已实现；MFA/SSO/ABAC 标注功能缺口。
   - S02（TLS/SM4/脱敏/审计）：SM4/脱敏/审计已实现+单测；TLS 标注待部署。
   - S03（SQL注入/XSS/CSRF/等保）：SQL注入/XSS/CSRF 已实现+单测；等保三级标注第三方测评。
4. **限制说明**：XSS body 未覆盖、IDOR 测试范围、国产驱动 CVE 覆盖弱、MFA/SSO/ABAC 缺口。
5. **生产补测清单**：ZAP DAST、等保三级测评、MFA/SSO/ABAC 实现、弱默认值生产覆盖。

---

## 4. 测试要求

1. **F-1/F-2 SCA**：`mvn dependency-check:check -Psecurity` + `npm audit`，真实报告产出。
2. **F-3 IDOR**：MockMvc 低权限用户访问他人资源，断言 403/隔离。
3. **F-5 脚本**：`bash -n security/run-zap.sh`（若新增）。
4. **回归**：`mvn test` 全量 + `cd platform-ui && npm run test:unit`（依赖升级后必须回归）。
5. **测试命令**：
   ```bash
   mvn test
   cd platform-ui && npm run test:unit
   mvn dependency-check:check -Psecurity   # SCA
   cd platform-ui && npm audit             # 前端 SCA
   ```

---

## 5. 输出要求（完成后提交给 Claude Code 审查）

1. 修改/新增文件清单。
2. SCA 真实报告（dependency-check HTML/JSON + npm audit + sca-summary.md），高危处置证据（升级版本/缓解）。
3. F-3 IDOR 测试结果（mvn 输出）。
4. F-5 DAST 脚本 `bash -n` 证据 + 渗透清单更新。
5. `.env.example` + 弱默认值审查清单。
6. 安全扫描报告 `security/p2-04-report.md`。
7. 潜在风险与未实测项（DAST、等保、MFA/SSO/ABAC、国产驱动 CVE）。
8. 偏离说明。

---

## 6. 共性约束

- 不破坏 P0/P1/P2-01~P2-03 既有闭环。
- 不修改 `.env`、真实密钥、生产配置、`docs/`（security 下报告除外）、`tasks/`（本任务单除外）、`reviews/`、`k8s/prod/`、`delivery/`。
- SCA 必须真实执行，不虚构漏洞结果。
- 依赖升级后必须全量回归（mvn test + 前端测试）。
- 不得在报告或脚本中泄露真实密钥/连接串。
- 上线前门禁：生产 ZAP DAST、等保三级测评、MFA/SSO/ABAC 实现（本任务标注未实测/缺口，不替代上线门禁）。

---

## 7. 验收标准（对齐 claude-plan-P2-04 §9）

- [ ] SCA 真实报告产出（dependency-check + npm audit），高危漏洞处置。
- [ ] IDOR 越权测试通过（低权限访问他人资源被拒/隔离）。
- [ ] DAST 脚本可执行（待部署），渗透清单更新。
- [ ] `.env.example` 提供，弱默认值审查标注。
- [ ] 报告诚实分层：SCA 实测 / DAST 待部署 / 功能缺口。
- [ ] 无高危未处置（SCA 高危已处置；DAST/等保待部署标注）。
- [ ] `mvn test` + 前端测试全绿，无回归。
