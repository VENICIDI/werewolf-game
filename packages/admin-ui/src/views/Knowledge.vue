<template>
  <div>
    <div class="page-header" style="display:flex; justify-content:space-between; align-items:flex-end;">
      <div>
        <h2>知识库管理</h2>
        <div class="desc">知识源文档列表与切片元数据（共 {{ docs.length }} 个文档）</div>
      </div>
      <el-button type="primary" :loading="rebuilding" @click="onRebuild">
        🔄 一键重建向量库
      </el-button>
    </div>

    <div class="glass-card" v-loading="loading">
      <el-input
        v-model="filter"
        placeholder="筛选文档名 / 角色 / 类型..."
        clearable
        style="max-width: 320px; margin-bottom: 16px;"
      >
        <template #prefix><span>🔎</span></template>
      </el-input>

      <el-table :data="filteredDocs" stripe @row-click="onRowClick" style="cursor:pointer;">
        <el-table-column prop="name" label="文档" min-width="240">
          <template #default="{ row }">
            <strong>📄 {{ row.name }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="chunk_count" label="切片数" width="100" align="right">
          <template #default="{ row }">
            <span class="tag-soft">{{ row.chunk_count }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="char_count" label="字符数" width="120" align="right">
          <template #default="{ row }">{{ row.char_count.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="角色" width="180">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles" :key="r" size="small" effect="dark" style="margin-right: 4px;">
              {{ roleLabel(r) }}
            </el-tag>
            <span v-if="!row.roles.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="160">
          <template #default="{ row }">
            <el-tag v-for="t in row.doc_types" :key="t" size="small" type="warning" effect="dark" style="margin-right: 4px;">
              {{ typeLabel(t) }}
            </el-tag>
            <span v-if="!row.doc_types.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="阶段" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="p in row.game_phases" :key="p" size="small" type="success" effect="dark" style="margin-right: 4px;">
              {{ phaseLabel(p) }}
            </el-tag>
            <span v-if="!row.game_phases.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="onRowClick(row)">查看 →</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments, rebuildKnowledge } from '../api'

const router = useRouter()
const loading = ref(false)
const rebuilding = ref(false)
const docs = ref([])
const filter = ref('')

const filteredDocs = computed(() => {
  const f = filter.value.trim().toLowerCase()
  if (!f) return docs.value
  return docs.value.filter(d =>
    d.name.toLowerCase().includes(f) ||
    d.roles.some(r => r.toLowerCase().includes(f)) ||
    d.doc_types.some(t => t.toLowerCase().includes(f))
  )
})

const ROLE_MAP = { WEREWOLF: '狼人', SEER: '预言家', WITCH: '女巫', HUNTER: '猎人', GUARD: '守卫', VILLAGER: '平民' }
const TYPE_MAP = { rules: '规则', strategy: '策略', speech: '发言', sheriff: '警长', case_study: '案例' }
const PHASE_MAP = { night: '夜间', vote: '投票', discussion: '讨论', sheriff_election: '警长竞选' }
const roleLabel = (r) => ROLE_MAP[r] || r
const typeLabel = (t) => TYPE_MAP[t] || t
const phaseLabel = (p) => PHASE_MAP[p] || p

async function load() {
  loading.value = true
  try {
    const data = await listDocuments()
    docs.value = data.items || []
  } finally {
    loading.value = false
  }
}

function onRowClick(row) {
  router.push(`/knowledge/${encodeURIComponent(row.name)}`)
}

async function onRebuild() {
  try {
    await ElMessageBox.confirm(
      '将清空当前向量库并重新加载 knowledge/ 目录下的所有 Markdown 文档，确认继续？',
      '重建知识库',
      { type: 'warning' }
    )
  } catch { return }
  rebuilding.value = true
  try {
    const r = await rebuildKnowledge()
    ElMessage.success(`重建完成：${r.documents_loaded} 个文档，${r.total_chunks} 个切片`)
    await load()
  } finally {
    rebuilding.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: var(--text-secondary); }
</style>
