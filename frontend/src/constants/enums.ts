// 枚举 → 展示文案 / 标签颜色的共享字典。
// 值严格对齐 contracts/enums.md（唯一事实来源），改动请先改契约。
// 现仅接入活动页，后续 admin/social/team 页面可复用同一文件。

export type ElTagType = 'success' | 'warning' | 'info' | 'danger'

// ActivityStatus（发布生命周期）
export const ACTIVITY_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
  TAKEN_DOWN: '已下架',
  CANCELLED: '已取消',
}

export const ACTIVITY_STATUS_TAG_TYPE: Record<string, ElTagType> = {
  DRAFT: 'info',
  PENDING_REVIEW: 'warning',
  PUBLISHED: 'success',
  REJECTED: 'danger',
  TAKEN_DOWN: 'danger',
  CANCELLED: 'danger',
}

// ActivityPhase（时间相位，由 start/deadline/end 计算）
export const ACTIVITY_PHASE_LABELS: Record<string, string> = {
  NOT_STARTED: '未开始',
  SIGNUP_OPEN: '报名中',
  SIGNUP_CLOSED: '报名截止',
  ONGOING: '活动中',
  ENDED: '已结束',
}

// ActivityAuditType（AI / 人工审核）
export const ACTIVITY_AUDIT_TYPE_LABELS: Record<string, string> = {
  AI: 'AI 审核',
  MANUAL: '人工审核',
}

// ActivityAuditResult（审核结果）
export const ACTIVITY_AUDIT_RESULT_LABELS: Record<string, string> = {
  PASSED: '通过',
  REJECTED: '驳回',
  NEEDS_REVISION: '要求修改',
  TO_MANUAL: '转人工复核',
}

export const ACTIVITY_AUDIT_RESULT_TAG_TYPE: Record<string, ElTagType> = {
  PASSED: 'success',
  REJECTED: 'danger',
  NEEDS_REVISION: 'warning',
  TO_MANUAL: 'warning',
}

export function activityAuditTypeLabel(value?: string): string {
  return (value && ACTIVITY_AUDIT_TYPE_LABELS[value]) || value || '审核'
}

export function activityAuditResultLabel(value?: string): string {
  return (value && ACTIVITY_AUDIT_RESULT_LABELS[value]) || value || '-'
}

export function activityAuditResultTagType(value?: string): ElTagType {
  return (value && ACTIVITY_AUDIT_RESULT_TAG_TYPE[value]) || 'info'
}

// 未知值兜底返回原值，模仿 ActivityDiscover.vue 里现有的 categoryLabel。
export function activityStatusLabel(value?: string): string {
  return (value && ACTIVITY_STATUS_LABELS[value]) || value || '未知'
}

export function activityStatusTagType(value?: string): ElTagType {
  return (value && ACTIVITY_STATUS_TAG_TYPE[value]) || 'info'
}

export function activityPhaseLabel(value?: string): string {
  return (value && ACTIVITY_PHASE_LABELS[value]) || value || '-'
}
