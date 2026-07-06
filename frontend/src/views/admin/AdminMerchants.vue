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
const reviewTargetRow = ref<MerchantAppVO | null>(null)

const detailVisible = ref(false)
const detailRow = ref<MerchantAppVO | null>(null)

function openDetail(row: MerchantAppVO) {
  detailRow.value = row
  detailVisible.value = true
}

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

function openReview(row: MerchantAppVO, action: 'APPROVE' | 'REJECT') {
  reviewTargetId.value = row.id
  reviewTargetRow.value = row
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
    <div class="page-head">
      <div>
        <p class="page-eyebrow">CONSOLE · 02 / MERCHANTS</p>
        <h3>商家审核</h3>
      </div>
      <span class="page-count">共 <em>{{ total }}</em> 份申请</span>
    </div>

    <el-form inline class="filter-bar">
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
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="merchantName" label="商家名称" />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column label="营业执照" width="100">
        <template #default="{ row }">
          <el-image v-if="row.licenseUrl" :src="row.licenseUrl" fit="cover" class="license-thumb"
            :preview-src-list="[row.licenseUrl]" preview-teleported />
          <span v-else style="color: #999">无</span>
        </template>
      </el-table-column>
      <el-table-column prop="auditStatus" label="状态" width="100" />
      <el-table-column prop="auditReason" label="审核原因" />
      <el-table-column prop="createdAt" label="申请时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openDetail(row)">查看</el-button>
          <template v-if="row.auditStatus === 'PENDING'">
            <el-button text size="small" type="success" @click="openReview(row, 'APPROVE')">通过</el-button>
            <el-button text size="small" type="danger" @click="openReview(row, 'REJECT')">驳回</el-button>
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

    <!-- 商家详情弹窗 -->
    <el-dialog v-model="detailVisible" title="商家信息" width="520px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="商家名称">{{ detailRow.merchantName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="商家昵称">{{ detailRow.nickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关注领域">{{ detailRow.focusFields || '—' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detailRow.userId }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">{{ detailRow.auditStatus }}</el-descriptions-item>
        <el-descriptions-item v-if="detailRow.auditReason" label="审核原因">{{ detailRow.auditReason }}</el-descriptions-item>
        <el-descriptions-item label="营业执照">
          <el-image v-if="detailRow.licenseUrl" :src="detailRow.licenseUrl" fit="contain" class="license-large"
            :preview-src-list="[detailRow.licenseUrl]" preview-teleported />
          <span v-else style="color: #999">未上传</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer v-if="detailRow?.auditStatus === 'PENDING'">
        <el-button type="success" @click="detailVisible = false; openReview(detailRow!, 'APPROVE')">通过</el-button>
        <el-button type="danger" @click="detailVisible = false; openReview(detailRow!, 'REJECT')">驳回</el-button>
      </template>
    </el-dialog>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="reviewVisible" title="驳回原因" width="440px">
      <div v-if="reviewTargetRow" class="review-merchant">
        <div class="review-info">
          <div><b>{{ reviewTargetRow.merchantName }}</b></div>
          <div class="muted">{{ reviewTargetRow.focusFields || '' }}</div>
        </div>
        <el-image v-if="reviewTargetRow.licenseUrl" :src="reviewTargetRow.licenseUrl" fit="cover" class="license-thumb"
          :preview-src-list="[reviewTargetRow.licenseUrl]" preview-teleported />
      </div>
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
.license-thumb { width: 56px; height: 56px; border-radius: 4px; border: 1px solid #eee; cursor: pointer; }
.license-large { max-width: 100%; max-height: 300px; border: 1px solid #eee; border-radius: 4px; }
.review-merchant { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.review-info { flex: 1; }
.muted { color: #999; font-size: 13px; }
</style>
