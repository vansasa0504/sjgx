# Codex 执行任务 - P0-04：API 凭证安全

> 阶段：P0（上线阻断修复）
> 任务编号：P0-04
> 分支建议：`ai/p0-api-credential`
> 依据：`docs/development-process-workflow.md` §3.1 P0-04、§5.2.5、§6.3、`docs/database-design.md`（`t_api_credential`）
> 前置：P0-03 通过（ApiCredentialRepository 已落库）
> 日期：2026-06-27

---

## 1. 背景与目标

M7-A 审查 S-02/F-08 指出：`/api/v1/services/{code}/invoke` 的 `secret` 由调用方在 body 明文传入（`InvokeRequest.secret()`），即 apiKey→secret 映射未实现，任何知道 secret 的人可调用。本任务实现 API 凭证安全：secret 密文存储（Sm4/KMS）、apiKey→secret 服务端查找、invoke 不再接收 secret、支持轮换与禁用。

**最小可行结果**：调用方仅传 apiKey + 签名，服务端按 apiKey 查 secret 校验签名；`t_api_credential` 表/日志/响应中无明文 secret；API Key 可轮换、可禁用。

## 2. 范围

### 本次实现
- `ApiCredentialRepository` JDBC 实现（P0-03 已落表，本任务深化）：secret 字段 Sm4 加密存储，存 `secret_hash` 用于校验，不存明文。
- `DataServiceManager.invoke` 改造：不再从 `InvokeRequest.secret()` 取 secret，改为按 `apiKey` 查 `t_api_credential` 取 secret 校验签名。
- `InvokeRequest` DTO 移除 `secret` 字段（破坏性变更，需同步前端/文档）。
- API Key 管理：新增 `/api/v1/services/{code}/credentials` 端点（创建/轮换/禁用），权限码 `service:update`。
- 凭证创建时返回一次性明文（仅此一次），后续只存密文+hash。
- 轮换：旧 key 标记 `disabled`，新 key 生效；禁用后不可调用。

### 不做
- 不做 KMS 真实对接（保留 Sm4 + 接口抽象，KMS 实现留 P2-04）。
- 不改签名算法（HMAC + nonce 防重放已有，仅改 secret 来源）。

## 3. 必读输入

- `AGENTS.md`、`docs/database-design.md`（`t_api_credential` 设计）
- `docs/detailed-requirements-design.md`（数据服务 invoke 链路）
- `platform-pipeline/src/main/java/.../service/DataServiceManager.java`（invoke 现状）
- `platform-pipeline/src/main/java/.../service/ApiCredentialRepository.java`（接口已存在）
- `reviews/claude-review.md`（M7-A S-02/F-08、M7-D D2-05）

## 4. 需要修改的模块

- `platform-pipeline.service`（DataServiceManager.invoke、ApiCredential、InvokeRequest、新增凭证管理 Controller）
- `db/migration`（若 `t_api_credential` 字段不全，补 V011 + U011）
- `platform-ui/src/api/service.ts`（移除 invoke 的 secret 入参，补凭证管理 API）
- `platform-ui/src/views/ServiceView.vue`（凭证管理入口）

## 5. 数据库/API/前端影响

- **数据库**：`t_api_credential` 字段核对（`api_key`/`secret_cipher`/`secret_hash`/`status`/`created_at`/`rotated_from`）。
- **API**：
  - `POST /services/{code}/invoke` 的 `InvokeRequest` 移除 `secret`（破坏性，须在完成报告标注）。
  - 新增 `POST /services/{code}/credentials`（创建，返回一次性明文）、`POST /credentials/{id}/rotate`、`POST /credentials/{id}/disable`。
- **前端**：ServiceView 增凭证管理；invoke 调用不传 secret。

## 6. 必须补充的测试

- **secret 泄露扫描**：断言 `t_api_credential` 表、`/invoke` 日志、响应体、异常消息中均无明文 secret。
- **签名校验测试**：apiKey 查 secret → 签名正确通过、错误拒绝、时间戳过期拒绝、nonce 重放拒绝。
- **轮换测试**：轮换后旧 key 调用拒绝、新 key 通过。
- **禁用测试**：禁用后 apiKey 调用拒绝。
- **MockMvc**：凭证管理端点 200/401/403；invoke 不传 secret 仍可工作（用有效 apiKey+签名）。

## 7. 验收命令

```bash
mvn test -pl platform-pipeline -Dspring.profiles.active=jdbc
npm run test:unit
# secret 泄露扫描（grep 明文 secret 不应出现在日志/响应）
```

## 8. M7 衔接

- **M7-A S-02/F-08**：invoke secret 明文 → 本任务修复为 apiKey→secret 服务端查找。
- **M7-D D2-05**：上线前项"invoke secret 仓储查找" → 本任务闭环。
- M7-D MockMvc `serviceInvokeIsWhitelistedNoToken` 须更新（invoke body 不再含 secret）。

## 9. 风险与回滚

- **风险**：`InvokeRequest` 移除 `secret` 是破坏性变更，影响已集成的消费方。控制：在完成报告明确 API 变更；提供迁移说明（消费方改用 apiKey+签名）。
- **风险**：Sm4 密钥管理。控制：密钥从 `${ENV_VAR}` 注入，不硬编码。
- **回滚**：DTO 改动可回退（但生产一旦用新凭证不应回退）；V011 有 U011。
- **敏感约束**：secret 明文仅在创建时返回一次；日志脱敏；不写明文到任何持久层。

## 10. 完成判定

- `t_api_credential` 密文+hash 存储，无明文。
- invoke 不接收 secret，按 apiKey 查 secret 校验签名。
- 凭证创建/轮换/禁用端点可用。
- secret 泄露扫描测试通过（表/日志/响应无明文）。
- MockMvc + 前端测试全绿。
- 输出 API 变更说明 + 安全测试证据。
