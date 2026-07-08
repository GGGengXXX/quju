<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import { socialApi, type MessageVO, type FriendVO } from '../../api/social'
import { teamApi, type TeamMemberItem } from '../../api/team'
import { authApi } from '../../api/auth'

declare global { interface Window { AMap?: any; __qujuAmapLoading__?: Promise<any> } }

const amapKey = (import.meta as any).env?.VITE_AMAP_KEY as string | undefined

async function ensureAmap() {
  if (window.AMap) return window.AMap
  if (!amapKey) return null
  if (!window.__qujuAmapLoading__) {
    window.__qujuAmapLoading__ = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${amapKey}`
      script.async = true
      script.onload = () => resolve(window.AMap)
      script.onerror = reject
      document.head.appendChild(script)
    })
  }
  return window.__qujuAmapLoading__
}

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
const aiReplyLoading = ref(false)
const showEmoji = ref(false)
const showLocationPicker = ref(false)
const locationPickerRef = ref<HTMLElement | null>(null)
let locationMap: any = null
let locationMarker: any = null

// @提醒
const showAtMenu = ref(false)
const atFilterText = ref('')
const atMenuMembers = ref<TeamMemberItem[]>([])
const inputRef = ref<any>(null)
const pickedLocation = reactive({ lng: '116.3521', lat: '39.9835', address: '' })

// 右键菜单
const contextMenu = reactive({ visible: false, x: 0, y: 0, msg: null as MessageVO | null })
const showForwardDialog = ref(false)
const forwardTargets = ref<{ type: string; id: number; name: string }[]>([])
const forwardMsg = ref<MessageVO | null>(null)

const emojis = ['😀','😂','🥰','😎','🤔','👍','👋','🎉','🔥','❤️','😭','😅','🙏','💪','✨','🥳','😤','🤣','😘','🫡','👀','💯','🤝','🫶','😈','🥲','😊','🤗','😏','🙄']
const messagesEnd = ref<HTMLElement | null>(null)
const memberMap = ref<Map<number, TeamMemberItem>>(new Map())

let ws: WebSocket | null = null

async function loadPeerInfo() {
  if (isFriendChat) {
    try {
      const friends = await socialApi.getFriends()
      const friend = friends.find((f: FriendVO) => f.userId === peerId)
      if (friend) {
        peerName.value = friend.remark || friend.nickname || "未知用户"
        memberMap.value.set(peerId, { userId: peerId, nickname: friend.nickname, avatar: friend.avatar } as TeamMemberItem)
      }
    } catch { /* ignore */ }
  } else {
    try {
      const team = await teamApi.getTeam(peerId)
      if (team?.name) peerName.value = team.name
      const members = await teamApi.listMembers(peerId)
      members.forEach(m => memberMap.value.set(m.userId, m))
    } catch { /* ignore */ }
  }
  // 把自己也加到 memberMap（方便统一取头像昵称）
  if (auth.user) {
    memberMap.value.set(auth.user.id, { userId: auth.user.id, nickname: auth.user.nickname, avatar: auth.user.avatar } as TeamMemberItem)
  }
}

function getMemberName(senderId: number) {
  const m = memberMap.value.get(senderId)
  return m?.nickname || "未知用户"
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
    socialApi.markRead({ scope, peerId })
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

async function requestAiReply() {
  if (aiReplyLoading.value) return
  aiReplyLoading.value = true
  try {
    const result = await socialApi.generateAiReply({ scope, peerId })
    const suggestion = result.suggestion?.trim()
    if (!suggestion) {
      ElMessage.warning('AI 暂时没有给出可用回复')
      return
    }
    inputText.value = inputText.value.trim() ? `${inputText.value.trim()}\n${suggestion}` : suggestion
    await nextTick()
    inputRef.value?.focus?.()
    ElMessage.success(`AI 已生成回复草稿，参考了最近 ${result.contextCount || 0} 条消息`)
  } catch {
    // handled by interceptor
  } finally {
    aiReplyLoading.value = false
  }
}

function insertEmoji(emoji: string) {
  inputText.value += emoji
  showEmoji.value = false
}

function onInputChange() {
  if (!isFriendChat && inputText.value.endsWith('@')) {
    // 弹出@成员选择
    atFilterText.value = ''
    atMenuMembers.value = Array.from(memberMap.value.values()).filter(m => m.userId !== auth.user?.id)
    showAtMenu.value = true
  } else if (showAtMenu.value) {
    const lastAt = inputText.value.lastIndexOf('@')
    if (lastAt === -1) { showAtMenu.value = false; return }
    const filter = inputText.value.slice(lastAt + 1).toLowerCase()
    atMenuMembers.value = Array.from(memberMap.value.values())
      .filter(m => m.userId !== auth.user?.id && (m.nickname || '').toLowerCase().includes(filter))
    if (!atMenuMembers.value.length) showAtMenu.value = false
  }
}

function selectAtMember(member: TeamMemberItem) {
  const lastAt = inputText.value.lastIndexOf('@')
  inputText.value = inputText.value.slice(0, lastAt) + `@${member.nickname || member.userId} `
  showAtMenu.value = false
}

function selectAtAll() {
  const lastAt = inputText.value.lastIndexOf('@')
  inputText.value = inputText.value.slice(0, lastAt) + '@所有人 '
  showAtMenu.value = false
}

function parseLocationLabel(content: string) {
  const parts = content.split(',')
  return parts.length >= 3 ? parts.slice(2).join(',') : '查看位置'
}

function openLocation(content: string) {
  const parts = content.split(',')
  if (parts.length >= 2) {
    const lng = parts[0]
    const lat = parts[1]
    window.open(`https://uri.amap.com/marker?position=${lng},${lat}&name=${encodeURIComponent(parts.slice(2).join(',') || '位置')}`, '_blank')
  }
}

async function sendLocation() {
  showLocationPicker.value = true
  pickedLocation.address = ''
  await nextTick()
  if (!locationPickerRef.value) return
  const AMap = await ensureAmap()
  if (!AMap) {
    ElMessage.warning('地图 Key 未配置')
    showLocationPicker.value = false
    return
  }
  if (!locationMap) {
    locationMap = new AMap.Map(locationPickerRef.value, { zoom: 14, center: [116.3521, 39.9835] })
    locationMap.on('click', async (e: any) => {
      pickedLocation.lng = e.lnglat.getLng().toFixed(6)
      pickedLocation.lat = e.lnglat.getLat().toFixed(6)
      if (locationMarker) locationMarker.setPosition(e.lnglat)
      else {
        locationMarker = new AMap.Marker({ map: locationMap, position: e.lnglat })
      }
      // 逆地理编码获取地址
      pickedLocation.address = '获取中...'
      try {
        const res = await fetch(`https://restapi.amap.com/v3/geocode/regeo?key=${amapKey}&location=${pickedLocation.lng},${pickedLocation.lat}`)
        const data = await res.json()
        if (data.status === '1' && data.regeocode?.formatted_address) {
          pickedLocation.address = data.regeocode.formatted_address
        } else {
          pickedLocation.address = `${pickedLocation.lng}, ${pickedLocation.lat}`
        }
      } catch {
        pickedLocation.address = `${pickedLocation.lng}, ${pickedLocation.lat}`
      }
    })
  }
}

async function confirmLocation() {
  if (!pickedLocation.address.trim() || pickedLocation.address === '获取中...') {
    ElMessage.warning('请在地图上点击选择位置')
    return
  }
  showLocationPicker.value = false
  sending.value = true
  try {
    const content = `${pickedLocation.lng},${pickedLocation.lat},${pickedLocation.address}`
    const msg = await socialApi.sendMessage({ scope, peerId, contentType: 'LOCATION', content })
    messages.value.push(msg)
    await nextTick()
    scrollToBottom()
  } catch {
    ElMessage.error('位置发送失败')
  } finally {
    sending.value = false
  }
}

function cancelLocation() {
  showLocationPicker.value = false
}

function useChatCurrentLocation() {
  if (!navigator.geolocation) {
    ElMessage.warning('浏览器不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition(
    async (pos) => {
      const lng = pos.coords.longitude.toFixed(6)
      const lat = pos.coords.latitude.toFixed(6)
      pickedLocation.lng = lng
      pickedLocation.lat = lat
      if (locationMap) {
        locationMap.setCenter([Number(lng), Number(lat)])
        const AMap = window.AMap
        if (locationMarker) locationMarker.setPosition([Number(lng), Number(lat)])
        else if (AMap) locationMarker = new AMap.Marker({ map: locationMap, position: [Number(lng), Number(lat)] })
      }
      // 逆地理编码
      try {
        const res = await fetch(`https://restapi.amap.com/v3/geocode/regeo?key=${amapKey}&location=${lng},${lat}`)
        const data = await res.json()
        if (data.status === '1' && data.regeocode?.formatted_address) {
          pickedLocation.address = data.regeocode.formatted_address
        }
      } catch {}
      ElMessage.success('已定位到当前位置')
    },
    () => { ElMessage.warning('定位失败，请在地图上点选') },
    { timeout: 8000 }
  )
}

async function sendImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  sending.value = true
  try {
    const res = await authApi.uploadImage(file)
    const url = res?.url || res
    const msg = await socialApi.sendMessage({ scope, peerId, contentType: 'IMAGE', content: url as string })
    messages.value.push(msg)
    await nextTick()
    scrollToBottom()
  } catch {
    ElMessage.error('图片发送失败')
  } finally {
    sending.value = false
    input.value = ''
  }
}

async function recall(msg: MessageVO) {
  const created = new Date(msg.createdAt || '').getTime()
  if (Date.now() - created > 2 * 60 * 1000) {
    ElMessage.warning('只能撤回2分钟内的消息')
    return
  }
  try {
    await ElMessageBox.confirm('确认撤回这条消息？', '撤回消息', { confirmButtonText: '撤回', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  await socialApi.recallMessage(msg.id)
  msg.isRecalled = true
  ElMessage.success('已撤回')
}

function showContextMenu(e: MouseEvent, msg: MessageVO) {
  e.preventDefault()
  contextMenu.visible = true
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.msg = msg
}

function hideContextMenu() {
  contextMenu.visible = false
}

async function openForwardDialog() {
  hideContextMenu()
  forwardMsg.value = contextMenu.msg
  // 加载好友和小队作为转发目标
  const targets: { type: string; id: number; name: string }[] = []
  try {
    const friends = await socialApi.getFriends()
    friends.forEach(f => targets.push({ type: 'FRIEND', id: f.userId, name: f.remark || f.nickname || "未知用户" }))
  } catch {}
  try {
    const me = auth.user?.id
    if (me) {
      const teams = await (await import('../../api/http')).default.get<any, any[]>(`/users/${me}/teams`)
      teams.forEach((t: any) => targets.push({ type: 'TEAM', id: t.id, name: `[群] ${t.name}` }))
    }
  } catch {}
  forwardTargets.value = targets
  showForwardDialog.value = true
}

async function doForward(target: { type: string; id: number; name: string }) {
  if (!forwardMsg.value) return
  try {
    await socialApi.forwardMessage(forwardMsg.value.id, { scope: target.type, peerId: target.id })
    ElMessage.success(`已转发给 ${target.name}`)
    showForwardDialog.value = false
  } catch {
    ElMessage.error('转发失败')
  }
}

function connectWebSocket() {
  const token = localStorage.getItem('quju_token')
  if (!token) return
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${location.host}/ws/chat?token=${token}`
  ws = new WebSocket(wsUrl)
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      // 处理已读回执
      if (data.type === 'READ_RECEIPT') {
        if (data.scope === 'FRIEND' && data.messageIds) {
          messages.value.forEach(m => {
            if (data.messageIds.includes(m.id)) m.isRead = true
          })
        } else if (data.scope === 'TEAM' && data.teamId === peerId) {
          // 群聊有人已读，增加所有自己发的消息的 readCount
          messages.value.forEach(m => {
            if (isMine(m) && m.readCount != null) m.readCount++
          })
        }
        return
      }
      // 普通消息
      const msg: MessageVO = data
      const match = isFriendChat
        ? msg.scope === 'FRIEND' && (msg.senderId === peerId || msg.receiverId === peerId)
        : msg.scope === 'TEAM' && msg.teamId === peerId
      if (match) {
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
  return name
}

function handleKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'j') {
    event.preventDefault()
    requestAiReply()
  }
}

onMounted(() => {
  loadPeerInfo()
  loadMessages()
  connectWebSocket()
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  ws?.close()
  ws = null
  window.removeEventListener('keydown', handleKeydown)
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
            </div>
            <div class="bubble" @contextmenu.prevent="showContextMenu($event, msg)">
              <template v-if="msg.contentType === 'IMAGE'">
                <img :src="msg.content" class="msg-img" />
              </template>
              <template v-else-if="msg.contentType === 'LOCATION'">
                <div class="location-msg" @click="openLocation(msg.content)">
                  <span class="location-icon">📍</span>
                  <span class="location-text">{{ parseLocationLabel(msg.content) }}</span>
                </div>
              </template>
              <template v-else>
                {{ msg.content }}
              </template>
            </div>
            <span class="msg-time">
              {{ msg.createdAt?.slice(11, 16) }}
              <span v-if="isMine(msg) && isFriendChat" class="read-status" :class="{ read: msg.isRead }">{{ msg.isRead ? '已读' : '未读' }}</span>
              <span v-if="isMine(msg) && !isFriendChat && msg.readCount != null" class="read-status read">{{ msg.readCount }}人已读</span>
            </span>
          </div>
        </template>
      </div>
      <div ref="messagesEnd" />
    </div>

    <div class="chat-input">
      <el-input ref="inputRef" v-model="inputText" placeholder="输入消息...（群聊中输入@提醒成员）" @keyup.enter="send" @input="onInputChange" :disabled="sending || aiReplyLoading" />
      <el-tooltip content="AI 回复草稿 (Ctrl/Cmd+J)" placement="top">
        <el-button class="ai-btn" :loading="aiReplyLoading" @click="requestAiReply">AI</el-button>
      </el-tooltip>
      <span class="emoji-btn" @click="showEmoji = !showEmoji">😊</span>
      <span class="img-btn" @click="sendLocation" title="发送位置">📍</span>
      <label class="img-btn">
        <span>📷</span>
        <input type="file" accept="image/*" hidden @change="sendImage" :disabled="sending || aiReplyLoading" />
      </label>
      <el-button type="primary" :loading="sending" :disabled="aiReplyLoading" @click="send">发送</el-button>
    </div>
    <div v-if="showEmoji" class="emoji-panel">
      <span v-for="e in emojis" :key="e" class="emoji-item" @click="insertEmoji(e)">{{ e }}</span>
    </div>

    <!-- @提醒成员选择 -->
    <div v-if="showAtMenu && !isFriendChat" class="at-menu">
      <div class="at-menu-item at-all" @click="selectAtAll">@所有人</div>
      <div v-for="m in atMenuMembers" :key="m.userId" class="at-menu-item" @click="selectAtMember(m)">
        <el-avatar :size="24" :src="m.avatar" />
        <span>{{ m.nickname || "未知用户" }}</span>
      </div>
    </div>

    <!-- 地图选点弹窗 -->
    <el-dialog v-model="showLocationPicker" title="选择位置" width="500px" @close="cancelLocation">
      <div ref="locationPickerRef" class="location-map"></div>
      <div class="location-form">
        <div style="display:flex;align-items:center;justify-content:space-between">
          <p class="location-hint">坐标：{{ pickedLocation.lng }}, {{ pickedLocation.lat }}</p>
          <el-button size="small" @click="useChatCurrentLocation">📍 当前位置</el-button>
        </div>
        <el-input v-model="pickedLocation.address" placeholder="位置描述（选点后自动填充）" />
      </div>
      <template #footer>
        <el-button @click="cancelLocation">取消</el-button>
        <el-button type="primary" @click="confirmLocation">发送位置</el-button>
      </template>
    </el-dialog>
    <!-- 右键菜单 -->
    <div v-if="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }" @mouseleave="hideContextMenu">
      <div v-if="contextMenu.msg && isMine(contextMenu.msg)" class="menu-item" @click="recall(contextMenu.msg!); hideContextMenu()">撤回</div>
      <div class="menu-item" @click="openForwardDialog">转发</div>
    </div>

    <!-- 转发对话框 -->
    <el-dialog v-model="showForwardDialog" title="转发给..." width="400px">
      <div class="forward-list">
        <div v-if="!forwardTargets.length" class="empty">暂无可转发对象</div>
        <div v-for="t in forwardTargets" :key="`${t.type}-${t.id}`" class="forward-item" @click="doForward(t)">
          {{ t.name }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-view { display: flex; flex-direction: column; height: calc(100vh - 60px); max-width: 720px; margin: 0 auto; background: var(--surface); border-left: 1px solid var(--line); border-right: 1px solid var(--line); }
.chat-header { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid var(--line); background: var(--surface-2); }
.peer-name { font-weight: 600; font-size: 15px; color: var(--ink); }
.chat-messages {
  flex: 1; overflow-y: auto; padding: 18px 16px; display: flex; flex-direction: column; gap: 16px;
  background: var(--paper);
  background-image: radial-gradient(rgba(27,28,24,0.04) 1px, transparent 1px);
  background-size: 22px 22px;
}
.msg-row { display: flex; }
.msg-row.mine { justify-content: flex-end; }
.msg-row.theirs { justify-content: flex-start; }
.msg-wrapper { max-width: 80%; display: inline-flex; flex-direction: column; }
.mine .msg-wrapper { align-items: flex-end; }
.theirs .msg-wrapper { align-items: flex-start; }
.sender-info { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; cursor: pointer; }
.mine .sender-info { flex-direction: row-reverse; }
.sender-info:hover .sender-name { color: var(--signal); }
.sender-name { font-size: 12.5px; color: var(--ink-soft); font-weight: 500; transition: color 0.15s ease; }
.msg-time { font-family: var(--font-mono); font-size: 10.5px; color: var(--ink-faint); margin-top: 3px; }
.mine .msg-time { text-align: right; }
.read-status { margin-left: 6px; font-size: 10px; color: var(--ink-faint); }
.read-status.read { color: var(--route); }
.bubble {
  display: inline-block; padding: 10px 14px; border-radius: 14px; font-size: 14px;
  word-break: break-word; line-height: 1.5; box-shadow: var(--shadow);
}
.mine .bubble { background: var(--ink); color: var(--paper); border-bottom-right-radius: 5px; }
.theirs .bubble { background: var(--surface); color: var(--ink); border: 1px solid var(--line); border-bottom-left-radius: 5px; }
.recalled { font-size: 12px; color: var(--ink-faint); font-style: italic; padding: 4px 0; }
.msg-img { max-width: 220px; border-radius: 10px; display: block; }
.chat-input { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-top: 1px solid var(--line); background: var(--surface); }
.chat-input .el-input { flex: 1; }
.ai-btn { min-width: 56px; }
.img-btn { cursor: pointer; font-size: 20px; padding: 4px 8px; border-radius: 8px; }
.img-btn:hover { background: var(--surface-2); }
.emoji-btn { cursor: pointer; font-size: 20px; padding: 4px 8px; border-radius: 8px; }
.emoji-btn:hover { background: var(--surface-2); }
.emoji-panel { display: flex; flex-wrap: wrap; gap: 4px; padding: 8px 16px; border-top: 1px solid var(--line); background: var(--surface); max-height: 120px; overflow-y: auto; }
.emoji-item { cursor: pointer; font-size: 22px; padding: 4px; border-radius: 8px; }
.emoji-item:hover { background: var(--surface-2); }
.location-msg { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.mine .location-msg { color: var(--paper); }
.location-msg:hover { text-decoration: underline; }
.location-icon { font-size: 18px; }
.location-text { font-size: 13px; }
.location-map { width: 100%; height: 300px; border-radius: 10px; margin-bottom: 12px; }
.location-form { display: flex; flex-direction: column; gap: 8px; }
.location-hint { font-family: var(--font-mono); font-size: 12px; color: var(--ink-soft); margin: 0; }
.context-menu { position: fixed; z-index: 9999; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-sm); box-shadow: var(--shadow-hover); padding: 4px 0; min-width: 100px; }
.menu-item { padding: 8px 16px; cursor: pointer; font-size: 14px; color: var(--ink); }
.menu-item:hover { background: var(--surface-2); color: var(--signal); }
.forward-list { max-height: 300px; overflow-y: auto; }
.forward-item { padding: 11px 16px; cursor: pointer; border-bottom: 1px solid var(--line); color: var(--ink); }
.forward-item:hover { background: var(--signal-wash); color: var(--signal-ink); }
.forward-item:last-child { border-bottom: none; }
.at-menu { position: relative; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-sm); box-shadow: var(--shadow-hover); padding: 4px 0; max-height: 200px; overflow-y: auto; margin: 0 16px; }
.at-menu-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; font-size: 14px; color: var(--ink); }
.at-menu-item:hover { background: var(--surface-2); }
.at-all { font-weight: 600; color: var(--signal); }
</style>
