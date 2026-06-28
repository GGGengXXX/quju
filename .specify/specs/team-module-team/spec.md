# 规格 Spec：R4 兴趣小队模块

- **模块**：team
- **负责人**：付东淏
- **关联需求**：`docs/requirements-breakdown.md` 中 `R4.1`–`R4.8`
- **分支**：`feat/team-module-team`

## 1. 目标（一句话）
这个功能让已登录用户能够创建、发现、加入和管理兴趣小队，并在小队内完成公告、投票、文件、相册、动态和积分互动。

## 2. 验收标准（可测试，逐条）
- [ ] 给定已登录用户提交合法小队信息，当调用创建接口时，则创建 `team`、`team_tag`、`team_member` 记录且创建者成为 `OWNER`。
- [ ] 给定公开小队未满员且用户不在队内、未被拉黑，当用户加入时，则直接成为成员且 `member_count` 增加。
- [ ] 给定审核制小队未满员且用户不在队内，当用户加入时，则创建 `team_join_request=PENDING`，不直接入队。
- [ ] 给定队长/管理员查看加入申请，当批准时，则申请变为 `APPROVED` 且成员入队；拒绝时状态变为 `REJECTED`。
- [ ] 给定队长调用解散接口，则小队状态变为 `DISSOLVED`，发现页不再展示，成员无法继续加入或操作。
- [ ] 给定成员访问公告、投票、文件、相册、动态、积分榜时，则能看到当前小队数据；给定队长/管理员操作对应管理接口时，则能成功写入或删除数据。
- [ ] 给定普通成员尝试设置角色、处理申请、发布公告、删除相册、精选动态等管理操作时，则返回 `5004 no_team_permission`。
- [ ] 边界/异常：小队已满返回 `5000`；已解散/停用返回 `5001`；重复加入返回 `5002`；审核制直接加入提示 `5003`；队长退出返回 `5005`。
- [ ] 非功能：小队发现列表与动态列表支持分页；所有接口保持统一响应信封。

## 3. 涉及的契约（必须先确认/补充 contracts/）
- **端点**：`/v1/teams`、`/v1/teams/{id}`、`/v1/teams/{id}/join`、`/v1/teams/{id}/leave`、`/v1/teams/{id}/join-requests*`、`/v1/teams/{id}/members*`、`/v1/teams/{id}/announcements`、`/v1/teams/{id}/votes*`、`/v1/teams/{id}/files*`、`/v1/teams/{id}/album*`、`/v1/teams/{id}/moments*`、`/v1/teams/{id}/points`、`/v1/teams/{id}/activities`
- **DB 表/列**：`team`、`team_tag`、`team_member`、`team_join_request`、`team_announcement`、`team_vote`、`team_vote_record`、`team_file`、`team_album_photo`、`team_moment`、`team_points_log`，以及只读查询 `activity.team_id`
- **枚举 / 错误码**：`TeamStatus`、`TeamJoinType`、`TeamRole`、`TeamJoinRequestStatus`、`PointsReason`；`5000`–`5005`
- 若需改契约：本次不改 `contracts/`，严格按既有定义实现。

## 4. 范围
- **做**：后端完整实现小队模块接口；前端提供小队发现/详情/创建与核心管理交互；补充必要测试与验证。
- **不做（Out of scope）**：修改 `contracts/`；改动其他业务模块逻辑；新增 OSS/IM/活动模块联动能力；自动化给活动参与发积分（仅消费已有 `team_points_log` 数据）。

## 5. UI / 交互（前端）
- 页面 / 组件：`frontend/src/views/team/TeamHub.vue`
- 关键交互、状态、空/错/加载态：小队列表搜索/分页、创建小队弹窗、详情抽屉、成员与申请管理、公告/投票/文件/相册/动态/积分榜分区展示。

## 6. 端到端验证步骤
1. 注册或登录一个普通用户，创建一个公开小队。
2. 用另一用户搜索该小队并加入，验证成员列表和人数变化。
3. 创建一个审核制小队，用另一用户申请加入，再由队长批准。
4. 队长发布公告、投票、文件、相册图片和精选动态，成员端刷新后可见。
5. 普通成员尝试执行管理操作，验证收到权限错误码。
6. 队长解散小队后，再次搜索或加入，验证返回已解散错误。
