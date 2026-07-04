import http from './http'

export interface UserBrief {
  id: number
  nickname?: string
  avatar?: string
  userType?: string
  status?: string
}

export interface ActivityItem {
  id: number | null
  name: string
  intro?: string
  category: string
  tags: string[]
  coverImage?: string
  startTime?: string
  endTime?: string
  signupDeadline?: string
  city?: string
  address?: string
  lng?: number
  lat?: number
  capacity?: number
  fee?: number
  status: string
  phase: string
  signupCount: number
  creator?: UserBrief
  teamId?: number
}

export interface LatestAudit {
  result?: string
  reason?: string | null
  auditType?: string
  createdAt?: string
}

export interface ActivityDetail extends ActivityItem {
  mySignupStatus?: string
  waitlistCount?: number
  checkinCode?: string | null
  // 最近一次审核结果（AI/人工），仅活动发起人本人可见，非本人为 null
  latestAudit?: LatestAudit | null
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface TemplateItem {
  id: number
  name: string
  category: string
  defaultIntro?: string
  defaultCapacity?: number
  icon?: string
  isSystem: boolean
}

export interface ActivityPoint {
  id: number
  name: string
  category: string
  lng?: number
  lat?: number
  city?: string
  status: string
  phase: string
}

export interface ActivityUpsertReq {
  name: string
  intro?: string
  category?: string
  tags?: string[]
  coverImage?: string
  startTime?: string
  endTime?: string
  signupDeadline?: string
  city?: string
  address?: string
  lng?: number
  lat?: number
  capacity?: number
  fee?: number
  teamId?: number
  submit?: boolean
}

export interface ActivityDiscoverParams {
  tab?: string
  keyword?: string
  category?: string
  categories?: string
  status?: string
  city?: string
  startFrom?: string
  startTo?: string
  feeMin?: number
  feeMax?: number
  lng?: number
  lat?: number
  distanceKm?: number
  page?: number
  size?: number
}

export interface SignupResult {
  status: string
  waitlistPosition?: number | null
}

export interface SignupManageItem {
  signupId: number
  userId: number
  nickname?: string
  signupStatus: string
  checkedIn: boolean
  signupAt?: string
  checkinAt?: string
}

export interface WaitlistItem {
  id: number
  userId: number
  nickname?: string
  position: number
  status: string
  notifiedAt?: string
  confirmDeadline?: string
}

export interface WaitlistPage {
  waitlistCount: number
  list: WaitlistItem[]
}

export interface CheckinCodeResp {
  code: string
}

export interface SummaryImageItem {
  id: number
  imageUrl: string
  aiCategory?: string
  confirmedCategory?: string | null
  confirmed: boolean
}

export interface SummaryItem {
  id: number
  activityId: number
  authorId: number
  content?: string
  status: string
  images: SummaryImageItem[]
}

export interface ReviewItem {
  id: number
  userId: number
  nickname?: string
  rating: number
  content?: string
  createdAt?: string
}

const AI_TIMEOUT = 70000

export const activityApi = {
  templates: () => http.get<any, TemplateItem[]>('/activity-templates'),
  discover: (params: ActivityDiscoverParams) => http.get<any, PageResult<ActivityItem>>('/activities', { params }),
  mapPoints: (params: { minLng?: number; maxLng?: number; minLat?: number; maxLat?: number }) =>
    http.get<any, ActivityPoint[]>('/activities/map', { params }),
  detail: (id: number) => http.get<any, ActivityDetail>(`/activities/${id}`),
  create: (data: ActivityUpsertReq) => http.post<any, ActivityItem>('/activities', data, {
    timeout: data.submit ? AI_TIMEOUT : 15000,
  }),
  mine: (params: { status?: string; page?: number; size?: number } = {}) =>
    http.get<any, PageResult<ActivityItem>>('/activities/mine', { params }),
  update: (id: number, data: ActivityUpsertReq) => http.put<any, ActivityItem>(`/activities/${id}`, data, {
    timeout: data.submit ? AI_TIMEOUT : 15000,
  }),
  remove: (id: number) => http.delete<any, void>(`/activities/${id}`),
  submit: (id: number) => http.post<any, void>(`/activities/${id}/submit`, null, {
    timeout: AI_TIMEOUT,
  }),
  clone: (id: number) => http.post<any, ActivityItem>(`/activities/${id}/clone`),
  aiPlan: (data: { theme?: string; category?: string }) =>
    http.post<any, ActivityItem>('/activities/ai-plan', data, { timeout: AI_TIMEOUT }),
  signup: (id: number, data: { signupInfo?: Record<string, unknown>; safetyConfirmed?: boolean }) =>
    http.post<any, SignupResult>(`/activities/${id}/signup`, data),
  cancelSignup: (id: number) => http.delete<any, void>(`/activities/${id}/signup`),
  signups: (id: number, params: { page?: number; size?: number } = {}) =>
    http.get<any, PageResult<SignupManageItem>>(`/activities/${id}/signups`, { params }),
  waitlist: (id: number) => http.get<any, WaitlistPage>(`/activities/${id}/waitlist`),
  confirmWaitlist: (id: number) => http.post<any, void>(`/activities/${id}/waitlist/confirm`),
  generateCheckinCode: (id: number) => http.post<any, CheckinCodeResp>(`/activities/${id}/checkin-code`),
  checkin: (id: number, data: { code: string; lng?: number; lat?: number }) =>
    http.post<any, void>(`/activities/${id}/checkin`, data),
  publicCheckin: (id: number, data: { code: string; lng?: number; lat?: number }) =>
    http.post<any, void>(`/public/activities/${id}/checkin`, data),
  getSummary: (id: number) => http.get<any, SummaryItem>(`/activities/${id}/summary`, { silentError: true }),
  upsertSummary: (id: number, data: { content?: string; publish?: boolean }) =>
    http.post<any, SummaryItem>(`/activities/${id}/summary`, data),
  uploadSummaryImages: (id: number, imageUrls: string[]) =>
    http.post<any, SummaryImageItem[]>(`/activities/${id}/summary/images`, { imageUrls }),
  uploadSummaryImageFile: async (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.post<any, SummaryImageItem>(`/activities/${id}/summary/images/upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000,
    })
  },
  updateSummaryImage: (id: number, imageId: number, category: string) =>
    http.put<any, SummaryImageItem>(`/activities/${id}/summary/images/${imageId}`, { category }),
  deleteSummaryImage: (id: number, imageId: number) =>
    http.delete<any, void>(`/activities/${id}/summary/images/${imageId}`),
  reviews: (id: number, params: { page?: number; size?: number } = {}) =>
    http.get<any, PageResult<ReviewItem>>(`/activities/${id}/reviews`, { params }),
  upsertReview: (id: number, data: { rating: number; content?: string }) =>
    http.post<any, ReviewItem>(`/activities/${id}/reviews`, data),
}
