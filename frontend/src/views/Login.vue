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
  await submit(type === 'member' ? resolveNextPath('/activities') : '/teams')
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
  <el-card class="box">
    <h2>登录</h2>
    <el-form label-width="64px" @submit.prevent>
      <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="you@example.com" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
      <el-button type="primary" :loading="loading" style="width:100%" @click="submit()">登录</el-button>
    </el-form>
    <div class="demo-row">
      <el-button text @click="loginDemo('owner')">
        {{ demoAccounts.owner.label }}
      </el-button>
      <el-button text @click="loginDemo('member')">
        {{ demoAccounts.member.label }}
      </el-button>
      <el-button text @click="loginAdmin">
        Admin Demo
      </el-button>
    </div>
    <p class="tip">没有账号？<router-link to="/register">去注册</router-link></p>
    <p class="tip"><router-link to="/admin/login">管理员登录</router-link></p>
  </el-card>
</template>

<style scoped>
.box{max-width:420px;margin:60px auto}
.tip{text-align:center;margin-top:12px}
.demo-row{display:flex;justify-content:center;gap:8px;margin-top:12px;flex-wrap:wrap}
</style>
