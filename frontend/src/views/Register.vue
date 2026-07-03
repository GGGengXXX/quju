<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'

const router = useRouter()
const form = reactive({ email: '', password: '', userType: 'INDIVIDUAL', licenseUrl: '', merchantName: '' })
const loading = ref(false)

async function submit() {
  if (form.userType === 'MERCHANT') {
    if (!form.merchantName.trim()) { ElMessage.warning('请填写商家名称'); return }
    if (!form.licenseUrl.trim()) { ElMessage.warning('请上传营业执照/凭证'); return }
  }
  loading.value = true
  try {
    await authApi.register({
      email: form.email, password: form.password, userType: form.userType,
      licenseUrl: form.userType === 'MERCHANT' ? form.licenseUrl : undefined,
      merchantName: form.userType === 'MERCHANT' ? form.merchantName : undefined,
    })
    ElMessage.success(form.userType === 'MERCHANT'
      ? '注册成功，请查收激活邮件；激活后将进入后台商家审核'
      : '注册成功，请查收激活邮件')
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
      <el-form-item v-if="form.userType === 'MERCHANT'" label="商家名称"><el-input v-model="form.merchantName" placeholder="营业执照上的商家名称" /></el-form-item>
      <el-form-item v-if="form.userType === 'MERCHANT'" label="营业执照"><el-input v-model="form.licenseUrl" placeholder="凭证图片 URL" /></el-form-item>
      <el-alert v-if="form.userType === 'MERCHANT'" type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="商家账号需在邮箱激活后由平台后台审核，通过后获得商家身份" />
      <el-button type="primary" :loading="loading" style="width:100%" @click="submit">注册</el-button>
    </el-form>
    <p class="tip">已有账号？<router-link to="/login">去登录</router-link></p>
  </el-card>
</template>

<style scoped>.box{max-width:460px;margin:50px auto}.tip{text-align:center;margin-top:12px}</style>
