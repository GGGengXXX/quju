# 共享枚举登记表

所有跨前后端的枚举集中登记于此，**前端 TS、后端 Java、OpenAPI、DB 必须保持一致**。
新增/修改枚举值视为契约变更（需 ADR + 评审）。

## 已定义

### UserType — 用户类型
`INDIVIDUAL` 个人 · `MERCHANT` 商家

### UserStatus — 账号状态
`PENDING_ACTIVATION` 待激活 · `ACTIVE` 正常 · `BANNED` 已封禁

### AuditStatus — 通用审核状态
`PENDING` 待审 · `APPROVED` 通过 · `REJECTED` 驳回 · `NEEDS_REVISION` 要求修改

### Gender
`MALE` · `FEMALE` · `UNKNOWN`

## 待补全（由对应负责人定义，登记到此）

- `<<TODO: ActivityStatus — 未开始 / 报名中 / 截止 / 活动中 / 已结束>>`
- `<<TODO: SignupStatus — 已报名 / 已取消 / 候补中 / 已递补>>`
- `<<TODO: ActivityAuditFlow — AI通过直接发布 / 转人工 / 人工通过 / 驳回 / 改>>`
- `<<TODO: TeamRole — 队长 / 管理员 / 普通成员>>`
- `<<TODO: TeamJoinType — 公开加入 / 审核加入>>`
- `<<TODO: FriendRelation — 关注 / 互关(好友) / 黑名单>>`
- `<<TODO: MessageType — 文字 / 表情 / 图片 / 位置>>`
- `<<TODO: ActivityImageCategory — 合影 / 场地 / 过程记录 / 物资 / 成果展示>>`（AI 图片分类）

> 枚举命名：值全大写下划线（`PENDING_ACTIVATION`）；DB 存字符串而非数字，便于可读与排错。
