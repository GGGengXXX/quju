# 10 人分工与文件归属

> 原则：**垂直切片** —— 每人对自己负责的功能从后端到前端端到端负责，配清晰的"文件归属"边界，最大化并行、最小化冲突。
> 平台核心组 2 人先行冻结契约，之后各组并行。成员分配见下表与 `TEAM.md`（建议，可调）。

## 分组建议（2 人/组 × 5）

| 组 | 成员 | 负责需求 | 后端包(只改这些) | 前端目录(只改这些) | 负责的表 |
|---|---|---|---|---|---|
| **平台核心** | 杨佳宇轩, 唐皓涵 | 横切 + 各带一个轻 feature | `common/` `config/` `module/*`(基类) AI 网关 第三方适配 | `api/http.ts` `router` 全局 `components` 地图封装 | user/admin 基础 + 公共 |
| **用户 + 后台** | 付东淏, 莫淼鑫 | R1.* + R5.* | `module/user` `module/admin` | `views/user` `views/admin` | user, merchant_profile, email_token, admin, user_ban |
| **活动 a（创建/审核/AI）** | 梁祎卓, 陈润 | R2.1–R2.6 | `module/activity`(创建/审核子域) | `views/activity`(创建/审核) | activity, activity_tag, activity_template, activity_audit_log |
| **活动 b（发现/报名/签到/总结）** | 庄耿雄, 靳远畅 | R2.7–R2.18 | `module/activity`(发现/报名子域) | `views/activity`(发现/报名/地图) | activity_signup, activity_waitlist, activity_checkin, activity_summary(+image), activity_review |
| **社交（好友/小队/IM）** | 倪晓帆, 程宇繁 | R3.* + R4.* | `module/social` `module/team` `module/chat` | `views/social` `views/team` `views/chat` | friend_request, friendship, follow, user_block, message, team*, team_member… |

> 活动 a/b 共用 `module/activity` 与 `views/activity`，需进一步把**子包/子目录**切清楚（如 `activity/create`、`activity/discover`），写进各自 spec，避免互踩。平台核心组在 Day0–2 划定子包边界。

## 文件归属规则
- 你**只改**自己组的后端包 + 前端目录 + 负责的表。
- 跨组依赖通过**契约**（端点/类型），不直接读改对方代码。
- 改公共/契约 → 走 ADR + 平台核心组评审（CodeArts 分支保护 + 必选评审强制，见 `codearts-and-cicd.md`）。

## 端口 / DB schema 分配
见 `dev-on-server-runbook.md` §2 的分配表（每人一段端口 + 独立 schema）。

## 角色（建议）
- **技术负责人/平台核心 ×2**：守契约、CI、横切、code review 把关。
- 各组组长：本组 spec 质量 + 子包边界。
- 技术负责人 = 杨佳宇轩（DeNeRATe-cool）；各组组长由组内商定。
