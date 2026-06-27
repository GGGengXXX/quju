# 设计文档：趣聚 QuJu — 团队 AI 开发工作区

- 日期：2026-06-27
- 状态：已与负责人确认主体方向
- 适用：第 6 组，10 人 / 10 天 / AI 辅助开发

## 1. 目标
搭建一个**统一、无歧义、可并行**的工作区，使 10 名开发者（使用混合 AI 工具：Claude Code / Cursor / Copilot / Codex 等）能够在 10 天内并行实现"趣聚平台"需求，互不阻塞、产出一致。

## 2. 关键决策（已确认）
| 维度 | 决策 |
|---|---|
| AI 工具 | 混合 → `AGENTS.md` 作工具中立事实来源 + GitHub Spec Kit 规格驱动 |
| 后端 | Java 17 + Spring Boot 3 + **MyBatis-Plus** + MySQL 8 + Redis |
| 前端 | **Vue 3 + TypeScript** + Vite + Pinia + Element Plus |
| 仓库 | **Monorepo** |
| 代码托管 | **华为云 CodeArts Repo**（cn-north-4，**非 GitHub**）；MR + 分支保护做评审治理 |
| 对象存储 | **阿里云 OSS**，bucket `se-resource-bucket`（密钥走环境变量，不入库） |
| 部署 / CI-CD | 在**单台服务器 `1.92.124.5`** 自托管；分支 master(部署)/dev(集成)/feature；合并到 master 自动构建 + 部署 |
| 协作 | 协调方式 = **契约优先 + 垂直切片所有权**；2 人平台核心组守契约 |
| 服务器 | 全员**直接在服务器**开发；每 feature 一个**独立 clone**（CentOS7 git 1.8 无 worktree）；每人独立 DB schema + 端口段，共用一台 MySQL/Redis |
| 生成范围 | 本次先生成**治理层 + 契约骨架**（不含可运行应用脚手架） |

## 3. 方法论依据（调研结论）
- **规格驱动开发 (SDD)**：GitHub Spec Kit，spec→plan→tasks→implement，消除"模型猜测"。
- **AGENTS.md**：AI 的 README，工具中立开放标准（Linux 基金会），与 `CLAUDE.md` 同步。
- **契约优先 (contract-first)**：OpenAPI + DB schema 作唯一事实来源，前端用 mock/生成 client、后端用生成 stub，**并行开发**。
- **Claude Code 实践**：explore→plan→code→commit、subagent 调研/对抗式 review、worktree 并行、可验证才算完成、精简 CLAUDE.md。

## 4. 架构（工作区）
见仓库结构（README）。三层：
1. **治理层**：`AGENTS.md`(+各层)、`CLAUDE.md`、`.specify/`(宪法+模板)、`docs/`(流程/分工/runbook/ADR/codearts-and-cicd/secrets)、`deploy/`(服务器 CI-CD)、`scripts/`。
2. **契约层**：`contracts/`(openapi + schema + enums + error-codes) —— 仅平台核心组可改，CodeArts 分支保护 + 必选评审强制。
3. **实现层**：`backend/` `frontend/`，各组按垂直切片在自己的包/目录内实现。

## 5. 流程
- **每需求流程**：`docs/workflow.md`（spec→plan→tasks→implement→对抗式自审→PR→CI→评审）。
- **契约变更**：ADR → 平台核心组评审 → 升版本 → 全员重生成。
- **服务器开发**：`docs/dev-on-server-runbook.md`（worktree + 隔离 DB + 端口）。

## 6. 10 天时间线
- Day 0–2：需求统一 → spec；冻结 schema+OpenAPI+约定；脚手架 + CI + 契约骨架；生成 stub/类型/mock。
- Day 3–8：并行垂直切片（前端用 mock、后端真实现，向契约收敛）。
- Day 9：集成、端到端、性能核对（<5s / <2s）。
- Day 10：修缺陷、打磨、演示准备。

## 7. 风险与对策
| 风险 | 对策 |
|---|---|
| 契约漂移 | 唯一 `contracts/` + CodeArts 分支保护/必选评审 + CI 契约校验 + 生成产物不手改 |
| 服务器上互相踩 | 每人独立 clone 隔离 + 独立 DB/端口 + 文件归属边界 |
| 活动 a/b 同模块冲突 | Day0–2 划清子包/子目录边界，写进各自 spec |
| AI 产出不可验证 | DoD 强制测试 + 对抗式自审 + CI 门禁 |
| 第三方未就绪 | AI 网关/适配层收口，可 mock；前端用 mock server |

## 8. 待补充清单（`<<TODO>>` 占位，待负责人确认）
> 全仓库一键查看：`grep -rn "<<TODO" .`

**✅ 已提供/已落地**：阿里云 OSS（key 在 `AccessKey.csv`，bucket `se-resource-bucket`，endpoint `oss-cn-beijing.aliyuncs.com`）· CodeArts 仓库 · 服务器 `1.92.124.5`(CentOS7+Docker) · Java 包 `cn.edu.buaa.quju` · **MySQL 容器 quju-mysql 已建(13306, 库 quju, 账号 quju)** · 部署 `deploy/` 脚手架 + SSH 部署密钥已就绪。密钥均走环境变量，见 `docs/secrets-and-config.md`。

**A. 第三方 / 密钥（仍缺，先留空）**
- AI 服务：活动策划/内容审核/图片分类 的 LLM+视觉 提供商及 key（建议国内合规：通义/智谱/DeepSeek + 视觉）
- 邮件 SMTP（账号激活链接）
- 高德 AMap key（选点/附近/距离/地图模式）

**B. 基础设施 / 命名（多数已定）**
- 端口区间确认（注意宿主 3306 被原生 mysqld 占用，本项目 MySQL 用 13306）

**C. 团队信息（可由我从 `第6组Github邮箱.xlsx` 自动填，待确认）**
- 10 人姓名 + CodeArts/邮箱账号 → `docs/TEAM.md`、分工与端口表
- 技术负责人 + 各组组长

**D. 待定决策 / ADR**
- ✅ **CI/CD 方案 = A**（CodeArts Pipeline 部署任务 SSH→服务器 `deploy/deploy.sh`）；服务器侧已就绪。待你：加仓库只读公钥 + 首次 push + 建流水线（见 `docs/codearts-and-cicd.md` 第五节）
- HTTP 状态码语义 vs 统一 200 信封（ADR 0002）
- Flyway vs Liquibase（ADR 0003）
- AI 服务提供商（ADR 0004）
- IM 方案 WebSocket/STOMP，是否引消息中间件（ADR 0005）

**E. 日程**
- 与 `综合实践日程安排.xlsx` 对齐 10 天时间线（可由我读取后细化，待确认）

## 9. 后续（确认本设计后）
1. 你确认/补充第 8 节清单（或授权我读两个 xlsx 自动填名册+日程）。
2. **选定 CI/CD 方案（A/B）**；如授权，我登服务器或用 CodeArts API 生成 `deploy/` 全套并搭好。
3. 平台核心组按本工作区 Day0–2 冻结契约。
4. 如需，我再生成"可运行应用脚手架"或"一个端到端示例切片(注册/登录)"作为团队范本。
