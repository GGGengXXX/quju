# 统一错误码 / 响应码

所有后端业务接口返回信封 `{ code, message, data }`。`code = 0` 成功，非 0 为错误。
**新增错误码必须登记在本表**，前后端共用。区间划分如下，避免冲突。

| 区间 | 归属 | 负责人 |
|---|---|---|
| `0` | 成功 | — |
| `1000–1999` | 通用 / 鉴权 / 参数 | 平台核心组 |
| `2000–2999` | 用户 / 商家 | 用户组 |
| `3000–3999` | 活动（创建/审核/发现/报名/签到/总结） | 活动组 |
| `4000–4999` | 社交 / 好友 / 关注 / IM | 社交组 |
| `5000–5999` | 兴趣小队 | 小队负责人 |
| `6000–6999` | 后台管理 | 后台组 |

## 已定义（通用区 1000–1999）

| code | message | 含义 | HTTP |
|---|---|---|---|
| 0 | success | 成功 | 200 |
| 1000 | bad_request | 参数校验失败 | 200 |
| 1001 | unauthorized | 未登录 / token 无效或过期 | 200 |
| 1002 | forbidden | 无权限 | 200 |
| 1003 | not_found | 资源不存在 | 200 |
| 1004 | conflict | 状态冲突（如重复操作） | 200 |
| 1005 | rate_limited | 触发限流 | 200 |
| 1500 | internal_error | 服务器内部错误 | 200 |
| 1501 | third_party_error | 第三方（AI/地图/邮件/OSS）调用失败 | 200 |

> 约定：HTTP 状态码一律 200（除网关/框架级错误），业务结果以信封 `code` 表达。
> `<<TODO: 若团队希望用 HTTP 状态码语义（401/403/404…）而非统一 200，请在 ADR 中决定并改此约定>>`

## 用户 / 商家（2000–2999）
| code | message | 含义 |
|---|---|---|
| 2000 | email_already_registered | 邮箱已注册 |
| 2001 | nickname_taken | 昵称已被占用 |
| 2002 | account_not_activated | 账号未激活 |
| 2003 | invalid_credentials | 邮箱或密码错误 |
| 2004 | activation_token_invalid | 激活/重置令牌无效或过期 |
| 2005 | merchant_license_required | 商家需上传营业执照 |
| 2006 | merchant_not_approved | 商家身份未通过审核 |

## 活动（3000–3999）
| code | message | 含义 |
|---|---|---|
| 3000 | activity_full | 活动名额已满（进入候补） |
| 3001 | signup_deadline_passed | 已过报名截止 |
| 3002 | already_signed_up | 已报名 |
| 3003 | not_signed_up | 未报名 |
| 3004 | activity_not_published | 活动未发布/不可报名 |
| 3005 | signup_check_failed | 报名校验未通过（信誉/年龄等） |
| 3006 | waitlist_confirm_expired | 候补确认超时 |
| 3007 | checkin_code_invalid | 签到码无效 |
| 3008 | checkin_location_too_far | 签到位置不在活动地点附近 |
| 3009 | review_window_closed | 评价入口已关闭 |
| 3010 | not_activity_owner | 非活动发起人 |

## 社交 / IM（4000–4999）
| code | message | 含义 |
|---|---|---|
| 4000 | already_friends | 已是好友 |
| 4001 | blocked_relation | 存在黑名单关系，操作受限 |
| 4002 | friend_request_duplicate | 重复的好友申请 |
| 4003 | not_friends | 非好友关系 |
| 4004 | message_recall_expired | 超过 2 分钟，不可撤回 |

## 兴趣小队（5000–5999）
| code | message | 含义 |
|---|---|---|
| 5000 | team_full | 小队人数已满 |
| 5001 | team_dissolved_or_suspended | 小队已解散/停用 |
| 5002 | already_team_member | 已是小队成员 |
| 5003 | join_needs_approval | 该小队需审核加入 |
| 5004 | no_team_permission | 无小队操作权限（需队长/管理员） |
| 5005 | owner_cannot_leave | 队长需先转让或解散 |

## 后台管理（6000–6999）
| code | message | 含义 |
|---|---|---|
| 6000 | ban_reason_required | 封禁需填写原因与期限 |
| 6001 | reject_reason_required | 驳回需填写原因 |
| 6002 | takedown_reason_required | 下架/停用需填写原因 |
