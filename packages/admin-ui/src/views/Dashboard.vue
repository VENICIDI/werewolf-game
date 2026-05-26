<template>
  <div>
    <div class="page-header">
      <h2>总览仪表盘</h2>
      <div class="desc">RAG 知识库与检索系统的实时全景视图</div>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">📚</div>
          <div class="stat-label">知识库源文档</div>
          <div class="stat-value">{{ overview?.knowledge?.document_count ?? 0 }}</div>
          <div class="stat-extra">状态：{{ overview?.knowledge?.status || 'unknown' }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">🧩</div>
          <div class="stat-label">向量切片总数</div>
          <div class="stat-value">{{ overview?.knowledge?.total_chunks ?? 0 }}</div>
          <div class="stat-extra">Embedding: {{ overview?.knowledge?.embedding_model || '—' }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">🔍</div>
          <div class="stat-label">24h 检索量</div>
          <div class="stat-value">{{ overview?.retrieval?.total ?? 0 }}</div>
          <div class="stat-extra">
            平均 {{ overview?.retrieval?.avg_duration_ms ?? 0 }}ms · P95 {{ overview?.retrieval?.p95_duration_ms ?? 0 }}ms
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon">💬</div>
          <div class="stat-label">LLM 日志游戏数</div>
          <div class="stat-value">{{ overview?.llm?.total_games ?? 0 }}</div>
          <div class="stat-extra">日志文件 {{ overview?.llm?.total_log_files ?? 0 }} 个</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;" v-loading="loading">
      <el-col :span="16">
        <div class="chart-card">
          <div class="chart-title">
            <span>📈 最近 24 小时检索量分布</span>
            <el-tag size="small" effect="dark" type="info">按小时聚合</el-tag>
          </div>
          <EChart :option="hourlyOption" :height="280" />
        </div>
      </el-col>
      <el-col :span="8">
        <div class="chart-card">
          <div class="chart-title">
            <span>🎭 检索按角色分布</span>
          </div>
          <EChart :option="roleOption" :height="280" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;" v-loading="loading">
      <el-col :span="12">
        <div class="chart-card">
          <div class="chart-title">
            <span>🔥 命中最多的知识源 Top10</span>
          </div>
          <EChart :option="topSourcesOption" :height="320" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart-card">
          <div class="chart-title">
            <span>🔎 高频 Query Top10</span>
          </div>
          <div v-if="!topQueries.length" class="empty-tip">暂无数据，发起几次检索后再来看看</div>
          <el-table v-else :data="topQueries" stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="query" label="Query" show-overflow-tooltip />
            <el-table-column prop="count" label="次数" width="80" align="right">
              <template #default="{ row }">
                <span class="tag-soft">{{ row.count }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getOverview } from '../api'
import EChart from '../components/EChart.vue'

const loading = ref(true)
const overview = ref(null)

async function load() {
  loading.value = true
  try {
    overview.value = await getOverview()
  } finally {
    loading.value = false
  }
}

onMounted(load)

const hourlyOption = computed(() => {
  const data = overview.value?.retrieval?.hourly || []
  return {
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.map(d => d.hour),
      axisLine: { lineStyle: { color: 'rgba(148,163,184,0.3)' } },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)' } },
    },
    series: [{
      type: 'bar',
      data: data.map(d => d.count),
      itemStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: '#a5b4fc' }, { offset: 1, color: '#6366f1' }],
        },
        borderRadius: [4, 4, 0, 0],
      },
      barMaxWidth: 24,
    }],
  }
})

const roleOption = computed(() => {
  const by = overview.value?.retrieval?.by_role || {}
  const arr = Object.entries(by).map(([k, v]) => ({ name: k, value: v }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#cbd5e1' } },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderColor: '#1f2937', borderWidth: 2 },
      label: { show: false },
      data: arr,
      color: ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#ec4899'],
    }],
  }
})

const topSourcesOption = computed(() => {
  const arr = overview.value?.retrieval?.top_sources || []
  return {
    grid: { left: 160, right: 24, top: 10, bottom: 30 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)' } } },
    yAxis: {
      type: 'category',
      data: arr.map(x => x.source).reverse(),
      axisLabel: { color: '#cbd5e1', formatter: v => v.length > 18 ? v.slice(0, 18) + '…' : v },
    },
    series: [{
      type: 'bar',
      data: arr.map(x => x.count).reverse(),
      itemStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
          colorStops: [{ offset: 0, color: '#06b6d4' }, { offset: 1, color: '#8b5cf6' }] },
        borderRadius: [0, 4, 4, 0],
      },
      barMaxWidth: 18,
    }],
  }
})

const topQueries = computed(() => overview.value?.retrieval?.top_queries || [])
</script>

<style scoped>
.empty-tip {
  color: var(--text-secondary);
  text-align: center;
  padding: 36px 0;
  font-size: 13px;
}
</style>
