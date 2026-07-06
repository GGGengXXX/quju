<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '../../api/admin'
import { useAdminAuthStore } from '../../stores/adminAuth'

const adminAuth = useAdminAuthStore()
const router = useRouter()
const loading = ref(true)

const stats = ref({
  users: 0,
  activities: 0,
  teams: 0,
  pendingMerchants: 0,
  pendingActivities: 0,
  pendingReports: 0,
})

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
  stats.value = {
    users: pick(users), activities: pick(acts), teams: pick(teams),
    pendingMerchants: pick(pm), pendingActivities: pick(pa), pendingReports: pick(pr),
  }
  loading.value = false
}

const kpis = computed(() => [
  { key: 'users', label: '注册用户', unit: 'USERS', value: stats.value.users, to: '/admin/users' },
  { key: 'activities', label: '活动总量', unit: 'ACTIVITIES', value: stats.value.activities, to: '/admin/activities' },
  { key: 'teams', label: '兴趣小队', unit: 'TEAMS', value: stats.value.teams, to: '/admin/teams' },
])

const pendings = computed(() => [
  { label: '商家待审核', unit: 'MERCHANTS', value: stats.value.pendingMerchants, to: '/admin/merchants' },
  { label: '活动待审核', unit: 'ACTIVITIES', value: stats.value.pendingActivities, to: '/admin/activities' },
  { label: '举报待处理', unit: 'REPORTS', value: stats.value.pendingReports, to: '/admin/reports' },
])

const totalPending = computed(() => stats.value.pendingMerchants + stats.value.pendingActivities + stats.value.pendingReports)

const now = ref(new Date())
let timer: number | undefined
const clock = computed(() =>
  now.value.toLocaleTimeString('zh-CN', { hour12: false })
)
const today = computed(() =>
  now.value.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' })
)
const greeting = computed(() => {
  const h = now.value.getHours()
  return h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'
})

function fmt(n: number) {
  return n.toLocaleString('en-US')
}

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
      <div class="board-top">
        <span class="board-eyebrow">CONSOLE · 态势总览</span>
        <span class="board-clock">{{ clock }}</span>
      </div>
      <div class="board-hello">
        <h2>{{ greeting }}，{{ adminAuth.username || 'admin' }}</h2>
        <p>{{ today }} · 当前有 <em :class="{ hot: totalPending > 0 }">{{ totalPending }}</em> 项待办</p>
      </div>
      <div class="kpi-row">
        <router-link v-for="k in kpis" :key="k.key" :to="k.to" class="kpi">
          <span class="kpi-unit">{{ k.unit }}</span>
          <span class="kpi-value">{{ fmt(k.value) }}</span>
          <span class="kpi-label">{{ k.label }}</span>
        </router-link>
      </div>
    </section>

    <!-- 待办告警 -->
    <div class="section-label"><span>待办队列</span> PENDING QUEUE</div>
    <div class="pending-row">
      <router-link
        v-for="p in pendings"
        :key="p.unit"
        :to="p.to"
        class="alert"
        :class="{ hot: p.value > 0 }"
      >
        <div class="alert-top">
          <span class="alert-dot" />
          <span class="alert-unit">{{ p.unit }}</span>
        </div>
        <span class="alert-value">{{ p.value }}</span>
        <div class="alert-foot">
          <span class="alert-label">{{ p.label }}</span>
          <span class="alert-go">{{ p.value > 0 ? '去处理 →' : '已清空 ✓' }}</span>
        </div>
      </router-link>
    </div>

    <!-- 快捷入口 -->
    <div class="section-label"><span>控制台</span> ALL CONSOLES</div>
    <div class="nav-row">
      <button class="nav-tile" @click="router.push('/admin/users')">用户管理<span>USERS</span></button>
      <button class="nav-tile" @click="router.push('/admin/merchants')">商家审核<span>MERCHANTS</span></button>
      <button class="nav-tile" @click="router.push('/admin/activities')">活动管理<span>ACTIVITIES</span></button>
      <button class="nav-tile" @click="router.push('/admin/teams')">小队管理<span>TEAMS</span></button>
      <button class="nav-tile" @click="router.push('/admin/reports')">举报管理<span>REPORTS</span></button>
    </div>
  </div>
</template>

<style scoped>
.dash { --a-signal: #ff5c3d; --a-teal: #3ba593; --a-amber: #e0a11b; }

/* —— 态势板 —— */
.board {
  position: relative; overflow: hidden;
  border-radius: 18px; padding: 26px 28px 28px; margin-bottom: 26px;
  background: #16160f; color: #f2f1ea;
  background-image: radial-gradient(rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: 20px 20px;
  box-shadow: 0 20px 50px rgba(22,22,15,0.28);
}
.board::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 5px; background: var(--a-signal); }
.board-top { display: flex; align-items: center; justify-content: space-between; }
.board-eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.12em; color: rgba(242,241,234,0.45); }
.board-clock { font-family: var(--font-mono); font-size: 15px; letter-spacing: 0.08em; color: var(--a-amber); }
.board-hello { margin: 16px 0 24px; }
.board-hello h2 { margin: 0 0 6px; font-size: 28px; color: #fff; letter-spacing: 0.01em; }
.board-hello p { margin: 0; font-size: 13.5px; color: rgba(242,241,234,0.6); }
.board-hello em { font-style: normal; font-family: var(--font-mono); color: rgba(242,241,234,0.85); }
.board-hello em.hot { color: var(--a-signal); font-weight: 700; }

.kpi-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.kpi {
  display: flex; flex-direction: column; gap: 3px; text-decoration: none;
  padding: 18px; border-radius: 13px;
  background: rgba(255,255,255,0.04); border: 1px solid rgba(242,241,234,0.10);
  transition: transform 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}
.kpi:hover { transform: translateY(-2px); border-color: rgba(255,92,61,0.5); background: rgba(255,255,255,0.06); }
.kpi-unit { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.1em; color: rgba(242,241,234,0.4); }
.kpi-value { font-family: var(--font-mono); font-size: 40px; line-height: 1.1; font-weight: 700; color: #fff; }
.kpi-label { font-size: 13px; color: rgba(242,241,234,0.65); }

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
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.alert:hover { transform: translateY(-2px); }
.alert.hot { box-shadow: inset 3px 0 0 var(--signal), 0 8px 24px rgba(255,67,36,0.12); }
.alert-top { display: flex; align-items: center; gap: 8px; }
.alert-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--line-strong); }
.alert.hot .alert-dot { background: var(--signal); box-shadow: 0 0 0 4px var(--signal-wash); animation: pulse 1.8s ease-in-out infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }
.alert-unit { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.08em; color: var(--ink-faint); }
.alert-value { font-family: var(--font-mono); font-size: 38px; line-height: 1; font-weight: 700; color: var(--ink-faint); }
.alert.hot .alert-value { color: var(--signal); }
.alert-foot { display: flex; align-items: center; justify-content: space-between; }
.alert-label { font-size: 14px; color: var(--ink); }
.alert-go { font-family: var(--font-mono); font-size: 11px; color: var(--ink-faint); }
.alert.hot .alert-go { color: var(--signal-ink); }

/* —— 快捷入口 —— */
.nav-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.nav-tile {
  display: flex; flex-direction: column; gap: 5px; align-items: flex-start; cursor: pointer;
  padding: 16px; border-radius: var(--radius-sm); text-align: left;
  background: var(--surface); border: 1px solid var(--line); color: var(--ink);
  font-size: 14px; transition: all 0.15s ease;
}
.nav-tile span { font-family: var(--font-mono); font-size: 9.5px; letter-spacing: 0.08em; color: var(--ink-faint); }
.nav-tile:hover { border-color: var(--signal); color: var(--signal); transform: translateY(-2px); }
.nav-tile:hover span { color: var(--signal); }

@media (max-width: 900px) {
  .kpi-row, .pending-row { grid-template-columns: 1fr; }
  .nav-row { grid-template-columns: repeat(2, 1fr); }
}
</style>
