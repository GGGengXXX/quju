# contracts/ — 唯一事实来源

前后端共同的契约。**改动须先写 ADR（`docs/adr/`）并由平台核心组在合并请求(MR)中批准**（CodeArts 分支保护 + 必选评审，见 `docs/codearts-and-cicd.md`）。

| 文件 | 内容 |
|---|---|
| `openapi.yaml` | OpenAPI 3.1，所有接口的请求/响应/错误。生成后端 DTO/stub、前端类型/client、mock server。 |
| `schema.dbml` | 数据库 schema（DBML）。是迁移脚本的基线。 |
| `enums.md` | 跨端共享枚举登记。 |
| `error-codes.md` | 统一错误码登记（按模块分区间）。 |

## 工作流：契约优先（contract-first）

1. 平台核心组在 **Day0–2** 冻结基础契约（信封、鉴权、分页、基础表、示例模块）。
2. 各模块负责人在评审下，把自己模块的路径 / schema / 表 / 枚举 / 错误码补进来。
3. 冻结后打 tag（如 `contract-v1`）。**冻结后前后端并行开发**：
   - 前端基于生成的 client + mock server 开发（不等后端）。
   - 后端基于生成的 stub 实现真实逻辑。
4. 之后任何契约变更：ADR → 评审 → 升版本 → 通知全员重新生成。

## 代码生成（建议命令，按需落地为 Makefile / 脚本）

> `<<TODO: 平台核心组确认并落地以下命令；下方为推荐工具，可替换>>`

```bash
# 后端：openapi-generator 生成 DTO + Spring 接口（只生成，不写实现）
make gen-backend
#   等价：openapi-generator-cli generate -i contracts/openapi.yaml \
#         -g spring -o backend --additional-properties=interfaceOnly=true,...

# 前端：openapi-typescript / orval 生成 TS 类型 + axios/fetch client
make gen-frontend
#   等价：npx openapi-typescript contracts/openapi.yaml -o frontend/src/api/generated/types.ts
#         （或 orval 生成带 hooks 的 client）

# Mock server：基于契约起 mock，前端联调用
make mock
#   等价：npx @stoplight/prism-cli mock contracts/openapi.yaml

# 校验契约本身合法（CI 也会跑）
make lint-contract
#   等价：npx @redocly/cli lint contracts/openapi.yaml
```

## 规则

- **生成产物不手改**：`backend/src/main/generated/`、`frontend/src/api/generated/` 是生成的，改契约后重新生成，别手动编辑（已在 .gitignore）。
- **契约与实现不符 = bug**：以契约为准。CI 会做契约校验。
