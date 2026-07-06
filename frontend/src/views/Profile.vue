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
  interestTags: [] as string[],
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
  form.interestTags = (auth.user as any)?.interestTags || []
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
    qrDataUrl.value = await QRCode.toDataURL(url, { width: 200, margin: 1, color: { dark: '#1b1c18', light: '#ffffff' } })
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

const auditTagType = computed(() => {
  const s = merchant.value?.auditStatus
  return s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'danger' : 'warning'
})
</script>

<template>
  <div v-if="auth.user" class="profile">
    <!-- 通行证 -->
    <section class="pass">
      <div class="pass-top">
        <span class="pass-eyebrow">QUJU · {{ isMerchant ? '商家通行证' : '会员通行证' }}</span>
        <span class="pass-serial">NO. {{ auth.user.accountId || '—' }}</span>
      </div>
      <div class="pass-body">
        <div class="pass-id">
          <div class="avatar-wrap">
            <el-avatar :size="72" :src="auth.user.avatar" class="pass-avatar" />
            <label class="avatar-btn" :class="{ busy: uploading }">
              {{ uploading ? '…' : '换' }}
              <input type="file" accept="image/*" hidden @change="handleAvatarChange" :disabled="uploading" />
            </label>
          </div>
          <div class="pass-who">
            <h2>{{ (isMerchant ? merchantForm.nickname || merchantForm.merchantName : form.nickname) || '未设置昵称' }}</h2>
            <p class="pass-sign">{{ form.signature || (isMerchant ? '经营你的第一场官方活动' : '写一句签名，让同行者记住你') }}</p>
            <div class="pass-chips">
              <span class="chip">{{ isMerchant ? '商家' : '个人' }}</span>
              <span class="chip mono">{{ auth.user.email }}</span>
              <span v-if="!isMerchant" class="chip stamp">信誉 {{ auth.user.reputation }}</span>
              <span v-if="isMerchant && merchant?.auditStatus" class="chip" :class="'audit-' + auditTagType">
                {{ auditStatusLabel[merchant.auditStatus] || merchant.auditStatus }}
              </span>
            </div>
          </div>
        </div>
        <div v-if="qrDataUrl" class="pass-qr">
          <img :src="qrDataUrl" alt="我的二维码" />
          <span>扫码加我</span>
        </div>
      </div>
      <p v-if="merchant?.auditStatus === 'REJECTED' && merchant?.auditReason" class="pass-reject">
        驳回原因：{{ merchant.auditReason }}
      </p>
    </section>

    <!-- 编辑区 -->
    <section class="editor">
      <div class="editor-head">
        <span class="editor-eyebrow">{{ isMerchant ? '经营资料' : '个人档案' }}</span>
        <h3>{{ isMerchant ? '完善商家信息' : '编辑我的资料' }}</h3>
      </div>

      <!-- 商家资料 -->
      <el-form v-if="isMerchant" label-position="top" @submit.prevent>
        <el-form-item label="商家名称" required><el-input v-model="merchantForm.merchantName" size="large" placeholder="营业执照上的名称" /></el-form-item>
        <el-form-item label="对外昵称"><el-input v-model="merchantForm.nickname" size="large" placeholder="展示给用户的昵称" /></el-form-item>
        <el-form-item label="关注领域"><el-input v-model="merchantForm.focusFields" type="textarea" :rows="2" placeholder="如：运动健身、户外徒步、桌游聚会" /></el-form-item>
        <el-form-item label="营业执照">
          <div class="license-upload">
            <el-image v-if="merchantForm.licenseUrl" :src="merchantForm.licenseUrl" fit="cover" class="license-preview"
              :preview-src-list="[merchantForm.licenseUrl]" preview-teleported />
            <span v-else class="muted">尚未上传</span>
            <label class="upload-btn">
              {{ licenseUploading ? '上传中…' : (merchantForm.licenseUrl ? '重新选择' : '＋ 选择图片') }}
              <input type="file" accept="image/*" hidden @change="handleLicenseChange" :disabled="licenseUploading" />
            </label>
          </div>
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" @click="saveMerchant">保存</el-button>
      </el-form>

      <!-- 个人资料 -->
      <el-form v-else label-position="top" @submit.prevent>
        <div class="grid-2">
          <el-form-item label="趣聚号"><el-input v-model="form.accountId" size="large" placeholder="4-32位，字母或数字" /></el-form-item>
          <el-form-item label="昵称"><el-input v-model="form.nickname" size="large" placeholder="你的昵称" /></el-form-item>
          <el-form-item label="性别">
            <el-select v-model="form.gender" size="large" style="width:100%">
              <el-option label="未知" value="UNKNOWN" />
              <el-option label="男" value="MALE" />
              <el-option label="女" value="FEMALE" />
            </el-select>
          </el-form-item>
          <el-form-item label="生日">
            <el-date-picker v-model="form.birthday" type="date" size="large" value-format="YYYY-MM-DD" placeholder="出生日期" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="签名"><el-input v-model="form.signature" type="textarea" :rows="2" placeholder="写一句个性签名" /></el-form-item>
        <el-form-item label="兴趣标签">
          <el-select v-model="form.interestTags" multiple filterable allow-create default-first-option size="large" placeholder="输入后回车添加标签" style="width:100%">
            <el-option v-for="tag in ['运动','徒步','桌游','读书','音乐','摄影','美食','旅行','编程','电影','游戏','公益']" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>

        <div class="privacy">
          <span class="privacy-label">隐私 · 谁能看到你</span>
          <div class="privacy-row">
            <span>在主页展示我参加的活动</span>
            <el-switch v-model="form.privacySettings.showActivities" />
          </div>
          <div class="privacy-row">
            <span>在主页展示我加入的小队</span>
            <el-switch v-model="form.privacySettings.showTeams" />
          </div>
        </div>

        <el-button type="primary" size="large" :loading="loading" @click="save">保存</el-button>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.profile { max-width: 640px; margin: 28px auto; padding: 0 16px; display: flex; flex-direction: column; gap: 18px; }
@keyframes qj-rise { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }

/* —— 通行证 —— */
.pass {
  position: relative;
  border-radius: var(--radius);
  overflow: hidden;
  background: linear-gradient(158deg, #fff7f3 0%, #fdf4e7 58%, #f4f7f2 100%);
  color: var(--ink);
  padding: 22px 24px 24px;
  box-shadow: var(--shadow-hover);
  animation: qj-rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}
.pass::before {
  content: '';
  position: absolute; left: 0; top: 0; bottom: 0; width: 4px;
  background: var(--signal);
}
.pass-top { display: flex; justify-content: space-between; align-items: center; font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; color: var(--ink-faint); }
.pass-serial { color: var(--stamp); }
.pass-body { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-top: 18px; }
.pass-id { display: flex; gap: 16px; align-items: center; min-width: 0; }
.avatar-wrap { position: relative; flex: 0 0 auto; }
.pass-avatar { border: 2px solid rgba(255,255,255,0.9); }
.avatar-btn {
  position: absolute; right: -4px; bottom: -4px;
  width: 26px; height: 26px; border-radius: 50%;
  background: var(--signal); color: var(--ink);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; cursor: pointer; border: 2px solid var(--ink);
  transition: transform 0.15s ease;
}
.avatar-btn:hover { transform: scale(1.08); }
.avatar-btn.busy { opacity: 0.7; }
.pass-who { min-width: 0; }
.pass-who h2 { margin: 0; font-size: 24px; color: var(--ink); letter-spacing: 0.01em; }
.pass-sign { margin: 6px 0 10px; font-size: 13px; color: var(--ink-soft); line-height: 1.5; }
.pass-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  font-size: 12px; padding: 3px 9px; border-radius: 20px;
  border: 1px solid var(--line-strong); color: var(--ink);
}
.chip.mono { font-family: var(--font-mono); font-size: 11px; }
.chip.stamp { border-color: transparent; background: var(--stamp-wash); color: var(--stamp); }
.chip.audit-success { border-color: transparent; background: var(--route-wash); color: var(--route); }
.chip.audit-warning { border-color: transparent; background: var(--stamp-wash); color: var(--stamp); }
.chip.audit-danger { border-color: transparent; background: var(--signal-wash); color: var(--signal-ink); }
.pass-qr { flex: 0 0 auto; display: flex; flex-direction: column; align-items: center; gap: 5px; }
.pass-qr img { width: 78px; height: 78px; border-radius: 8px; display: block; background: #ffffff; padding: 3px; }
.pass-qr span { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.04em; color: var(--ink-faint); }
.pass-reject { margin: 16px 0 0; font-size: 12.5px; color: var(--signal-ink); border-top: 1px dashed var(--line); padding-top: 12px; }

/* —— 编辑区 —— */
.editor {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 24px;
  box-shadow: var(--shadow);
  animation: qj-rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
  animation-delay: 90ms;
}
.editor-head { margin-bottom: 18px; }
.editor-eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; color: var(--ink-faint); }
.editor-head h3 { margin: 4px 0 0; font-size: 20px; color: var(--ink); }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }

.privacy { border-top: 1px solid var(--line); margin: 6px 0 20px; padding-top: 16px; }
.privacy-label { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.06em; text-transform: uppercase; color: var(--ink-faint); }
.privacy-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; font-size: 14px; color: var(--ink-soft); }
.privacy-row + .privacy-row { border-top: 1px dashed var(--line); }

.license-upload { display: flex; align-items: center; gap: 12px; }
.license-preview { width: 84px; height: 84px; border-radius: var(--radius-sm); border: 1px solid var(--line); }
.muted { color: var(--ink-faint); font-size: 13px; }
.upload-btn {
  font-family: var(--font-mono); font-size: 13px; padding: 8px 14px;
  border-radius: var(--radius-sm); border: 1px dashed var(--line-strong);
  color: var(--ink-soft); cursor: pointer; transition: all 0.15s ease;
}
.upload-btn:hover { border-color: var(--signal); color: var(--signal); }

@media (max-width: 560px) {
  .grid-2 { grid-template-columns: 1fr; }
  .pass-qr { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .pass, .editor { animation: none; }
}
</style>
