# ADR 0006：活动 AI/人工审核结果可见性（latestAudit 字段 + 审核流水端点）

- 状态：提议
- 日期：2026-07-04
- 决策者：平台核心组（待评审）

## 背景
需求 v2 §2.2「活动审核」要求 AI 自动内容安全审核 + 人工审核，处理结果为通过/驳回/要求修改。当前实现（`backend/module/activity/service/ActivityService.applySubmissionDecision`）已经在提交时调用 `ActivityAiService.auditActivity()`，把结果（`PASSED/REJECTED/TO_MANUAL`）连同「人数>50 转人工」规则映射到活动状态，并把每次结果写入 `activity_audit_log`（`audit_type=AI|MANUAL`, `result`, `reason`）。

**问题：`activity_audit_log` 没有任何契约端点/字段把它暴露出来。** 因此：
- 活动发起人看不到自己活动被驳回/转人工的**理由**，也看不到「AI 已自动通过」，体感上以为「没有 AI 审核」。
- 人工审核员在 `GET /admin/activities/pending-review` 里看不到 AI 为什么把这条转人工，缺少辅助决策依据。

补齐可见性需要新增契约（一个响应字段 + 一个只读端点）——按金律 #1 走 ADR + 平台核心组评审。

## 决策
1. **发起人可见**：给 `ActivityDetail` 增加只读字段 `latestAudit`（最近一次审核结果，**仅活动发起人本人可见**，非本人为 `null`；不含审核管理员标识）：
   ```yaml
   latestAudit:
     result: ActivityAuditResult   # PASSED|REJECTED|NEEDS_REVISION|TO_MANUAL
     reason: string
     auditType: ActivityAuditType  # AI|MANUAL
     createdAt: date-time
   ```
2. **审核员可见**：新增只读端点，返回完整审核时间线（含 `auditorAdminId`），供人工审核弹窗参考：
   ```yaml
   /admin/activities/{id}/audit-logs:
     get:
       tags: [admin]
       summary: 活动审核流水时间线(AI/人工，供审核员参考)
       responses: { '200': { $ref: '#/components/responses/AuditLogListResp' } }  # data: AuditLog[]
   ```
3. 把已在 `contracts/enums.md` 登记的 `ActivityAuditType`、`ActivityAuditResult` 补成 OpenAPI 命名 schema，并新增 `AuditLog` / `LatestAudit` schema。

数据来源：既有 `activity_audit_log` 表（`contracts/schema.sql:234`），**不新增表、不改 DDL**。`latestAudit` 取该活动 `created_at desc` 第一条；审核流水取全部 `created_at asc`。

## 后果（好处 / 代价 / 影响谁）
- 好处：补齐需求 §2.2 的审核反馈闭环——发起人知道为何被驳回/可修改重提，审核员看到 AI 判定辅助决策；正常工作的 AI 变得可见，消除「AI 没生效」的误解。
- 代价：新增 1 个响应字段 + 1 个只读端点 + 4 个 schema；后端两处查询（注意仅 owner 可见 `latestAudit` 的鉴权）。
- 影响谁：后端 `module/activity`、`module/admin`；前端 `views/activity`、`views/admin`（活动组 + 后台组）；契约由平台核心组合入。

## 备选方案
1. **给 `ActivityDetail` 直接内嵌完整 `auditLogs[]`（含 auditorAdminId）**：会把「仅管理员可见」的审核员身份发给普通发起人，越权且负载偏重，否决。
2. **给待审核列表项内嵌 AI 理由**：列表 N 条需批量带出，负载偏重且多数场景用不到，否决（审核员点开弹窗时按需拉取即可）。
3. **本 ADR 方案：发起人加轻量 `latestAudit` 字段 + 管理员独立审核流水端点**（推荐）。
