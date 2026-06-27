<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const form = reactive({ email: '', password: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(form.email, form.password)
    ElMessage.success('登录成功')
    router.push('/profile')
  } catch { /* http 拦截器已提示 */ } finally { loading.value = false }
}
</script>

<template>
  <el-card class="box">
    <h2>登录</h2>
    <el-form label-width="64px" @submit.prevent>
      <el-form-item label="邮箱"><el-input v-model="form.email" placeholder="you@example.com" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
      <el-button type="primary" :loading="loading" style="width:100%" @click="submit">登录</el-button>
    </el-form>
    <p class="tip">没有账号？<router-link to="/register">去注册</router-link></p>
  </el-card>
</template>

<style scoped>.box{max-width:420px;margin:60px auto}.tip{text-align:center;margin-top:12px}</style>
