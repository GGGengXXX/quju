<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'
import { merchantApi, type MerchantVO } from '../api/merchant'

const auth = useAuthStore()
const isMerchant = computed(() => auth.user?.userType === 'MERCHANT')
const qrDataUrl = ref('')

const form = reactive({
  accountId: '',
  nickname: '',
  gender: 'UNKNOWN',
  birthday: '',
  signature: '',
  privacySettings: { showActivities: true, showTeams: true } as Record<string, boolean>,
})
const merchantForm = reactive({ merchantName: '', nickname: '', focusFields: '', licenseUrl: '' })
const merchant = ref<MerchantVO | null>(null)
const loading = ref(false)
const uploading = ref(false)
const licenseUploading = ref(false)

const auditStatusLabel: Record<string, string> = {
  PENDING: '审核中', APPROVED: '已通过', REJECTED: '已驳回',
}

onMounted(async () => {
  await auth.loadMe()
  form.accountId = auth.user?.accountId || ''
  form.nickname = auth.user?.nickname || ''
  form.gender = auth.user?.gender || 'UNKNOWN'
  form.birthday = auth.user?.birthday || ''
  form.signature = auth.user?.signature || ''
  if ((auth.user as any)?.privacySettings) {
    form.privacySettings = { showActivities: true, showTeams: true, ...(auth.user as any).privacySettings }
  }
  if (isMerchant.value) {
    try {
      merchant.value = await merchantApi.getMyProfile()
      merchantForm.merchantName = merchant.value?.merchantName || ''
      merchantForm.nickname = merchant.value?.nickname || ''
      merchantForm.focusFields = merchant.value?.focusFields || ''
      merchantForm.licenseUrl = merchant.value?.licenseUrl || ''
    } catch { /* 尚无商家资料时忽略 */ }
  }
  // 生成个人二维码
  if (auth.user?.id) {
    const url = `${window.location.origin}/social/user/${auth.user.id}`
    qrDataUrl.value = await QRCode.toDataURL(url, { width: 200 })
  }
})

async function save() {
  loading.value = true
  try {
    auth.user = await authApi.updateMe({
      accountId: form.accountId,
      nickname: form.nickname,
      gender: form.gender,
      birthday: form.birthday || undefined,
      signature: form.signature,
      privacySettings: form.privacySettings,
    })
    ElMessage.success('已保存')
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function saveMerchant() {
  if (!merchantForm.merchantName.trim()) { ElMessage.warning('请填写商家名称'); return }
  loading.value = true
  try {
    merchant.value = await merchantApi.updateProfile({
      merchantName: merchantForm.merchantName,
      nickname: merchantForm.nickname || undefined,
      focusFields: merchantForm.focusFields || undefined,
      licenseUrl: merchantForm.licenseUrl || undefined,
    })
    ElMessage.success('已保存')
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function handleLicenseChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  licenseUploading.value = true
  try {
    const res = await authApi.uploadLicense(file)
    merchantForm.licenseUrl = (res as any)?.url || res
    ElMessage.success('营业执照已上传，保存后将重新提交审核')
  } catch (err: any) {
    ElMessage.error('上传失败: ' + (err?.message || '未知错误'))
  } finally {
    licenseUploading.value = false
    input.value = ''
  }
}

async function handleAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const res = await authApi.uploadImage(file)
    const url = res?.url || res
    auth.user = await authApi.updateMe({ avatar: url as string })
    ElMessage.success('头像已更新')
  } catch (err: any) {
    ElMessage.error('上传失败: ' + (err?.message || '未知错误'))
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <el-card v-if="auth.user" class="box">
    <h2>{{ isMerchant ? '我的商家资料' : '我的资料' }}</h2>

    <div class="avatar-section">
      <el-avatar :size="80" :src="auth.user.avatar" />
      <label class="avatar-btn">
        <span>{{ uploading ? '上传中...' : '更换头像' }}</span>
        <input type="file" accept="image/*" hidden @change="handleAvatarChange" :disabled="uploading" />
      </label>
      <div v-if="qrDataUrl" class="qr-section">
        <img :src="qrDataUrl" alt="我的二维码" class="qr-img" />
        <span class="qr-hint">扫码访问我的主页</span>
      </div>
    </div>

    <el-descriptions :column="1" border style="margin-top: 16px">
      <el-descriptions-item label="趣聚号">{{ auth.user.accountId || '未设置' }}</el-descriptions-item>
      <el-descriptions-item label="邮箱">{{ auth.user.email }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ isMerchant ? '商家' : '个人' }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ auth.user.status }}</el-descriptions-item>
      <el-descriptions-item v-if="!isMerchant" label="信誉">{{ auth.user.reputation }}</el-descriptions-item>
      <el-descriptions-item v-if="isMerchant" label="审核状态">
        <el-tag v-if="merchant?.auditStatus" :type="merchant.auditStatus === 'APPROVED' ? 'success' : merchant.auditStatus === 'REJECTED' ? 'danger' : 'warning'">
          {{ auditStatusLabel[merchant.auditStatus] || merchant.auditStatus }}
        </el-tag>
        <span v-else class="muted">未提交（完善资料并保存后进入后台审核）</span>
        <span v-if="merchant?.auditStatus === 'REJECTED' && merchant?.auditReason" class="reject-reason">（{{ merchant.auditReason }}）</span>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 商家资料 -->
    <el-form v-if="isMerchant" label-width="90px" style="margin-top: 16px" @submit.prevent>
      <el-form-item label="商家名称" required><el-input v-model="merchantForm.merchantName" placeholder="营业执照上的名称" /></el-form-item>
      <el-form-item label="商家昵称"><el-input v-model="merchantForm.nickname" placeholder="对外展示的昵称" /></el-form-item>
      <el-form-item label="关注领域"><el-input v-model="merchantForm.focusFields" type="textarea" :rows="2" placeholder="如：运动健身、户外徒步、桌游聚会" /></el-form-item>
      <el-form-item label="营业执照">
        <div class="license-upload">
          <el-image v-if="merchantForm.licenseUrl" :src="merchantForm.licenseUrl" fit="cover" class="license-preview"
            :preview-src-list="[merchantForm.licenseUrl]" preview-teleported />
          <span v-else class="muted">未上传</span>
          <label class="avatar-btn">
            <span>{{ licenseUploading ? '上传中...' : (merchantForm.licenseUrl ? '重新选择图片' : '选择图片') }}</span>
            <input type="file" accept="image/*" hidden @change="handleLicenseChange" :disabled="licenseUploading" />
          </label>
        </div>
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="saveMerchant">保存</el-button>
    </el-form>

    <!-- 个人资料 -->
    <el-form v-else label-width="80px" style="margin-top: 16px" @submit.prevent>
      <el-form-item label="趣聚号"><el-input v-model="form.accountId" placeholder="4-32位，字母或数字" /></el-form-item>
      <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
      <el-form-item label="性别">
        <el-select v-model="form.gender">
          <el-option label="未知" value="UNKNOWN" />
          <el-option label="男" value="MALE" />
          <el-option label="女" value="FEMALE" />
        </el-select>
      </el-form-item>
      <el-form-item label="生日">
        <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="请选择出生日期" style="width: 100%" />
      </el-form-item>
      <el-form-item label="签名"><el-input v-model="form.signature" type="textarea" :rows="2" /></el-form-item>

      <el-divider content-position="left">隐私设置</el-divider>
      <el-form-item label="展示活动">
        <el-switch v-model="form.privacySettings.showActivities" active-text="公开" inactive-text="隐藏" />
      </el-form-item>
      <el-form-item label="展示小队">
        <el-switch v-model="form.privacySettings.showTeams" active-text="公开" inactive-text="隐藏" />
      </el-form-item>

      <el-button type="primary" :loading="loading" @click="save">保存</el-button>
    </el-form>
  </el-card>
</template>

<style scoped>
.box { max-width: 560px; margin: 30px auto; }
.avatar-section { display: flex; align-items: center; gap: 16px; }
.avatar-btn { color: #409eff; cursor: pointer; font-size: 14px; }
.avatar-btn:hover { text-decoration: underline; }
.qr-section { display: flex; flex-direction: column; align-items: center; margin-left: auto; }
.qr-img { width: 100px; height: 100px; border-radius: 6px; }
.qr-hint { font-size: 11px; color: #999; margin-top: 4px; }
.muted { color: #999; }
.reject-reason { color: #f56c6c; margin-left: 6px; }
.license-upload { display: flex; align-items: center; gap: 12px; }
.license-preview { width: 80px; height: 80px; border-radius: 6px; border: 1px solid #eee; }
</style>
