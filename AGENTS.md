# AGENTS.md

## 1. 总体原则

本项目由 Claude Code 与 Codex 协作完成自动化开发。

```text
Claude Code 负责想清楚要做什么、为什么做、怎么验收。
Codex 负责按照明确任务实现代码、补充测试、运行测试。
```

Claude Code 是主控方。  
Codex 是执行方。  
最终验收由 Claude Code 完成。

---

## 2. Claude Code 职责

Claude Code 必须负责：

1. 读取 `CLAUDE.md`。
2. 读取 `docs/technical-requirements.md`。
3. 生成 `tasks/requirement-analysis.md`。
4. 生成 `docs/requirements.md`。
5. 基于第一性原理生成 `tasks/claude-plan.md`。
6. 生成 `tasks/codex-task.md`。
7. 指导或委派 Codex 实现。
8. Codex 完成后读取 `git status`、`git diff` 和测试结果。
9. 生成 `reviews/claude-review.md`。
10. 判断任务通过、返工或建议提交。

Claude Code 不应：

1. 不直接大规模编写业务代码。
2. 不绕过需求确定阶段。
3. 不在需求不清晰时擅自扩大范围。
4. 不修改密钥、生产配置、证书文件。
5. 不直接删除大量文件。
6. 不在审查未完成时建议提交主分支。

---

## 3. Codex 职责

Codex 必须负责：

1. 读取 `AGENTS.md`。
2. 读取 `docs/requirements.md`。
3. 读取 `tasks/requirement-analysis.md`。
4. 读取 `tasks/claude-plan.md`。
5. 读取 `tasks/codex-task.md`。
6. 按任务单实现代码。
7. 按最小改动原则修改文件。
8. 补充或更新测试。
9. 运行测试命令。
10. 输出修改文件、测试命令、测试结果和潜在风险。

Codex 不应：

1. 不重新解释需求。
2. 不覆盖 Claude Code 的需求判断。
3. 不负责最终验收。
4. 不擅自扩大实现范围。
5. 不引入无关大型依赖。
6. 不修改密钥、生产配置、证书文件。
7. 不进行无关重构。
8. 不删除无关代码。

---

## 4. 文件交接规则

Claude Code 与 Codex 通过文件系统和 Git 状态完成交接。

```text
docs/technical-requirements.md
  ↓
tasks/requirement-analysis.md
  ↓
docs/requirements.md
  ↓
tasks/claude-plan.md
  ↓
tasks/codex-task.md
  ↓
代码改动与测试结果
  ↓
reviews/claude-review.md
```

---

## 5. Git 使用规则

建议每次任务使用独立分支：

```bash
git checkout -b ai/task-name
```

Codex 完成后，不要直接提交主分支。  
Claude Code 必须先审查：

```bash
git status
git diff
```

审查通过后再建议提交：

```bash
git add .
git commit -m "feat: implement task with claude codex workflow"
```

---

## 6. 安全边界

任何智能体都不得直接修改：

```text
.env
.env.*
*.pem
*.key
*.crt
生产数据库配置
生产部署脚本
真实账号密码
真实访问令牌
```

如确需修改，必须先说明原因、影响范围、回滚方式，并等待用户确认。

---

## 7. 最小改动原则

默认采用最小改动路径：

```text
1. 优先复用现有结构。
2. 优先修改与任务直接相关的文件。
3. 不进行无关重构。
4. 不引入不必要抽象。
5. 不为未来假设场景过度设计。
6. 测试覆盖当前需求和关键异常场景。
```

---

## 8. 质量标准

代码完成后必须满足：

```text
1. 满足 docs/requirements.md。
2. 符合 tasks/claude-plan.md。
3. 未超出 tasks/codex-task.md。
4. 有必要测试。
5. 测试可运行。
6. 无明显安全风险。
7. 无明显过度设计。
8. 可回滚。
```
