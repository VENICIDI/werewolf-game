<template>
  <div ref="chartEl" :style="{ width: '100%', height: height + 'px' }" />
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: Number, default: 280 },
})

const chartEl = ref(null)
let chart = null
let ro = null

function applyTheme(opt) {
  return {
    backgroundColor: 'transparent',
    textStyle: { color: '#cbd5e1' },
    ...opt,
  }
}

function render() {
  if (!chart) return
  chart.setOption(applyTheme(props.option), true)
}

onMounted(() => {
  chart = echarts.init(chartEl.value, 'dark')
  render()
  ro = new ResizeObserver(() => chart?.resize())
  ro.observe(chartEl.value)
})

onBeforeUnmount(() => {
  ro?.disconnect()
  chart?.dispose()
})

watch(() => props.option, render, { deep: true })
</script>
