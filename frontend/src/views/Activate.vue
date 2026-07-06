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
  <div class="activate">
    <span class="pin"></span>
    <span class="eyebrow">QUJU · 账号激活</span>
    <h2>{{ msg }}</h2>
    <router-link to="/login" class="back">返回登录</router-link>
  </div>
</template>

<style scoped>
.activate {
  max-width: 440px;
  margin: 100px auto;
  padding: 40px 32px;
  text-align: center;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: var(--shadow);
}
.pin {
  display: inline-block;
  width: 22px; height: 22px;
  border-radius: 50% 50% 50% 0;
  background: var(--signal);
  transform: rotate(-45deg);
  margin-bottom: 20px;
  box-shadow: inset -3px -3px 0 rgba(0,0,0,0.12);
}
.eyebrow { display: block; font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; color: var(--ink-faint); text-transform: uppercase; margin-bottom: 10px; }
.activate h2 { margin: 0 0 22px; font-size: 22px; }
.back { font-size: 14px; }
</style>
