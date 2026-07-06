<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminUserListVO, type AdminUserDetailVO, type PageResult } from '../../api/admin'

const loading = ref(false)
const list = ref<AdminUserListVO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', userType: '', status: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detail = ref<AdminUserDetailVO | null>(null)
const detailLoading = ref(false)

const banVisible = ref(false)
const banForm = reactive({ reason: '', banUntil: '' })
const banTargetId = ref(0)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.getUsers(query) as PageResult<AdminUserListVO>
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(id: number) {
  detailLoading.value = true
  detailVisible.value = true
  try {
    detail.value = await adminApi.getUserDetail(id)
  } finally {
    detailLoading.value = false
  }
}

function openBan(id: number) {
  banTargetId.value = id
  banForm.reason = ''
  banForm.banUntil = ''
  banVisible.value = true
}

async function submitBan() {
  if (!banForm.reason.trim()) {
    ElMessage.warning('请填写封禁原因')
    return
  }
  await adminApi.banUser(banTargetId.value, {
    reason: banForm.reason,
    banUntil: banForm.banUntil || null,
  })
  ElMessage.success('已封禁')
  banVisible.value = false
  load()
}

async function unban(id: number) {
  await ElMessageBox.confirm('确认解封该用户？', '提示')
  await adminApi.unbanUser(id)
  ElMessage.success('已解封')
  load()
}

function handlePageChange(page: number) {
  query.page = page
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <p class="page-eyebrow">CONSOLE · 01 / USERS</p>
        <h3>用户管理</h3>
      </div>
      <span class="page-count">共 <em>{{ total }}</em> 名用户</span>
    </div>

    <el-form inline class="filter-bar">
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="搜索昵称/邮箱" clearable @clear="load" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="query.userType" placeholder="用户类型" clearable @change="load">
          <el-option label="个人" value="INDIVIDUAL" />
          <el-option label="商家" value="MERCHANT" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已封禁" value="BANNED" />
          <el-option label="待激活" value="PENDING_ACTIVATION" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="userType" label="类型" width="80" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="createdAt" label="注册时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="showDetail(row.id)">详情</el-button>
          <el-button v-if="row.status !== 'BANNED'" text size="small" type="danger" @click="openBan(row.id)">封禁</el-button>
          <el-button v-else text size="small" type="success" @click="unban(row.id)">解封</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      @current-change="handlePageChange"
    />

    <!-- 用户详情 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="640px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.email }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detail.nickname }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.userType }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detail.gender || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生日">{{ detail.birthday || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签名">{{ detail.signature || '-' }}</el-descriptions-item>
          <el-descriptions-item label="信誉">{{ detail.reputation ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ detail.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail">
          <h4 class="section-title">发布的活动（{{ detail.activities?.length ?? 0 }}）</h4>
          <el-table v-if="detail.activities?.length" :data="detail.activities" size="small" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="活动名称" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="startTime" label="开始时间" width="170" />
          </el-table>
          <el-empty v-else description="暂无活动" :image-size="60" />

          <h4 class="section-title">创建的小队（{{ detail.teams?.length ?? 0 }}）</h4>
          <el-table v-if="detail.teams?.length" :data="detail.teams" size="small" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="小队名称" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="memberCount" label="成员数" width="80" />
          </el-table>
          <el-empty v-else description="暂无小队" :image-size="60" />
        </template>
      </div>
    </el-dialog>

    <!-- 封禁弹窗 -->
    <el-dialog v-model="banVisible" title="封禁用户" width="420px">
      <el-form label-width="80px">
        <el-form-item label="原因" required>
          <el-input v-model="banForm.reason" type="textarea" :rows="3" placeholder="请输入封禁原因" />
        </el-form-item>
        <el-form-item label="封禁至">
          <el-date-picker v-model="banForm.banUntil" type="datetime" placeholder="留空为永久封禁" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banVisible = false">取消</el-button>
        <el-button type="danger" @click="submitBan">确认封禁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
h3 { margin-bottom: 16px; }
.section-title { margin: 18px 0 8px; font-size: 14px; font-weight: 600; }
</style>
