<script setup lang="ts">
import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { teamApi, type TeamAnnouncementItem, type TeamSummary } from './api/team'
import { notificationApi } from './api/notification'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const minimalLayout = computed(() => Boolean(route.meta.minimalLayout))
let notificationTimer: number | null = null
const unreadCount = ref(0)
const isDark = ref(localStorage.getItem('quju_dark') === 'true')

function toggleDark() {
  isDark.value = !isDark.value
  localStorage.setItem('quju_dark', isDark.value ? 'true' : 'false')
  document.documentElement.classList.toggle('dark', isDark.value)
}

// 初始化暗色模式
if (isDark.value) document.documentElement.classList.add('dark')

async function pollUnreadCount() {
  if (!auth.token) {
    unreadCount.value = 0
    return
  }
  try {
    unreadCount.value = await notificationApi.unreadCount()
  } catch {
    // ignore
  }
}

function startUnreadPolling() {
  pollUnreadCount()
}

function stopUnreadPolling() {
  unreadCount.value = 0
}

provide('refreshUnread', pollUnreadCount)

function goNotifications() {
  router.push('/notifications')
}

watch(() => router.currentRoute.value.path, (newPath, oldPath) => {
  if (oldPath === '/notifications' && newPath !== '/notifications') {
    pollUnreadCount()
  }
})

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
  if (notificationChecking || !auth.token || minimalLayout.value) return
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
  if (minimalLayout.value) return
  const ready = await ensureCurrentUserLoaded()
  if (!ready) return
  await pollAnnouncements()
  notificationTimer = window.setInterval(() => {
    void pollAnnouncements()
  }, 30000)
}

watch(() => [auth.token, minimalLayout.value], ([token, minimal]) => {
  if (token && !minimal) {
    void startAnnouncementPolling()
    startUnreadPolling()
    return
  }
  stopAnnouncementPolling()
  stopUnreadPolling()
}, { immediate: true })

onBeforeUnmount(() => {
  stopAnnouncementPolling()
  stopUnreadPolling()
})
</script>

<template>
  <template v-if="minimalLayout">
    <router-view />
  </template>
  <el-container v-else>
    <el-header class="hd">
      <span class="logo" @click="router.push('/')">🎯 趣聚</span>
      <span class="spacer" />
      <el-button text class="theme-toggle" @click="toggleDark">{{ isDark ? '☀️' : '🌙' }}</el-button>
      <template v-if="auth.token">
        <el-button text @click="router.push('/activities')">活动</el-button>
        <el-button text @click="router.push('/social')">社交</el-button>
        <el-button text @click="goNotifications">
          通知<el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="notify-badge" />
        </el-button>
        <el-button text @click="router.push('/teams')">小队</el-button>
        <el-button text @click="router.push('/profile')">我的</el-button>
        <el-button text @click="logout">退出</el-button>
      </template>
      <template v-else>
        <el-button text @click="router.push('/login')">登录</el-button>
        <el-button text @click="router.push('/register')">注册</el-button>
      </template>
    </el-header>
    <el-main><router-view /></el-main>
  </el-container>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Nunito:wght@400;500;600;700;800&family=Fredoka:wght@400;500;600;700&display=swap');

:root {
  --qj-primary: #f56c2e;
  --qj-primary-light: #fff7ed;
  --qj-primary-hover: #ea580c;
  --qj-secondary: #8b5cf6;
  --qj-accent: #06b6d4;
  --qj-text: #1e293b;
  --qj-text-secondary: #64748b;
  --qj-bg: #fefcfa;
  --qj-card: #ffffff;
  --qj-border: #f0ece6;
  --qj-nav-bg: linear-gradient(135deg, #1e293b 0%, #312e81 100%);
  --qj-nav-text: #f8fafc;
  --qj-success: #10b981;
  --qj-danger: #ef4444;
  --qj-radius: 16px;
  --qj-radius-sm: 10px;
  --qj-shadow: 0 2px 8px rgba(0,0,0,0.04), 0 8px 24px rgba(0,0,0,0.02);
  --qj-shadow-hover: 0 8px 32px rgba(245,108,46,0.15), 0 2px 8px rgba(0,0,0,0.04);
  --qj-gradient: linear-gradient(135deg, #f56c2e, #8b5cf6);
}

html.dark {
  --qj-primary: #fb923c;
  --qj-primary-light: #1c1917;
  --qj-primary-hover: #f97316;
  --qj-secondary: #a78bfa;
  --qj-accent: #22d3ee;
  --qj-text: #f1f5f9;
  --qj-text-secondary: #94a3b8;
  --qj-bg: #0f172a;
  --qj-card: #1e293b;
  --qj-border: #334155;
  --qj-nav-bg: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
  --qj-nav-text: #f1f5f9;
  --qj-shadow: 0 2px 8px rgba(0,0,0,0.3), 0 8px 24px rgba(0,0,0,0.2);
  --qj-shadow-hover: 0 8px 32px rgba(251,146,60,0.2), 0 2px 8px rgba(0,0,0,0.3);
  --qj-gradient: linear-gradient(135deg, #fb923c, #a78bfa);
}

* { box-sizing: border-box; }
body {
  margin: 0;
  background: var(--qj-bg);
  font-family: 'Nunito', -apple-system, BlinkMacSystemFont, sans-serif;
  color: var(--qj-text);
  line-height: 1.6;
  transition: background 0.3s ease, color 0.3s ease;
}

h1, h2, h3, h4, h5 {
  font-family: 'Fredoka', 'Nunito', sans-serif;
  font-weight: 600;
  line-height: 1.3;
}

/* Element Plus 主题覆盖 */
.el-button--primary {
  background: var(--qj-gradient) !important;
  border: none !important;
  border-radius: var(--qj-radius-sm) !important;
  color: #fff !important;
  font-weight: 600;
}
.el-button--primary:hover { transform: translateY(-2px); box-shadow: var(--qj-shadow-hover); opacity: 0.9; }
.el-button { border-radius: var(--qj-radius-sm) !important; transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1); }
.el-tag { border-radius: 20px; font-weight: 600; padding: 2px 12px; }
.el-card {
  border-radius: var(--qj-radius) !important;
  border: 1px solid var(--qj-border) !important;
  background: var(--qj-card) !important;
  box-shadow: var(--qj-shadow) !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.el-card:hover { box-shadow: var(--qj-shadow-hover) !important; transform: translateY(-3px); }
.el-dialog { border-radius: var(--qj-radius) !important; background: var(--qj-card) !important; }
.el-input__wrapper { border-radius: var(--qj-radius-sm) !important; background: var(--qj-card) !important; }
.el-tabs__item.is-active { color: var(--qj-primary) !important; font-weight: 700; }
.el-tabs__active-bar { background: var(--qj-gradient) !important; height: 3px; border-radius: 2px; }
.el-table { background: var(--qj-card) !important; }
.el-table th.el-table__cell { background: var(--qj-bg) !important; }

/* 导航栏 */
.hd {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 28px;
  height: 64px;
  background: var(--qj-nav-bg);
  border-bottom: none;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
  position: sticky;
  top: 0;
  z-index: 100;
}
.hd .el-button {
  color: var(--qj-nav-text) !important;
  font-size: 14px;
  font-weight: 600;
  font-family: 'Nunito', sans-serif;
  border-radius: 10px !important;
  padding: 8px 14px !important;
  transition: all 0.2s ease;
}
.hd .el-button:hover { color: #fff !important; background: rgba(255,255,255,0.12) !important; transform: translateY(-1px); }
.logo {
  font-family: 'Fredoka', sans-serif;
  font-weight: 700;
  font-size: 24px;
  cursor: pointer;
  background: var(--qj-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-right: 16px;
}
.spacer { flex: 1; }
.notify-badge { margin-left: 4px; vertical-align: middle; }
.theme-toggle { font-size: 18px !important; }

/* 全局 main 区域 */
.el-main { padding: 24px 28px; }

/* 链接样式 */
a { color: var(--qj-primary); text-decoration: none; transition: color 0.2s; }
a:hover { color: var(--qj-primary-hover); }

/* 暗色模式下 Element Plus 组件 */
html.dark .el-button:not(.el-button--primary) { color: var(--qj-text) !important; border-color: var(--qj-border) !important; background: var(--qj-card) !important; }
html.dark .el-input__wrapper { border-color: var(--qj-border) !important; background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-input__inner { color: var(--qj-text) !important; }
html.dark .el-dialog { color: var(--qj-text); background: var(--qj-card) !important; }
html.dark .el-dialog__header { color: var(--qj-text); }
html.dark .el-message-box { background: var(--qj-card); color: var(--qj-text); }
html.dark .el-descriptions__cell { background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-card { background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-card__body { color: var(--qj-text) !important; }
html.dark .el-tabs__header { background: transparent; }
html.dark .el-tabs__item { color: var(--qj-text-secondary) !important; }
html.dark .el-tabs__item.is-active { color: var(--qj-primary) !important; }
html.dark .el-table { background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-table th.el-table__cell { background: var(--qj-bg) !important; color: var(--qj-text) !important; }
html.dark .el-table td.el-table__cell { background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell { background: var(--qj-bg) !important; }
html.dark .el-table__empty-text { color: var(--qj-text-secondary) !important; }
html.dark .el-select__wrapper { background: var(--qj-card) !important; border-color: var(--qj-border) !important; color: var(--qj-text) !important; }
html.dark .el-tag { background: var(--qj-bg) !important; color: var(--qj-text) !important; border-color: var(--qj-border) !important; }
html.dark .el-form-item__label { color: var(--qj-text) !important; }
html.dark .el-empty__description p { color: var(--qj-text-secondary) !important; }
html.dark .el-pagination button, html.dark .el-pagination .el-pager li { background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-drawer { background: var(--qj-card) !important; color: var(--qj-text) !important; }
html.dark .el-drawer__header { color: var(--qj-text) !important; }
html.dark .el-radio__label, html.dark .el-checkbox__label { color: var(--qj-text) !important; }
html.dark .el-switch__label { color: var(--qj-text-secondary) !important; }
html.dark .el-divider__text { background: var(--qj-bg) !important; color: var(--qj-text-secondary) !important; }
html.dark .el-skeleton__item { background: var(--qj-border) !important; }
html.dark .panel, html.dark .dialog-panel, html.dark .mine-panel, html.dark .sidebar-mine { background: var(--qj-card) !important; color: var(--qj-text) !important; border-color: var(--qj-border) !important; }
html.dark .section-head h3 { color: var(--qj-text) !important; }
html.dark .muted { color: var(--qj-text-secondary) !important; }
</style>
