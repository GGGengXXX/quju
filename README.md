# 趣聚 QuJu — 兴趣线下活动社交平台

> 10 人 / 10 天 / AI 辅助开发。本仓库是**统一工作区**：所有人（无论用 Claude Code / Cursor / Copilot / Codex）都遵循同一套规则、同一份契约、同一套流程，做到**无歧义、可并行**的开发。

## 这个仓库的"宪法"是什么

| 文件 | 作用 | 谁能改 |
|---|---|---|
| [`AGENTS.md`](AGENTS.md) | **唯一事实来源**：所有 AI 工具读它来了解如何在本仓库工作 | 全员（评审后） |
| [`CLAUDE.md`](CLAUDE.md) | Claude Code 入口，导入 `AGENTS.md` | 全员 |
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | 项目不可妥协的底线原则 | 平台核心组 |
| [`contracts/`](contracts/) | ⭐ **DB schema + OpenAPI 契约 + 枚举/错误码**，前后端共同的事实来源 | **仅平台核心组**（CodeArts 分支保护，见 [codearts-and-cicd.md](docs/codearts-and-cicd.md)） |

> 代码托管：华为云 CodeArts（**非 GitHub**）
> `https://codehub.devcloud.cn-north-4.huaweicloud.com/5c09170aa96c46008547da02db15afa0/quju.git`

## 第一次来？按顺序读

1. [`docs/dev-on-server-runbook.md`](docs/dev-on-server-runbook.md) — 如何在服务器上用 worktree 开始你的 feature
2. [`docs/workflow.md`](docs/workflow.md) — 每个需求的 AI 实现流程（spec → plan → tasks → implement → review → MR）
3. [`docs/requirements-breakdown.md`](docs/requirements-breakdown.md) — 4 大模块 → 子功能 → 负责人 → 端点/表
4. [`AGENTS.md`](AGENTS.md) — 编码规范与铁律
5. [`docs/codearts-and-cicd.md`](docs/codearts-and-cicd.md) — CodeArts 仓库/推送/MR/服务器 CI-CD；[`docs/secrets-and-config.md`](docs/secrets-and-config.md) — 密钥与配置（OSS/服务器，均走环境变量）

## 技术栈

- **后端**：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8（含空间索引）+ Redis
- **前端**：Vue 3 + TypeScript + Vite + Pinia + Element Plus
- **契约**：OpenAPI 3.1（生成后端 DTO/stub、前端 TS 类型与 client、mock server）
- **协作**：Monorepo + 服务器上每 feature 一个独立 clone + 华为云 CodeArts 合并请求(MR) + **CodeArts 流水线自动部署**（合并到 master 触发）
- **存储/部署**：阿里云 OSS（bucket `se-resource-bucket`）+ 单台服务器 `1.92.124.5`（密钥见 `docs/secrets-and-config.md`，均走环境变量不入库）
- **流程**：契约优先（contract-first）+ 规格驱动（GitHub Spec Kit）

## 待补充（占位）

本仓库中所有需要你方提供的信息都以 `<<TODO: ...>>` 标记。一次性查看：

```bash
grep -rn "<<TODO" . --include="*.md" --include="*.yaml" --include="*.sh" --include="*.example"
```

逐项确认后替换即可。详见 [`docs/superpowers/specs/2026-06-27-quju-ai-workspace-design.md`](docs/superpowers/specs/2026-06-27-quju-ai-workspace-design.md) 的"待补充清单"。
