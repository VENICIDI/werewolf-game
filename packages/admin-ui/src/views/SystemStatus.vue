<template>
  <div>
    <div class="page-header">
      <h2>系统状态</h2>
      <div class="desc">RAG 引擎运行环境与组件信息</div>
    </div>

    <el-row v-loading="loading" :gutter="16">
      <el-col :span="12">
        <div class="glass-card">
          <h3 class="card-h">🧬 向量库 (Vectorstore)</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="状态">
              <el-tag :type="status.rag_available ? 'success' : 'danger'" effect="dark">
                {{ status.rag_available ? '运行中' : '不可用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="持久化目录">
              <code>{{ status.vectorstore?.persist_dir || '—' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="文档数">{{ status.vectorstore?.document_count ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="切片数">{{ status.vectorstore?.total_chunks ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="Embedding 模型">
              <el-tag effect="dark">{{ status.vectorstore?.embedding_model || '—' }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="glass-card">
          <h3 class="card-h">⚙️ 环境变量</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item v-for="(v, k) in status.env" :key="k" :label="k">
              <code>{{ maskKey(k, v) }}</code>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <div class="glass-card" style="margin-top: 16px;">
      <h3 class="card-h">📐 RAG 检索流程</h3>
      <div class="flow">
        <div class="step" v-for="(s, i) in flow" :key="i">
          <div class="step-num">{{ i + 1 }}</div>
          <div class="step-body">
            <div class="step-title">{{ s.title }}</div>
            <div class="step-desc">{{ s.desc }}</div>
          </div>
          <div v-if="i < flow.length - 1" class="step-arrow">→</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getSystemStatus } from '../api'

const loading = ref(false)
const status = ref({})

const flow = [
  { title: '用户 Query', desc: 'API / Agent 提交检索请求' },
  { title: 'Query 改写 (可选)', desc: 'HyDE 假设性回答 / Multi-Query 多角度改写' },
  { title: '稠密向量召回', desc: 'BGE Embedding + Chroma similarity (top recall_k)' },
  { title: 'BM25 稀疏召回', desc: 'jieba 中文分词 + Okapi BM25 (top recall_k)' },
  { title: 'RRF 融合', desc: 'Reciprocal Rank Fusion 合并多路候选' },
  { title: 'BGE Reranker', desc: 'bge-reranker-base 精排到 top_k' },
  { title: '日志落盘', desc: 'JSONL 记录 query/pipeline/stages/scores' },
  { title: '注入 Prompt', desc: 'Top-K 切片拼入 LangChain Prompt 给 LLM' },
]

function maskKey(k, v) {
  if (!v) return '(empty)'
  if (k.includes('KEY') || k.includes('SECRET')) {
    const s = String(v)
    if (s.length <= 8) return '****'
    return s.slice(0, 4) + '****' + s.slice(-4)
  }
  return v
}

onMounted(async () => {
  loading.value = true
  try {
    status.value = await getSystemStatus()
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.card-h {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: 600;
  color: #a5b4fc;
}

:deep(.el-descriptions__label) {
  background: rgba(99, 102, 241, 0.08) !important;
  color: var(--text-secondary) !important;
}
code {
  font-family: monospace;
  font-size: 12px;
  color: #06b6d4;
}

.flow {
  display: flex;
  align-items: stretch;
  gap: 8px;
  flex-wrap: wrap;
  .step {
    flex: 1;
    min-width: 140px;
    display: flex;
    align-items: center;
    background: rgba(99, 102, 241, 0.05);
    border: 1px solid rgba(99, 102, 241, 0.2);
    border-radius: 8px;
    padding: 12px;
    position: relative;
  }
  .step-num {
    width: 28px; height: 28px;
    border-radius: 50%;
    background: var(--gradient-primary);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    flex-shrink: 0;
    margin-right: 10px;
  }
  .step-body { flex: 1; min-width: 0; }
  .step-title { font-size: 13px; font-weight: 600; }
  .step-desc { font-size: 11px; color: var(--text-secondary); margin-top: 2px; }
  .step-arrow {
    color: #a5b4fc;
    font-size: 18px;
    margin-left: 8px;
  }
}
</style>
