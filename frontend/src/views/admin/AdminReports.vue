<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi, type ReportVO, type PageResult } from '../../api/admin'

const loading = ref(false)
const list = ref<ReportVO[]>([])
const total = ref(0)
const query = reactive({ status: '', page: 1, size: 10 })

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
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      @current-change="handlePageChange"
    />
  </div>
</template>

<style scoped>
.page { padding: 16px; }
h3 { margin-bottom: 16px; }
</style>
