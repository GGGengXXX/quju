import http from './http'

export type ReportTargetType = 'ACTIVITY' | 'TEAM'

export interface ReportCreateReq {
  targetType: ReportTargetType
  targetId: number
  reason: string
  detail?: string
}

export const reportApi = {
  create: (data: ReportCreateReq) =>
    http.post<any, void>('/reports', data),
}
