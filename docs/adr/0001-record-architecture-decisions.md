# ADR 0001：采用 ADR 记录架构决策，并确立初始技术栈

- 状态：已接受
- 日期：2026-06-27
- 决策者：平台核心组

## 背景
10 人 / 10 天 / AI 辅助开发。需要让所有人（不同 AI 工具）在统一、无歧义的约束下并行开发，并把"为什么这么做"沉淀下来，避免口头/记忆传递。

## 决策
1. 用 **ADR**（Architecture Decision Record）记录所有重要/不可逆决策，存于 `docs/adr/`，编号递增。**任何对 `contracts/` 的变更必须有对应 ADR。**
2. 初始技术栈（见 README）：
   - 后端：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis
   - 前端：Vue 3 + TypeScript + Vite + Pinia + Element Plus
   - 契约：OpenAPI 3.1 + DBML，contract-first
   - 协作：Monorepo + 服务器 git worktree + 华为云 CodeArts 合并请求(MR) + 服务器自托管 CI/CD；规格驱动（Spec Kit）
   - 代码托管：华为云 CodeArts Repo（cn-north-4）；对象存储：阿里云 OSS（bucket `se-resource-bucket`）；部署：单台服务器 `1.92.124.5`
   - 工具中立规则：`AGENTS.md`（+ `CLAUDE.md` 导入）

## 后果
- 好处：决策可追溯；新人/AI 读 ADR 即懂"为什么"；契约变更有据可查。
- 代价：改契约多一步写 ADR —— 这是有意的"摩擦"，用来保护唯一事实来源。

## 待决策（后续 ADR）
- `<<TODO: ADR 0002 — HTTP 状态码语义 vs 统一 200 信封>>`
- `<<TODO: ADR 0003 — Flyway vs Liquibase>>`
- `<<TODO: ADR 0004 — AI 服务提供商（活动策划/内容审核/图片分类）>>`
- `<<TODO: ADR 0005 — IM 方案（WebSocket/STOMP；是否引入消息中间件）>>`

---
### ADR 模板（复制此段新建）
```
# ADR XXXX：<标题>
- 状态：提议 / 已接受 / 已废弃 / 被取代(→ADR YYYY)
- 日期：YYYY-MM-DD
- 决策者：
## 背景
## 决策
## 后果（好处 / 代价 / 影响谁）
## 备选方案
```
