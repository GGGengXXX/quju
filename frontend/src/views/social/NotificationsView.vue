<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi, type NotificationItem } from '../../api/notification'

const router = useRouter()
const list = ref<NotificationItem[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)

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

async function handleClick(item: NotificationItem) {
  // 标记已读
  if (!item.isRead) {
    try {
      await notificationApi.markRead(item.id)
      item.isRead = true
    } catch { /* ignore */ }
  }
  // 根据类型跳转
  navigate(item)
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
    case 'FRIEND_ACCEPT':
      router.push(`/social/user/${refId}`)
      break
    case 'FRIEND_MESSAGE':
      router.push(`/social/chat/${refId}`)
      break
    case 'TEAM_JOIN':
    case 'TEAM_JOIN_REQUEST':
      router.push(`/teams?detail=${refId}`)
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
  TEAM_JOIN: '小队加入',
  TEAM_JOIN_REQUEST: '小队申请',
  SYSTEM: '系统通知',
}

onMounted(load)
</script>

<template>
  <div class="notifications-page">
    <div class="header">
      <h2>通知中心</h2>
      <el-button text type="primary" @click="markAllRead">全部已读</el-button>
    </div>

    <div v-loading="loading" class="list">
      <div v-if="!list.length && !loading" class="empty">暂无通知</div>
      <div
        v-for="item in list"
        :key="item.id"
        :class="['notify-item', { unread: !item.isRead }]"
        @click="handleClick(item)"
      >
        <div class="item-main">
          <div class="item-left">
            <el-tag size="small" :type="item.isRead ? 'info' : ''">{{ typeLabel[item.type] || item.type }}</el-tag>
            <span class="title">{{ item.title }}</span>
          </div>
          <div class="item-right">
            <span class="time">{{ item.createdAt?.slice(0, 16).replace('T', ' ') }}</span>
            <span v-if="!item.isRead" class="dot" />
          </div>
        </div>
        <div v-if="item.content" class="item-content">{{ item.content }}</div>
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
.notifications-page { max-width: 700px; margin: 0 auto; padding: 16px; }
.header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.header h2 { margin: 0; }
.list { min-height: 100px; }
.empty { text-align: center; color: #999; padding: 32px 0; }
.notify-item { padding: 12px; border-bottom: 1px solid #f0f0f0; cursor: pointer; transition: background 0.2s; }
.notify-item:hover { background: #f9f9f9; }
.notify-item.unread { background: #ecf5ff; }
.item-main { display: flex; align-items: center; justify-content: space-between; }
.item-left { display: flex; align-items: center; gap: 8px; }
.title { font-size: 14px; font-weight: 500; }
.item-right { display: flex; align-items: center; gap: 8px; }
.time { font-size: 12px; color: #999; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #f56c6c; }
.item-content { font-size: 13px; color: #666; margin-top: 6px; padding-left: 4px; }
</style>
