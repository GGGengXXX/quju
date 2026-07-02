<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { socialApi } from '../../api/social'

const route = useRoute()
const router = useRouter()
const userId = Number(route.params.id)

const profile = ref<any>(null)
const activities = ref<any[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const [p, acts] = await Promise.allSettled([
      socialApi.getUserProfile(userId),
      socialApi.getUserActivities(userId),
    ])
    if (p.status === 'fulfilled') profile.value = p.value
    if (acts.status === 'fulfilled') activities.value = acts.value
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="user-profile" v-loading="loading">
    <el-button text @click="router.push('/social')" style="margin-bottom: 12px">← 返回好友列表</el-button>

    <el-card v-if="profile" class="info-card">
      <div class="profile-header">
        <el-avatar :size="64" :src="profile.avatar" />
        <div class="profile-text">
          <h2>{{ profile.nickname || '未设置昵称' }}</h2>
          <p v-if="profile.accountId" class="account-id">趣聚号: {{ profile.accountId }}</p>
          <p v-if="profile.signature" class="signature">{{ profile.signature }}</p>
        </div>
      </div>
    </el-card>

    <el-card class="section-card">
      <template #header>参加的活动（{{ activities.length }}）</template>
      <div v-if="!activities.length" class="empty">暂无参加的活动</div>
      <div v-for="act in activities" :key="act.id" class="act-item" @click="router.push('/activities')" style="cursor: pointer">
        <strong>{{ act.name }}</strong>
        <span class="meta">{{ act.category }} · {{ act.status }}</span>
        <span class="meta">{{ act.startTime?.slice(0, 10) }}</span>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.user-profile { max-width: 700px; margin: 0 auto; padding: 16px; }
.info-card { margin-bottom: 16px; }
.profile-header { display: flex; align-items: center; gap: 16px; }
.profile-text h2 { margin: 0; }
.account-id { font-size: 13px; color: #666; margin: 4px 0; }
.signature { font-size: 13px; color: #999; }
.section-card { margin-bottom: 16px; }
.empty { text-align: center; color: #999; padding: 24px 0; }
.act-item { padding: 10px 0; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center; gap: 12px; }
.act-item:last-child { border-bottom: none; }
.meta { font-size: 12px; color: #999; }
</style>
