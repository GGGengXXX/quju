# AGENTS.md — 趣聚 QuJu 工作区规则（唯一事实来源）

> 这是所有 AI 编码工具（Claude Code / Cursor / Copilot / Codex / Gemini …）在本仓库工作前**必须**读取的规则文件。`CLAUDE.md` 仅导入本文件。改动本文件需 PR 评审。
> 保持精简：只写"不写就会出错"的内容。详细流程链接到 `docs/`。

## 0. 铁律（Golden Rules）

1. **契约即法律。** `contracts/`（DB schema + OpenAPI + 枚举/错误码）是前后端唯一事实来源。**任何对 `contracts/` 的改动都必须**：先写 ADR（`docs/adr/`）→ 单独的合并请求(MR) → 平台核心组批准（CodeArts 分支保护 + 必选评审，见 `docs/codearts-and-cicd.md`）。严禁在 feature 分支里"顺手"改契约。
2. **先看契约/已有代码，再动手。** 实现任何端点/页面前，先读对应的 OpenAPI 定义与同模块已有实现，遵循既有模式，不要另起一套。
3. **一个 feature = 一个分支 = 一个独立 clone。** 只在你自己的 clone 与你负责的目录/表内改动（见 `docs/work-assignment.md`）。不要碰别人负责的文件。
4. **完成必须可验证。** 每次实现都要写测试并运行通过；后端 `mvn test`，前端 `npm run test`/`type-check`。没有通过的验证不算完成（见 §6 Definition of Done）。
5. **不提交密钥。** 第三方 key（高德、LLM、SMTP、OSS）一律走环境变量 / `.env`（已 gitignore），代码与提交里不得出现明文。

## 1. 仓库结构

```
contracts/   ⭐ 契约（仅平台核心组改）   backend/   Spring Boot 服务
frontend/    Vue 3 应用                  docs/      规范、流程、分工、ADR、runbook
.specify/    Spec Kit（宪法+模板）       scripts/   dev-bootstrap 等脚本
deploy/      服务器部署脚本/编排（自托管 CI-CD，见 docs/codearts-and-cicd.md）
```
后端、前端各有自己的 `AGENTS.md`，描述该层规范，必须遵守。

## 2. 环境与常用命令

> 标准开发环境是**服务器上的 worktree**，不在本地。新功能开始前先读 `docs/dev-on-server-runbook.md`。

```bash
# 新建一个 feature 的独立 clone + 隔离 DB schema + 分配端口（在 /srv/quju/main 运行）
scripts/dev-bootstrap.sh <your-name> <feature-slug>

# 后端（在 backend/）
mvn spring-boot:run            # 启动（端口见你的 .env）
mvn test                       # 单元/集成测试
mvn -q -DskipTests package     # 构建

# 前端（在 frontend/）
npm ci                         # 安装依赖
npm run dev                    # 启动 dev server（端口见你的 .env）
npm run type-check             # TS 类型检查（CI 门禁）
npm run lint                   # ESLint（CI 门禁）
npm run test                   # 单元测试

# 契约（在 contracts/，改动需 ADR + 评审）
make gen-backend               # 由 openapi.yaml 生成后端 DTO/接口
make gen-frontend              # 由 openapi.yaml 生成前端 TS 类型 + API client
make mock                      # 启动 mock server（前端可在后端未就绪时联调）
```
> `make` 目标见 `contracts/README.md`。`<<TODO: 若团队不用 make，请改成对应脚本命令>>`

## 3. 全局编码约定（前后端共同遵守）

- **统一响应信封**：所有后端业务接口返回 `{ "code": int, "message": string, "data": T }`。`code = 0` 表示成功；非 0 见 `contracts/error-codes.md`。**不要**直接返回裸对象。
- **分页**：列表接口统一用 `page`（从 1 开始）、`size`，返回 `PageResult<T> = { total, page, size, list }`。
- **命名**：URL 用 kebab-case 且名词复数（`/v1/interest-teams`）；JSON 字段用 camelCase；DB 表/列用 snake_case。
- **版本**：所有接口前缀 `/v1`。
- **时间**：后端统一 UTC，`OffsetDateTime`/`timestamp`，JSON 用 ISO-8601；展示时区由前端处理。
- **鉴权**：JWT Bearer Token，`Authorization: Bearer <token>`。受保护接口在 OpenAPI 中标 `security`。
- **错误**：业务错误抛统一异常 → 全局异常处理器转成信封 + 对应 `code`，不要在 controller 里散落 try/catch 拼装。
- **ID 策略**：主键 `bigint`，雪花/数据库自增（见 `contracts/schema.dbml` 约定块），对外可暴露但不要泄露可枚举的敏感顺序。
- **软删除/审计**：含 `created_at`、`updated_at`；需要软删的表用 `deleted_at`（见 schema 约定）。

## 4. 每个需求的实现流程（必须）

详见 [`docs/workflow.md`](docs/workflow.md)。简版：
`specify`（写规格）→ `plan`（实现计划，引用契约）→ `tasks`（拆任务）→ `implement`（写代码+测试）→ **对抗式自审**（新上下文 review diff 对照规格）→ PR → CI → 人评审 → 合并。

## 5. 非功能要求（硬指标）

- App/Web 从启动到登录页/首页可操作 **< 5s**。
- 每个后台业务接口单次响应 **< 2000ms**（AI/地图/邮件等第三方及其调用方不计入）。
- 写代码时即考虑：N+1 查询、列表分页、地理查询走空间索引、热点数据走 Redis。

## 6. Definition of Done（PR 可合并的标准）

- [ ] 行为与 `contracts/openapi.yaml` 完全一致（字段名、类型、错误码）。
- [ ] 仅改动了你负责的目录/表；未触碰 `contracts/`（除非带 ADR + 核心组批准）。
- [ ] 写了测试且本地通过；后端 `mvn test`、前端 `type-check`+`lint`+`test` 全绿。
- [ ] 跑过一次"对抗式自审"且无遗留问题（见 workflow）。
- [ ] PR 按模板填写，关联了对应 spec，列出了涉及的端点。
- [ ] CI 全绿。

## 7. 合并请求(MR) / 分支约定

> 代码托管在**华为云 CodeArts**（不是 GitHub）。详见 `docs/codearts-and-cicd.md`。

- 分支模型：**master**(生产，合并即部署) ← **dev**(集成) ← **feature**(从 `dev` 拉出)。详见 `docs/codearts-and-cicd.md` §〇。
- 分支命名：`feat/<module>-<slug>`、`fix/<module>-<slug>`、`chore/<slug>`，**从 `dev` 拉出**。
- 推送 + 建 MR：`git push -u origin feat/...` → CodeArts 新建合并请求，**目标 `dev`**。
- 一个 MR 只做一件事，尽量小、可独立评审。MR 标题 `<module>: <做了什么>`，描述按 `docs/merge-request-template.md`。
- `master`/`dev` 受分支保护：禁直接 push、必须经 MR、≥1 评审 + CI 门禁通过才可合并。
- 改 `contracts/` 需平台核心组评审。**部署**：把 `dev` 合并到 `master` → 自动触发服务器部署。
