<script setup lang="ts">
import { ref, onMounted } from 'vue'

const health = ref<Record<string, unknown> | null>(null)
const err = ref<string>('')

onMounted(async () => {
  try {
    const r = await fetch('/v1/health')
    health.value = await r.json()
  } catch (e) {
    err.value = String(e)
  }
})
</script>

<template>
  <main class="wrap">
    <h1>趣聚 QuJu</h1>
    <p class="sub">部署冒烟测试 · 前端(nginx) → <code>/v1</code> 反代 → 后端(Spring Boot) → MySQL</p>
    <pre v-if="health" class="ok">{{ JSON.stringify(health, null, 2) }}</pre>
    <p v-else-if="err" class="bad">后端未就绪：{{ err }}</p>
    <p v-else>加载中…</p>
  </main>
</template>

<style scoped>
.wrap { font-family: system-ui, -apple-system, sans-serif; max-width: 680px; margin: 60px auto; padding: 24px; }
h1 { margin: 0 0 8px; }
.sub { color: #555; }
code { background: #f2f2f2; padding: 1px 5px; border-radius: 4px; }
.ok { background: #0b1021; color: #6ee7b7; padding: 16px; border-radius: 8px; overflow: auto; }
.bad { color: #c0392b; }
</style>
