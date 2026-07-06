<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '../../api/admin'
import { useAdminAuthStore } from '../../stores/adminAuth'

const adminAuth = useAdminAuthStore()
const router = useRouter()
const loading = ref(true)

// 目标真值 + 动画显示值（数字滚动到位）
const target = reactive({ users: 0, activities: 0, teams: 0, pendingMerchants: 0, pendingActivities: 0, pendingReports: 0 })
const display = reactive({ users: 0, activities: 0, teams: 0, pendingMerchants: 0, pendingActivities: 0, pendingReports: 0 })

type Key = keyof typeof target
function tween(key: Key, to: number, duration = 1000) {
  const from = display[key]
  if (to === from) return
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduce) { display[key] = to; return }
  const start = performance.now()
  const step = (t: number) => {
    const p = Math.min((t - start) / duration, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    display[key] = Math.round(from + (to - from) * eased)
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

// 用列表接口的 total 字段拿真实规模数据（size:1 只为取计数）
async function loadStats() {
  loading.value = true
  const pick = (r: PromiseSettledResult<{ total: number }>) => (r.status === 'fulfilled' ? r.value.total : 0)
  const [users, acts, teams, pm, pa, pr] = await Promise.allSettled([
    adminApi.getUsers({ size: 1 }),
    adminApi.getActivities({ size: 1 }),
    adminApi.getTeams({ size: 1 }),
    adminApi.getMerchantApplications({ status: 'PENDING', size: 1 }),
    adminApi.getPendingReviewActivities({ size: 1 }),
    adminApi.getReports({ status: 'PENDING', size: 1 }),
  ])
  Object.assign(target, {
    users: pick(users), activities: pick(acts), teams: pick(teams),
    pendingMerchants: pick(pm), pendingActivities: pick(pa), pendingReports: pick(pr),
  })
  loading.value = false
  ;(Object.keys(target) as Key[]).forEach((k, i) => setTimeout(() => tween(k, target[k]), 80 + i * 60))
}

const kpis = computed(() => [
  { key: 'users' as Key, label: '注册用户', unit: 'USERS', to: '/admin/users' },
  { key: 'activities' as Key, label: '活动总量', unit: 'ACTIVITIES', to: '/admin/activities' },
  { key: 'teams' as Key, label: '兴趣小队', unit: 'TEAMS', to: '/admin/teams' },
])

const pendings = computed(() => [
  { key: 'pendingMerchants' as Key, label: '商家待审核', unit: 'MERCHANTS', to: '/admin/merchants' },
  { key: 'pendingActivities' as Key, label: '活动待审核', unit: 'ACTIVITIES', to: '/admin/activities' },
  { key: 'pendingReports' as Key, label: '举报待处理', unit: 'REPORTS', to: '/admin/reports' },
])

const totalPending = computed(() => target.pendingMerchants + target.pendingActivities + target.pendingReports)

const now = ref(new Date())
let timer: number | undefined
const clock = computed(() => now.value.toLocaleTimeString('zh-CN', { hour12: false }))
const today = computed(() => now.value.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' }))
const greeting = computed(() => {
  const h = now.value.getHours()
  return h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'
})

function fmt(n: number) { return n.toLocaleString('en-US') }

onMounted(() => {
  loadStats()
  timer = window.setInterval(() => { now.value = new Date() }, 1000)
})
onBeforeUnmount(() => { if (timer) window.clearInterval(timer) })
</script>

<template>
  <div class="page dash" v-loading="loading">
    <!-- 态势板 -->
    <section class="board">
      <div class="aura">
        <span class="blob b1" />
        <span class="blob b2" />
        <span class="blob b3" />
      </div>
      <div class="board-inner">
        <div class="board-top">
          <span class="board-eyebrow">CONSOLE · 态势总览</span>
          <span class="board-clock">{{ clock }}</span>
        </div>
        <div class="board-hello">
          <h2>{{ greeting }}，{{ adminAuth.username || 'admin' }}</h2>
          <p>{{ today }} · 当前有 <em :class="{ hot: totalPending > 0 }">{{ totalPending }}</em> 项待办</p>
        </div>
        <div class="kpi-row">
          <router-link v-for="(k, i) in kpis" :key="k.key" :to="k.to" class="kpi" :style="{ '--i': i }">
            <span class="kpi-unit">{{ k.unit }}</span>
            <span class="kpi-value">{{ fmt(display[k.key]) }}</span>
            <span class="kpi-label">{{ k.label }}</span>
          </router-link>
        </div>
      </div>
    </section>

    <!-- 待办告警 -->
    <div class="section-label"><span>待办队列</span> PENDING QUEUE</div>
    <div class="pending-row">
      <router-link
        v-for="(p, i) in pendings"
        :key="p.unit"
        :to="p.to"
        class="alert"
        :class="{ hot: target[p.key] > 0 }"
        :style="{ '--i': i }"
      >
        <div class="alert-top">
          <span class="alert-dot" />
          <span class="alert-unit">{{ p.unit }}</span>
        </div>
        <span class="alert-value">{{ display[p.key] }}</span>
        <div class="alert-foot">
          <span class="alert-label">{{ p.label }}</span>
          <span class="alert-go">{{ target[p.key] > 0 ? '去处理 →' : '已清空 ✓' }}</span>
        </div>
      </router-link>
    </div>

    <!-- 快捷入口 -->
    <div class="section-label"><span>控制台</span> ALL CONSOLES</div>
    <div class="nav-row">
      <button class="nav-tile" style="--i:0" @click="router.push('/admin/users')">用户管理<span>USERS</span></button>
      <button class="nav-tile" style="--i:1" @click="router.push('/admin/merchants')">商家审核<span>MERCHANTS</span></button>
      <button class="nav-tile" style="--i:2" @click="router.push('/admin/activities')">活动管理<span>ACTIVITIES</span></button>
      <button class="nav-tile" style="--i:3" @click="router.push('/admin/teams')">小队管理<span>TEAMS</span></button>
      <button class="nav-tile" style="--i:4" @click="router.push('/admin/reports')">举报管理<span>REPORTS</span></button>
    </div>
  </div>
</template>

<style scoped>
.dash { --a-teal: #157a6e; }
@keyframes rise { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: none; } }

/* —— 态势板（浅色 + 极光） —— */
.board {
  position: relative; overflow: hidden;
  border-radius: 18px; padding: 0; margin-bottom: 26px;
  background: var(--surface); border: 1px solid var(--line);
  box-shadow: var(--shadow-hover);
  animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
}
.board::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 5px; background: var(--signal); z-index: 3; }
.board-inner {
  position: relative; z-index: 2; padding: 26px 28px 28px;
  background-image: radial-gradient(rgba(27,28,24,0.045) 1px, transparent 1px);
  background-size: 20px 20px;
}
/* 极光光斑 */
.aura { position: absolute; inset: 0; z-index: 1; overflow: hidden; }
.blob { position: absolute; border-radius: 50%; filter: blur(46px); opacity: 0.7; }
.b1 { width: 300px; height: 300px; background: rgba(255,67,36,0.16); top: -90px; left: 8%; animation: float1 14s ease-in-out infinite; }
.b2 { width: 260px; height: 260px; background: rgba(21,122,110,0.14); top: -40px; right: 14%; animation: float2 17s ease-in-out infinite; }
.b3 { width: 240px; height: 240px; background: rgba(200,134,13,0.14); bottom: -120px; left: 42%; animation: float3 20s ease-in-out infinite; }
@keyframes float1 { 0%,100% { transform: translate(0,0); } 50% { transform: translate(40px, 30px); } }
@keyframes float2 { 0%,100% { transform: translate(0,0); } 50% { transform: translate(-36px, 26px); } }
@keyframes float3 { 0%,100% { transform: translate(0,0); } 50% { transform: translate(24px, -30px); } }

.board-top { display: flex; align-items: center; justify-content: space-between; }
.board-eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.12em; color: var(--ink-faint); }
.board-clock { font-family: var(--font-mono); font-size: 15px; letter-spacing: 0.08em; color: var(--stamp); font-variant-numeric: tabular-nums; }
.board-hello { margin: 16px 0 24px; }
.board-hello h2 { margin: 0 0 6px; font-size: 28px; color: var(--ink); letter-spacing: 0.01em; }
.board-hello p { margin: 0; font-size: 13.5px; color: var(--ink-soft); }
.board-hello em { font-style: normal; font-family: var(--font-mono); color: var(--ink); }
.board-hello em.hot { color: var(--signal); font-weight: 700; }

.kpi-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.kpi {
  display: flex; flex-direction: column; gap: 3px; text-decoration: none;
  padding: 18px; border-radius: 13px;
  background: rgba(255,255,255,0.72); backdrop-filter: blur(4px);
  border: 1px solid var(--line);
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
  animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
  animation-delay: calc(var(--i) * 90ms + 120ms);
}
.kpi:hover { transform: translateY(-3px); border-color: var(--signal); box-shadow: 0 10px 26px rgba(255,67,36,0.12); }
.kpi-unit { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.1em; color: var(--ink-faint); }
.kpi-value { font-family: var(--font-mono); font-size: 40px; line-height: 1.1; font-weight: 700; color: var(--ink); font-variant-numeric: tabular-nums; }
.kpi-label { font-size: 13px; color: var(--ink-soft); }

/* —— section label —— */
.section-label {
  display: flex; align-items: center; gap: 10px;
  font-family: var(--font-mono); font-size: 10.5px; letter-spacing: 0.1em; color: var(--ink-faint);
  margin: 0 2px 12px; text-transform: uppercase;
}
.section-label span { font-family: var(--font-body); font-size: 14px; letter-spacing: 0; color: var(--ink); font-weight: 600; text-transform: none; }
.section-label::after { content: ''; flex: 1; height: 1px; background: var(--line); }

/* —— 待办告警 —— */
.pending-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 28px; }
.alert {
  display: flex; flex-direction: column; gap: 8px; text-decoration: none;
  padding: 18px 20px; border-radius: var(--radius);
  background: var(--surface); border: 1px solid var(--line);
  box-shadow: inset 3px 0 0 var(--line-strong), var(--shadow);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
  animation-delay: calc(var(--i) * 90ms + 60ms);
}
.alert:hover { transform: translateY(-3px); }
.alert.hot { box-shadow: inset 3px 0 0 var(--signal), 0 8px 24px rgba(255,67,36,0.12); }
.alert-top { display: flex; align-items: center; gap: 8px; }
.alert-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--line-strong); }
.alert.hot .alert-dot { background: var(--signal); box-shadow: 0 0 0 4px var(--signal-wash); animation: pulse 1.8s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.45; transform: scale(0.82); } }
.alert-unit { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.08em; color: var(--ink-faint); }
.alert-value { font-family: var(--font-mono); font-size: 38px; line-height: 1; font-weight: 700; color: var(--ink-faint); font-variant-numeric: tabular-nums; }
.alert.hot .alert-value { color: var(--signal); }
.alert-foot { display: flex; align-items: center; justify-content: space-between; }
.alert-label { font-size: 14px; color: var(--ink); }
.alert-go { font-family: var(--font-mono); font-size: 11px; color: var(--ink-faint); transition: transform 0.18s ease; }
.alert:hover .alert-go { transform: translateX(3px); }
.alert.hot .alert-go { color: var(--signal-ink); }

/* —— 快捷入口 —— */
.nav-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.nav-tile {
  display: flex; flex-direction: column; gap: 5px; align-items: flex-start; cursor: pointer;
  padding: 16px; border-radius: var(--radius-sm); text-align: left;
  background: var(--surface); border: 1px solid var(--line); color: var(--ink);
  font-size: 14px; transition: all 0.18s ease;
  animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.3, 1) both;
  animation-delay: calc(var(--i) * 60ms + 100ms);
}
.nav-tile span { font-family: var(--font-mono); font-size: 9.5px; letter-spacing: 0.08em; color: var(--ink-faint); }
.nav-tile:hover { border-color: var(--signal); color: var(--signal); transform: translateY(-3px); box-shadow: var(--shadow); }
.nav-tile:hover span { color: var(--signal); }

@media (max-width: 900px) {
  .kpi-row, .pending-row { grid-template-columns: 1fr; }
  .nav-row { grid-template-columns: repeat(2, 1fr); }
}
@media (prefers-reduced-motion: reduce) {
  .board, .kpi, .alert, .nav-tile { animation: none; }
  .blob { animation: none; }
  .alert.hot .alert-dot { animation: none; }
}
</style>
