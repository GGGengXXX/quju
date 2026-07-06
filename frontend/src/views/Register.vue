<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'

const router = useRouter()
const form = reactive({ email: '', password: '', userType: 'INDIVIDUAL', licenseUrl: '', merchantName: '' })
const loading = ref(false)
const uploading = ref(false)

async function handleLicenseChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const res = await authApi.uploadLicense(file)
    form.licenseUrl = (res as any)?.url || res
    ElMessage.success('营业执照已上传')
  } catch (err: any) {
    ElMessage.error('上传失败: ' + (err?.message || '未知错误'))
  } finally {
    uploading.value = false
    input.value = ''
  }
}

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
  <div class="auth">
    <aside class="auth-poster">
      <div class="poster-top">
        <span class="poster-eyebrow">QUJU · 加入寻趣</span>
        <span class="poster-coord">NEW · MEMBER</span>
      </div>
      <div class="poster-mid">
        <span class="poster-pin"></span>
        <h1>开始<br />你的第一场</h1>
        <p>个人用户即刻出发；<br />商家审核通过后可发布官方活动。</p>
      </div>
      <ul class="poster-list">
        <li><span>01</span>邮箱注册并激活账号</li>
        <li><span>02</span>完善兴趣标签与资料</li>
        <li><span>03</span>发现并报名身边活动</li>
      </ul>
    </aside>

    <section class="auth-form">
      <div class="form-head">
        <h2>创建账号</h2>
        <p>用邮箱注册，激活后即可加入趣聚。</p>
      </div>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="邮箱"><el-input v-model="form.email" size="large" placeholder="you@example.com" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password placeholder="至少 8 位" /></el-form-item>
        <el-form-item label="账号类型">
          <el-radio-group v-model="form.userType">
            <el-radio value="INDIVIDUAL">个人用户</el-radio>
            <el-radio value="MERCHANT">商家用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.userType === 'MERCHANT'" label="商家名称"><el-input v-model="form.merchantName" size="large" placeholder="营业执照上的商家名称" /></el-form-item>
        <el-form-item v-if="form.userType === 'MERCHANT'" label="营业执照 / 凭证">
          <div class="license-upload">
            <el-image v-if="form.licenseUrl" :src="form.licenseUrl" fit="cover" class="license-preview"
              :preview-src-list="[form.licenseUrl]" preview-teleported />
            <label class="upload-btn">
              <span>{{ uploading ? '上传中…' : (form.licenseUrl ? '重新选择' : '＋ 选择图片') }}</span>
              <input type="file" accept="image/*" hidden @change="handleLicenseChange" :disabled="uploading" />
            </label>
          </div>
        </el-form-item>
        <el-alert v-if="form.userType === 'MERCHANT'" type="info" :closable="false" show-icon style="margin-bottom:14px"
          title="商家账号需邮箱激活后由平台后台审核，通过后获得商家身份" />
        <el-button type="primary" size="large" :loading="loading" style="width:100%" @click="submit">注册</el-button>
      </el-form>
      <div class="form-foot">
        <span>已有账号？<router-link to="/login">去登录</router-link></span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  max-width: 940px;
  margin: 40px auto;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-hover);
}
.auth-poster {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 28px;
  padding: 34px 32px;
  background: #17170f; /* 固定深色"墨板"，两种主题下保持白字高对比 */
  color: #f2f1ea;
  background-image: radial-gradient(rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 20px 20px;
}
.poster-top { display: flex; justify-content: space-between; font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.06em; color: rgba(255,255,255,0.55); }
.poster-coord { color: var(--signal); }
.poster-pin {
  display: inline-block;
  width: 20px; height: 20px;
  border-radius: 50% 50% 50% 0;
  background: var(--signal);
  transform: rotate(-45deg);
  margin-bottom: 22px;
  box-shadow: inset -3px -3px 0 rgba(0,0,0,0.15);
}
.auth-poster h1 { font-size: 40px; margin: 0 0 14px; line-height: 1.15; color: #fff; }
.auth-poster p { margin: 0; font-size: 16px; line-height: 1.6; color: rgba(255,255,255,0.75); }
.poster-list { list-style: none; margin: 0; padding: 18px 0 0; border-top: 1px dashed rgba(255,255,255,0.18); display: flex; flex-direction: column; gap: 11px; }
.poster-list li { font-size: 13.5px; color: rgba(255,255,255,0.82); display: flex; gap: 12px; align-items: baseline; }
.poster-list span { font-family: var(--font-mono); font-size: 12px; color: var(--stamp); }

.auth-form { padding: 40px 40px 32px; display: flex; flex-direction: column; }
.form-head h2 { font-size: 28px; margin: 0 0 6px; }
.form-head p { margin: 0 0 22px; color: var(--ink-soft); font-size: 14px; }
.license-upload { display: flex; align-items: center; gap: 12px; }
.license-preview { width: 84px; height: 84px; border-radius: var(--radius-sm); border: 1px solid var(--line); }
.upload-btn {
  font-family: var(--font-mono);
  font-size: 13px;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--line-strong);
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.15s ease;
}
.upload-btn:hover { border-color: var(--signal); color: var(--signal); }
.form-foot { margin-top: 20px; padding-top: 18px; border-top: 1px solid var(--line); font-size: 14px; color: var(--ink-soft); }

@media (max-width: 760px) {
  .auth { grid-template-columns: 1fr; margin: 16px auto; }
  .auth-poster { padding: 26px 24px; }
  .auth-poster h1 { font-size: 32px; }
  .poster-list { display: none; }
  .auth-form { padding: 28px 22px; }
}
</style>
