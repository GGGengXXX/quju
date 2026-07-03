import http from './http'

export interface UserVO {
  id: number; accountId?: string; email: string; nickname?: string; avatar?: string
  userType: string; status: string; gender?: string; birthday?: string
  signature?: string; reputation?: number; interestTags?: string[]
  privacySettings?: Record<string, boolean>
}

export const authApi = {
  register: (data: { email: string; password: string; userType: string; licenseUrl?: string; merchantName?: string }) =>
    http.post('/auth/register', data),
  activate: (token: string) => http.post('/auth/activate', { token }),
  login: (data: { email: string; password: string }) =>
    http.post<any, { token: string; expiresIn: number; user: UserVO }>('/auth/login', data),
  getMe: () => http.get<any, UserVO>('/users/me'),
  updateMe: (data: Partial<UserVO>) => http.put<any, UserVO>('/users/me', data),
  uploadImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.post<any, { url: string }>('/upload/image', form)
  },
}
