<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type MerchantAppVO, type PageResult } from '../../api/admin'

const loading = ref(false)
const list = ref<MerchantAppVO[]>([])
const total = ref(0)
const query = reactive({ status: '', page: 1, size: 10 })

const reviewVisible = ref(false)
const reviewForm = reactive({ action: '' as 'APPROVE' | 'REJECT', reason: '' })
const reviewTargetId = ref(0)

async function load() {
  loading.value = true
  try {
    const res = await adminApi.getMerchantApplications(query) as PageResult<MerchantAppVO>
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openReview(id: number, action: 'APPROVE' | 'REJECT') {
  reviewTargetId.value = id
  reviewForm.action = action
  reviewForm.reason = ''
  if (action === 'APPROVE') {
    submitReview()
  } else {
    reviewVisible.value = true
  }
}

async function submitReview() {
  if (reviewForm.action === 'REJECT' && !reviewForm.reason.trim()) {
    ElMessage.warning('驳回需填写原因')
    return
  }
  await adminApi.reviewMerchant(reviewTargetId.value, {
    action: reviewForm.action,
    reason: reviewForm.reason || undefined,
  })
  ElMessage.success(reviewForm.action === 'APPROVE' ? '已通过' : '已驳回')
  reviewVisible.value = false
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
    <h3>商家审核</h3>

    <el-form inline style="margin-bottom: 16px">
      <el-form-item>
        <el-select v-model="query.status" placeholder="审核状态" clearable @change="load">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="merchantName" label="商家名称" />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column prop="auditStatus" label="状态" width="100" />
      <el-table-column prop="auditReason" label="审核原因" />
      <el-table-column prop="createdAt" label="申请时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.auditStatus === 'PENDING'">
            <el-button text size="small" type="success" @click="openReview(row.id, 'APPROVE')">通过</el-button>
            <el-button text size="small" type="danger" @click="openReview(row.id, 'REJECT')">驳回</el-button>
          </template>
          <span v-else style="color: #999">已处理</span>
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

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="reviewVisible" title="驳回原因" width="400px">
      <el-input v-model="reviewForm.reason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReview">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 16px; }
h3 { margin-bottom: 16px; }
</style>
