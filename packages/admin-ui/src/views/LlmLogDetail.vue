<template>
  <div>
    <div class="page-header" style="display:flex; justify-content:space-between; align-items:center;">
      <div>
        <el-button text @click="$router.back()">← 返回列表</el-button>
        <h2 style="margin-top: 4px;">🎮 Game {{ gameId }}</h2>
        <div class="desc">
          完整 LLM 调用记录 · 共 {{ callCount }} 次调用
          <span v-if="logSize" class="muted">· {{ formatSize(logSize) }}</span>
        </div>
      </div>
      <div>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button label="parsed">结构化视图</el-radio-button>
          <el-radio-button label="raw">原始日志</el-radio-button>
        </el-radio-group>
        <el-button @click="load" :loading="loading" size="small" style="margin-left: 8px;">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="viewMode === 'parsed'">
        <el-empty v-if="!calls.length" description="未解析到 LLM 调用记录" />
        <div v-for="(c, i) in calls" :key="i" class="call-card">
          <div class="call-head">
            <div class="call-tag">
              <el-tag effect="dark" type="primary">player={{ c.player }}</el-tag>
              <el-tag effect="dark" type="warning" style="margin-left: 6px;">{{ c.action }}</el-tag>
              <el-tag v-if="c.duration" effect="dark" type="info" style="margin-left: 6px;">{{ c.duration }}</el-tag>
            </div>
            <span class="muted">#{{ i + 1 }}</span>
          </div>
          <div class="call-section">
            <div class="section-label">📝 PROMPT <span class="muted">({{ c.prompt.length }} chars)</span></div>
            <pre class="log-content">{{ c.prompt }}</pre>
          </div>
          <div class="call-section">
            <div class="section-label">🤖 RESPONSE <span class="muted">({{ c.response.length }} chars)</span></div>
            <pre class="log-content response">{{ c.response }}</pre>
          </div>
        </div>
      </div>

      <div v-else class="glass-card">
        <pre class="log-content">{{ rawContent }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getLlmGameLog } from '../api'

const props = defineProps({ gameId: String })
const loading = ref(false)
const rawContent = ref('')
const logSize = ref(0)
const viewMode = ref('parsed')

async function load() {
  loading.value = true
  try {
    const data = await getLlmGameLog(props.gameId)
    rawContent.value = data.content || ''
    logSize.value = data.size || 0
  } finally {
    loading.value = false
  }
}

const calls = computed(() => parseLogContent(rawContent.value))
const callCount = computed(() => calls.value.length)

function parseLogContent(content) {
  if (!content) return []
  const blocks = []
  // 按 "[LLM CALL]" 切分
  const lines = content.split('\n')
  let cur = null
  let section = null

  for (const raw of lines) {
    const line = raw
    if (line.includes('[LLM CALL]')) {
      if (cur) blocks.push(cur)
      cur = { player: '?', action: '?', duration: '', prompt: '', response: '' }
      section = 'header'
      const m = line.match(/player=(\S+)\s+action=(\S+)(?:\s+\((.*?)\))?/)
      if (m) {
        cur.player = m[1]
        cur.action = m[2]
        cur.duration = m[3] || ''
      }
      continue
    }
    if (!cur) continue
    if (line.startsWith('PROMPT:')) { section = 'prompt'; continue }
    if (line.startsWith('RESPONSE:')) { section = 'response'; continue }
    if (/^=+$/.test(line) || /^(- )+$/.test(line.trim())) continue

    if (section === 'prompt') cur.prompt += (cur.prompt ? '\n' : '') + line
    else if (section === 'response') cur.response += (cur.response ? '\n' : '') + line
  }
  if (cur) blocks.push(cur)
  return blocks.filter(b => b.prompt || b.response)
}

function formatSize(b) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

onMounted(load)
</script>

<style lang="scss" scoped>
.muted { color: var(--text-secondary); font-size: 12px; }

.call-card {
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 16px;
  .call-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }
  .call-section {
    margin-bottom: 12px;
    &:last-child { margin-bottom: 0; }
  }
  .section-label {
    font-size: 12px;
    font-weight: 600;
    color: #a5b4fc;
    margin-bottom: 6px;
  }
}

.log-content { max-height: 320px; }
.log-content.response {
  background: rgba(16, 185, 129, 0.05);
  border-color: rgba(16, 185, 129, 0.25);
  color: #d1fae5;
}
</style>
