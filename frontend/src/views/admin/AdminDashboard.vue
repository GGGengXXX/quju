<script setup lang="ts">
import { useAdminAuthStore } from '../../stores/adminAuth'

const adminAuth = useAdminAuthStore()
</script>

<template>
  <div class="dashboard">
    <h2>管理后台</h2>
    <p>欢迎，{{ adminAuth.username }}</p>
    <el-row :gutter="16" style="margin-top: 24px">
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>用户管理</template>
          <router-link to="/admin/users">查看用户列表</router-link>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>商家审核</template>
          <router-link to="/admin/merchants">查看待审核</router-link>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>活动管理</template>
          <router-link to="/admin/activities">查看活动</router-link>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <template #header>举报管理</template>
          <router-link to="/admin/reports">查看举报</router-link>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard { padding: 16px; }
h2 { margin-bottom: 8px; }
</style>
