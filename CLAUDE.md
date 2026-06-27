# CLAUDE.md

本仓库的规则统一写在 `AGENTS.md`，请优先遵循它。

@AGENTS.md

## 仅 Claude Code 用户的补充说明

- **plan mode**：实现多文件 / 不确定方案的需求前，先用 plan mode 探索 `contracts/` 与同模块代码，产出计划，再切换实现。一句话能描述的小改动可跳过。
- **subagent**：用 subagent 做"读很多文件"的调研与"对抗式 review"，保持主上下文干净。完成实现后用 `/code-review` 让新上下文审 diff。
- **/clear**：切换到不相关任务前先 `/clear`，避免上下文污染。
- **并行**：本项目在服务器上每 feature 一个独立 clone 并行（CentOS7 git 1.8 无 worktree）。一次并行别超过你能 review 的 2–3 个。
- 团队自定义 skill 放在 `.claude/skills/`（按需创建，不强制）。Cursor/Copilot/Codex 用户通过 `AGENTS.md` + `docs/workflow.md` 获得同样的规则。
