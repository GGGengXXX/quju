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
// 暗色模式暂时下线：强制浅色主题，忽略历史偏好
document.documentElement.classList.remove('dark')

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

function isNav(prefix: string) {
  return route.path.startsWith(prefix)
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
  <div v-else class="app-shell">
    <header class="topbar">
      <a class="brand" @click="router.push('/')">
        <span class="brand-pin" aria-hidden="true"></span>
        <span class="brand-name">趣聚</span>
        <span class="brand-sub">QUJU / 城市寻趣</span>
      </a>
      <nav v-if="auth.token" class="nav">
        <a class="nav-link" :class="{ active: isNav('/activities') }" @click="router.push('/activities')">活动</a>
        <a class="nav-link" :class="{ active: isNav('/social') }" @click="router.push('/social')">社交</a>
        <a class="nav-link" :class="{ active: isNav('/notifications') }" @click="goNotifications">
          通知<span v-if="unreadCount > 0" class="nav-dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </a>
        <a class="nav-link" :class="{ active: isNav('/teams') }" @click="router.push('/teams')">小队</a>
        <a class="nav-link" :class="{ active: isNav('/profile') }" @click="router.push('/profile')">我的</a>
      </nav>
      <span class="spacer" />
      <template v-if="auth.token">
        <button class="ghost-btn" @click="logout">退出</button>
      </template>
      <template v-else>
        <button class="ghost-btn" @click="router.push('/login')">登录</button>
        <button class="signal-btn" @click="router.push('/register')">注册</button>
      </template>
    </header>
    <main class="app-main"><router-view /></main>
  </div>
</template>

<style>
@import url('https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,600;12..96,700;12..96,800&family=Space+Mono:wght@400;700&display=swap');

/* ── 城市寻趣 · Wayfinding design tokens ──────────────────────────────
   信号朱红(pin/CTA) · 等高线墨(text) · 纸面(bg) · 路线青(secondary) · 邮戳琥珀(badge) */
:root {
  --ink: #1b1c18;
  --ink-soft: #5c5f56;
  --ink-faint: #8b8d82;
  --paper: #f2f1ea;
  --surface: #ffffff;
  --surface-2: #faf9f3;
  --line: #e2e0d5;
  --line-strong: #d3d0c2;
  --signal: #ff4324;
  --signal-ink: #d6300f;
  --signal-wash: #fff0ec;
  --route: #157a6e;
  --route-wash: #e6f2ef;
  --stamp: #c8860d;
  --stamp-wash: #fbf1dc;

  --font-display: 'Bricolage Grotesque', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --font-mono: 'Space Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  --font-body: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans SC', sans-serif;

  --radius: 14px;
  --radius-sm: 9px;
  --shadow: 0 1px 2px rgba(27,28,24,0.05), 0 8px 24px rgba(27,28,24,0.05);
  --shadow-hover: 0 2px 6px rgba(27,28,24,0.08), 0 16px 40px rgba(27,28,24,0.10);

  /* Legacy --qj-* aliases (existing views reference these) */
  --qj-primary: var(--signal);
  --qj-primary-light: var(--signal-wash);
  --qj-primary-hover: var(--signal-ink);
  --qj-secondary: var(--route);
  --qj-accent: var(--route);
  --qj-text: var(--ink);
  --qj-text-secondary: var(--ink-soft);
  --qj-bg: var(--paper);
  --qj-card: var(--surface);
  --qj-border: var(--line);
  --qj-nav-bg: var(--surface);
  --qj-nav-text: var(--ink);
  --qj-success: var(--route);
  --qj-danger: var(--signal);
  --qj-radius: var(--radius);
  --qj-radius-sm: var(--radius-sm);
  --qj-shadow: var(--shadow);
  --qj-shadow-hover: var(--shadow-hover);
  --qj-gradient: var(--signal);

  /* Element Plus 主色 → 信号朱红：一处改动，全站组件（单选/多选/开关/日期/加载）统一 */
  --el-color-primary: #ff4324;
  --el-color-primary-light-3: #ff7050;
  --el-color-primary-light-5: #ff9077;
  --el-color-primary-light-7: #ffbfb2;
  --el-color-primary-light-8: #ffd5cc;
  --el-color-primary-light-9: #ffe9e3;
  --el-color-primary-dark-2: #d6300f;
  --el-color-success: #157a6e;
  --el-color-success-light-9: #e6f2ef;
  --el-border-radius-base: 9px;
}

html.dark {
  --ink: #ecebe1;
  --ink-soft: #a3a498;
  --ink-faint: #74766b;
  --paper: #13140f;
  --surface: #1d1e17;
  --surface-2: #24261d;
  --line: #34362b;
  --line-strong: #43463a;
  --signal: #ff5c3d;
  --signal-ink: #ff7a60;
  --signal-wash: #2a1611;
  --route: #3ba593;
  --route-wash: #12241f;
  --stamp: #e0a11b;
  --stamp-wash: #2a2110;
  --shadow: 0 1px 2px rgba(0,0,0,0.4), 0 8px 24px rgba(0,0,0,0.35);
  --shadow-hover: 0 2px 6px rgba(0,0,0,0.5), 0 16px 40px rgba(0,0,0,0.45);

  --el-color-primary: #ff5c3d;
  --el-color-primary-light-3: #cc4028;
  --el-color-primary-light-5: #9e3320;
  --el-color-primary-light-7: #6b2417;
  --el-color-primary-light-8: #4d1c12;
  --el-color-primary-light-9: #2a1611;
  --el-color-primary-dark-2: #ff7a60;
  --el-bg-color: #1d1e17;
  --el-bg-color-overlay: #24261d;
  --el-fill-color-blank: #1d1e17;
  --el-text-color-primary: #ecebe1;
  --el-text-color-regular: #c9c9bd;
  --el-border-color: #34362b;
  --el-border-color-light: #34362b;
}

* { box-sizing: border-box; }
body {
  margin: 0;
  background: var(--paper);
  /* 极淡的地图网点纹理，营造"纸面/等高线"的寻趣氛围 */
  background-image: radial-gradient(rgba(27,28,24,0.045) 1px, transparent 1px);
  background-size: 22px 22px;
  font-family: var(--font-body);
  color: var(--ink);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  transition: background-color 0.3s ease, color 0.3s ease;
}
html.dark body { background-image: radial-gradient(rgba(255,255,255,0.035) 1px, transparent 1px); }

h1, h2, h3, h4, h5 {
  font-family: var(--font-display);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
}

/* Element Plus 主题覆盖 */
.el-button--primary {
  background: var(--signal) !important;
  border: 1px solid var(--signal) !important;
  border-radius: var(--radius-sm) !important;
  color: #fff !important;
  font-weight: 600;
}
.el-button--primary:hover { background: var(--signal-ink) !important; border-color: var(--signal-ink) !important; box-shadow: var(--shadow-hover); }
.el-button { border-radius: var(--radius-sm) !important; font-weight: 600; transition: all 0.18s cubic-bezier(0.4, 0, 0.2, 1); }
.el-button:not(.el-button--primary):not(.is-text):hover { color: var(--signal) !important; border-color: var(--signal) !important; }
.el-tag { border-radius: 6px; font-weight: 600; padding: 2px 9px; font-family: var(--font-mono); font-size: 12px; letter-spacing: 0.02em; }
.el-radio-button__inner { font-weight: 600; }
.el-radio-button.is-active .el-radio-button__inner {
  background: var(--ink) !important;
  border-color: var(--ink) !important;
  box-shadow: -1px 0 0 0 var(--ink) !important;
  color: var(--paper) !important;
}
.el-card {
  border-radius: var(--radius) !important;
  border: 1px solid var(--line) !important;
  background: var(--surface) !important;
  box-shadow: var(--shadow) !important;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.el-card:hover { box-shadow: var(--shadow-hover) !important; }
.el-dialog { border-radius: var(--radius) !important; background: var(--surface) !important; }
.el-dialog__title { font-family: var(--font-display); font-weight: 700; }
.el-input__wrapper, .el-textarea__inner { border-radius: var(--radius-sm) !important; background: var(--surface) !important; }
.el-input__wrapper.is-focus { box-shadow: 0 0 0 1px var(--signal) inset !important; }
.el-tabs__item.is-active { color: var(--signal) !important; font-weight: 700; }
.el-tabs__item:hover { color: var(--ink) !important; }
.el-tabs__active-bar { background: var(--signal) !important; height: 2px; }
.el-table { background: var(--surface) !important; }
.el-table th.el-table__cell { background: var(--surface-2) !important; font-weight: 700; color: var(--ink); }

/* ── 导航栏：寻趣路牌 ───────────────────────────── */
.app-shell { min-height: 100vh; }
.topbar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 26px;
  height: 62px;
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: saturate(1.4) blur(10px);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 100;
}
.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  cursor: pointer;
  margin-right: 26px;
  color: var(--ink) !important;
  white-space: nowrap;
}
.brand-pin {
  align-self: center;
  width: 13px;
  height: 13px;
  border-radius: 50% 50% 50% 0;
  background: var(--signal);
  transform: rotate(-45deg);
  box-shadow: inset -2px -2px 0 rgba(0,0,0,0.12);
}
.brand-name { font-family: var(--font-display); font-weight: 800; font-size: 22px; letter-spacing: 0.02em; }
.brand-sub { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.08em; color: var(--ink-faint); text-transform: uppercase; }
.nav { display: flex; align-items: center; gap: 2px; }
.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-soft) !important;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: color 0.15s ease, background 0.15s ease;
}
.nav-link:hover { color: var(--ink) !important; background: var(--surface-2); }
.nav-link.active { color: var(--ink) !important; }
.nav-link.active::after {
  content: '';
  position: absolute;
  left: 14px; right: 14px; bottom: -1px;
  height: 2px;
  background: var(--signal);
  border-radius: 2px;
}
.nav-dot {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  padding: 2px 5px;
  border-radius: 20px;
  background: var(--signal);
  color: #fff;
}
.spacer { flex: 1; }
.icon-btn {
  width: 36px; height: 36px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink);
  font-size: 16px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.icon-btn:hover { border-color: var(--signal); color: var(--signal); }
.ghost-btn, .signal-btn {
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s ease;
}
.ghost-btn { border: 1px solid transparent; background: transparent; color: var(--ink-soft); }
.ghost-btn:hover { color: var(--ink); background: var(--surface-2); }
.signal-btn { border: 1px solid var(--signal); background: var(--signal); color: #fff; }
.signal-btn:hover { background: var(--signal-ink); border-color: var(--signal-ink); }

/* 全局 main 区域 */
.app-main { padding: 26px 28px 56px; max-width: 1360px; margin: 0 auto; }

/* 链接样式 */
a { color: var(--signal); text-decoration: none; transition: color 0.2s; }
a:hover { color: var(--signal-ink); }

@media (max-width: 720px) {
  .topbar { padding: 0 14px; gap: 2px; overflow-x: auto; }
  .brand { margin-right: 12px; }
  .brand-sub { display: none; }
  .nav-link { padding: 8px 10px; font-size: 14px; }
  .app-main { padding: 18px 14px 48px; }
}

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
