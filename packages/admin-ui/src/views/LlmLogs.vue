<template>
  <div>
    <div class="page-header">
      <h2>LLM 调用日志</h2>
      <div class="desc">所有产生过 LLM 调用的游戏，点击进入查看完整 prompt 与 response</div>
    </div>

    <div class="glass-card" v-loading="loading">
      <el-empty v-if="!loading && !items.length" description="还没有 LLM 日志，开一局狼人杀吧 🎲" />
      <el-table v-else :data="items" stripe @row-click="onRow" style="cursor:pointer;">
        <el-table-column label="Game ID" min-width="220">
          <template #default="{ row }">
            <strong>🎮 {{ row.game_id }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="日志文件" min-width="280">
          <template #default="{ row }">
            <code class="muted">{{ row.file }}</code>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="120" align="right">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="最近修改" width="180">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.modified) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="onRow(row)">查看 →</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listLlmGames } from '../api'

const router = useRouter()
const loading = ref(false)
const items = ref([])

async function load() {
  loading.value = true
  try {
    const data = await listLlmGames()
    items.value = data.items || []
  } finally {
    loading.value = false
  }
}

function onRow(row) {
  router.push(`/llm-logs/${row.game_id}`)
}

function formatSize(b) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

function formatTime(ms) {
  return new Date(ms).toLocaleString('zh-CN')
}

onMounted(load)
</script>

<style scoped>
.muted { color: var(--text-secondary); font-size: 12px; }
code {
  background: rgba(99, 102, 241, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}
</style>
