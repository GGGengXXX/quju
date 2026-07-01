<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useAdminAuthStore } from '../../stores/adminAuth'

const adminAuth = useAdminAuthStore()
const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/admin/dashboard', label: '仪表盘', icon: 'Odometer' },
  { path: '/admin/users', label: '用户管理', icon: 'User' },
  { path: '/admin/merchants', label: '商家审核', icon: 'Shop' },
  { path: '/admin/activities', label: '活动管理', icon: 'Calendar' },
  { path: '/admin/teams', label: '小队管理', icon: 'UserFilled' },
  { path: '/admin/reports', label: '举报管理', icon: 'Warning' },
]

function logout() {
  adminAuth.logout()
  router.push('/admin/login')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="200px" class="aside">
      <div class="brand">趣聚管理后台</div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
      <div class="bottom">
        <span class="user">{{ adminAuth.username }}</span>
        <el-button text size="small" @click="logout">退出</el-button>
      </div>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.admin-layout { height: 100vh; }
.aside { background: #304156; display: flex; flex-direction: column; overflow: hidden; }
.brand { color: #fff; font-size: 16px; font-weight: 700; padding: 20px 16px; text-align: center; }
.menu { border-right: none; background: #304156; flex: 1; }
.menu .el-menu-item { color: #bfcbd9; }
.menu .el-menu-item:hover, .menu .el-menu-item.is-active { background: #263445; color: #409eff; }
.bottom { padding: 12px 16px; color: #bfcbd9; display: flex; align-items: center; justify-content: space-between; }
.bottom .el-button { color: #bfcbd9; }
.user { font-size: 13px; }
.main { background: #f0f2f5; overflow-y: auto; }
</style>
