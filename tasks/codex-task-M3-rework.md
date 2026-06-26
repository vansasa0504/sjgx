# Codex 桌面端 M3 阶段返工任务（可全文复制粘贴）

> 使用方式：在 Codex 桌面端新建会话，工作目录 E:\project\sjgx，将下方「===== 从此处开始复制 =====」到结尾的全部文本粘贴发送即可。
> 前置条件：M3 阶段首次实现已完成，Claude Code 审查结论为「需要返工（中度）」，审查文件见 `reviews/claude-review.md`。

===== 从此处开始复制 =====

你是本项目的代码执行智能体（Codex）。

请严格读取并遵守以下文件：
1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md
6. tasks/codex-task-M3.md
7. reviews/claude-review.md   ← 本次返工依据，必须逐条对照 FIX-301 ~ FIX-311

当前执行阶段：M3 返工（质量体系 + 加工存储）
请只执行本提示词中「M3 返工任务」段落列出的任务，不越界到 M4 及之后阶段。本次为返工，不得推翻 M3 已通过的设计方向（策略模式六维规则、最小闭环、不过度设计），只修正审查指出的偏离与遗漏。

---

## 项目背景（一句话）

金融机构外部数据采集平台。M3 首次实现已完成 platform-quality 六维质量体系与 platform-pipeline.storage 加工存储子模块，25 个测试全部通过。本次返工聚焦：数据库表结构与 claude-plan.md 4.4.4 对齐、补齐缺失的配置/集市表、分级存储真实落库、SM4 存储加密接入、docker-compose 补 MinIO/ES、评分与 failRate 语义修正、测试补强。

---

## 执行规则（全局）

1. 只实现本次返工任务（FIX-301 ~ FIX-311），不越界到 M4 及之后阶段，不重新实现 M3 已通过的部分。
2. 不重新解释需求，不覆盖 Claude Code 的需求判断和开发计划（claude-plan.md 第 4.4.4 节是表结构的权威来源，必须严格对齐）。
3. 优先采用最小改动，不进行无关重构，不引入大型新依赖。如确需引入新依赖，必须在完成报告中说明理由。
4. 不修改：.env / .env.* / *.pem / *.key / *.crt / CLAUDE.md / AGENTS.md / docs/ / tasks/ / reviews/ / k8s/prod/。密码密钥一律用 ${ENV_VAR} 占位符。
5. 必须补充或更新测试，完成后运行全部测试。测试不通过不得声明完成。
6. 国产数据库双适配：开发期可用 MySQL/H2 跑通，但建表 SQL 必须兼容达梦与 OceanBase 方言。统一用 `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`；布尔/标志位统一用 `TINYINT`（与 V001~V006 既有脚本风格一致）。
7. 不重新发明轮子：缓存用 Redis，调度用 XXL-Job，质量规则保持策略模式 + Java Predicate，不引入 Drools/Spring Batch。
8. 配置外置，不硬编码。
9. 安全基线：密码/密钥加密存储（SM4，复用 platform-common 的 `com.platform.common.security.Sm4Util`），外部输入校验，敏感日志脱敏。
10. 可回滚：SQL 迁移脚本用 Flyway 管理，有回滚脚本。本次返工如改 V007，须同步更新 U007；如新增表，U007 须补对应 DROP。
11. 不做最终验收，最终验收由 Claude Code 完成。
12. 数据库迁移脚本编号规则：V007 已存在，本次返工**修改 V007 本身**（M3 尚未提交，无需新增 V008），同步更新 U007。

---

## M3 返工任务

### 阶段目标
修正 M3 首次实现与 claude-plan.md 4.4.4 及 codex-task-M3 的偏离，补齐缺失的配置表与持久化，使"可配外置""按规则归因""分级落库""存储加密"验收项真正满足。

### 前置条件
M3 首次实现工作区改动仍在（未提交）：platform-quality 模块、platform-pipeline/storage 子包、V007/U007 脚本、IngestQualityGuard 均已存在。

### 返工清单（对照 reviews/claude-review.md §11）

#### FIX-301 [高] V007 质量三表字段对齐 plan 4.4.4
按 `tasks/claude-plan.md` 第 4.4.4 节重写 `db/migration/V007__quality_storage.sql` 中的三张质量表，同步更新 `db/rollback/U007__quality_storage.sql`：

- `t_quality_rule`：字段对齐为 `id, rule_code(UNIQUE), rule_name, dimension, rule_type(SYSTEM_BUILTIN/CUSTOM), target_object, rule_expression(TEXT,JSON), severity(ERROR/WARN/INFO), enabled(TINYINT), created_at, updated_at`。保留首次实现的 `field_name`/`expression_json` 语义时，将其纳入 `rule_expression` JSON 结构（即规则字段名、表达式参数都写在 rule_expression JSON 里），不再单独建列。
- `t_quality_check_result`：字段对齐为 `id, rule_id, batch_no, total_count, pass_count, fail_count, fail_rate(DECIMAL(5,4)), checked_at`，并保留与首次实现执行器结果的映射能力（rule_id 允许为空表示批量聚合结果）。
- `t_quality_issue`：字段对齐为 `id, check_result_id, rule_id, issue_type(ANOMALY/MISSING/ERROR/DUPLICATE), severity, description, status(OPEN/ASSIGNED/FIXING/VERIFYING/CLOSED), assignee, resolution, created_at, updated_at`。

要求：表结构必须与 plan 4.4.4 一致；Java 实体/记录类（如 QualityRuleConfig、QualityCheckResult、QualityIssue）若需扩展字段以映射新列，做最小扩展，不破坏既有测试语义。

#### FIX-302 [高] 新增 t_quality_weight 权重配置表
- 在 V007 新增 `t_quality_weight`：`id, dimension(VARCHAR,六维之一), weight(INT), enabled(TINYINT), created_at, updated_at`。
- 新增 `QualityWeightConfig` 加载入口（可 DAO 或内存仓储接口，开发期提供内存实现），`QualityScoringService` 改为支持从权重配置加载权重；当无外部配置时回退到 `QualityRuleConfig.weight()` 既有行为（保持向后兼容）。
- 测试：给定权重配置表覆盖默认值的场景。

#### FIX-303 [高] 新增 t_storage_policy 存储策略表
- 在 V007 新增 `t_storage_policy`：`id, policy_code(UNIQUE), hot_threshold(INT), warm_threshold(INT), cool_target(VARCHAR:REDIS/OCEANBASE/MINIO), enabled(TINYINT), created_at, updated_at`。
- `TieredStorageRouter` 改为可由策略配置构造（保留现有 int 构造器用于测试，新增从 policy 构造的入口）。
- 测试：从策略配置构造路由器，验证阈值生效。

#### FIX-304 [中] 新增 t_marketplace_data 集市持久化表
- 在 V007 新增 `t_marketplace_data`：`id, asset_code(UNIQUE), fields_json(TEXT), tags_json(TEXT), source(VARCHAR), created_at`。
- `DataMarketplace` 增加可注入的持久化接口（`MarketplaceRepository`，开发期提供内存实现 + 可选 JDBC 实现），publish 时写仓储，searchByTag/list 从仓储读。
- 保留现有内存构造器以兼容测试。

#### FIX-305 [中] t_lifecycle_event 重命名为 t_lifecycle_record
- V007 中 `t_lifecycle_event` 改名为 `t_lifecycle_record`（与 codex-task-M3 §71 要求一致），U007 同步。
- Java 侧 `LifecycleEvent` 记录类名可保留（它是内存事件模型），但若已有 DAO/表映射需同步改名。

#### FIX-306 [中] 分级存储真实落温库
- `TieredStorageRouter` 增加可注入的温库/冷库写入接口（`WarmStorageStore` 走 JDBC DataSource、`ColdStorageStore` 走 MinIO 或本地归档目录占位）。
- 开发期温库用 H2/MySQL DataSource 真实写入（参考 M2 DbAdapter 的 JDBC 用法），冷库用本地目录占位实现（写文件到 `${COLD_STORAGE_DIR:-./target/cold-storage}`，路径外置，不硬编码）。
- HOT 仍走 Redis（可复用 `RedisCacheStore` 或留接口）。
- store(key,payload) 时按路由结果分别落入对应存储；records(tier) 改为从对应存储读取。
- 测试：用 H2 内存库验证温库真实写入，用临时目录验证冷库归档文件生成。

#### FIX-307 [中] docker-compose 补 MinIO / Elasticsearch
- 在 `docker-compose.yml` 补充两个服务定义（本阶段起服务即可，不需应用接入）：
  - `minio`：`minio/minio` 镜像，端口 9000/9001，环境变量 `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` 用 `${ENV_VAR}` 占位，command `server /data --console-address ":9001"`。
  - `elasticsearch`：`docker.elastic.co/elasticsearch/elasticsearch:8.x`，端口 9200，环境变量 `discovery.type=single-node`，`xpack.security.enabled=false`（开发期），`ES_JAVA_OPTS=-Xms512m -Xmx512m`。
- 不修改既有服务定义。

#### FIX-308 [中] SM4 存储加密接入
- 在 storage 落库点接入 `com.platform.common.security.Sm4Util`：至少 `t_data_asset.fields_json` 与 `t_marketplace_data.fields_json` 落库前加密、读取时解密。
- 加密 key 从配置读取（`${DATA_ASSET_SM4_KEY}` 占位符，开发期可用 application.yml 默认值），不得硬编码。
- 新增 `StorageCipher` 封装（encrypt/decrypt），便于测试注入。
- 测试：写入后 fields_json 为密文（非明文），读取后还原为明文。

#### FIX-309 [中] 评分基线修正
- `QualityScoringService` 维度失败率分母修正：`dimensionFailRate = 该维违规数 / (totalRows × 该维规则数)`，避免同维度多规则时惩罚被放大。
- 补测试：同维度 2 条规则、部分行违规，验证得分与单规则场景一致（不被双倍惩罚）。

#### FIX-310 [低] 六维规则独立测试补强
- 为六维规则各补至少 1 个"正常通过 + 异常命中"成对用例（可拆分为 6×2 或 6 个参数化用例），提升分支覆盖。
- 保留现有复合用例。

#### FIX-311 [低] failRate 语义明确
- 在 `QualityCheckExecutor.check` 的 failRate 计算处补 JavaDoc，明确语义为"规则×行失败率"（分母 = totalRows × configs.size()），或改为"行级失败率"（任一规则失败的行数 / totalRows）。
- 推荐改为行级失败率（更符合 E-04 "fail_rate > 阈值暂停接入"的语义）。若改语义，同步更新 IngestQualityGuard 与既有测试断言。
- 在完成报告中说明最终选择的语义。

### 数据库表设计依据
严格按 `tasks/claude-plan.md` 第 4.4.4 节。新增表（t_quality_weight / t_storage_policy / t_marketplace_data / t_lifecycle_record）达梦/OceanBase 兼容写法，与 V001~V006 既有脚本风格一致。

### 测试要求
- FIX-301：质量三表新字段在 DAO/记录类映射上有验证（可用 H2 内存库或内存仓储测试）。
- FIX-302：权重配置覆盖默认值的评分测试。
- FIX-303：从策略配置构造路由器的测试。
- FIX-304：集市持久化写入/查询测试。
- FIX-306：温库 H2 真实写入 + 冷库归档文件测试。
- FIX-308：SM4 加密落库/读取还原测试。
- FIX-309：同维度多规则不放大惩罚测试。
- FIX-310：六维规则各正常+异常用例。
- FIX-311：failRate 语义对应的断言更新。
- 整体行覆盖率 ≥80%，核心方法 100%（如条件受限，至少保证新增/修改方法有测试）。
- 全部模块测试通过（`mvn test`），不得有失败。

### 完成判定（本阶段返工验收标准）
- V007/U007 表结构与 claude-plan.md 4.4.4 对齐，t_quality_weight / t_storage_policy / t_marketplace_data / t_lifecycle_record 齐全。
- 权重、存储策略可从配置加载，不再硬编码。
- 分级存储温库真实落 JDBC、冷库落归档目录。
- t_data_asset / t_marketplace_data 的 fields_json 落库为 SM4 密文。
- docker-compose 含 MinIO、Elasticsearch。
- 评分多规则不放大惩罚；failRate 语义明确。
- 六维规则独立测试补齐。
- `mvn test` 全绿。

---

## 完成后必须输出（阶段完成报告 - M3 返工）

完成后请严格按以下格式输出（Claude Code 据此审查）：

## 阶段完成报告 - M3 返工

### 1. 修改/新增文件清单
（按目录列出，标注新增 N / 修改 M；重点标注 FIX-301~311 各自涉及的文件）

### 2. 关键实现说明
（每个 FIX 一句话说明最终实现方式；特别说明 FIX-311 选择的 failRate 语义）

### 3. 测试命令
（可在项目根目录直接运行的命令，如 `mvn test`）

### 4. 测试结果
（通过数/失败数、覆盖率、失败用例明细；如有失败不得声明完成）

### 5. 偏离计划说明
（如有与 claude-plan.md 不一致之处，必须列出原因）

### 6. 潜在风险与遗留问题
（未完成项、待下一阶段处理项、技术债）

===== 复制到此结束 =====
