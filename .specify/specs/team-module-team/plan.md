# 实现计划 Plan：R4 兴趣小队模块

## 1. 方案概述
以现有用户模块的 `controller -> service -> mapper -> entity/dto` 模式为模板，在 `backend/src/main/java/cn/edu/buaa/quju/module/team` 新建完整切片，复用 `R`、`BizException`、`UserContext`、MyBatis-Plus 与 JWT 鉴权约定。前端在 `frontend/src/views/team` 新建单页工作台，延续当前 Element Plus + 手写 API 封装模式，不改全局 HTTP 解信封逻辑。

## 2. 后端改动
- 包/文件：新增 `module/team/{controller,service,mapper,entity,dto}`，补充 `activity` 只读 mapper 支持队内活动列表。
- 数据库：不改 schema，不新增迁移；严格依赖现有 `contracts/schema.sql` 中的小队相关表结构。
- 关键逻辑 / 事务 / 校验 / 错误码：
  - 创建小队时事务性写入主表、标签、队长成员记录。
  - 加入/审批/移除/离队时维护 `member_count` 与成员角色边界。
  - 管理操作统一校验 `OWNER/ADMIN`；角色设置仅 `OWNER` 可执行。
  - 发布动态与精选动态时同步写积分日志并回写 `team_member.points`。
  - 队内活动列表只读查询 `activity.team_id`，不修改活动模块代码。

## 3. 前端改动
- 视图/组件：新增 `frontend/src/views/team/TeamHub.vue`
- store / api 封装；路由：新增 `frontend/src/api/team.ts`，在路由中添加 `/teams` 入口，并在已有登录用户场景下可访问。

## 4. 契约依赖
- 用到的端点均已在 `contracts/openapi.yaml` 定义；仓库目前未接入生成 client，本次沿用手写 API 封装但 URL/字段严格对齐契约。
- 不修改 `contracts/`；对 R4.5 的实现按契约中的 `activity.team_id` 处理，而非新增 `team_activity` 表。

## 5. 测试策略
- 后端：补 service/controller 关键分支测试，覆盖创建、公开加入、审核加入、权限校验、角色设置、积分累加等核心路径。
- 前端：当前仓库未配置 Vitest；本次至少通过 `npm install && npm run build && npm run type-check` 验证。
- 端到端验证：使用两个账号手工走创建、加入、审批、内容管理、解散路径。

## 6. 风险与依赖
- 当前 `contracts/openapi.yaml` 对 team 响应 schema 多为通用 `Ok`，需要在实现中自行保持字段稳定。
- 仓库未配置前端测试脚本，需要以构建与类型检查作为门禁。
- 队内活动积分的“参与活动自动加分”需要活动模块事件联动，本次不越权扩展，只消费现有日志表数据。
