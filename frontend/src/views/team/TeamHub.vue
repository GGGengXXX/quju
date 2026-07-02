<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { teamApi, type TeamDetail, type TeamSummary, type TeamMemberItem, type TeamJoinRequestItem, type TeamAnnouncementItem, type TeamVoteItem, type TeamFileItem, type TeamAlbumPhotoItem, type TeamMomentItem, type TeamPointItem, type ActivityItem } from '../../api/team'

const loading = ref(false)
const keyword = ref('')
const tag = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const teams = ref<TeamSummary[]>([])
const selectedTeam = ref<TeamDetail | null>(null)
const drawerVisible = ref(false)
const detailsLoading = ref(false)
const activeTab = ref('members')

const members = ref<TeamMemberItem[]>([])
const joinRequests = ref<TeamJoinRequestItem[]>([])
const announcements = ref<TeamAnnouncementItem[]>([])
const votes = ref<TeamVoteItem[]>([])
const files = ref<TeamFileItem[]>([])
const album = ref<TeamAlbumPhotoItem[]>([])
const moments = ref<TeamMomentItem[]>([])
const points = ref<TeamPointItem[]>([])
const activities = ref<ActivityItem[]>([])
const voteSelections = reactive<Record<number, number[]>>({})

const canManage = computed(() => ['OWNER', 'ADMIN'].includes(selectedTeam.value?.myRole || ''))
const isOwner = computed(() => selectedTeam.value?.myRole === 'OWNER')

const createDialogVisible = ref(false)
const createForm = reactive({ name: '', intro: '', avatar: '', tags: '', joinType: 'PUBLIC', capacity: 100 })
const announcementText = ref('')
const announcementInputRef = ref<any>(null)
const mentionMenuVisible = ref(false)
const mentionQuery = ref('')
const mentionStartIndex = ref(-1)
const mentionDropdownStyle = ref<Record<string, string>>({})
const hideMentionTimer = ref<number | null>(null)
const voteForm = reactive({ title: '', optionsText: '', multiChoice: false, deadline: '' })
const fileDialogVisible = ref(false)
const fileDraft = ref<LocalFileDraft | null>(null)
const fileUploading = ref(false)
const albumDrafts = ref<LocalImageDraft[]>([])
const albumUploading = ref(false)
const momentForm = reactive({ content: '' })
const momentDrafts = ref<LocalImageDraft[]>([])
const momentPublishing = ref(false)

interface LocalImageDraft {
  id: string
  file: File
  previewUrl: string
}

interface LocalFileDraft {
  file: File
  id: string
}

const mentionCandidates = computed(() => {
  if (!canManage.value) return [] as Array<{ key: string; label: string; mentionValue: string; type: 'all' | 'member' }>
  const query = mentionQuery.value.trim().toLowerCase()
  const memberItems = members.value
    .filter(member => member.nickname)
    .filter(member => !query || member.nickname!.toLowerCase().includes(query))
    .map(member => ({
      key: `member-${member.userId}`,
      label: member.nickname || '',
      mentionValue: member.nickname || '',
      type: 'member' as const,
    }))

  const everyone = !query || '所有人'.includes(query)
    ? [{ key: 'all', label: '所有人', mentionValue: '所有人', type: 'all' as const }]
    : []

  const dedupedMembers = memberItems.filter((item, index, list) => list.findIndex(candidate => candidate.mentionValue === item.mentionValue) === index)
  return [...everyone, ...dedupedMembers].slice(0, 8)
})

function splitLines(value: string) {
  return value.split(/\n|,/).map(item => item.trim()).filter(Boolean)
}

function isWindowsLocalPath(value: string) {
  return /^[A-Za-z]:[\\/]/.test(value)
}

function isUnixLocalPath(value: string) {
  return /^\/(Users|home|var|tmp|opt|private)\//.test(value)
}

function looksLikeHostPath(value: string) {
  return /^(localhost|127\.0\.0\.1|\d{1,3}(?:\.\d{1,3}){3}|[A-Za-z0-9.-]+\.[A-Za-z]{2,})(:\d+)?(\/.*)?$/.test(value)
}

function normalizeUrl(raw: string) {
  const value = raw.trim()
  if (!value) return ''
  if (/^(https?:|data:|blob:|file:)/i.test(value)) return value
  if (value.startsWith('//')) return `${window.location.protocol}${value}`
  if (isWindowsLocalPath(value)) return `file:///${value.replace(/\\/g, '/')}`
  if (isUnixLocalPath(value)) return `file://${value}`
  if (looksLikeHostPath(value)) return `http://${value}`
  return value
}

function resetTeamCollections() {
  members.value = []
  joinRequests.value = []
  announcements.value = []
  votes.value = []
  files.value = []
  album.value = []
  moments.value = []
  points.value = []
  activities.value = []
}

function revokeDrafts(drafts: LocalImageDraft[]) {
  drafts.forEach(draft => URL.revokeObjectURL(draft.previewUrl))
}

function replaceDrafts(target: typeof albumDrafts, files: FileList | null) {
  revokeDrafts(target.value)
  const nextDrafts = Array.from(files || [])
    .filter(file => file.type.startsWith('image/'))
    .map((file, index) => ({
      id: `${file.name}-${file.size}-${file.lastModified}-${index}`,
      file,
      previewUrl: URL.createObjectURL(file),
    }))
  target.value = nextDrafts
}

function handleAlbumSelection(event: Event) {
  replaceDrafts(albumDrafts, (event.target as HTMLInputElement).files)
}

function handleMomentSelection(event: Event) {
  replaceDrafts(momentDrafts, (event.target as HTMLInputElement).files)
}

function clearAlbumDrafts() {
  revokeDrafts(albumDrafts.value)
  albumDrafts.value = []
}

function clearMomentDrafts() {
  revokeDrafts(momentDrafts.value)
  momentDrafts.value = []
}

function handleFileSelection(event: Event) {
  const [file] = Array.from((event.target as HTMLInputElement).files || [])
  fileDraft.value = file ? { file, id: `${file.name}-${file.size}-${file.lastModified}` } : null
}

function clearFileDraft() {
  fileDraft.value = null
}

async function uploadDraftImages(teamId: number, drafts: LocalImageDraft[]) {
  const uploads = await Promise.all(drafts.map(draft => teamApi.uploadImage(teamId, draft.file)))
  return uploads.map(item => item.url)
}

function clearHideMentionTimer() {
  if (hideMentionTimer.value !== null) {
    window.clearTimeout(hideMentionTimer.value)
    hideMentionTimer.value = null
  }
}

function hideMentionMenu() {
  clearHideMentionTimer()
  mentionMenuVisible.value = false
  mentionQuery.value = ''
  mentionStartIndex.value = -1
}

function scheduleHideMentionMenu() {
  clearHideMentionTimer()
  hideMentionTimer.value = window.setTimeout(() => {
    hideMentionMenu()
  }, 120)
}

function getAnnouncementTextarea(): HTMLTextAreaElement | null {
  return announcementInputRef.value?.textarea || null
}

function updateMentionDropdownPosition(textarea: HTMLTextAreaElement) {
  const container = textarea.closest('.announcement-editor') as HTMLElement | null
  if (!container) return
  mentionDropdownStyle.value = {
    left: '0px',
    top: `${textarea.offsetTop + textarea.offsetHeight + 8}px`,
    width: `${Math.max(textarea.offsetWidth, 240)}px`,
  }
}

function syncMentionState() {
  const textarea = getAnnouncementTextarea()
  if (!textarea || !canManage.value) {
    hideMentionMenu()
    return
  }
  const cursor = textarea.selectionStart ?? announcementText.value.length
  const beforeCursor = announcementText.value.slice(0, cursor)
  const atIndex = beforeCursor.lastIndexOf('@')
  if (atIndex < 0) {
    hideMentionMenu()
    return
  }
  const query = beforeCursor.slice(atIndex + 1)
  if (/\s/.test(query) || query.includes('@')) {
    hideMentionMenu()
    return
  }
  mentionStartIndex.value = atIndex
  mentionQuery.value = query
  updateMentionDropdownPosition(textarea)
  mentionMenuVisible.value = mentionCandidates.value.length > 0
}

function handleAnnouncementInput() {
  syncMentionState()
}

function handleAnnouncementClick() {
  syncMentionState()
}

function selectMention(mentionValue: string) {
  const textarea = getAnnouncementTextarea()
  if (!textarea || mentionStartIndex.value < 0) return
  const cursor = textarea.selectionStart ?? announcementText.value.length
  const before = announcementText.value.slice(0, mentionStartIndex.value)
  const after = announcementText.value.slice(cursor)
  announcementText.value = `${before}@${mentionValue} ${after}`
  hideMentionMenu()
  nextTick(() => {
    textarea.focus()
    const nextCursor = `${before}@${mentionValue} `.length
    textarea.setSelectionRange(nextCursor, nextCursor)
  })
}

async function loadTeams() {
  loading.value = true
  try {
    const data = await teamApi.searchTeams({ keyword: keyword.value || undefined, tag: tag.value || undefined, page: page.value, size: pageSize.value })
    teams.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function openTeam(team: Pick<TeamSummary, 'id' | 'joined'>) {
  if (!team.joined) return
  drawerVisible.value = true
  detailsLoading.value = true
  try {
    selectedTeam.value = await teamApi.getTeam(team.id)
    await refreshDetails()
  } finally {
    detailsLoading.value = false
  }
}

async function refreshDetails() {
  if (!selectedTeam.value) return
  if (!selectedTeam.value.joined) {
    resetTeamCollections()
    return
  }
  members.value = await teamApi.listMembers(selectedTeam.value.id)
  try { joinRequests.value = canManage.value ? await teamApi.listJoinRequests(selectedTeam.value.id) : [] } catch { joinRequests.value = [] }
  try { announcements.value = await teamApi.listAnnouncements(selectedTeam.value.id) } catch { announcements.value = [] }
  try { votes.value = await teamApi.listVotes(selectedTeam.value.id) } catch { votes.value = [] }
  try { files.value = await teamApi.listFiles(selectedTeam.value.id) } catch { files.value = [] }
  try { album.value = await teamApi.listAlbum(selectedTeam.value.id) } catch { album.value = [] }
  try { moments.value = (await teamApi.listMoments(selectedTeam.value.id, { page: 1, size: 20 })).list } catch { moments.value = [] }
  try { points.value = await teamApi.listPoints(selectedTeam.value.id) } catch { points.value = [] }
  try { activities.value = (await teamApi.listActivities(selectedTeam.value.id, { page: 1, size: 20 })).list } catch { activities.value = [] }
  votes.value.forEach(v => { voteSelections[v.id] = [...(v.myOptionIndexes || [])] })
}

async function createTeam() {
  const team = await teamApi.createTeam({
    name: createForm.name,
    intro: createForm.intro || undefined,
    avatar: createForm.avatar || undefined,
    tags: splitLines(createForm.tags),
    joinType: createForm.joinType,
    capacity: createForm.capacity,
  })
  ElMessage.success('小队已创建')
  createDialogVisible.value = false
  Object.assign(createForm, { name: '', intro: '', avatar: '', tags: '', joinType: 'PUBLIC', capacity: 100 })
  await loadTeams()
  await openTeam(team)
}

async function joinTeam(teamId: number) {
  const result = await teamApi.joinTeam(teamId)
  ElMessage.success(result.status === 'PENDING' ? '申请已提交' : '加入成功')
  await loadTeams()
}

async function leaveTeam() {
  if (!selectedTeam.value) return
  await teamApi.leaveTeam(selectedTeam.value.id)
  ElMessage.success('已退出小队')
  drawerVisible.value = false
  await loadTeams()
}

async function dissolveTeam() {
  if (!selectedTeam.value) return
  await ElMessageBox.confirm('解散后将无法继续加入，确认继续吗？', '确认解散', { type: 'warning' })
  await teamApi.dissolveTeam(selectedTeam.value.id)
  ElMessage.success('小队已解散')
  drawerVisible.value = false
  await loadTeams()
}

async function reviewJoinRequest(req: TeamJoinRequestItem, action: 'APPROVE' | 'REJECT') {
  if (!selectedTeam.value) return
  await teamApi.handleJoinRequest(selectedTeam.value.id, req.id, action)
  ElMessage.success(action === 'APPROVE' ? '已批准' : '已拒绝')
  await refreshDetails()
  await loadTeams()
}

async function promote(member: TeamMemberItem, role: 'ADMIN' | 'MEMBER') {
  if (!selectedTeam.value) return
  await teamApi.updateRole(selectedTeam.value.id, member.userId, role)
  ElMessage.success('角色已更新')
  selectedTeam.value = await teamApi.getTeam(selectedTeam.value.id)
  await refreshDetails()
}

async function kick(member: TeamMemberItem) {
  if (!selectedTeam.value) return
  await teamApi.removeMember(selectedTeam.value.id, member.userId)
  ElMessage.success('成员已移除')
  await refreshDetails()
  await loadTeams()
}

async function publishAnnouncement() {
  if (!selectedTeam.value || !announcementText.value.trim()) return
  await teamApi.createAnnouncement(selectedTeam.value.id, announcementText.value)
  announcementText.value = ''
  hideMentionMenu()
  ElMessage.success('公告已发布')
  await refreshDetails()
}

async function publishVote() {
  if (!selectedTeam.value) return
  await teamApi.createVote(selectedTeam.value.id, {
    title: voteForm.title,
    options: splitLines(voteForm.optionsText),
    multiChoice: voteForm.multiChoice,
    deadline: voteForm.deadline || undefined,
  })
  Object.assign(voteForm, { title: '', optionsText: '', multiChoice: false, deadline: '' })
  ElMessage.success('投票已创建')
  await refreshDetails()
}

async function castVote(vote: TeamVoteItem) {
  if (!selectedTeam.value) return
  await teamApi.castVote(selectedTeam.value.id, vote.id, voteSelections[vote.id] || [])
  ElMessage.success('投票成功')
  await refreshDetails()
}

async function uploadFile() {
  if (!selectedTeam.value) return
  if (!fileDraft.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  fileUploading.value = true
  try {
    const uploaded = await teamApi.uploadFile(selectedTeam.value.id, fileDraft.value.file)
    await teamApi.createFile(selectedTeam.value.id, {
      fileName: uploaded.fileName,
      fileUrl: uploaded.url,
      fileSize: uploaded.fileSize,
    })
    clearFileDraft()
    fileDialogVisible.value = false
    ElMessage.success('文件已上传')
    await refreshDetails()
  } finally {
    fileUploading.value = false
  }
}

async function deleteFile(fileId: number) {
  if (!selectedTeam.value) return
  await teamApi.deleteFile(selectedTeam.value.id, fileId)
  ElMessage.success('文件已删除')
  await refreshDetails()
}

async function uploadAlbum() {
  if (!selectedTeam.value) return
  if (!albumDrafts.value.length) {
    ElMessage.warning('请先选择图片')
    return
  }
  albumUploading.value = true
  try {
    const imageUrls = await uploadDraftImages(selectedTeam.value.id, albumDrafts.value)
    await teamApi.createAlbum(selectedTeam.value.id, imageUrls)
    clearAlbumDrafts()
    ElMessage.success('照片已上传')
    await refreshDetails()
  } finally {
    albumUploading.value = false
  }
}

async function deletePhoto(photoId: number) {
  if (!selectedTeam.value) return
  await teamApi.deleteAlbum(selectedTeam.value.id, photoId)
  ElMessage.success('照片已删除')
  await refreshDetails()
}

async function publishMoment() {
  if (!selectedTeam.value) return
  if (!momentForm.content.trim() && !momentDrafts.value.length) {
    ElMessage.warning('请填写动态内容或选择图片')
    return
  }
  momentPublishing.value = true
  try {
    const imageUrls = momentDrafts.value.length ? await uploadDraftImages(selectedTeam.value.id, momentDrafts.value) : []
    await teamApi.createMoment(selectedTeam.value.id, {
      content: momentForm.content || undefined,
      images: imageUrls,
    })
    Object.assign(momentForm, { content: '' })
    clearMomentDrafts()
    ElMessage.success('动态已发布')
    await refreshDetails()
  } finally {
    momentPublishing.value = false
  }
}

async function featureMoment(momentId: number) {
  if (!selectedTeam.value) return
  await teamApi.featureMoment(selectedTeam.value.id, momentId)
  ElMessage.success('已设为精选')
  await refreshDetails()
}

const teamRoute = useRoute()

onMounted(async () => {
  await loadTeams()
  const detailId = Number(teamRoute.query.detail)
  if (detailId) {
    const target = teams.value.find(t => t.id === detailId)
    if (target) openTeam(target)
  }
})
onBeforeUnmount(() => {
  hideMentionMenu()
  clearFileDraft()
  clearAlbumDrafts()
  clearMomentDrafts()
})
</script>

<template>
  <div class="team-page">
    <section class="hero">
      <div>
        <p class="eyebrow">R4 Team Module</p>
        <h1>兴趣小队工作台</h1>
        <p class="sub">创建小队、发现加入、审批成员，并统一管理公告、投票、群文件、相册、动态和积分榜。</p>
      </div>
      <el-button type="primary" size="large" @click="createDialogVisible = true">创建小队</el-button>
    </section>

    <el-card class="search-card">
      <div class="search-row">
        <el-input v-model="keyword" placeholder="按名称搜索" clearable @keyup.enter="loadTeams" />
        <el-input v-model="tag" placeholder="按标签搜索" clearable @keyup.enter="loadTeams" />
        <el-button type="primary" @click="loadTeams">搜索</el-button>
      </div>
    </el-card>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="team in teams" :key="team.id" :xs="24" :md="12" :lg="8">
        <el-card class="team-card" shadow="hover">
          <div class="team-header">
            <div>
              <h3>{{ team.name }}</h3>
              <p>{{ team.intro || '这个小队还没有简介。' }}</p>
            </div>
            <el-tag :type="team.joinType === 'PUBLIC' ? 'success' : 'warning'">{{ team.joinType === 'PUBLIC' ? '公开加入' : '审核加入' }}</el-tag>
          </div>
          <div class="meta-line">
            <span>队长：{{ team.owner?.nickname || '未命名' }}</span>
            <span>{{ team.memberCount }}/{{ team.capacity }}</span>
          </div>
          <div class="tag-list">
            <el-tag v-for="item in team.tags" :key="item" size="small" effect="plain">{{ item }}</el-tag>
          </div>
          <div class="actions">
            <el-button v-if="team.joined" @click="openTeam(team)">查看</el-button>
            <el-button v-if="!team.joined" type="primary" @click="joinTeam(team.id)">加入</el-button>
            <el-tag v-else type="info">{{ team.myRole || 'MEMBER' }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="pager">
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" background layout="prev, pager, next" :total="total" @current-change="loadTeams" />
    </div>

    <el-dialog v-model="createDialogVisible" title="创建小队" width="560px">
      <el-form label-width="92px">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="createForm.intro" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="头像 URL"><el-input v-model="createForm.avatar" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="createForm.tags" type="textarea" :rows="2" placeholder="多个标签用逗号或换行分隔" /></el-form-item>
        <el-form-item label="加入方式">
          <el-radio-group v-model="createForm.joinType">
            <el-radio-button label="PUBLIC">公开</el-radio-button>
            <el-radio-button label="APPROVAL">审核</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="人数上限"><el-input-number v-model="createForm.capacity" :min="1" :max="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createTeam">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" :title="selectedTeam?.name || '小队详情'" size="80%">
      <div v-if="selectedTeam" v-loading="detailsLoading">
        <div class="detail-hero">
          <div>
            <p class="detail-intro">{{ selectedTeam.intro || '这个小队暂时还没有简介。' }}</p>
            <div class="tag-list">
              <el-tag v-for="item in selectedTeam.tags" :key="item" size="small">{{ item }}</el-tag>
            </div>
            <div class="meta-line">
              <span>队长：{{ selectedTeam.owner?.nickname || '未命名' }}</span>
              <span>成员：{{ selectedTeam.memberCount }}/{{ selectedTeam.capacity }}</span>
              <span>我的角色：{{ selectedTeam.myRole || '未加入' }}</span>
            </div>
          </div>
          <div class="actions">
            <el-button v-if="selectedTeam.joined" type="primary" @click="$router.push(`/social/team-chat/${selectedTeam.id}`)">群聊</el-button>
            <el-button v-if="selectedTeam.joined && !isOwner" @click="leaveTeam">退出小队</el-button>
            <el-button v-if="isOwner" type="danger" @click="dissolveTeam">解散小队</el-button>
          </div>
        </div>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="成员" name="members">
            <el-table :data="members" stripe>
              <el-table-column label="成员">
                <template #default="{ row }">
                  <span class="member-link" @click="$router.push(`/social/user/${row.userId}`)">{{ row.nickname || row.userId }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="role" label="角色" width="120" />
              <el-table-column prop="points" label="积分" width="100" />
              <el-table-column v-if="canManage" label="操作" width="240">
                <template #default="{ row }">
                  <el-button v-if="isOwner && row.role !== 'OWNER' && row.role !== 'ADMIN'" size="small" @click="promote(row, 'ADMIN')">设管理员</el-button>
                  <el-button v-if="isOwner && row.role === 'ADMIN'" size="small" @click="promote(row, 'MEMBER')">撤管理员</el-button>
                  <el-button v-if="row.role !== 'OWNER'" size="small" type="danger" @click="kick(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="申请" name="requests">
            <div v-if="!canManage" class="empty-tip">仅队长或管理员可查看加入申请。</div>
            <el-table v-else :data="joinRequests" stripe>
              <el-table-column prop="nickname" label="申请人" />
              <el-table-column prop="status" label="状态" width="120" />
              <el-table-column prop="createdAt" label="申请时间" min-width="180" />
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button size="small" type="success" :disabled="row.status !== 'PENDING'" @click="reviewJoinRequest(row, 'APPROVE')">批准</el-button>
                  <el-button size="small" type="danger" :disabled="row.status !== 'PENDING'" @click="reviewJoinRequest(row, 'REJECT')">拒绝</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="公告" name="announcements">
            <div v-if="canManage" class="announcement-editor">
              <el-input
                ref="announcementInputRef"
                v-model="announcementText"
                type="textarea"
                :rows="3"
                placeholder="输入 @ 后可选择所有人或成员姓名"
                @input="handleAnnouncementInput"
                @click="handleAnnouncementClick"
                @keyup="handleAnnouncementInput"
                @focus="handleAnnouncementInput"
                @blur="scheduleHideMentionMenu"
              />
              <div v-if="mentionMenuVisible" class="mention-menu" :style="mentionDropdownStyle" @mousedown.prevent @mouseenter="clearHideMentionTimer">
                <button
                  v-for="candidate in mentionCandidates"
                  :key="candidate.key"
                  class="mention-menu-item"
                  type="button"
                  @click="selectMention(candidate.mentionValue)"
                >
                  <span>@{{ candidate.label }}</span>
                  <small>{{ candidate.type === 'all' ? '通知全部成员' : '通知该成员' }}</small>
                </button>
              </div>
            </div>
            <el-button v-if="canManage" class="section-btn" type="primary" @click="publishAnnouncement">发布公告</el-button>
            <el-timeline>
              <el-timeline-item v-for="item in announcements" :key="item.id" :timestamp="item.createdAt">
                <strong>{{ item.authorName }}</strong>
                <p class="announcement-text">{{ item.content }}</p>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>

          <el-tab-pane label="投票" name="votes">
            <el-card v-if="canManage" class="mini-card">
              <el-form label-width="80px">
                <el-form-item label="标题"><el-input v-model="voteForm.title" /></el-form-item>
                <el-form-item label="选项"><el-input v-model="voteForm.optionsText" type="textarea" :rows="3" placeholder="一行一个选项" /></el-form-item>
                <el-form-item label="截止时间"><el-date-picker v-model="voteForm.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
                <el-form-item label="多选"><el-switch v-model="voteForm.multiChoice" /></el-form-item>
              </el-form>
              <el-button type="primary" @click="publishVote">发起投票</el-button>
            </el-card>
            <el-card v-for="vote in votes" :key="vote.id" class="mini-card">
              <div class="team-header">
                <div>
                  <strong>{{ vote.title }}</strong>
                  <p>{{ vote.creatorName }} · {{ vote.createdAt }}</p>
                </div>
                <el-tag v-if="vote.multiChoice">多选</el-tag>
              </div>
              <el-checkbox-group v-model="voteSelections[vote.id]">
                <div v-for="(item, index) in vote.options" :key="`${vote.id}-${index}`" class="vote-option">
                  <el-checkbox :label="index">{{ item }}（{{ vote.counts[index] || 0 }}票）</el-checkbox>
                </div>
              </el-checkbox-group>
              <el-button type="primary" size="small" @click="castVote(vote)">提交投票</el-button>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="群文件" name="files">
            <div class="upload-panel">
              <el-button type="primary" @click="fileDialogVisible = true">上传本地文件</el-button>
            </div>
            <el-table :data="files" stripe>
              <el-table-column prop="fileName" label="文件名" />
              <el-table-column prop="uploaderName" label="上传人" width="140" />
              <el-table-column label="链接" min-width="220">
                <template #default="{ row }"><a :href="normalizeUrl(row.fileUrl)" target="_blank">{{ normalizeUrl(row.fileUrl) }}</a></template>
              </el-table-column>
              <el-table-column v-if="canManage" label="操作" width="100">
                <template #default="{ row }"><el-button size="small" type="danger" @click="deleteFile(row.id)">删除</el-button></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="相册" name="album">
            <div class="upload-panel">
              <label class="upload-trigger">
                <input class="upload-input" type="file" accept="image/*" multiple @change="handleAlbumSelection" />
                <span>选择本地图片</span>
              </label>
              <el-button class="section-btn" type="primary" :loading="albumUploading" @click="uploadAlbum">上传图片</el-button>
              <el-button v-if="albumDrafts.length" class="section-btn" @click="clearAlbumDrafts">清空选择</el-button>
            </div>
            <div v-if="albumDrafts.length" class="preview-grid">
              <div v-for="draft in albumDrafts" :key="draft.id" class="preview-item">
                <img :src="draft.previewUrl" :alt="draft.file.name" />
                <span>{{ draft.file.name }}</span>
              </div>
            </div>
            <div class="album-grid">
              <div v-for="photo in album" :key="photo.id" class="album-item">
                <img :src="normalizeUrl(photo.imageUrl)" alt="team-photo" />
                <div class="meta-line">
                  <span>{{ photo.uploaderName }}</span>
                  <el-button v-if="canManage" text type="danger" @click="deletePhoto(photo.id)">删除</el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="动态" name="moments">
            <el-card class="mini-card">
              <el-input v-model="momentForm.content" type="textarea" :rows="3" placeholder="分享小队动态" />
              <div class="upload-panel" style="margin-top: 12px">
                <label class="upload-trigger">
                  <input class="upload-input" type="file" accept="image/*" multiple @change="handleMomentSelection" />
                  <span>选择动态图片</span>
                </label>
                <el-button class="section-btn" type="primary" :loading="momentPublishing" @click="publishMoment">发布动态</el-button>
                <el-button v-if="momentDrafts.length" class="section-btn" @click="clearMomentDrafts">清空选择</el-button>
              </div>
              <div v-if="momentDrafts.length" class="preview-grid small">
                <div v-for="draft in momentDrafts" :key="draft.id" class="preview-item">
                  <img :src="draft.previewUrl" :alt="draft.file.name" />
                  <span>{{ draft.file.name }}</span>
                </div>
              </div>
            </el-card>
            <el-card v-for="moment in moments" :key="moment.id" class="mini-card">
              <div class="team-header">
                <div>
                  <strong>{{ moment.authorName }}</strong>
                  <p>{{ moment.createdAt }}</p>
                </div>
                <el-tag v-if="moment.featured" type="warning">精选</el-tag>
              </div>
              <p v-if="moment.content">{{ moment.content }}</p>
              <div class="album-grid small">
                <img v-for="image in moment.images" :key="image" :src="normalizeUrl(image)" alt="moment" />
              </div>
              <el-button v-if="canManage && !moment.featured" size="small" @click="featureMoment(moment.id)">设为精选</el-button>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="积分榜" name="points">
            <el-table :data="points" stripe>
              <el-table-column prop="rank" label="名次" width="80" />
              <el-table-column label="成员">
                <template #default="{ row }">
                  <span class="member-link" @click="$router.push(`/social/user/${row.userId}`)">{{ row.nickname || row.userId }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="points" label="积分" width="100" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="队内活动" name="activities">
            <el-empty v-if="!activities.length" description="暂无队内活动" />
            <el-card v-for="activity in activities" :key="activity.id" class="mini-card">
              <div class="team-header">
                <div>
                  <strong>{{ activity.name }}</strong>
                  <p>{{ activity.category }} · {{ activity.phase }} · {{ activity.startTime || '待定' }}</p>
                </div>
                <el-tag>{{ activity.city || '未设城市' }}</el-tag>
              </div>
              <p>{{ activity.intro || '暂无简介' }}</p>
              <p>{{ activity.address || '未设置地点' }}</p>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="fileDialogVisible" title="上传群文件" width="520px" @closed="clearFileDraft">
      <div class="upload-panel">
        <label class="upload-trigger">
          <input class="upload-input" type="file" @change="handleFileSelection" />
          <span>{{ fileDraft ? '重新选择文件' : '选择本地文件' }}</span>
        </label>
      </div>
      <div v-if="fileDraft" class="file-draft-card">
        <strong>{{ fileDraft.file.name }}</strong>
        <span>{{ (fileDraft.file.size / 1024 / 1024).toFixed(2) }} MB</span>
      </div>
      <div v-else class="empty-tip">支持本地文件上传，上传后会存入 OSS 并展示下载链接。</div>
      <template #footer>
        <el-button @click="fileDialogVisible = false">取消</el-button>
        <el-button v-if="fileDraft" @click="clearFileDraft">清空</el-button>
        <el-button type="primary" :loading="fileUploading" @click="uploadFile">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.team-page { max-width: 1200px; margin: 0 auto; padding: 24px 0 40px; }
.hero { display: flex; justify-content: space-between; align-items: end; gap: 24px; padding: 28px; border-radius: 24px; background: linear-gradient(135deg, #e6f4ff 0%, #fff4dc 100%); margin-bottom: 20px; }
.eyebrow { margin: 0 0 8px; text-transform: uppercase; letter-spacing: 0.16em; color: #6283a1; font-size: 12px; }
.hero h1 { margin: 0 0 8px; font-size: 34px; color: #17324d; }
.sub { margin: 0; color: #4d6580; max-width: 720px; }
.search-card { margin-bottom: 18px; }
.search-row { display: grid; grid-template-columns: 1.6fr 1fr auto; gap: 12px; }
.search-row.compact { grid-template-columns: 1fr 1.4fr 160px auto; margin-bottom: 16px; }
.team-card { margin-bottom: 16px; min-height: 210px; }
.team-header, .meta-line, .actions, .detail-hero { display: flex; justify-content: space-between; gap: 12px; }
.team-header h3 { margin: 0 0 8px; }
.team-header p, .detail-intro { margin: 0; color: #5f7288; }
.meta-line { margin: 12px 0; color: #5f7288; font-size: 13px; flex-wrap: wrap; }
.tag-list { display: flex; flex-wrap: wrap; gap: 8px; margin: 10px 0 16px; }
.actions { justify-content: flex-end; align-items: center; }
.pager { display: flex; justify-content: center; }
.detail-hero { background: #f7fbff; border: 1px solid #d9ebff; border-radius: 18px; padding: 20px; margin-bottom: 18px; align-items: flex-start; }
.mini-card { margin-bottom: 14px; }
.section-btn { margin-top: 12px; }
.vote-option { margin-bottom: 8px; }
.announcement-editor { position: relative; }
.mention-menu {
  position: absolute;
  z-index: 20;
  background: #fff;
  border: 1px solid #dbe6f2;
  border-radius: 14px;
  box-shadow: 0 12px 30px rgba(25, 55, 90, 0.16);
  padding: 6px;
  display: grid;
  gap: 4px;
}
.mention-menu-item {
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #17324d;
}
.mention-menu-item:hover {
  background: #eef6ff;
}
.mention-menu-item small {
  color: #7b8ba0;
}
.upload-panel { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.upload-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border: 1px dashed #9ab6d6;
  border-radius: 12px;
  background: #f7fbff;
  color: #24507d;
  cursor: pointer;
}
.upload-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}
.preview-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 14px; margin-top: 16px; }
.preview-grid.small { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
.preview-item {
  border: 1px solid #e4edf5;
  border-radius: 14px;
  padding: 8px;
  background: #fff;
}
.preview-item img {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border-radius: 10px;
  background: #f3f6f9;
}
.preview-item span {
  display: block;
  margin-top: 8px;
  color: #5f7288;
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
}
.file-draft-card {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid #e4edf5;
  border-radius: 14px;
  background: #f8fbff;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #29445f;
}
.album-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; margin-top: 16px; }
.album-grid.small { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); }
.album-item { border: 1px solid #edf2f7; border-radius: 14px; padding: 8px; }
.album-item img, .album-grid img { width: 100%; aspect-ratio: 1 / 1; object-fit: cover; border-radius: 12px; background: #f3f6f9; }
.announcement-text { white-space: pre-wrap; }
.empty-tip { padding: 32px 12px; color: #7f8ea0; text-align: center; }
@media (max-width: 900px) {
  .hero, .detail-hero { flex-direction: column; align-items: stretch; }
  .search-row, .search-row.compact { grid-template-columns: 1fr; }
  .mention-menu { width: 100% !important; left: 0 !important; }
}
.member-link { color: #409eff; cursor: pointer; }
.member-link:hover { text-decoration: underline; }
</style>
