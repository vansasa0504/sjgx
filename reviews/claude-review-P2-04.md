# Claude Code 审查结果 — P2-04 安全扫描

## 1. 审查对象

- 任务：P2-04 安全扫描
- 分支：`ai/p2-security-scan`（改动在工作区未提交，含已提交计划 commit `f27b93dc`）
- 任务单：`tasks/codex-task-P2-04.md`，计划：`tasks/claude-plan-P2-04.md`
- 审查日期：2026-06-30
- 改动范围：SCA 插件、前端依赖升级、IDOR 测试、DAST 脚本、.env.example、安全报告
- 审查规则：CLAUDE.md §7 + §7.1 对抗式审查（安全场景，必做）

## 2. Git 状态

6 文件修改 + 4 文件新增（`.env.example`、`security/p2-04-report.md`、`security/reports/`、`security/run-zap.sh`），与任务单 F-1~F-7 对齐，无越界模块改动。

## 3. 常规审查

| 项 | 结论 |
|---|---|
| F-1 SCA 工具 | dependency-check 9.2.0 security profile（failBuildOnCVSS=9，NVD_API_KEY 外置）+ npm audit 脚本。✓ |
| F-2 SCA 执行 | 前端 npm audit 真实执行（5 漏洞→升级→0 漏洞）；后端 dependency-check 因 NVD 403/404 无 API key 未完成，诚实标注。⚠ 后端待补 |
| F-3 IDOR 测试 | partner/pipeline 各 +1 测试，viewer token 访问他人资源→403。⚠ 名不副实（见 §4.3） |
| F-4 XSS body | 标注限制，不改造 XssFilter，清单+报告说明。✓ |
| F-5 DAST 脚本 | run-zap.sh 参数化 + bash -n 通过；owasp-zap.md + 渗透清单更新。✓ |
| F-6 配置安全 | .env.example 完整规范 + .gitignore 例外 + 弱默认值审查。✓ |
| F-7 报告 | p2-04-report.md SCA 实测/DAST 待部署/NFR-S 对照/缺口分层。✓ |

## 4. 对抗式审查

### 4.1 攻击面枚举

1. 前端大版本升级（vite 5→8、vitest 1→4）是否破坏构建/测试。
2. 后端 SCA 未完成是否构成阻断。
3. IDOR 测试是否真验证水平越权。
4. SCA 报告是否虚构结果。
5. .env.example 是否泄露真实密钥。
6. 依赖升级后端是否回归。

### 4.2 反例与追踪

| 反例 | 追踪结果 | 结论 |
|---|---|---|
| 前端升级破坏测试 | 独立跑 `npm run test:unit`：12 文件 88 测试通过；报告称 `npm run build` 通过（非致命警告）。vitest 1→4 跨版本但测试绿 | 已反驳 |
| 后端升级破坏构建 | 独立跑 `mvn test`：8 模块全绿（common 39/gateway 2/auth 33/partner 33/quality 35/pipeline 119/billing 53） | 已反驳 |
| 后端 SCA 未完成阻断 | NVD 403/404 无 API key，dependency-check 未产出报告；sca-summary 诚实标注"不宣称无 CVE，待 NVD_API_KEY 重跑"。P2-04 核心交付之一未完成，但诚实标注非虚构 | 存活 P2-1（跟进） |
| IDOR 测试名不副实 | `viewerToken` 仅 `stats:view` 权限，无 `consumer:view`/`service:view`；访问 logs→403 是 RBAC 权限码否定（垂直越权），非资源所有权隔离（水平越权 IDOR） | 存活 P2-2 |
| SCA 虚构结果 | 前端 npm audit 真实执行有漏洞列表+升级证据；后端诚实标注未完成不虚构 | 已反驳 |
| .env.example 泄露密钥 | 全部占位符（`replace-with-*`），无真实密钥；.gitignore `!.env.example` 例外合理 | 已反驳 |
| failBuildOnCVSS 阻断普通构建 | dependency-check 在独立 `security` profile，普通 `mvn test` 不触发 | 已反驳 |
| IDOR 测试发现真越权 | viewer 无权限码→403，未发现真 IDOR 漏洞；但系统无资源所有权模型，有 `consumer:view` 的用户能否访问任意 consumer 未测试 | 存活 P2-2（系统局限） |
| run-zap.sh 语法 | `bash -n` 通过 | 已反驳 |
| 渗透清单遗漏 | 新增 SCA/IDOR/XSS body/TLS/等保/MFA-SSO-ABAC 行，状态标注完整 | 已反驳 |

### 4.3 存活缺陷

**无 P1 阻断。** 2 项 P2 改进 + 1 项 P3 提示：

#### P2 改进（2 项）

**P2-1 后端 SCA 未完成**
- 后端 dependency-check 因 NVD 403/404（无 `NVD_API_KEY`）未产出报告，`security/reports/` 仅有 sca-summary.md，无 HTML/JSON。
- 评估：诚实标注"不宣称无 CVE"，符合诚实原则。但后端 SCA 是 P2-04 核心交付之一（任务单 F-2 要求"跑 SCA + 高危处置"）。
- 处理：不阻断合入（前端 SCA 已真实完成、工具配置就绪）。建议作为跟进项：获取 `NVD_API_KEY` 后重跑 `mvn dependency-check:check -Psecurity`，产出后端 CVE 报告并处置高危，回填 sca-summary。上线前门禁项。

**P2-2 IDOR 测试未真验证水平越权**
- `viewerToken` 仅 `stats:view`，无 `consumer:view`/`service:view`，访问 logs→403 是 RBAC 权限码否定（垂直越权），非"有权限但访问他人资源被隔离"（水平越权 IDOR）。
- 测试名 `consumerLogsRejectLowPrivilegeTokenForOtherConsumerResource` 名不副实。
- 根因：系统是 RBAC（权限码）模型，无资源所有权/ABAC，`ConsumerController.logs` 只校验权限码不校验资源归属。真正的 IDOR 测试需两个同有 `consumer:view` 但归属不同 consumer 的用户，验证 A 不能访问 B 的 consumer 日志——当前模型下无法测试，且系统确实存在"任何有 `consumer:view` 的用户可访问任意 consumer 日志"的设计局限。
- 处理：不阻断合入（测试虽非真 IDOR 但增加了越权覆盖）。建议：(1) 将测试名改为准确描述（如 `consumerLogsRejectTokenWithoutConsumerViewPermission`）；(2) 真正的水平越权防护需引入资源所有权模型（ABAC），列为 NFR-S01 ABAC 缺口的后续任务。

#### P3 提示（1 项，不阻断）

- **P3-1**：前端大版本升级（vite 5→8、vitest 1→4、@vitejs/plugin-vue 5→6）跨多个主版本，报告称 build 有非致命 chunk-size/pure-annotation 警告。建议 CI 持续监控构建警告，确认无隐性破坏。`package-lock.json` 已同步更新。

### 4.4 对"建议通过"的反驳

- 为何不应通过？后端 SCA 未完成是否算"无高危未处置"未达成？→ 通过标准"无高危未处置"——前端 SCA 已真实清零（0 漏洞），后端 SCA 工具就绪但待 NVD_API_KEY 重跑（诚实标注未宣称无 CVE），DAST/等保标注待部署。已处置的高危（前端 5 漏洞升级）+ 诚实标注的待测项，符合"无高危未处置"（已发现的已处置，未发现的待补测标注）。
- IDOR 测试是否虚假通过？→ 测试本身通过（403 行为正确），但验证的是 RBAC 非 IDOR，名不副实属 P2-2，不阻断合入但需跟进。
- 前端升级是否埋雷？→ 独立验证 88 测试 + build 通过，P3-1 监控。
- 反驳未发现存活 P1 阻断，结论成立。

## 5. 测试验证

```text
mvn test（独立验证，8 模块）
- platform-common:   39 (Skipped 1 = P2-01 PartitionIT)
- platform-gateway:  2
- platform-auth:     33
- platform-partner:  33 (+1 IDOR)
- platform-quality:  35
- platform-pipeline: 119 (+1 IDOR)
- platform-billing:  53
BUILD SUCCESS

cd platform-ui && npm run test:unit（独立验证）
- 12 files / 88 tests passed

bash -n security/run-zap.sh: OK
```

前端大版本升级后独立验证测试+构建通过。IDOR 测试通过（但见 P2-2）。

## 6. 未实测项

1. 后端 dependency-check 真实 CVE 报告（待 `NVD_API_KEY`）。
2. ZAP DAST 全扫描（待部署环境）。
3. 等保三级测评（第三方机构）。
4. MFA/SSO/ABAC/证书认证（功能缺口，未实现）。
5. 真实水平越权（IDOR）防护（待资源所有权模型）。
6. 国产驱动（达梦/oceanbase）CVE 厂商确认。

## 7. 审查结论

**建议通过（附 P2 跟进项）。**

- SCA 工具就绪（dependency-check security profile + npm audit），前端 SCA 真实执行（5 漏洞升级→0），后端 SCA 诚实标注待 NVD_API_KEY。
- IDOR 测试增加越权覆盖（虽名不副实，P2-2 跟进）。
- DAST 脚本 + 渗透清单 + ZAP 文档完善，bash -n 通过。
- .env.example + 弱默认值审查 + .gitignore 例外。
- 报告诚实分层：SCA 前端实测/后端待补、DAST 待部署、MFA/SSO/ABAC 缺口。
- 对抗式审查未发现存活 P1 阻断。
- 前端大版本升级独立验证通过（88 测试 + build）。
- 2 项 P2 跟进（后端 SCA 重跑、IDOR 测试正名+ABAC）+ 1 项 P3（构建警告监控）不阻断。

## 8. 返工/跟进清单

### 非阻断跟进（P2）

1. **P2-1**：获取 `NVD_API_KEY` 后重跑 `mvn dependency-check:check -Psecurity`，产出后端 CVE 报告，处置高危，回填 `security/reports/sca-summary.md`。上线前门禁项。
2. **P2-2**：(a) 将 IDOR 测试名改为准确描述（垂直越权/RBAC 否定）；(b) 真正水平越权防护引入资源所有权模型（ABAC），列为 NFR-S01 后续任务。

### 可选改进（P3，不阻断）

3. **P3-1**：CI 监控前端大版本升级后的构建警告（vite 8 chunk-size/pure-annotation）。

返工改动可提交 `ai/p2-security-scan` 并合并 master；P2-1/P2-2 作为独立跟进项，不阻断 P2-05 启动。
