<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useRouter } from 'vue-router'
import { teamApi, type TeamAnnouncementItem, type TeamSummary } from './api/team'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()
let notificationTimer: number | null = null
let notificationChecking = false

function logout() {
  stopAnnouncementPolling()
  auth.logout()
  router.push('/login')
}

function storageKey(userId: number) {
  return `quju_team_announcement_seen_v1_${userId}`
}

function readSeenMap(userId: number) {
  try {
    const raw = localStorage.getItem(storageKey(userId))
    if (!raw) return {} as Record<string, number>
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed as Record<string, number> : {}
  } catch {
    return {} as Record<string, number>
  }
}

function writeSeenMap(userId: number, value: Record<string, number>) {
  localStorage.setItem(storageKey(userId), JSON.stringify(value))
}

function extractMentions(content: string) {
  return Array.from(content.matchAll(/@([^\s@，。！？,;；:：]+)/g)).map(match => match[1])
}

function isRelevantAnnouncement(userId: number, nickname: string | undefined, announcement: TeamAnnouncementItem) {
  if (announcement.authorId === userId) return false
  const content = announcement.content || ''
  if (content.includes('@所有人')) return true
  if (!nickname?.trim()) return false
  return extractMentions(content).includes(nickname.trim())
}

function notifyAnnouncement(team: TeamSummary) {
  ElNotification({
    title: '小队公告提醒',
    message: `${team.name} 小队有新的和你相关的公告`,
    position: 'top-right',
    duration: 6000,
    onClick: () => router.push('/teams'),
  })
}

async function ensureCurrentUserLoaded() {
  if (!auth.token) return false
  if (auth.user) return true
  try {
    await auth.loadMe()
    return Boolean(auth.user)
  } catch {
    return false
  }
}

async function pollAnnouncements() {
  if (notificationChecking || !auth.token) return
  const ready = await ensureCurrentUserLoaded()
  if (!ready || !auth.user?.id) return

  notificationChecking = true
  try {
    const userId = auth.user.id
    const nickname = auth.user.nickname
    const teamPage = await teamApi.searchTeams({ page: 1, size: 100 })
    const joinedTeams = teamPage.list.filter(team => team.joined)
    const seenMap = readSeenMap(userId)
    let changed = false

    for (const team of joinedTeams) {
      let announcements: TeamAnnouncementItem[] = []
      try {
        announcements = await teamApi.listAnnouncements(team.id)
      } catch {
        continue
      }
      if (!announcements.length) continue

      const latestId = Math.max(...announcements.map(item => item.id))
      const teamKey = String(team.id)
      const lastSeenId = Number(seenMap[teamKey] || 0)
      const isFirstVisitForTeam = !(teamKey in seenMap)
      const relevantAnnouncements = announcements
        .filter(item => isRelevantAnnouncement(userId, nickname, item))
        .sort((a, b) => a.id - b.id)

      if (isFirstVisitForTeam) {
        if (relevantAnnouncements.length) {
          notifyAnnouncement(team)
        }
        seenMap[teamKey] = latestId
        changed = true
        continue
      }

      const unseenRelevantAnnouncements = relevantAnnouncements.filter(item => item.id > lastSeenId)
      if (unseenRelevantAnnouncements.length) {
        notifyAnnouncement(team)
      }

      if (latestId > lastSeenId) {
        seenMap[teamKey] = latestId
        changed = true
      }
    }

    if (changed) {
      writeSeenMap(userId, seenMap)
    }
  } finally {
    notificationChecking = false
  }
}

function stopAnnouncementPolling() {
  if (notificationTimer !== null) {
    window.clearInterval(notificationTimer)
    notificationTimer = null
  }
}

async function startAnnouncementPolling() {
  stopAnnouncementPolling()
  const ready = await ensureCurrentUserLoaded()
  if (!ready) return
  await pollAnnouncements()
  notificationTimer = window.setInterval(() => {
    void pollAnnouncements()
  }, 30000)
}

watch(() => auth.token, (token) => {
  if (token) {
    void startAnnouncementPolling()
    return
  }
  stopAnnouncementPolling()
}, { immediate: true })

onBeforeUnmount(() => {
  stopAnnouncementPolling()
})
</script>

<template>
  <el-container>
    <el-header class="hd">
      <span class="logo" @click="router.push('/')">趣聚 QuJu</span>
      <span class="spacer" />
      <template v-if="auth.token">
        <el-button text @click="router.push('/social')">社交</el-button>
        <el-button text @click="router.push('/teams')">小队</el-button>
        <el-button text @click="router.push('/profile')">我的</el-button>
        <el-button text @click="logout">退出</el-button>
      </template>
    </el-header>
    <el-main><router-view /></el-main>
  </el-container>
</template>

<style>
body { margin: 0; }
.hd { display: flex; align-items: center; background: #409eff; color: #fff; }
.logo { font-weight: 700; font-size: 18px; cursor: pointer; }
.spacer { flex: 1; }
.hd .el-button { color: #fff; }
</style>
