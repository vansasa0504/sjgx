# Claude Code 返工复审 — P0-04 API 凭证安全

## 1. 复审对象

- 任务：P0-04 API 凭证安全（返工）
- 分支：`ai/p0-api-credential`
- 初版审查：`reviews/claude-review-P0-04.md`（2026-06-28）
- 返工复审日期：2026-06-28
- 审查者：Claude Code

---

## 2. 返工清单逐项检查

依据初版审查 §8 返工任务清单：

### RW-1（中-高）Sm4 密钥默认值 → ✅ 已修复

**初版问题**：`secretKey()` 用 `getOrDefault("API_CREDENTIAL_SM4_KEY", "local-api-credential-key")`，生产忘配则密文用公开默认密钥加密，形同明文。

**返工代码**（`ApiCredentialRepository.java:223-238`）：
```java
private static String secretKey() {
    String key = System.getenv("API_CREDENTIAL_SM4_KEY");
    if (key != null && !key.isBlank()) {
        return key;
    }
    if (isProductionProfile()) {
        throw new IllegalStateException("API_CREDENTIAL_SM4_KEY must be set in production profile");
    }
    return "local-api-credential-key";
}

private static boolean isProductionProfile() {
    String active = System.getProperty("spring.profiles.active",
            System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));
    String normalized = active.toLowerCase();
    return normalized.contains("prod") || normalized.contains("production");
}
```

- 生产 profile（含 `prod`/`production`）未设环境变量 → 抛 `IllegalStateException` 拒绝启动
- 非生产保留开发默认值，不破坏测试与本地开发
- 同时检查 JVM 系统属性和环境变量 `SPRING_PROFILES_ACTIVE`
- 与 P0-03 RW-2（bootstrapAdmin 拒绝启动）处理标准一致

**测试证据**：`DataServiceManagerTest.productionProfileRequiresConfiguredCredentialSm4Key`
- `Assumptions.assumeTrue` 跳过已设环境变量的环境（避免污染）
- 设置 `spring.profiles.active=prod` → `ApiCredentialRepository::new` 抛 `IllegalStateException`
- finally 块恢复系统属性，无测试间污染

**判定**：✅ 通过。安全一致性达成。

---

### RW-2（中）rotate 未设 rotated_from → ✅ 已修复

**初版问题**：`create` 硬编码 `rotatedFrom=null`，`rotated_from` 列永远为空，轮换链路断裂。

**返工代码**：
- `create` 增加 `rotatedFrom` 参数重载（`ApiCredentialRepository.java:59`）：
  ```java
  CreatedCredential create(String consumerCode, String serviceCode, String apiKey, String secret, Long rotatedFrom)
  ```
- 旧两个重载委托到新方法，传 `null`
- `rotate(id)` 传入旧凭证 id（`:72`）：
  ```java
  return create(current.consumerCode(), current.serviceCode(),
          randomToken("ak"), randomToken("sk"), current.id());
  ```

**测试证据**：`ApiCredentialRepositoryJdbcTest.jdbcRotateSetsRotatedFromDisablesOldKeyAndNewKeyWorks`
- 轮换后 `findById(newId).rotatedFrom()` == `oldId` ✅
- 旧 key `status=DISABLED` ✅
- 旧 apiKey `findByApiKey` 抛异常 ✅
- 新 apiKey `findByApiKey` 返回正确 secret ✅

**判定**：✅ 通过。轮换链路字段已正确写入，审计可追溯。

---

### RW-3（中）JDBC 路径 rotate/disable/list 测试缺失 → ✅ 已修复

**初版问题**：`ApiCredentialRepositoryJdbcTest` 仅 1 个用例，JDBC 路径生命周期未验证。

**返工新增**（共 5 个用例）：

| 用例 | 覆盖 |
|---|---|
| `jdbcStorageDoesNotPersistPlainSecret` | 密文不存明文（原有） |
| `jdbcRotateSetsRotatedFromDisablesOldKeyAndNewKeyWorks` | JDBC 轮换全链路 |
| `jdbcDisableRejectsApiKeyAndListFiltersByServiceCode` | JDBC 禁用 + list 按 serviceCode 过滤 |
| `jdbcHashMismatchRejectsCredentialWithoutLeakingSecret` | hash 篡改拒绝 + 异常不含明文 |
| `invokeResponseLogAndExceptionDoNotContainPlainSecret` | 响应/日志/异常均不含明文 secret（RW-7 闭环） |

**判定**：✅ 通过。JDBC 路径 rotate/disable/list/校验全面覆盖，超额完成（含 RW-7 泄露扫描扩展）。

---

### RW-4（低）secret_hash 字段未使用 → ✅ 已修复（超出预期）

**初版问题**：hash 存储但从未用于校验，为死字段。

**返工代码**：新增 `decryptAndVerify`（`:197-206`），`findByApiKey` 改用它：
```java
private String decryptAndVerify(StoredCredential credential) {
    if (credential.secretCipher() == null || credential.secretCipher().isBlank()) {
        throw new BusinessException("AUTH-500", "api credential secret missing");
    }
    String secret = decrypt(credential.secretCipher());
    if (!hash(secret).equals(credential.secretHash())) {
        throw new BusinessException("AUTH-500", "api credential secret corrupted");
    }
    return secret;
}
```

- 解密后用 `secret_hash` 校验完整性，防篡改/防密文损坏
- 篡改 hash → 抛 `AUTH-500`，且异常消息不含明文（`jdbcHashMismatchRejectsCredentialWithoutLeakingSecret` 断言）
- 同时处理密文为 null（旧凭证迁移后）的情况

**判定**：✅ 通过。hash 字段已实际消费，成为完整性校验机制。

---

### RW-5（低）storedSecretSnapshot 暴露密文 → ✅ 已修复

**初版问题**：方法为 `public`，任何代码可取密文。

**返工代码**（`:136`）：降为 package-private：
```java
StoredSecretSnapshot storedSecretSnapshot(String apiKey) {
```

仅同包测试可访问，暴露面收窄。

**判定**：✅ 通过。

---

### RW-6（低）旧凭证迁移说明 → ✅ 已修复

dev-progress §14.4 已标注：
> V012 会将旧 `secret` 列改为固定占位；若环境中已有旧明文凭证，需要迁移后重新创建新凭证。

且代码层 `decryptAndVerify` 对密文 null 抛 `AUTH-500`（旧凭证迁移后失效有明确报错而非 NPE）。

**判定**：✅ 通过。

---

### RW-7（低）secret 泄露扫描扩展 → ✅ 已修复

`invokeResponseLogAndExceptionDoNotContainPlainSecret`：
- invoke 成功响应体不含明文 secret
- `ServiceInvokeLog` 的 `toString()` 不含明文 secret
- 验签失败异常消息不含明文 secret
- hash 篡改异常消息不含明文 secret

**判定**：✅ 通过。任务 §6 要求的"表/日志/响应/异常无明文"四维扫描全部覆盖。

---

## 3. 其他改进观察

### 3.1 异常处理精确化
`storedByApiKey` 从 `catch (Exception ex)` 收窄为 `catch (EmptyResultDataAccessException ex)`（`:170`），避免吞掉非"未找到"的异常（如连接错误、SQL 语法错误），更准确。

### 3.2 测试隔离
`productionProfileRequiresConfiguredCredentialSm4Key` 用 `Assumptions.assumeTrue` + finally 恢复系统属性，避免环境污染其他测试，工程规范。

---

## 4. 测试结果

```
mvn test -pl platform-pipeline -am

platform-common:   28 tests, 0 failures
platform-quality:  18 tests, 0 failures
platform-pipeline: 57 tests, 0 failures
  ├─ ApiCredentialRepositoryJdbcTest: 5 (原1→5，+4)
  ├─ DataServiceManagerTest:          9 (原8→9，+1 生产密钥强制)
  └─ PipelineModuleMockMvcTest:       17 (含凭证/invoke 2 个)
总计: 103 tests, 0 failures, 0 errors

BUILD SUCCESS
```

dev-progress §14.2 记录前端 `npm run test:unit` 11 文件 35 用例通过（未本地复跑，沿用记录）。

---

## 5. 返工总结

| 编号 | 优先级 | 状态 | 说明 |
|---|---|---|---|
| RW-1 | 中-高 | ✅ 已修复 | 生产 profile 未设 Sm4 密钥拒绝启动 |
| RW-2 | 中 | ✅ 已修复 | rotate 写入 rotated_from |
| RW-3 | 中 | ✅ 已修复 | JDBC 路径 rotate/disable/list 测试补齐 |
| RW-4 | 低 | ✅ 已修复 | secret_hash 用于完整性校验（decryptAndVerify） |
| RW-5 | 低 | ✅ 已修复 | storedSecretSnapshot 降 package-private |
| RW-6 | 低 | ✅ 已修复 | 旧凭证迁移说明 + null 密文防御 |
| RW-7 | 低 | ✅ 已修复 | 响应/日志/异常四维泄露扫描 |

**7/7 全部修复**，其中 RW-4（hash 校验）从"低优可后续"提升为实际落地的完整性校验机制，超出预期。

---

## 6. 审查结论

**✅ 建议通过。**

返工完整且超额：3 个中优先级（RW-1~RW-3）全部修复并有对应测试，4 个低优先级（RW-4~RW-7）一并闭环。`decryptAndVerify` 引入的密文完整性校验是额外安全增强。103 个测试全绿。

未触及敏感文件、未引入大型依赖、无无关重构。破坏性 API 变更（invoke 移除 secret）已同步前后端 + MockMvc + dev-progress 说明。

**建议提交信息**：

```text
feat(P0-04): secure API credentials with ciphertext storage and server-side lookup

- Store api credential secrets as SM4 ciphertext (secret_cipher) plus
  SHA-256 hash; legacy secret column written with placeholder only
- Remove secret from InvokeRequest; DataServiceManager.invoke looks up
  secret by apiKey and verifies HMAC signature server-side
- decryptAndVerify enforces secret_hash integrity on lookup
- Credential management endpoints: create/list/rotate/disable under
  /api/v1/services (service:update / service:view); create/rotate return
  plaintext secret once; rotation disables old key and records rotated_from
- Require API_CREDENTIAL_SM4_KEY in prod profile, else fail fast
- V012/U012 migration (mysql + dm parity) for cipher/hash/status/rotated_from
- ServiceView credential management UI (create/rotate/disable)
- Tests: ApiCredentialRepositoryJdbcTest(5), DataServiceManagerTest(9),
  MockMvc 401/403/200 + invoke-without-secret + leak scan

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

---

**返工复审结论**：✅ 建议通过。
**是否需要 Codex 再次返工**：否。
**是否建议提交**：是。
