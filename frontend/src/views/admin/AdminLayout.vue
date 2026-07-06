<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAdminAuthStore } from '../../stores/adminAuth'

const adminAuth = useAdminAuthStore()
const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/admin/dashboard', label: '态势总览', code: 'OVERVIEW' },
  { path: '/admin/users', label: '用户管理', code: 'USERS' },
  { path: '/admin/merchants', label: '商家审核', code: 'MERCHANTS' },
  { path: '/admin/activities', label: '活动管理', code: 'ACTIVITIES' },
  { path: '/admin/teams', label: '小队管理', code: 'TEAMS' },
  { path: '/admin/reports', label: '举报管理', code: 'REPORTS' },
]

function logout() {
  adminAuth.logout()
  router.push('/admin/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="rail">
      <div class="rail-brand">
        <span class="pin" />
        <div class="brand-text">
          <strong>趣聚 · 控制台</strong>
          <span class="brand-sub">ADMIN CONSOLE</span>
        </div>
      </div>

      <nav class="rail-nav">
        <router-link
          v-for="(item, i) in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
          :style="{ '--i': i }"
        >
          <span class="nav-idx">{{ String(i).padStart(2, '0') }}</span>
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-code">{{ item.code }}</span>
        </router-link>
      </nav>

      <div class="rail-foot">
        <div class="who">
          <span class="who-avatar">{{ (adminAuth.username || 'A').slice(0, 1).toUpperCase() }}</span>
          <div class="who-text">
            <strong>{{ adminAuth.username || 'admin' }}</strong>
            <span>在线 · ONLINE</span>
          </div>
        </div>
        <button class="logout" @click="logout">退出</button>
      </div>
    </aside>

    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  height: 100vh;
  --a-teal: #157a6e;
  --a-amber: #c8860d;
}

/* —— 导航轨（浅色） —— */
.rail {
  flex: 0 0 238px;
  background: var(--surface);
  color: var(--ink);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line);
  background-image: radial-gradient(rgba(27,28,24,0.035) 1px, transparent 1px);
  background-size: 22px 22px;
}
.rail-brand { display: flex; align-items: center; gap: 11px; padding: 22px 20px 20px; border-bottom: 1px solid var(--line); }
.pin {
  width: 18px; height: 18px; flex: 0 0 auto;
  border-radius: 50% 50% 50% 0; background: var(--signal);
  transform: rotate(-45deg); box-shadow: inset -2px -2px 0 rgba(0,0,0,0.14);
  animation: pinDrop 0.5s cubic-bezier(0.2, 1.3, 0.4, 1) both;
}
@keyframes pinDrop { from { opacity: 0; transform: rotate(-45deg) translate(6px, -6px); } to { opacity: 1; transform: rotate(-45deg) translate(0, 0); } }
.brand-text { display: flex; flex-direction: column; line-height: 1.25; }
.brand-text strong { font-size: 14.5px; color: var(--ink); letter-spacing: 0.01em; }
.brand-sub { font-family: var(--font-mono); font-size: 10px; letter-spacing: 0.14em; color: var(--ink-faint); }

.rail-nav { flex: 1; padding: 14px 12px; display: flex; flex-direction: column; gap: 3px; overflow-y: auto; }
.nav-item {
  position: relative; display: flex; align-items: center; gap: 11px;
  padding: 11px 12px; border-radius: 10px; text-decoration: none;
  color: var(--ink-soft); overflow: hidden;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
  animation: navRise 0.42s cubic-bezier(0.2, 0.7, 0.3, 1) both;
  animation-delay: calc(var(--i) * 45ms + 120ms);
}
@keyframes navRise { from { opacity: 0; transform: translateX(-10px); } to { opacity: 1; transform: none; } }
.nav-item:hover { background: var(--surface-2); color: var(--ink); }
.nav-item:hover .nav-label { transform: translateX(2px); }
.nav-item.active { background: var(--signal-wash); color: var(--signal-ink); }
.nav-item.active::before {
  content: ''; position: absolute; left: 0; top: 7px; bottom: 7px; width: 3px;
  background: var(--signal); border-radius: 0 3px 3px 0;
  animation: barGrow 0.28s ease both;
}
@keyframes barGrow { from { transform: scaleY(0); } to { transform: scaleY(1); } }
.nav-idx { font-family: var(--font-mono); font-size: 11px; color: var(--ink-faint); transition: color 0.18s ease; }
.nav-item.active .nav-idx { color: var(--signal); }
.nav-label { flex: 1; font-size: 14px; transition: transform 0.18s ease; }
.nav-code { font-family: var(--font-mono); font-size: 9px; letter-spacing: 0.08em; color: var(--ink-faint); opacity: 0; transform: translateX(6px); transition: opacity 0.18s ease, transform 0.18s ease; }
.nav-item:hover .nav-code, .nav-item.active .nav-code { opacity: 1; transform: none; }

.rail-foot { padding: 14px; border-top: 1px solid var(--line); display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.who { display: flex; align-items: center; gap: 10px; min-width: 0; }
.who-avatar {
  width: 34px; height: 34px; flex: 0 0 auto; border-radius: 9px;
  background: var(--signal); color: #fff; display: flex; align-items: center; justify-content: center;
  font-family: var(--font-mono); font-weight: 700; font-size: 15px;
}
.who-text { display: flex; flex-direction: column; line-height: 1.3; min-width: 0; }
.who-text strong { font-size: 13px; color: var(--ink); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.who-text span { font-family: var(--font-mono); font-size: 9px; letter-spacing: 0.08em; color: var(--a-teal); }
.logout {
  flex: 0 0 auto; background: none; border: 1px solid var(--line-strong); color: var(--ink-soft);
  border-radius: 8px; padding: 6px 11px; font-size: 12px; cursor: pointer; transition: all 0.15s ease;
}
.logout:hover { border-color: var(--signal); color: var(--signal); }

.main { flex: 1; background: var(--paper); overflow-y: auto; }

@media (max-width: 820px) {
  .rail { flex-basis: 64px; }
  .brand-text, .nav-label, .nav-code, .who-text { display: none; }
  .rail-brand { justify-content: center; padding: 20px 0; }
  .nav-item { justify-content: center; padding: 12px 0; }
  .nav-item.active::before { left: 0; }
  .rail-foot { flex-direction: column; }
}
@media (prefers-reduced-motion: reduce) {
  .pin, .nav-item, .nav-item.active::before { animation: none; }
}
</style>

<!-- 全局：统一后台所有子页的排版与表格风格（非 scoped，仅作用于 .admin-shell 内） -->
<style>
.admin-shell .main .page { padding: 26px 30px 40px; max-width: 1180px; }

.admin-shell .page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.admin-shell .page-eyebrow { font-family: var(--font-mono); font-size: 11px; letter-spacing: 0.1em; text-transform: uppercase; color: var(--ink-faint); }
.admin-shell .page-head h3 { margin: 5px 0 0; font-size: 24px; color: var(--ink); letter-spacing: 0.01em; }
.admin-shell .page-count {
  flex: 0 0 auto; font-family: var(--font-mono); font-size: 12px; color: var(--ink-soft);
  padding: 6px 12px; border: 1px solid var(--line); border-radius: 20px; background: var(--surface);
}
.admin-shell .page-count em { font-style: normal; color: var(--signal); font-weight: 700; }

/* 过滤栏 */
.admin-shell .filter-bar {
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px;
  padding: 14px 16px; margin-bottom: 18px;
  background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius);
  box-shadow: var(--shadow);
}
.admin-shell .filter-bar .el-form-item { margin: 0 !important; }

/* 表格：干净的 paper 面板 */
.admin-shell .el-table {
  border-radius: var(--radius); overflow: hidden;
  border: 1px solid var(--line);
  --el-table-border-color: var(--line);
  --el-table-header-bg-color: var(--surface-2);
  --el-table-header-text-color: var(--ink-soft);
  --el-table-row-hover-bg-color: var(--signal-wash);
  --el-table-text-color: var(--ink);
  --el-table-tr-bg-color: var(--surface);
  box-shadow: var(--shadow);
}
.admin-shell .el-table th.el-table__cell { font-weight: 600; font-size: 12.5px; letter-spacing: 0.02em; }
.admin-shell .el-table .cell { line-height: 1.5; }
.admin-shell .el-table__cell:first-child .cell { font-family: var(--font-mono); }

/* 分页 */
.admin-shell .el-pagination { margin-top: 18px; justify-content: flex-end; }

/* 表内“详情/编辑”里的小标题 */
.admin-shell .section-title { margin: 20px 0 10px; font-size: 13px; font-weight: 600; color: var(--ink); font-family: var(--font-mono); letter-spacing: 0.02em; }

/* 页面切换动画 */
.admin-shell .page-enter-active { transition: opacity 0.32s ease, transform 0.32s cubic-bezier(0.2, 0.7, 0.3, 1); }
.admin-shell .page-leave-active { transition: opacity 0.16s ease; }
.admin-shell .page-enter-from { opacity: 0; transform: translateY(12px); }
.admin-shell .page-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .admin-shell .page-enter-active, .admin-shell .page-leave-active { transition: none; }
  .admin-shell .page-enter-from { transform: none; }
}
</style>
