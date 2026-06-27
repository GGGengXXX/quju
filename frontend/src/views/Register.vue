<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'

const router = useRouter()
const form = reactive({ email: '', password: '', userType: 'INDIVIDUAL', licenseUrl: '' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await authApi.register({
      email: form.email, password: form.password, userType: form.userType,
      licenseUrl: form.licenseUrl || undefined,
    })
    ElMessage.success('注册成功，请查收激活邮件')
    router.push('/login')
  } catch { /* 已提示 */ } finally { loading.value = false }
}
</script>

<template>
  <el-card class="box">
    <h2>注册</h2>
    <el-form label-width="84px" @submit.prevent>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" /></el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="form.userType">
          <el-radio value="INDIVIDUAL">个人</el-radio>
          <el-radio value="MERCHANT">商家</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.userType === 'MERCHANT'" label="营业执照"><el-input v-model="form.licenseUrl" placeholder="凭证图片 URL" /></el-form-item>
      <el-button type="primary" :loading="loading" style="width:100%" @click="submit">注册</el-button>
    </el-form>
    <p class="tip">已有账号？<router-link to="/login">去登录</router-link></p>
  </el-card>
</template>

<style scoped>.box{max-width:460px;margin:50px auto}.tip{text-align:center;margin-top:12px}</style>
