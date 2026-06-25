# Claude Code 开发计划

> 本文件由 Claude Code 基于 `docs/requirements.md`、`tasks/requirement-analysis.md` 和第一性原理生成。
> 日期：2026-06-24
> 已确认决策：从零搭建、Java 17 + Spring Cloud Alibaba + Vue3、达梦 + OceanBase 双适配、同城双活、一次性全量交付 9 大模块、6 个月自研周期。

---

## 1. 需求来源

- **技术要求文档**：`docs/technical-requirements.md`（外部数据采集平台软件招采需求说明书）
- **需求文档**：`docs/requirements.md`（46 条功能需求 + 24 条非功能需求）
- **需求分析文件**：`tasks/requirement-analysis.md`

---

## 2. 第一性原理分析

### 2.1 用户真正要解决的问题

金融机构外部数据接入**混乱且不可治理**。具体表现为：

1. **没有统一入口**：各部门各自对接合作方，同一数据源被多次采购，资金浪费。
2. **接入无标准**：每种数据源都是"手工适配"，新数据上线周期以月计。
3. **使用无管控**：谁在用、用了多少、是否合规，无人知晓。
4. **质量无保障**：接入数据质量参差不齐，无自动校验，坏数据流入业务系统。
5. **价值未释放**：同一条数据 A 部门接入后 B 部门不知晓、无法复用。
6. **合规无审计**：监管检查时需要手工拼凑数据使用记录。

**本质问题**：外部数据从"进门"到"出门"缺乏一个**全链路的、可治理的管道系统**。

### 2.2 最小可行结果

> 注：用户已确认一次性全量交付 9 大模块。此处"最小可行"指每模块的最小可验收闭环，非只做部分模块。

对于**整个平台**，最小可行结果是：

```text
一个合作方注册并配置数据接口
    → 数据自动接入（协议适配 + 格式转换 + 落库）
    → 数据质量自动校验
    → 数据作为服务发布
    → 一个消费方申请并获批权限
    → 消费方调用服务获取数据
    → 调用行为被记录（审计日志 + 计费统计）
    → 全程在统一管理控制台可视化管理
```

这条**端到端链路**是平台的"主动脉"。所有其他功能（缓存、推荐、大屏、数据集市）都是此链路的增强。

### 2.3 核心输入

| 输入来源 | 具体内容 | 接口形态 |
|---|---|---|
| 外部合作方 | 政务/征信/工商司法/运营商/互联网/行业垂直数据 | API/文件/消息/DB 协议各异 |
| 平台管理员 | 合作方准入审批、服务配置、权限分配、规则配置 | Web 管理控制台 |
| 消费方（内部业务系统） | 数据服务调用请求、数据使用申请 | 服务 API |
| 质量系统 | 质量规则定义、校验调度、告警阈值 | 配置 |
| 计费系统 | 计费模型、费率、结算周期 | 配置 |
| 监管方 | 报表模板、报送规范 | 配置（待外部规范） |

### 2.4 核心输出

| 输出目标 | 具体内容 | 接口形态 |
|---|---|---|
| 消费方（内部业务系统） | 标准化数据响应（JSON/XML） | 服务 API |
| 平台管理员 | 合作方档案、接入状态、服务状态、消费方配额、质量报告 | Web 看板 |
| 审计/合规 | 全链路操作行为日志、数据访问记录、不可篡改审计日志 | 审计查询/导出 |
| 财务/采购 | 合作方结算账单、消费方费用账单 | 账单/导出（待外部接口） |
| 监管方 | 合规报表、个人信息使用报表、数据来源统计报表 | 报送（待外部规范） |
| 运维 | 全链路监控指标、告警通知、日志检索 | Web 看板 / 告警 |

### 2.5 不可省略的处理过程

从输入到输出，以下环节**不可省略**：

```text
1. 身份认证       —— 每个入口请求必须验明身份（谁在操作/调用）
2. 权限校验       —— 每个操作必须通过权限判定（有没有权限做这个操作）
3. 协议适配       —— 多协议外部数据 → 统一内部格式（不可省略协议差异）
4. 格式转换       —— 多格式 → 统一数据模型（不可省略格式差异）
5. 数据质量校验   —— 在存储前校验，脏数据不入库（金融合规底线）
6. 数据存储       —— 原始+加工数据持久化（不可省略落库）
7. 服务封装       —— 统一接口对外暴露，屏蔽底层差异（不可省略统一出口）
8. 调用日志       —— 每次数据调用全量记录（不可省略，审计与计费的基础）
9. 状态流转       —— 合作方/服务/消费方的生命周期状态机（不可省略，否则失去管控）
10. 监控告警      —— 全链路指标采集与异常通知（不可省略，否则不可运维）
```

### 2.6 核心能力与增强能力区分

| 类型 | 能力 | 是否本次必须实现 | 说明 |
|---|---|---|---|
| 核心 | 合作方全生命周期管理（注册/审核/准入/评级/退出） | 是 | 数据来源的入口治理 |
| 核心 | 多协议适配层（HTTP/WS/SFTP/Kafka/MQ/DB/API-GW） | 是 | 不可省略的协议差异桥接 |
| 核心 | 多格式转换层（JSON/XML/CSV/Excel） | 是 | 不可省略的格式差异桥接 |
| 核心 | 数据接入全流程管控（申请→映射→测试→审批→上线） | 是 | 接入标准化的流程载体 |
| 核心 | 数据同步调度（增量/全量/实时/定时/断点续传） | 是 | 数据流入的核心引擎 |
| 核心 | 服务全生命周期管理（注册→发布→版本→下线） | 是 | 统一出口的治理 |
| 核心 | 服务路由/负载均衡/限流/熔断/重试 | 是 | 高可用的基础 |
| 核心 | RBAC+ABAC 权限管控（功能/数据/操作三维，字段级） | 是 | 安全合规硬要求 |
| 核心 | 服务调用日志全量记录与追溯 | 是 | 审计与计费的基础数据 |
| 核心 | 消费方全生命周期+配额管理 | 是 | 使用侧的治理 |
| 核心 | 数据目录与元信息管理 | 是 | 数据可发现性的基础 |
| 核心 | 数据质量六维规则引擎+自动校验 | 是 | 脏数据不入库的底线 |
| 核心 | 全链路监控与告警 | 是 | 可运维的底线 |
| 核心 | 审计日志不可篡改（≥3年留存） | 是 | 合规硬要求 |
| 核心 | 传输加密(TLS1.2+)+存储加密(SM4)+脱敏 | 是 | 安全合规硬要求 |
| 增强 | 热点数据缓存（命中率≥90%） | 是 | 性能指标要求，但非不可省略 |
| 增强 | 分级存储（热/温/冷） | 是 | 降低成本，但数据先存下来更重要 |
| 增强 | 数据加工ETL（清洗/标准化/关联/标签） | 是 | 价值释放的核心手段 |
| 增强 | 外部数据集市 | 是 | 复用价值的载体 |
| 增强 | 数据生命周期（归档/销毁） | 是 | 合规留存要求 |
| 增强 | 计费模型+账单+费用统计 | 是 | 成本管控的抓手 |
| 增强 | 监管报表+报送 | 是 | 监管合规要求（报送对接待外部规范） |
| 增强 | 可视化大屏 | 是 | 非功能要求 |
| 增强 | 数据检索与智能推荐 | 是 | 本期规则推荐，AI 推荐后续 |
| 增强 | 数据质量评分评级 | 是 | 量化管理手段 |
| 增强 | 质量标准接口（开箱即用） | 是 | 80%可视化配置 |
| 增强 | 开放API+SDK+插件化扩展 | 是 | 扩展性要求 |
| 增强 | 消费方违规行为识别预警 | 是 | 安全管控增强 |
| 增强 | 质量闭环工单管理 | 是 | 治理闭环 |
| 增强 | MFA + IAM/SSO 单点登录 | 是 | 合规要求 |

> 结论：本次 46 条 FR 和 24 条 NFR 全部在范围。区分"核心/增强"的目的是指导**开发顺序**——先贯通核心链路，再叠加增强能力。

### 2.7 最小改动路径

当前仓库为空（无 src），是纯 greenfield 项目。"最小改动路径"变为"最小搭建路径"：

```text
Step 1: 项目脚手架
  ├── Maven 多模块父 POM
  ├── platform-common（共享库：数据模型、工具类、安全工具、审计注解）
  └── CI/CD 配置（Dockerfile、K8s 部署描述）

Step 2: 基础设施服务
  ├── platform-gateway（Spring Cloud Gateway）
  ├── platform-auth（认证 + 鉴权 + 用户/角色/权限管理）
  └── 数据库初始化脚本

Step 3: 核心数据管道（端到端最小闭环）
  ├── platform-partner（合作方 + 消费方管理）
  ├── platform-pipeline（接入 + 服务 + 目录 + 存储缓存）
  │   └── 先通一个协议(HTTP)+一个格式(JSON)的最小闭环
  └── platform-quality（质量规则 + 自动校验）

Step 4: 扩展协议与格式 + 增强治理
  ├── 补充 SFTP、Kafka、MQ、DB直连、WebService 协议适配器
  ├── 补充 XML、CSV、Excel 格式转换器
  └── platform-billing（计费 + 统计监管）

Step 5: 前端 + 集成 + 测试 + 性能调优
  ├── Vue3 管理控制台（全部模块页面）
  ├── 端到端集成测试
  ├── 性能压测与调优
  └── 安全渗透测试
```

### 2.8 避免过度设计的约束

| 约束 | 说明 |
|---|---|
| 不写自定义协议实现 | 已有成熟库：Apache HttpClient(HTTP)、Apache CXF(WS)、JSch(SFTP)、Spring Kafka、Spring AMQP、MyBatis-Plus(DB)。直接封装，不重新发明轮子。 |
| 不写自定义工作流引擎 | 接入审批、服务上线审批等流程用状态机模式（枚举+事件驱动），不引入 Flowable/Activiti。审批流是线性状态转移，不是通用BPM。 |
| 不写自定义调度框架 | 用 XXL-Job（国产、轻量），不要自研 Cron 调度。 |
| 不写自定义规则引擎 | 质量校验规则用"策略模式 + 数据库配置 + Drools Lite"或直接 Java Predicate 组合，六维各一策略类，不用完整 Drools。 |
| 不用 CQRS/Event Sourcing | 单一数据模型，不做读写分离的复杂事件溯源。需要审计的地方用审计表追加（不可篡改），不用区块链。 |
| 不做多租户 | 平台面向单一金融机构，不做 SaaS 多租户隔离。多机构是未来场景，当前不设计。 |
| 不做实时计算 | 统计指标用定时任务聚合计算，不做 Flink/Spark Streaming。日均10亿条接入 ≠ 需要实时流计算，批量处理+缓存足以满足≤5分钟延迟。 |
| 不做 AI/ML | 智能推荐用规则+标签匹配，不用机器学习模型。 |
| 国产化适配用 SPI 机制 | 达梦/OceanBase 双适配用 MyBatis-Plus 方言扩展 + 配置切换，不是两套代码。 |
| 前端避免自研组件库 | 用 Element Plus + ECharts + Vite，不自己写 UI 组件。 |

---

## 3. 功能拆解

### 3.1 模块与服务映射

| 服务 | 负责模块 | FR 编号 | 功能点数 |
|---|---|---|---|
| platform-gateway | API 网关（路由、限流、初步鉴权） | 支撑全部 | — |
| platform-auth | 用户/角色/权限管理、认证、SSO | 支撑 NFR-S01 | — |
| platform-partner | 2.1 合作方管理 + 2.5 消费方管理 | FR-101~104, FR-501~505 | 9 |
| platform-pipeline | 2.2 数据接入 + 2.3 数据服务 + 2.4 数据目录 + 2.6 缓存存储 | FR-201~205, FR-301~305, FR-401~405, FR-601~606 | 22 |
| platform-quality | 2.9 数据质量管理 | FR-901~906 | 6 |
| platform-billing | 2.7 计费管理 + 2.8 统计监管 | FR-701~705, FR-801~805 | 9 |
| platform-ops | 运维监控 + 日志管理（非功能） | NFR-M01, NFR-U01 | — |
| platform-ui | 前端管理控制台（Vue3） | 全部模块 UI | — |

### 3.2 功能处理链

```text
外部合作方                  平台管理员                内部消费方
    │                          │                        │
    ▼                          ▼                        ▲
┌──────────────┐    ┌──────────────────────┐    ┌──────────────┐
│ 合作方数据源  │    │   platform-ui (Vue3)  │    │ 业务系统调用  │
│ (API/File/   │    │   管理控制台           │    │ (API Client) │
│  MQ/DB)      │    └────────┬─────────────┘    └──────┬───────┘
└──────┬───────┘             │                         │
       │                     ▼                         │
       │           ┌──────────────────┐                │
       │           │ platform-gateway │◄───────────────┘
       │           │ (路由/限流/鉴权)  │                │
       │           └────────┬─────────┘                │
       │                    │                          │
       ▼                    ▼                          │
┌──────────────────────────────────────────────────────┼───┐
│              platform-pipeline (核心管道)             │   │
│                                                      │   │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────┴─┐ │
│  │ 协议适配 │→│ 格式转换 │→│ 质量校验 │→│ 存储层   │ │ │
│  │(HTTP/WS/ │ │(JSON/XML/│ │(六维规则)│ │(分库分表 │ │ │
│  │SFTP/Kafka│ │CSV/Excel)│ │          │ │+缓存+FS) │ │ │
│  │/MQ/DB)   │ │          │ │          │ │          │ │ │
│  └──────────┘  └──────────┘  └──────────┘  └────┬────┘ │
│                                                  │      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐       │      │
│  │ 服务发布 │←│ 服务注册 │←│ 数据目录 │←──────┘      │
│  │(路由/LB/ │ │(接口定义)│ │(元信息)  │              │
│  │ 限流/熔断)│ │          │ │          │              │
│  └────┬─────┘  └──────────┘  └──────────┘              │
│       │                                                 │
└───────┼─────────────────────────────────────────────────┘
        │ 对外提供标准化数据服务
        ▼
  内部业务系统消费方
```

### 3.3 功能点明细

| 编号 | 功能点 | 输入 | 处理 | 输出 | 优先级 | 所属服务 |
|---|---|---|---|---|---|---|
| FR-101 | 合作方注册与生命周期 | 注册信息/资质材料 | 状态机：注册→审核→准入→评级→退出 | 合作方档案 | P0 | platform-partner |
| FR-102 | 合作方分类分级标签 | 标签定义/赋值 | 多维标签化分类 | 分级结果 | P1 | platform-partner |
| FR-103 | 合作方接口与密钥配置 | 接口参数/密钥 | 配置存储+密钥加密 | 接入配置 | P0 | platform-partner |
| FR-104 | 合作方服务质量监控 | 监控指标 | 实时采集+阈值预警 | 监控看板/告警 | P1 | platform-partner |
| FR-201 | 多类型数据接入 | 合作方数据源 | 类型识别+路由 | 接入任务 | P0 | platform-pipeline |
| FR-202 | 多协议多格式适配 | 协议+格式数据 | 适配器+转换器链 | 标准化数据 | P0 | platform-pipeline |
| FR-203 | 标准+定制接入模式 | 接入配置 | 标准模板/可视化定制 | 接入配置生效 | P0 | platform-pipeline |
| FR-204 | 接入全流程管控 | 接入需求 | 状态机：申请→映射→测试→审批→上线→版本→下线 | 流程记录+上线任务 | P0 | platform-pipeline |
| FR-205 | 多种同步模式 | 同步策略 | 增量/全量/实时/定时调度执行 | 落库数据 | P0 | platform-pipeline |
| FR-301 | 对外数据服务 | 业务请求 | 标准/定制服务处理 | 数据响应 | P0 | platform-pipeline |
| FR-302 | 服务全生命周期 | 服务定义 | 状态机：注册→定义→测试→发布→迭代→下线 | 服务配置生效 | P0 | platform-pipeline |
| FR-303 | 服务高可用(路由/LB/限流/熔断/重试) | 服务调用 | 路由选择+负载+限流判定+熔断判断+重试 | 路由结果/限流拒绝/熔断响应 | P0 | platform-pipeline |
| FR-304 | 服务权限管控 | 调用请求+鉴权 | 按系统/角色判定 | 允许/拒绝 | P0 | platform-pipeline |
| FR-305 | 服务调用日志 | 调用请求 | 全量异步记录 | 日志记录 | P0 | platform-pipeline |
| FR-401 | 统一数据目录 | 数据资产 | 多维分类+可视化 | 目录页面 | P1 | platform-pipeline |
| FR-402 | 元信息管理 | 元数据定义 | CRUD | 元信息展示 | P1 | platform-pipeline |
| FR-403 | 数据预览 | 预览请求 | 授权校验+抽样+统计 | 样本/统计/质量报告 | P2 | platform-pipeline |
| FR-404 | 数据检索与推荐 | 检索条件 | 关键词搜索+标签规则推荐 | 检索结果 | P2 | platform-pipeline |
| FR-405 | 数据申请流程 | 使用申请 | 在线申请→审批→开通权限 | 权限开通 | P1 | platform-pipeline |
| FR-501 | 消费方生命周期 | 注册信息 | 状态机：注册→审核→配额→开通→注销 | 消费方档案 | P0 | platform-partner |
| FR-502 | 消费方分类分级 | 分类标签 | 按条线/系统/合规分级 | 分级结果 | P1 | platform-partner |
| FR-503 | 消费方配额管理 | 配额配置 | 频次/数据量/范围限制+预警+超额拦截 | 配额生效+告警 | P0 | platform-partner |
| FR-504 | 消费方行为审计 | 行为数据 | 全量记录+违规识别 | 审计记录+告警 | P1 | platform-partner |
| FR-505 | 消费方质量反馈 | 反馈内容 | 收集+汇总 | 反馈报告 | P3 | platform-partner |
| FR-601 | 多维数据缓存 | 缓存策略 | 接口结果/热点/全量缓存 | 缓存数据 | P2 | platform-pipeline |
| FR-602 | 分级存储管理 | 存储策略 | 热/温/冷分层路由 | 分层存储 | P2 | platform-pipeline |
| FR-603 | 数据脱敏与加密存储 | 敏感数据 | SM4加密+脱敏算法(掩码/替换/哈希) | 加密/脱敏数据 | P0 | platform-pipeline |
| FR-604 | 数据加工与复用(ETL) | 原始数据 | 清洗+标准化+关联+标签 | 标准化资产 | P1 | platform-pipeline |
| FR-605 | 外部数据集市 | 加工后数据 | 集中存储+共享管理 | 集市数据 | P2 | platform-pipeline |
| FR-606 | 数据生命周期管理 | 生命周期配置 | 留存→归档→销毁自动化 | 处置记录 | P2 | platform-pipeline |
| FR-701 | 多维度计费模型 | 调用/数据量统计 | 按次/量/接口/套餐/时长计算 | 费用 | P1 | platform-billing |
| FR-702 | 计费规则配置 | 规则+价格 | 差异化规则匹配 | 计价结果 | P1 | platform-billing |
| FR-703 | 账单自动生成与核对 | 费用数据 | 日/月/季度生成+核对+异议处理 | 结算/费用账单 | P2 | platform-billing |
| FR-704 | 费用统计与分析 | 费用明细 | 多维统计(趋势/占比) | 费用报表 | P2 | platform-billing |
| FR-705 | 财务/采购系统对接 | 账单数据 | 同步/联动 | 对接结果 | P3 | platform-billing |
| FR-801 | 全链路数据统计 | 指标数据 | 实时采集+聚合+趋势 | 指标看板 | P1 | platform-billing |
| FR-802 | 监管报表自动生成 | 报表配置 | 模板填充+生成+导出 | 合规/来源/个人信息报表 | P2 | platform-billing |
| FR-803 | 监管数据报送 | 监管数据 | 整理+校验+加密+发送 | 报送回执 | P3 | platform-billing |
| FR-804 | 合规审计与追溯 | 审计查询 | 全生命周期操作关联追溯 | 审计记录 | P1 | platform-billing |
| FR-805 | 可视化大屏 | 核心指标 | 图表渲染+实时刷新 | 大屏展示 | P3 | platform-billing |
| FR-901 | 全链路质量监控 | 质量指标 | 接入/传输/加工/存储/服务全环节监测 | 监控指标+告警 | P0 | platform-quality |
| FR-902 | 六维质量规则配置 | 规则定义 | 完整性/准确性/一致性/及时性/有效性/唯一性规则+可视化配置 | 规则生效 | P0 | platform-quality |
| FR-903 | 数据质量自动校验 | 待校验数据 | 六维规则执行 | 校验结果(异常/缺失/错误/重复) | P0 | platform-quality |
| FR-904 | 质量问题闭环管理 | 问题记录 | 发现→预警→工单→整改→验证→复盘 | 闭环记录 | P1 | platform-quality |
| FR-905 | 质量报告生成 | 质量数据 | 按合作方/类型/时间聚合统计 | 质量报告 | P2 | platform-quality |
| FR-906 | 质量评分评级 | 质量指标 | 六维加权可配模型计算 | 评分/评级 | P2 | platform-quality |

> P0 = 核心链路必须，P1 = 关键治理，P2 = 增强能力，P3 = 依赖外部或锦上添花

---

## 4. 技术设计

### 4.1 架构总览

```text
┌─────────────────────────────────────────────────────────────┐
│                        客户端层                              │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │   platform-ui (Vue3)  │  │  外部系统 / 消费方 API Client  │ │
│  │   管理控制台 SPA       │  │  (SDK / HTTP / 证书)          │ │
│  └──────────┬───────────┘  └──────────────┬───────────────┘ │
└─────────────┼─────────────────────────────┼─────────────────┘
              │                             │
              ▼                             ▼
┌─────────────────────────────────────────────────────────────┐
│                     网关层                                   │
│  ┌─────────────────────────────────────────────────────────┐│
│  │            platform-gateway (Spring Cloud Gateway)       ││
│  │  路由转发 · 限流(Sentinel) · JWT解析 · IP白名单 · CORS   ││
│  └─────────────────────────┬───────────────────────────────┘│
└─────────────────────────────┼────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ platform-auth │  │  platform-*      │  │  Nacos 集群      │
│ (认证鉴权服务) │  │  (业务微服务)     │  │  (注册/配置中心)  │
│               │  │                  │  │                  │
│ · 登录/登出   │  │  partner         │  │  · 服务发现      │
│ · MFA        │  │  pipeline        │  │  · 配置管理      │
│ · RBAC+ABAC  │  │  quality         │  │  ·  Sentinel     │
│ · SSO 对接   │  │  billing         │  │    控制台        │
│ · Token管理  │  │  ops             │  │                  │
│ · API Key    │  │                  │  │                  │
└───────┬───────┘  └────────┬─────────┘  └──────────────────┘
        │                   │
        ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                     数据层                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────┐  │
│  │  达梦     │ │OceanBase │ │  Redis   │ │ Elasticsearch │  │
│  │ (事务型   │ │(高性能   │ │ (缓存/   │ │ (日志检索/    │  │
│  │  主库)    │ │  分析库)  │ │  Session)│ │  全文搜索)    │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │  Kafka   │ │ MinIO/FS │ │ XXL-Job │                    │
│  │ (异步消息)│ │(文件存储) │ │ (调度)   │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 受影响的模块（本次新建）

全部为新建，无历史模块。

| 服务/模块 | 包路径 | 端口 | 说明 |
|---|---|---|---|
| platform-common | `com.platform.common` | — | 共享库（模型/DTO/工具/注解） |
| platform-gateway | `com.platform.gateway` | 8080 | API 网关 |
| platform-auth | `com.platform.auth` | 8081 | 认证鉴权 |
| platform-partner | `com.platform.partner` | 8082 | 合作方+消费方管理 |
| platform-pipeline | `com.platform.pipeline` | 8083 | 接入/服务/目录/存储缓存 |
| platform-quality | `com.platform.quality` | 8084 | 数据质量管理 |
| platform-billing | `com.platform.billing` | 8085 | 计费+统计监管 |
| platform-ops | `com.platform.ops` | 8086 | 运维监控+日志 |
| platform-ui | `platform-ui` | 3000 (dev) | Vue3 前端 |

### 4.3 核心技术选型

| 类别 | 选型 | 版本 | 说明 |
|---|---|---|---|
| 语言 | Java | 17 LTS | 金融行业标准 |
| 框架 | Spring Boot + Spring Cloud Alibaba | 3.x + 2023.x | 微服务全家桶 |
| 注册/配置 | Nacos | 2.x | 服务发现+配置中心 |
| 网关 | Spring Cloud Gateway | 4.x | 响应式网关 |
| 限流熔断 | Sentinel | 1.8.x | 阿里开源，生产验证 |
| 认证 | Spring Security + OAuth2.0 | — | 安全框架 |
| ORM | MyBatis-Plus | 3.5.x | 国产数据库方言支持好 |
| 数据库 | 达梦(DM8) + OceanBase | — | 双适配，MyBatis-Plus 方言切换 |
| 缓存 | Redis (Sentinel 模式) | 7.x | 缓存 + Session 共享 |
| 消息队列 | Kafka | 3.x | 高吞吐异步解耦 |
| 搜索引擎 | Elasticsearch | 8.x | 日志检索 + 数据目录全文搜索 |
| 对象存储 | MinIO | — | 文件数据 + 归档 |
| 调度 | XXL-Job | 2.4.x | 轻量分布式任务调度 |
| 日志 | Logback + SLF4J | — | 结构化日志 |
| 监控 | Spring Boot Actuator + Micrometer | — | 指标暴露 |
| 前端 | Vue3 + Element Plus + ECharts | — | SPA 管理控制台 |
| 构建 | Maven + Vite | — | 多模块构建 |
| 容器化 | Docker + Docker Compose (dev) / K8s (prod) | — | 部署 |

### 4.4 数据结构（核心表设计）

#### 4.4.1 合作方管理（platform-partner）

```sql
-- 合作方主表
t_partner (
    id              BIGINT PRIMARY KEY,
    partner_code    VARCHAR(64) UNIQUE NOT NULL,     -- 合作方编码
    partner_name    VARCHAR(255) NOT NULL,            -- 合作方名称
    data_type       VARCHAR(64) NOT NULL,            -- 数据类型(政务/征信/工商司法/运营商/互联网/行业垂直)
    industry        VARCHAR(64),                      -- 行业属性
    compliance_level VARCHAR(16),                     -- 合规等级(HIGH/MEDIUM/LOW)
    status          VARCHAR(32) NOT NULL,            -- 状态(PENDING/ACTIVE/SUSPENDED/TERMINATED)
    contact_info    JSON,                             -- 联系信息
    agreement_doc   VARCHAR(512),                     -- 协议文件路径
    rating          DECIMAL(3,2),                     -- 评级(1.00-5.00)
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    deleted         TINYINT DEFAULT 0
);

-- 合作方接口配置
t_partner_interface (
    id              BIGINT PRIMARY KEY,
    partner_id      BIGINT NOT NULL,
    protocol        VARCHAR(32) NOT NULL,            -- HTTP/HTTPS/WEBSERVICE/SFTP/FTP/KAFKA/MQ/DB/API_GW
    endpoint        VARCHAR(1024) NOT NULL,
    auth_type       VARCHAR(32),                      -- OAUTH2/API_KEY/CERTIFICATE/NONE
    credential      TEXT,                              -- 加密存储的凭证
    rate_limit      INT,                               -- 调用频次限制(次/秒)
    status          VARCHAR(32) NOT NULL,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);

-- 合作方生命周期事件
t_partner_event (
    id              BIGINT PRIMARY KEY,
    partner_id      BIGINT NOT NULL,
    event_type      VARCHAR(32) NOT NULL,            -- REGISTER/SUBMIT/APPROVE/REJECT/ACTIVATE/SUSPEND/RATE/TERMINATE
    operator        VARCHAR(64) NOT NULL,
    comment         TEXT,
    snapshot        JSON,                              -- 事件快照
    created_at      DATETIME NOT NULL
);
```

#### 4.4.2 数据接入（platform-pipeline）

```sql
-- 接入任务主表
t_ingest_task (
    id              BIGINT PRIMARY KEY,
    task_code       VARCHAR(64) UNIQUE NOT NULL,      -- 任务编码
    partner_id      BIGINT NOT NULL,
    interface_id    BIGINT NOT NULL,
    task_name       VARCHAR(255) NOT NULL,
    data_type       VARCHAR(64) NOT NULL,
    sync_mode       VARCHAR(32) NOT NULL,             -- INCREMENTAL/FULL/REALTIME/SCHEDULED
    cron_expression VARCHAR(64),                       -- 定时拉取cron
    status          VARCHAR(32) NOT NULL,             -- DRAFT/TESTING/PENDING_APPROVAL/ONLINE/OFFLINE
    version         INT DEFAULT 1,
    field_mapping   JSON,                              -- 字段映射配置
    transform_rules JSON,                              -- 转换规则
    quality_rules   JSON,                              -- 关联质量规则ID列表
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);

-- 接入执行记录
t_ingest_record (
    id              BIGINT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    batch_no        VARCHAR(64) NOT NULL,
    total_count     BIGINT,                            -- 总条数
    success_count   BIGINT,                            -- 成功条数
    fail_count      BIGINT,                            -- 失败条数
    duration_ms     BIGINT,                            -- 耗时
    status          VARCHAR(32) NOT NULL,             -- RUNNING/SUCCESS/FAILED/CANCELLED
    error_msg       TEXT,
    started_at      DATETIME,
    finished_at     DATETIME,
    created_at      DATETIME NOT NULL
);

-- 原始数据暂存
t_raw_data (
    id              BIGINT PRIMARY KEY,
    task_id         BIGINT NOT NULL,
    record_id       BIGINT,
    batch_no        VARCHAR(64),
    raw_content     LONGTEXT,                          -- 原始数据(JSON/XML/CSV文本)
    data_hash       VARCHAR(64),                       -- SHA256去重校验
    created_at      DATETIME NOT NULL
);
```

#### 4.4.3 数据服务（platform-pipeline）

```sql
-- 服务注册表
t_data_service (
    id              BIGINT PRIMARY KEY,
    service_code    VARCHAR(64) UNIQUE NOT NULL,
    service_name    VARCHAR(255) NOT NULL,
    service_type    VARCHAR(32) NOT NULL,             -- STANDARD/CUSTOM
    data_source_id  BIGINT,                            -- 关联数据源
    endpoint_path   VARCHAR(256) NOT NULL,
    method          VARCHAR(16) DEFAULT 'POST',
    request_schema  JSON,                              -- 请求参数定义
    response_schema JSON,                              -- 响应格式定义
    rate_limit      INT DEFAULT 1000,                  -- 限流TPS
    timeout_ms      INT DEFAULT 5000,                  -- 超时
    retry_count     INT DEFAULT 0,                     -- 重试次数
    circuit_breaker BOOLEAN DEFAULT TRUE,              -- 是否启用熔断
    status          VARCHAR(32) NOT NULL,             -- DRAFT/TESTING/ONLINE/DEPRECATED/OFFLINE
    version         INT DEFAULT 1,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);

-- 服务调用日志（核心审计表）
t_service_invoke_log (
    id              BIGINT PRIMARY KEY,
    service_id      BIGINT NOT NULL,
    consumer_id     BIGINT NOT NULL,
    request_params  TEXT,                               -- 脱敏后的请求参数
    response_code   VARCHAR(16),
    response_size   BIGINT,                             -- 响应数据大小(字节)
    duration_ms     BIGINT,
    source_ip       VARCHAR(64),
    auth_token_id   VARCHAR(128),
    status          VARCHAR(16) NOT NULL,              -- SUCCESS/FAILED/TIMEOUT/REJECTED
    error_msg       VARCHAR(1024),
    created_at      DATETIME NOT NULL,
    INDEX idx_created_at (created_at),
    INDEX idx_service_consumer (service_id, consumer_id, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at)) (...);      -- 按日分区
```

#### 4.4.4 数据质量（platform-quality）

```sql
-- 质量规则定义
t_quality_rule (
    id              BIGINT PRIMARY KEY,
    rule_code       VARCHAR(64) UNIQUE NOT NULL,
    rule_name       VARCHAR(255) NOT NULL,
    dimension       VARCHAR(32) NOT NULL,              -- COMPLETENESS/ACCURACY/CONSISTENCY/TIMELINESS/VALIDITY/UNIQUENESS
    rule_type       VARCHAR(32) NOT NULL,              -- SYSTEM_BUILTIN/CUSTOM
    target_object   VARCHAR(256) NOT NULL,             -- 校验对象(表/字段/接口)
    rule_expression TEXT,                               -- 规则表达式(JSON配置)
    severity        VARCHAR(16) DEFAULT 'WARN',        -- ERROR/WARN/INFO
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);

-- 质量校验结果
t_quality_check_result (
    id              BIGINT PRIMARY KEY,
    rule_id         BIGINT NOT NULL,
    batch_no        VARCHAR(64),
    total_count     BIGINT,
    pass_count      BIGINT,
    fail_count      BIGINT,
    fail_rate       DECIMAL(5,4),
    checked_at      DATETIME NOT NULL,
    INDEX idx_checked_at (checked_at)
);

-- 质量问题工单
t_quality_issue (
    id              BIGINT PRIMARY KEY,
    check_result_id BIGINT,
    rule_id         BIGINT NOT NULL,
    issue_type      VARCHAR(32) NOT NULL,             -- ANOMALY/MISSING/ERROR/DUPLICATE
    severity        VARCHAR(16) NOT NULL,
    description     TEXT,
    status          VARCHAR(32) NOT NULL,             -- OPEN/ASSIGNED/FIXING/VERIFYING/CLOSED
    assignee        VARCHAR(64),
    resolution      TEXT,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);
```

#### 4.4.5 计费统计（platform-billing）

```sql
-- 计费规则配置
t_billing_rule (
    id              BIGINT PRIMARY KEY,
    rule_code       VARCHAR(64) UNIQUE NOT NULL,
    rule_name       VARCHAR(255) NOT NULL,
    billing_model   VARCHAR(32) NOT NULL,             -- BY_COUNT/BY_VOLUME/BY_INTERFACE/BY_PACKAGE/BY_DURATION
    target_type     VARCHAR(32) NOT NULL,             -- PARTNER/CONSUMER/SERVICE
    target_id       BIGINT NOT NULL,
    unit_price      DECIMAL(12,6),
    currency        VARCHAR(8) DEFAULT 'CNY',
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    created_at      DATETIME NOT NULL
);

-- 账单主表
t_bill (
    id              BIGINT PRIMARY KEY,
    bill_no         VARCHAR(64) UNIQUE NOT NULL,
    bill_type       VARCHAR(32) NOT NULL,             -- SETTLEMENT(合作方结算)/EXPENSE(消费方费用)
    bill_period     VARCHAR(16) NOT NULL,             -- DAILY/MONTHLY/QUARTERLY
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    total_amount    DECIMAL(16,4),
    status          VARCHAR(32) NOT NULL,             -- GENERATED/CONFIRMED/DISPUTED/ADJUSTED/SETTLED
    created_at      DATETIME NOT NULL
);

-- 统计指标聚合表
t_stats_snapshot (
    id              BIGINT PRIMARY KEY,
    metric_name     VARCHAR(64) NOT NULL,             -- INGEST_COUNT/INVOKE_COUNT/TRANSFER_BYTES/CACHE_HIT_RATE/SUCCESS_RATE
    dimension       VARCHAR(32) NOT NULL,             -- PARTNER/CONSUMER/SERVICE
    dimension_id    BIGINT,
    metric_value    DECIMAL(20,4),
    snapshot_at     DATETIME NOT NULL,
    INDEX idx_snapshot (metric_name, snapshot_at)
);
```

#### 4.4.6 审计日志（跨服务通用）

```sql
-- 审计日志主表（不可篡改，追加写）
t_audit_log (
    id              BIGINT PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL,              -- 全链路追踪ID
    event_type      VARCHAR(64) NOT NULL,             -- PARTNER_REGISTER/DATA_INGEST/SERVICE_INVOKE/PERMISSION_CHANGE/...
    actor_type      VARCHAR(32) NOT NULL,             -- USER/SYSTEM/PARTNER/CONSUMER
    actor_id        VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64),                       -- 操作对象类型
    target_id       VARCHAR(64),                       -- 操作对象ID
    action          VARCHAR(128) NOT NULL,             -- 具体操作
    detail          JSON,                              -- 操作详情
    source_ip       VARCHAR(64),
    user_agent      VARCHAR(512),
    status          VARCHAR(16) NOT NULL,             -- SUCCESS/FAILED
    created_at      DATETIME NOT NULL,
    INDEX idx_trace (trace_id),
    INDEX idx_event_type_created (event_type, created_at),
    INDEX idx_actor (actor_type, actor_id, created_at)
) PARTITION BY RANGE (TO_DAYS(created_at)) (...);
```

### 4.5 接口设计

#### 4.5.1 对外服务接口（消费方调用）

| 方法 | 路径 | 说明 | 认证方式 |
|---|---|---|---|
| POST | `/api/v1/data/{serviceCode}/invoke` | 调用数据服务 | API Key + 签名 |
| GET | `/api/v1/data/{serviceCode}/schema` | 获取服务 Schema | API Key |
| POST | `/api/v1/data/{serviceCode}/batch` | 批量调用 | API Key + 签名 |

#### 4.5.2 管理控制台接口（Web 前端调用）

**认证鉴权**
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/login` | 账号密码登录 |
| POST | `/api/v1/auth/login/mfa` | MFA 验证 |
| POST | `/api/v1/auth/refresh` | 刷新 Token |
| POST | `/api/v1/auth/logout` | 登出 |
| GET | `/api/v1/auth/sso/redirect` | SSO 跳转 |
| GET | `/api/v1/auth/sso/callback` | SSO 回调 |
| GET | `/api/v1/auth/users` | 用户列表 |
| POST | `/api/v1/auth/users` | 创建用户 |
| PUT | `/api/v1/auth/users/{id}` | 修改用户 |
| GET | `/api/v1/auth/roles` | 角色列表 |
| POST | `/api/v1/auth/roles` | 创建角色 |
| PUT | `/api/v1/auth/roles/{id}/permissions` | 配置角色权限 |
| POST | `/api/v1/auth/apikeys` | 创建 API Key |

**合作方管理**
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/partners` | 注册合作方 |
| GET | `/api/v1/partners` | 合作方列表(分页/筛选) |
| GET | `/api/v1/partners/{id}` | 合作方详情 |
| PUT | `/api/v1/partners/{id}` | 更新合作方信息 |
| POST | `/api/v1/partners/{id}/submit` | 提交审核 |
| POST | `/api/v1/partners/{id}/approve` | 准入审批 |
| POST | `/api/v1/partners/{id}/reject` | 驳回 |
| PUT | `/api/v1/partners/{id}/rating` | 评级 |
| POST | `/api/v1/partners/{id}/terminate` | 退出注销 |
| POST | `/api/v1/partners/{id}/interfaces` | 配置接口 |
| PUT | `/api/v1/partners/{id}/interfaces/{iid}` | 更新接口配置 |
| POST | `/api/v1/partners/{id}/interfaces/{iid}/keys` | 管理密钥 |

**数据接入**
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/ingest/tasks` | 创建接入任务 |
| GET | `/api/v1/ingest/tasks` | 任务列表 |
| GET | `/api/v1/ingest/tasks/{id}` | 任务详情 |
| PUT | `/api/v1/ingest/tasks/{id}/mapping` | 字段映射 |
| PUT | `/api/v1/ingest/tasks/{id}/rules` | 转换规则 |
| POST | `/api/v1/ingest/tasks/{id}/test` | 测试执行 |
| POST | `/api/v1/ingest/tasks/{id}/submit` | 提交上线审批 |
| POST | `/api/v1/ingest/tasks/{id}/approve` | 审批通过 |
| POST | `/api/v1/ingest/tasks/{id}/offline` | 下线 |
| GET | `/api/v1/ingest/records` | 执行记录 |

**数据服务**
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/services` | 注册服务 |
| GET | `/api/v1/services` | 服务列表 |
| GET | `/api/v1/services/{id}` | 服务详情 |
| PUT | `/api/v1/services/{id}` | 更新服务 |
| POST | `/api/v1/services/{id}/test` | 测试服务 |
| POST | `/api/v1/services/{id}/publish` | 发布上线 |
| POST | `/api/v1/services/{id}/offline` | 下线归档 |
| GET | `/api/v1/services/{id}/logs` | 调用日志 |

**数据目录**
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/catalog` | 目录浏览(多维分类) |
| GET | `/api/v1/catalog/{id}/meta` | 元信息详情 |
| GET | `/api/v1/catalog/{id}/preview` | 数据预览 |
| GET | `/api/v1/catalog/search` | 检索数据 |
| POST | `/api/v1/catalog/{id}/apply` | 申请使用数据 |
| POST | `/api/v1/catalog/applications/{id}/approve` | 审批申请 |

**消费方管理**
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/consumers` | 注册消费方 |
| GET | `/api/v1/consumers` | 消费方列表 |
| GET | `/api/v1/consumers/{id}` | 消费方详情 |
| PUT | `/api/v1/consumers/{id}/quota` | 配置配额 |
| GET | `/api/v1/consumers/{id}/audit` | 行为审计 |
| GET | `/api/v1/consumers/{id}/logs` | 调用日志 |

**数据质量**
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/quality/rules` | 创建质量规则 |
| GET | `/api/v1/quality/rules` | 规则列表 |
| PUT | `/api/v1/quality/rules/{id}` | 更新规则 |
| GET | `/api/v1/quality/checks` | 校验结果 |
| POST | `/api/v1/quality/checks` | 手动触发校验 |
| GET | `/api/v1/quality/issues` | 问题列表 |
| POST | `/api/v1/quality/issues/{id}/assign` | 指派 |
| POST | `/api/v1/quality/issues/{id}/resolve` | 解决 |
| GET | `/api/v1/quality/reports` | 质量报告 |
| GET | `/api/v1/quality/scores` | 评分评级 |

**计费统计**
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/billing/rules` | 计费规则列表 |
| POST | `/api/v1/billing/rules` | 配置计费规则 |
| GET | `/api/v1/billing/bills` | 账单列表 |
| POST | `/api/v1/billing/bills/generate` | 生成账单 |
| POST | `/api/v1/billing/bills/{id}/confirm` | 确认账单 |
| POST | `/api/v1/billing/bills/{id}/dispute` | 异议处理 |
| GET | `/api/v1/billing/stats` | 费用统计 |
| GET | `/api/v1/stats/dashboard` | 全链路统计面板 |
| GET | `/api/v1/stats/reports` | 监管报表 |

#### 4.5.3 内部服务间接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/auth/validate` | Token 校验 |
| GET | `/internal/auth/permissions/{userId}` | 获取用户权限 |
| POST | `/internal/pipeline/ingest/trigger` | 触发接入任务 |
| POST | `/internal/quality/check` | 触发质量校验 |
| POST | `/internal/billing/record` | 记录调用(计费用) |

### 4.6 外部依赖

| 依赖 | 用途 | 是否必须 | 备注 |
|---|---|---|---|
| Nacos | 服务注册发现 + 配置中心 | 是 | 微服务基础设施 |
| Sentinel | 限流熔断 | 是 | 网关+服务侧都需 |
| Kafka | 异步消息(接入完成通知/日志采集/质量触发) | 是 | 解耦关键链路 |
| Redis | 缓存 + Session + 分布式锁 | 是 | 性能硬要求 |
| Elasticsearch | 日志检索 + 全文搜索 | 是 | 调用日志检索需要 |
| MinIO | 文件存储 | 是 | SFTP文件/归档 |
| XXL-Job | 定时调度 | 是 | 定时接入/统计/账单 |
| 达梦 DM8 | 事务数据库 | 是 | 国产化硬要求 |
| OceanBase | 高性能数据库 | 是 | 国产化硬要求 |
| 外部 IAM/SSO | 统一身份认证 | 否 | 对接待明确后实施 |
| 监管报送系统 | 监管数据报送 | 否 | 对接待明确后实施 |
| 财务/采购系统 | 账单同步 | 否 | 对接待明确后实施 |

---

## 5. 异常场景

| 编号 | 场景 | 触发条件 | 预期处理 |
|---|---|---|---|
| E-01 | 合作方接口不可达 | 接口健康检查失败 | 记录不可用状态，触发告警，暂停接入任务，恢复后自动重试 |
| E-02 | 数据格式解析失败 | 返回数据与约定格式不匹配 | 记录失败批次与错误明细，不落脏数据，告警通知管理员 |
| E-03 | 接入数据量激增 | 单批次远超预期(>10x) | 限流（合作方侧）+ 分片处理，防止 OOM |
| E-04 | 数据质量校验大批不通过 | fail_rate > 阈值(如50%) | 暂停接入，告警，记录问题工单，人工确认后决定继续或回滚 |
| E-05 | 服务调用超配额 | 消费方配额耗尽 | 返回 429 Too Many Requests，不扣费，记录超额事件 |
| E-06 | 服务调用鉴权失败 | Token 过期/API Key 无效/签名错误 | 返回 401/403，记录审计日志，连续失败触发告警 |
| E-07 | 数据库主库故障 | 达梦不可用 | 自动切换备库，业务无感（Sentinel 检测） |
| E-08 | OceanBase 集群故障 | OB 节点宕机 | 自动切换，无数据丢失 |
| E-09 | Redis 集群故障 | Redis 不可达 | 降级：Session 回退到 JWT 无状态验证；缓存穿透到数据库 |
| E-10 | Kafka 消息积压 | 消费速度低于生产速度 | 扩容消费者实例，监控积压量告警 |
| E-11 | 断点续传恢复 | 传输中断后恢复 | 从上次完成的 offset 继续，不重复、不丢失 |
| E-12 | 同城双活切换 | 主机房宕机 | 备机房接管，RPO≤5min/RTO≤30min |
| E-13 | 加密密钥泄露 | 密钥管理检测到异常访问 | 触发密钥轮换，记录全量审计日志，通知安全团队 |
| E-14 | 并发超过 2000TPS | 瞬时流量洪峰 | Sentinel 限流拒绝超额请求，返回 429；触发自动扩容 |

---

## 6. 测试策略

| 测试类型 | 测试内容 | 验收标准 | 工具 |
|---|---|---|---|
| 单元测试 | Service 层、工具类、状态机、规则引擎 | 覆盖率≥80%，核心路径 100% | JUnit5 + Mockito |
| 集成测试 | 数据库 DAO、消息队列收发、缓存读写、调度任务执行 | 所有数据访问层通过 | Spring Boot Test + Testcontainers |
| 协议适配测试 | HTTP/WS/SFTP/Kafka/MQ/DB 每种协议的接入→落库 | 每种协议至少 1 个用例通过 | Mock Server + 真实协议端点 |
| 格式转换测试 | JSON/XML/CSV/Excel 每种格式的解析与校验 | 每种格式正常+异常用例通过 | 样本文件 |
| API 测试 | 全部 REST 接口（正常+异常+权限） | 接口覆盖率 100% | Spring MockMvc / REST Assured |
| 安全测试 | SQL注入/XSS/CSRF/越权/未授权/签名绕过/重放 | 无高危漏洞 | OWASP ZAP + 手工渗透 |
| 性能测试 | 接口响应、并发 TPS、批量传输、数据接入/加工吞吐、缓存命中率、查询响应 | 全部性能指标达标（NFR-P01~P07） | JMeter + 监控 |
| 稳定性测试 | 48h 连续运行 | 无宕机、无内存泄漏、无性能下降 | 长时间压测 |
| 故障注入 | 网络分区、节点宕机、数据库主从切换、Redis 故障、MQ 故障 | 故障恢复达标（NFR-A01~A05） | Chaos Mesh |
| 兼容性测试 | 达梦+OceanBase SQL 方言、国产 OS、X86/ARM、主流浏览器 | 全环境可运行 | 多环境部署 |
| 端到端测试 | 合作方注册→接入→校验→服务→消费→审计→计费 全链路 | 全链路可走通 | 自动化脚本 |

---

## 7. Codex 实现边界

### 7.1 Codex 可以修改

- 所有 `src/main/java` 下的业务代码（7 个微服务 + 1 个 common 库）
- 所有 `src/main/resources` 下的配置文件（数据库连接、MQ 配置、缓存配置等）
- 所有 `src/test` 下的测试代码
- `platform-ui/src` 下的前端代码
- `pom.xml`（Maven 多模块父 POM 与子模块 POM）
- `docker-compose.yml` / `Dockerfile`
- SQL 初始化脚本（`db/migration/`）
- 项目脚手架目录结构

### 7.2 Codex 不允许修改

- `.env` / `.env.*`（环境变量文件，含密钥、密码）
- 任何 `*.pem` / `*.key` / `*.crt`（证书文件）
- `CLAUDE.md` / `AGENTS.md`（协作规范文件）
- `docs/` 目录下的所有文档（需求/计划文档由 Claude Code 维护）
- `tasks/` 目录下的所有任务文件
- K8s 生产部署配置（`k8s/prod/`，仅能修改 `k8s/dev/` 开发环境）
- 生产数据库连接配置（仅能用占位符 `${}`）

### 7.3 Codex 必须补充的测试

- 每个 Service 类至少 1 个单元测试（核心方法 100% 覆盖）
- 每个 DAO/Repository 至少 1 个集成测试
- 每个 REST Controller 至少 1 个 API 测试（正常+异常）
- 每个协议适配器至少 1 个功能验证测试
- 每个格式转换器至少 1 个解析测试
- 状态机流转测试（所有合法+非法状态转移）
- 权限控制测试（未授权/越权/过期 Token）

---

## 8. 验收标准

### 8.1 功能验收（对照 docs/requirements.md）

| 验收项 | 标准 |
|---|---|
| 功能覆盖率 | 46 条 FR 全部实现，无核心功能缺失 |
| 用例覆盖率 | 测试用例覆盖率 100%（每条 FR 至少 1 个用例） |
| 核心通过率 | P0 功能测试通过率 100% |
| 缺陷等级 | 无严重(Blocker)与高危(Critical)缺陷 |

### 8.2 性能验收

| 指标 | 标准 | 测试方法 |
|---|---|---|
| 标准接口响应 | P50≤200ms, P95≤500ms, P99≤1s | JMeter 压测 |
| 定制接口响应 | P50≤500ms, P95≤1s | JMeter 压测 |
| 并发 TPS | ≥1000TPS(常规), ≥2000TPS(峰值) | 逐步加压 |
| 数据传输 | 100万条/批, ≥100MB/s, 断点续传 | 批量传输测试 |
| 数据处理 | 1万条/秒(接入), 5亿/天(加工) | 持续灌入 |
| 缓存 | 命中率≥90%, 查询≤10ms | 缓存压测 |
| 查询 | 千万级≤2s, 亿级≤5s | 大数据量查询 |

### 8.3 48 小时稳定性验收

- 系统无宕机（无 JVM 进程退出）
- 无内存泄漏（Heap 稳定，GC 正常）
- 无性能明显下降（TPS/响应时间波动 <10%）
- 核心指标全程稳定达标

### 8.4 安全验收

- 认证鉴权：MFA、SSO、RBAC+ABAC 字段级均生效
- 加密：TLS1.2+、SM4 验证通过
- 脱敏：动态+静态脱敏规则生效
- 审计：日志全量、不可篡改（Append-Only 表）
- 渗透测试：无高危漏洞（OWASP Top 10 覆盖）
- 等保：满足等保2.0三级技术要求

### 8.5 文档验收

- 系统架构文档 + 部署手册 + 运维手册 + 开发手册 + 用户操作手册
- 文档与实际系统功能一致，可操作

---

## 9. 风险与回滚

### 9.1 关键风险

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---|---|---|
| 达梦/OceanBase SQL 方言不兼容 MyBatis-Plus | 中 | 高 | 初期用 MySQL 开发，MyBatis-Plus 方言抽象层隔离，集成测试阶段切换验证 |
| 6 个月内全量交付 9 大模块工期不足 | 中 | 高 | 严格按 MVP 主线优先 → 每两周一个可演示增量 → 保留最后 1 个月补漏 |
| 多协议适配复杂度超预期 | 中 | 中 | 第一周先通 HTTP+JSON，验证架构后逐协议补充；其他协议复用适配器接口 |
| 日均 10 亿条数据的实际规模无法在开发环境验证 | 高 | 中 | 用数据生成器模拟大批量，生产环境预留性能调优窗口 |
| 外部依赖接口规范不明确（财务/监管） | 高 | 低 | 保留适配器接口与 Mock，不影响核心链路 |
| 国产中间件（麒麟/UOS）兼容性问题 | 低 | 中 | 用 Docker 容器化，降低 OS 层耦合；测试阶段独立验证 |
| 团队不熟悉达梦/OceanBase | 中 | 中 | 从 MyBatis-Plus + 标准 SQL 起步，方言差异最小化 |

### 9.2 回滚策略

- **代码回滚**：Git 分支保护，每个迭代打 Tag，问题时可回退到上一个稳定 Tag。
- **数据库回滚**：所有 SQL 迁移脚本必须有对应的回滚脚本，Flyway/Liquibase 管理。
- **配置回滚**：Nacos 配置版本管理，一键回滚到历史版本。
- **部署回滚**：K8s 滚动升级 + 灰度发布，异常时自动回滚，回滚时间 ≤10 分钟。
- **服务回滚**：灰度发布先切 10% 流量验证，异常自动回滚（Sentinel 熔断触发）。

---

## 10. 开发里程碑（6 个月）

| 月份 | 里程碑 | 交付物 | 关键验收 |
|---|---|---|---|
| M1 | 基础设施搭建 + 核心链路贯通 | 脚手架、Gateway、Auth、Partner(CRUD)、Ingest(HTTP+JSON单协议) | 合作方注册→HTTP接入→落库可走通 |
| M2 | 核心管道完整 | Ingest 全部协议、Service(发布+调用+日志)、Consumer管理、Catalog | 接入→服务→消费 全链路可走通 |
| M3 | 质量体系 + 加工存储 | Quality 全量(六维规则+校验+工单)、Storage(缓存+分级+ETL) | 质量规则生效、数据加工可用 |
| M4 | 治理 + 前端 | Billing 全量、Statistics 全量、Vue3 前端全部页面 | 计费可用、前端可操作全部模块 |
| M5 | 集成测试 + 性能调优 | 端到端自动化测试、性能压测、安全渗透、国产化兼容验证 | 全部指标达标 |
| M6 | 稳定性 + 文档 + 验收 | 48h 稳定性测试、故障演练、文档交付、验收 | 全部验收标准通过 |

---

## 附录：项目脚手架结构

```text
sjgx/
├── pom.xml                          # Maven 父 POM（依赖管理）
├── docker-compose.yml               # 本地开发环境
├── CLAUDE.md
├── AGENTS.md
├── docs/
│   ├── technical-requirements.md
│   └── requirements.md
├── tasks/
│   ├── requirement-analysis.md
│   ├── claude-plan.md
│   └── codex-task.md
├── reviews/
│   └── claude-review.md
├── db/
│   └── migration/                   # SQL 迁移脚本
│       ├── V001__init_schema.sql
│       ├── V002__partner.sql
│       └── ...
├── platform-common/
│   ├── pom.xml
│   └── src/main/java/com/platform/common/
│       ├── model/                   # 通用数据模型
│       ├── dto/                     # 通用 DTO
│       ├── enums/                   # 枚举
│       ├── exception/               # 异常定义
│       ├── util/                    # 工具类
│       ├── security/                # SM4/脱敏/签名工具
│       ├── audit/                   # 审计注解与切面
│       └── config/                  # 通用自动配置
├── platform-gateway/
│   ├── pom.xml
│   └── src/main/java/com/platform/gateway/
│       ├── GatewayApplication.java
│       ├── filter/                  # 全局过滤器
│       └── config/                  # 路由/限流/CORS 配置
├── platform-auth/
│   ├── pom.xml
│   └── src/main/java/com/platform/auth/
│       ├── AuthApplication.java
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       ├── security/                # Spring Security 配置
│       └── config/
├── platform-partner/
│   ├── pom.xml
│   └── src/main/java/com/platform/partner/
│       ├── PartnerApplication.java
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       └── state/                   # 状态机定义
├── platform-pipeline/
│   ├── pom.xml
│   └── src/main/java/com/platform/pipeline/
│       ├── PipelineApplication.java
│       ├── ingest/                  # 接入子模块
│       │   ├── controller/
│       │   ├── service/
│       │   ├── adapter/             # 协议适配器接口+实现
│       │   │   ├── ProtocolAdapter.java      # 接口
│       │   │   ├── HttpAdapter.java
│       │   │   ├── WebServiceAdapter.java
│       │   │   ├── SftpAdapter.java
│       │   │   ├── KafkaAdapter.java
│       │   │   ├── MqAdapter.java
│       │   │   ├── DbAdapter.java
│       │   │   └── ApiGatewayAdapter.java
│       │   ├── converter/           # 格式转换器
│       │   │   ├── FormatConverter.java      # 接口
│       │   │   ├── JsonConverter.java
│       │   │   ├── XmlConverter.java
│       │   │   ├── CsvConverter.java
│       │   │   └── ExcelConverter.java
│       │   └── sync/                # 同步调度
│       │       ├── SyncStrategy.java
│       │       ├── IncrementalSync.java
│       │       ├── FullSync.java
│       │       ├── RealtimeSync.java
│       │       └── ScheduledSync.java
│       ├── service/                 # 服务发布/调用子模块
│       │   ├── controller/
│       │   ├── service/
│       │   ├── routing/             # 路由/LB
│       │   └── resilience/          # 限流/熔断/重试
│       ├── catalog/                 # 数据目录子模块
│       │   ├── controller/
│       │   ├── service/
│       │   └── search/              # ES 搜索
│       └── storage/                 # 存储缓存子模块
│           ├── cache/               # 缓存管理
│           ├── tier/                # 分级存储
│           ├── etl/                 # 数据加工
│           │   ├── cleaner/         # 清洗
│           │   ├── standardizer/    # 标准化
│           │   ├── joiner/          # 关联整合
│           │   └── tagger/          # 标签加工
│           ├── marketplace/         # 数据集市
│           └── lifecycle/           # 生命周期
├── platform-quality/
│   ├── pom.xml
│   └── src/main/java/com/platform/quality/
│       ├── QualityApplication.java
│       ├── controller/
│       ├── service/
│       ├── rule/                    # 规则引擎
│       │   ├── QualityRule.java            # 规则接口
│       │   ├── CompletenessRule.java
│       │   ├── AccuracyRule.java
│       │   ├── ConsistencyRule.java
│       │   ├── TimelinessRule.java
│       │   ├── ValidityRule.java
│       │   └── UniquenessRule.java
│       ├── executor/                # 校验执行器
│       ├── issue/                   # 问题工单
│       ├── report/                  # 报告生成
│       └── scoring/                 # 评分模型
├── platform-billing/
│   ├── pom.xml
│   └── src/main/java/com/platform/billing/
│       ├── BillingApplication.java
│       ├── controller/
│       ├── service/
│       ├── model/                   # 计费模型
│       ├── bill/                    # 账单引擎
│       ├── stats/                   # 统计引擎
│       └── report/                  # 报表引擎
├── platform-ops/
│   ├── pom.xml
│   └── src/main/java/com/platform/ops/
│       ├── OpsApplication.java
│       ├── monitor/                 # 监控面板
│       └── log/                     # 日志管理
└── platform-ui/
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── views/                   # 页面
        ├── components/              # 公共组件
        ├── api/                     # API 调用
        ├── router/                  # 路由
        ├── store/                   # 状态管理(Pinia)
        └── utils/                   # 工具
```
