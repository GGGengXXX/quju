<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'

const route = useRoute()
const router = useRouter()
const msg = ref('正在激活…')

onMounted(async () => {
  const token = route.query.token as string
  if (!token) { msg.value = '缺少激活令牌'; return }
  try {
    await authApi.activate(token)
    msg.value = '激活成功！即将跳转登录…'
    ElMessage.success('激活成功')
    setTimeout(() => router.push('/login'), 1500)
  } catch {
    msg.value = '激活失败：令牌无效或已过期'
  }
})
</script>

<template>
  <div class="center">
    <h2>趣聚 · 账号激活</h2>
    <p>{{ msg }}</p>
    <router-link to="/login">返回登录</router-link>
  </div>
</template>

<style scoped>.center{max-width:480px;margin:120px auto;text-align:center}</style>
