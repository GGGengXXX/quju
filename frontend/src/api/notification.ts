import http from './http'

export interface NotificationItem {
  id: number
  userId: number
  type: string
  title: string
  content?: string
  isRead: boolean
  refType?: string
  refId?: number
  createdAt?: string
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export const notificationApi = {
  list: (params: { isRead?: boolean; page?: number; size?: number }) =>
    http.get<any, PageResult<NotificationItem>>('/notifications', { params }),

  unreadCount: () =>
    http.get<any, number>('/notifications/unread-count'),

  markRead: (id: number) =>
    http.post<any, void>(`/notifications/${id}/read`),

  markAllRead: () =>
    http.post<any, void>('/notifications/read-all'),
}
