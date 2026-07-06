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
const filterDrawer = ref(false)
const listMode = ref<'discover' | 'mine' | 'joined'>('discover')
const editingId = ref<number | null>(null)
const detailTab = ref('overview')
const aiTheme = ref('')
const tagText = ref('')
const generatedCode = ref('')
const checkinQrDataUrl = ref('')
const summaryFiles = ref<File[]>([])
const mapRef = ref<HTMLDivElement | null>(null)
const pickerMapRef = ref<HTMLDivElement | null>(null)
const locating = ref(false)
const myLocation = ref<{ lng: number; lat: number } | null>(null)

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
// 仅当空位已释放并保留给本人(NOTIFIED)时才可确认；仅排队中(WAITING)无名额可确认
const canConfirmWaitlist = computed(() => detail.value?.myWaitlistStatus === 'NOTIFIED')
// 已进入候补但仍在排队、尚无空位
const isWaitingInQueue = computed(() => detail.value?.myWaitlistStatus === 'WAITING')
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
  // 重置地图实例，下次打开重新初始化
  pickerMap = null
  pickerMarker = null
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

const activeFilterCount = computed(() => {
  let n = 0
  if (query.categories.length) n++
  if (query.city) n++
  if (query.feeMin != null) n++
  if (query.feeMax != null) n++
  if (query.startFrom) n++
  if (query.startTo) n++
  return n
})

function switchTab(tab: string) {
  query.tab = tab
  loadActivities()
}

function resetFilters() {
  query.categories = []
  query.city = ''
  query.feeMin = undefined
  query.feeMax = undefined
  query.startFrom = ''
  query.startTo = ''
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
  // 提交发布时校验必填字段
  if (submit) {
    if (!form.name.trim()) { ElMessage.warning('请填写活动名称'); return }
    if (!form.intro || !form.intro.trim()) { ElMessage.warning('请填写活动简介'); return }
    if (!form.capacity || form.capacity < 1) { ElMessage.warning('人数上限必须大于等于1'); return }
    if (!form.startTime) { ElMessage.warning('请选择活动开始时间'); return }
    if (!form.endTime) { ElMessage.warning('请选择活动结束时间'); return }
    if (!form.signupDeadline) { ElMessage.warning('请选择报名截止时间'); return }
  }
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

function useMyLocation() {
  if (!navigator.geolocation) {
    ElMessage.warning('浏览器不支持定位，请在地图上点击选择')
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      const lng = pos.coords.longitude
      const lat = pos.coords.latitude
      if (locationPickerTarget.value === 'query') {
        query.lng = lng
        query.lat = lat
      } else {
        form.lng = lng
        form.lat = lat
      }
      if (locationPickerMap) {
        const AMap = window.AMap
        locationPickerMap.setCenter([lng, lat])
        if (locationPickerMarker) locationPickerMarker.setPosition([lng, lat])
        else if (AMap) locationPickerMarker = new AMap.Marker({ map: locationPickerMap, position: [lng, lat] })
      }
      ElMessage.success('已定位到当前位置')
    },
    () => { ElMessage.warning('定位失败，请确认已授权位置权限或在地图上点选') },
    { timeout: 8000 }
  )
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

async function locateMe(opts: { center: boolean; silent?: boolean }) {
  if (!amap) return
  locating.value = true
  let result: { lng: number; lat: number } | null = null
  let lastError = ''
  try {
    result = await requestBrowserLocation()
  } catch (error: any) {
    lastError = error?.message || ''
    try {
      result = await requestAmapLocation()
    } catch (fallbackError: any) {
      lastError = fallbackError?.message || lastError
    }
  } finally {
    locating.value = false
  }
  if (!result) {
    if (!opts.silent) ElMessage.warning(resolveLocationFailureMessage(lastError))
    return
  }
  myLocation.value = result
  setMyLocationMarker(result.lng, result.lat)
  if (opts.center) amap.setCenter([result.lng, result.lat])
  if (!opts.silent) ElMessage.success('已定位到当前位置')
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
    // 进入地图模式时自动定位并居中；静默失败（拒权/HTTP）不打扰用户
    locateMe({ center: true, silent: true })
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
    <!-- 命令台 · 发现活动 -->
    <section class="discover-hero">
      <div class="hero-lead">
        <span class="hero-eyebrow">◍ 此刻 · 附近正在发生</span>
        <h1>发现身边<br>正在发生的事</h1>
        <p class="hero-sub">以兴趣为纽带，赴一场线下相遇。</p>
      </div>

      <div class="hero-console">
        <div class="seg">
          <button type="button" :class="{ on: query.tab === 'RECOMMEND' }" @click="switchTab('RECOMMEND')">推荐</button>
          <button type="button" :class="{ on: query.tab === 'LATEST' }" @click="switchTab('LATEST')">最新</button>
          <button type="button" :class="{ on: query.tab === 'NEARBY' }" @click="switchTab('NEARBY')">附近</button>
        </div>

        <div class="hero-search">
          <span class="hs-icon">🔍</span>
          <input v-model="query.keyword" placeholder="搜索活动标题、标签或简介" @keyup.enter="loadActivities" />
          <button type="button" class="hs-go" @click="loadActivities">搜索</button>
        </div>

        <div class="hero-tools">
          <button type="button" class="tool" @click="filterDrawer = true">
            <span>筛选</span>
            <span v-if="activeFilterCount" class="tool-badge">{{ activeFilterCount }}</span>
          </button>
          <button type="button" class="tool" @click="openLocationPicker('query')">📍 我的位置</button>
          <span class="tool-flex"></span>
          <button type="button" class="tool create" @click="openCreate">＋ 发起活动</button>
        </div>
      </div>
    </section>

    <!-- 舞台 · 地图 + 列表 -->
    <section class="stage" :class="{ 'no-map': !showMapPanel }">
      <section v-show="showMapPanel" class="stage-map">
        <div class="stage-head">
          <h3><span class="dot"></span>地图</h3>
          <div class="stage-meta">
            <span class="mono-count">{{ mapPoints.length }} 点位</span>
            <button type="button" class="mini" :disabled="mapLoading" @click="refreshMapPoints(true)">刷新</button>
            <button type="button" class="mini" @click="showMapPanel = false">收起</button>
          </div>
        </div>
        <div v-if="amapKey" class="map-canvas-wrap">
          <div ref="mapRef" class="map-canvas" />
          <button
            class="map-locate-btn"
            :class="{ 'is-loading': locating }"
            type="button"
            title="回到我的位置"
            @click="locateMe({ center: true })"
          >
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="3" />
              <line x1="12" y1="2" x2="12" y2="5" />
              <line x1="12" y1="19" x2="12" y2="22" />
              <line x1="2" y1="12" x2="5" y2="12" />
              <line x1="19" y1="12" x2="22" y2="12" />
            </svg>
          </button>
        </div>
        <el-empty v-else description="未配置地图 Key，仍可使用列表发现活动" />
      </section>

      <section class="stage-list">
        <div class="stage-head">
          <div class="list-seg">
            <button type="button" :class="{ on: listMode === 'discover' }" @click="listMode = 'discover'">发现<i>{{ total }}</i></button>
            <button type="button" v-if="auth.token" :class="{ on: listMode === 'mine' }" @click="listMode = 'mine'">我发起<i>{{ mineActivities.length }}</i></button>
            <button type="button" v-if="auth.token" :class="{ on: listMode === 'joined' }" @click="listMode = 'joined'">我报名<i>{{ joinedActivities.length }}</i></button>
          </div>
          <button type="button" v-if="!showMapPanel" class="mini" @click="showMapPanel = true">显示地图</button>
        </div>

        <!-- 发现 -->
        <el-skeleton v-if="listMode === 'discover'" :loading="loading" animated :rows="6">
          <div class="pass-list">
            <button v-for="item in activities" :key="String(item.id)" type="button" class="activity-card" @click="openDetail(item.id as number)">
              <div class="card-eyebrow">
                <span class="ce-cat">{{ categoryLabel(item.category) }}</span>
                <span class="ce-sep">/</span>
                <span class="ce-city">{{ item.city || '未设城市' }}</span>
                <span class="ce-stamp" :class="'st-' + activityStatusTagType(item.status)">{{ activityStatusLabel(item.status) }}</span>
              </div>
              <div class="title-row">
                <h4>{{ item.name }}</h4>
                <el-tag v-if="item.creator?.userType === 'MERCHANT'" size="small" type="warning" effect="dark">商家</el-tag>
              </div>
              <p class="intro">{{ item.intro || '暂无简介' }}</p>
              <div class="meta-strip">
                <span class="ms"><b>{{ activityPhaseLabel(item.phase) }}</b><i>阶段</i></span>
                <span class="ms"><b>{{ item.signupCount }}<em>/{{ item.capacity || '∞' }}</em></b><i>报名</i></span>
                <span class="ms-tags">
                  <el-tag v-for="tag in item.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                </span>
              </div>
            </button>
            <div v-if="!activities.length" class="list-empty">附近暂时没有匹配的活动，换个关键词或筛选试试。</div>
          </div>
        </el-skeleton>

        <!-- 我发起 / 我报名 -->
        <div v-else class="pass-list">
          <button
            v-for="item in (listMode === 'mine' ? mineActivities : joinedActivities)"
            :key="String(item.id)"
            type="button"
            class="activity-card compact-pass"
            @click="openDetail(item.id as number)"
          >
            <div class="card-eyebrow">
              <span class="ce-cat">{{ categoryLabel(item.category) }}</span>
              <span class="ce-sep">/</span>
              <span class="ce-city">{{ item.city || '未设城市' }}</span>
              <span class="ce-stamp" :class="'st-' + activityStatusTagType(item.status)">{{ activityStatusLabel(item.status) }}</span>
            </div>
            <div class="title-row"><h4>{{ item.name }}</h4></div>
          </button>
          <div v-if="!(listMode === 'mine' ? mineActivities : joinedActivities).length" class="list-empty">
            {{ listMode === 'mine' ? '你还没有发起活动，点「发起活动」开始第一场。' : '你还没有报名任何活动。' }}
          </div>
        </div>
      </section>
    </section>

    <!-- 高级筛选抽屉 -->
    <el-drawer v-model="filterDrawer" title="高级筛选" size="384px">
      <div class="filter-body">
        <div class="filter-field">
          <label>活动分类</label>
          <el-select v-model="query.categories" multiple collapse-tags collapse-tags-tooltip placeholder="全部分类" style="width:100%">
            <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>
        <div class="filter-field">
          <label>城市</label>
          <el-input v-model="query.city" clearable placeholder="不限城市" />
        </div>
        <div class="filter-field">
          <label>费用范围（元）</label>
          <div class="filter-inline">
            <el-input-number v-model="query.feeMin" :min="0" :step="10" placeholder="最低" controls-position="right" />
            <span class="dash">–</span>
            <el-input-number v-model="query.feeMax" :min="0" :step="10" placeholder="最高" controls-position="right" />
          </div>
        </div>
        <div class="filter-field">
          <label>开始时间</label>
          <el-date-picker v-model="query.startFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="从" style="width:100%" />
          <el-date-picker v-model="query.startTo" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="到" style="width:100%;margin-top:8px" />
        </div>
        <div v-if="query.tab === 'NEARBY'" class="filter-field">
          <label>附近范围 · {{ query.distanceKm }} 公里</label>
          <el-slider v-model="query.distanceKm" :min="1" :max="50" />
        </div>
        <div class="filter-field">
          <label>从模板快速发起</label>
          <div class="tpl-chips">
            <button type="button" v-for="template in templates" :key="template.id" class="tpl-chip" @click="filterDrawer = false; fillFromTemplate(template)">
              {{ template.name }}
            </button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="resetFilters">重置</el-button>
        <el-button type="primary" @click="filterDrawer = false; loadActivities()">应用筛选</el-button>
      </template>
    </el-drawer>

    <!-- 地图选点弹窗 -->
    <el-dialog v-model="locationPickerVisible" title="在地图上选择位置" width="560px">
      <div ref="locationPickerMapRef" style="width:100%;height:350px;border-radius:8px"></div>
      <div style="display:flex;align-items:center;justify-content:space-between;margin-top:8px">
        <p style="font-size:12px;color:#999;margin:0">点击地图或使用当前位置，坐标：{{ query.lng.toFixed(4) }}, {{ query.lat.toFixed(4) }}</p>
        <el-button size="small" @click="useMyLocation">📍 使用当前位置</el-button>
      </div>
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
              <el-form-item label="人数上限" required><el-input-number v-model="form.capacity" :min="1" :max="500" /></el-form-item>
              <el-form-item label="费用（元）"><el-input-number v-model="form.fee" :min="0" :step="10" /></el-form-item>
              <el-form-item label="标签"><el-input v-model="tagText" placeholder="逗号分隔，如：户外,徒步" /></el-form-item>
            </div>
            <div class="form-row">
              <el-form-item label="开始时间" required><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择开始时间" /></el-form-item>
              <el-form-item label="结束时间" required><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择结束时间" /></el-form-item>
              <el-form-item label="报名截止" required><el-date-picker v-model="form.signupDeadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择截止时间" /></el-form-item>
            </div>
            <el-form-item label="活动简介" required><el-input v-model="form.intro" type="textarea" :rows="4" placeholder="活动简介、注意事项等" /></el-form-item>
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
            <el-tag v-if="isWaitingInQueue" type="info" effect="plain">候补中，有空位释放时将通知你确认</el-tag>
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
  gap: 18px;
}

/* ── 命令台 Hero ─────────────────────────────── */
.discover-hero {
  display: grid;
  grid-template-columns: minmax(220px, 0.75fr) 1.7fr;
  gap: 28px;
  align-items: center;
  padding: 6px 2px 2px;
}
.hero-eyebrow {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--signal);
}
.hero-lead h1 {
  font-size: 34px;
  line-height: 1.08;
  letter-spacing: -0.02em;
  margin: 8px 0 8px;
}
.hero-sub { margin: 0; color: var(--ink-soft); font-size: 14px; }

.hero-console {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.seg {
  align-self: flex-start;
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 11px;
}
.seg button {
  border: none;
  background: transparent;
  padding: 7px 20px;
  border-radius: 8px;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-soft);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.seg button:hover { color: var(--ink); }
.seg button.on { background: var(--ink); color: var(--paper); }

.hero-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 4px 4px 14px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 11px;
  transition: border-color 0.15s ease;
}
.hero-search:focus-within { border-color: var(--signal); }
.hs-icon { font-size: 14px; opacity: 0.5; }
.hero-search input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  outline: none;
  font-family: var(--font-body);
  font-size: 15px;
  color: var(--ink);
  padding: 9px 2px;
}
.hs-go {
  border: none;
  background: var(--signal);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  padding: 9px 20px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.hs-go:hover { background: var(--signal-ink); }

.hero-tools { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.tool {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--line);
  background: var(--surface);
  border-radius: 9px;
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.tool:hover { border-color: var(--signal); color: var(--signal); }
.tool-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  background: var(--signal);
  color: #fff;
  border-radius: 20px;
  padding: 1px 6px;
}
.tool-flex { flex: 1; }
.tool.create { background: var(--signal); border-color: var(--signal); color: #fff; }
.tool.create:hover { background: var(--signal-ink); border-color: var(--signal-ink); color: #fff; }

/* ── 舞台 · 地图 + 列表 ────────────────────────── */
.stage {
  --stage-h: 648px;
  display: grid;
  grid-template-columns: 1.12fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.stage.no-map { grid-template-columns: 1fr; }
.stage-map,
.stage-list {
  height: var(--stage-h);
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}
.stage-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}
.stage-head h3 { margin: 0; font-size: 15px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.stage-head h3 .dot {
  width: 8px; height: 8px;
  border-radius: 50% 50% 50% 0;
  background: var(--signal);
  transform: rotate(-45deg);
}
.stage-meta { display: flex; align-items: center; gap: 10px; }
.mono-count { font-family: var(--font-mono); font-size: 12px; color: var(--ink-faint); }
.mini {
  border: 1px solid var(--line);
  background: transparent;
  border-radius: 7px;
  padding: 5px 11px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.mini:hover { border-color: var(--signal); color: var(--signal); }
.mini:disabled { opacity: 0.5; cursor: default; }

.list-seg {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 10px;
}
.list-seg button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  padding: 6px 12px;
  border-radius: 7px;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.list-seg button i { font-style: normal; font-family: var(--font-mono); font-size: 11px; opacity: 0.65; }
.list-seg button.on { background: var(--surface); color: var(--ink); box-shadow: var(--shadow); }

.stage-map .map-canvas-wrap { flex: 1 1 auto; min-height: 0; }
.stage-map .map-canvas { height: 100%; }

.pass-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 4px;
}
.compact-pass { padding: 12px 14px 12px 16px; }
.compact-pass .title-row h4 { font-size: 15.5px; }
.list-empty { padding: 40px 16px; text-align: center; color: var(--ink-faint); font-size: 14px; line-height: 1.6; }

/* ── 高级筛选抽屉 ─────────────────────────────── */
.filter-body { display: flex; flex-direction: column; gap: 20px; padding: 2px; }
.filter-field label {
  display: block;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-faint);
  margin-bottom: 8px;
}
.filter-inline { display: flex; align-items: center; gap: 8px; }
.filter-inline .dash { color: var(--ink-faint); }
.tpl-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.tpl-chip {
  font-family: var(--font-body);
  font-size: 13px;
  padding: 7px 13px;
  border: 1px solid var(--line-strong);
  background: var(--surface-2);
  border-radius: 20px;
  color: var(--ink-soft);
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.tpl-chip:hover { border-color: var(--signal); color: var(--signal); }

@media (max-width: 1024px) {
  .discover-hero { grid-template-columns: 1fr; gap: 16px; }
  .stage { grid-template-columns: 1fr; }
  .stage-map, .stage-list { height: auto; }
  .stage-map { min-height: 380px; }
  .pass-list { max-height: 72vh; }
}

.panel,
.dialog-panel {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
}

.panel {
  padding: 18px;
}

.dialog-panel {
  padding: 16px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.section-head h3::before {
  content: '';
  width: 8px; height: 8px;
  border-radius: 50% 50% 50% 0;
  background: var(--signal);
  transform: rotate(-45deg);
}
.list-panel .section-head h3::before,
.map-panel .section-head h3::before { display: inline-block; }

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
.sidebar-mine .empty-hint { font-size: 13px; color: var(--ink-faint); padding: 8px 0; }

.layout-grid .section-head {
  flex: 0 0 auto;
}

/* 地图 canvas 填满面板剩余高度；活动发现列表吃剩余高度独立滚动 */
.map-panel .map-canvas-wrap,
.list-panel .activity-list {
  flex: 1 1 auto;
  min-height: 0;
}

/* 地图容器包裹层：承载右下角定位按钮的绝对定位 */
.map-canvas-wrap {
  position: relative;
}

.map-canvas-wrap .map-canvas {
  height: 100%;
}

/* 右下角"回到我的位置"按钮，走 --qj-* 变量以适配暗色模式 */
.map-locate-btn {
  position: absolute;
  right: 16px;
  bottom: 24px;
  z-index: 10;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--qj-card);
  color: var(--qj-text);
  border: 1px solid var(--qj-border);
  box-shadow: var(--qj-shadow, 0 2px 8px rgba(0, 0, 0, 0.15));
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.map-locate-btn:hover {
  color: var(--qj-primary);
}

.map-locate-btn.is-loading {
  opacity: 0.6;
  pointer-events: none;
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
  border: 1px solid var(--line);
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
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px 14px;
  background: var(--surface-2);
}

.location-label {
  display: block;
  font-size: 12px;
  color: var(--ink-soft);
  margin-bottom: 6px;
}

.location-card strong {
  display: block;
  font-size: 14px;
  color: var(--ink);
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
  position: relative;
  width: 100%;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 15px 16px 15px 18px;
  background: var(--surface);
  cursor: pointer;
  /* 左侧类目脊 —— 用 inset 阴影，悬停点亮，像活动通行证的装订边 */
  box-shadow: inset 3px 0 0 var(--line-strong);
  transition: border-color 0.15s ease, box-shadow 0.2s ease, transform 0.15s ease;
}
.activity-card:hover,
.mine-card:hover {
  border-color: var(--line-strong);
  box-shadow: inset 3px 0 0 var(--signal), var(--shadow);
  transform: translateY(-1px);
}
.card-spine { display: none; }

.card-eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font-mono);
  font-size: 11.5px;
  letter-spacing: 0.02em;
  color: var(--ink-soft);
  margin-bottom: 7px;
}
.ce-cat { color: var(--ink); font-weight: 700; }
.ce-sep { color: var(--ink-faint); }
.ce-stamp {
  margin-left: auto;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 2px 7px;
  border-radius: 4px;
  border: 1px solid currentColor;
  text-transform: uppercase;
}
/* 状态邮戳配色（映射 element tag type） */
.ce-stamp.st-success { color: var(--route); background: var(--route-wash); }
.ce-stamp.st-warning { color: var(--stamp); background: var(--stamp-wash); }
.ce-stamp.st-danger  { color: var(--signal); background: var(--signal-wash); }
.ce-stamp.st-info    { color: var(--ink-faint); background: var(--surface-2); }
.ce-stamp.st-primary { color: var(--route); background: var(--route-wash); }
.ce-stamp[class="ce-stamp"] { color: var(--ink-soft); background: var(--surface-2); }

.activity-card .title-row h4 { margin: 0; font-size: 16.5px; font-weight: 700; }

.meta-strip {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}
.ms { flex: 0 0 auto; }
.ms { display: flex; flex-direction: column; line-height: 1.25; }
.ms b { font-family: var(--font-mono); font-size: 14px; font-weight: 700; color: var(--ink); }
.ms b em { font-style: normal; color: var(--ink-faint); font-weight: 400; }
.ms i { font-style: normal; font-size: 11px; color: var(--ink-faint); }
.ms-tags { flex: 1 1 auto; min-width: 0; display: flex; flex-wrap: nowrap; gap: 6px; justify-content: flex-end; overflow: hidden; max-height: 24px; }
/* 卡片内标签走中性色，避免与状态邮戳争夺注意力 */
.ms-tags .el-tag {
  --el-tag-text-color: var(--ink-soft);
  --el-tag-border-color: var(--line-strong);
  --el-tag-bg-color: transparent;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  color: var(--ink-soft);
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
  color: var(--ink-soft);
  line-height: 1.6;
}
.creator-line {
  color: var(--ink-soft);
  font-size: 13px;
  margin: 4px 0 8px;
}
.creator-line .merchant-badge {
  color: #d48806;
  font-weight: 600;
}

.muted {
  color: var(--ink-soft);
  font-size: 14px;
}

.kv-card {
  border: 1px solid var(--line);
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
  background: var(--surface-2);
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
  border: 1px solid var(--line);
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
  background: var(--surface-2);
}

.image-meta {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  color: var(--ink-soft);
  font-size: 14px;
}

.table-row {
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
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
