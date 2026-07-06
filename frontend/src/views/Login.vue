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
      <div class="poster-aura"><span class="blob b1" /><span class="blob b2" /></div>
      <span class="deco-pin p1" /><span class="deco-pin p2" />
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
  animation: qj-deck-in 0.55s cubic-bezier(0.2, 0.8, 0.3, 1) both;
}
@keyframes qj-deck-in { from { opacity: 0; transform: translateY(18px) scale(0.985); } to { opacity: 1; transform: none; } }

/* 海报面 */
.auth-poster {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  padding: 34px 32px;
  background: linear-gradient(158deg, #fff7f3 0%, #fdf4e7 58%, #f4f7f2 100%);
  color: var(--ink);
  
  
}
.poster-aura { position: absolute; inset: 0; z-index: 0; }
.poster-aura .blob { position: absolute; border-radius: 50%; filter: blur(48px); opacity: 0.55; }
.poster-aura .b1 { width: 240px; height: 240px; background: rgba(255,67,36,0.16); top: -70px; left: -50px; animation: qj-float-a 15s ease-in-out infinite; }
.poster-aura .b2 { width: 210px; height: 210px; background: rgba(200,134,13,0.16); bottom: -80px; right: -40px; animation: qj-float-b 19s ease-in-out infinite; }
@keyframes qj-float-a { 0%,100% { transform: translate(0,0); } 50% { transform: translate(30px, 24px); } }
@keyframes qj-float-b { 0%,100% { transform: translate(0,0); } 50% { transform: translate(-26px, -20px); } }
.deco-pin { position: absolute; z-index: 1; width: 12px; height: 12px; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); opacity: 0.7; }
.deco-pin.p1 { top: 24%; right: 16%; background: var(--signal); animation: qj-bob 6.5s ease-in-out infinite; }
.deco-pin.p2 { top: 70%; right: 26%; background: var(--stamp); width: 9px; height: 9px; animation: qj-bob 8s ease-in-out infinite 0.6s; }
@keyframes qj-bob { 0%,100% { transform: rotate(-45deg) translateY(0); } 50% { transform: rotate(-45deg) translateY(-8px); } }
.poster-top, .poster-mid, .poster-list { position: relative; z-index: 2; }
.poster-top { display: flex; justify-content: space-between; font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.06em; color: var(--ink-faint); }
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
.auth-poster h1 { font-size: 58px; margin: 0 0 14px; letter-spacing: 0.04em; color: var(--ink); }
.auth-poster p { margin: 0; font-size: 17px; line-height: 1.6; color: var(--ink-soft); }
.poster-list { list-style: none; margin: 0; padding: 18px 0 0; border-top: 1px dashed var(--line); display: flex; flex-direction: column; gap: 11px; }
.poster-list li { font-size: 13.5px; color: var(--ink-soft); display: flex; gap: 12px; }
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
@media (prefers-reduced-motion: reduce) {
  .auth, .poster-aura .blob, .deco-pin { animation: none; }
}
</style>
