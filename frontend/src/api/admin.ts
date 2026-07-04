import http from './http'

// ---- Types ----

export interface AdminLoginReq {
  username: string
  password: string
}

export interface AdminLoginResp {
  token: string
  expiresIn: number
  username: string
}

export interface ChangePasswordReq {
  oldPassword: string
  newPassword: string
}

export interface AdminUserListVO {
  id: number
  email: string
  nickname?: string
  userType: string
  status: string
  createdAt?: string
}

export interface AdminUserActivityBriefVO {
  id: number
  name: string
  status: string
  startTime?: string
}

export interface AdminUserTeamBriefVO {
  id: number
  name: string
  status: string
  memberCount?: number
}

export interface AdminUserDetailVO {
  id: number
  email: string
  nickname?: string
  avatar?: string
  userType: string
  status: string
  gender?: string
  birthday?: string
  signature?: string
  reputation?: number
  createdAt?: string
  updatedAt?: string
  activities?: AdminUserActivityBriefVO[]
  teams?: AdminUserTeamBriefVO[]
}

export interface BanReq {
  reason: string
  banUntil?: string | null
}

export interface MerchantAppVO {
  id: number
  userId: number
  merchantName?: string
  nickname?: string
  focusFields?: string
  licenseUrl?: string
  auditStatus: string
  auditReason?: string
  createdAt?: string
}

export interface MerchantReviewReq {
  action: 'APPROVE' | 'REJECT'
  reason?: string
}

export interface AdminActivityListVO {
  id: number
  creatorId: number
  creatorNickname?: string
  name: string
  category?: string
  status: string
  startTime?: string
  createdAt?: string
}

export interface ActivityReviewReq {
  result: 'PASSED' | 'REJECTED' | 'NEEDS_REVISION'
  reason?: string
}

export interface ReasonReq {
  reason: string
}

export interface AdminTeamListVO {
  id: number
  ownerId: number
  name: string
  status: string
  memberCount?: number
  createdAt?: string
}

export interface ReportVO {
  id: number
  reporterId: number
  targetType: string
  targetId: number
  reason: string
  detail?: string
  status: string
  createdAt?: string
}

export interface ReportHandleReq {
  action: 'DISMISS' | 'RESOLVE' | 'TAKEDOWN'
  reason?: string
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

// ---- API ----

export const adminApi = {
  login: (data: AdminLoginReq) =>
    http.post<any, AdminLoginResp>('/admin/login', data),

  changePassword: (data: ChangePasswordReq) =>
    http.put<any, void>('/admin/password', data),

  // 用户管理
  getUsers: (params: { keyword?: string; userType?: string; status?: string; page?: number; size?: number }) =>
    http.get<any, PageResult<AdminUserListVO>>('/admin/users', { params }),

  getUserDetail: (id: number) =>
    http.get<any, AdminUserDetailVO>(`/admin/users/${id}`),

  banUser: (id: number, data: BanReq) =>
    http.post<any, void>(`/admin/users/${id}/ban`, data),

  unbanUser: (id: number) =>
    http.post<any, void>(`/admin/users/${id}/unban`),

  // 商家审核
  getMerchantApplications: (params: { status?: string; page?: number; size?: number }) =>
    http.get<any, PageResult<MerchantAppVO>>('/admin/merchant-applications', { params }),

  reviewMerchant: (id: number, data: MerchantReviewReq) =>
    http.post<any, void>(`/admin/merchant-applications/${id}`, data),

  // 活动管理
  getActivities: (params: { status?: string; keyword?: string; page?: number; size?: number }) =>
    http.get<any, PageResult<AdminActivityListVO>>('/admin/activities', { params }),

  getPendingReviewActivities: (params: { page?: number; size?: number }) =>
    http.get<any, PageResult<AdminActivityListVO>>('/admin/activities/pending-review', { params }),

  reviewActivity: (id: number, data: ActivityReviewReq) =>
    http.post<any, void>(`/admin/activities/${id}/review`, data),

  takedownActivity: (id: number, data: ReasonReq) =>
    http.post<any, void>(`/admin/activities/${id}/takedown`, data),

  restoreActivity: (id: number) =>
    http.post<any, void>(`/admin/activities/${id}/restore`),

  // 小队管理
  getTeams: (params: { keyword?: string; status?: string; page?: number; size?: number }) =>
    http.get<any, PageResult<AdminTeamListVO>>('/admin/teams', { params }),

  suspendTeam: (id: number, data: ReasonReq) =>
    http.post<any, void>(`/admin/teams/${id}/suspend`, data),

  restoreTeam: (id: number) =>
    http.post<any, void>(`/admin/teams/${id}/restore`),

  // 举报
  getReports: (params: { status?: string; page?: number; size?: number }) =>
    http.get<any, PageResult<ReportVO>>('/admin/reports', { params }),

  handleReport: (id: number, data: ReportHandleReq) =>
    http.post<any, void>(`/admin/reports/${id}/handle`, data),
}
