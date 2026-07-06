<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminAuthStore } from '../../stores/adminAuth'

const adminAuth = useAdminAuthStore()
const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await adminAuth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch {
    // http 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-auth">
    <div class="deck">
      <!-- 左：控制台标识 -->
      <aside class="deck-side">
        <div class="aura">
          <span class="blob b1" />
          <span class="blob b2" />
        </div>
        <span class="deco-pin p1" />
        <span class="deco-pin p2" />
        <span class="deco-pin p3" />

        <div class="side-inner">
          <div class="side-top">
            <span class="pin" />
            <span class="side-code">QUJU · CONTROL</span>
          </div>
          <div class="side-mid">
            <span class="eyebrow">ADMIN CONSOLE / 调度台</span>
            <h1>城市在<br />你的掌控中</h1>
            <p>用户、商家、活动、小队与举报 —— 一个后台，统管趣聚的全部秩序。</p>
          </div>
          <ul class="side-stats">
            <li><span>USERS</span>用户与信誉</li>
            <li><span>AUDIT</span>商家 / 活动审核</li>
            <li><span>SAFETY</span>举报与下架</li>
          </ul>
        </div>
      </aside>

      <!-- 右：登录 -->
      <section class="deck-form">
        <div class="form-head">
          <span class="eyebrow">SECURE LOGIN</span>
          <h2>管理员登录</h2>
          <p>请使用后台账号登录，操作将被审计记录。</p>
        </div>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" size="large" placeholder="admin" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="输入密码" @keyup.enter="submit" />
          </el-form-item>
          <el-button type="primary" size="large" :loading="loading" style="width:100%" @click="submit">进入控制台</el-button>
        </el-form>
        <router-link to="/login" class="back-link">← 返回普通用户登录</router-link>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-auth {
  min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  padding: 24px;
  background: var(--paper);
  background-image: radial-gradient(rgba(27,28,24,0.04) 1px, transparent 1px);
  background-size: 24px 24px;
}
.deck {
  display: grid; grid-template-columns: 1.05fr 1fr;
  width: 100%; max-width: 900px;
  border: 1px solid var(--line); border-radius: 22px; overflow: hidden;
  background: var(--surface);
  box-shadow: var(--shadow-hover);
  animation: deckIn 0.55s cubic-bezier(0.2, 0.8, 0.3, 1) both;
}
@keyframes deckIn { from { opacity: 0; transform: translateY(18px) scale(0.985); } to { opacity: 1; transform: none; } }
.eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; }

/* 左侧：暖色控制台面 */
.deck-side {
  position: relative; overflow: hidden;
  background: linear-gradient(158deg, #fff7f3 0%, #fdf4e7 58%, #f4f7f2 100%);
}
.deck-side::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--signal); z-index: 4; }
.aura { position: absolute; inset: 0; z-index: 1; }
.blob { position: absolute; border-radius: 50%; filter: blur(50px); }
.b1 { width: 280px; height: 280px; background: rgba(255,67,36,0.16); top: -70px; left: -40px; animation: floatA 15s ease-in-out infinite; }
.b2 { width: 240px; height: 240px; background: rgba(200,134,13,0.16); bottom: -90px; right: -30px; animation: floatB 18s ease-in-out infinite; }
@keyframes floatA { 0%,100% { transform: translate(0,0); } 50% { transform: translate(30px, 26px); } }
@keyframes floatB { 0%,100% { transform: translate(0,0); } 50% { transform: translate(-26px, -22px); } }

.deco-pin {
  position: absolute; z-index: 2;
  width: 14px; height: 14px; border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg); opacity: 0.5;
}
.p1 { top: 20%; right: 22%; background: var(--signal); animation: bob 6s ease-in-out infinite; }
.p2 { top: 62%; right: 14%; background: var(--stamp); width: 10px; height: 10px; animation: bob 7.5s ease-in-out infinite 0.5s; }
.p3 { top: 78%; left: 20%; background: var(--route); width: 11px; height: 11px; animation: bob 8s ease-in-out infinite 1s; }
@keyframes bob { 0%,100% { transform: rotate(-45deg) translateY(0); } 50% { transform: rotate(-45deg) translateY(-9px); } }

.side-inner {
  position: relative; z-index: 3;
  display: flex; flex-direction: column; justify-content: space-between; gap: 28px;
  height: 100%; padding: 36px 34px; color: var(--ink);
}
.side-top { display: flex; align-items: center; gap: 10px; }
.pin { width: 16px; height: 16px; border-radius: 50% 50% 50% 0; background: var(--signal); transform: rotate(-45deg); box-shadow: inset -2px -2px 0 rgba(0,0,0,0.14); }
.side-code { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.12em; color: var(--ink-faint); }
.side-mid .eyebrow { color: var(--signal-ink); }
.side-mid h1 { margin: 12px 0 14px; font-size: 40px; line-height: 1.12; color: var(--ink); letter-spacing: 0.01em; }
.side-mid p { margin: 0; font-size: 14px; line-height: 1.65; color: var(--ink-soft); max-width: 320px; }
.side-stats { list-style: none; margin: 0; padding: 20px 0 0; border-top: 1px dashed var(--line-strong); display: flex; flex-direction: column; gap: 12px; }
.side-stats li { display: flex; align-items: baseline; gap: 14px; font-size: 13.5px; color: var(--ink-soft); }
.side-stats span { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.06em; color: var(--stamp); min-width: 62px; }

/* 右侧表单面 */
.deck-form { background: var(--surface); padding: 40px 40px 32px; display: flex; flex-direction: column; }
.form-head { margin-bottom: 22px; }
.form-head .eyebrow { color: var(--signal-ink); }
.form-head h2 { margin: 8px 0 6px; font-size: 26px; color: var(--ink); }
.form-head p { margin: 0; font-size: 13.5px; color: var(--ink-soft); line-height: 1.5; }
.back-link { margin-top: 20px; font-size: 13px; color: var(--ink-faint); text-decoration: none; }
.back-link:hover { color: var(--signal); }

@media (max-width: 720px) {
  .deck { grid-template-columns: 1fr; max-width: 420px; }
  .side-inner { padding: 28px 26px; }
  .side-mid h1 { font-size: 32px; }
  .side-stats { display: none; }
  .deck-form { padding: 30px 26px; }
}
@media (prefers-reduced-motion: reduce) {
  .deck, .blob, .deco-pin { animation: none; }
}
</style>
