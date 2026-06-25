# Codex 桌面端 M4 阶段执行文本（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M3 阶段已通过 Claude Code 审查。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md

当前执行阶段：M4（治理：计费 + 统计监管 + 前端）
请只执行本提示词中「M4 阶段任务」段落列出的任务，不越界到 M5 及之后阶段。

---

## 项目背景（一句话）

从零搭建金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。M1/M2/M3 已完成后端核心与质量存储。本阶段完成计费统计治理与全部前端。

---

## 执行规则（全局）

1. 只实现当前 M4 阶段任务，不越界到其他阶段。复用 M1/M2/M3 已建好的后端服务。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 是架构/数据结构/接口/脚手架结构的权威来源，必须遵循）。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。如确需引入新依赖，必须在完成报告中说明理由。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须补充或更新测试，完成后运行全部测试。测试不通过不得声明完成。
6. 国产数据库双适配：DAO 层 SQL 兼容达梦与 OceanBase 方言。
7. 不重新发明轮子：统计用定时任务聚合（不做实时流计算，满足 ≤5 分钟延迟即可），报表用 EasyExcel/POI，不写自定义报表引擎。状态机用枚举 + 事件驱动。
8. 配置外置，不硬编码。
9. 安全基线：密码/密钥加密存储（SM4），外部输入校验，敏感日志脱敏，审计日志不可篡改。
10. 可回滚：SQL 迁移脚本用 Flyway 管理，有回滚脚本。
11. 不做最终验收，最终验收由 Claude Code 完成。

---

## M4 阶段任务

### 阶段目标
完成计费管理、统计监管全量功能，并实现 Vue3 管理控制台全部页面，审计日志落地。

### 前置条件
M3 验收通过（t_service_invoke_log 已有调用日志可作为计费统计来源）。

### 本阶段交付物
- platform-billing（新建模块）：多维度计费模型（按次/量/接口/套餐/时长）、计费规则配置、账单自动生成（日/月/季度）+ 核对 + 异议处理、费用统计分析、财务/采购系统对接适配器接口（Mock 实现，待外部规范）。
- platform-billing.stats：全链路统计（接入量/调用量/传输量/缓存命中率/成功率）、监管报表自动生成（合规/来源/个人信息）、监管报送适配器接口（Mock）、合规审计追溯、可视化大屏数据接口。
- platform-ui（新建，Vue3 + Element Plus + ECharts + Vite + Pinia）：全部模块页面。
- 审计日志落地：M1 的 @AuditLog 切面写入 t_audit_log（不可篡改，追加写）。

### 实现要求
1. 计费基于 M2 的服务调用日志（t_service_invoke_log）聚合计算，定时任务（XXL-Job）按周期生成账单。
2. 计费规则引擎：按 target_type（PARTNER/CONSUMER/SERVICE）+ billing_model（BY_COUNT/BY_VOLUME/BY_INTERFACE/BY_PACKAGE/BY_DURATION）匹配规则，支持套餐叠加。表 t_billing_rule 按 claude-plan.md 4.4.5 设计。
3. 账单状态机：GENERATED→CONFIRMED→DISPUTED→ADJUSTED→SETTLED，非法转移拒绝。账单表 t_bill。
4. 统计指标用定时任务聚合到 t_stats_snapshot（不做实时流计算，满足 ≤5 分钟延迟）。
5. 监管报表用模板引擎（EasyExcel/POI）生成，支持自定义配置导出。报表类型：合规报表/数据来源统计/个人信息使用报表。
6. 财务/监管对接：定义适配器接口（FinanceSystemAdapter / RegulatoryReportingAdapter）+ Mock 实现，真实对接待外部规范明确后替换（不影响核心链路）。
7. 前端 platform-ui：
   - 技术栈 Vue3 + Element Plus + ECharts + Vite + Pinia + axios
   - 统一 API 封装（axios 拦截器处理 Token 注入/错误处理/401 跳转登录）
   - 路由权限控制（基于后端返回的权限码动态渲染菜单）
   - 页面：登录、合作方管理、接入任务、数据服务、数据目录、消费方管理、数据质量、计费管理、统计监管、系统管理（用户/角色/权限）、监控大屏
   - 响应式布局，ECharts 大屏
8. 审计日志表 t_audit_log 按 claude-plan.md 4.4.6 设计（追加写，提供防篡改：仅追加约束或哈希链）。@AuditLog 切面将 M1 的日志输出改为同时写入 t_audit_log。
9. 全链路统计接口供前端大屏调用（运行状态/数据服务/合规管控/成本费用核心信息）。

### 数据库表设计依据
严格按 tasks/claude-plan.md 第 4.4.5 节（t_billing_rule / t_bill / t_stats_snapshot）与 4.4.6 节（t_audit_log）建表。达梦/OceanBase 兼容写法，审计表追加写约束。

### 测试要求
- 计费规则匹配准确性测试（五种计费模型各一）。
- 账单生成 + 状态流转测试（含非法转移拒绝）。
- 统计指标聚合准确性测试（给定调用日志，验证聚合值）。
- 报表生成导出测试（Excel 导出格式正确）。
- 审计日志写入 + 不可篡改测试（尝试 UPDATE/DELETE 被拒绝）。
- 财务/监管适配器 Mock 调用测试。
- 前端：核心页面渲染测试（Vue Test Utils）、API 调用 Mock 测试、路由权限控制测试。
- 整体行覆盖率 ≥80%，核心方法 100%。

### 完成判定（本阶段验收标准）
- 计费账单可生成核对，五种计费模型可用。
- 统计面板数据准确，大屏接口可用。
- 前端可操作全部 9 大模块功能。
- 审计日志全量记录且不可篡改。
- 监管报表可生成导出。

---

## 完成后必须输出（阶段完成报告 - M4）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M4

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M）

### 2. 关键实现说明
（每个核心类/接口/页面一句话职责说明）

### 3. 测试命令
（后端 mvn test + 前端 npm run test:unit）

### 4. 测试结果
（覆盖率、通过数/失败数、失败用例明细；如有失败不得声明完成）

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致之处，必须列出原因）

### 6. 潜在风险与遗留问题
（未完成项、待下一阶段处理项、技术债）

===== 复制到此结束 =====
