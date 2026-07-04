<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type ReportVO, type ReportHandleReq, type PageResult } from '../../api/admin'

const loading = ref(false)
const list = ref<ReportVO[]>([])
const total = ref(0)
const query = reactive({ status: '', page: 1, size: 10 })

const handleVisible = ref(false)
const submitting = ref(false)
const handleTargetId = ref<number | null>(null)
const handleForm = reactive<{ action: ReportHandleReq['action']; reason: string }>({
  action: 'RESOLVE',
  reason: '',
})
const needReason = computed(() => handleForm.action === 'TAKEDOWN')

async function load() {
  loading.value = true
  try {
    const res = await adminApi.getReports(query) as PageResult<ReportVO>
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  query.page = page
  load()
}

function openHandle(row: ReportVO) {
  handleTargetId.value = row.id
  handleForm.action = 'RESOLVE'
  handleForm.reason = ''
  handleVisible.value = true
}

async function submitHandle() {
  if (handleTargetId.value == null) return
  if (needReason.value && !handleForm.reason.trim()) {
    ElMessage.warning('下架/停用目标需填写原因')
    return
  }
  submitting.value = true
  try {
    await adminApi.handleReport(handleTargetId.value, {
      action: handleForm.action,
      reason: handleForm.reason.trim() || undefined,
    })
    ElMessage.success('处理完成')
    handleVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <h3>举报管理</h3>

    <el-form inline style="margin-bottom: 16px">
      <el-form-item>
        <el-select v-model="query.status" placeholder="状态" clearable @change="load">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已处理" value="HANDLED" />
          <el-option label="已驳回" value="DISMISSED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="reporterId" label="举报人ID" width="90" />
      <el-table-column prop="targetType" label="目标类型" width="100" />
      <el-table-column prop="targetId" label="目标ID" width="80" />
      <el-table-column prop="reason" label="原因" />
      <el-table-column prop="detail" label="详情" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column prop="createdAt" label="举报时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" text size="small" type="primary" @click="openHandle(row)">处理</el-button>
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

    <el-dialog v-model="handleVisible" title="处理举报" width="440px">
      <el-form label-width="80px">
        <el-form-item label="处理动作">
          <el-select v-model="handleForm.action" style="width: 100%">
            <el-option label="驳回（不属实）" value="DISMISS" />
            <el-option label="标记已处理" value="RESOLVE" />
            <el-option label="处理并下架/停用目标" value="TAKEDOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" :required="needReason">
          <el-input
            v-model="handleForm.reason"
            type="textarea"
            :rows="3"
            :placeholder="needReason ? '下架/停用目标的原因（必填）' : '可选'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
h3 { margin-bottom: 16px; }
</style>
