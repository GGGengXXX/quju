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
  background: #100f0a;
  background-image: radial-gradient(rgba(255,255,255,0.04) 1px, transparent 1px);
  background-size: 24px 24px;
}
.deck {
  display: grid; grid-template-columns: 1.05fr 1fr;
  width: 100%; max-width: 900px;
  border: 1px solid rgba(242,241,234,0.12); border-radius: 20px; overflow: hidden;
  box-shadow: 0 30px 80px rgba(0,0,0,0.55);
}
.eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; }

/* 左侧控制台面 */
.deck-side {
  position: relative; display: flex; flex-direction: column; justify-content: space-between; gap: 28px;
  padding: 36px 34px; color: #f2f1ea;
  background: #16160f;
  background-image: radial-gradient(rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 20px 20px;
}
.deck-side::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: #ff5c3d; }
.side-top { display: flex; align-items: center; gap: 10px; }
.pin { width: 16px; height: 16px; border-radius: 50% 50% 50% 0; background: #ff5c3d; transform: rotate(-45deg); box-shadow: inset -2px -2px 0 rgba(0,0,0,0.18); }
.side-code { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.12em; color: rgba(242,241,234,0.5); }
.side-mid .eyebrow { color: rgba(242,241,234,0.4); }
.side-mid h1 { margin: 12px 0 14px; font-size: 40px; line-height: 1.12; color: #fff; letter-spacing: 0.01em; }
.side-mid p { margin: 0; font-size: 14px; line-height: 1.65; color: rgba(242,241,234,0.6); max-width: 320px; }
.side-stats { list-style: none; margin: 0; padding: 20px 0 0; border-top: 1px dashed rgba(242,241,234,0.16); display: flex; flex-direction: column; gap: 12px; }
.side-stats li { display: flex; align-items: baseline; gap: 14px; font-size: 13.5px; color: rgba(242,241,234,0.78); }
.side-stats span { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.06em; color: #e0a11b; min-width: 62px; }

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
  .deck-side { padding: 28px 26px; }
  .side-mid h1 { font-size: 32px; }
  .side-stats { display: none; }
  .deck-form { padding: 30px 26px; }
}
</style>
