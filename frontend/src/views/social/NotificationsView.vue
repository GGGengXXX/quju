<script setup lang="ts">
import { onMounted, inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi, type NotificationItem } from '../../api/notification'

const router = useRouter()
const refreshUnread = inject<() => void>('refreshUnread', () => {})
const list = ref<NotificationItem[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const expandedId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await notificationApi.list({ page: page.value, size: 20 })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function toggleExpand(item: NotificationItem) {
  if (expandedId.value === item.id) {
    expandedId.value = null
    return
  }
  expandedId.value = item.id
  // 展开即标记已读
  if (!item.isRead) {
    try {
      await notificationApi.markRead(item.id)
      item.isRead = true
      refreshUnread()
    } catch { /* ignore */ }
  }
}

function navigate(item: NotificationItem) {
  const { type, refType, refId } = item
  if (!refId) return
  switch (type) {
    case 'ACTIVITY_REVIEW':
    case 'ACTIVITY_SIGNUP':
      router.push(`/activities?detail=${refId}`)
      break
    case 'FRIEND_REQUEST':
      router.push('/social?tab=requests')
      break
    case 'FRIEND_ACCEPT':
      router.push(`/social/user/${refId}`)
      break
    case 'FRIEND_MESSAGE':
      router.push(`/social/chat/${refId}`)
      break
    case 'NEW_FOLLOWER':
      router.push(`/social/user/${refId}`)
      break
    case 'TEAM_JOIN':
    case 'TEAM_JOIN_REQUEST':
      router.push(`/teams?detail=${refId}`)
      break
    case 'TEAM_MESSAGE':
    case 'TEAM_AT':
      router.push(`/social/team-chat/${refId}`)
      break
    default:
      if (refType === 'ACTIVITY') router.push(`/activities?detail=${refId}`)
      else if (refType === 'USER') router.push(`/social/user/${refId}`)
      else if (refType === 'TEAM') router.push(`/teams?detail=${refId}`)
  }
}

async function markAllRead() {
  await notificationApi.markAllRead()
  list.value.forEach(n => { n.isRead = true })
  refreshUnread()
  ElMessage.success('已全部标记已读')
}

function handlePageChange(p: number) {
  page.value = p
  load()
}

const typeLabel: Record<string, string> = {
  ACTIVITY_REVIEW: '活动审核',
  ACTIVITY_SIGNUP: '活动报名',
  FRIEND_REQUEST: '好友申请',
  FRIEND_ACCEPT: '好友通过',
  FRIEND_MESSAGE: '好友消息',
  NEW_FOLLOWER: '新粉丝',
  TEAM_JOIN: '小队加入',
  TEAM_JOIN_REQUEST: '小队申请',
  TEAM_MESSAGE: '小队消息',
  TEAM_AT: '小队@提醒',
  SYSTEM: '系统通知',
}

onMounted(load)
</script>

<template>
  <div class="notifications-page">
    <div class="header">
      <div class="head-lead">
        <span class="head-eyebrow">QUJU · 信号台</span>
        <h2>通知中心</h2>
      </div>
      <button class="allread-btn" @click="markAllRead">全部已读</button>
    </div>

    <div v-loading="loading" class="list">
      <div v-if="!list.length && !loading" class="empty">此刻没有新信号</div>
      <div
        v-for="item in list"
        :key="item.id"
        :class="['notify-item', { unread: !item.isRead, expanded: expandedId === item.id }]"
        @click="toggleExpand(item)"
      >
        <div class="item-main">
          <div class="item-left">
            <span class="dot" :class="{ on: !item.isRead }" />
            <span class="ntype">{{ typeLabel[item.type] || item.type }}</span>
            <span class="title">{{ item.title }}</span>
          </div>
          <span class="time">{{ item.createdAt?.slice(0, 16).replace('T', ' ') }}</span>
        </div>
        <div v-if="expandedId === item.id" class="item-detail">
          <p v-if="item.content" class="detail-content">{{ item.content }}</p>
          <p v-else class="detail-content empty-content">无附加内容</p>
          <el-button v-if="item.refId" size="small" type="primary" @click.stop="navigate(item)">前往查看</el-button>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > 20"
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="20"
      :current-page="page"
      @current-change="handlePageChange"
    />
  </div>
</template>

<style scoped>
.notifications-page { max-width: 720px; margin: 24px auto; padding: 0 16px; }
.header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 14px; }
.head-eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ink-faint); }
.header h2 { margin: 4px 0 0; font-size: 26px; color: var(--ink); }
.allread-btn {
  background: none; border: 1px solid var(--line-strong); border-radius: 22px; cursor: pointer;
  font-size: 12.5px; padding: 7px 14px; color: var(--ink-soft); transition: all 0.15s ease;
}
.allread-btn:hover { border-color: var(--signal); color: var(--signal); }
.list { min-height: 100px; }
.empty { text-align: center; color: var(--ink-faint); padding: 40px 0; font-size: 13px; }
@keyframes qj-rise { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }
.notify-item {
  padding: 13px 14px; cursor: pointer; transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.18s ease;
  border-radius: var(--radius-sm); margin-bottom: 8px;
  background: var(--surface); border: 1px solid var(--line);
  box-shadow: inset 3px 0 0 var(--line-strong);
  animation: qj-rise 0.42s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}
.notify-item:nth-child(1) { animation-delay: 30ms; }
.notify-item:nth-child(2) { animation-delay: 75ms; }
.notify-item:nth-child(3) { animation-delay: 120ms; }
.notify-item:nth-child(4) { animation-delay: 165ms; }
.notify-item:nth-child(5) { animation-delay: 210ms; }
.notify-item:nth-child(n+6) { animation-delay: 250ms; }
.notify-item:hover { border-color: var(--line-strong); transform: translateX(3px); }
.notify-item.unread { box-shadow: inset 3px 0 0 var(--signal); }
.notify-item.expanded { background: var(--surface-2); }
.item-main { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.item-left { display: flex; align-items: center; gap: 9px; min-width: 0; }
.dot { width: 7px; height: 7px; border-radius: 50%; background: var(--line-strong); flex-shrink: 0; }
.dot.on { background: var(--signal); box-shadow: 0 0 0 3px var(--signal-wash); }
.ntype {
  flex: 0 0 auto; font-family: var(--font-mono); font-size: 10.5px; letter-spacing: 0.03em;
  padding: 2px 8px; border-radius: 6px; background: var(--surface-2);
  border: 1px solid var(--line); color: var(--ink-soft);
}
.notify-item.unread .ntype { background: var(--signal-wash); border-color: transparent; color: var(--signal-ink); }
.title { font-size: 14px; font-weight: 500; color: var(--ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.time { flex: 0 0 auto; font-family: var(--font-mono); font-size: 11px; color: var(--ink-faint); white-space: nowrap; }
.item-detail { margin-top: 12px; padding: 12px; background: var(--surface-2); border-radius: var(--radius-sm); border: 1px dashed var(--line-strong); }
.detail-content { font-size: 13px; color: var(--ink-soft); margin: 0 0 10px 0; line-height: 1.55; }
.empty-content { color: var(--ink-faint); font-style: italic; }
@media (prefers-reduced-motion: reduce) {
  .notify-item { animation: none; }
  .notify-item:hover { transform: none; }
}
</style>
