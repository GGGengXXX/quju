<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminTeamListVO, type PageResult } from '../../api/admin'
import http from '../../api/http'

const loading = ref(false)
const list = ref<AdminTeamListVO[]>([])
const total = ref(0)
const query = reactive({ keyword: '', status: '', page: 1, size: 10 })

const reasonVisible = ref(false)
const reasonForm = reactive({ reason: '' })
const reasonTargetId = ref(0)

// 小队详情
const teamDetailVisible = ref(false)
const teamDetail = ref<any>(null)
const teamMembers = ref<any[]>([])
const teamDetailLoading = ref(false)

async function showTeamDetail(teamId: number) {
  teamDetailVisible.value = true
  teamDetailLoading.value = true
  try {
    const [detail, members] = await Promise.all([
      http.get<any, any>(`/teams/${teamId}`),
      http.get<any, any[]>(`/teams/${teamId}/members`),
    ])
    teamDetail.value = detail
    teamMembers.value = members
  } catch { teamDetail.value = null; teamMembers.value = [] }
  finally { teamDetailLoading.value = false }
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.getTeams(query) as PageResult<AdminTeamListVO>
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openSuspend(id: number) {
  reasonTargetId.value = id
  reasonForm.reason = ''
  reasonVisible.value = true
}

async function submitSuspend() {
  if (!reasonForm.reason.trim()) {
    ElMessage.warning('请填写停用原因')
    return
  }
  await adminApi.suspendTeam(reasonTargetId.value, { reason: reasonForm.reason })
  ElMessage.success('已停用')
  reasonVisible.value = false
  load()
}

async function restore(id: number) {
  await ElMessageBox.confirm('确认恢复该小队？', '提示')
  await adminApi.restoreTeam(id)
  ElMessage.success('已恢复')
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
    <h3>小队管理</h3>

    <el-form inline style="margin-bottom: 16px">
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="搜索小队名称" clearable @clear="load" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option label="活跃" value="ACTIVE" />
          <el-option label="已停用" value="SUSPENDED" />
          <el-option label="已解散" value="DISSOLVED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="小队名称" />
      <el-table-column prop="ownerId" label="队长ID" width="80" />
      <el-table-column prop="memberCount" label="成员数" width="80" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="showTeamDetail(row.id)">详情</el-button>
          <el-button v-if="row.status === 'ACTIVE'" text size="small" type="danger" @click="openSuspend(row.id)">停用</el-button>
          <el-button v-if="row.status === 'SUSPENDED'" text size="small" type="success" @click="restore(row.id)">恢复</el-button>
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

    <!-- 停用原因弹窗 -->
    <el-dialog v-model="reasonVisible" title="停用小队" width="400px">
      <el-input v-model="reasonForm.reason" type="textarea" :rows="3" placeholder="请输入停用原因" />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="danger" @click="submitSuspend">确认停用</el-button>
      </template>
    </el-dialog>

    <!-- 小队详情弹窗 -->
    <el-dialog v-model="teamDetailVisible" title="小队详情" width="640px">
      <div v-loading="teamDetailLoading">
        <template v-if="teamDetail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="名称">{{ teamDetail.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ teamDetail.status }}</el-descriptions-item>
            <el-descriptions-item label="队长">{{ teamDetail.owner?.nickname || '未知' }}</el-descriptions-item>
            <el-descriptions-item label="成员数">{{ teamDetail.memberCount }} / {{ teamDetail.capacity }}</el-descriptions-item>
            <el-descriptions-item label="加入方式">{{ teamDetail.joinType === 'PUBLIC' ? '公开' : '需审核' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ teamDetail.createdAt }}</el-descriptions-item>
          </el-descriptions>
          <el-descriptions v-if="teamDetail.intro" :column="1" border style="margin-top: 12px">
            <el-descriptions-item label="简介">{{ teamDetail.intro }}</el-descriptions-item>
          </el-descriptions>

          <h4 style="margin-top: 16px">成员列表（{{ teamMembers.length }}）</h4>
          <el-table :data="teamMembers" size="small" border>
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="role" label="角色" width="100" />
            <el-table-column prop="points" label="积分" width="80" />
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
h3 { margin-bottom: 16px; }
</style>
