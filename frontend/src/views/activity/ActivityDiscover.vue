<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { useAuthStore } from '../../stores/auth'
import { merchantApi } from '../../api/merchant'
import http from '../../api/http'
import ReportDialog from '../../components/ReportDialog.vue'
import {
  activityStatusLabel,
  activityStatusTagType,
  activityPhaseLabel,
} from '../../constants/enums'
import {
  activityApi,
  type ActivityDetail,
  type ActivityItem,
  type ActivityPoint,
  type ActivityUpsertReq,
  type ReviewItem,
  type SignupManageItem,
  type SummaryImageItem,
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
const currentRoute = useRoute()
const amapKey = (import.meta as any).env?.VITE_AMAP_KEY as string | undefined
const categoryOptions = [
  { label: '运动', value: 'SPORTS' },
  { label: '徒步', value: 'HIKING' },
  { label: '桌游', value: 'BOARD_GAME' },
  { label: '学习', value: 'STUDY' },
  { label: '公益', value: 'CHARITY' },
  { label: '城市漫步', value: 'CITY_WALK' },
  { label: '其他', value: 'OTHER' },
]
const summaryCategoryOptions = [
  { label: '合影', value: 'GROUP_PHOTO' },
  { label: '场地', value: 'VENUE' },
  { label: '过程', value: 'PROCESS' },
  { label: '物资', value: 'MATERIAL' },
  { label: '成果', value: 'RESULT' },
]

const loading = ref(false)
const mapLoading = ref(false)
const showMapPanel = ref(true)
const saving = ref(false)
const locationPickerVisible = ref(false)
const locationPickerTarget = ref<'query' | 'form'>('query')
const locationPickerMapRef = ref<HTMLDivElement | null>(null)
let locationPickerMap: any = null
let locationPickerMarker: any = null
const actionLoading = ref(false)
const activities = ref<ActivityItem[]>([])
const mineActivities = ref<ActivityItem[]>([])
const joinedActivities = ref<any[]>([])
const templates = ref<TemplateItem[]>([])
const mapPoints = ref<ActivityPoint[]>([])
const detail = ref<ActivityDetail | null>(null)
const summary = ref<SummaryItem | null>(null)
const reviews = ref<ReviewItem[]>([])
const signups = ref<SignupManageItem[]>([])
const waitlist = ref<WaitlistPage | null>(null)
const total = ref(0)
const detailVisible = ref(false)
const reportVisible = ref(false)
const createVisible = ref(false)
const editingId = ref<number | null>(null)
const detailTab = ref('overview')
const aiTheme = ref('')
const tagText = ref('')
const generatedCode = ref('')
const checkinQrDataUrl = ref('')
const summaryFiles = ref<File[]>([])
const mapRef = ref<HTMLDivElement | null>(null)
const pickerMapRef = ref<HTMLDivElement | null>(null)

let amap: any = null
let pickerMap: any = null
let pickerMarker: any = null
let mapMarkers: any[] = []
let mapInfoWindow: any = null
let myLocationMarker: any = null
let mapRefreshTimer: ReturnType<typeof setTimeout> | null = null

const query = reactive({
  tab: 'RECOMMEND',
  keyword: '',
  categories: [] as string[],
  city: '',
  feeMin: undefined as number | undefined,
  feeMax: undefined as number | undefined,
  startFrom: '',
  startTo: '',
  lng: 116.397428,
  lat: 39.90923,
  distanceKm: 5,
  page: 1,
  size: 10,
})

const form = reactive<ActivityUpsertReq>({
  name: '',
  intro: '',
  category: 'OTHER',
  tags: [],
  city: '北京',
  address: '',
  lng: 116.397428,
  lat: 39.90923,
  capacity: 20,
  fee: 0,
  submit: false,
})

const summaryForm = reactive({
  content: '',
})

const reviewForm = reactive({
  rating: 5,
  content: '',
})

const checkinForm = reactive({
  code: '',
  lng: undefined as number | undefined,
  lat: undefined as number | undefined,
})

const tagPreview = computed(() =>
  tagText.value
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter(Boolean)
)

const isOwner = computed(() => !!detail.value && !!auth.user && detail.value.creator?.id === auth.user.id)
// 仅草稿 / 被驳回的活动允许发起人编辑（与契约「编辑活动（草稿/被要求修改时）」一致）
const canEdit = computed(() => isOwner.value && ['DRAFT', 'REJECTED'].includes(detail.value?.status || ''))
const canSignup = computed(() => !!detail.value && detail.value.status === 'PUBLISHED' && !detail.value.mySignupStatus)
const canCancelSignup = computed(() => detail.value?.mySignupStatus === 'REGISTERED')
const canConfirmWaitlist = computed(() => detail.value?.mySignupStatus === 'WAITLISTED')
const canReview = computed(() => !!detail.value && detail.value.phase === 'ENDED' && !!auth.token)
const canManageSummary = computed(() => !!detail.value && isOwner.value && detail.value.phase === 'ENDED')
const visibleCheckinCode = computed(() => detail.value?.checkinCode || generatedCode.value || '')

// 审核结果横幅：仅发起人本人可见（后端也只对本人返回 latestAudit），让 AI/人工审核结果可见。
const auditBanner = computed<{ type: 'success' | 'warning' | 'error'; text: string } | null>(() => {
  const audit = detail.value?.latestAudit
  if (!isOwner.value || !audit || !audit.result) return null
  const isAi = audit.auditType === 'AI'
  const who = isAi ? 'AI' : '人工'
  const reason = audit.reason ? `：${audit.reason}` : ''
  switch (audit.result) {
    case 'PASSED':
      return { type: 'success', text: `${who}审核通过${isAi ? reason : ''}` }
    case 'REJECTED':
      return { type: 'error', text: `${who}审核驳回${reason}` }
    case 'NEEDS_REVISION':
      return { type: 'warning', text: `审核要求修改${reason}` }
    case 'TO_MANUAL':
      return { type: 'warning', text: `AI 已转人工复核${reason}` }
    default:
      return null
  }
})

function formatTime(value?: string) {
  return value ? value.replace('T', ' ') : '未设置'
}

function categoryLabel(value?: string) {
  return categoryOptions.find((item) => item.value === value)?.label || value || '未分类'
}

function imageCategoryLabel(value?: string | null) {
  return summaryCategoryOptions.find((item) => item.value === value)?.label || value || '-'
}

function resetCreateForm() {
  form.name = ''
  form.intro = ''
  form.category = 'OTHER'
  form.tags = []
  form.city = '北京'
  form.address = ''
  form.lng = query.lng
  form.lat = query.lat
  form.capacity = 20
  form.fee = 0
  form.coverImage = ''
  form.startTime = ''
  form.endTime = ''
  form.signupDeadline = ''
  form.teamId = undefined
  form.submit = false
  editingId.value = null
  tagText.value = ''
  aiTheme.value = ''
}

async function ensureCurrentUser() {
  if (auth.token && !auth.user) {
    try {
      await auth.loadMe()
    } catch {
      // ignore
    }
  }
}

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
    joinedActivities.value = []
    return
  }
  const data = await activityApi.mine({ page: 1, size: 6 })
  mineActivities.value = data.list
  // 加载我报名的活动
  if (auth.user?.id) {
    try {
      joinedActivities.value = await http.get<any, any[]>(`/users/${auth.user.id}/activities`)
    } catch { joinedActivities.value = [] }
  }
}

async function loadTemplates() {
  templates.value = await activityApi.templates()
}

async function loadSummary(activityId: number) {
  try {
    summary.value = await activityApi.getSummary(activityId)
    summaryForm.content = summary.value.content || ''
  } catch {
    summary.value = null
    summaryForm.content = ''
  }
}

async function loadReviews(activityId: number) {
  const data = await activityApi.reviews(activityId, { page: 1, size: 20 })
  reviews.value = data.list
}

async function loadOwnerData(activityId: number) {
  if (!isOwner.value) {
    signups.value = []
    waitlist.value = null
    return
  }
  const [signupData, waitData] = await Promise.all([
    activityApi.signups(activityId, { page: 1, size: 50 }),
    activityApi.waitlist(activityId),
  ])
  signups.value = signupData.list
  waitlist.value = waitData
}

async function openDetail(
  id: number,
  options: { tab?: string; presetCheckinCode?: string; preserveGeneratedCode?: boolean } = {}
) {
  detail.value = await activityApi.detail(id)
  detailVisible.value = true
  detailTab.value = options.tab === 'checkin' ? 'checkin' : 'overview'
  generatedCode.value = options.preserveGeneratedCode ? generatedCode.value : ''
  checkinForm.code = options.presetCheckinCode || detail.value.checkinCode || ''
  checkinForm.lng = detail.value.lng ?? undefined
  checkinForm.lat = detail.value.lat ?? undefined
  await Promise.allSettled([
    loadSummary(id),
    loadReviews(id),
    loadOwnerData(id),
  ])
  await updateCheckinQrCode()
}

async function refreshDetail() {
  if (!detail.value?.id) return
  await openDetail(detail.value.id, {
    tab: detailTab.value,
    presetCheckinCode: checkinForm.code || undefined,
    preserveGeneratedCode: true,
  })
  await Promise.allSettled([loadActivities(), loadMine(), refreshMapPoints(false)])
}

function fillFromTemplate(template: TemplateItem) {
  form.category = template.category
  form.intro = template.defaultIntro || ''
  form.capacity = template.defaultCapacity || 20
  form.name = template.name + '活动'
  createVisible.value = true
}

function applyAiPlan(plan: ActivityItem) {
  form.name = plan.name
  form.intro = plan.intro || ''
  form.category = plan.category
  form.capacity = plan.capacity || 20
  form.fee = plan.fee || 0
  form.city = plan.city || '北京'
  form.address = plan.address || ''
  form.startTime = plan.startTime || ''
  form.endTime = plan.endTime || ''
  form.signupDeadline = plan.signupDeadline || ''
  tagText.value = plan.tags.join(', ')
  ElMessage.success('AI 已生成活动草稿，可继续修改后保存')
}

async function generateAiPlan() {
  if (!aiTheme.value.trim()) {
    ElMessage.warning('请先输入活动主题')
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

// 未过审的商家禁止发起活动：个人用户直接放行，商家需审核通过（后端为权威校验，此处仅前置提示）
async function ensureCanCreate(): Promise<boolean> {
  if (auth.user?.userType !== 'MERCHANT') return true
  try {
    const m = await merchantApi.getMyProfile()
    if (m?.auditStatus === 'APPROVED') return true
    ElMessage.warning('商家资质未通过审核，暂不能发起活动')
    return false
  } catch {
    ElMessage.warning('无法确认商家审核状态，暂不能发起活动')
    return false
  }
}

async function openCreate() {
  if (!(await ensureCanCreate())) return
  createVisible.value = true
}

// 用现有活动信息填充表单并进入编辑模式（复用创建弹窗）
function openEditFromDetail() {
  const src = detail.value
  if (!src) return
  editingId.value = src.id as number
  form.name = src.name || ''
  form.intro = src.intro || ''
  form.category = src.category || 'OTHER'
  form.tags = [...(src.tags || [])]
  form.city = src.city || ''
  form.address = src.address || ''
  form.lng = src.lng ?? query.lng
  form.lat = src.lat ?? query.lat
  form.capacity = src.capacity ?? 20
  form.fee = src.fee ?? 0
  form.coverImage = src.coverImage || ''
  form.startTime = src.startTime || ''
  form.endTime = src.endTime || ''
  form.signupDeadline = src.signupDeadline || ''
  form.teamId = src.teamId
  form.submit = false
  tagText.value = (src.tags || []).join(',')
  aiTheme.value = ''
  detailVisible.value = false
  createVisible.value = true
}

async function submitCreate(submit: boolean) {
  saving.value = true
  try {
    form.tags = tagPreview.value
    form.submit = submit
    if (editingId.value != null) {
      await activityApi.update(editingId.value, { ...form })
      ElMessage.success(submit ? '活动已提交，系统会先进行 AI 审核' : '活动已保存')
    } else {
      await activityApi.create({ ...form })
      ElMessage.success(submit ? '活动已提交，系统会先进行 AI 审核' : '活动草稿已保存')
    }
    createVisible.value = false
    resetCreateForm()
    await Promise.allSettled([loadActivities(), loadMine(), refreshMapPoints(false)])
  } finally {
    saving.value = false
  }
}

async function signupCurrent() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      '<div style="text-align:left;line-height:1.8">' +
      '<p><b>线下活动安全须知：</b></p>' +
      '<p>1. 请确认活动地点安全，注意人身和财产安全</p>' +
      '<p>2. 活动中如遇紧急情况，请及时拨打110/120</p>' +
      '<p>3. 请勿向陌生人透露个人隐私信息</p>' +
      '<p>4. 报名即视为确认本人信誉分≥60、年龄≥16岁</p>' +
      '<p>5. 活动开始前可取消报名，开始后无法取消</p>' +
      '</div>',
      '报名确认', {
      confirmButtonText: '我已阅读并同意，确认报名',
      cancelButtonText: '取消',
      type: 'warning',
      dangerouslyUseHTMLString: true,
    })
  } catch {
    return
  }
  actionLoading.value = true
  try {
    const data = await activityApi.signup(detail.value.id as number, {
      signupInfo: { source: 'activity-page' },
      safetyConfirmed: true,
    })
    ElMessage.success(data.status === 'WAITLISTED' ? `报名成功，已进入候补，当前排位 ${data.waitlistPosition}` : '报名成功')
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
    ElMessage.success('已取消报名，若有候补将自动顺延')
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
    ElMessage.success('候补转正成功')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

async function cloneCurrent() {
  if (!detail.value) return
  if (!(await ensureCanCreate())) return
  actionLoading.value = true
  try {
    await activityApi.clone(detail.value.id as number)
    ElMessage.success('已克隆为你的新草稿')
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
    ElMessage.success(detail.value.status === 'PUBLISHED' ? '活动已取消' : '草稿已删除')
    detailVisible.value = false
    await Promise.allSettled([loadActivities(), loadMine(), refreshMapPoints(false)])
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
    await updateCheckinQrCode()
    ElMessage.success('签到轮次已开启，已报名成员现在会看到各自独立的签到二维码')
    await refreshDetail()
  } finally {
    actionLoading.value = false
  }
}

function resolveLocationFailureMessage(reason?: string) {
  if (typeof window !== 'undefined' && !window.isSecureContext && location.hostname !== 'localhost' && location.hostname !== '127.0.0.1') {
    return '当前页面使用的是 HTTP IP 地址，Chrome 会直接拒绝定位；这不是权限按钮问题，需要改成 HTTPS 入口'
  }
  if (reason) return reason
  return '定位失败，请检查浏览器定位权限'
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

async function useCurrentLocation(target: 'query' | 'form' | 'checkin') {
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
  const { lng, lat } = locationResult
  if (target === 'query') {
    query.lng = lng
    query.lat = lat
    query.tab = 'NEARBY'
    if (amap) amap.setCenter([lng, lat])
  } else if (target === 'form') {
    form.lng = lng
    form.lat = lat
    setPickerMarker(lng, lat)
  } else {
    checkinForm.lng = lng
    checkinForm.lat = lat
  }
  ElMessage.success('已读取当前位置')
}

async function openLocationPicker(target: 'query' | 'form') {
  locationPickerTarget.value = target
  locationPickerVisible.value = true
  await nextTick()
  if (!locationPickerMapRef.value || !amapKey) {
    ElMessage.warning('地图未配置')
    locationPickerVisible.value = false
    return
  }
  const AMap = await ensureAmap()
  if (!AMap) return
  if (!locationPickerMap) {
    locationPickerMap = new AMap.Map(locationPickerMapRef.value, {
      zoom: 12,
      center: [query.lng, query.lat],
    })
    locationPickerMap.on('click', (e: any) => {
      const lng = e.lnglat.getLng()
      const lat = e.lnglat.getLat()
      if (locationPickerMarker) locationPickerMarker.setPosition(e.lnglat)
      else locationPickerMarker = new AMap.Marker({ map: locationPickerMap, position: e.lnglat })
      // 立即更新目标
      if (locationPickerTarget.value === 'query') {
        query.lng = lng
        query.lat = lat
      } else {
        form.lng = lng
        form.lat = lat
      }
    })
  }
}

function confirmLocationPicker() {
  locationPickerVisible.value = false
  ElMessage.success('位置已更新，已切换到"附近"模式')
  if (locationPickerTarget.value === 'query') {
    query.tab = 'NEARBY'
    loadActivities()
  }
}

async function submitCheckin() {
  if (!detail.value) return
  if (!checkinForm.code) {
    ElMessage.warning('请输入或使用展示的签到码')
    return
  }
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
    ElMessage.success(publish ? '活动总结已发布' : '总结草稿已保存')
    await loadSummary(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

function onSummaryFilesChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  summaryFiles.value = files
}

async function uploadSelectedSummaryFiles() {
  if (!detail.value || !summaryFiles.value.length) return
  actionLoading.value = true
  try {
    for (const file of summaryFiles.value) {
      await activityApi.uploadSummaryImageFile(detail.value.id as number, file)
    }
    summaryFiles.value = []
    ElMessage.success('总结图片上传完成，可继续追加上传')
    await loadSummary(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

async function confirmImage(image: SummaryImageItem, category: string) {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.updateSummaryImage(detail.value.id as number, image.id, category)
    ElMessage.success('图片分类已确认')
    await loadSummary(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

async function deleteImage(image: SummaryImageItem) {
  if (!detail.value) return
  actionLoading.value = true
  try {
    await activityApi.deleteSummaryImage(detail.value.id as number, image.id)
    ElMessage.success('图片已删除')
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
    reviewForm.content = ''
    await loadReviews(detail.value.id as number)
  } finally {
    actionLoading.value = false
  }
}

const checkinPublicOrigin = ((import.meta as any).env?.VITE_PUBLIC_ORIGIN as string | undefined)?.replace(/\/$/, '') || ''

function buildCheckinEntryUrl(code: string) {
  if (!detail.value?.id || typeof window === 'undefined') return code
  const origin = checkinPublicOrigin || window.location.origin
  const url = new URL(`${origin}/activities/checkin`)
  url.searchParams.set('activityId', String(detail.value.id))
  url.searchParams.set('checkinCode', code)
  return url.toString()
}

async function updateCheckinQrCode() {
  const code = visibleCheckinCode.value
  if (!code) {
    checkinQrDataUrl.value = ''
    return
  }
  checkinQrDataUrl.value = await QRCode.toDataURL(buildCheckinEntryUrl(code))
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
  return {
    minLng: query.lng - 0.25,
    maxLng: query.lng + 0.25,
    minLat: query.lat - 0.18,
    maxLat: query.lat + 0.18,
  }
}

async function refreshMapPoints(resetCenter: boolean) {
  mapLoading.value = true
  try {
    if (resetCenter && amap) amap.setCenter([query.lng, query.lat])
    mapPoints.value = await activityApi.mapPoints(currentBounds())
    renderMapMarkers()
  } finally {
    mapLoading.value = false
  }
}

function scheduleMapRefresh() {
  if (mapRefreshTimer) clearTimeout(mapRefreshTimer)
  mapRefreshTimer = setTimeout(() => {
    refreshMapPoints(false)
  }, 250)
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

function escapeHtml(value?: string) {
  return String(value ?? '').replace(/[&<>"']/g, (ch) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch] as string)
  )
}

function buildPointInfoHtml(point: ActivityPoint) {
  const name = escapeHtml(point.name)
  const meta = escapeHtml(`${categoryLabel(point.category)} · ${point.city || '未设置城市'}`)
  const state = escapeHtml(`${activityStatusLabel(point.status)} · ${activityPhaseLabel(point.phase)}`)
  return (
    `<div style="padding:8px 10px;min-width:160px;max-width:240px;line-height:1.5">` +
    `<div style="font-weight:600;color:#111827;margin-bottom:4px">${name}</div>` +
    `<div style="font-size:12px;color:#6b7280">${meta}</div>` +
    `<div style="font-size:12px;color:#6b7280">${state}</div>` +
    `</div>`
  )
}

function renderMapMarkers() {
  if (!amap || !window.AMap) return
  mapMarkers.forEach((marker) => marker.setMap(null))
  mapMarkers = mapPoints.value
    .filter((point) => point.lng != null && point.lat != null)
    .map((point) => {
      const marker = new window.AMap.Marker({
        map: amap,
        position: [point.lng, point.lat],
        title: point.name,
      })
      marker.on('mouseover', () => {
        if (!mapInfoWindow) {
          mapInfoWindow = new window.AMap.InfoWindow({
            isCustom: false,
            offset: new window.AMap.Pixel(0, -30),
          })
        }
        mapInfoWindow.setContent(buildPointInfoHtml(point))
        mapInfoWindow.open(amap, [point.lng, point.lat])
      })
      marker.on('mouseout', () => mapInfoWindow?.close())
      marker.on('click', () => openDetail(point.id))
      return marker
    })
}

function setMyLocationMarker(lng: number, lat: number) {
  if (!amap || !window.AMap) return
  if (!myLocationMarker) {
    myLocationMarker = new window.AMap.Marker({
      map: amap,
      position: [lng, lat],
      offset: new window.AMap.Pixel(-9, -9),
      zIndex: 150,
      content:
        '<div style="width:14px;height:14px;background:#3b82f6;border:2px solid #fff;border-radius:50%;box-shadow:0 0 0 5px rgba(59,130,246,.25)"></div>',
    })
  } else {
    myLocationMarker.setPosition([lng, lat])
  }
}

async function initMainMap() {
  if (!mapRef.value || amap || !amapKey) return
  try {
    const AMap = await ensureAmap()
    if (!AMap || !mapRef.value) return
    amap = new AMap.Map(mapRef.value, {
      zoom: 11,
      center: [query.lng, query.lat],
    })
    amap.on('moveend', scheduleMapRefresh)
    amap.on('zoomend', scheduleMapRefresh)
    await refreshMapPoints(false)
  } catch {
    ElMessage.warning('地图脚本加载失败，已保留列表模式')
  }
}

function setPickerMarker(lng?: number, lat?: number) {
  if (!pickerMap || !window.AMap || lng == null || lat == null) return
  if (!pickerMarker) {
    pickerMarker = new window.AMap.Marker({
      map: pickerMap,
      position: [lng, lat],
      draggable: true,
    })
    pickerMarker.on('dragend', (event: any) => {
      form.lng = Number(event.lnglat.lng.toFixed(6))
      form.lat = Number(event.lnglat.lat.toFixed(6))
    })
  } else {
    pickerMarker.setPosition([lng, lat])
  }
  pickerMap.setCenter([lng, lat])
}

async function initPickerMap() {
  if (!pickerMapRef.value || pickerMap || !amapKey) return
  const AMap = await ensureAmap()
  if (!AMap || !pickerMapRef.value) return
  pickerMap = new AMap.Map(pickerMapRef.value, {
    zoom: 13,
    center: [form.lng || query.lng, form.lat || query.lat],
  })
  pickerMap.on('click', (event: any) => {
    form.lng = Number(event.lnglat.lng.toFixed(6))
    form.lat = Number(event.lnglat.lat.toFixed(6))
    setPickerMarker(form.lng, form.lat)
  })
  setPickerMarker(form.lng, form.lat)
}

watch(createVisible, async (visible) => {
  if (!visible) return
  await nextTick()
  await initPickerMap()
  setPickerMarker(form.lng, form.lat)
})

watch(visibleCheckinCode, () => {
  updateCheckinQrCode()
})

// 地图面板从隐藏(display:none)重新显示后，高德需要 resize 重新计算尺寸，否则会空白/错位
watch(showMapPanel, async (visible) => {
  if (!visible) return
  await nextTick()
  amap?.resize()
})

watch(
  () => [form.lng, form.lat],
  ([lng, lat]) => {
    if (typeof lng === 'number' && typeof lat === 'number') setPickerMarker(lng, lat)
  }
)

onMounted(async () => {
  await ensureCurrentUser()
  await Promise.allSettled([loadActivities(), loadTemplates(), loadMine()])
  await nextTick()
  await initMainMap()
  if (!amap) await refreshMapPoints(false)
  // 支持 ?detail=id 自动打开详情
  const detailId = Number(currentRoute.query.detail)
  if (detailId) openDetail(detailId)
  // 支持 ?createForTeam=id 自动打开创建弹窗（队内活动）
  const teamId = Number(currentRoute.query.createForTeam)
  if (teamId) {
    form.teamId = teamId
    if (await ensureCanCreate()) createVisible.value = true
  }
})
</script>

<template>
  <div class="activity-page">
    <section class="page-toolbar panel">
      <div class="toolbar-top">
        <el-radio-group v-model="query.tab" size="large" @change="loadActivities">
          <el-radio-button label="RECOMMEND">推荐</el-radio-button>
          <el-radio-button label="LATEST">最新</el-radio-button>
          <el-radio-button label="NEARBY">附近</el-radio-button>
        </el-radio-group>
        <div class="toolbar-actions">
          <el-button @click="openLocationPicker('query')">选择位置</el-button>
          <el-button type="primary" @click="openCreate">创建活动</el-button>
        </div>
      </div>

      <div class="toolbar-grid">
        <el-input v-model="query.keyword" clearable placeholder="搜索标题、简介或标签" />
        <el-select v-model="query.categories" multiple collapse-tags collapse-tags-tooltip placeholder="活动分类">
          <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="query.city" clearable placeholder="城市" />
        <el-input-number v-model="query.feeMin" :min="0" :step="10" placeholder="最低费用(元)" controls-position="right" />
        <el-input-number v-model="query.feeMax" :min="0" :step="10" placeholder="最高费用(元)" controls-position="right" />
        <el-date-picker v-model="query.startFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间(从)" />
        <el-date-picker v-model="query.startTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间(到)" />
        <el-input-number v-model="query.distanceKm" :min="1" :max="50" placeholder="附近范围(公里)" controls-position="right" />
      </div>

      <div class="toolbar-bottom">
        <div class="template-actions">
          <span class="muted">模板：</span>
          <el-button v-for="template in templates" :key="template.id" text @click="fillFromTemplate(template)">
            {{ template.name }}
          </el-button>
        </div>
        <el-button type="primary" @click="loadActivities">查询活动</el-button>
      </div>
    </section>

    <section class="layout-grid" :class="{ 'no-map': !showMapPanel }">
      <section v-show="showMapPanel" class="panel map-panel">
        <div class="section-head">
          <h3>地图模式</h3>
          <div class="section-meta">
            <el-button :loading="mapLoading" @click="refreshMapPoints(true)">刷新点位</el-button>
            <span>{{ mapPoints.length }} 个点位</span>
            <el-button text @click="showMapPanel = false">收起地图</el-button>
          </div>
        </div>
        <div v-if="amapKey" ref="mapRef" class="map-canvas" />
        <el-empty v-else description="未配置地图 Key，仍可使用列表发现活动" />
      </section>

      <section class="panel list-panel">
        <div class="section-head">
          <h3>活动发现</h3>
          <div class="section-meta">
            <span class="muted">{{ total }} 条</span>
            <el-button v-if="!showMapPanel" text type="primary" @click="showMapPanel = true">显示地图</el-button>
          </div>
        </div>
        <el-skeleton :loading="loading" animated :rows="6">
          <div class="activity-list">
            <button v-for="item in activities" :key="String(item.id)" type="button" class="activity-card" @click="openDetail(item.id as number)">
              <div class="title-row">
                <h4>{{ item.name }}</h4>
                <el-tag v-if="item.creator?.userType === 'MERCHANT'" size="small" type="warning" effect="dark">商家</el-tag>
                <el-tag size="small" :type="activityStatusTagType(item.status)">{{ activityStatusLabel(item.status) }}</el-tag>
              </div>
              <p class="intro">{{ item.intro || '暂无简介' }}</p>
              <div class="meta-grid">
                <span>{{ categoryLabel(item.category) }}</span>
                <span>{{ item.city || '未设置城市' }}</span>
                <span>{{ activityPhaseLabel(item.phase) }}</span>
                <span>{{ item.signupCount }}/{{ item.capacity || '-' }}</span>
              </div>
              <div class="tag-row">
                <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
              </div>
            </button>
          </div>
        </el-skeleton>
      </section>

      <!-- 右侧栏：我的活动 -->
      <aside class="panel sidebar-mine" v-if="auth.token">
        <div class="section-head">
          <h3>我发起的</h3>
          <span class="muted">{{ mineActivities.length }}</span>
        </div>
        <div v-if="mineActivities.length" class="mine-list">
          <button v-for="item in mineActivities" :key="String(item.id)" type="button" class="mine-card" @click="openDetail(item.id as number)">
            <strong>{{ item.name }}</strong>
            <span>{{ activityStatusLabel(item.status) }}</span>
          </button>
        </div>
        <div v-else class="empty-hint">暂无</div>

        <div class="section-head" style="margin-top: 16px">
          <h3>我报名的</h3>
          <span class="muted">{{ joinedActivities.length }}</span>
        </div>
        <div v-if="joinedActivities.length" class="mine-list">
          <button v-for="item in joinedActivities" :key="String(item.id)" type="button" class="mine-card" @click="openDetail(item.id as number)">
            <strong>{{ item.name }}</strong>
            <span>{{ activityStatusLabel(item.status) }}</span>
          </button>
        </div>
        <div v-else class="empty-hint">暂无</div>
      </aside>
    </section>

    <!-- 地图选点弹窗 -->
    <el-dialog v-model="locationPickerVisible" title="在地图上选择位置" width="560px">
      <div ref="locationPickerMapRef" style="width:100%;height:350px;border-radius:8px"></div>
      <p style="font-size:12px;color:#999;margin:8px 0 0">点击地图选择位置，当前：{{ query.lng.toFixed(4) }}, {{ query.lat.toFixed(4) }}</p>
      <template #footer>
        <el-button @click="locationPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmLocationPicker">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" :title="editingId ? '编辑活动' : '创建活动'" width="980px" destroy-on-close @closed="resetCreateForm">
      <div class="dialog-grid">
        <section class="dialog-panel">
          <div class="section-head compact">
            <h3>AI 活动策划</h3>
          </div>
          <div class="inline-row">
            <el-input v-model="aiTheme" placeholder="输入主题，例如：期末复习局 / 周末羽毛球 / 城市漫步" />
            <el-button type="primary" :loading="actionLoading" @click="generateAiPlan">AI 生成草稿</el-button>
          </div>
          <div class="hint">这里会调用 AI 生成名称、简介、时间建议、标签和基础配置，不是简单模板填空。</div>
        </section>

        <section class="dialog-panel">
          <div class="section-head compact">
            <h3>基础信息</h3>
          </div>
          <el-form label-width="80px" label-position="top">
            <el-form-item label="活动名称" required><el-input v-model="form.name" placeholder="例：周末徒步香山" /></el-form-item>
            <div class="form-row">
              <el-form-item label="活动分类" required>
                <el-select v-model="form.category" placeholder="选择分类">
                  <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="城市"><el-input v-model="form.city" placeholder="例：北京" /></el-form-item>
              <el-form-item label="详细地址"><el-input v-model="form.address" placeholder="例：香山公园东门" /></el-form-item>
            </div>
            <div class="form-row">
              <el-form-item label="人数上限"><el-input-number v-model="form.capacity" :min="1" :max="500" /></el-form-item>
              <el-form-item label="费用（元）"><el-input-number v-model="form.fee" :min="0" :step="10" /></el-form-item>
              <el-form-item label="标签"><el-input v-model="tagText" placeholder="逗号分隔，如：户外,徒步" /></el-form-item>
            </div>
            <div class="form-row">
              <el-form-item label="开始时间" required><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择开始时间" /></el-form-item>
              <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择结束时间" /></el-form-item>
              <el-form-item label="报名截止"><el-date-picker v-model="form.signupDeadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择截止时间" /></el-form-item>
            </div>
            <el-form-item label="活动简介"><el-input v-model="form.intro" type="textarea" :rows="4" placeholder="活动简介、注意事项等" /></el-form-item>
          </el-form>
        </section>

        <section class="dialog-panel">
          <div class="section-head compact">
            <h3>地图选点</h3>
            <div class="section-meta">
              <el-button @click="useCurrentLocation('form')">使用当前位置</el-button>
              <span v-if="form.lng != null && form.lat != null">{{ form.lng.toFixed(6) }}, {{ form.lat.toFixed(6) }}</span>
              <span v-else>请在地图上点击选择活动地点</span>
            </div>
          </div>
          <div v-if="amapKey" ref="pickerMapRef" class="picker-map" />
          <div v-else class="hint">未配置地图 Key，当前只能退回经纬度方式。</div>
          <div class="location-summary">
            <div class="location-card">
              <span class="location-label">选点方式</span>
              <strong>点击地图或拖动标记</strong>
            </div>
            <div class="location-card">
              <span class="location-label">当前坐标</span>
              <strong v-if="form.lng != null && form.lat != null">{{ form.lng.toFixed(6) }}, {{ form.lat.toFixed(6) }}</strong>
              <strong v-else>尚未选择</strong>
            </div>
          </div>
          <div class="hint">创建活动时不再要求手动输入经纬度数字，直接在地图上选点即可。</div>
        </section>
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button :loading="saving" @click="submitCreate(false)">{{ editingId ? '保存' : '保存草稿' }}</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate(true)">提交发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="detail?.name || '活动详情'" width="1100px" destroy-on-close>
      <template v-if="detail">
        <div class="detail-hero">
          <div>
            <div class="title-row">
              <h2>{{ detail.name }}</h2>
              <el-tag v-if="detail.creator?.userType === 'MERCHANT'" type="warning" effect="dark">商家</el-tag>
              <el-tag :type="activityStatusTagType(detail.status)">{{ activityStatusLabel(detail.status) }}</el-tag>
              <el-tag type="info">{{ activityPhaseLabel(detail.phase) }}</el-tag>
            </div>
            <p class="creator-line" v-if="detail.creator">
              主办方：{{ detail.creator.nickname || '未知用户' }}
              <span v-if="detail.creator.userType === 'MERCHANT'" class="merchant-badge">· 商家</span>
            </p>
            <p>{{ detail.intro || '暂无简介' }}</p>
            <el-alert
              v-if="auditBanner"
              class="audit-banner"
              :type="auditBanner.type"
              :title="auditBanner.text"
              :closable="false"
              show-icon
            />
            <div class="tag-row">
              <el-tag v-for="tag in detail.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </div>
          <div class="detail-actions">
            <el-button v-if="canSignup" type="primary" :loading="actionLoading" @click="signupCurrent">报名</el-button>
            <el-button v-if="canCancelSignup" :loading="actionLoading" @click="cancelSignup">取消报名</el-button>
            <el-button v-if="canConfirmWaitlist" type="warning" :loading="actionLoading" @click="confirmWaitlist">确认候补名额</el-button>
            <el-button v-if="detail.status === 'PUBLISHED'" @click="cloneCurrent">克隆活动</el-button>
            <el-button v-if="canEdit" :loading="actionLoading" @click="openEditFromDetail">编辑</el-button>
            <el-button v-if="isOwner && detail.status === 'DRAFT'" type="primary" :loading="actionLoading" @click="submitDraft">提交审核</el-button>
            <el-button v-if="isOwner" type="danger" plain :loading="actionLoading" @click="deleteCurrent">删除/取消</el-button>
            <el-button v-if="auth.user && !isOwner" text type="danger" @click="reportVisible = true">举报</el-button>
          </div>
        </div>

        <ReportDialog v-model="reportVisible" target-type="ACTIVITY" :target-id="detail.id as number" :target-name="detail.name" />

        <el-tabs v-model="detailTab">
          <el-tab-pane label="概览" name="overview">
            <div class="overview-grid">
              <div class="kv-card"><span>分类</span><strong>{{ categoryLabel(detail.category) }}</strong></div>
              <div class="kv-card"><span>时间</span><strong>{{ formatTime(detail.startTime) }}</strong></div>
              <div class="kv-card"><span>报名截止</span><strong>{{ formatTime(detail.signupDeadline) }}</strong></div>
              <div class="kv-card"><span>地点</span><strong>{{ detail.city || '-' }} {{ detail.address || '' }}</strong></div>
              <div class="kv-card"><span>人数</span><strong>{{ detail.signupCount }}/{{ detail.capacity || '-' }}</strong></div>
              <div class="kv-card"><span>费用</span><strong>{{ detail.fee ?? 0 }} 元</strong></div>
            </div>
            <div class="hint">报名时会校验信誉分不少于 60、年龄不少于 16 岁，并要求确认安全须知。容量满后自动进入等待队列。</div>
          </el-tab-pane>

          <el-tab-pane label="签到" name="checkin">
            <div class="checkin-grid">
              <section class="dialog-panel">
                <div class="section-head compact">
                  <h3>签到码</h3>
                  <el-button v-if="isOwner" type="primary" :loading="actionLoading" @click="createCheckinCode">生成签到码</el-button>
                </div>
                <div v-if="visibleCheckinCode" class="code-box">{{ visibleCheckinCode }}</div>
                <div v-else class="hint">活动发起人开启签到后，已报名成员会在这里看到自己的专属签到码。</div>
                <img v-if="checkinQrDataUrl" :src="checkinQrDataUrl" alt="签到码" class="qr-image" />
              </section>
              <section class="dialog-panel">
                <div class="section-head compact">
                  <h3>扫码/输入签到</h3>
                </div>
                <div class="form-grid">
                  <el-input v-model="checkinForm.code" placeholder="签到码" />
                  <el-button @click="useCurrentLocation('checkin')">使用当前位置</el-button>
                  <el-input-number v-model="checkinForm.lng" :precision="6" :step="0.0001" placeholder="我的经度" />
                  <el-input-number v-model="checkinForm.lat" :precision="6" :step="0.0001" placeholder="我的纬度" />
                </div>
                <div class="hint">若活动设置了地点，签到时会校验你与活动点位的距离。使用他人的专属签到码时，也会直接签到到对应报名人名下。</div>
                <el-button type="primary" :loading="actionLoading" @click="submitCheckin">提交签到</el-button>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="总结" name="summary">
            <div v-if="canManageSummary" class="summary-manage">
              <section class="dialog-panel">
                <div class="section-head compact">
                  <h3>总结内容</h3>
                  <span class="muted">只有活动发起人可编辑，活动结束后开放</span>
                </div>
                <el-input v-model="summaryForm.content" type="textarea" :rows="6" placeholder="总结活动过程、成果、感想" />
                <div class="action-row">
                  <el-button :loading="actionLoading" @click="saveSummary(false)">保存草稿</el-button>
                  <el-button type="primary" :loading="actionLoading" @click="saveSummary(true)">发布总结</el-button>
                </div>
              </section>
              <section class="dialog-panel">
                <div class="section-head compact">
                  <h3>总结图片</h3>
                  <span class="muted">支持多图、追加上传、删除重传</span>
                </div>
                <input type="file" accept="image/png,image/jpeg,image/webp" multiple @change="onSummaryFilesChange" />
                <div v-if="summaryFiles.length" class="file-list">
                  <span v-for="file in summaryFiles" :key="file.name + file.size">{{ file.name }}</span>
                </div>
                <div class="action-row">
                  <el-button type="primary" :disabled="!summaryFiles.length" :loading="actionLoading" @click="uploadSelectedSummaryFiles">上传图片</el-button>
                </div>
                <div class="image-grid" v-if="summary?.images?.length">
                  <div v-for="image in summary.images" :key="image.id" class="image-card">
                    <img :src="image.imageUrl" alt="summary" />
                    <div class="image-meta">
                      <span>AI 识别：{{ imageCategoryLabel(image.aiCategory) }}</span>
                      <span>已确认：{{ imageCategoryLabel(image.confirmedCategory) }}</span>
                    </div>
                    <div class="action-row wrap">
                      <el-select :model-value="image.confirmedCategory || image.aiCategory" placeholder="确认分类" @change="(value: string | number | boolean) => confirmImage(image, String(value))">
                        <el-option v-for="option in summaryCategoryOptions" :key="option.value" :label="option.label" :value="option.value" />
                      </el-select>
                      <el-button type="danger" plain @click="deleteImage(image)">删除</el-button>
                    </div>
                  </div>
                </div>
              </section>
            </div>
            <section class="dialog-panel">
              <div class="section-head compact">
                <h3>已发布总结</h3>
              </div>
              <el-empty v-if="!summary || summary.status !== 'PUBLISHED'" description="暂未发布总结" />
              <template v-else>
                <p class="summary-content">{{ summary.content || '暂无文字总结' }}</p>
                <div class="image-grid" v-if="summary.images.length">
                  <div v-for="image in summary.images" :key="image.id" class="image-card readonly">
                    <img :src="image.imageUrl" alt="summary" />
                    <div class="image-meta">
                      <span>分类：{{ imageCategoryLabel(image.confirmedCategory || image.aiCategory) }}</span>
                    </div>
                  </div>
                </div>
              </template>
            </section>
          </el-tab-pane>

          <el-tab-pane label="评价" name="reviews">
            <section class="dialog-panel">
              <div class="section-head compact">
                <h3>参与者评价</h3>
                <span class="muted">活动结束后 7 天内可评价，过期自动隐藏</span>
              </div>
              <div v-if="canReview" class="review-form">
                <el-rate v-model="reviewForm.rating" />
                <el-input v-model="reviewForm.content" type="textarea" :rows="4" placeholder="写下你的体验" />
                <el-button type="primary" :loading="actionLoading" @click="submitReview">提交评价</el-button>
              </div>
              <el-empty v-if="!reviews.length" description="暂无评价" />
              <div v-else class="review-list">
                <div v-for="review in reviews" :key="review.id" class="review-card">
                  <div class="title-row">
                    <strong>{{ review.nickname || "未知用户" }}</strong>
                    <span>{{ formatTime(review.createdAt) }}</span>
                  </div>
                  <el-rate :model-value="review.rating" disabled />
                  <p>{{ review.content || '未填写文字评价' }}</p>
                </div>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane v-if="isOwner" label="管理" name="manage">
            <div class="manage-grid">
              <section class="dialog-panel">
                <div class="section-head compact">
                  <h3>报名名单</h3>
                  <span class="muted">{{ signups.length }} 人</span>
                </div>
                <el-empty v-if="!signups.length" description="暂无报名" />
                <div v-else class="table-list">
                  <div v-for="item in signups" :key="item.signupId" class="table-row">
                    <span>{{ item.nickname || "未知用户" }}</span>
                    <span>{{ item.signupStatus }}</span>
                    <span>{{ item.checkedIn ? '已签到' : '未签到' }}</span>
                  </div>
                </div>
              </section>
              <section class="dialog-panel">
                <div class="section-head compact">
                  <h3>等待队列</h3>
                  <span class="muted">{{ waitlist?.waitlistCount || 0 }} 人</span>
                </div>
                <el-empty v-if="!waitlist?.list?.length" description="暂无候补" />
                <div v-else class="table-list">
                  <div v-for="item in waitlist.list" :key="item.id" class="table-row">
                    <span>{{ item.nickname || "未知用户" }}</span>
                    <span>排位 {{ item.position }}</span>
                    <span>{{ item.status }}</span>
                  </div>
                </div>
              </section>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.activity-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel,
.dialog-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.panel {
  padding: 16px;
}

.dialog-panel {
  padding: 16px;
}

.page-toolbar,
.layout-grid,
.mine-grid,
.dialog-grid,
.manage-grid,
.checkin-grid,
.summary-manage,
.overview-grid,
.form-grid,
.toolbar-grid {
  display: grid;
  gap: 12px;
}

.layout-grid {
  --row-h: 560px;
  grid-template-columns: 1.2fr 1fr 0.7fr;
  align-items: start;
}

.layout-grid.no-map {
  grid-template-columns: 1fr 0.7fr;
}

.layout-grid .map-panel,
.layout-grid .list-panel {
  height: var(--row-h);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sidebar-mine {
  height: var(--row-h);
  overflow-y: auto;
  padding: 16px;
}
.sidebar-mine .section-head h3 { font-size: 14px; margin: 0; }
.sidebar-mine .empty-hint { font-size: 13px; color: #999; padding: 8px 0; }

.layout-grid .section-head {
  flex: 0 0 auto;
}

/* 地图 canvas 填满面板剩余高度；活动发现列表吃剩余高度独立滚动 */
.map-panel .map-canvas,
.list-panel .activity-list {
  flex: 1 1 auto;
  min-height: 0;
}

.list-panel .activity-list {
  overflow-y: auto;
}

.layout-grid.no-map {
  grid-template-columns: 1fr;
}

.layout-grid.no-map .map-panel {
  display: none;
}

/* 收起地图后活动发现占满宽，卡片改多列网格利用宽度，仍保持固定高滚动 */
.layout-grid.no-map .activity-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  align-content: start;
}

.mine-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.toolbar-top,
.toolbar-bottom,
.section-head,
.title-row,
.action-row,
.inline-row,
.detail-hero,
.toolbar-actions,
.section-meta,
.image-meta,
.table-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-top,
.toolbar-bottom,
.section-head,
.detail-hero {
  justify-content: space-between;
}

.toolbar-grid,
.overview-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.form-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.dialog-grid {
  grid-template-columns: 1fr;
}

.summary-manage,
.manage-grid,
.checkin-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.inline-row.two-up {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  width: 100%;
}

.map-canvas,
.picker-map {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

/* 主地图填满面板高度（配合 .map-panel .map-canvas 的 flex），并给窄屏兜底最小高度 */
.map-canvas {
  min-height: 320px;
}

.picker-map {
  height: 280px;
}

.location-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.location-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 14px;
  background: #f8fbff;
}

.location-label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 6px;
}

.location-card strong {
  display: block;
  font-size: 14px;
  color: #111827;
  word-break: break-all;
}

.activity-list,
.mine-list,
.review-list,
.table-list,
.file-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-card,
.mine-card {
  width: 100%;
  text-align: left;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
  background: #fff;
  cursor: pointer;
}

.activity-card:hover,
.mine-card:hover {
  border-color: #409eff;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  color: #4b5563;
  font-size: 14px;
}

.tag-row,
.template-actions,
.detail-actions,
.review-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.audit-banner {
  margin: 8px 0 4px;
}

.intro,
.summary-content,
.hint {
  color: #4b5563;
  line-height: 1.6;
}
.creator-line {
  color: #6b7280;
  font-size: 13px;
  margin: 4px 0 8px;
}
.creator-line .merchant-badge {
  color: #d48806;
  font-weight: 600;
}

.muted {
  color: #6b7280;
  font-size: 14px;
}

.kv-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.code-box {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #f3f4f6;
}

.qr-image {
  width: 180px;
  height: 180px;
  object-fit: contain;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.image-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-card img {
  width: 100%;
  height: 160px;
  object-fit: cover;
  border-radius: 6px;
  background: #f3f4f6;
}

.image-meta {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  color: #4b5563;
  font-size: 14px;
}

.table-row {
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
}

.wrap {
  flex-wrap: wrap;
}

.compact {
  margin-bottom: 10px;
}

@media (max-width: 1200px) {
  .layout-grid,
  .mine-grid,
  .dialog-grid,
  .manage-grid,
  .checkin-grid,
  .summary-manage {
    grid-template-columns: 1fr;
  }

  /* 窄屏放开固定高度，改回自然堆叠滚动 */
  .layout-grid .map-panel,
  .layout-grid .list-panel {
    height: auto;
  }

  .list-panel .activity-list {
    overflow-y: visible;
  }

  .layout-grid.no-map .activity-list {
    grid-template-columns: 1fr;
  }

  .toolbar-grid,
  .form-grid,
  .overview-grid,
  .meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .toolbar-grid,
  .form-grid,
  .overview-grid,
  .meta-grid,
  .inline-row.two-up {
    grid-template-columns: 1fr;
  }

  .toolbar-top,
  .toolbar-bottom,
  .section-head,
  .detail-hero {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
