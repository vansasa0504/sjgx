# Codex 执行任务 - P0-10：真实依赖 E2E

> 阶段：P0（上线阻断修复·收尾门禁）
> 任务编号：P0-10
> 分支建议：`ai/p0-e2e-real-deps`
> 依据：`docs/development-process-workflow.md` §3.1 P0-10、§7.2、§9、`docs/implementation-gap-and-test-plan.md`
> 前置：P0-01~P0-09 全部通过
> 日期：2026-06-27

---

## 1. 背景与目标

M7-D 的端到端 10 步主链路基于内存仓储 + 部分依赖（仅 MySQL/Redis/Nacos），未验证真实 DB（JDBC 落库后）、MQ（Kafka/RabbitMQ）、MinIO（对象存储）profile。P0-03~P0-08 完成落库与事实源后，须用真实依赖重新验证 10 步主链路，作为 P0 阶段收尾门禁（流程文档 §9 持久化/安全/测试/合规门禁）。

**最小可行结果**：docker-compose 启动全部依赖（MySQL/Redis/Nacos/Kafka/RabbitMQ/MinIO）+ 6 后端服务（`jdbc` profile）+ 前端，10 步主链路全部走通并留 curl 证据；Testcontainers 集成测试覆盖真实依赖 profile。

## 2. 范围

### 本次实现
- 启用 `jdbc` profile 跑全链路（P0-03 落库后的真实持久化）。
- 启用真实 MQ：接入任务/异步调用日志走 Kafka/RabbitMQ（若 M7 期间未接，本任务接通或标注遗留）。
- 启用真实 MinIO：对象存储（冷数据/归档）走 MinIO profile（P0-03 storage 落库 + MinIO 客户端）。
- 10 步主链路 E2E 脚本（基于 M7-D 的 e2e，改用 `jdbc` profile + 真实依赖）：
  1. 登录 + permissions
  2. 合作方 create→interface→submit→approve→admit
  3. 接入任务 create→test→records→submit→approve
  4. 数据服务 register→define→test→publish→invoke（apiKey+签名）→logs
  5. 消费方 register→submit→approve→quota→audit→logs
  6. 数据质量 rule→check→issues→resolve
  7. 计费 rule→generate（从日志聚合）→confirm→stats
  8. 统计 dashboard→audit（trace_id 链路 + hash 链 verify）
  9. 系统 users→roles→permissions
  10. 权限校验：低权限用户越权 403
- Testcontainers 集成测试：MySQL + Redis + MQ + MinIO profile 下主链路关键端点。
- 重启恢复验证：主链路数据写入 → 重启全部服务 → 数据仍在（持久化门禁）。

### 不做
- 不做性能压测（P2-02）。
- 不做故障注入（P2-03）。
- 不重复 P0-03~P0-08 的单元/MockMvc 测试。

## 3. 必读输入

- `AGENTS.md`、`docs/development-process-workflow.md` §7.2、§9
- `docs/implementation-gap-and-test-plan.md`
- `reviews/m7d-completion-report.md`（M7-D 10 步 E2E 脚本基础）
- `docker-compose.yml`、`tasks/dev-progress.md` §5（启动命令）
- P0-01~P0-09 的任务单与审查报告

## 4. 需要修改的模块

- `platform-*` 的 `application.yml`（`jdbc` profile 默认化 + MQ/MinIO profile）
- `platform-*` 的集成测试目录（Testcontainers）
- `delivery/` 或 `tasks/`（E2E 脚本，若 `delivery/` 禁改则放 `tasks/e2e-p0.sh`）
- `docs/`（不改动，证据入完成报告）

## 5. 数据库/API/前端影响

- **数据库**：真实 MySQL（`jdbc` profile），flyway 启用（P0-01 修复后）。
- **API**：无新增；验证既有端点在真实依赖下行为。
- **前端**：无改动；从 5173 登录走通。

## 6. 必须补充的测试

- **Testcontainers 集成测试**：MySQL + Redis + Kafka/RabbitMQ + MinIO 容器，跑主链路关键端点（partner create、service invoke+logs、billing generate 从日志聚合、audit hash 链 verify）。
- **10 步 E2E 脚本**：curl 序列，每步记录请求 + 响应（脱敏 token）。
- **重启恢复测试**：写数据 → `docker compose restart` → 数据仍在。
- **trace_id 全链路测试**：一次主链路按 trace_id 查全事件（P0-05 + P0-08 联合验证）。
- **hash 链 verify**：E2E 后调 `/stats/audit/verify` 通过。

## 7. 验收命令

```bash
# 1. 启动全部依赖
docker compose up -d
# 2. 启动后端（jdbc profile + 真实 MQ/MinIO）
mvn -pl platform-auth spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=jdbc --server.port=8081 ..." 
# （其余服务同 dev-progress.md §5.4，去掉 memory/nacos 禁用，启用 flyway）
# 3. 启动前端
cd platform-ui && npm run dev
# 4. E2E 10 步
bash tasks/e2e-p0.sh   # 输出每步 curl + 响应
# 5. Testcontainers 集成测试
mvn test -Dspring.profiles.active=jdbc -Dtest="*IT"
# 6. 重启恢复
docker compose restart
# 重新 GET 主链路数据 → 仍在
```

## 8. M7 衔接

- **M7-D D2-01**：端到端基于内存 → 本任务改真实依赖。
- **M7-D 完成报告 §6**：Pipeline health=DOWN（RabbitMQ 未启动）→ 本任务接通真实 MQ。
- **M7-D D2-05 上线前项**：真实依赖验证属上线门禁，本任务闭环。
- 复用 M7-D 的 10 步 E2E 脚本，升级为 `jdbc` + 真实依赖版本。

## 9. 风险与回滚

- **风险**：真实依赖启动不稳定（MQ/MinIO 版本、端口、卷）。控制：docker-compose 固定版本；Testcontainers 隔离。
- **风险**：`jdbc` profile 暴露 P0-03 落库的并发/事务问题。控制：本任务发现则返工 P0-03（流程文档 §10 返工闭环）。
- **风险**：MQ 接入改动较大可能超范围。控制：若 MQ 接入未在 P0-03~08 完成，本任务可标注"MQ 真实化"为遗留到 P1，但 DB/Redis/MinIO 必须真实化。
- **回滚**：`jdbc` profile 可切回 `memory`（仅 E2E 退化为 M7-D 状态）；docker-compose 可停服。
- **敏感约束**：E2E 证据脱敏 token/secret；不写真实密钥到脚本。

## 10. 完成判定

- 10 步主链路在真实依赖（MySQL/Redis/MQ/MinIO）+ `jdbc` profile 下全部走通，有 curl 证据。
- Testcontainers 集成测试通过。
- 重启恢复测试通过（持久化门禁）。
- trace_id 全链路 + hash 链 verify 通过（合规/安全门禁）。
- `mvn test` + `npm run test:unit` 全绿。
- 输出 P0 阶段验收材料：10 步证据 + 重启恢复证据 + Testcontainers 结果 + P0 总体结论。
- P0 全部门禁（功能/数据库/持久化/安全/测试/合规）达成，可进入 P1。
