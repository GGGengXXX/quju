<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reportApi, type ReportTargetType } from '../api/report'

const props = defineProps<{
  modelValue: boolean
  targetType: ReportTargetType
  targetId: number
  targetName?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const REASON_OPTIONS = ['垃圾广告', '色情低俗', '违法违规', '人身攻击/骚扰', '虚假信息/欺诈', '其他']

const submitting = ref(false)
const form = reactive({ reason: '', detail: '' })

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      form.reason = ''
      form.detail = ''
    }
  }
)

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  if (!form.reason) {
    ElMessage.warning('请选择举报原因')
    return
  }
  submitting.value = true
  try {
    await reportApi.create({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: form.reason,
      detail: form.detail.trim() || undefined,
    })
    ElMessage.success('举报已提交，感谢反馈')
    close()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="举报"
    width="440px"
    @update:model-value="close"
  >
    <p v-if="targetName" class="report-target">举报对象：{{ targetName }}</p>
    <el-form label-width="72px">
      <el-form-item label="原因" required>
        <el-select v-model="form.reason" placeholder="请选择举报原因" style="width: 100%">
          <el-option v-for="r in REASON_OPTIONS" :key="r" :label="r" :value="r" />
        </el-select>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="可选，补充具体情况"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.report-target {
  margin: 0 0 12px;
  color: #4d6580;
  font-size: 13px;
}
</style>
