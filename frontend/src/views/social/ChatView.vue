<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { socialApi, type MessageVO, type FriendVO } from '../../api/social'
import { teamApi, type TeamMemberItem } from '../../api/team'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isFriendChat = !route.path.startsWith('/social/team-chat')
const scope = isFriendChat ? 'FRIEND' : 'TEAM'
const peerId = Number(route.params.id)
const peerName = ref(isFriendChat ? `用户 ${peerId}` : `小队 ${peerId}`)
const messages = ref<MessageVO[]>([])
const loading = ref(false)
const inputText = ref('')
const sending = ref(false)
const messagesEnd = ref<HTMLElement | null>(null)
const memberMap = ref<Map<number, TeamMemberItem>>(new Map())

let ws: WebSocket | null = null

async function loadPeerInfo() {
  if (isFriendChat) {
    try {
      const friends = await socialApi.getFriends()
      const friend = friends.find((f: FriendVO) => f.userId === peerId)
      if (friend) peerName.value = friend.remark || friend.nickname || `用户 ${peerId}`
    } catch { /* ignore */ }
  } else {
    try {
      const team = await teamApi.getTeam(peerId)
      if (team?.name) peerName.value = team.name
      const members = await teamApi.listMembers(peerId)
      members.forEach(m => memberMap.value.set(m.userId, m))
    } catch { /* ignore */ }
  }
}

function getMemberName(senderId: number) {
  const m = memberMap.value.get(senderId)
  return m?.nickname || `用户${senderId}`
}

function getMemberAvatar(senderId: number) {
  return memberMap.value.get(senderId)?.avatar || undefined
}

async function loadMessages() {
  loading.value = true
  try {
    const res = await socialApi.getMessages({ scope, peerId, page: 1, size: 50 })
    messages.value = res.list
    await nextTick()
    scrollToBottom()
    if (isFriendChat) socialApi.markRead({ scope, peerId })
  } finally {
    loading.value = false
  }
}

function scrollToBottom() {
  messagesEnd.value?.scrollIntoView({ behavior: 'smooth' })
}

async function send() {
  const text = inputText.value.trim()
  if (!text) return
  sending.value = true
  try {
    const msg = await socialApi.sendMessage({ scope, peerId, contentType: 'TEXT', content: text })
    messages.value.push(msg)
    inputText.value = ''
    await nextTick()
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

async function recall(msg: MessageVO) {
  const created = new Date(msg.createdAt || '').getTime()
  if (Date.now() - created > 2 * 60 * 1000) {
    ElMessage.warning('只能撤回2分钟内的消息')
    return
  }
  await socialApi.recallMessage(msg.id)
  msg.isRecalled = true
  ElMessage.success('已撤回')
}

function connectWebSocket() {
  const token = localStorage.getItem('quju_token')
  if (!token) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/chat?token=${token}`
  ws = new WebSocket(wsUrl)
  ws.onmessage = (event) => {
    try {
      const msg: MessageVO = JSON.parse(event.data)
      const match = isFriendChat
        ? msg.scope === 'FRIEND' && (msg.senderId === peerId || msg.receiverId === peerId)
        : msg.scope === 'TEAM' && msg.teamId === peerId
      if (match) {
        // 避免重复（自己发的消息已经在 send() 中 push 了）
        if (!messages.value.some(m => m.id === msg.id)) {
          messages.value.push(msg)
          nextTick(scrollToBottom)
        }
      }
    } catch { /* ignore non-json */ }
  }
}

function isMine(msg: MessageVO) {
  return msg.senderId === auth.user?.id
}

function truncName(name: string) {
  return name.length > 15 ? name.slice(0, 15) + '…' : name
}

onMounted(() => {
  loadPeerInfo()
  loadMessages()
  connectWebSocket()
})

onBeforeUnmount(() => {
  ws?.close()
  ws = null
})
</script>

<template>
  <div class="chat-view">
    <div class="chat-header">
      <el-button text @click="router.back()">← 返回</el-button>
      <span class="peer-name">{{ peerName }}</span>
      <el-tag v-if="!isFriendChat" size="small" type="success">群聊</el-tag>
    </div>

    <div class="chat-messages" v-loading="loading">
      <div v-for="msg in messages" :key="msg.id" :class="['msg-row', isMine(msg) ? 'mine' : 'theirs']">
        <div v-if="msg.isRecalled" class="recalled">消息已撤回</div>
        <template v-else>
          <div class="msg-wrapper">
            <div class="sender-info" @click="router.push(`/social/user/${msg.senderId}`)">
              <el-avatar :size="32" :src="isMine(msg) ? auth.user?.avatar : getMemberAvatar(msg.senderId)" />
              <span class="sender-name">{{ isMine(msg) ? truncName(auth.user?.nickname || '我') : truncName(getMemberName(msg.senderId)) }}</span>
              <span class="time">{{ msg.createdAt?.slice(11, 16) }}</span>
            </div>
            <div class="bubble" @contextmenu.prevent="isMine(msg) && recall(msg)">
              <template v-if="msg.contentType === 'IMAGE'">
                <img :src="msg.content" class="msg-img" />
              </template>
              <template v-else>
                {{ msg.content }}
              </template>
            </div>
          </div>
        </template>
      </div>
      <div ref="messagesEnd" />
    </div>

    <div class="chat-input">
      <el-input v-model="inputText" placeholder="输入消息..." @keyup.enter="send" :disabled="sending" />
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: calc(100vh - 60px); max-width: 700px; margin: 0 auto; }
.chat-header { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #eee; }
.peer-name { font-weight: 600; font-size: 15px; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.msg-row { display: flex; }
.msg-row.mine { justify-content: flex-end; }
.msg-row.theirs { justify-content: flex-start; }
.msg-wrapper { max-width: 75%; }
.mine .msg-wrapper { align-items: flex-end; }
.sender-info { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; cursor: pointer; }
.mine .sender-info { flex-direction: row-reverse; }
.sender-info:hover .sender-name { text-decoration: underline; }
.sender-name { font-size: 12px; color: #666; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.time { font-size: 11px; color: #999; }
.bubble { padding: 10px 14px; border-radius: 12px; font-size: 14px; word-break: break-word; line-height: 1.5; }
.mine .bubble { background: #409eff; color: #fff; border-top-right-radius: 4px; }
.theirs .bubble { background: #f0f0f0; color: #333; border-top-left-radius: 4px; }
.recalled { font-size: 12px; color: #999; font-style: italic; padding: 4px 0; }
.msg-img { max-width: 200px; border-radius: 8px; display: block; }
.chat-input { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #eee; }
.chat-input .el-input { flex: 1; }
</style>
