# P2-04 第一性原理开发计划 — 安全扫描

> 阶段：P2（生产强化）第四任务
> 依据：`docs/development-process-workflow.md` §3.3、`tasks/phase-task-checklist.md` §4、`docs/requirements.md` §3.3（NFR-S01~S03）、`security/`
> 前置：P2-03 已合入 master（`3e80f693`）；P0-04（API 凭证）、P0-08（审计防篡改）已满足
> 日期：2026-06-30
> 分支：`ai/p2-security-scan`（建议）

---

## 1. 需求来源

### 1.1 任务口径

| 项 | 内容 |
|---|---|
| 编号 | P2-04 |
| 主题 | 安全扫描 |
| 依赖 | P0-04（API 凭证）、P0-08（审计防篡改） |
| 涉及模块 | `security/`、`pom.xml`/`platform-ui/package.json`（SCA）、`platform-common`/`platform-pipeline`/`platform-auth`（安全测试） |
| 输出 | SCA/渗透测试报告 |
| 通过标准 | **无高危未处置** |

### 1.2 安全指标基线（requirements.md §3.3）

| NFR | 指标 |
|---|---|
| S01 | MFA、IAM/SSO、RBAC+ABAC 字段级、OAuth2.0/API Key/证书 |
| S02 | TLS1.2+、国密SM4存储加密、脱敏（动态+静态）、审计≥3年不可篡改 |
| S03 | SQL注入/XSS/CSRF防护、流量清洗防刷、等保三级 |

### 1.3 触发事实（调研发现）

1. **无任何 SCA 工具**：grep `dependency-check|owasp|snyk|trivy` 全无命中，需从零引入。
2. **DAST 待部署**：`security/owasp-zap.md` 明确"M6 外部环境执行，不虚构结果"。
3. **安全防护代码已有但存缺口**：
   - ✅ SQL 注入（DbAdapter 白名单 + JdbcTemplate 参数化）、XSS（XssFilter）、签名/重放（SignatureUtil）、RBAC 越权 403、SM4 加密、审计脱敏+哈希链——均有单测。
   - ⚠️ **无水平越权（IDOR）测试**：现有仅"无 token/无权限码→403"，无"低权限用户访问他人资源"。
   - ⚠️ **XssFilter 未覆盖 request body**（JSON payload 不转义）。
   - ⚠️ **无 MFA/SSO/ABAC**（NFR-S01 功能缺口，非扫描项但需标注）。
4. **依赖 CVE 风险待核实**：Spring Boot 3.2.5、easyexcel 3.3.4、oceanbase/达梦驱动——无 fastjson/log4j/commons-collections（好消息），但需 SCA 工具核实。
5. **缺 `.env.example`**：配置密钥走环境变量，但无模板；部分弱默认值（`JWT_SECRET=change-me-in-env`、`DATA_ASSET_SM4_KEY` 默认）。

---

## 2. 第一性原理分析

### 2.1 用户真正要解决什么？

招采验收要求"无高危未处置"+ NFR-S01~S03 安全达标。"安全扫描"本质是**主动发现并处置漏洞**，分两个维度：①组件漏洞（SCA，扫依赖 CVE）——开发环境可真实执行；②应用漏洞（DAST/渗透，扫运行时）——部分开发可验证（单测），全扫描待部署。**本质：用 SCA 工具跑出真实组件漏洞报告 + 补强应用安全测试缺口 + 诚实标注 DAST/等保待部署，确保无高危未处置**。

### 2.2 核心区别于 P2-02/P2-03

SCA 是**开发环境可真实执行并产出真实报告**的（扫描 pom.xml/package.json 依赖 CVE），不需生产集群。这是 P2-04 的核心可交付，必须真实跑出结果而非"待填充"。

### 2.3 最小可行结果

1. **SCA 真实报告**：引入 OWASP dependency-check（Maven）+ `npm audit`（前端），跑出真实组件漏洞报告，高危处置。
2. **安全测试缺口补强**：IDOR 越权测试、XSS body 覆盖（如可行）。
3. **DAST 脚本完善 + 报告**：ZAP 脚本可执行（待部署环境），手工渗透清单更新。
4. **配置安全**：`.env.example` 模板 + 弱默认值审查。
5. **诚实标注**：SCA 已实测 / DAST/等保待部署 / MFA/SSO/ABAC 功能缺口。

### 2.4 系统必须接收哪些输入？

- SCA 工具配置（dependency-check 插件 + npm audit）。
- 安全测试用例（IDOR、XSS body）。
- DAST 扫描目标（待部署环境地址）。
- 依赖清单（pom.xml/package.json）。

### 2.5 系统必须产生哪些输出？

- SCA 报告（dependency-check HTML + npm audit，真实漏洞列表 + 处置）。
- 补强的安全测试（IDOR、XSS）+ 测试结果。
- DAST 脚本（ZAP）+ 渗透清单（更新）。
- `.env.example` + 配置安全审查。
- 安全扫描报告（SCA 实测 + DAST 待部署 + 缺口标注）。

### 2.6 从输入到输出不可省略的处理过程

1. **引入 SCA**：`pom.xml` 加 `org.owasp:dependency-check-maven` 插件；前端 `npm audit`。
2. **跑 SCA**：执行 dependency-check + npm audit，收集漏洞列表。
3. **处置高危**：升级有 CVE 的依赖，或标注处置方案（无补丁则缓解措施）。
4. **补 IDOR 测试**：低权限用户访问他人资源，验证 403/隔离。
5. **补 XSS body 测试**（如可行）：JSON payload 注入。
6. **完善 DAST**：ZAP 脚本参数化，渗透清单更新"开发验证/待部署"。
7. **配置安全**：`.env.example` + 弱默认值审查。
8. **出报告**：SCA 实测 + DAST 待部署 + NFR-S 对照 + 缺口。

### 2.7 哪些是核心能力？

- SCA 真实扫描 + 高危处置（开发可执行）。
- IDOR 越权测试补强。
- 安全扫描报告（诚实分层）。

### 2.8 哪些是增强能力？

- ZAP DAST 全扫描（待部署环境）。
- 等保三级测评（第三方机构）。
- MFA/SSO/ABAC 实现（功能缺口，非扫描项）。
- Trivy/Snyk 等额外 SCA 工具。

### 2.9 当前代码库最小改动路径

- **`pom.xml`**：加 `dependency-check-maven` 插件（profile 或 plugin）。
- **`platform-ui`**：`npm audit` 脚本（package.json scripts）。
- **新增 IDOR 测试**：`platform-partner`/`platform-pipeline` MockMvc，低权限用户访问他人 consumer/service 资源。
- **补 XSS body 测试**（如 XssFilter 可扩展）：或标注限制。
- **`.env.example`**：新增配置模板。
- **`security/`**：更新 owasp-zap.md、manual-pentest-checklist.md，新增 `security/p2-04-report.md`、SCA 报告产物。

### 2.10 如何测试？

- SCA：`mvn dependency-check:check` + `npm audit`，真实漏洞列表。
- IDOR：MockMvc 低权限用户访问他人资源，断言 403/隔离。
- XSS：既有 XssFilterTest + 新增 body 场景（如可行）。
- 既有安全测试回归。

### 2.11 如何验收？

- SCA 真实报告产出，高危漏洞处置（升级或缓解）。
- IDOR 测试通过。
- DAST 脚本可执行（待部署），渗透清单更新。
- `.env.example` 提供，弱默认值审查。
- 报告诚实分层：SCA 实测 / DAST 待部署 / 功能缺口（MFA/SSO/ABAC）。
- 无高危未处置（SCA 高危已处置；DAST 待部署标注）。

### 2.12 如何避免过度设计？

- **不实现 MFA/SSO/ABAC**：属功能开发非扫描，标注缺口留后续。
- **不引多个 SCA 工具**：dependency-check + npm audit 足够，不引 Trivy/Snyk。
- **不做等保三级测评**：第三方机构，标注待测。
- **不改造 XssFilter 覆盖 body**（若范围大）：标注限制，留后续。
- **不在开发环境跑 ZAP 全扫描**：需部署环境，标注待部署。

---

## 3. 功能拆解

| 编号 | 任务 | 模块 | 说明 |
|---|---|---|---|
| F-1 | 引入 SCA 工具 | pom.xml + platform-ui | dependency-check-maven 插件 + npm audit 脚本 |
| F-2 | 跑 SCA + 高危处置 | security/reports | 真实漏洞报告 + 升级/缓解 |
| F-3 | IDOR 越权测试 | platform-partner/pipeline | 低权限访问他人资源，验证隔离 |
| F-4 | XSS body 测试（可选） | platform-common | JSON payload 注入，或标注限制 |
| F-5 | DAST 脚本完善 | security | ZAP 参数化 + 渗透清单更新 |
| F-6 | 配置安全 | .env.example + 审查 | 模板 + 弱默认值审查 |
| F-7 | 安全扫描报告 | security/p2-04-report.md | SCA 实测 + DAST 待部署 + NFR-S 对照 + 缺口 |

---

## 4. 影响模块

| 模块 | 改动类型 | 风险 |
|---|---|---|
| `pom.xml` | 加 dependency-check 插件 | 低，构建插件 |
| `platform-ui/package.json` | npm audit 脚本 | 低 |
| `platform-partner/pipeline` test | IDOR 测试 | 低，新测试 |
| `platform-common` test | XSS body 测试（可选） | 低 |
| `security/` | 报告 + 脚本更新 | 低 |
| `.env.example` | 新增 | 低 |

---

## 5. 接口设计

### 5.1 SCA 工具

```xml
<!-- pom.xml, profile 或 plugin -->
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <version>9.x</version>
  <configuration>
    <formats>HTML,JSON</formats>
    <outputDirectory>security/reports</outputDirectory>
    <failBuildOnCVSS>9</failBuildOnCVSS>  <!-- 高危(CVSS>=9)阻断 -->
  </configuration>
</plugin>
```

```json
// platform-ui/package.json scripts
"audit": "npm audit --audit-level=high"
```

### 5.2 IDOR 测试

```text
用户 A 的 consumerId=1，用户 B 低权限 token
GET /api/v1/consumers/1/logs  (B 的 token)
  -> 403 或数据隔离（不返回 A 的数据）
```

---

## 6. 异常场景

| 场景 | 处理 |
|---|---|
| SCA 工具下载 NVD 库慢/失败 | 标注"需联网更新 NVD"，提供本地缓存方案或降级手动核实 |
| 依赖有 CVE 无补丁 | 标注缓解措施（如禁用相关功能），不阻断 |
| 国产驱动 CVE 库覆盖弱 | 标注"达梦/oceanbase 驱动 CVE 库覆盖弱，需厂商确认" |
| IDOR 测试发现真越权 | 修复 + 测试覆盖（升级为返工） |
| ZAP 无法在开发跑 | 标注待部署环境 |
| 弱默认值无法移除（开发便利） | `.env.example` 标注"生产必须覆盖" |

---

## 7. 测试策略

1. F-1/F-2 SCA：`mvn dependency-check:check` + `npm audit`，真实报告。
2. F-3 IDOR：MockMvc 低权限用户访问他人资源，断言 403/隔离。
3. F-4 XSS body：JSON payload 注入测试（如可行）。
4. F-5 DAST：ZAP 脚本语法/参数检查，待部署执行。
5. 回归：`mvn test` 全量 + 前端 `npm run test:unit`。

---

## 8. Codex 实现边界

Codex 须在 `tasks/codex-task-P2-04.md` 中实现，且**仅限**：

1. F-1 引入 SCA 工具（dependency-check + npm audit）。
2. F-2 跑 SCA + 高危处置（升级/缓解 + 报告）。
3. F-3 IDOR 越权测试。
4. F-4 XSS body 测试（或标注限制）。
5. F-5 DAST 脚本完善 + 渗透清单更新。
6. F-6 `.env.example` + 弱默认值审查。
7. F-7 安全扫描报告。

**不得做**：
- 不实现 MFA/SSO/ABAC（功能缺口标注）。
- 不引多个 SCA 工具（Trivy/Snyk）。
- 不做等保三级测评（第三方）。
- 不修改 `.env`、真实密钥、生产配置。
- 不在开发环境跑 ZAP 全扫描（待部署）。
- 不重构无关模块。

---

## 9. 验收标准

- [ ] SCA 真实报告产出（dependency-check + npm audit），高危漏洞处置。
- [ ] IDOR 越权测试通过（低权限访问他人资源被拒/隔离）。
- [ ] DAST 脚本可执行（待部署），渗透清单更新。
- [ ] `.env.example` 提供，弱默认值审查标注。
- [ ] 报告诚实分层：SCA 实测 / DAST 待部署 / 功能缺口。
- [ ] 无高危未处置（SCA 高危已处置；DAST/等保待部署标注）。
- [ ] `mvn test` + 前端测试全绿，无回归。

---

## 10. 风险与回滚

| 风险 | 等级 | 控制 |
|---|---|---|
| SCA 工具 NVD 库下载失败 | 中 | 提供缓存/降级方案，标注 |
| 依赖升级破坏构建 | 中 | 升级后全量回归；保留旧版本可回滚 |
| IDOR 测试发现真越权 | 高 | 修复 + 测试（升级返工） |
| 弱默认值移除影响开发 | 低 | `.env.example` 标注，保留开发默认 |
| 国产驱动 CVE 覆盖弱 | 中 | 标注厂商确认 |

**回滚**：SCA 插件可移除；依赖升级可回滚版本；测试改动可还原。

---

## 11. 下一步

本计划通过后，生成 `tasks/codex-task-P2-04.md`，按 F-1~F-7 拆解派发。

---

## 附：范围决策（已确认）

1. **SCA 工具**：✅ dependency-check-maven（后端）+ npm audit（前端），不引 Trivy。
2. **IDOR 测试**：✅ 纳入——补 MockMvc 测试，低权限用户访问他人 consumer/service 资源，验证 403/隔离。
3. **XSS body**：✅ 标注限制，不改造 XssFilter（JSON body 不转义留后续）。
4. **MFA/SSO/ABAC**：✅ 功能缺口标注，不实现。
5. **DAST/等保**：✅ 标注待部署/第三方测评，不在开发环境跑 ZAP 全扫描。
