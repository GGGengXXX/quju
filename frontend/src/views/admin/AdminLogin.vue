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
  <el-card class="box">
    <h2>管理员登录</h2>
    <el-form label-width="72px" @submit.prevent="submit">
      <el-form-item label="用户名">
        <el-input v-model="form.username" placeholder="admin" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-button type="primary" :loading="loading" style="width:100%" @click="submit">登录</el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
.box { max-width: 400px; margin: 80px auto; }
h2 { text-align: center; margin-bottom: 24px; }
</style>
