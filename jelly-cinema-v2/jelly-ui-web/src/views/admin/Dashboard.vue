<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboardStats, type DashboardStats } from '@/api/admin'
import * as echarts from 'echarts'

const loading = ref(false)
const stats = ref<DashboardStats | null>(null)

const userTrendChart = ref<HTMLElement>()
const messageDistChart = ref<HTMLElement>()

onMounted(async () => {
  await loadStats()
})

async function loadStats() {
  loading.value = true
  try {
    const res = await getDashboardStats()
    stats.value = res.data
    
    // 渲染图表
    setTimeout(() => {
      renderUserTrendChart()
      renderMessageDistChart()
    }, 100)
  } finally {
    loading.value = false
  }
}

function renderUserTrendChart() {
  if (!userTrendChart.value || !stats.value?.userTrend) return
  
  const chart = echarts.init(userTrendChart.value)
  chart.setOption({
    title: { text: '近7日新增用户', left: 'center', textStyle: { color: '#fff', fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: stats.value.userTrend.map(i => i.date),
      axisLabel: { color: '#aaa' },
      axisLine: { lineStyle: { color: '#444' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#aaa' },
      splitLine: { lineStyle: { color: '#333' } }
    },
    series: [{
      data: stats.value.userTrend.map(i => i.value),
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.3 },
      itemStyle: { color: '#409eff' }
    }],
    grid: { left: 50, right: 20, bottom: 30, top: 50 }
  })
}

function renderMessageDistChart() {
  if (!messageDistChart.value || !stats.value?.messageDist) return
  
  const chart = echarts.init(messageDistChart.value)
  chart.setOption({
    title: { text: '消息时段分布', left: 'center', textStyle: { color: '#fff', fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: stats.value.messageDist.map(i => i.date),
      axisLabel: { color: '#aaa' },
      axisLine: { lineStyle: { color: '#444' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#aaa' },
      splitLine: { lineStyle: { color: '#333' } }
    },
    series: [{
      data: stats.value.messageDist.map(i => i.value),
      type: 'bar',
      itemStyle: { color: '#67c23a' }
    }],
    grid: { left: 50, right: 20, bottom: 30, top: 50 }
  })
}
</script>

<template>
  <div class="p-2" v-loading="loading">
    <!-- 统计卡片 - Neo-Brutalism -->
    <div class="grid grid-cols-4 gap-6 mb-6">
      <div class="bg-pop-blue border-3 border-black shadow-brutal rounded-2xl p-5 text-white hover:translate-x-1 hover:-translate-y-1 hover:shadow-brutal-sm transition-all">
        <div class="flex items-center gap-4">
          <div class="w-14 h-14 rounded-full border-3 border-black bg-white flex items-center justify-center text-pop-blue">
            <el-icon size="28"><User /></el-icon>
          </div>
          <div>
            <div class="text-3xl font-black">{{ stats?.onlineCount || 0 }}</div>
            <div class="font-bold text-sm opacity-90">实时在线</div>
          </div>
        </div>
      </div>
      
      <div class="bg-pop-green border-3 border-black shadow-brutal rounded-2xl p-5 text-black hover:translate-x-1 hover:-translate-y-1 hover:shadow-brutal-sm transition-all">
        <div class="flex items-center gap-4">
          <div class="w-14 h-14 rounded-full border-3 border-black bg-white flex items-center justify-center text-pop-green">
            <el-icon size="28"><ChatDotRound /></el-icon>
          </div>
          <div>
            <div class="text-3xl font-black">{{ stats?.todayMessageCount || 0 }}</div>
            <div class="font-bold text-sm opacity-90">今日消息</div>
          </div>
        </div>
      </div>
      
      <div class="bg-pop-orange border-3 border-black shadow-brutal rounded-2xl p-5 text-black hover:translate-x-1 hover:-translate-y-1 hover:shadow-brutal-sm transition-all">
        <div class="flex items-center gap-4">
          <div class="w-14 h-14 rounded-full border-3 border-black bg-white flex items-center justify-center text-pop-orange">
            <el-icon size="28"><UserFilled /></el-icon>
          </div>
          <div>
            <div class="text-3xl font-black">{{ stats?.todayNewUsers || 0 }} / {{ stats?.totalUsers || 0 }}</div>
            <div class="font-bold text-sm opacity-90">新增/总用户</div>
          </div>
        </div>
      </div>
      
      <div class="bg-pop-red border-3 border-black shadow-brutal rounded-2xl p-5 text-white hover:translate-x-1 hover:-translate-y-1 hover:shadow-brutal-sm transition-all">
        <div class="flex items-center gap-4">
          <div class="w-14 h-14 rounded-full border-3 border-black bg-white flex items-center justify-center text-pop-red">
            <el-icon size="28"><Warning /></el-icon>
          </div>
          <div>
            <div class="text-3xl font-black">{{ stats?.pendingReports || 0 }}</div>
            <div class="font-bold text-sm opacity-90">待处理举报</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区域 - Neo-Brutalism -->
    <div class="grid grid-cols-2 gap-6 mb-6">
      <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
        <div class="bg-pop-purple border-2 border-black rounded-xl px-4 py-2 inline-block mb-4">
          <h3 class="text-lg font-black text-white uppercase">用户增长趋势</h3>
        </div>
        <div ref="userTrendChart" class="w-full h-80"></div>
      </div>
      <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
        <div class="bg-pop-yellow border-2 border-black rounded-xl px-4 py-2 inline-block mb-4">
          <h3 class="text-lg font-black text-black uppercase">消息活跃度</h3>
        </div>
        <div ref="messageDistChart" class="w-full h-80"></div>
      </div>
    </div>

    <!-- 快捷入口 - Neo-Brutalism -->
    <div>
      <div class="bg-pop-pink border-3 border-black rounded-2xl px-6 py-3 inline-block mb-4 shadow-brutal-sm">
        <h3 class="text-lg font-black text-white uppercase flex items-center">
          <el-icon class="mr-2"><Lightning /></el-icon> 快捷操作
        </h3>
      </div>
      <div class="grid grid-cols-5 gap-6">
        <router-link to="/admin/users" class="group flex flex-col items-center gap-3 p-6 bg-white border-3 border-black shadow-brutal-sm rounded-2xl hover:bg-pop-blue hover:text-white hover:shadow-brutal hover:translate-x-1 hover:-translate-y-1 transition-all">
          <div class="w-16 h-16 rounded-full border-3 border-black bg-pop-blue/10 group-hover:bg-white flex items-center justify-center transition-colors">
            <el-icon size="32" class="text-pop-blue"><User /></el-icon>
          </div>
          <span class="font-bold text-lg">用户管理</span>
        </router-link>
        <router-link to="/admin/films" class="group flex flex-col items-center gap-3 p-6 bg-white border-3 border-black shadow-brutal-sm rounded-2xl hover:bg-pop-purple hover:text-white hover:shadow-brutal hover:translate-x-1 hover:-translate-y-1 transition-all">
          <div class="w-16 h-16 rounded-full border-3 border-black bg-pop-purple/10 group-hover:bg-white flex items-center justify-center transition-colors">
            <el-icon size="32" class="text-pop-purple"><Film /></el-icon>
          </div>
          <span class="font-bold text-lg">影片管理</span>
        </router-link>
        <router-link to="/admin/sensitive" class="group flex flex-col items-center gap-3 p-6 bg-white border-3 border-black shadow-brutal-sm rounded-2xl hover:bg-pop-yellow hover:text-black hover:shadow-brutal hover:translate-x-1 hover:-translate-y-1 transition-all">
          <div class="w-16 h-16 rounded-full border-3 border-black bg-pop-yellow/10 group-hover:bg-white flex items-center justify-center transition-colors">
            <el-icon size="32" class="text-pop-yellow"><Edit /></el-icon>
          </div>
          <span class="font-bold text-lg">敏感词库</span>
        </router-link>
        <router-link to="/admin/reports" class="group flex flex-col items-center gap-3 p-6 bg-white border-3 border-black shadow-brutal-sm rounded-2xl hover:bg-pop-red hover:text-white hover:shadow-brutal hover:translate-x-1 hover:-translate-y-1 transition-all">
          <div class="w-16 h-16 rounded-full border-3 border-black bg-pop-red/10 group-hover:bg-white flex items-center justify-center transition-colors">
            <el-icon size="32" class="text-pop-red"><Warning /></el-icon>
          </div>
          <span class="font-bold text-lg">举报处理</span>
        </router-link>
        <router-link to="/admin/groups" class="group flex flex-col items-center gap-3 p-6 bg-white border-3 border-black shadow-brutal-sm rounded-2xl hover:bg-pop-green hover:text-black hover:shadow-brutal hover:translate-x-1 hover:-translate-y-1 transition-all">
          <div class="w-16 h-16 rounded-full border-3 border-black bg-pop-green/10 group-hover:bg-white flex items-center justify-center transition-colors">
            <el-icon size="32" class="text-pop-green"><ChatDotRound /></el-icon>
          </div>
          <span class="font-bold text-lg">群组审计</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  @apply flex items-center gap-4 p-5 rounded-lg text-white;
}

.stat-icon {
  @apply w-14 h-14 rounded-full bg-white/20 flex items-center justify-center;
}

.stat-value {
  @apply text-2xl font-bold;
}

.stat-label {
  @apply text-sm opacity-80;
}

.chart-card {
  @apply bg-gray-800 rounded-lg p-4;
}

.quick-link {
  @apply flex flex-col items-center gap-2 p-4 bg-gray-800 rounded-lg text-gray-300 hover:text-primary hover:bg-gray-700 transition-colors;
}
</style>
