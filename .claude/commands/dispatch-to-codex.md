# dispatch-to-codex

你是项目主控智能体，负责将明确任务交给 Codex 执行。

## 目标

基于以下文件生成 Codex 执行任务：

```text
docs/requirements.md
tasks/requirement-analysis.md
tasks/claude-plan.md
```

输出文件：

```text
tasks/codex-task.md
```

如果 Codex 插件可用，可以将任务派发给 Codex。  
如果 Codex 插件不可用，输出可复制到 Codex 桌面端的执行提示词。

---

## 第一步：读取文件

必须读取：

```text
CLAUDE.md
AGENTS.md
docs/requirements.md
tasks/requirement-analysis.md
tasks/claude-plan.md
```

如果 `tasks/claude-plan.md` 不存在，先执行 `/first-principles-plan`。

---

## 第二步：生成 Codex 执行任务

生成或覆盖：

```text
tasks/codex-task.md
```

内容必须包含：

```markdown
# Codex 执行任务

## 1. 任务目标

## 2. 需求依据

## 3. 允许修改范围

## 4. 禁止修改范围

## 5. 实现要求

## 6. 测试要求

## 7. 完成后输出

## 8. 返工规则
```

---

## 第三步：任务边界规则

Codex 任务必须满足：

```text
1. 目标清晰。
2. 范围明确。
3. 可以通过代码实现。
4. 可以通过测试验证。
5. 不包含需求判断任务。
6. 不包含最终验收任务。
7. 不允许修改敏感文件。
8. 不允许做无关重构。
```

---

## 第四步：派发方式

如果 Codex 插件可用，使用 Codex 插件派发任务。

可尝试：

```text
/codex:status
/codex:review
```

如果插件不可用，输出以下提示词供用户复制到 Codex 桌面端：

```text
你是本项目的代码执行智能体。

请严格读取并遵守以下文件：

1. AGENTS.md
2. docs/requirements.md
3. tasks/requirement-analysis.md
4. tasks/claude-plan.md
5. tasks/codex-task.md

执行规则：

1. 只实现 tasks/codex-task.md 中列出的任务。
2. 不重新解释需求。
3. 不覆盖 Claude Code 的需求判断和开发计划。
4. 优先采用最小改动。
5. 不修改密钥、生产配置和无关模块。
6. 不进行无关重构。
7. 必须补充或更新测试。
8. 完成后运行测试。
9. 输出修改文件、测试命令、测试结果和潜在风险。
10. 不做最终验收，最终验收由 Claude Code 完成。
```

---

## 输出要求

完成后说明：

```text
1. 已生成 tasks/codex-task.md。
2. Codex 允许修改哪些范围。
3. Codex 禁止修改哪些范围。
4. 建议运行哪些测试命令。
5. 下一步如何审查 Codex 结果。
```
