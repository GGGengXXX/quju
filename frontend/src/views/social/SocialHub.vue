<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import {
  socialApi,
  type FriendVO,
  type FriendRequestVO,
  type FollowVO,
  type BlockVO,
} from '../../api/social'

const auth = useAuthStore()
const router = useRouter()
const tab = ref('friends')

// 好友列表
const friends = ref<FriendVO[]>([])
const friendsLoading = ref(false)

// 好友申请
const requests = ref<FriendRequestVO[]>([])
const requestsLoading = ref(false)

// 关注
const followTab = ref('FOLLOWING')
const followList = ref<FollowVO[]>([])
const followLoading = ref(false)

// 黑名单
const blocks = ref<BlockVO[]>([])
const blocksLoading = ref(false)

// 修改备注弹窗
const remarkVisible = ref(false)
const remarkForm = reactive({ userId: 0, remark: '', groupTag: '' })

// 添加好友弹窗
const addVisible = ref(false)
const addForm = reactive({ toUserId: 0, message: '' })

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

async function loadFollows() {
  followLoading.value = true
  try {
    followList.value = await socialApi.getFollows({ type: followTab.value as 'FOLLOWING' | 'FOLLOWERS' })
  } finally {
    followLoading.value = false
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

function onTabChange(t: string) {
  tab.value = t
  if (t === 'friends') loadFriends()
  else if (t === 'requests') loadRequests()
  else if (t === 'follows') loadFollows()
  else if (t === 'blocks') loadBlocks()
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

async function unfollow(userId: number) {
  await socialApi.unfollow(userId)
  ElMessage.success('已取关')
  loadFollows()
}

async function unblock(userId: number) {
  await socialApi.unblock(userId)
  ElMessage.success('已解除')
  loadBlocks()
}

function openAdd() {
  addForm.toUserId = 0
  addForm.message = ''
  addVisible.value = true
}

async function submitAdd() {
  if (!addForm.toUserId) {
    ElMessage.warning('请输入用户ID')
    return
  }
  await socialApi.sendFriendRequest({ toUserId: addForm.toUserId, source: 'PROFILE', message: addForm.message || undefined })
  ElMessage.success('申请已发送')
  addVisible.value = false
}

function goChat(userId: number) {
  router.push(`/social/chat/${userId}`)
}

onMounted(loadFriends)
</script>

<template>
  <div class="social-hub">
    <div class="hub-header">
      <h2>社交</h2>
      <el-button type="primary" size="small" @click="openAdd">添加好友</el-button>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="好友" name="friends">
        <div v-loading="friendsLoading" class="list">
          <div v-if="!friends.length && !friendsLoading" class="empty">暂无好友</div>
          <div v-for="f in friends" :key="f.userId" class="card">
            <div class="info">
              <el-avatar :size="40" :src="f.avatar" />
              <div class="text">
                <strong>{{ f.remark || f.nickname || f.userId }}</strong>
                <span v-if="f.remark && f.nickname" class="sub">{{ f.nickname }}</span>
                <span class="sub">{{ f.groupTag || '' }}</span>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="primary" @click="goChat(f.userId)">发消息</el-button>
              <el-button text size="small" @click="openRemark(f)">备注</el-button>
              <el-button text size="small" type="danger" @click="deleteFriend(f.userId)">删除</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="好友申请" name="requests">
        <div v-loading="requestsLoading" class="list">
          <div v-if="!requests.length && !requestsLoading" class="empty">暂无申请</div>
          <div v-for="r in requests" :key="r.id" class="card">
            <div class="info">
              <el-avatar :size="40" :src="r.fromAvatar" />
              <div class="text">
                <strong>{{ r.fromNickname || r.fromUserId }}</strong>
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

      <el-tab-pane label="关注" name="follows">
        <el-radio-group v-model="followTab" style="margin-bottom: 12px" @change="loadFollows">
          <el-radio-button value="FOLLOWING">我关注的</el-radio-button>
          <el-radio-button value="FOLLOWERS">关注我的</el-radio-button>
        </el-radio-group>
        <div v-loading="followLoading" class="list">
          <div v-if="!followList.length && !followLoading" class="empty">暂无数据</div>
          <div v-for="f in followList" :key="f.userId" class="card">
            <div class="info">
              <el-avatar :size="40" :src="f.avatar" />
              <div class="text">
                <strong>{{ f.nickname || f.userId }}</strong>
              </div>
            </div>
            <div class="actions">
              <el-button v-if="followTab === 'FOLLOWING'" text size="small" type="danger" @click="unfollow(f.userId)">取关</el-button>
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
                <strong>{{ b.nickname || b.userId }}</strong>
              </div>
            </div>
            <div class="actions">
              <el-button text size="small" type="success" @click="unblock(b.userId)">解除</el-button>
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
    <el-dialog v-model="addVisible" title="添加好友" width="360px">
      <el-form label-width="72px">
        <el-form-item label="用户ID">
          <el-input-number v-model="addForm.toUserId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="验证消息">
          <el-input v-model="addForm.message" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">发送申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.social-hub { max-width: 700px; margin: 0 auto; padding: 16px; }
.hub-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.hub-header h2 { margin: 0; }
.list { min-height: 100px; }
.empty { text-align: center; color: #999; padding: 32px 0; }
.card { display: flex; align-items: center; justify-content: space-between; padding: 12px; border-bottom: 1px solid #f0f0f0; }
.card:last-child { border-bottom: none; }
.info { display: flex; align-items: center; gap: 12px; }
.text { display: flex; flex-direction: column; }
.text strong { font-size: 14px; }
.sub { font-size: 12px; color: #999; }
.actions { display: flex; gap: 4px; }
</style>
