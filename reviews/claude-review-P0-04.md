# Claude Code 审查结果 — P0-04 API 凭证安全

## 1. 审查对象

- 任务：P0-04 API 凭证安全
- 分支：`ai/p0-api-credential`
- 任务单：`tasks/codex-task-P0-04-api-credential.md`
- 审查日期：2026-06-28
- 前置：P0-03 已合入 master（`bb16f8ba`）
- 改动范围：ApiCredentialRepository 深化（密文+hash）、DataServiceManager.invoke 改造、InvokeRequest 移除 secret、凭证管理端点、V012/U012 迁移、前端 ServiceView 凭证管理

## 2. Git 状态

改动全部未提交（工作区）：

```text
 M platform-pipeline/ingest/PipelineApplication.java
 M platform-pipeline/service/ApiCredentialRepository.java
 M platform-pipeline/service/DataServiceController.java
 M platform-pipeline/service/DataServiceManager.java
 M platform-pipeline/test/.../PipelineModuleMockMvcTest.java
 M platform-pipeline/test/.../DataServiceManagerTest.java
 M platform-ui/src/api/service.ts
 M platform-ui/src/views/ServiceView.vue
 M tasks/dev-progress.md
?? db/migration/V012__api_credential_secret_cipher.sql
?? db/migration/U012__api_credential_secret_cipher.sql
?? db/migration-dm/V012__api_credential_secret_cipher.sql
?? db/migration-dm/U012__api_credential_secret_cipher.sql
?? platform-pipeline/test/.../ApiCredentialRepositoryJdbcTest.java
```

未触及：`.env`、密钥、证书、生产配置。未引入新依赖。无大批量删除。

## 3. 代码差异摘要

### 3.1 核心安全机制（任务主线，达成）
- **secret 密文存储**：`ApiCredentialRepository` 用 `Sm4Util.encrypt(secret, sm4Key)` 存 `secret_cipher`，SHA-256 存 `secret_hash`，旧 `secret` 列写固定占位符 `__SECRET_CIPHER_ONLY__`。
- **invoke 不再收 secret**：`InvokeRequest` DTO 移除 `secret` 字段（破坏性变更，已同步前端/MockMvc）。`DataServiceManager.invoke` 按 `apiKey` 查凭证 → `decrypt(secretCipher)` 取明文 → `SignatureUtil.verify` 验签。
- **凭证生命周期**：create（返回一次性明文）/ list / rotate（禁用旧 key + 创建新 key）/ disable。
- **复用既有安全组件**：`Sm4Util`（CBC + 随机 IV + PKCS7）、`SignatureUtil`（HMAC-SHA256 + nonce 防重放 + 300s 时间窗 + constantTimeEquals）。质量可靠，未重新造轮子。

### 3.2 invoke 鉴权链路（正确）
- invoke 端点无 `@RequirePermission`，靠 apiKey+签名鉴权；`JwtAuthFilter` 白名单放行 `/api/v1/services/*/invoke`。符合"外部消费方用 apiKey 而非 JWT"的设计。
- invoke 增加了 serviceCode/consumerCode 一致性校验（凭证绑定服务与消费方），优于任务要求。

### 3.3 V012/U012 迁移
- V012（通用 + dm 对等）：加 `secret_cipher/secret_hash/status/rotated_from` 四列 + 2 个索引；旧数据 `status` 按 `enabled` 回填，旧 `secret` 列写占位符。
- U012 对等回滚 DROP COLUMN + DROP INDEX。
- dm 版 `ALTER TABLE ... ADD col`（省略 COLUMN 关键字）、`DROP INDEX ... ON table`，与 P0-02 达梦模式一致。

### 3.4 前端
- `service.ts` 加 4 个凭证 API + 类型；`ServiceView.vue` 加凭证管理入口（创建/轮换/禁用），创建/轮换后弹窗展示一次性明文 secret。实现完整。

## 4. 需求满足情况

依据 `tasks/codex-task-P0-04-api-credential.md` §2 与 §10：

| 编号 | 需求 | 是否满足 | 说明 |
|---|---|---|---|
| R1 | secret 密文存储，不存明文 | ✅ | Sm4 加密 + 占位符；JdbcTest 断言表无明文 |
| R2 | 存 secret_hash 用于校验 | ⚠️ | hash 已存储但**未被任何代码用于校验**（见 5.2） |
| R3 | invoke 按 apiKey 查 secret 验签 | ✅ | 已改造，移除 secret 入参 |
| R4 | InvokeRequest 移除 secret | ✅ | DTO + 前端 + MockMvc 同步 |
| R5 | 凭证 create/rotate/disable 端点 | ✅ | 4 端点齐备，权限码正确 |
| R6 | 创建返回一次性明文 | ✅ | CreatedCredential 仅 create/rotate 返回 |
| R7 | 轮换：旧 key disabled，新 key 生效 | ✅ | rotate→disable+create |
| R8 | 禁用后不可调用 | ✅ | findByApiKey 检查 status |
| R9 | secret 泄露扫描测试 | ⚠️ | 表扫描✅；日志/响应扫描未做（见 5.5） |
| R10 | 签名校验测试（正确/错误/过期/重放） | ✅ | DataServiceManagerTest 覆盖 4 类 |
| R11 | 轮换/禁用测试 | ✅ | rotationDisablesOldKeyAndDisableRejectsCurrentKey |
| R12 | MockMvc 凭证端点 200/401/403 | ✅ | credentialCreateRequiresServiceUpdatePermission |
| R13 | MockMvc invoke 不传 secret 可工作 | ✅ | invokeWorksWithoutSecretFieldWhenSignatureIsValid |
| R14 | Sm4 密钥从 ENV 注入不硬编码 | ⚠️ | 有默认值 `local-api-credential-key`（见 5.1） |
| R15 | API 变更说明 + 安全测试证据 | ✅ | dev-progress §14 完整 |

## 5. 安全与风险检查

### 5.1 Sm4 密钥默认值（中-高风险，建议合入前修复）
`ApiCredentialRepository.secretKey()`：
```java
return System.getenv().getOrDefault("API_CREDENTIAL_SM4_KEY", "local-api-credential-key");
```
- 生产若忘配 `API_CREDENTIAL_SM4_KEY`，所有 secret 用公开默认密钥加密，攻击者拿到库即可解密全部 secret_cipher，密文形同明文。
- **与 P0-03 RW-2 处理不一致**：P0-03 刚把 bootstrapAdmin 的 `admin123` 默认值改为"未设则拒绝启动"。Sm4 密钥应采用同样标准——生产 profile 下未设环境变量应拒绝启动或告警，而非静默用默认值。
- dev-progress §14.4 已注明"生产需注入"，但代码层无强制。属纵深防御弱点（前提是攻击者已获库访问）。

### 5.2 `secret_hash` 字段未使用（低-设计冗余）
- `store()` 写入 `secret_hash`，但 `findByApiKey` 用 `decrypt(secretCipher)` 解密明文用于 HMAC，**从未读取或比对 `secret_hash`**。
- 任务 §2 说"存 secret_hash 用于校验"，但实际校验靠解密。hash 字段成为死字段，V012 加列无实际消费方。
- 非安全漏洞（HMAC 需明文 secret，解密路径可行），但与任务设计不符，且浪费存储。建议：要么删除 hash 字段，要么明确其用途（如用于不解密场景的等值校验）。

### 5.3 轮换未设置 `rotated_from`（中-功能缺陷，建议修复）
`rotate(id)` → `disable(id)` + `create(consumerCode, serviceCode)`：
```java
CreatedCredential create(..., String apiKey, String secret) {
    ...
    store(new StoredCredential(id, apiKey, encrypt(secret), hash(secret),
            consumerCode, serviceCode, ACTIVE, null, ...));  // rotatedFrom 硬编码 null
```
- `create` 的 `rotatedFrom` 参数硬编码 `null`，无重载接收旧凭证 id。
- **`rotated_from` 列永远为 null**，轮换链路断裂，无法追溯轮换来源。V012 加该列 + 前端 CredentialView 展示 `rotatedFrom` 列均无数据。
- 违反任务 §5 "rotated_from" 设计意图。

### 5.4 `storedSecretSnapshot` 公开暴露密文（低）
- `public StoredSecretSnapshot storedSecretSnapshot(String apiKey)` 返回 `secretCipher` + `secretHash`。任何能访问仓储的代码可取密文。
- 仅测试使用。建议降为 package-private 或移除（测试可改用反射或包内访问）。密文本身需密钥才能用，风险低，但减少暴露面是好习惯。

### 5.5 JDBC 路径 rotate/disable 测试缺失（中-测试）
- `ApiCredentialRepositoryJdbcTest` 仅 1 个用例（密文不存明文）。
- JDBC 路径的 `rotate`/`disable`/`findById`/`list`/`findByApiKey(disabled 拒绝)` 未在 JDBC 测试验证，仅内存模式 `DataServiceManagerTest` 覆盖。
- 任务 §6 要求的"secret 泄露扫描"仅覆盖表，未扫描 `/invoke` 日志、响应体、异常消息（虽然 invoke 不再返回 secret，日志中 `ServiceInvokeLog` 也不含 secret，但缺断言）。

### 5.6 旧凭证迁移后失效（低，可接受）
- V012 把旧 `secret` 列写占位符，但未回填 `secret_cipher`（旧凭证密文为 null）。
- 若库中已有旧明文凭证，迁移后 `findByApiKey` 解密 null 失败 → 旧凭证全部失效。
- dev-progress 表明 P0-04 前无真实凭证数据，开发库可接受。但应在迁移说明标注"旧凭证迁移后需重新创建"。

### 5.7 无安全红线违反
- 未改 .env/密钥/证书；未连生产库；BCrypt/Sm4 存储正确；invoke 链路无明文 secret 泄露。

## 6. 测试检查

| 测试 | 结果 | 说明 |
|---|---|---|
| `mvn test -pl platform-pipeline -am` | ✅ 全绿 | common28/quality18/pipeline52，含新增 ApiCredentialRepositoryJdbcTest(1)、DataServiceManagerTest(8 含 4 个凭证相关)、PipelineModuleMockMvcTest(17 含 2 个新增) |
| `ApiCredentialRepositoryJdbcTest` | ✅ 1/1 | 仅覆盖密文不存明文 |
| `DataServiceManagerTest` 凭证用例 | ✅ 4/4 | 加密存储、签名校验四类、轮换禁用 |
| MockMvc 凭证/invoke | ✅ 2/2 | 权限 401/403/200 + invoke 不传 secret |
| 前端 `npm run test:unit` | 未本地复跑 | dev-progress §14.2 记录 11 文件 35 用例通过 |

## 7. 审查结论

### 建议有条件通过

核心安全目标全部达成：secret 密文存储、invoke 移除 secret 入参、apiKey→secret 服务端查找验签、轮换/禁用可用、破坏性变更已同步前后端。复用既有 Sm4Util/SignatureUtil，未重新造轮子，质量可靠。测试全绿。

存在 2 个建议合入前修复的中风险项 + 1 个功能缺陷：
- **RW-1（中-安全）** Sm4 密钥默认值 → 与 P0-03 RW-2 对齐，生产未设应拒绝启动；
- **RW-2（中-功能）** rotate 未设 rotated_from → 轮换链路字段永远 null；
- **RW-3（中-测试）** JDBC 路径 rotate/disable/list 测试缺失。

未触及"暂不通过"红线（无敏感文件、无盗版依赖、无大批量删除）。是否先返工 RW-1~RW-3 再提交，由你定夺——若接受"Sm4 默认密钥留待生产部署强制配置 + rotated_from 审计追溯暂缺"两项已知风险，可直接提交；否则建议先返工。

## 8. 返工任务清单

| 编号 | 问题 | 修改要求 | 优先级 |
|---|---|---|---|
| RW-1 | Sm4 密钥默认值 `local-api-credential-key` | 与 P0-03 RW-2 对齐：生产 profile 未设 `API_CREDENTIAL_SM4_KEY` 时拒绝启动或告警；保留开发默认值仅用于非生产 | 中-高 |
| RW-2 | rotate 未设 rotated_from | `create` 增加重载接收 `rotatedFrom`；`rotate(id)` 传入旧凭证 id，使新凭证 `rotated_from` 指向旧凭证 | 中 |
| RW-3 | JDBC 路径 rotate/disable/list 测试缺失 | 在 `ApiCredentialRepositoryJdbcTest` 补 JDBC 路径的 rotate（验证 rotated_from + 旧 key disabled）、disable（findByApiKey 抛 AUTH-403）、list（按 serviceCode 过滤）用例 | 中 |
| RW-4 | secret_hash 字段未使用 | 要么删除该列+移除存储，要么明确用途并在校验路径消费（如等值比对）；当前为死字段 | 低 |
| RW-5 | storedSecretSnapshot 暴露密文 | 降为 package-private 或仅测试可见 | 低 |
| RW-6 | 旧凭证迁移说明 | V012 或 dev-progress 标注"旧明文凭证迁移后 secret_cipher 为 null，需重新创建" | 低 |
| RW-7 | secret 泄露扫描扩展 | 补断言：invoke 响应体、ServiceInvokeLog、异常消息均不含明文 secret | 低 |

## 9. 建议提交信息

若接受已知风险直接提交：

```text
feat(P0-04): secure API credentials with ciphertext storage and server-side secret lookup

- Store api credential secrets as SM4 ciphertext (secret_cipher) plus
  SHA-256 hash; legacy secret column written with placeholder only
- Remove secret from InvokeRequest; DataServiceManager.invoke now looks
  up secret by apiKey and verifies HMAC signature server-side
- Add credential management endpoints: create/list/rotate/disable under
  /api/v1/services (service:update / service:view permissions)
- Create/rotate return plaintext secret once; rotation disables old key
- Add V012/U012 migration (mysql + dm parity) for cipher/hash/status/rotated_from
- Add ServiceView credential management UI (create/rotate/disable)
- Tests: ApiCredentialRepositoryJdbcTest, signature/rotation/disable
  coverage in DataServiceManagerTest, MockMvc 401/403/200 + invoke-without-secret

Known follow-ups: SM4 key production enforcement, rotated_from wiring,
JDBC-path rotate/disable tests, secret_hash usage.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**审查结论**：建议有条件通过（RW-1~RW-3 建议合入前修复，RW-4~RW-7 低优可后续）。
**是否需要 Codex 返工**：建议返工 RW-1~RW-3；若接受已知风险可不返工。
**是否建议提交**：可提交（建议先修 RW-1~RW-3）；远程推送待确认。
