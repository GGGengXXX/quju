<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useAdminAuthStore } from '../stores/adminAuth'

const auth = useAuthStore()
const adminAuth = useAdminAuthStore()
const route = useRoute()
const router = useRouter()
const form = reactive({ email: '', password: '' })
const loading = ref(false)

const demoAccounts = {
  owner: { label: 'Owner Demo', email: 'activity.demo.owner@example.com', password: 'Pass123456!' },
  member: { label: 'Member Demo', email: 'activity.demo.member@example.com', password: 'Pass123456!' },
} as const

function fillDemo(email: string, password: string) {
  form.email = email
  form.password = password
}

function resolveNextPath(fallback = '/activities') {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  return redirect || fallback
}

async function submit(nextPath?: string) {
  loading.value = true
  try {
    await auth.login(form.email, form.password)
    ElMessage.success('登录成功')
    router.push(nextPath || resolveNextPath())
  } catch {
    // http 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function loginDemo(type: keyof typeof demoAccounts) {
  const account = demoAccounts[type]
  fillDemo(account.email, account.password)
  await submit(resolveNextPath(type === 'member' ? '/activities' : '/teams'))
}

async function loginAdmin() {
  loading.value = true
  try {
    await adminAuth.login('admin', '123456Aa')
    ElMessage.success('管理员登录成功')
    router.push('/admin/dashboard')
  } catch {
    // http 拦截器已提示
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!(import.meta as any).env?.DEV) return
  const demo = typeof route.query.demo === 'string' ? route.query.demo : ''
  if (demo !== 'owner' && demo !== 'member') return
  await loginDemo(demo)
})
</script>

<template>
  <div class="auth">
    <!-- 左：身份/寻趣海报 -->
    <aside class="auth-poster">
      <div class="poster-top">
        <span class="poster-eyebrow">QUJU · 城市寻趣</span>
        <span class="poster-coord">N39.90 · E116.40</span>
      </div>
      <div class="poster-mid">
        <span class="poster-pin"></span>
        <h1>趣聚</h1>
        <p>以兴趣为纽带，<br />把线上浏览变成线下相遇。</p>
      </div>
      <ul class="poster-list">
        <li><span>周六 09:00</span>香山徒步 · 剩 6 位</li>
        <li><span>周日 14:30</span>咖啡桌游局 · 报名中</li>
        <li><span>今晚 19:00</span>河畔夜跑 · 就在附近</li>
      </ul>
    </aside>

    <!-- 右：登录表单 -->
    <section class="auth-form">
      <div class="form-head">
        <h2>欢迎回来</h2>
        <p>登录后继续发现身边的活动与伙伴。</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="邮箱"><el-input v-model="form.email" size="large" placeholder="you@example.com" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password placeholder="输入密码" /></el-form-item>
        <el-button type="primary" size="large" :loading="loading" style="width:100%" @click="submit()">登录</el-button>
      </el-form>

      <div class="demo-block">
        <span class="demo-label">快速体验</span>
        <div class="demo-row">
          <button class="chip" @click="loginDemo('owner')">{{ demoAccounts.owner.label }}</button>
          <button class="chip" @click="loginDemo('member')">{{ demoAccounts.member.label }}</button>
          <button class="chip" @click="loginAdmin">Admin Demo</button>
        </div>
      </div>

      <div class="form-foot">
        <span>还没有账号？<router-link to="/register">去注册</router-link></span>
        <router-link to="/admin/login" class="admin-link">管理员登录</router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  max-width: 940px;
  margin: 40px auto;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-hover);
}

/* 海报面 */
.auth-poster {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  padding: 34px 32px;
  background: #17170f; /* 固定深色"墨板"，两种主题下保持白字高对比 */
  color: #f2f1ea;
  background-image: radial-gradient(rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 20px 20px;
}
.poster-top { display: flex; justify-content: space-between; font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.06em; color: rgba(255,255,255,0.55); }
.poster-coord { color: var(--signal); }
.poster-mid { margin-top: 8px; }
.poster-pin {
  display: inline-block;
  width: 20px; height: 20px;
  border-radius: 50% 50% 50% 0;
  background: var(--signal);
  transform: rotate(-45deg);
  margin-bottom: 22px;
  box-shadow: inset -3px -3px 0 rgba(0,0,0,0.15);
}
.auth-poster h1 { font-size: 58px; margin: 0 0 14px; letter-spacing: 0.04em; color: #fff; }
.auth-poster p { margin: 0; font-size: 17px; line-height: 1.6; color: rgba(255,255,255,0.75); }
.poster-list { list-style: none; margin: 0; padding: 18px 0 0; border-top: 1px dashed rgba(255,255,255,0.18); display: flex; flex-direction: column; gap: 11px; }
.poster-list li { font-size: 13.5px; color: rgba(255,255,255,0.82); display: flex; gap: 12px; }
.poster-list span { font-family: var(--font-mono); font-size: 12px; color: var(--stamp); min-width: 76px; }

/* 表单面 */
.auth-form { padding: 40px 40px 32px; display: flex; flex-direction: column; }
.form-head h2 { font-size: 28px; margin: 0 0 6px; }
.form-head p { margin: 0 0 22px; color: var(--ink-soft); font-size: 14px; }
.demo-block { margin-top: 18px; padding-top: 18px; border-top: 1px solid var(--line); }
.demo-label { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ink-faint); }
.demo-row { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.chip {
  font-family: var(--font-mono);
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid var(--line-strong);
  background: var(--surface-2);
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.15s ease;
}
.chip:hover { border-color: var(--signal); color: var(--signal); }
.form-foot { margin-top: auto; padding-top: 24px; display: flex; justify-content: space-between; align-items: center; font-size: 14px; color: var(--ink-soft); }
.admin-link { color: var(--ink-faint); font-size: 13px; }

@media (max-width: 760px) {
  .auth { grid-template-columns: 1fr; margin: 16px auto; }
  .auth-poster { padding: 26px 24px; }
  .auth-poster h1 { font-size: 44px; }
  .poster-list { display: none; }
  .auth-form { padding: 28px 22px; }
}
</style>
