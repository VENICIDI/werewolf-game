<template>
  <el-container class="layout-root">
    <el-aside class="aside">
      <div class="brand">
        <div class="logo">🐺</div>
        <div class="title">
          <div class="title-main">Werewolf RAG</div>
          <div class="title-sub">管理后台</div>
        </div>
      </div>
      <el-menu
        :default-active="activeMenu"
        :router="true"
        background-color="transparent"
        text-color="#cbd5e1"
        active-text-color="#a5b4fc"
        class="menu"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <span class="menu-icon">{{ m.icon }}</span>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <div class="status-pill" :class="systemOk ? 'ok' : 'bad'">
          <span class="dot" />
          {{ systemOk ? 'AI 服务运行中' : '连接失败' }}
        </div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="bread">
          <span class="bc-main">{{ currentMeta.title }}</span>
          <span v-if="currentMeta.desc" class="bc-desc">/ {{ currentMeta.desc }}</span>
        </div>
        <div class="topbar-actions">
          <el-tag effect="dark" round class="meta-tag">
            Embedding: {{ embeddingModel || '—' }}
          </el-tag>
          <el-tag effect="dark" round type="info" class="meta-tag">
            Chunks: {{ totalChunks }}
          </el-tag>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getSystemStatus } from './api'

const route = useRoute()
const menus = [
  { path: '/dashboard', title: '总览', icon: '📊', desc: '系统概览与统计图表' },
  { path: '/knowledge', title: '知识库', icon: '📚', desc: '源文档、切片与元数据' },
  { path: '/search', title: '检索调试', icon: '🔍', desc: 'RAG 在线检索与相似度可视化' },
  { path: '/retrieval-logs', title: '检索日志', icon: '🧾', desc: '每次 RAG 查询的明细记录' },
  { path: '/llm-logs', title: 'LLM 日志', icon: '💬', desc: 'LLM 完整 prompt / response' },
  { path: '/system', title: '系统状态', icon: '⚙️', desc: '运行环境与组件信息' },
]

const activeMenu = computed(() => route.path)
const currentMeta = computed(() => menus.find(m => m.path === route.path) || menus[0])

const systemOk = ref(false)
const embeddingModel = ref('')
const totalChunks = ref(0)

onMounted(async () => {
  try {
    const data = await getSystemStatus()
    systemOk.value = !!data.rag_available
    embeddingModel.value = data?.vectorstore?.embedding_model || ''
    totalChunks.value = data?.vectorstore?.total_chunks || 0
  } catch (e) {
    systemOk.value = false
  }
})
</script>

<style lang="scss" scoped>
.layout-root { height: 100vh; }

.aside {
  width: 220px !important;
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  border-right: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column;
  padding: 18px 0;
}

.brand {
  display: flex;
  align-items: center;
  padding: 4px 20px 20px;
  border-bottom: 1px solid var(--border-soft);
  margin-bottom: 12px;
  .logo {
    font-size: 28px;
    background: var(--gradient-primary);
    width: 42px; height: 42px;
    border-radius: 10px;
    display: flex; align-items: center; justify-content: center;
    margin-right: 10px;
  }
  .title-main {
    font-size: 15px;
    font-weight: 700;
    background: var(--gradient-primary);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .title-sub { font-size: 11px; color: var(--text-secondary); }
}

.menu {
  flex: 1;
  border: none;
  :deep(.el-menu-item) {
    height: 44px;
    line-height: 44px;
    margin: 4px 12px;
    border-radius: 8px;
    font-size: 13px;
  }
  :deep(.el-menu-item.is-active) {
    background: rgba(99, 102, 241, 0.15) !important;
    border: 1px solid rgba(99, 102, 241, 0.4);
  }
  .menu-icon { margin-right: 10px; font-size: 15px; }
}

.aside-footer { padding: 12px 16px; }

.status-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: rgba(16, 185, 129, 0.1);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.25);
  &.bad {
    background: rgba(239, 68, 68, 0.1);
    color: #fca5a5;
    border-color: rgba(239, 68, 68, 0.3);
  }
  .dot {
    width: 8px; height: 8px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 8px currentColor;
  }
}

.topbar {
  background: rgba(15, 23, 42, 0.6);
  border-bottom: 1px solid var(--border-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  backdrop-filter: blur(8px);
  .bread {
    .bc-main { font-size: 15px; font-weight: 600; }
    .bc-desc { color: var(--text-secondary); margin-left: 8px; font-size: 12px; }
  }
}

.meta-tag { margin-left: 8px; }

.main {
  padding: 24px;
  background: var(--bg-page);
  overflow: auto;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.18s ease;
}
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
