# CLAUDE.md

## 1. 项目协作模式

本项目采用 **Claude Code + Codex** 协作开发模式。

```text
Claude Code = 主控智能体
负责：需求分析、需求确认、第一性原理开发计划、任务拆解、任务派发、代码审查。

Codex = 代码执行智能体
负责：代码实现、测试补充、局部修复、运行测试。
```

Claude Code 不直接大规模编写业务代码。  
Codex 不负责需求判断、架构决策和最终验收。

---

## 2. 核心文件

```text
docs/technical-requirements.md
原始技术要求文档。

docs/requirements.md
Claude Code 根据技术要求文档生成的正式开发需求。

tasks/requirement-analysis.md
需求分析说明。

tasks/claude-plan.md
Claude Code 基于第一性原理生成的开发计划。

tasks/codex-task.md
给 Codex 的具体实现任务。

reviews/claude-review.md
Claude Code 对 Codex 改动的审查结果。

AGENTS.md
Claude Code 与 Codex 的职责边界。
```

---

## 3. 标准工作流

每次开发任务必须按以下顺序执行：

```text
读取技术要求文档
  ↓
生成需求分析
  ↓
生成正式 requirements.md
  ↓
基于第一性原理生成开发计划
  ↓
生成 Codex 执行任务
  ↓
Codex 实现代码和测试
  ↓
Claude Code 审查 git diff
  ↓
通过、返工或提交
```

---

## 4. 需求确定阶段

Claude Code 必须先读取：

```text
docs/technical-requirements.md
```

然后生成：

```text
tasks/requirement-analysis.md
docs/requirements.md
```

需求确定阶段必须完成：

```text
1. 提取功能需求。
2. 提取非功能需求。
3. 提取数据要求。
4. 提取接口要求。
5. 提取权限、安全、日志要求。
6. 提取部署和运行环境要求。
7. 标记歧义、缺失和待确认内容。
8. 明确本次实现范围和不实现范围。
```

`docs/requirements.md` 中的每条需求必须满足：

```text
可开发
可测试
可验收
边界清晰
```

---

## 5. 第一性原理开发计划

Claude Code 生成 `tasks/claude-plan.md` 时，必须遵循第一性原理。

不要直接堆功能点，必须按以下逻辑推导：

```text
1. 用户真正要解决的问题是什么？
2. 最小可行结果是什么？
3. 系统必须接收哪些输入？
4. 系统必须产生哪些输出？
5. 从输入到输出之间不可省略的处理过程是什么？
6. 哪些是核心能力？
7. 哪些是增强能力？
8. 当前代码库中的最小改动路径是什么？
9. 如何测试？
10. 如何验收？
11. 如何避免过度设计？
```

`tasks/claude-plan.md` 必须包含：

```text
需求来源
第一性原理分析
功能拆解
影响模块
接口设计
数据结构
异常场景
测试策略
Codex 实现边界
验收标准
风险与回滚
```

---

## 6. Codex 执行规则

Claude Code 生成 `tasks/codex-task.md` 后，Codex 才能开始实现。

Codex 必须读取：

```text
AGENTS.md
docs/requirements.md
tasks/requirement-analysis.md
tasks/claude-plan.md
tasks/codex-task.md
```

Codex 必须遵守：

```text
1. 只实现 tasks/codex-task.md 中列出的任务。
2. 不重新解释需求。
3. 不覆盖 Claude Code 的需求判断。
4. 优先采用最小改动。
5. 不修改密钥、生产配置和无关模块。
6. 不进行无关重构。
7. 必须补充或更新测试。
8. 完成后运行测试。
9. 输出修改文件、测试命令、测试结果和潜在风险。
```

---

## 7. Claude Code 审查规则

Codex 完成后，Claude Code 必须读取：

```text
git status
git diff
测试输出
Codex 修改说明
```

然后生成：

```text
reviews/claude-review.md
```

审查必须覆盖：

```text
1. 是否满足 docs/requirements.md。
2. 是否符合 tasks/claude-plan.md。
3. 是否遵守最小可行结果。
4. 是否超出 tasks/codex-task.md 范围。
5. 是否存在过度设计。
6. 是否存在安全风险。
7. 是否补充测试。
8. 是否建议通过。
9. 是否需要返工。
10. 返工任务清单。
```

---

## 8. 常用命令

需求确定：

```text
/confirm-requirements
```

生成第一性原理开发计划：

```text
/first-principles-plan
```

派发给 Codex：

```text
/dispatch-to-codex
```

代码审查：

```text
/review-codex-output
```

---

## 9. 禁止事项

Claude Code 和 Codex 都不得执行以下操作：

```text
1. 不修改 .env、密钥、证书、生产配置。
2. 不删除大量文件。
3. 不擅自引入大型依赖。
4. 不修改与当前任务无关的模块。
5. 不跳过测试。
6. 不在审查未通过时提交主分支。
7. 不把敏感信息写入日志或文档。
8. 不直接连接生产数据库。
```

---

## 10. 推荐启动提示词

在 Claude Code 中输入：

```text
请严格按照 CLAUDE.md 和 AGENTS.md 执行本次任务。

先读取 docs/technical-requirements.md，
生成 tasks/requirement-analysis.md 和 docs/requirements.md。

然后基于第一性原理生成 tasks/claude-plan.md，
再生成 tasks/codex-task.md。

Codex 完成实现后，
请读取 git status、git diff 和测试结果，
生成 reviews/claude-review.md。

开发计划必须从问题本质、最小可行结果、输入、输出、不可省略处理过程、最小改动路径和测试验证方式逐层推导。
```
