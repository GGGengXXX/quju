import http from './http'

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface UserBrief {
  id: number
  nickname?: string
  avatar?: string
  userType?: string
  status?: string
}

export interface TeamSummary {
  id: number
  name: string
  intro?: string
  avatar?: string
  tags: string[]
  joinType: 'PUBLIC' | 'APPROVAL'
  capacity: number
  memberCount: number
  status: string
  owner: UserBrief
  myRole?: string
  joined?: boolean
  createdAt?: string
}

export interface TeamDetail extends TeamSummary {}
export interface TeamMemberItem {
  userId: number
  nickname?: string
  avatar?: string
  userType?: string
  status?: string
  role: string
  points: number
  joinedAt?: string
}
export interface TeamJoinRequestItem {
  id: number
  userId: number
  nickname?: string
  avatar?: string
  status: string
  createdAt?: string
  handledAt?: string
  handlerId?: number
}
export interface TeamAnnouncementItem {
  id: number
  authorId: number
  authorName?: string
  content: string
  createdAt?: string
}
export interface TeamVoteItem {
  id: number
  title: string
  options: string[]
  counts: number[]
  multiChoice: boolean
  deadline?: string
  createdAt?: string
  creatorId: number
  creatorName?: string
  myOptionIndexes: number[]
}
export interface TeamFileItem {
  id: number
  uploaderId: number
  uploaderName?: string
  fileName: string
  fileUrl: string
  fileSize?: number
  createdAt?: string
}
export interface TeamAlbumPhotoItem {
  id: number
  uploaderId: number
  uploaderName?: string
  imageUrl: string
  createdAt?: string
}
export interface TeamImageUploadItem {
  url: string
  fileName: string
  fileSize: number
}
export interface TeamFileUploadItem {
  url: string
  fileName: string
  fileSize: number
}
export interface TeamMomentItem {
  id: number
  authorId: number
  authorName?: string
  authorAvatar?: string
  content?: string
  images: string[]
  featured: boolean
  createdAt?: string
}
export interface TeamPointItem {
  userId: number
  nickname?: string
  avatar?: string
  points: number
  rank: number
}
export interface ActivityItem {
  id: number
  name: string
  intro?: string
  category?: string
  city?: string
  address?: string
  startTime?: string
  signupDeadline?: string
  phase?: string
  creator?: UserBrief
}

export const teamApi = {
  searchTeams: (params: { keyword?: string; tag?: string; page?: number; size?: number }) =>
    http.get<any, PageResult<TeamSummary>>('/teams', { params }),
  createTeam: (data: { name: string; intro?: string; avatar?: string; tags?: string[]; joinType: string; capacity?: number }) =>
    http.post<any, TeamDetail>('/teams', data),
  getTeam: (id: number) => http.get<any, TeamDetail>(`/teams/${id}`),
  dissolveTeam: (id: number) => http.delete(`/teams/${id}`),
  joinTeam: (id: number) => http.post<any, { status: string; requestId?: number }>(`/teams/${id}/join`),
  leaveTeam: (id: number) => http.post(`/teams/${id}/leave`),
  listMembers: (id: number) => http.get<any, TeamMemberItem[]>(`/teams/${id}/members`),
  updateRole: (id: number, userId: number, role: string) => http.put(`/teams/${id}/members/${userId}`, { role }),
  removeMember: (id: number, userId: number) => http.delete(`/teams/${id}/members/${userId}`),
  listJoinRequests: (id: number) => http.get<any, TeamJoinRequestItem[]>(`/teams/${id}/join-requests`),
  handleJoinRequest: (id: number, reqId: number, action: 'APPROVE' | 'REJECT') => http.post(`/teams/${id}/join-requests/${reqId}`, { action }),
  listAnnouncements: (id: number) => http.get<any, TeamAnnouncementItem[]>(`/teams/${id}/announcements`),
  createAnnouncement: (id: number, content: string) => http.post<any, TeamAnnouncementItem>(`/teams/${id}/announcements`, { content }),
  listVotes: (id: number) => http.get<any, TeamVoteItem[]>(`/teams/${id}/votes`),
  createVote: (id: number, data: { title: string; options: string[]; multiChoice?: boolean; deadline?: string }) => http.post<any, TeamVoteItem>(`/teams/${id}/votes`, data),
  castVote: (id: number, voteId: number, optionIndexes: number[]) => http.post(`/teams/${id}/votes/${voteId}/cast`, { optionIndexes }),
  listFiles: (id: number) => http.get<any, TeamFileItem[]>(`/teams/${id}/files`),
  createFile: (id: number, data: { fileName: string; fileUrl: string; fileSize?: number }) => http.post<any, TeamFileItem>(`/teams/${id}/files`, data),
  uploadFile: async (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<any, TeamFileUploadItem>(`/teams/${id}/files/upload`, formData)
  },
  deleteFile: (id: number, fileId: number) => http.delete(`/teams/${id}/files/${fileId}`),
  listAlbum: (id: number) => http.get<any, TeamAlbumPhotoItem[]>(`/teams/${id}/album`),
  uploadImage: async (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<any, TeamImageUploadItem>(`/teams/${id}/images/upload`, formData)
  },
  createAlbum: (id: number, imageUrls: string[]) => http.post<any, TeamAlbumPhotoItem[]>(`/teams/${id}/album`, { imageUrls }),
  deleteAlbum: (id: number, photoId: number) => http.delete(`/teams/${id}/album/${photoId}`),
  listMoments: (id: number, params: { page?: number; size?: number }) => http.get<any, PageResult<TeamMomentItem>>(`/teams/${id}/moments`, { params }),
  createMoment: (id: number, data: { content?: string; images?: string[] }) => http.post<any, TeamMomentItem>(`/teams/${id}/moments`, data),
  featureMoment: (id: number, momentId: number) => http.post(`/teams/${id}/moments/${momentId}/feature`),
  listPoints: (id: number) => http.get<any, TeamPointItem[]>(`/teams/${id}/points`),
  listActivities: (id: number, params: { page?: number; size?: number }) => http.get<any, PageResult<ActivityItem>>(`/teams/${id}/activities`, { params }),
}
