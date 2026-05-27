<template>
  <div>
    <div class="page-header">
      <h2>RAG 检索调试器</h2>
      <div class="desc">混合检索 (BM25 + 向量) → RRF 融合 → BGE Reranker 精排 | 支持 HyDE / Multi-Query 改写</div>
    </div>

    <div class="glass-card">
      <el-input
        v-model="query"
        placeholder="例如：狼人首夜应该刀谁？预言家被悍跳怎么办？"
        size="large"
        @keyup.enter="onSearch"
      >
        <template #prepend><span>🔍</span></template>
      </el-input>

      <div class="config-row">
        <div class="config-group">
          <span class="config-label">角色过滤</span>
          <el-select v-model="role" placeholder="不限" clearable size="default" style="width: 150px;">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </div>
        <div class="config-group">
          <span class="config-label">Top-K</span>
          <el-input-number v-model="topK" :min="1" :max="20" size="default" controls-position="right" style="width: 110px;" />
        </div>
        <div class="config-group">
          <span class="config-label">召回数</span>
          <el-input-number v-model="recallK" :min="3" :max="50" size="default" controls-position="right" style="width: 110px;" />
        </div>
        <div class="config-group">
          <span class="config-label">Query 改写</span>
          <el-select v-model="queryRewrite" size="default" style="width: 140px;">
            <el-option label="不改写" value="none" />
            <el-option label="HyDE (假设性回答)" value="hyde" />
            <el-option label="Multi-Query" value="multi_query" />
          </el-select>
        </div>
        <div class="config-group switches">
          <el-switch v-model="useHybrid" active-text="混合检索 (BM25+向量)" />
          <el-switch v-model="useReranker" active-text="BGE Reranker 精排" />
        </div>
        <div class="config-group" style="margin-left: auto;">
          <el-button type="primary" :loading="loading" @click="onSearch" size="default">检索</el-button>
        </div>
      </div>
    </div>

    <div v-if="lastResult" class="result-summary">
      <div>
        <span class="big">命中 {{ lastResult.hits.length }}</span> 条结果
        <span class="muted">· 总用时 {{ lastResult.duration_ms }} ms</span>
      </div>
      <div class="muted">
        <el-tag v-if="lastResult.pipeline.use_hybrid" size="small" effect="dark" type="info" style="margin-right: 4px;">Hybrid</el-tag>
        <el-tag v-else size="small" effect="plain" style="margin-right: 4px;">Vector-only</el-tag>
        <el-tag v-if="lastResult.pipeline.use_reranker" size="small" effect="dark" type="success" style="margin-right: 4px;">Reranked</el-tag>
        <el-tag v-if="lastResult.pipeline.query_rewrite !== 'none'" size="small" effect="dark" type="warning" style="margin-right: 4px;">
          {{ lastResult.pipeline.query_rewrite.toUpperCase() }}
        </el-tag>
        <span>Top-{{ lastResult.top_k }} · 召回-{{ lastResult.pipeline.recall_k }} · 角色：{{ lastResult.role_filter || '不限' }}</span>
      </div>
    </div>

    <div v-if="lastResult && lastResult.stages.length" class="pipeline-viz">
      <div class="pipeline-title">🧬 检索管线 (Pipeline Trace)</div>
      <div class="pipeline-stages">
        <div v-for="(s, i) in lastResult.stages" :key="i" class="pipe-stage">
          <div class="pipe-step-num">{{ i + 1 }}</div>
          <div class="pipe-stage-name">{{ stageLabel(s.name) }}</div>
          <div class="pipe-stage-meta">
            <span class="meta-pill duration" :class="durationClass(s.duration_ms)">{{ s.duration_ms }} ms</span>
            <span v-if="s.candidates !== undefined" class="meta-pill">候选 {{ s.candidates }}</span>
            <span v-if="s.in_candidates !== undefined" class="meta-pill">{{ s.in_candidates }} → {{ s.out_candidates }}</span>
            <span v-if="s.mode" class="meta-pill mode">{{ s.mode }}</span>
            <span v-if="s.model" class="meta-pill model" :title="s.model">{{ shortenModel(s.model) }}</span>
          </div>
          <div v-if="s.queries && s.queries.length" class="pipe-queries">
            <div v-for="(q, qi) in s.queries" :key="qi" class="pipe-query">→ {{ q }}</div>
          </div>
          <div v-if="i < lastResult.stages.length - 1" class="pipe-arrow">↓</div>
        </div>
      </div>
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
            <div class="score-label">{{ scoreLabel }}</div>
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
import { computed, ref } from 'vue'
import { searchKnowledge } from '../api'

const query = ref('')
const role = ref('')
const topK = ref(5)
const recallK = ref(20)
const useHybrid = ref(true)
const useReranker = ref(false)
const queryRewrite = ref('none')

const loading = ref(false)
const lastResult = ref(null)

const roleOptions = [
  { value: 'WEREWOLF', label: '狼人' },
  { value: 'SEER', label: '预言家' },
  { value: 'WITCH', label: '女巫' },
  { value: 'HUNTER', label: '猎人' },
  { value: 'GUARD', label: '守卫' },
]

const STAGE_LABELS = {
  query_rewrite: '① Query 改写',
  vector_recall: '稠密向量召回',
  bm25_recall: 'BM25 稀疏召回',
  rrf_fusion: 'RRF 融合',
  rerank: 'BGE Reranker 精排',
}

const scoreLabel = computed(() => useReranker.value ? '精排分数' : '检索分数')

async function onSearch() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    lastResult.value = await searchKnowledge({
      query: query.value.trim(),
      top_k: topK.value,
      role_filter: role.value || null,
      use_hybrid: useHybrid.value,
      use_reranker: useReranker.value,
      query_rewrite: queryRewrite.value,
      recall_k: recallK.value,
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

function stageLabel(name) {
  return STAGE_LABELS[name] || name
}

function durationClass(ms) {
  if (!ms || ms < 50) return 'fast'
  if (ms < 500) return 'normal'
  return 'slow'
}

function shortenModel(m) {
  return m.split('/').pop()
}
</script>

<style lang="scss" scoped>
.config-row {
  display: flex;
  align-items: center;
  margin-top: 14px;
  gap: 16px;
  flex-wrap: wrap;
}
.config-group {
  display: flex;
  align-items: center;
  gap: 6px;
  &.switches {
    gap: 16px;
    margin-left: 8px;
  }
}
.config-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.result-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 12px;
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

/* --- Pipeline Visualizer --- */
.pipeline-viz {
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
}
.pipeline-title {
  font-size: 14px;
  font-weight: 600;
  color: #a5b4fc;
  margin-bottom: 12px;
}
.pipeline-stages {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pipe-stage {
  position: relative;
  display: grid;
  grid-template-columns: 28px 200px 1fr;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 8px;
}
.pipe-step-num {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--gradient-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
}
.pipe-stage-name {
  font-weight: 600;
  font-size: 13px;
}
.pipe-stage-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.meta-pill {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.15);
  color: #cbd5e1;
  font-family: "JetBrains Mono", Menlo, monospace;
  &.duration.fast { background: rgba(16, 185, 129, 0.2); color: #34d399; }
  &.duration.normal { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
  &.duration.slow { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }
  &.mode { background: rgba(139, 92, 246, 0.2); color: #c4b5fd; }
  &.model { background: rgba(6, 182, 212, 0.2); color: #67e8f9; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
.pipe-queries {
  grid-column: 2 / -1;
  margin-top: 4px;
  font-size: 11px;
  color: #94a3b8;
  font-style: italic;
}
.pipe-query {
  padding: 2px 0;
  line-height: 1.4;
}
.pipe-arrow {
  display: none;
}

/* --- Hit cards (复用旧样式) --- */
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
