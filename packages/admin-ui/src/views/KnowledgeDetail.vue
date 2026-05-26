<template>
  <div>
    <div class="page-header" style="display:flex; justify-content:space-between; align-items:center;">
      <div>
        <el-button text @click="$router.back()">← 返回知识库</el-button>
        <h2 style="margin-top: 4px;">📄 {{ name }}</h2>
        <div class="desc">共 {{ chunks.length }} 个切片 · 总 {{ totalChar.toLocaleString() }} 字符</div>
      </div>
      <el-radio-group v-model="mode" size="small">
        <el-radio-button label="chunks">切片视图</el-radio-button>
        <el-radio-button label="raw">原文视图</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="mode === 'chunks'" v-loading="loading">
      <el-row :gutter="16">
        <el-col v-for="(c, i) in chunks" :key="c.id" :span="12" style="margin-bottom: 16px;">
          <div class="chunk-card">
            <div class="chunk-head">
              <span class="idx">#{{ i + 1 }}</span>
              <span class="chunk-tags">
                <el-tag v-if="c.role" size="small" effect="dark">{{ c.role }}</el-tag>
                <el-tag v-if="c.doc_type" size="small" type="warning" effect="dark">{{ c.doc_type }}</el-tag>
                <el-tag v-if="c.game_phase" size="small" type="success" effect="dark">{{ c.game_phase }}</el-tag>
                <el-tag v-if="c.difficulty" size="small" type="info" effect="dark">{{ c.difficulty }}</el-tag>
              </span>
              <span class="chunk-len">{{ c.char_count }} 字</span>
            </div>
            <div class="chunk-body">{{ c.content }}</div>
          </div>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && !chunks.length" description="该文档没有切片数据" />
    </div>

    <div v-else v-loading="rawLoading" class="glass-card">
      <pre class="log-content">{{ rawContent }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listDocumentChunks, getRawDocument } from '../api'

const props = defineProps({ name: String })
const chunks = ref([])
const loading = ref(false)
const mode = ref('chunks')
const rawContent = ref('')
const rawLoading = ref(false)

const totalChar = computed(() => chunks.value.reduce((s, c) => s + (c.char_count || 0), 0))

async function loadChunks() {
  loading.value = true
  try {
    const data = await listDocumentChunks(props.name)
    chunks.value = data.items || []
  } finally {
    loading.value = false
  }
}

async function loadRaw() {
  rawLoading.value = true
  try {
    const data = await getRawDocument(props.name)
    rawContent.value = data.content || ''
  } catch {
    rawContent.value = '(读取原文失败)'
  } finally {
    rawLoading.value = false
  }
}

onMounted(loadChunks)

watch(mode, (v) => {
  if (v === 'raw' && !rawContent.value) loadRaw()
})
</script>

<style lang="scss" scoped>
.chunk-card {
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.2s;
  height: 100%;
  &:hover {
    border-color: rgba(99, 102, 241, 0.5);
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.15);
  }
  .chunk-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
    flex-wrap: wrap;
    .idx {
      font-weight: 700;
      color: #a5b4fc;
    }
    .chunk-tags { flex: 1; display: flex; gap: 4px; flex-wrap: wrap; }
    .chunk-len { color: var(--text-secondary); font-size: 11px; }
  }
  .chunk-body {
    font-size: 13px;
    line-height: 1.7;
    color: #e2e8f0;
    white-space: pre-wrap;
    word-break: break-word;
    max-height: 220px;
    overflow-y: auto;
  }
}
</style>
