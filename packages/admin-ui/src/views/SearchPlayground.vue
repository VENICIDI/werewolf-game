<template>
  <div>
    <div class="page-header">
      <h2>RAG 检索调试器</h2>
      <div class="desc">输入 query，实时查看向量检索命中结果与相似度分数</div>
    </div>

    <div class="glass-card search-bar">
      <el-input
        v-model="query"
        placeholder="例如：狼人首夜应该刀谁？预言家被悍跳怎么办？"
        size="large"
        @keyup.enter="onSearch"
      >
        <template #prepend><span>🔍</span></template>
      </el-input>
      <el-select v-model="role" placeholder="角色过滤" clearable size="large" style="width: 180px; margin: 0 8px;">
        <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
      <el-input-number v-model="topK" :min="1" :max="20" size="large" controls-position="right" />
      <el-button type="primary" size="large" :loading="loading" @click="onSearch" style="margin-left: 8px;">
        检索
      </el-button>
    </div>

    <div v-if="lastResult" class="result-summary">
      <div>
        <span class="big">命中 {{ lastResult.hits.length }}</span> 条结果
        <span class="muted">· 用时 {{ lastResult.duration_ms }} ms</span>
      </div>
      <div class="muted">Top-{{ lastResult.top_k }} · 角色过滤：{{ lastResult.role_filter || '不限' }}</div>
    </div>

    <el-empty v-if="!loading && !lastResult" description="先输入一个 query 试试吧 🧠" />

    <div v-if="lastResult" class="hits-grid">
      <div v-for="h in lastResult.hits" :key="h.rank" class="hit-card">
        <div class="hit-head">
          <div class="rank-badge">#{{ h.rank }}</div>
          <div class="hit-meta">
            <div class="hit-source">📄 {{ h.source }}</div>
            <div class="hit-tags">
              <el-tag v-if="h.role" size="small" effect="dark">{{ h.role }}</el-tag>
              <el-tag v-if="h.doc_type" size="small" effect="dark" type="warning">{{ h.doc_type }}</el-tag>
              <el-tag v-if="h.game_phase" size="small" effect="dark" type="success">{{ h.game_phase }}</el-tag>
            </div>
          </div>
          <div class="hit-score">
            <div class="score-num">{{ formatScore(h.score) }}</div>
            <div class="score-label">相似度</div>
          </div>
        </div>
        <div class="score-bar">
          <div class="score-bar-inner" :style="{ width: scorePercent(h.score) + '%' }" />
        </div>
        <div class="hit-body">{{ h.content }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchKnowledge } from '../api'

const query = ref('')
const role = ref('')
const topK = ref(5)
const loading = ref(false)
const lastResult = ref(null)

const roleOptions = [
  { value: 'WEREWOLF', label: '狼人' },
  { value: 'SEER', label: '预言家' },
  { value: 'WITCH', label: '女巫' },
  { value: 'HUNTER', label: '猎人' },
  { value: 'GUARD', label: '守卫' },
]

async function onSearch() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    lastResult.value = await searchKnowledge({
      query: query.value.trim(),
      top_k: topK.value,
      role_filter: role.value || null,
    })
  } finally {
    loading.value = false
  }
}

function formatScore(s) {
  return (Number(s) || 0).toFixed(3)
}

function scorePercent(s) {
  const v = Number(s) || 0
  return Math.min(100, Math.max(0, v * 100))
}
</script>

<style lang="scss" scoped>
.search-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.result-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 12px 16px;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 8px;
  font-size: 13px;
  .big {
    font-size: 18px;
    font-weight: 700;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .muted { color: var(--text-secondary); font-size: 12px; margin-left: 8px; }
}

.hits-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hit-card {
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.2s;
  &:hover {
    border-color: rgba(99, 102, 241, 0.4);
    transform: translateX(4px);
  }
  .hit-head {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
  }
  .rank-badge {
    width: 32px; height: 32px;
    border-radius: 8px;
    background: var(--gradient-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 13px;
    color: white;
    flex-shrink: 0;
  }
  .hit-meta { flex: 1; min-width: 0; }
  .hit-source { font-weight: 600; font-size: 13px; }
  .hit-tags { margin-top: 4px; display: flex; gap: 4px; flex-wrap: wrap; }
  .hit-score {
    text-align: right;
    .score-num {
      font-size: 20px;
      font-weight: 700;
      background: var(--gradient-accent);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .score-label {
      font-size: 11px;
      color: var(--text-secondary);
    }
  }
  .hit-body {
    margin-top: 10px;
    font-size: 13px;
    line-height: 1.7;
    color: #e2e8f0;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .score-bar { margin-bottom: 0; }
}
</style>
