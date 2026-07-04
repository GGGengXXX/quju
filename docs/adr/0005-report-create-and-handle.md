# ADR 0005：举报功能补全（用户提交举报 + 后台处理）

- 状态：提议
- 日期：2026-07-04
- 决策者：平台核心组（待评审）

## 背景
举报此前是半成品：`report` 表、`Report` 实体、`ReportMapper` 与只读的 `GET /admin/reports` 均已存在，
但**没有用户提交入口，也没有后台处理动作**——`reportMapper.insert` 从未被调用，表永远为空，后台「举报管理」永远无数据。
要打通端到端（用户举报 → 后台可见 → 后台处理），需要在契约中新增两个端点。属契约变更，按金律 #1 走 ADR。

`report` 表现有列已足够，**无需改 schema**：create 写 `reporter_id/target_type/target_id/reason/detail`（`status` 默认 `PENDING`，`created_at` 自动）；
handle 更新 `status`(→`HANDLED`/`DISMISSED`)、`handler_admin_id`、`handled_at`。本次范围仅活动(ACTIVITY)与小队(TEAM)两类对象。

## 决策
1. 新增用户端 `POST /v1/reports`（需登录）：
   ```yaml
   requestBody: { targetType: ACTIVITY|TEAM, targetId: int64, reason: string(<=64), detail?: string(<=500) }
   ```
   服务端取当前用户为 `reporter_id`；同一用户对同一对象存在 `PENDING` 举报时拒绝重复提交（`CONFLICT`）；`targetType` 越界返回 `BAD_REQUEST`。

2. 新增后台 `POST /v1/admin/reports/{id}/handle`：
   ```yaml
   requestBody: { action: DISMISS|RESOLVE|TAKEDOWN, reason?: string }
   ```
   - `DISMISS` → 举报置 `DISMISSED`；`RESOLVE` → 置 `HANDLED`（仅确认，不动目标）；
   - `TAKEDOWN` → 置 `HANDLED` 并对目标执行既有下架/停用（`reason` 必填，否则 `6002`）：
     `TEAM`→复用 `AdminTeamService.suspendTeam`（→SUSPENDED），`ACTIVITY`→复用 `AdminActivityService.takedown`（→TAKEN_DOWN），均会记 `moderation_action`。
   - 处理后写 `handler_admin_id` 与 `handled_at`；举报非 `PENDING` 时返回 `CONFLICT`，举报不存在返回 `NOT_FOUND`。

3. 既有 `GET /admin/reports` 保留（实现从 `AdminTeamController`/`Service` 迁至新的 `AdminReportController`/`Service`）。
   错误码全部复用现有码（`1000/1003/1004/6002`），**不新增错误码**，不改 `error-codes.md`、不改 `schema.sql`。

## 后果（好处 / 代价 / 影响谁）
- 好处：举报端到端可用；后台「举报管理」有真实数据并可一键处置（含联动下架/停用），复用既有 moderation 审计。
- 代价：契约新增 2 个端点；新增 `module/report`，`Report` 实体/Mapper 从 `module/admin` 迁入该模块（后台侧改引用）。向后兼容。
- 影响谁：新 `module/report`（用户提交）、`module/admin`（处理，复用小队/活动下架）、前端活动/小队页 + 后台举报页；契约由平台核心组合入。

## 备选方案
1. 举报只读、不做提交/处理：维持现状即半成品，否决。
2. 处理举报时不联动目标、仅改状态：无法真正处置违规内容，后台还要再手动去各模块操作，体验差；本 ADR 支持可选 `TAKEDOWN` 联动（推荐）。
3. 为「后台处理备注」加列：需改 schema，收益低，暂不做（可后续 ADR）。
