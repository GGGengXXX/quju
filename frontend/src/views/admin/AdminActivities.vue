<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminActivityListVO, type AuditLogVO, type PageResult } from '../../api/admin'
import {
  activityAuditTypeLabel,
  activityAuditResultLabel,
  activityAuditResultTagType,
} from '../../constants/enums'

const loading = ref(false)
const list = ref<AdminActivityListVO[]>([])
const total = ref(0)
const tab = ref('all')
const query = reactive({ status: '', keyword: '', page: 1, size: 10 })

const reviewVisible = ref(false)
const reviewForm = reactive({ result: '' as 'PASSED' | 'REJECTED' | 'NEEDS_REVISION', reason: '' })
const reviewTargetId = ref(0)
const auditLogs = ref<AuditLogVO[]>([])
const auditLogsLoading = ref(false)

const reasonVisible = ref(false)
const reasonForm = reactive({ reason: '' })
const reasonTargetId = ref(0)

async function load() {
  loading.value = true
  try {
    let res: PageResult<AdminActivityListVO>
    if (tab.value === 'pending') {
      res = await adminApi.getPendingReviewActivities({ page: query.page, size: query.size })
    } else {
      res = await adminApi.getActivities(query)
    }
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function switchTab(t: string) {
  tab.value = t
  query.page = 1
  load()
}

async function openReview(id: number) {
  reviewTargetId.value = id
  reviewForm.result = 'PASSED'
  reviewForm.reason = ''
  reviewVisible.value = true
  // 拉取审核流水时间线，帮助审核员了解 AI 为何转人工
  auditLogs.value = []
  auditLogsLoading.value = true
  try {
    auditLogs.value = await adminApi.getActivityAuditLogs(id)
  } catch {
    auditLogs.value = []
  } finally {
    auditLogsLoading.value = false
  }
}

function formatAuditTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

async function submitReview() {
  if (reviewForm.result !== 'PASSED' && !reviewForm.reason.trim()) {
    ElMessage.warning('驳回/修改需填写原因')
    return
  }
  await adminApi.reviewActivity(reviewTargetId.value, {
    result: reviewForm.result,
    reason: reviewForm.reason || undefined,
  })
  ElMessage.success('审核完成')
  reviewVisible.value = false
  load()
}

function openTakedown(id: number) {
  reasonTargetId.value = id
  reasonForm.reason = ''
  reasonVisible.value = true
}

async function submitTakedown() {
  if (!reasonForm.reason.trim()) {
    ElMessage.warning('请填写下架原因')
    return
  }
  await adminApi.takedownActivity(reasonTargetId.value, { reason: reasonForm.reason })
  ElMessage.success('已下架')
  reasonVisible.value = false
  load()
}

async function restore(id: number) {
  await ElMessageBox.confirm('确认恢复该活动？', '提示')
  await adminApi.restoreActivity(id)
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
    <h3>活动管理</h3>

    <el-radio-group v-model="tab" style="margin-bottom: 16px" @change="switchTab">
      <el-radio-button value="all">全部</el-radio-button>
      <el-radio-button value="pending">待审核</el-radio-button>
    </el-radio-group>

    <el-form v-if="tab === 'all'" inline style="margin-bottom: 16px">
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="搜索活动名称" clearable @clear="load" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已下架" value="TAKEN_DOWN" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="活动名称" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column label="创建者" width="120">
        <template #default="{ row }">
          {{ row.creatorNickname || row.creatorId }}
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING_REVIEW'" text size="small" type="primary" @click="openReview(row.id)">审核</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" text size="small" type="danger" @click="openTakedown(row.id)">下架</el-button>
          <el-button v-if="row.status === 'TAKEN_DOWN'" text size="small" type="success" @click="restore(row.id)">恢复</el-button>
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

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewVisible" title="审核活动" width="480px">
      <!-- 审核流水时间线：突出 AI 判定与理由，辅助人工决策 -->
      <div class="audit-logs" v-loading="auditLogsLoading">
        <div class="audit-logs__title">审核流水</div>
        <el-empty v-if="!auditLogsLoading && auditLogs.length === 0" description="暂无审核记录" :image-size="48" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="log in auditLogs"
            :key="log.id"
            :timestamp="formatAuditTime(log.createdAt)"
            placement="top"
          >
            <el-tag size="small" effect="plain">{{ activityAuditTypeLabel(log.auditType) }}</el-tag>
            <el-tag size="small" :type="activityAuditResultTagType(log.result)" style="margin-left: 6px">
              {{ activityAuditResultLabel(log.result) }}
            </el-tag>
            <div v-if="log.reason" class="audit-logs__reason">{{ log.reason }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
      <el-divider />
      <el-form label-width="80px">
        <el-form-item label="结果">
          <el-radio-group v-model="reviewForm.result">
            <el-radio value="PASSED">通过</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
            <el-radio value="NEEDS_REVISION">需修改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="reviewForm.result !== 'PASSED'" label="原因">
          <el-input v-model="reviewForm.reason" type="textarea" :rows="3" placeholder="请输入原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>

    <!-- 下架原因弹窗 -->
    <el-dialog v-model="reasonVisible" title="下架活动" width="400px">
      <el-input v-model="reasonForm.reason" type="textarea" :rows="3" placeholder="请输入下架原因" />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="danger" @click="submitTakedown">确认下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
h3 { margin-bottom: 16px; }
.audit-logs { max-height: 220px; overflow-y: auto; }
.audit-logs__title { font-weight: 600; margin-bottom: 8px; }
.audit-logs__reason { margin-top: 4px; color: #606266; font-size: 13px; line-height: 1.5; }
</style>
