<template>
  <div>
    <div class="page-header" style="display:flex; justify-content:space-between; align-items:flex-end;">
      <div>
        <h2>检索日志</h2>
        <div class="desc">每次 RAG 检索的完整明细，可按关键词、角色、来源、时间筛选</div>
      </div>
      <el-button @click="onClear" type="danger" plain>清空全部日志</el-button>
    </div>

    <div class="glass-card filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索 query 关键词..."
        clearable
        style="width: 240px;"
        @keyup.enter="reload"
      />
      <el-select v-model="filters.role_filter" placeholder="角色" clearable style="width: 140px;" @change="reload">
        <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
      </el-select>
      <el-select v-model="filters.source" placeholder="来源" clearable style="width: 140px;" @change="reload">
        <el-option label="API" value="api" />
        <el-option label="Admin" value="admin" />
        <el-option label="Agent" value="agent" />
      </el-select>
      <el-input v-model="filters.game_id" placeholder="game_id" clearable style="width: 160px;" @keyup.enter="reload" />
      <el-select v-model="filters.since_hours" placeholder="时间窗口" clearable style="width: 140px;" @change="reload">
        <el-option label="最近 1 小时" :value="1" />
        <el-option label="最近 24 小时" :value="24" />
        <el-option label="最近 7 天" :value="168" />
        <el-option label="最近 30 天" :value="720" />
      </el-select>
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <div v-loading="loading" class="glass-card" style="margin-top: 16px;">
      <el-table :data="items" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-body">
              <div v-if="!row.results.length" class="muted">该次检索没有命中任何结果</div>
              <div v-for="(r, idx) in row.results" :key="idx" class="hit-item">
                <div class="hit-line">
                  <span class="rank">#{{ idx + 1 }}</span>
                  <span class="src">📄 {{ r.source }}</span>
                  <el-tag v-if="r.role" size="small" effect="dark">{{ r.role }}</el-tag>
                  <el-tag v-if="r.doc_type" size="small" effect="dark" type="warning">{{ r.doc_type }}</el-tag>
                  <span class="score">score {{ Number(r.score || 0).toFixed(3) }}</span>
                </div>
                <div class="snippet">{{ r.snippet }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <span class="muted">{{ row.ts }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="query" label="Query" show-overflow-tooltip min-width="280">
          <template #default="{ row }">
            <strong>{{ row.query }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.role_filter" size="small" effect="dark">{{ row.role_filter }}</el-tag>
            <span v-else class="muted">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceType(row.source)" effect="plain">{{ row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="top_k" width="80" align="right">
          <template #default="{ row }">{{ row.top_k }}</template>
        </el-table-column>
        <el-table-column label="命中" width="80" align="right">
          <template #default="{ row }">
            <span class="tag-soft">{{ row.hit_count }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" width="110" align="right">
          <template #default="{ row }">
            <span :class="durationClass(row.duration_ms)">{{ row.duration_ms }}</span>
          </template>
        </el-table-column>
        <el-table-column label="game_id" width="120">
          <template #default="{ row }">
            <span class="muted">{{ row.game_id || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="reload"
        @size-change="reload"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRetrievalLogs, clearRetrievalLogs } from '../api'

const loading = ref(false)
const items = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ keyword: '', role_filter: '', source: '', game_id: '', since_hours: null })

const roleOptions = ['WEREWOLF', 'SEER', 'WITCH', 'HUNTER', 'GUARD', 'VILLAGER']

async function reload() {
  loading.value = true
  try {
    const data = await getRetrievalLogs({
      keyword: filters.keyword || null,
      role_filter: filters.role_filter || null,
      source: filters.source || null,
      game_id: filters.game_id || null,
      since_hours: filters.since_hours || null,
      page: page.value,
      page_size: pageSize.value,
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function onClear() {
  try {
    await ElMessageBox.confirm('确定要清空所有检索日志吗？此操作不可恢复', '清空检索日志', { type: 'warning' })
  } catch { return }
  const r = await clearRetrievalLogs()
  ElMessage.success(`已清空 ${r.cleared} 条日志`)
  page.value = 1
  reload()
}

function sourceType(s) {
  return { admin: 'primary', agent: 'success', api: 'info' }[s] || 'info'
}

function durationClass(ms) {
  if (ms < 100) return 'fast'
  if (ms < 500) return 'normal'
  return 'slow'
}

onMounted(reload)
</script>

<style lang="scss" scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.muted { color: var(--text-secondary); }

.fast { color: #10b981; font-weight: 600; }
.normal { color: #f59e0b; font-weight: 600; }
.slow { color: #ef4444; font-weight: 600; }

.expand-body {
  padding: 8px 32px 12px;
  background: rgba(15, 23, 42, 0.4);
  border-radius: 6px;
}
.hit-item { padding: 8px 0; border-bottom: 1px dashed var(--border-soft); }
.hit-item:last-child { border-bottom: none; }
.hit-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  margin-bottom: 4px;
  .rank { color: #a5b4fc; font-weight: 700; }
  .src { font-weight: 600; }
  .score {
    margin-left: auto;
    font-family: monospace;
    color: #06b6d4;
    font-weight: 600;
  }
}
.snippet {
  font-size: 12px;
  color: #cbd5e1;
  line-height: 1.6;
  padding-left: 28px;
}
</style>
