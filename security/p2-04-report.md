# P2-04 Security Scan Report

Date: 2026-06-30
Branch: `ai/p2-security-scan`

## Scope

P2-04 covers SCA setup and execution, high-risk dependency disposition, IDOR regression tests, DAST preparation, environment-secret baseline, and honest gap reporting for NFR-S01~S03.

This report does not replace production DAST, equal protection assessment, or MFA/SSO/ABAC feature delivery.

## SCA Results

| Area | Command | Result | Disposition |
|---|---|---|---|
| Backend Maven | `mvn org.owasp:dependency-check-maven:9.2.0:aggregate -Psecurity` | Failed before analysis because NVD returned 403/404 and no local NVD documents existed | Tool configured; must re-run with valid `NVD_API_KEY` or warmed cache. No backend CVE result is claimed. |
| Frontend npm | `npm audit --audit-level=high` | Initial scan found high/critical vulnerabilities | Upgraded affected packages; rescan returned 0 vulnerabilities. |

Frontend packages upgraded:

| Package | From | To | Reason |
|---|---:|---:|---|
| `axios` | `1.6.8` | `1.18.1` | High SSRF/proxy/header/prototype pollution advisories |
| `vitest` | `1.6.0` | `4.1.9` | Critical Vitest server RCE/file-read advisories |
| `vite` | `5.2.11` | `8.1.1` | Vulnerable Vite/esbuild chain |
| `@vitejs/plugin-vue` | `5.0.4` | `6.0.7` | Vite 8 peer compatibility |
| `element-plus` | `2.7.2` | `2.14.2` | `el-link` href validation advisory |
| `vue` | `3.4.27` | `3.5.39` | Peer compatibility with upgraded Element Plus transitive `@vueuse` |

Detailed SCA notes: `security/reports/sca-summary.md`.

## Application Security Tests

| Gap | Coverage Added | Result |
|---|---|---|
| Consumer logs IDOR | Low-privilege valid token accesses another consumer log endpoint | `403` verified in `PartnerModuleMockMvcTest` |
| Service logs IDOR | Low-privilege valid token accesses another service log endpoint | `403` verified in `PipelineModuleMockMvcTest` |

Targeted command:

```bash
mvn test -pl platform-partner,platform-pipeline -am "-Dtest=PartnerModuleMockMvcTest,PipelineModuleMockMvcTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result: passed, 43 tests.

## DAST Status

ZAP DAST was not run in the development environment. The deployment target, auth token, and report path are parameterized in:

- `security/owasp-zap.md`
- `security/run-zap.sh`

Script syntax check is required before handoff:

```bash
bash -n security/run-zap.sh
```

Production/test-environment DAST remains an acceptance gate.

## XSS Body Limitation

`XssFilter` currently covers request parameters and headers. It does not rewrite JSON request bodies.

Current mitigation:

- JSON bodies are parsed by Jackson rather than rendered as HTML.
- Frontend rendering must keep output escaping enabled.
- Existing XSS tests cover parameter/header sanitization.

Follow-up:

- Add body-wrapping request filter or enforce output-layer escaping policy for HTML contexts.

## NFR-S Mapping

| NFR | Requirement | Current Status |
|---|---|---|
| S01 | MFA, IAM/SSO, RBAC+ABAC, OAuth2/API Key/cert | RBAC and API Key/signature paths exist. MFA, IAM/SSO, ABAC field-level policy, and cert auth are functional gaps. |
| S02 | TLS1.2+, SM4 storage encryption, masking, immutable audit | SM4, masking, audit hash-chain have code/tests. TLS1.2+ and long-term audit retention are deployment/ops checks. |
| S03 | SQL injection, XSS, CSRF, traffic cleaning, equal protection level 3 | SQL guard, XSS param/header filter, JWT/API Key non-cookie model, and RBAC tests exist. ZAP/CSRF deployment verification, traffic cleaning, and level-3 assessment remain pending. |

## Configuration Security

`.env.example` was added as a production override template. Production must override all placeholder/default values.

Weak defaults observed:

| Location | Default | Disposition |
|---|---|---|
| `security.jwt.secret` / `JWT_SECRET` | `change-me-in-env` | Development only; production must override. |
| `PARTNER_CREDENTIAL_KEY` | `change-me-in-env` | Development only; production must override. |
| `DATA_ASSET_SM4_KEY` | `0123456789abcdef` | Development only; production must override. |
| `API_CREDENTIAL_SM4_KEY` | local fallback outside production profile | Production profile already requires env key; keep override in `.env.example`. |
| DB passwords | `root` | Development/docker only; production must override. |
| MinIO credentials | `minioadmin` | Development/docker only; production must override. |
| RabbitMQ password | `guest` | Development only; production must override. |

## Limitations And Gates

- Backend dependency-check did not complete without `NVD_API_KEY`; no backend "no CVE" claim is made.
- Dameng/OceanBase driver vulnerability coverage should be confirmed with vendors.
- ZAP DAST, TLS configuration, traffic cleaning, and equal protection level-3 assessment require deployed environment/third party.
- MFA/SSO/ABAC/certificate auth are functional gaps and were intentionally not implemented in P2-04.
- Vite 8 build passes but emits non-fatal bundle-size and pure-annotation warnings; monitor in frontend CI.

## Verification

| Command | Result |
|---|---|
| `mvn test -pl platform-partner,platform-pipeline -am "-Dtest=PartnerModuleMockMvcTest,PipelineModuleMockMvcTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | Passed, 43 tests |
| `npm audit --audit-level=high` | Passed, 0 vulnerabilities |
| `npm ls vite @vitejs/plugin-vue vue element-plus --depth=0` | Passed |
| `npm run test:unit` | Passed, 12 files / 88 tests |
| `npm run build` | Passed with non-fatal Vite warnings |
| `bash -n security/run-zap.sh` | Passed |
| `mvn test` | Passed, all 8 Maven modules |
