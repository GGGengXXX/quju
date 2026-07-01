<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { socialApi, type MessageVO } from '../../api/social'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const peerId = Number(route.params.id)
const messages = ref<MessageVO[]>([])
const loading = ref(false)
const inputText = ref('')
const sending = ref(false)
const messagesEnd = ref<HTMLElement | null>(null)

let ws: WebSocket | null = null

async function loadMessages() {
  loading.value = true
  try {
    const res = await socialApi.getMessages({ scope: 'FRIEND', peerId, page: 1, size: 50 })
    messages.value = res.list.reverse()
    await nextTick()
    scrollToBottom()
    socialApi.markRead({ scope: 'FRIEND', peerId })
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
    const msg = await socialApi.sendMessage({ scope: 'FRIEND', peerId, contentType: 'TEXT', content: text })
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
      if (msg.scope === 'FRIEND' && (msg.senderId === peerId || msg.receiverId === peerId)) {
        messages.value.push(msg)
        nextTick(scrollToBottom)
        socialApi.markRead({ scope: 'FRIEND', peerId })
      }
    } catch { /* ignore non-json */ }
  }
}

function isMine(msg: MessageVO) {
  return msg.senderId === auth.user?.id
}

onMounted(() => {
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
      <el-button text @click="router.push('/social')">← 返回</el-button>
      <span class="peer-name">与用户 {{ peerId }} 的对话</span>
    </div>

    <div class="chat-messages" v-loading="loading">
      <div v-for="msg in messages" :key="msg.id" :class="['msg-row', isMine(msg) ? 'mine' : 'theirs']">
        <div v-if="msg.isRecalled" class="recalled">消息已撤回</div>
        <div v-else class="bubble" @contextmenu.prevent="isMine(msg) && recall(msg)">
          <template v-if="msg.contentType === 'IMAGE'">
            <img :src="msg.content" class="msg-img" />
          </template>
          <template v-else>
            {{ msg.content }}
          </template>
          <span class="time">{{ msg.createdAt?.slice(11, 16) }}</span>
        </div>
      </div>
      <div ref="messagesEnd" />
    </div>

    <div class="chat-input">
      <el-input
        v-model="inputText"
        placeholder="输入消息..."
        @keyup.enter="send"
        :disabled="sending"
      />
      <el-button type="primary" :loading="sending" @click="send">发送</el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: calc(100vh - 60px); max-width: 700px; margin: 0 auto; }
.chat-header { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #eee; }
.peer-name { font-weight: 600; font-size: 15px; }
.chat-messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
.msg-row { display: flex; }
.msg-row.mine { justify-content: flex-end; }
.msg-row.theirs { justify-content: flex-start; }
.bubble { max-width: 70%; padding: 8px 12px; border-radius: 12px; font-size: 14px; position: relative; word-break: break-word; }
.mine .bubble { background: #409eff; color: #fff; border-bottom-right-radius: 4px; }
.theirs .bubble { background: #f0f0f0; color: #333; border-bottom-left-radius: 4px; }
.recalled { font-size: 12px; color: #999; font-style: italic; padding: 4px 0; }
.time { font-size: 10px; opacity: 0.7; margin-left: 8px; }
.msg-img { max-width: 200px; border-radius: 8px; display: block; }
.chat-input { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #eee; }
.chat-input .el-input { flex: 1; }
</style>
