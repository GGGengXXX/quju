import http from './http'

// ---- Types ----

export interface FriendRequestVO {
  id: number
  fromUserId: number
  fromNickname?: string
  fromAvatar?: string
  toUserId: number
  status: string
  source?: string
  message?: string
  createdAt?: string
}

export interface FriendVO {
  userId: number
  nickname?: string
  avatar?: string
  userType?: string
  remark?: string
  groupTag?: string
  friendSince?: string
}

export interface FollowVO {
  userId: number
  nickname?: string
  avatar?: string
  followedAt?: string
}

export interface BlockVO {
  userId: number
  nickname?: string
  avatar?: string
  blockedAt?: string
}

export interface MessageVO {
  id: number
  scope: string
  senderId: number
  receiverId?: number
  teamId?: number
  contentType: string
  content: string
  isRead?: boolean
  isRecalled?: boolean
  forwardedFromId?: number
  createdAt?: string
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

// ---- API ----

export const socialApi = {
  // 好友申请
  sendFriendRequest: (data: { toUserId: number; source?: string; message?: string }) =>
    http.post<any, void>('/friend-requests', data),

  getFriendRequests: (params: { page?: number; size?: number }) =>
    http.get<any, PageResult<FriendRequestVO>>('/friend-requests', { params }),

  acceptRequest: (id: number) =>
    http.post<any, void>(`/friend-requests/${id}/accept`),

  rejectRequest: (id: number) =>
    http.post<any, void>(`/friend-requests/${id}/reject`),

  // 好友
  getFriends: () =>
    http.get<any, FriendVO[]>('/friends'),

  updateFriend: (userId: number, data: { remark?: string; groupTag?: string }) =>
    http.put<any, void>(`/friends/${userId}`, data),

  deleteFriend: (userId: number) =>
    http.delete<any, void>(`/friends/${userId}`),

  // 关注
  follow: (userId: number) =>
    http.post<any, void>(`/follows/${userId}`),

  unfollow: (userId: number) =>
    http.delete<any, void>(`/follows/${userId}`),

  getFollows: (params: { type: 'FOLLOWING' | 'FOLLOWERS' }) =>
    http.get<any, FollowVO[]>('/follows', { params }),

  // 黑名单
  block: (userId: number) =>
    http.post<any, void>(`/blocks/${userId}`),

  unblock: (userId: number) =>
    http.delete<any, void>(`/blocks/${userId}`),

  getBlocks: () =>
    http.get<any, BlockVO[]>('/blocks'),

  // 消息
  getMessages: (params: { scope: string; peerId: number; page?: number; size?: number }) =>
    http.get<any, PageResult<MessageVO>>('/messages', { params }),

  sendMessage: (data: { scope: string; peerId: number; contentType: string; content: string }) =>
    http.post<any, MessageVO>('/messages', data),

  markRead: (data: { scope: string; peerId: number }) =>
    http.post<any, void>('/messages/read', data),

  recallMessage: (id: number) =>
    http.post<any, void>(`/messages/${id}/recall`),

  forwardMessage: (id: number, data: { scope: string; peerId: number }) =>
    http.post<any, MessageVO>(`/messages/${id}/forward`, data),
}
