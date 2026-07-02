<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'

const auth = useAuthStore()
const form = reactive({ accountId: '', nickname: '', gender: 'UNKNOWN', signature: '' })
const loading = ref(false)
const uploading = ref(false)

onMounted(async () => {
  await auth.loadMe()
  form.accountId = auth.user?.accountId || ''
  form.nickname = auth.user?.nickname || ''
  form.gender = auth.user?.gender || 'UNKNOWN'
  form.signature = auth.user?.signature || ''
})

async function save() {
  loading.value = true
  try {
    auth.user = await authApi.updateMe(form)
    ElMessage.success('已保存')
  } catch { /* 已提示 */ } finally { loading.value = false }
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const { url } = await authApi.uploadImage(file)
    auth.user = await authApi.updateMe({ avatar: url })
    ElMessage.success('头像已更新')
  } catch { /* 已提示 */ } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <el-card class="box" v-if="auth.user">
    <h2>我的资料</h2>

    <div class="avatar-section">
      <el-avatar :size="80" :src="auth.user.avatar" />
      <label class="avatar-btn">
        <span>{{ uploading ? '上传中...' : '更换头像' }}</span>
        <input type="file" accept="image/*" hidden @change="handleAvatarChange" :disabled="uploading" />
      </label>
    </div>

    <el-descriptions :column="1" border style="margin-top: 16px">
      <el-descriptions-item label="趣聚号">{{ auth.user.accountId || '未设置' }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ auth.user.email }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ auth.user.userType }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ auth.user.status }}</el-descriptions-item>
      <el-descriptions-item label="信誉">{{ auth.user.reputation }}</el-descriptions-item>
    </el-descriptions>
    <el-form label-width="64px" style="margin-top:16px" @submit.prevent>
      <el-form-item label="趣聚号"><el-input v-model="form.accountId" placeholder="4-32位，字母或数字" /></el-form-item>
      <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
      <el-form-item label="性别">
        <el-select v-model="form.gender">
          <el-option label="未知" value="UNKNOWN" />
          <el-option label="男" value="MALE" />
          <el-option label="女" value="FEMALE" />
        </el-select>
      </el-form-item>
      <el-form-item label="签名"><el-input v-model="form.signature" type="textarea" :rows="2" /></el-form-item>
      <el-button type="primary" :loading="loading" @click="save">保存</el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
.box { max-width: 560px; margin: 30px auto; }
.avatar-section { display: flex; align-items: center; gap: 16px; }
.avatar-btn { color: #409eff; cursor: pointer; font-size: 14px; }
.avatar-btn:hover { text-decoration: underline; }
</style>
