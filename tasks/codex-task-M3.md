# Codex 桌面端 M3 阶段执行文本（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M2 阶段已通过 Claude Code 审查。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md

当前执行阶段：M3（质量体系 + 加工存储）
请只执行本提示词中「M3 阶段任务」段落列出的任务，不越界到 M4 及之后阶段。

---

## 项目背景（一句话）

从零搭建金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。M1/M2 已完成核心链路（接入/服务/消费方/目录）。本阶段聚焦数据质量与缓存存储加工。

---

## 执行规则（全局）

1. 只实现当前 M3 阶段任务，不越界到其他阶段。复用 M1/M2 已建好的基础设施。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 是架构/数据结构/接口/脚手架结构的权威来源，必须遵循）。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。如确需引入新依赖，必须在完成报告中说明理由。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须补充或更新测试，完成后运行全部测试。测试不通过不得声明完成。
6. 国产数据库双适配：开发期可用 MySQL/H2 跑通，但 DAO 层 SQL 必须兼容达梦与 OceanBase 方言。
7. 不重新发明轮子：调度用 XXL-Job，缓存用 Redis，ETL 用 Spring Batch，质量规则用策略模式 + Java Predicate（六维各一策略类，不用完整 Drools）。状态机用枚举 + 事件驱动。
8. 配置外置，不硬编码。
9. 安全基线：密码/密钥加密存储（SM4），外部输入校验，敏感日志脱敏。
10. 可回滚：SQL 迁移脚本用 Flyway 管理，有回滚脚本。
11. 不做最终验收，最终验收由 Claude Code 完成。

---

## M3 阶段任务

### 阶段目标
完成数据质量管理全量功能与数据缓存/分级存储/加工/集市/生命周期，并在接入链路集成质量校验。

### 前置条件
M2 验收通过（接入/服务/消费方/目录已就绪，t_raw_data 有原始数据可加工）。

### 本阶段交付物
- platform-quality 完整服务（新建模块）：六维质量规则（完整性/准确性/一致性/及时性/有效性/唯一性）+ 规则配置 + 自动校验执行器 + 问题工单闭环 + 质量报告 + 评分评级。
- platform-pipeline.storage 子模块：多维缓存（接口结果/热点/全量）、分级存储（热/温/冷）、数据加工 ETL（清洗/标准化/关联/标签）、外部数据集市、数据生命周期（归档/销毁）。
- 接入链路集成质量校验：接入数据落库前触发质量校验，fail_rate 超阈值暂停接入（对应异常场景 E-04）。

### 实现要求
1. 六维规则用策略模式：QualityRule 接口 + 6 个实现类（CompletenessRule / AccuracyRule / ConsistencyRule / TimelinessRule / ValidityRule / UniquenessRule），规则配置存 t_quality_rule，表达式用 JSON 配置（非硬编码）。
2. 校验执行器对接入数据/加工数据/服务输出数据均可触发；校验结果写 t_quality_check_result（表结构按 claude-plan.md 4.4.4）。
3. 问题工单状态机：OPEN→ASSIGNED→FIXING→VERIFYING→CLOSED，非法转移拒绝。
4. 评分模型：六维加权可配（权重存配置表 t_quality_weight），输出 0-100 分与评级（A/B/C/D）。
5. 缓存用 Redis + Spring Cache 注解；热点数据用 LFU 策略；缓存失效规则可配（TTL/主动失效）；缓存命中率统计。
6. 分级存储：热数据 Redis、温数据 OceanBase（开发期 MySQL）、冷数据 MinIO/归档；路由策略按访问频率自动判定（t_storage_policy 配置）。
7. ETL 用 Spring Batch 或自定义 pipeline；清洗规则可配；标签加工输出到 t_data_asset（标准化资产表）。
8. 外部数据集市：加工后数据集中存储 t_marketplace_data，支持共享复用查询。
9. 生命周期：定时任务（XXL-Job）扫描到期数据，按归档/销毁规则处置，留痕 t_lifecycle_record。
10. 接入集成：在 M2 的接入落库流程中插入质量校验环节，校验不通过的数据进问题工单，fail_rate 超阈值暂停接入任务并告警。
11. docker-compose 补充 MinIO、Elasticsearch（为后续日志/搜索预留，本阶段可先起服务）。

### 数据库表设计依据
严格按 tasks/claude-plan.md 第 4.4.4 节（t_quality_rule / t_quality_check_result / t_quality_issue）建质量表；新增存储相关表（t_storage_policy / t_data_asset / t_marketplace_data / t_lifecycle_record / t_quality_weight）。达梦/OceanBase 兼容写法。

### 测试要求
- 六维规则各至少 1 个用例（正常通过 + 异常命中）。
- 质量校验执行器对脏数据识别测试（缺失/重复/格式错误）。
- 工单状态流转测试（含非法转移拒绝）。
- 评分模型计算准确性测试（给定六维通过率，验证加权得分与评级）。
- 缓存命中/失效测试。
- ETL 加工链路测试（输入原始数据 → 输出标准化资产，字段映射/清洗/标签正确）。
- 生命周期归档/销毁执行测试。
- 接入集成质量校验：脏数据被拦截、fail_rate 超阈值暂停接入测试。
- 整体行覆盖率 ≥80%，核心方法 100%。

### 完成判定（本阶段验收标准）
- 六维质量规则可配可执行。
- 接入脏数据被校验拦截，fail_rate 超阈值暂停接入。
- 缓存命中率可达 ≥90%（构造热点场景验证）。
- ETL 加工产出标准化资产可查。
- 分级存储路由生效。
- 生命周期归档/销毁可执行留痕。

---

## 完成后必须输出（阶段完成报告 - M3）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M3

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M）

### 2. 关键实现说明
（每个核心类/接口一句话职责说明）

### 3. 测试命令
（可在项目根目录直接运行的命令）

### 4. 测试结果
（覆盖率、通过数/失败数、失败用例明细；如有失败不得声明完成）

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致之处，必须列出原因）

### 6. 潜在风险与遗留问题
（未完成项、待下一阶段处理项、技术债）

===== 复制到此结束 =====
