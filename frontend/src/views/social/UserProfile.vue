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
  <div class="visitor" v-loading="loading">
    <button class="back" @click="router.back()">← 返回</button>

    <!-- 访客通行证 -->
    <section v-if="profile" class="pass">
      <div class="pass-top">
        <span class="pass-eyebrow">QUJU · 访客档案</span>
        <span class="pass-serial">NO. {{ profile.accountId || '—' }}</span>
      </div>
      <div class="pass-body">
        <el-avatar :size="72" :src="profile.avatar" class="pass-avatar" />
        <div class="pass-who">
          <div class="who-name">
            <h2>{{ profile.nickname || '未设置昵称' }}</h2>
            <span v-if="isFriend" class="mutual">互关好友</span>
          </div>
          <p class="pass-sign">{{ profile.signature || '这个人还没有留下签名。' }}</p>
        </div>
      </div>
      <div class="pass-acts">
        <button v-if="isFriend" class="act primary" @click="router.push(`/social/chat/${userId}`)">发消息</button>
        <button v-if="!isFriend" class="act primary" :disabled="addingFriend" @click="addFriend">＋ 加好友</button>
        <button class="act" :class="{ on: following }" @click="toggleFollow">{{ following ? '已关注' : '关注' }}</button>
      </div>
    </section>

    <!-- 足迹：活动 -->
    <section class="record">
      <div class="record-head">
        <span class="record-eyebrow">TA 的足迹</span>
        <h3>参加的活动 <em>{{ privacyShow('showActivities') ? activities.length : '—' }}</em></h3>
      </div>
      <div v-if="!privacyShow('showActivities')" class="empty">该用户已隐藏活动</div>
      <div v-else-if="!activities.length" class="empty">还没有参加过活动</div>
      <ul v-else class="trail">
        <li v-for="act in activities" :key="act.id" @click="goActivity(act.id)">
          <span class="trail-when">{{ act.startTime?.slice(5, 10) || '待定' }}</span>
          <div class="trail-body">
            <strong>{{ act.name }}</strong>
            <span class="trail-meta">{{ act.category }} · {{ act.status }}</span>
          </div>
          <span class="trail-go">›</span>
        </li>
      </ul>
    </section>

    <!-- 足迹：小队 -->
    <section class="record">
      <div class="record-head">
        <span class="record-eyebrow">TA 的队伍</span>
        <h3>加入的小队 <em>{{ privacyShow('showTeams') ? teams.length : '—' }}</em></h3>
      </div>
      <div v-if="!privacyShow('showTeams')" class="empty">该用户已隐藏小队</div>
      <div v-else-if="!teams.length" class="empty">还没有加入小队</div>
      <ul v-else class="trail">
        <li v-for="t in teams" :key="t.id" @click="goTeam(t.id)">
          <span class="trail-when squad">队</span>
          <div class="trail-body">
            <strong>{{ t.name }}</strong>
            <span class="trail-meta">{{ t.memberCount || 0 }} 人 · {{ t.status }}</span>
          </div>
          <span class="trail-go">›</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.visitor { max-width: 640px; margin: 24px auto; padding: 0 16px; display: flex; flex-direction: column; gap: 16px; }
@keyframes qj-rise { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }
.back {
  align-self: flex-start; background: none; border: none; cursor: pointer;
  font-family: var(--font-mono); font-size: 12px; color: var(--ink-soft); padding: 4px 0;
}
.back:hover { color: var(--signal); }

/* —— 访客通行证 —— */
.pass {
  position: relative; overflow: hidden;
  background: linear-gradient(158deg, #fff7f3 0%, #fdf4e7 58%, #f4f7f2 100%); color: var(--ink);
  border-radius: var(--radius); padding: 22px 24px;
  box-shadow: var(--shadow-hover);
  animation: qj-rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}
.pass::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--route); }
.pass-top { display: flex; justify-content: space-between; font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; color: var(--ink-faint); }
.pass-serial { color: var(--stamp); }
.pass-body { display: flex; align-items: center; gap: 16px; margin-top: 16px; }
.pass-avatar { border: 2px solid rgba(255,255,255,0.9); flex: 0 0 auto; }
.pass-who { min-width: 0; }
.who-name { display: flex; align-items: center; gap: 10px; }
.who-name h2 { margin: 0; font-size: 24px; color: var(--ink); }
.mutual { font-size: 11px; padding: 2px 8px; border-radius: 20px; background: var(--route-wash); color: var(--route); }
.pass-sign { margin: 6px 0 0; font-size: 13px; color: var(--ink-soft); line-height: 1.5; }
.pass-acts { display: flex; gap: 8px; margin-top: 18px; }
.act {
  font-size: 13px; padding: 8px 16px; border-radius: 22px; cursor: pointer;
  border: 1px solid var(--line-strong); background: transparent; color: var(--ink);
  transition: all 0.15s ease;
}
.act:hover { border-color: var(--ink); }
.act.primary { background: var(--signal); border-color: var(--signal); color: var(--ink); }
.act.primary:hover { background: var(--signal-ink); }
.act.primary:disabled { opacity: 0.6; cursor: default; }
.act.on { background: var(--surface-2); }

/* —— 足迹 —— */
.record {
  background: var(--surface); border: 1px solid var(--line);
  border-radius: var(--radius); padding: 20px 22px; box-shadow: var(--shadow);
  animation: qj-rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}
.record:nth-of-type(2) { animation-delay: 90ms; }
.record:nth-of-type(3) { animation-delay: 160ms; }
.record-head { margin-bottom: 4px; }
.record-eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ink-faint); }
.record-head h3 { margin: 3px 0 0; font-size: 18px; color: var(--ink); display: flex; align-items: baseline; gap: 8px; }
.record-head em { font-family: var(--font-mono); font-style: normal; font-size: 14px; color: var(--signal); }
.empty { color: var(--ink-faint); font-size: 13px; padding: 18px 0 6px; }
.trail { list-style: none; margin: 8px 0 0; padding: 0; }
.trail li {
  display: flex; align-items: center; gap: 14px; padding: 12px 0; cursor: pointer;
  border-top: 1px dashed var(--line);
}
.trail li:hover .trail-body strong { color: var(--signal); }
.trail-when {
  flex: 0 0 auto; font-family: var(--font-mono); font-size: 12px; color: var(--ink-soft);
  min-width: 44px; text-align: center; padding: 4px 0; border-radius: 6px; background: var(--surface-2);
}
.trail-when.squad { color: var(--route); background: var(--route-wash); }
.trail-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.trail-body strong { font-size: 14.5px; color: var(--ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; transition: color 0.15s ease; }
.trail-meta { font-size: 12px; color: var(--ink-faint); }
.trail-go { flex: 0 0 auto; font-size: 20px; color: var(--ink-faint); }

@media (max-width: 560px) {
  .pass-acts { flex-wrap: wrap; }
}
@media (prefers-reduced-motion: reduce) {
  .pass, .record { animation: none; }
}
</style>
