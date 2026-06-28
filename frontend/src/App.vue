<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
const auth = useAuthStore()
const router = useRouter()
function logout() { auth.logout(); router.push('/login') }
</script>

<template>
  <el-container>
    <el-header class="hd">
      <span class="logo" @click="router.push('/')">趣聚 QuJu</span>
      <span class="spacer" />
      <template v-if="auth.token">
        <el-button text @click="router.push('/teams')">小队</el-button>
        <el-button text @click="router.push('/profile')">我的</el-button>
        <el-button text @click="logout">退出</el-button>
      </template>
    </el-header>
    <el-main><router-view /></el-main>
  </el-container>
</template>

<style>
body { margin: 0; }
.hd { display: flex; align-items: center; background: #409eff; color: #fff; }
.logo { font-weight: 700; font-size: 18px; cursor: pointer; }
.spacer { flex: 1; }
.hd .el-button { color: #fff; }
</style>
