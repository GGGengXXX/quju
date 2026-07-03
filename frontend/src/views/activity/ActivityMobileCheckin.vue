<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { activityApi, type ActivityDetail } from '../../api/activity'

declare global {
  interface Window {
    AMap?: any
    __qujuAmapLoading__?: Promise<any>
  }
}

const route = useRoute()
const router = useRouter()
const amapKey = (import.meta as any).env?.VITE_AMAP_KEY as string | undefined
const loading = ref(true)
const submitting = ref(false)
const locating = ref(false)
const detail = ref<ActivityDetail | null>(null)
const checkinForm = reactive({
  code: '',
  lng: undefined as number | undefined,
  lat: undefined as number | undefined,
})

const activityId = computed(() => {
  const raw = typeof route.query.activityId === 'string' ? Number(route.query.activityId) : NaN
  return Number.isFinite(raw) ? raw : null
})

function formatTime(value?: string) {
  return value ? value.replace('T', ' ') : '未设置'
}

async function loadDetail() {
  if (!activityId.value) {
    ElMessage.warning('签到链接缺少活动信息')
    loading.value = false
    return
  }
  loading.value = true
  try {
    detail.value = await activityApi.detail(activityId.value)
    const routeCode = typeof route.query.checkinCode === 'string' ? route.query.checkinCode : ''
    checkinForm.code = routeCode || detail.value.checkinCode || ''
    checkinForm.lng = detail.value.lng ?? undefined
    checkinForm.lat = detail.value.lat ?? undefined
  } finally {
    loading.value = false
  }
}

function resolveLocationFailureMessage(reason?: string) {
  if (typeof window !== 'undefined' && !window.isSecureContext && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
    return '当前页面使用的是 HTTP IP 地址，Chrome 会直接拒绝定位；这不是权限按钮问题，需要改成 HTTPS 入口'
  }
  if (reason) return reason
  return '定位失败，请检查浏览器定位权限'
}

async function ensureAmap() {
  if (!amapKey) return null
  if (window.AMap) return window.AMap
  if (!window.__qujuAmapLoading__) {
    window.__qujuAmapLoading__ = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${amapKey}`
      script.async = true
      script.onload = () => resolve(window.AMap)
      script.onerror = reject
      document.head.appendChild(script)
    })
  }
  return window.__qujuAmapLoading__
}

async function requestBrowserLocation() {
  if (!navigator.geolocation) {
    throw new Error('当前浏览器不支持定位')
  }
  return new Promise<{ lng: number; lat: number }>((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          lng: Number(position.coords.longitude.toFixed(6)),
          lat: Number(position.coords.latitude.toFixed(6)),
        })
      },
      (error) => {
        if (error?.code === 1) reject(new Error('定位权限被拒绝'))
        else if (error?.code === 2) reject(new Error('定位服务暂时不可用'))
        else if (error?.code === 3) reject(new Error('定位超时'))
        else reject(new Error(error?.message || '浏览器定位失败'))
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 0,
      }
    )
  })
}

async function requestAmapLocation() {
  const AMap = await ensureAmap()
  if (!AMap) {
    throw new Error('地图定位插件不可用')
  }
  return new Promise<{ lng: number; lat: number }>((resolve, reject) => {
    AMap.plugin('AMap.Geolocation', () => {
      const geolocation = new AMap.Geolocation({
        enableHighAccuracy: true,
        timeout: 10000,
        convert: true,
        showButton: false,
        GeoLocationFirst: true,
      })
      geolocation.getCurrentPosition((status: string, result: any) => {
        if (status === 'complete' && result?.position) {
          resolve({
            lng: Number(Number(result.position.lng).toFixed(6)),
            lat: Number(Number(result.position.lat).toFixed(6)),
          })
          return
        }
        reject(new Error(result?.message || result?.info || '高德定位失败'))
      })
    })
  })
}

async function useCurrentLocation() {
  locating.value = true
  try {
    let locationResult: { lng: number; lat: number } | null = null
    let lastError = ''
    try {
      locationResult = await requestBrowserLocation()
    } catch (error: any) {
      lastError = error?.message || ''
      try {
        locationResult = await requestAmapLocation()
      } catch (fallbackError: any) {
        lastError = fallbackError?.message || lastError
      }
    }
    if (!locationResult) {
      ElMessage.warning(resolveLocationFailureMessage(lastError))
      return
    }
    checkinForm.lng = locationResult.lng
    checkinForm.lat = locationResult.lat
    ElMessage.success('已读取当前位置')
  } finally {
    locating.value = false
  }
}

async function submitCheckin() {
  if (!detail.value?.id) return
  if (!checkinForm.code) {
    ElMessage.warning('签到码缺失，请重新扫码')
    return
  }
  submitting.value = true
  try {
    await activityApi.checkin(detail.value.id, { ...checkinForm })
    ElMessage.success('签到成功')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

function backToActivities() {
  router.push('/activities')
}

onMounted(async () => {
  await loadDetail()
  await useCurrentLocation()
})
</script>

<template>
  <div class="mobile-checkin-page">
    <div class="mobile-shell">
      <div class="brand">趣聚 QuJu</div>
      <div v-if="loading" class="state-card">正在加载签到信息...</div>
      <template v-else-if="detail">
        <section class="hero-card">
          <div class="eyebrow">活动签到</div>
          <h1>{{ detail.name }}</h1>
          <p>{{ detail.intro || '请确认定位后提交签到。' }}</p>
          <div class="meta-grid">
            <div class="meta-item">
              <span>时间</span>
              <strong>{{ formatTime(detail.startTime) }}</strong>
            </div>
            <div class="meta-item">
              <span>地点</span>
              <strong>{{ detail.city || '-' }} {{ detail.address || '' }}</strong>
            </div>
          </div>
        </section>

        <section class="action-card">
          <h2>手机签到</h2>
          <el-input v-model="checkinForm.code" placeholder="签到码" readonly />
          <div class="location-row">
            <el-button :loading="locating" @click="useCurrentLocation">读取当前位置</el-button>
            <span class="location-tip">若浏览器未弹权限，请在系统或浏览器设置中允许定位。</span>
          </div>
          <div class="coord-grid">
            <el-input-number v-model="checkinForm.lng" :precision="6" :step="0.0001" placeholder="经度" />
            <el-input-number v-model="checkinForm.lat" :precision="6" :step="0.0001" placeholder="纬度" />
          </div>
          <el-button type="primary" size="large" :loading="submitting" class="submit-btn" @click="submitCheckin">
            确认签到
          </el-button>
          <el-button text class="back-btn" @click="backToActivities">返回活动列表</el-button>
        </section>
      </template>
      <div v-else class="state-card">未找到可签到的活动信息。</div>
    </div>
  </div>
</template>

<style scoped>
.mobile-checkin-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eaf4ff 0%, #f7fbff 100%);
  padding: 20px 14px 32px;
}
.mobile-shell {
  max-width: 520px;
  margin: 0 auto;
}
.brand {
  font-size: 20px;
  font-weight: 700;
  color: #1f5fae;
  margin-bottom: 14px;
}
.hero-card,
.action-card,
.state-card {
  background: #fff;
  border: 1px solid #dbe8f5;
  border-radius: 16px;
  padding: 18px;
  box-shadow: 0 10px 30px rgba(27, 74, 124, 0.08);
}
.hero-card { margin-bottom: 14px; }
.eyebrow {
  font-size: 12px;
  color: #5b7fa6;
  margin-bottom: 8px;
}
.hero-card h1 {
  margin: 0 0 10px;
  font-size: 28px;
  line-height: 1.2;
  color: #17324d;
}
.hero-card p {
  margin: 0;
  color: #4d6580;
  line-height: 1.6;
}
.meta-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-top: 16px;
}
.meta-item {
  border: 1px solid #e6eef8;
  border-radius: 12px;
  padding: 12px;
}
.meta-item span {
  display: block;
  font-size: 12px;
  color: #6b8199;
  margin-bottom: 6px;
}
.meta-item strong {
  color: #17324d;
  line-height: 1.5;
}
.action-card h2 {
  margin: 0 0 14px;
  font-size: 24px;
  color: #17324d;
}
.location-row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
}
.location-tip {
  color: #617993;
  font-size: 13px;
  line-height: 1.5;
}
.coord-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 12px;
}
.submit-btn {
  width: 100%;
  margin-top: 16px;
  height: 46px;
}
.back-btn {
  width: 100%;
  margin-top: 8px;
}
.state-card {
  text-align: center;
  color: #4d6580;
}
@media (max-width: 640px) {
  .hero-card h1 { font-size: 24px; }
  .action-card h2 { font-size: 22px; }
  .location-row,
  .coord-grid { grid-template-columns: 1fr; }
}
</style>
