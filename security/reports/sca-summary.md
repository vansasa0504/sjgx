# P2-04 SCA Summary

Date: 2026-06-30

## Backend Maven SCA

Command:

```bash
mvn org.owasp:dependency-check-maven:9.2.0:aggregate -Psecurity
```

Result: not completed.

Observed failure:

- `dependency-check-maven` plugin downloaded successfully.
- NVD data update failed with `Error updating the NVD Data; the NVD returned a 403 or 404 error`.
- No local NVD documents existed, so dependency-check stopped with `NoDataException: No documents exist`.

Disposition:

- No backend CVE report was produced, and no backend vulnerability result is claimed.
- Re-run with a valid `NVD_API_KEY` or a pre-warmed dependency-check data cache before production acceptance.
- `failBuildOnCVSS` is configured as `9` under the Maven `security` profile so CVSS >= 9 blocks the SCA run when the vulnerability database is available.
- Dameng and OceanBase driver CVE coverage still requires vendor confirmation because public CVE data may be incomplete.

## Frontend npm SCA

Initial command:

```bash
cd platform-ui
npm audit --audit-level=high
```

Initial result: failed with 5 vulnerabilities.

| Package | Severity | Finding | Disposition |
|---|---:|---|---|
| `axios@1.6.8` | High | SSRF, credential leakage, DoS, prototype pollution and header/proxy related advisories | Upgraded to `axios@1.18.1` |
| `vitest@1.6.0` | Critical | RCE / arbitrary file read through Vitest server advisories | Upgraded to `vitest@4.1.9` |
| `vite@5.2.11` / `esbuild` | Moderate/High chain | vulnerable `esbuild` through Vite toolchain | Upgraded to `vite@8.1.1` |
| `@vitejs/plugin-vue@5.0.4` | High chain | depended on vulnerable Vite range | Upgraded to `@vitejs/plugin-vue@6.0.7` |
| `element-plus@2.7.2` | Moderate | insufficient `el-link` href validation | Upgraded to `element-plus@2.14.2` |
| `vue@3.4.27` | Peer alignment | Element Plus transitive `@vueuse` expects Vue 3.5+ | Upgraded to `vue@3.5.39` |

Verification after upgrade:

```bash
cd platform-ui
npm audit --audit-level=high
npm ls vite @vitejs/plugin-vue vue element-plus --depth=0
npm run test:unit
npm run build
```

Result:

- `npm audit --audit-level=high`: 0 vulnerabilities.
- `npm ls`: clean top-level dependency tree for Vite/Vue/Element Plus.
- `npm run test:unit`: 12 test files, 88 tests passed.
- `npm run build`: passed. Vite emitted non-fatal chunk size and pure annotation warnings.
