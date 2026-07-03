# ADR 0002：后台小队详情端点（成员/队内活动/举报记录）

- 状态：提议
- 日期：2026-07-03
- 决策者：平台核心组（待评审）

## 背景
需求 v2 §4.4「小队查询」要求：管理员可查看小队的**基本信息、成员信息、队内活动及相关举报记录**，但不得直接修改小队名称、标签、公告和成员身份。

当前契约（`contracts/openapi.yaml`）后台小队相关端点只有：
- `GET /admin/teams`（列表，仅基本信息）
- `POST /admin/teams/{id}/suspend` `/restore`（停用/恢复）
- `GET /admin/reports`（全局举报，未按小队聚合）

缺少一个「小队详情」只读端点，因此需求 §4.4 的「成员信息 / 队内活动 / 该小队的举报记录」在后台无法展示。补齐它需要新增契约端点——按金律 #1 必须走 ADR + 平台核心组评审，不得在 feature 分支顺手改契约。

## 决策
新增只读端点 `GET /admin/teams/{id}`，返回聚合的小队详情（只读，不提供任何修改小队名称/标签/公告/成员身份的能力）：

```yaml
/admin/teams/{id}:
  get:
    tags: [admin]
    summary: 小队详情(基本信息/成员/队内活动/举报记录)
    parameters: [{ $ref: '#/components/parameters/PathId' }]
    responses: { '200': { $ref: '#/components/responses/Ok' } }
```

`data` 结构（camelCase，遵循全局约定）：
```jsonc
{
  "id": 1, "name": "登山小队", "status": "ACTIVE", "ownerId": 10,
  "intro": "...", "joinType": "PUBLIC", "capacity": 50, "memberCount": 12,
  "createdAt": "...",
  "members": [ { "userId": 10, "nickname": "队长", "role": "OWNER", "points": 30 } ],
  "activities": [ { "id": 5, "name": "周末登山", "status": "PUBLISHED", "startTime": "..." } ],
  "reports": [ { "id": 3, "reporterId": 7, "reason": "SPAM", "detail": "...", "status": "PENDING", "createdAt": "..." } ]
}
```

数据来源：`team` + `team_member`（join `user` 取昵称）+ `activity`(team_id) + `report`(target_type='TEAM', target_id=teamId)。成员/活动列表做上限保护（如各 200 条）避免大队卡顿。

## 后果（好处 / 代价 / 影响谁）
- 好处：补齐需求 §4.4，后台可完整审查违规小队（成员、活动、举报一屏可见）。只读，不破坏「管理员不得修改小队内容」的边界。
- 代价：新增一个契约端点 + 后端聚合查询 + 前端小队详情弹窗。查询跨 `team_member`/`activity`/`report`，需注意分页/上限与 N+1。
- 影响谁：后端 `module/admin`、前端 `views/admin`（用户+后台组）；契约由平台核心组合入。

## 备选方案
1. **不新增端点，前端拼多个已有接口**：`GET /admin/reports` 无法按小队过滤，且没有「小队成员」「队内活动」的后台端点，无法拼出，否决。
2. **扩展 `GET /admin/teams` 列表项内嵌成员/活动/举报**：列表接口负载过重、违反分页与性能约束（§5），否决。
3. **本 ADR 方案：独立只读详情端点**（推荐）。
