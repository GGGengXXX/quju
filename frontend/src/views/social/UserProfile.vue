<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { socialApi } from '../../api/social'
import http from '../../api/http'

const route = useRoute()
const router = useRouter()
const userId = Number(route.params.id)

const profile = ref<any>(null)
const activities = ref<any[]>([])
const teams = ref<any[]>([])
const loading = ref(true)
const isFriend = ref(false)
const addingFriend = ref(false)
const following = ref(false)

function privacyShow(key: string) {
  if (!profile.value?.privacySettings) return true
  return profile.value.privacySettings[key] !== false
}

async function load() {
  loading.value = true
  try {
    const [p, friendList, followingList] = await Promise.all([
      socialApi.getUserProfile(userId),
      socialApi.getFriends(),
      socialApi.getFollows({ type: 'FOLLOWING' }),
    ])
    profile.value = p
    isFriend.value = friendList.some((f: any) => f.userId === userId)
    following.value = followingList.some((f: any) => f.userId === userId)

    const fetches: Promise<any>[] = []
    fetches.push(http.get<any, any[]>(`/users/${userId}/activities`).then(r => { activities.value = r }).catch(() => {}))
    fetches.push(http.get<any, any[]>(`/users/${userId}/teams`).then(r => { teams.value = r }).catch(() => {}))
    await Promise.allSettled(fetches)
  } finally {
    loading.value = false
  }
}

async function addFriend() {
  addingFriend.value = true
  try {
    await socialApi.sendFriendRequest({ toUserId: userId, source: 'PROFILE' })
    ElMessage.success('好友申请已发送')
  } catch { /* 已提示 */ } finally {
    addingFriend.value = false
  }
}

async function toggleFollow() {
  if (following.value) {
    await socialApi.unfollow(userId)
    following.value = false
    ElMessage.success('已取消关注')
  } else {
    await socialApi.follow(userId)
    following.value = true
    ElMessage.success('已关注（互相关注可自动成为好友）')
  }
}

function goActivity(actId: number) {
  router.push(`/activities?detail=${actId}`)
}

function goTeam(teamId: number) {
  router.push(`/teams?detail=${teamId}`)
}

onMounted(load)
</script>

<template>
  <div class="user-profile" v-loading="loading">
    <el-button text @click="router.back()" style="margin-bottom: 12px">← 返回</el-button>

    <el-card v-if="profile" class="info-card">
      <div class="profile-header">
        <el-avatar :size="64" :src="profile.avatar" />
        <div class="profile-text">
          <h2>{{ profile.nickname || '未设置昵称' }}</h2>
          <p v-if="profile.accountId" class="account-id">趣聚号: {{ profile.accountId }}</p>
          <p v-if="profile.signature" class="signature">{{ profile.signature }}</p>
        </div>
        <div class="profile-actions">
          <el-button v-if="isFriend" type="primary" size="small" @click="router.push(`/social/chat/${userId}`)">发消息</el-button>
          <el-button :type="following ? 'info' : 'warning'" size="small" plain @click="toggleFollow">{{ following ? '已关注' : '关注' }}</el-button>
          <el-tag v-if="isFriend" size="small" type="success">互关好友</el-tag>
        </div>
      </div>
    </el-card>

    <el-card v-if="privacyShow('showActivities')" class="section-card">
      <template #header>参加的活动（{{ activities.length }}）</template>
      <div v-if="!activities.length" class="empty">暂无参加的活动</div>
      <div v-for="act in activities" :key="act.id" class="item" @click="goActivity(act.id)">
        <strong>{{ act.name }}</strong>
        <span class="meta">{{ act.category }} · {{ act.status }}</span>
        <span class="meta">{{ act.startTime?.slice(0, 10) }}</span>
      </div>
    </el-card>
    <el-card v-else class="section-card"><div class="empty">该用户已隐藏活动</div></el-card>

    <el-card v-if="privacyShow('showTeams')" class="section-card">
      <template #header>加入的小队（{{ teams.length }}）</template>
      <div v-if="!teams.length" class="empty">暂无加入的小队</div>
      <div v-for="t in teams" :key="t.id" class="item" @click="goTeam(t.id)">
        <strong>{{ t.name }}</strong>
        <span class="meta">{{ t.memberCount || 0 }} 人 · {{ t.status }}</span>
      </div>
    </el-card>
    <el-card v-else class="section-card"><div class="empty">该用户已隐藏小队</div></el-card>
  </div>
</template>

<style scoped>
.user-profile { max-width: 700px; margin: 0 auto; padding: 16px; }
.info-card { margin-bottom: 16px; }
.profile-header { display: flex; align-items: center; gap: 16px; }
.profile-text { flex: 1; }
.profile-text h2 { margin: 0; }
.account-id { font-size: 13px; color: #666; margin: 4px 0; }
.signature { font-size: 13px; color: #999; }
.section-card { margin-bottom: 16px; }
.empty { text-align: center; color: #999; padding: 24px 0; }
.item { padding: 10px 0; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center; gap: 12px; cursor: pointer; }
.item:hover { background: #f9f9f9; }
.item:last-child { border-bottom: none; }
.meta { font-size: 12px; color: #999; }
</style>
