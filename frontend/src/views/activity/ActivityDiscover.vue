<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import {
  activityApi,
  type ActivityDetail,
  type ActivityItem,
  type ActivityPoint,
  type ActivityUpsertReq,
  type ReviewItem,
  type SignupManageItem,
  type SummaryItem,
  type TemplateItem,
  type WaitlistPage,
} from '../../api/activity'

declare global {
  interface Window {
    AMap?: any
    __qujuAmapLoading__?: Promise<any>
  }
}

const auth = useAuthStore()
const amapKey = (import.meta as any).env?.VITE_AMAP_KEY as string | undefined
const loading = ref(false)
const saving = ref(false)
const actionLoading = ref(false)
const mapLoading = ref(false)
const mapReady = ref(false)
const activities = ref<ActivityItem[]>([])
const mineActivities = ref<ActivityItem[]>([])
const templates = ref<TemplateItem[]>([])
const mapPoints = ref<ActivityPoint[]>([])
const total = ref(0)
const detail = ref<ActivityDetail | null>(null)
const detailVisible = ref(false)
const createVisible = ref(false)
const detailTab = ref('overview')
const summary = ref<SummaryItem | null>(null)
const reviews = ref<ReviewItem[]>([])
const signups = ref<SignupManageItem[]>([])
const waitlist = ref<WaitlistPage | null>(null)
const generatedCode = ref('')
const mapRef = ref<HTMLDivElement | null>(null)
const aiTheme = ref('')

let amap: any = null
let markers: any[] = []

const categoryOptions = [
  { label: '运动', value: 'SPORTS' },
  { label: '徒步', value: 'HIKING' },
  { label: '桌游', value: 'BOARD_GAME' },
  { label: '学习', value: 'STUDY' },
  { label: '公益', value: 'CHARITY' },
  { label: '城市漫步', value: 'CITY_WALK' },
  { label: '其他', value: 'OTHER' },
]

const query = reactive({
  tab: 'RECOMMEND',
  keyword: '',
  categories: [] as string[],
  city: '',
  feeMin: undefined as number | undefined,
  feeMax: undefined as number | undefined,
  startFrom: '',
  startTo: '',
  lng: 116.3521,
  lat: 39.9835,
  distanceKm: 5,
  page: 1,
  size: 10,
})

const form = reactive<ActivityUpsertReq>({
  name: '',
  intro: '',
  category: 'OTHER',
  tags: [],
  city: '',
  address: '',
  lng: 116.3521,
  lat: 39.9835,
  capacity: 20,
  fee: 0,
  submit: false,
})

const summaryForm = reactive({
  content: '',
  imageUrlsText: '',
})

const reviewForm = reactive({
  rating: 5,
  content: '',
})

const checkinForm = reactive({
  code: '',
  lng: 116.3521 as number | undefined,
  lat: 39.9835 as number | undefined,
})

const tagText = ref('')

const tagPreview = computed(() =>
  tagText.value
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter(Boolean)
)

const imageUrlList = computed(() =>
  summaryForm.imageUrlsText
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
)

const isOwner = computed(() => {
  if (!detail.value || !auth.user) return false
  return auth.user.id === detail.value.creator?.id
})

const canSignup = computed(() => !!detail.value && detail.value.status === 'PUBLISHED' && !detail.value.mySignupStatus)
const canCancelSignup = computed(() => detail.value?.mySignupStatus === 'REGISTERED')
const canConfirmWaitlist = computed(() => detail.value?.mySignupStatus === 'WAITLISTED')
const canReview = computed(() => !!detail.value && detail.value.phase === 'ENDED' && !!auth.token)
const canDelete = computed(() => !!detail.value && isOwner.value)
const canSubmitDraft = computed(() => !!detail.value && isOwner.value && (detail.value.status === 'DRAFT' || detail.value.status === 'REJECTED'))

const statusMap: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '审核中',
  PUBLISHED: '已发布',
  REJECTED: '已驳回',
  TAKEN_DOWN: '已下架',
  CANCELLED: '已取消',
}
const phaseMap: Record<string, string> = {
  NOT_STARTED: '未开始',
  SIGNUP_OPEN: '报名中',
  SIGNUP_CLOSED: '报名截止',
  ONGOING: '进行中',
  ENDED: '已结束',
}
function statusLabel(s: string) { return statusMap[s] || s }
function phaseLabel(s: string) { return phaseMap[s] || s }

async function loadActivities() {
  loading.value = true
  try {
    const data = await activityApi.discover({
      tab: query.tab,
      keyword: query.keyword || undefined,
      categories: query.categories.length ? query.categories.join(',') : undefined,
      city: query.city || undefined,
      feeMin: query.feeMin,
      feeMax: query.feeMax,
      startFrom: query.startFrom || undefined,
      startTo: query.startTo || undefined,
      lng: query.tab === 'NEARBY' ? query.lng : undefined,
      lat: query.tab === 'NEARBY' ? query.lat : undefined,
      distanceKm: query.tab === 'NEARBY' ? query.distanceKm : undefined,
      page: query.page,
      size: query.size,
    })
    activities.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadMine() {
  if (!auth.token) {
    mineActivities.value = []
    return
  }
  const data = await activityApi.mine({ page: 1, size: 6 })
  mineActivities.value = data.list
}

async function loadTemplates() {
  templates.value = await activityApi.templates()
}

async function loadSummary(id: number) {
  try {
    summary.value = await activityApi.getSummary(id)
    summaryForm.content = summary.value.content || ''
  } catch {
    summary.value = null
    summaryForm.content = ''
  }
}

async function loadReviews(id: number) {
  const data = await activityApi.reviews(id, { page: 1, size: 20 })
  reviews.value = data.list
}

async function loadOwnerData(id: number) {
  if (!isOwner.value) {
    signups.value = []
    waitlist.value = null
    return
  }
  const [signupData, waitData] = await Promise.all([
    activityApi.signups(id, { page: 1, size: 20 }),
    activityApi.waitlist(id),
  ])
  signups.value = signupData.list
  waitlist.value = waitData
}

async function openDetail(id: number) {
  detail.value = await activityApi.detail(id)
  detailVisible.value = true
  detailTab.value = 'overview'
  generatedCode.value = ''
  checkinForm.code = ''
  if (detail.value.lng != null) checkinForm.lng = detail.value.lng
  if (detail.value.lat != null) checkinForm.lat = detail.value.lat
  await Promise.allSettled([
    loadSummary(id),
    loadReviews(id),
    loadOwnerData(id),
  ])
  if (mapReady.value && detail.value.lng != null && detail.value.lat != null) {
    amap.setCenter([detail.value.lng, detail.value.lat])
  }
}

async function refreshDetail() {
  if (!detail.value) return
  await openDetail(detail.value.id as number)
  await Promise.allSettled([loadActivities(), loadMine(), loadMapPoints()])
}

function fillFromTemplate(template: TemplateItem) {
  form.category = template.category
  form.intro = template.defaultIntro || ''
  form.capacity = template.defaultCapacity || 20
}

function applyAiPlan(plan: ActivityItem) {
  form.name = plan.name.replace(/ · AI 初稿$/, '')
  form.intro = plan.intro || ''
  form.category = plan.category
  form.capacity = plan.capacity || 20
  form.fee = plan.fee || 0
  form.city = plan.city || ''
  form.address = plan.address || ''
  form.startTime = plan.startTime || ''
  form.endTime = plan.endTime || ''
  form.signupDeadline = plan.signupDeadline || ''
  tagText.value = plan.tags.join(', ')
  ElMessage.success('AI 初稿已填入表单，可继续修改后保存')
}

async function generateAiPlan() {
  if (!aiTheme.value.trim()) {
    ElMessage.warning('先输入活动主题')
    return
  }
  actionLoading.value = true
  try {
    const plan = await activityApi.aiPlan({ theme: aiTheme.value, category: form.category })
    applyAiPlan(plan)
  } finally {
    actionLoading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.intro = ''
  form.category = 'OTHER'
  form.tags = []
  form.city = ''
  form.address = ''
  form.lng = 116.3521
  form.lat = 39.9835
  form.capacity = 20
  form.fee = 0
  form.startTime = ''
  form.endTime = ''
  form.signupDeadline = ''
  form.submit = false
  tagText.value = ''
  aiTheme.value = ''
}

async function submitCreate(submit: boolean) {
  saving.value = true
  try {
    form.tags = tagPreview.value
    form.submit = submit
    await activityApi.create({ ...form })
    ElMessage.success(submit ? '活动已提交审核' : '草稿已保存')
    createVisible.value = false
    resetForm()
    await Promise.allSettled([loadActivities(), loadMine(), loadMapPoints()])
  } finally {
    saving.value = false
  }
}

async function signupCurrent() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    const data = await activityApi.signup(detail.value.id as number)
    ElMessage.success(data.status === 'WAITLISTED' ? `已进入候补，当前排位 ${data.waitlistPosition}` : '报名成功')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

async function cancelSignup() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.cancelSignup(detail.value.id as number)
    ElMessage.success('已取消报名')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

async function confirmWaitlist() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.confirmWaitlist(detail.value.id as number)
    ElMessage.success('候补确认成功')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

async function cloneCurrent() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.clone(detail.value.id as number)
    ElMessage.success('已克隆为新草稿')
    await Promise.allSettled([loadActivities(), loadMine()])
  } finally {
    actionLoading.value = false
  }
}

async function deleteCurrent() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.remove(detail.value.id as number)
    ElMessage.success(detail.value.status === 'PUBLISHED' ? '活动已取消' : '活动已删除')
    detailVisible.value = false
    await Promise.allSettled([loadActivities(), loadMine(), loadMapPoints()])
  } finally {
    actionLoading.value = false
  }
}

async function submitDraft() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.submit(detail.value.id as number)
    ElMessage.success('活动已提交审核')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

async function createCheckinCode() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    const data = await activityApi.generateCheckinCode(detail.value.id as number)
    generatedCode.value = data.code
    checkinForm.code = data.code
    ElMessage.success('签到码已生成')
    await loadOwnerData(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

async function submitCheckin() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.checkin(detail.value.id as number, { ...checkinForm })
    ElMessage.success('签到成功')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

async function saveSummary(publish: boolean) {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.upsertSummary(detail.value.id as number, {
      content: summaryForm.content,
      publish,
    })
    ElMessage.success(publish ? '总结已发布' : '总结草稿已保存')
    await loadSummary(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

async function uploadImages() {
  if (!detail.value || !imageUrlList.value.length) return
  actionLoading.value = true
  try {
    await activityApi.uploadSummaryImages(detail.value.id as number, imageUrlList.value)
    summaryForm.imageUrlsText = ''
    ElMessage.success('总结图片已上传')
    await loadSummary(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

function onImageCategoryChange(imageId: number, value: string | number | boolean) {
  return confirmImage(imageId, String(value))
}

async function confirmImage(imageId: number, category: string) {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.updateSummaryImage(detail.value.id as number, imageId, category)
    ElMessage.success('图片分类已确认')
    await loadSummary(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

async function submitReview() {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.upsertReview(detail.value.id as number, {
      rating: reviewForm.rating,
      content: reviewForm.content,
    })
    ElMessage.success('评价已提交')
    await loadReviews(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '未填写'
  return value.replace('T', ' ')
}

function useDemoNearby() {
  query.tab = 'NEARBY'
  query.lng = 116.3521
  query.lat = 39.9835
  query.distanceKm = 5
  loadActivities()
  loadMapPoints()
}

function currentBounds() {
  if (amap) {
    const bounds = amap.getBounds()
    if (bounds) {
      const southWest = bounds.getSouthWest()
      const northEast = bounds.getNorthEast()
      return {
        minLng: southWest.lng,
        minLat: southWest.lat,
        maxLng: northEast.lng,
        maxLat: northEast.lat,
      }
    }
  }
  const centerLng = query.lng || 116.3974
  const centerLat = query.lat || 39.9093
  return {
    minLng: centerLng - 0.18,
    maxLng: centerLng + 0.18,
    minLat: centerLat - 0.12,
    maxLat: centerLat + 0.12,
  }
}

let skipMapEvent = false

async function loadMapPoints() {
  mapLoading.value = true
  try {
    mapPoints.value = await activityApi.mapPoints(currentBounds())
    skipMapEvent = true
    renderMarkers()
  } finally {
    mapLoading.value = false
  }
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

function renderMarkers() {
  if (!amap || !window.AMap) return
  markers.forEach((marker) => marker.setMap(null))
  markers = mapPoints.value
    .filter((point) => point.lng != null && point.lat != null)
    .map((point) => {
      const marker = new window.AMap.Marker({
        map: amap,
        position: [point.lng, point.lat],
        title: point.name,
      })
      marker.on('click', () => openDetail(point.id))
      return marker
    })
}

async function initMap() {
  if (!mapRef.value || mapReady.value || !amapKey) return
  try {
    const AMap = await ensureAmap()
    if (!AMap || !mapRef.value) return
    amap = new AMap.Map(mapRef.value, {
      zoom: 11,
      center: [query.lng, query.lat],
    })
    mapReady.value = true
    amap.on('moveend', () => { if (skipMapEvent) { skipMapEvent = false; return } loadMapPoints() })
    amap.on('zoomend', () => { if (skipMapEvent) { skipMapEvent = false; return } loadMapPoints() })
    await loadMapPoints()
  } catch {
    ElMessage.warning('地图脚本加载失败，已保留活动点位列表')
  }
}

onMounted(async () => {
  if (auth.token && !auth.user) {
    try {
      await auth.loadMe()
    } catch {
      // 鉴权失败已由拦截器处理。
    }
  }
  await Promise.allSettled([loadActivities(), loadTemplates(), loadMine(), loadMapPoints()])
  await nextTick()
  await initMap()
})
</script>

<template>
  <div class="activity-page">
    <section class="toolbar panel">
      <div class="tab-row">
        <el-radio-group v-model="query.tab" size="large" @change="loadActivities">
          <el-radio-button label="RECOMMEND">推荐</el-radio-button>
          <el-radio-button label="LATEST">最新</el-radio-button>
          <el-radio-button label="NEARBY">附近</el-radio-button>
        </el-radio-group>
        <div class="toolbar-actions">
          <el-button @click="useDemoNearby">用示例坐标</el-button>
          <el-button type="primary" @click="createVisible = true">新建活动</el-button>
        </div>
      </div>

      <div class="search-grid">
        <el-input v-model="query.keyword" placeholder="搜索活动标题或简介" clearable />
        <el-select v-model="query.categories" multiple collapse-tags collapse-tags-tooltip placeholder="分类筛选">
          <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="query.city" placeholder="城市" clearable />
        <el-input-number v-model="query.feeMin" :min="0" :step="10" placeholder="最低费用" />
        <el-input-number v-model="query.feeMax" :min="0" :step="10" placeholder="最高费用" />
        <el-date-picker v-model="query.startFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间从" />
        <el-date-picker v-model="query.startTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间到" />
        <el-input-number v-model="query.lng" :precision="4" :step="0.0001" placeholder="经度" />
        <el-input-number v-model="query.lat" :precision="4" :step="0.0001" placeholder="纬度" />
        <el-input-number v-model="query.distanceKm" :min="1" :max="50" placeholder="附近公里数" />
      </div>

      <div class="template-row">
        <span class="label">模板:</span>
        <el-button v-for="template in templates" :key="template.id" text @click="fillFromTemplate(template)">
          {{ template.name }}
        </el-button>
        <span class="spacer" />
        <el-button type="primary" @click="loadActivities">查询</el-button>
      </div>
    </section>

    <section class="content-grid">
      <section class="panel list-panel">
        <div class="panel-header">
          <span>活动发现</span>
          <span class="panel-meta">{{ total }} 条</span>
        </div>

        <el-skeleton :loading="loading" animated :rows="6">
          <div class="activity-list">
            <button v-for="item in activities" :key="String(item.id)" class="activity-item" type="button" @click="openDetail(item.id as number)">
              <div class="item-main">
                <div class="item-title-row">
                  <h3>{{ item.name }}</h3>
                  <el-tag size="small" :type="item.status === 'REJECTED' ? 'danger' : ''">{{ statusLabel(item.status) }}</el-tag>
                </div>
                <p class="intro">{{ item.intro || '暂无简介' }}</p>
                <div class="meta-row">
                  <span>{{ item.category }}</span>
                  <span>{{ item.city || '未填写城市' }}</span>
                  <span>{{ phaseLabel(item.phase) }}</span>
                  <span>{{ item.signupCount }}/{{ item.capacity || '∞' }}</span>
                </div>
                <div class="tag-row">
                  <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                </div>
              </div>
            </button>
          </div>
        </el-skeleton>
      </section>

      <section class="panel side-panel">
        <div class="panel-header">
          <span>我的活动</span>
          <span class="panel-meta">{{ mineActivities.length }} 条</span>
        </div>
        <div class="mine-list" v-if="mineActivities.length">
          <button v-for="item in mineActivities" :key="String(item.id)" type="button" class="mine-item" @click="openDetail(item.id as number)">
            <strong>{{ item.name }}</strong>
            <span :class="{ 'status-rejected': item.status === 'REJECTED' }">{{ statusLabel(item.status) }} / {{ phaseLabel(item.phase) }}</span>
          </button>
        </div>
        <div v-else class="empty-text">登录后可查看你创建的活动</div>
      </section>
    </section>

    <section class="panel map-panel">
      <div class="panel-header">
        <span>地图模式</span>
        <div class="toolbar-actions">
          <el-button :loading="mapLoading" @click="loadMapPoints">刷新点位</el-button>
          <span class="panel-meta">{{ mapPoints.length }} 个点位</span>
        </div>
      </div>
      <div class="map-layout">
        <div ref="mapRef" class="map-canvas">
          <div v-if="!amapKey" class="map-placeholder">未配置地图 Key，已保留点位列表</div>
        </div>
        <div class="map-list">
          <button v-for="point in mapPoints" :key="point.id" type="button" class="map-item" @click="openDetail(point.id)">
            <strong>{{ point.name }}</strong>
            <span>{{ point.category }} / {{ point.city || '未填写城市' }}</span>
            <span>{{ statusLabel(point.status) }} / {{ phaseLabel(point.phase) }}</span>
          </button>
        </div>
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="活动详情" width="980px">
      <template v-if="detail">
        <div class="detail-toolbar">
          <div class="detail-tags">
            <el-tag :type="detail.status === 'REJECTED' ? 'danger' : ''">{{ statusLabel(detail.status) }}</el-tag>
            <el-tag type="success">{{ phaseLabel(detail.phase) }}</el-tag>
            <el-tag v-if="detail.mySignupStatus" type="warning">{{ detail.mySignupStatus }}</el-tag>
          </div>
          <div class="detail-actions">
            <el-button :loading="actionLoading" @click="refreshDetail">刷新</el-button>
            <el-button :loading="actionLoading" @click="cloneCurrent">克隆</el-button>
            <el-button v-if="canSubmitDraft" type="primary" :loading="actionLoading" @click="submitDraft">
              {{ detail.status === 'REJECTED' ? '重新提交审核' : '提交审核' }}
            </el-button>
            <el-button v-if="canDelete" type="danger" plain :loading="actionLoading" @click="deleteCurrent">
              {{ detail.status === 'PUBLISHED' ? '取消活动' : '删除活动' }}
            </el-button>
            <el-button v-if="canSignup" type="primary" :loading="actionLoading" @click="signupCurrent">立即报名</el-button>
            <el-button v-if="canCancelSignup" type="danger" plain :loading="actionLoading" @click="cancelSignup">取消报名</el-button>
            <el-button v-if="canConfirmWaitlist" type="warning" :loading="actionLoading" @click="confirmWaitlist">确认候补</el-button>
          </div>
        </div>

        <el-tabs v-model="detailTab">
          <el-tab-pane label="概览" name="overview">
            <div class="detail-grid">
              <div><strong>标题</strong><p>{{ detail.name }}</p></div>
              <div><strong>分类</strong><p>{{ detail.category }}</p></div>
              <div><strong>城市</strong><p>{{ detail.city || '未填写' }}</p></div>
              <div><strong>地址</strong><p>{{ detail.address || '未填写' }}</p></div>
              <div><strong>发起人</strong><p class="creator-link" @click="detail.creator?.id && $router.push(`/social/user/${detail.creator.id}`)">{{ detail.creator?.nickname || '-' }}</p></div>
              <div><strong>人数</strong><p>{{ detail.signupCount }} / {{ detail.capacity || '不限' }}</p></div>
              <div><strong>开始时间</strong><p>{{ formatTime(detail.startTime) }}</p></div>
              <div><strong>结束时间</strong><p>{{ formatTime(detail.endTime) }}</p></div>
              <div><strong>报名截止</strong><p>{{ formatTime(detail.signupDeadline) }}</p></div>
              <div><strong>候补人数</strong><p>{{ detail.waitlistCount || 0 }}</p></div>
              <div class="full"><strong>简介</strong><p>{{ detail.intro || '暂无简介' }}</p></div>
              <div class="full"><strong>标签</strong>
                <div class="tag-row compact">
                  <el-tag v-for="tag in detail.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                </div>
              </div>
            </div>

            <section class="inline-block" v-if="detail.mySignupStatus === 'REGISTERED' || isOwner">
              <div class="inline-header">
                <strong>签到</strong>
                <el-button v-if="isOwner" size="small" :loading="actionLoading" @click="createCheckinCode">生成签到码</el-button>
              </div>
              <div class="checkin-grid">
                <el-input v-model="checkinForm.code" placeholder="签到码" />
                <el-input-number v-model="checkinForm.lng" :precision="4" :step="0.0001" placeholder="经度" />
                <el-input-number v-model="checkinForm.lat" :precision="4" :step="0.0001" placeholder="纬度" />
                <el-button type="primary" :loading="actionLoading" @click="submitCheckin">提交签到</el-button>
              </div>
              <div v-if="generatedCode" class="helper-text">当前签到码: {{ generatedCode }}</div>
            </section>

            <section v-if="isOwner" class="inline-block">
              <div class="inline-header"><strong>发起人管理</strong></div>
              <div class="owner-panels">
                <div>
                  <h4>报名名单</h4>
                  <div v-if="signups.length" class="mini-list">
                    <div v-for="item in signups" :key="item.signupId" class="mini-row">
                      <span class="creator-link" @click="$router.push(`/social/user/${item.userId}`)">{{ item.nickname || item.userId }}</span>
                      <span>{{ item.signupStatus }}</span>
                      <span>{{ item.checkedIn ? '已签到' : '未签到' }}</span>
                    </div>
                  </div>
                  <div v-else class="empty-text">暂无报名数据</div>
                </div>
                <div>
                  <h4>候补队列</h4>
                  <div v-if="waitlist?.list?.length" class="mini-list">
                    <div v-for="item in waitlist.list" :key="item.id" class="mini-row">
                      <span class="creator-link" @click="$router.push(`/social/user/${item.userId}`)">#{{ item.position }} {{ item.nickname || item.userId }}</span>
                      <span>{{ item.status }}</span>
                    </div>
                  </div>
                  <div v-else class="empty-text">暂无候补</div>
                </div>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane label="总结" name="summary">
            <section class="summary-block">
              <div class="inline-header">
                <strong>图文总结</strong>
                <el-tag v-if="summary">{{ summary.status }}</el-tag>
              </div>
              <p class="summary-text">{{ summary?.content || '暂未发布总结' }}</p>

              <div v-if="summary?.images?.length" class="image-list">
                <div v-for="image in summary.images" :key="image.id" class="image-item">
                  <a :href="image.imageUrl" target="_blank" rel="noreferrer">{{ image.imageUrl }}</a>
                  <div class="image-meta">
                    <span>AI: {{ image.aiCategory || '-' }}</span>
                    <span>确认: {{ image.confirmedCategory || '未确认' }}</span>
                    <el-select
                      v-if="isOwner"
                      size="small"
                      :model-value="image.confirmedCategory || image.aiCategory || 'PROCESS'"
                      @change="onImageCategoryChange(image.id, $event)"
                    >
                      <el-option label="合影" value="GROUP_PHOTO" />
                      <el-option label="场地" value="VENUE" />
                      <el-option label="过程" value="PROCESS" />
                      <el-option label="物资" value="MATERIAL" />
                      <el-option label="成果" value="RESULT" />
                    </el-select>
                  </div>
                </div>
              </div>

              <div v-if="isOwner" class="owner-summary-form">
                <el-form label-width="88px">
                  <el-form-item label="总结内容">
                    <el-input v-model="summaryForm.content" type="textarea" :rows="5" />
                  </el-form-item>
                  <el-form-item label="图片 URL">
                    <el-input v-model="summaryForm.imageUrlsText" type="textarea" :rows="3" placeholder="每行一个 URL，或用逗号分隔" />
                  </el-form-item>
                </el-form>
                <div class="summary-actions">
                  <el-button :loading="actionLoading" @click="uploadImages">上传图片</el-button>
                  <el-button :loading="actionLoading" @click="saveSummary(false)">保存草稿</el-button>
                  <el-button type="primary" :loading="actionLoading" @click="saveSummary(true)">发布总结</el-button>
                </div>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane label="评价" name="reviews">
            <section class="summary-block">
              <div class="inline-header"><strong>活动评价</strong></div>
              <div v-if="reviews.length" class="review-list">
                <div v-for="review in reviews" :key="review.id" class="review-item">
                  <div class="review-head">
                    <strong>{{ review.nickname || review.userId }}</strong>
                    <span>{{ review.rating }}/5</span>
                    <span>{{ formatTime(review.createdAt) }}</span>
                  </div>
                  <p>{{ review.content || '无文字评价' }}</p>
                </div>
              </div>
              <div v-else class="empty-text">暂无评价</div>

              <div v-if="canReview" class="owner-summary-form">
                <el-form label-width="88px">
                  <el-form-item label="评分">
                    <el-rate v-model="reviewForm.rating" />
                  </el-form-item>
                  <el-form-item label="评价内容">
                    <el-input v-model="reviewForm.content" type="textarea" :rows="4" />
                  </el-form-item>
                </el-form>
                <el-button type="primary" :loading="actionLoading" @click="submitReview">提交评价</el-button>
              </div>
            </section>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新建活动" width="820px" @closed="resetForm">
      <div class="ai-bar">
        <el-input v-model="aiTheme" placeholder="先输入主题，例如：新生破冰夜跑" />
        <el-button :loading="actionLoading" @click="generateAiPlan">AI 生成初稿</el-button>
      </div>
      <el-form label-width="96px">
        <el-form-item label="活动标题"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="活动分类">
          <el-select v-model="form.category">
            <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动简介"><el-input v-model="form.intro" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="tagText" placeholder="用英文逗号或中文逗号分隔" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="form.city" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="经纬度">
          <div class="lng-lat-row">
            <el-input-number v-model="form.lng" :precision="4" :step="0.0001" placeholder="经度" />
            <el-input-number v-model="form.lat" :precision="4" :step="0.0001" placeholder="纬度" />
          </div>
        </el-form-item>
        <el-form-item label="人数上限"><el-input-number v-model="form.capacity" :min="1" :max="500" /></el-form-item>
        <el-form-item label="费用"><el-input-number v-model="form.fee" :min="0" :step="10" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="报名截止"><el-date-picker v-model="form.signupDeadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button :loading="saving" @click="submitCreate(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate(true)">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.activity-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}
.panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 16px;
  display: grid;
  gap: 12px;
}
.tab-row,
.panel-header,
.detail-toolbar,
.inline-header,
.review-head,
.image-meta,
.item-title-row,
.meta-row,
.tag-row,
.mini-row,
.ai-bar,
.lng-lat-row,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tab-row,
.panel-header,
.detail-toolbar,
.inline-header,
.review-head,
.image-meta,
.item-title-row,
.meta-row,
.tag-row,
.mini-row {
  justify-content: space-between;
}
.spacer {
  flex: 1;
}
.search-grid,
.content-grid,
.activity-list,
.mine-list,
.map-layout,
.map-list,
.review-list,
.image-list,
.mini-list,
.item-main,
.owner-summary-form,
.summary-block,
.inline-block,
.detail-grid,
.owner-panels,
.checkin-grid,
.summary-actions {
  display: grid;
  gap: 12px;
}
.search-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
.content-grid {
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  align-items: start;
}
.activity-item,
.mine-item,
.map-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 14px;
  text-align: left;
  cursor: pointer;
}
.activity-item:hover,
.mine-item:hover,
.map-item:hover {
  border-color: #93c5fd;
}
.map-layout {
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
}
.map-canvas {
  min-height: 360px;
  border-radius: 8px;
  overflow: hidden;
  background: #f3f4f6;
  position: relative;
}
.map-placeholder {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #6b7280;
}
.intro,
.summary-text,
.review-item p,
.detail-grid p {
  margin: 0;
  color: #374151;
  line-height: 1.5;
}
.label,
.panel-meta,
.helper-text,
.empty-text {
  color: #6b7280;
}
.meta-row,
.tag-row.compact,
.template-row,
.detail-tags,
.detail-actions {
  flex-wrap: wrap;
}
.mine-item {
  display: flex;
  justify-content: space-between;
}
.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.detail-grid .full {
  grid-column: 1 / -1;
}
.inline-block {
  margin-top: 16px;
  border-top: 1px solid #e5e7eb;
  padding-top: 16px;
}
.owner-panels {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.image-item,
.review-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  display: grid;
  gap: 8px;
}
.checkin-grid {
  grid-template-columns: minmax(0, 1fr) 150px 150px auto;
}
.summary-actions {
  grid-template-columns: repeat(3, auto);
  justify-content: end;
}
.ai-bar,
.lng-lat-row {
  justify-content: stretch;
}
.ai-bar :deep(.el-input),
.lng-lat-row :deep(.el-input-number) {
  flex: 1;
}
@media (max-width: 1100px) {
  .content-grid,
  .map-layout,
  .owner-panels,
  .search-grid,
  .checkin-grid,
  .summary-actions {
    grid-template-columns: 1fr;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
.status-rejected { color: #f56c6c; font-weight: 600; }
.creator-link { color: #409eff; cursor: pointer; }
.creator-link:hover { text-decoration: underline; }
</style>
