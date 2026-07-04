<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import jsQR from 'jsqr'
import { useAuthStore } from '../../stores/auth'
import {
  socialApi,
  type FriendVO,
  type FriendRequestVO,
  type FollowVO,
  type BlockVO,
  type UserBrief,
} from '../../api/social'
import http from '../../api/http'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const tab = ref((route.query.tab as string) || 'friends')

// 好友列表
const friends = ref<FriendVO[]>([])
const friendsLoading = ref(false)

// 好友申请（保留但降级）
const requests = ref<FriendRequestVO[]>([])
const requestsLoading = ref(false)

// 黑名单
const blocks = ref<BlockVO[]>([])
const blocksLoading = ref(false)

// 关注 / 粉丝
const followingList = ref<FollowVO[]>([])
const followersList = ref<FollowVO[]>([])
const followLoading = ref(false)
const fansLoading = ref(false)

// 我的小队
const myTeams = ref<any[]>([])
const teamsLoading = ref(false)

// 修改备注弹窗
const remarkVisible = ref(false)
const remarkForm = reactive({ userId: 0, remark: '', groupTag: '' })

// 添加好友弹窗
const addVisible = ref(false)
const addForm = reactive({ accountId: '', message: '' })
const searchResult = ref<UserBrief | null>(null)
const searching = ref(false)

async function loadFriends() {
  friendsLoading.value = true
  try {
    friends.value = await socialApi.getFriends()
  } finally {
    friendsLoading.value = false
  }
}

async function loadRequests() {
  requestsLoading.value = true
  try {
    const res = await socialApi.getFriendRequests({ page: 1, size: 50 })
    requests.value = res.list
  } finally {
    requestsLoading.value = false
  }
}

async function loadBlocks() {
  blocksLoading.value = true
  try {
    blocks.value = await socialApi.getBlocks()
  } finally {
    blocksLoading.value = false
  }
}

async function loadFollows() {
  followLoading.value = true
  try {
    followingList.value = await socialApi.getFollows({ type: 'FOLLOWING' })
  } finally {
    followLoading.value = false
  }
}

async function loadFans() {
  fansLoading.value = true
  try {
    followersList.value = await socialApi.getFollows({ type: 'FOLLOWERS' })
  } finally {
    fansLoading.value = false
  }
}

async function loadMyTeams() {
  teamsLoading.value = true
  try {
    const me = auth.user?.id
    if (me) myTeams.value = await http.get<any, any[]>(`/users/${me}/teams`)
  } finally {
    teamsLoading.value = false
  }
}

function onTabChange(t: string) {
  tab.value = t
  if (t === 'friends') loadFriends()
  else if (t === 'requests') loadRequests()
  else if (t === 'following') loadFollows()
  else if (t === 'fans') loadFans()
  else if (t === 'blocks') loadBlocks()
  else if (t === 'teams') loadMyTeams()
}

async function acceptRequest(id: number) {
  await socialApi.acceptRequest(id)
  ElMessage.success('已接受')
  loadRequests()
  loadFriends()
}

async function rejectRequest(id: number) {
  await socialApi.rejectRequest(id)
  ElMessage.success('已拒绝')
  loadRequests()
}

function openRemark(friend: FriendVO) {
  remarkForm.userId = friend.userId
  remarkForm.remark = friend.remark || ''
  remarkForm.groupTag = friend.groupTag || ''
  remarkVisible.value = true
}

async function submitRemark() {
  await socialApi.updateFriend(remarkForm.userId, {
    remark: remarkForm.remark || undefined,
    groupTag: remarkForm.groupTag || undefined,
  })
  ElMessage.success('已更新')
  remarkVisible.value = false
  loadFriends()
}

async function deleteFriend(userId: number) {
  await ElMessageBox.confirm('确认删除该好友？删除后双方好友关系解除', '提示')
  await socialApi.deleteFriend(userId)
  ElMessage.success('已删除')
  loadFriends()
}

async function blockFriend(userId: number) {
  await ElMessageBox.confirm('确认拉黑？拉黑后好友关系解除', '提示')
  await socialApi.block(userId)
  ElMessage.success('已拉黑')
  loadFriends()
}

async function unblock(userId: number) {
  await socialApi.unblock(userId)
  ElMessage.success('已解除')
  loadBlocks()
}

async function unfollow(userId: number) {
  await ElMessageBox.confirm('取消关注后，如果是互关好友，好友关系也会解除', '提示')
  await socialApi.unfollow(userId)
  ElMessage.success('已取消关注')
  loadFollows()
  loadFriends()
}

async function followUser(userId: number) {
  await socialApi.follow(userId)
  ElMessage.success('已关注（互相关注自动成为好友）')
  loadFans()
  loadFriends()
}

function openAdd() {
  addForm.accountId = ''
  addForm.message = ''
  searchResult.value = null
  addVisible.value = true
}

async function searchUser() {
  if (!addForm.accountId.trim()) {
    ElMessage.warning('请输入趣聚号')
    return
  }
  searching.value = true
  try {
    searchResult.value = await socialApi.searchUser(addForm.accountId.trim())
  } catch {
    searchResult.value = null
  } finally {
    searching.value = false
  }
}

async function submitAdd() {
  if (!searchResult.value) {
    ElMessage.warning('请先搜索用户')
    return
  }
  await socialApi.sendFriendRequest({ toUserId: searchResult.value.id, source: 'PROFILE', message: addForm.message || undefined })
  ElMessage.success('申请已发送')
  addVisible.value = false
}

function goChat(userId: number) {
  router.push(`/social/chat/${userId}`)
}

function goProfile(userId: number) {
  router.push(`/social/user/${userId}`)
}

function scanQrFromImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const img = new Image()
  img.onload = () => {
    const canvas = document.createElement('canvas')
    canvas.width = img.width
    canvas.height = img.height
    const ctx = canvas.getContext('2d')!
    ctx.drawImage(img, 0, 0)
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
    const code = jsQR(imageData.data, imageData.width, imageData.height)
    if (code?.data) {
      // 尝试解析 URL 中的用户 ID
      const match = code.data.match(/\/social\/user\/(\d+)/)
      if (match) {
        router.push(`/social/user/${match[1]}`)
      } else {
        ElMessage.info(`识别到内容: ${code.data}`)
      }
    } else {
      ElMessage.warning('未识别到二维码，请选择清晰的二维码图片')
    }
  }
  img.src = URL.createObjectURL(file)
  input.value = ''
}

onMounted(() => {
  if (tab.value === 'requests') loadRequests()
  else if (tab.value === 'following') loadFollows()
  else if (tab.value === 'fans') loadFans()
  else if (tab.value === 'blocks') loadBlocks()
  else if (tab.value === 'teams') loadMyTeams()
  else loadFriends()
})
</script>

<template>
  <div class="social-hub">
    <div class="hub-header">
      <h2>社交</h2>
      <div class="hub-actions">
        <label class="scan-btn">
          <span>📷 扫码加好友</span>
          <input type="file" accept="image/*" hidden @change="scanQrFromImage" />
        </label>
        <el-button type="primary" size="small" @click="openAdd">搜索用户</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="好友" name="friends">
        <div v-loading="friendsLoading" class="list">
          <div v-if="!friends.length && !friendsLoading" class="empty">暂无好友</div>
          <div v-for="f in friends" :key="f.userId" class="card">
            <div class="info" @click="goProfile(f.userId)" style="cursor: pointer">
              <el-avatar :size="40" :src="f.avatar" />
              <div class="text">
                <strong>{{ f.remark || f.nickname || "未知用户" }}</strong>
                <span v-if="f.remark && f.nickname" class="sub">{{ f.nickname }}</span>
                <span class="sub">{{ f.groupTag || '' }}</span>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="primary" @click="goChat(f.userId)">发消息</el-button>
              <el-button text size="small" @click="openRemark(f)">备注</el-button>
              <el-button text size="small" type="warning" @click="blockFriend(f.userId)">拉黑</el-button>
              <el-button text size="small" type="danger" @click="deleteFriend(f.userId)">删除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="好友申请" name="requests">
        <div v-loading="requestsLoading" class="list">
          <div v-if="!requests.length && !requestsLoading" class="empty">暂无好友申请</div>
          <div v-for="r in requests" :key="r.id" class="card">
            <div class="info" style="cursor: pointer" @click="goProfile(r.fromUserId)">
              <el-avatar :size="40" :src="r.fromAvatar" />
              <div class="text">
                <strong class="link-name">{{ r.fromNickname || "未知用户" }}</strong>
                <span class="sub">{{ r.message || '请求加为好友' }}</span>
              </div>
            </div>
            <div class="actions">
              <template v-if="r.status === 'PENDING'">
                <el-button size="small" type="success" @click="acceptRequest(r.id)">接受</el-button>
                <el-button size="small" type="info" @click="rejectRequest(r.id)">拒绝</el-button>
              </template>
              <el-tag v-else size="small">{{ r.status === 'ACCEPTED' ? '已接受' : '已拒绝' }}</el-tag>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="关注" name="following">
        <div v-loading="followLoading" class="list">
          <div v-if="!followingList.length && !followLoading" class="empty">暂未关注任何人</div>
          <div v-for="f in followingList" :key="f.userId" class="card">
            <div class="info" style="cursor: pointer" @click="goProfile(f.userId)">
              <el-avatar :size="40" :src="f.avatar" />
              <div class="text">
                <strong class="link-name">{{ f.nickname || "未知用户" }}</strong>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="danger" @click="unfollow(f.userId)">取消关注</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="粉丝" name="fans">
        <div v-loading="fansLoading" class="list">
          <div v-if="!followersList.length && !fansLoading" class="empty">暂无粉丝</div>
          <div v-for="f in followersList" :key="f.userId" class="card">
            <div class="info" style="cursor: pointer" @click="goProfile(f.userId)">
              <el-avatar :size="40" :src="f.avatar" />
              <div class="text">
                <strong class="link-name">{{ f.nickname || "未知用户" }}</strong>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="success" @click="followUser(f.userId)">回关</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="黑名单" name="blocks">
        <div v-loading="blocksLoading" class="list">
          <div v-if="!blocks.length && !blocksLoading" class="empty">暂无黑名单</div>
          <div v-for="b in blocks" :key="b.userId" class="card">
            <div class="info">
              <el-avatar :size="40" :src="b.avatar" />
              <div class="text">
                <strong>{{ b.nickname || "未知用户" }}</strong>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="success" @click="unblock(b.userId)">解除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的小队" name="teams">
        <div v-loading="teamsLoading" class="list">
          <div v-if="!myTeams.length && !teamsLoading" class="empty">暂未加入小队</div>
          <div v-for="t in myTeams" :key="t.id" class="card">
            <div class="info" style="cursor: pointer" @click="router.push(`/teams?detail=${t.id}`)">
              <div class="text">
                <strong>{{ t.name }}</strong>
                <span class="sub">{{ t.memberCount || 0 }} 人</span>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="primary" @click="router.push(`/social/team-chat/${t.id}`)">群聊</el-button>
              <el-button text size="small" @click="router.push(`/teams?detail=${t.id}`)">详情</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 修改备注弹窗 -->
    <el-dialog v-model="remarkVisible" title="修改备注" width="360px">
      <el-form label-width="60px">
        <el-form-item label="备注">
          <el-input v-model="remarkForm.remark" placeholder="好友备注" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="remarkForm.groupTag" placeholder="分组标签" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="remarkVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRemark">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加好友弹窗 -->
    <el-dialog v-model="addVisible" title="添加好友" width="400px">
      <el-form label-width="72px">
        <el-form-item label="趣聚号">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="addForm.accountId" placeholder="输入对方趣聚号" @keyup.enter="searchUser" />
            <el-button type="primary" :loading="searching" @click="searchUser">搜索</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="searchResult" label="搜索结果">
          <div class="search-result">
            <el-avatar :size="32" :src="searchResult.avatar" />
            <span>{{ searchResult.nickname || searchResult.accountId }}</span>
            <el-tag size="small">{{ searchResult.accountId }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="验证消息">
          <el-input v-model="addForm.message" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!searchResult" @click="submitAdd">发送申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.social-hub { max-width: 700px; margin: 0 auto; padding: 16px; }
.hub-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.hub-header h2 { margin: 0; }
.hub-actions { display: flex; align-items: center; gap: 8px; }
.scan-btn { cursor: pointer; font-size: 13px; color: #409eff; padding: 6px 12px; border: 1px solid #dcdfe6; border-radius: 4px; }
.scan-btn:hover { background: #ecf5ff; }
.list { min-height: 100px; }
.empty { text-align: center; color: #999; padding: 32px 0; }
.card { display: flex; align-items: center; justify-content: space-between; padding: 12px; border-bottom: 1px solid #f0f0f0; }
.card:last-child { border-bottom: none; }
.info { display: flex; align-items: center; gap: 12px; }
.text { display: flex; flex-direction: column; }
.text strong { font-size: 14px; }
.sub { font-size: 12px; color: #999; }
.actions { display: flex; gap: 4px; }
.search-result { display: flex; align-items: center; gap: 8px; }
.link-name { color: #409eff; }
.link-name:hover { text-decoration: underline; }
</style>
