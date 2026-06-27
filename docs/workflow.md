# 每个需求的 AI 实现流程（全员、全工具通用）

> 适用于 Claude Code / Cursor / Copilot / Codex / Gemini。核心思想：**先把要做什么写清楚（规格），再让 AI 去做**。模糊的提示会逼模型去"猜"上千个没说的需求 —— 规格消除猜测。

## 总览：spec → plan → tasks → implement → review → PR

```
①拿到需求条目  ──►  ②/specify 写规格  ──►  ③/plan 生成计划
   (来自 requirements-breakdown.md)        (引用契约+现有代码)
                                                │
                                                ▼
⑥PR + CI + 人评审  ◄──  ⑤对抗式自审  ◄──  ④/tasks 拆任务 → implement
```

## 步骤详解

### ① 领取需求
从 `docs/requirements-breakdown.md` 找你负责的条目；确认它在 `contracts/` 里已有对应端点/表（没有就先按下面 §契约变更走）。

### ② 写规格（specify）
用 `.specify/templates/spec-template.md` 写一份 spec，存到 `.specify/specs/<feature>/spec.md`。
- **必须**：验收标准（可测试）、涉及的契约端点/表/枚举、明确"不做什么"、端到端验证步骤。
- Spec Kit 用户：`/speckit.specify <描述>`。其他工具：直接按模板让 AI 起草，你来改实。

### ③ 生成计划（plan）
让 AI 读 spec + `contracts/` + 同模块已有代码，产出 `plan.md`（用 plan 模板）。
- Claude Code：用 **plan mode** 探索后产出计划；多文件/不确定方案时必做。
- 关键：让 AI **指向现有模式**（"参照 `module/user` 的 controller/service 写法"），而不是另起一套。

### ④ 拆任务 + 实现（tasks → implement）
- 生成依赖排序的 `tasks.md`，逐条实现。
- **契约即输入**：后端基于生成的 stub 实现；前端基于生成的 client + mock server 开发。
- **边写边测**：每条任务完成就跑测试（`mvn test` / `npm run type-check`+`test`），附证据。

### ⑤ 对抗式自审（必做）
让一个**全新上下文**只看 diff + spec 来挑毛病（不带"我刚写完"的偏向）：
- Claude Code：`/code-review`，或 `用 subagent 对照 spec 审这次 diff，只报影响正确性/需求的问题`。
- 其他工具：新开一个会话，贴 diff + spec，让它找"未实现的验收项 / 边界缺失 / 越权 / 与契约不符"。
- 修复后再过一遍。注意：reviewer 总能挑出东西，别为"风格偏好"过度设计。

### ⑥ PR
按 `docs/merge-request-template.md` 提**合并请求(MR)**（CodeArts，非 GitHub）：关联 spec、列出涉及端点、确认 DoD、贴验证证据。CI 门禁全绿 + 1 人评审后合并；合并到 `main` 自动触发服务器 CI/CD 部署（见 `docs/codearts-and-cicd.md`）。

## 契约变更怎么走（高频坑）
发现需要新增/改端点、表、枚举、错误码？**不要在 feature 分支直接改 `contracts/`**：
1. 写一条 ADR（`docs/adr/`）说明为什么、怎么改、影响谁。
2. 单独 MR 改 `contracts/`，平台核心组评审（CodeArts 分支保护 + 必选评审，见 `docs/codearts-and-cicd.md`）。
3. 合并后升版本、通知全员重新生成，再继续你的 feature。

## 反模式（来自实践，避开它们）
- **大杂烩会话**：一个会话里穿插无关任务 → 上下文污染。切任务前 `/clear`。
- **反复纠正**：纠正两次还不对 → `/clear`，用更具体的提示重开。
- **信而不验**：看起来对≠对。没有测试/证据不算完成。
- **无边界探索**："研究一下 X"不设范围 → 读几百个文件爆上下文。用 subagent + 限定范围。
- **臃肿的 AGENTS.md/CLAUDE.md**：太长 → AI 忽略一半。只留"不写就出错"的。
