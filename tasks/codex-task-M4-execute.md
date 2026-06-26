# Codex 桌面端 M4 阶段执行任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M3 阶段已通过 Claude Code 审查（见 reviews/claude-review.md 结论"通过"）。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M4.md   ← 本阶段任务来源
7. reviews/claude-review.md ← M1~M3 审查历史，了解既有实现与遗留项

当前执行阶段：M4（治理：计费 + 统计监管 + 前端 + 审计落地）
请只执行本提示词中「M4 阶段任务」段落列出的任务，不越界到 M5 及之后阶段。

---

## 项目背景（一句话）

金融机构外部数据采集平台。技术栈：Java 17 + Spring Boot 3.x + Spring Cloud Alibaba 2023.x + MyBatis-Plus 3.5.x + Nacos + Sentinel + Vue3 + 达梦/OceanBase 双适配 + 同城双活。M1/M2/M3 已完成后端核心链路、质量体系与加工存储。本阶段完成计费统计治理、审计日志落地与全部前端控制台。

### 既有现状（必须复用，不得推翻）
- `t_service_invoke_log`（V005）字段：id / service_code / consumer_code / status_code / elapsed_millis / log_day / created_at。**计费与统计的数据来源即此表**。
- 审计基础设施：`platform-common/audit/AuditLog`（注解）+ `AuditLogger`（仅内存 CopyOnWriteArrayList + java.util.logging，**无切面、无 t_audit_log 表**）。本阶段需补切面 + 表 + 持久化。
- 已有模块：platform-common / gateway / auth / partner / quality / pipeline。`platform-billing` 与 `platform-ui` **尚未创建**，本阶段新建。
- 根 pom modules 当前不含 billing/ui，本阶段需注册。

---

## 执行规则（全局）

1. 只实现 M4 阶段任务，不越界到 M5 及之后阶段。复用 M1/M2/M3 已建后端服务。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 第 4.4.5 / 4.4.6 节是表结构权威来源，必须遵循）。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。如确需引入新依赖，必须在完成报告中说明理由。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须补充或更新测试，完成后运行全部测试。测试不通过不得声明完成。
6. 国产数据库双适配：建表 SQL 兼容达梦与 OceanBase 方言，统一 `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`、标志位用 `TINYINT`，与 V001~V007 既有风格一致。
7. 不重新发明轮子：统计用定时任务聚合（不做实时流计算，满足 ≤5 分钟延迟即可），报表用 EasyExcel/POI，不写自定义报表引擎。状态机用枚举 + 事件驱动。
8. 配置外置，不硬编码。
9. 安全基线：密码/密钥加密存储（SM4，复用 platform-common Sm4Util），外部输入校验，敏感日志脱敏，审计日志不可篡改。
10. 可回滚：SQL 迁移脚本用 Flyway 管理，有回滚脚本。M4 新表用 **V008**，回滚用 **U008**。
11. 不做最终验收，最终验收由 Claude Code 完成。
12. 前端独立 npm 工程，不阻塞后端 mvn test；后端测试不得因前端缺失而失败。

---

## M4 阶段任务

### 阶段目标
完成计费管理、统计监管全量功能，审计日志落地（不可篡改），并实现 Vue3 管理控制台全部页面。

### 前置条件
M3 验收通过；t_service_invoke_log 已有调用日志可作为计费/统计来源。

### 本阶段交付物
- **platform-billing（新建模块）**：多维度计费模型 + 规则配置 + 账单生成核对异议 + 费用统计 + 财务对接适配器（Mock）。
- **platform-billing.stats**：全链路统计聚合 + 监管报表生成导出 + 监管报送适配器（Mock）+ 合规审计追溯 + 大屏数据接口。
- **审计日志落地**：t_audit_log 表 + @AuditLog AOP 切面持久化（不可篡改）。
- **platform-ui（新建，Vue3）**：全部模块页面。
- **V008 迁移 / U008 回滚**：t_billing_rule / t_bill / t_stats_snapshot / t_audit_log。

### 实现要求

#### A. 数据库（V008__governance.sql / U008__governance.sql）
严格按 claude-plan.md 4.4.5 + 4.4.6 建表，达梦/OceanBase 兼容：
- `t_billing_rule`：id, rule_code(UNIQUE), rule_name, billing_model(VARCHAR: BY_COUNT/BY_VOLUME/BY_INTERFACE/BY_PACKAGE/BY_DURATION), target_type(PARTNER/CONSUMER/SERVICE), target_id, unit_price(DECIMAL(12,6)), currency(VARCHAR default CNY), effective_from(DATE), effective_to(DATE), status(VARCHAR default ACTIVE), created_at, updated_at。
- `t_bill`：id, bill_no(UNIQUE), bill_type(SETTLEMENT/EXPENSE), bill_period(DAILY/MONTHLY/QUARTERLY), period_start(DATE), period_end(DATE), total_amount(DECIMAL(16,4)), status(VARCHAR: GENERATED/CONFIRMED/DISPUTED/ADJUSTED/SETTLED), created_at, updated_at。
- `t_stats_snapshot`：id, metric_name(VARCHAR: INGEST_COUNT/INVOKE_COUNT/TRANSFER_BYTES/CACHE_HIT_RATE/SUCCESS_RATE), dimension(PARTNER/CONSUMER/SERVICE), dimension_id(BIGINT nullable), metric_value(DECIMAL(20,4)), snapshot_at(TIMESTAMP), 索引 idx_snapshot(metric_name, snapshot_at)。
- `t_audit_log`：按 4.4.6，id, trace_id, event_type, actor_type, actor_id, target_type, target_id, action, detail(TEXT/JSON), source_ip, user_agent, status(SUCCESS/FAILED), created_at；索引 idx_trace / idx_event_type_created / idx_actor。**不可篡改**：开发期用 H2/MySQL 时通过 DAO 层仅提供 INSERT 不提供 UPDATE/DELETE 接口 + 单测验证；生产达梦/OceanBase 可备注触发器禁改（脚本注释说明，不强建触发器以免方言问题）。
- U008 反向 DROP 四表，顺序正确。

#### B. platform-billing 计费
1. 计费规则引擎：`BillingRuleEngine`，按 target_type + billing_model 匹配规则，支持套餐叠加（BY_PACKAGE 与其他模型并存时按套餐包含量计费）。规则从 `t_billing_rule` 加载（开发期可内存仓储接口 + 内存实现，DAO 接口预留）。
2. 五种计费模型各一策略类（策略模式，参考 M3 质量规则风格）：BillingCalculator 接口 + CountCalculator / VolumeCalculator / InterfaceCalculator / PackageCalculator / DurationCalculator。
3. 账单生成：`BillGenerator` 基于 t_service_invoke_log 按周期（日/月/季度）聚合 + 规则匹配计算金额，写入 t_bill（状态 GENERATED）。定时任务入口定义（XXL-Job Handler 类，开发期不依赖 XXL-Job 运行时，提供可被直接调用的 generate 方法）。
4. 账单状态机：`BillStateMachine`，GENERATED→CONFIRMED→DISPUTED→ADJUSTED→SETTLED，非法转移抛 BusinessException。异议(Dispute)、调整(Adjust)、结算(Settle)操作。
5. 财务对接适配器：`FinanceSystemAdapter` 接口 + `MockFinanceSystemAdapter`，真实对接待外部规范。

#### C. platform-billing.stats 统计监管
1. 统计聚合：`StatsAggregator` 从 t_service_invoke_log 聚合 INVOKE_COUNT / SUCCESS_RATE / TRANSFER_BYTES（elapsed_millis 可作传输量代理或留接口）/ CACHE_HIT_RATE（从 M3 LfuCacheService.hitRate() 取，定义采集接口）等指标，写入 t_stats_snapshot。定时任务入口。
2. 监管报表：`ReportGenerator` 用 EasyExcel 生成三类报表：合规报表 / 数据来源统计 / 个人信息使用报表。模板可配，导出 .xlsx。
3. 监管报送适配器：`RegulatoryReportingAdapter` 接口 + `MockRegulatoryReportingAdapter`。
4. 合规审计追溯：`AuditTraceService` 从 t_audit_log 按 trace_id / actor / event_type / 时间范围检索。
5. 大屏数据接口：`DashboardService` 聚合运行状态/数据服务/合规管控/成本费用四类指标，供前端调用（本阶段出 Service + DTO，Controller 可放 billing 模块或留 M5 集成）。

#### D. 审计日志落地
1. 新增 `@AuditLog` AOP 切面（platform-common 或 platform-billing，建议放 platform-common 供全服务复用）：拦截标注方法，构造 AuditEvent 写入 t_audit_log。
2. `AuditLogRepository` 仅 INSERT 接口（无 update/delete），开发期 JdbcTemplate/H2 实现。
3. `AuditLogger` 既有内存记录保留向后兼容，切面同时写库 + 内存。
4. 不可篡改验证：单测尝试 update/delete 应被 DAO 接口拒绝（无对应方法）或触发异常。

#### E. platform-ui 前端（Vue3）
1. 技术栈：Vue3 + Element Plus + ECharts + Vite + Pinia + axios + Vue Router 4。Vue Test Utils + vitest 测试。
2. 统一 API 封装：axios 实例 + 拦截器（Token 注入 / 统一错误处理 / 401 跳转登录）。
3. 路由权限：基于后端返回权限码动态渲染菜单（权限码可 Mock，与 M1 auth 衔接）。
4. 页面（每个模块至少一个列表页 + 关键操作）：
   - 登录页
   - 合作方管理（列表 / 状态流转）
   - 接入任务（列表 / 创建）
   - 数据服务（列表 / 发布）
   - 数据目录（浏览 / 检索）
   - 消费方管理（列表 / 配额）
   - 数据质量（规则列表 / 工单 / 评分）
   - 计费管理（规则 / 账单 / 异议）
   - 统计监管（统计面板 / 报表 / 大屏）
   - 系统管理（用户 / 角色 / 权限）
   - 监控大屏（ECharts，四类核心指标）
5. 响应式布局；API 调用可用 Mock（msw 或 axios-mock-adapter），不依赖后端真实运行。
6. 根目录独立 `platform-ui/` 工程，`npm install && npm run build && npm run test:unit` 可通过。

### 数据库表设计依据
严格按 tasks/claude-plan.md 第 4.4.5 节（t_billing_rule / t_bill / t_stats_snapshot）与 4.4.6 节（t_audit_log）。达梦/OceanBase 兼容写法，审计表追加写约束（DAO 层仅 INSERT + 注释说明）。

### 测试要求
- 计费规则匹配准确性测试：五种计费模型各一用例（给定 invoke_log + 规则，验证金额）。
- 账单生成 + 状态流转测试（含非法转移拒绝、异议/调整/结算闭环）。
- 统计指标聚合准确性测试（给定 invoke_log，验证 INVOKE_COUNT / SUCCESS_RATE 等聚合值）。
- 报表生成导出测试（EasyExcel 生成 .xlsx，验证文件可解析、含预期 Sheet/表头）。
- 审计日志写入 + 不可篡改测试（写入成功；尝试 update/delete 被拒绝）。
- 财务/监管适配器 Mock 调用测试。
- 前端：核心页面渲染测试（Vue Test Utils，至少 3 个页面）、API 调用 Mock 测试、路由权限控制测试。
- 整体行覆盖率 ≥80%，核心方法 100%。
- 后端 `mvn test` 全绿；前端 `npm run test:unit` 全绿。

### 完成判定（本阶段验收标准）
- 计费账单可生成核对，五种计费模型可用。
- 统计面板数据准确，大屏接口可用。
- 前端可操作全部 9 大模块功能（页面渲染 + 路由权限）。
- 审计日志全量记录且不可篡改（DAO 仅 INSERT）。
- 监管报表可生成导出 .xlsx。
- V008/U008 齐全；根 pom 注册 platform-billing；platform-ui 工程可独立构建。

---

## 完成后必须输出（阶段完成报告 - M4）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M4

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M；后端与前端分开）

### 2. 关键实现说明
（每个核心类/接口/页面一句话职责说明；计费五模型、账单状态机、统计聚合、报表、审计切面、前端路由权限各覆盖）

### 3. 测试命令
（后端 `mvn test` + 前端 `npm install && npm run test:unit`）

### 4. 测试结果
（后端通过数/失败数、前端通过数/失败数、覆盖率、失败用例明细；如有失败不得声明完成）

### 5. 偏离计划说明
（如有与 claude-plan.md 4.4.5/4.4.6 不一致之处，必须列出原因；如 t_service_invoke_log 字段简化导致计费/统计字段映射调整，需说明）

### 6. 潜在风险与遗留问题
（未完成项、待 M5/M6 处理项、技术债；如 XXL-Job 定时未真正注册、报表模板待业务确认、前端 API 全 Mock 等）

===== 复制到此结束 =====
