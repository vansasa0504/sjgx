# Codex 桌面端 M6 阶段执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M5 阶段已通过 Claude Code 审查（见 reviews/claude-review.md 结论"通过"）。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M6.md   ← 本阶段任务来源
7. reviews/claude-review.md ← M1~M5 审查历史，含 M5 遗留 P-01M5~P-08M5

当前执行阶段：M6（稳定性 + 故障演练 + 升级回滚 + 文档交付 + 验收准备 + M5 遗留修复）
本阶段为收尾阶段。请只执行本提示词中「M6 阶段任务」段落列出的任务。

---

## 项目背景（一句话）

金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。M1~M5 已完成全部功能、集成测试、性能调优、安全加固、国产化适配、双活配置。本阶段做稳定性测试、故障演练、升级回滚演练、文档交付、验收准备，并修复 M5 遗留项。

### 既有现状（必须基于此）
- 后端 7 模块 + 前端 platform-ui 已就绪，`mvn test` 全绿、`npm run test:unit` 全绿。
- M5 已交付：JMeter 脚本(perf/jmeter/)、调优配置、安全文档(security/)、国产化报告(national-db-compat-report.md)、k8s/dev 双活配置、Dockerfile。
- M5 遗留 8 项（P-01M5~P-08M5，见 reviews/claude-review.md §9），本阶段修复代码层可修项。
- **无** docs/delivery 目录、**无** 五类交付文档、**无** 验收报告。
- 开发环境为 Windows + Maven + npm，**无** 48h 压测环境、**无** Chaos Mesh/K8s 集群、**无** 达梦/OceanBase 实例。需外部环境的项采取**输出脚本/方案/报告框架 + 标注"待上线环境执行"**策略，**严禁编造实测数据**。

### 目录约束（重要）
- `docs/` 目录由 Claude Code 维护，Codex 不得修改（CLAUDE.md/Codex 规则）。本阶段交付文档放项目根 **`delivery/`** 目录（非 docs/delivery/）。
- `k8s/prod/` 禁止修改；`k8s/dev/` 可改。

---

## 执行规则（全局）

1. 只实现 M6 阶段任务。以稳定性测试方案、演练脚本、文档、验收材料为主，原则上不新增业务功能；发现的问题按最小改动修复。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 是权威来源）。
3. 优先最小改动，不无关重构。修复必须有测试覆盖。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律 ${ENV_VAR} 占位符。交付文档放 `delivery/`。
5. 必须运行全部测试并输出结果。测试不通过不得声明完成。
6. 文档须与实际系统功能一致，可操作（引用真实模块名/表名/接口/配置项）。
7. **诚实原则**：开发环境无法执行的项（48h 稳定性实测、故障演练实测、升级回滚实测、达梦/OceanBase 实测、ZAP 扫描实测），输出方案/脚本/报告模板并明确标注"待上线环境执行"，**不得编造实测数值**。
8. 可回滚：本阶段如改既有 V00x 迁移须同步 U00x；原则上不建新表。
9. 不做最终验收，最终验收由 Claude Code 完成；本阶段输出验收报告材料供审查。
10. 国产数据库双适配：开发期 H2(MySQL 模式)/MySQL 跑通。

---

## M6 阶段任务

### 阶段目标
稳定性测试方案与执行框架、故障演练脚本、升级回滚演练脚本、五类交付文档、验收报告、M5 遗留修复。

### 前置条件
M5 验收通过。

### 本阶段交付物
- 稳定性测试方案 + 48h 执行框架（JMeter 持续跑 + 监控采集脚本 + 通过判定标准）。
- 故障演练脚本（Chaos Mesh 或 kubectl/脚本注入：节点宕机/DB 主从切换/Redis 故障/Kafka 故障/双活切换）+ 演练报告模板。
- 升级回滚演练脚本（灰度 10%→50%→100% + 自动回滚 + 回滚 ≤10min 验证）。
- 五类文档（delivery/）：系统架构、部署手册、运维手册、开发手册、用户操作手册。
- 验收报告（delivery/acceptance-report.md）：功能/性能/安全/文档逐项对照 docs/requirements.md §7。
- M5 遗留修复（P-01M5~P-08M5 代码层可修项）。

### 实现要求

#### A. M5 遗留修复（代码层，必须有测试）
- **P-01M5**：后端 `Dockerfile` 端口动态化——`ENTRYPOINT` 用 `java -Dserver.port=${SERVER_PORT} -jar /app/app.jar`，`EXPOSE` 用 `ARG PORT` 默认 8080；ARG MODULE 保留。
- **P-02M5**：`DbAdapter` `connection.setReadOnly(true)` 移到 `createStatement` 之前调用。
- **P-03M5**：billing pom / pipeline pom 格式修正（`</dependencies>` 前补换行）。
- **P-05M5**：XXL-Job Handler 加 `@XxlJob` 注解——在 billing 模块 pom 引 `xxl-job-core:2.4.1`(与 docker-compose 一致，compile scope)，Handler 方法标注 `@XxlJob("billGenerate")`/`@XxlJob("statsAggregate")`，保留现有可调用入口。说明：若引依赖导致测试依赖 XXL-Job 运行时，用 provided 或确保注解类在编译期可用即可。
- **P-06M5**：集成测试增加真实 DB 落库场景——用 H2 内存库 + JdbcTemplate 验证 t_service_invoke_log 真实写入与查询（可在现有 M5EndToEndIntegrationTest 扩展或新增用例）。
- **P-08M5**：核对 national-db-compat-report.md 中 V003 等字段类型描述与实际 V003 SQL 一致，修正不一致描述。
- P-04M5（JMeter 实载）、P-07M5（k8s 各微服务 Deployment）属上线环境项，在验收报告标注待执行，本阶段不强做。

#### B. 稳定性测试方案与框架
1. `delivery/stability-test-plan.md`：48h 混合负载方案
   - 负载模型：复用 perf/jmeter/m5-performance.jmx，持续 48h，线程数/循环参数化。
   - 监控指标：JVM Heap/GC、TPS、P95/P99、错误率、DB 连接池、Redis 命中率。
   - 采集脚本 `perf/monitor/collect-metrics.sh`：通过 Spring Boot Actuator(`/actuator/metrics`、`/actuator/health`) + JVM GC 日志定时采样落 CSV。
   - 通过判定：无宕机、Heap 稳定(无持续上涨)、GC 正常、TPS 波动<10%、错误率<0.1%、无内存泄漏。
   - 实测值留空标注"待上线环境 48h 执行填充"。
2. `perf/monitor/heap-trend-analysis.md`：Heap 趋势分析方法（如何判断泄漏：多轮 GC 后 Heap 基线不持续抬升）。

#### C. 故障演练脚本
1. `delivery/chaos-drill/` 目录，每场景一脚本 + 一报告模板：
   - `node-down.sh`：kubectl scale 单节点副本 0 → 验证服务自动调度 → 恢复。
   - `db-failover.sh`：模拟主库不可用(标签/网络策略) → 验证切换备库 → 数据零丢失校验(SQL count 对比)。
   - `redis-down.sh`：scale redis 0 → 验证降级(JWT 无状态/缓存穿透 DB) → 恢复。
   - `kafka-outage.sh`：scale kafka 0 → 验证消息积压告警/消费者扩容 → 恢复。
   - `dual-active-switch.sh`：复用 k8s/dev/failover-drill.sh，扩展 RPO/RTO 采集点。
2. 每脚本含：故障注入命令、预期恢复行为、检查点命令、RPO/RTO 采集、退出条件。
3. `delivery/chaos-drill/chaos-report-template.md`：各场景切换时间/RPO/RTO/数据丢失/是否达标表格，实测留空标注待执行。
4. 所有脚本用 `set -euo pipefail`，参数化 `${NS}`/`${SERVICE}`，可重复执行。

#### D. 升级回滚演练
1. `delivery/upgrade-rollback-drill.md`：
   - 灰度发布流程：kubectl set image 10%→50%→100%，每阶段 health check + 流量验证。
   - 自动回滚：基于 health check 失败 / 错误率阈值触发 `kubectl rollout undo`。
   - 回滚计时脚本 `delivery/rollback-timer.sh`：触发回滚 → 计时到所有 Pod ready → 输出耗时，验证 ≤10min。
   - 业务无中断验证：灰度期间持续调用关键接口，断言无 5xx。
   - 实测耗时留空标注待执行。

#### E. 五类交付文档（delivery/）
每类一文件，与实际系统一致，引用真实模块名/表名/接口/配置项。每文档头部标注版本、日期、维护者。
1. `delivery/system-architecture.md`：架构图(ASCII 或 mermaid)、9 模块职责、技术选型表、数据流(接入→质量→存储→服务→消费→计费→统计→审计)、部署拓扑(双活)。
2. `delivery/deployment-guide.md`：环境要求(JDK17/Maven/Node20/Docker)、docker-compose 本地起、k8s/dev 部署步骤、配置项清单(所有 ${ENV_VAR} 逐项说明默认值/作用)、Flyway 迁移、国产化驱动安装(达梦 provided 手动安装)、前端构建部署。
3. `delivery/ops-manual.md`：监控指标(Actuator 端点 + 关键指标)、日志查看(各服务日志路径/级别)、故障排查(常见故障→定位→处理)、巡检清单(日/周/月)、备份恢复(DB/Redis/MinIO)。
4. `delivery/dev-guide.md`：模块结构、开发规范、扩展指南(新增协议适配器/格式转换器/质量规则/计费模型/服务)，各举一例指向既有代码。
5. `delivery/user-guide.md`：各功能模块操作步骤(登录/合作方/接入/服务/目录/消费方/质量/计费/统计/系统管理/大屏)、角色权限说明(管理员/合作方管理员/消费方/运维/开发)。

#### F. 验收报告
`delivery/acceptance-report.md`，对照 docs/requirements.md §7 验收标准逐项：
- 功能验收：46 条 FR 覆盖情况(逐条标注已实现模块/测试)、用例覆盖率、核心通过率。
- 性能验收：NFR-P01~P07 对照 perf/report-template.md，标注"待上线压测"。
- 可用性验收：NFR-A01~A05，故障演练/双活/升级回滚达标情况(待执行)。
- 安全验收：NFR-S01~S03，等保三级技术要求满足情况、加密/脱敏/审计覆盖、ZAP 扫描(待执行)。
- 兼容性验收：NFR-C01~C03，国产化(待达梦/OceanBase 实测)、对接、客户端。
- 文档验收：五类文档齐全且与系统一致。
- 代码质量验收：符合 requirements.md/claude-plan.md/codex-task、有测试可运行、无过度设计、可回滚。
- 总体结论：列已达标项与待上线环境验证项，不编造结论。

### 测试要求
- M5 遗留修复各有测试（Dockerfile 端口验证可仅文档说明构建方式；DbAdapter setReadOnly 顺序不改行为但补注释；pom 格式不影响测试；XXL-Job 注解编译通过；集成测试真实 DB 落库用例）。
- `mvn test` 全绿、`npm run test:unit` 全绿。
- 故障/升级演练脚本语法可检查（`bash -n`）。
- 文档完整性：五类文档 + 验收报告齐全，关键模块名/表名/接口与代码一致（抽查）。

### 完成判定（本阶段验收标准）
- M5 遗留 P-01M5/02M5/03M5/05M5/06M5/08M5 修复完成。
- 稳定性测试方案 + 采集脚本 + 报告框架产出。
- 5 个故障演练脚本 + 报告模板产出。
- 升级回滚演练脚本 + 文档产出。
- 五类交付文档产出且与系统一致。
- 验收报告产出，逐项对照 requirements.md §7，待执行项明确标注。
- `mvn test` + `npm run test:unit` 全绿。
- **实测类指标（48h 稳定性/故障演练/升级回滚/达梦 OceanBase/ZAP）明确标注"待上线环境执行"，不得编造。**

---

## 完成后必须输出（阶段完成报告 - M6）

完成后请严格按以下格式输出（Claude Code 据此做最终验收）：

## 阶段完成报告 - M6

### 1. 修改/新增文件清单
（分：M5遗留修复 / 稳定性 / 故障演练 / 升级回滚 / 文档 / 验收报告；标注新增 N / 修改 M）

### 2. 关键实现说明
（M5 遗留逐项修复说明；稳定性方案要点；故障演练场景清单；升级回滚流程；文档目录结构；验收报告结构）

### 3. 测试命令
（`mvn test` / `npm run test:unit` / `bash -n` 脚本语法检查 / 文档抽查）

### 4. 测试结果
- 单元/集成测试：通过数/失败数
- 脚本语法检查：故障/升级脚本 `bash -n` 是否通过
- 文档完整性：五类文档 + 验收报告是否齐全
- **待上线环境执行项**（明确列出）：48h 稳定性实测、5 故障演练实测、升级回滚实测、达梦/OceanBase 实测、ZAP 扫描实测

### 5. 验收报告摘要
- 功能覆盖率 / 用例覆盖率 / 核心通过率（开发环境可统计部分）
- 性能/可用性/安全/兼容性/文档/代码质量 各项状态（已达标 / 待上线验证）
- 总体结论

### 6. 偏离计划说明
（如有与 claude-plan.md 不一致或因环境限制调整，必须列出原因；说明文档放 delivery/ 而非 docs/ 的原因）

### 7. 潜在风险与遗留问题
（待上线环境验证项、上线前需关注事项、技术债）

===== 复制到此结束 =====
