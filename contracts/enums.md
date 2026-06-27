# 共享枚举登记表

所有跨前后端枚举集中登记于此，**前端 TS、后端 Java、OpenAPI、DB 必须一致**（值全大写下划线，DB 存字符串）。
新增/改枚举值视为契约变更（ADR + 评审）。

## 用户 / 账号
- **UserType**：`INDIVIDUAL` 个人 · `MERCHANT` 商家
- **UserStatus**：`PENDING_ACTIVATION` 待激活 · `ACTIVE` 正常 · `BANNED` 已封禁
- **Gender**：`MALE` · `FEMALE` · `UNKNOWN`
- **AuditStatus**（通用审核）：`PENDING` · `APPROVED` · `REJECTED` · `NEEDS_REVISION`
- **EmailTokenType**：`ACTIVATION` · `RESET_PASSWORD`
- **BanStatus**：`ACTIVE` · `LIFTED`

## 活动
- **ActivityCategory**：`SPORTS` 运动健身 · `HIKING` 户外徒步 · `BOARD_GAME` 桌游聚会 · `STUDY` 学习交流 · `CHARITY` 公益活动 · `CITY_WALK` 城市探索 · `OTHER` 其他
- **ActivityStatus**（发布生命周期）：`DRAFT` 草稿 · `PENDING_REVIEW` 待审 · `PUBLISHED` 已发布 · `REJECTED` 驳回 · `TAKEN_DOWN` 已下架 · `CANCELLED` 已取消
- **ActivityPhase**（时间相位，由 start/deadline/end 计算，不入库）：`NOT_STARTED` 未开始 · `SIGNUP_OPEN` 报名中 · `SIGNUP_CLOSED` 截止 · `ONGOING` 活动中 · `ENDED` 已结束
- **SignupStatus**：`REGISTERED` 已报名 · `CANCELLED` 已取消 · `WAITLISTED` 候补中
- **WaitlistStatus**：`WAITING` · `NOTIFIED` · `PROMOTED` 已递补 · `EXPIRED` 超时 · `CANCELLED`
- **ActivityAuditType**：`AI` · `MANUAL`
- **ActivityAuditResult**：`PASSED` · `REJECTED` · `NEEDS_REVISION` · `TO_MANUAL` 转人工
- **ActivityImageCategory**（AI 图片分类）：`GROUP_PHOTO` 合影 · `VENUE` 场地 · `PROCESS` 过程记录 · `MATERIAL` 物资 · `RESULT` 成果展示
- **SummaryStatus**：`DRAFT` · `PUBLISHED`

## 社交 / IM
- **FriendRequestStatus**：`PENDING` · `ACCEPTED` · `REJECTED`
- **FriendRequestSource**：`PROFILE` · `ACTIVITY` · `QRCODE`
- **MessageScope**：`FRIEND` · `TEAM`
- **MessageContentType**：`TEXT` · `EMOJI` · `IMAGE` · `LOCATION`

## 兴趣小队
- **TeamStatus**：`ACTIVE` · `DISSOLVED` 已解散 · `SUSPENDED` 已停用
- **TeamJoinType**：`PUBLIC` 公开加入 · `APPROVAL` 审核加入
- **TeamRole**：`OWNER` 队长 · `ADMIN` 管理员 · `MEMBER` 普通成员
- **TeamJoinRequestStatus**：`PENDING` · `APPROVED` · `REJECTED`
- **PointsReason**：`JOIN_ACTIVITY` 参与活动 · `POST_MOMENT` 发布动态 · `MOMENT_FEATURED` 动态被精选（可扩展）

## 后台
- **ModerationTargetType**：`ACTIVITY` · `TEAM`
- **ModerationAction**：`TAKE_DOWN` 下架 · `RESTORE` 恢复 · `SUSPEND` 停用
- **ReportTargetType**：`ACTIVITY` · `TEAM` · `USER` · `MOMENT`
- **ReportStatus**：`PENDING` · `HANDLED` · `DISMISSED`
