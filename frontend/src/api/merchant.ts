import http from './http'

export interface MerchantVO {
  id: number
  userId: number
  merchantName: string
  nickname?: string
  focusFields?: string
  licenseUrl?: string
  auditStatus: string // PENDING | APPROVED | REJECTED
  auditReason?: string
  createdAt?: string
}

export interface MerchantUpdateReq {
  merchantName?: string
  nickname?: string
  focusFields?: string
  licenseUrl?: string
}

export const merchantApi = {
  // 我的商家资料
  getMyProfile: () => http.get<any, MerchantVO>('/merchants/me'),
  // 更新商家资料（名称/昵称/关注领域）
  updateProfile: (data: MerchantUpdateReq) => http.put<any, MerchantVO>('/merchants/me', data),
}
