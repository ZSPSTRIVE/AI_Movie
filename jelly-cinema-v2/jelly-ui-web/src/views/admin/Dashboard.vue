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
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(255, 255, 255, 0.9)', textStyle: { color: '#333' } },
    xAxis: {
      type: 'category',
      data: stats.value.userTrend.map(i => i.date),
      axisLabel: { color: '#64748b' },
      axisLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#f1f5f9' } }
    },
    series: [{
      data: stats.value.userTrend.map(i => i.value),
      type: 'line',
      smooth: true,
      areaStyle: { 
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
          { offset: 1, color: 'rgba(59, 130, 246, 0.01)' }
        ])
      },
      itemStyle: { color: '#3b82f6' },
      lineStyle: { width: 3 }
    }],
    grid: { left: 40, right: 20, bottom: 20, top: 40, containLabel: true }
  })
  
  window.addEventListener('resize', () => chart.resize())
}

function renderMessageDistChart() {
  if (!messageDistChart.value || !stats.value?.messageDist) return
  
  const chart = echarts.init(messageDistChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(255, 255, 255, 0.9)', textStyle: { color: '#333' } },
    xAxis: {
      type: 'category',
      data: stats.value.messageDist.map(i => i.date),
      axisLabel: { color: '#64748b' },
      axisLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#f1f5f9' } }
    },
    series: [{
      data: stats.value.messageDist.map(i => i.value),
      type: 'bar',
      itemStyle: { 
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#10b981' },
          { offset: 1, color: 'rgba(16, 185, 129, 0.2)' }
        ]),
        borderRadius: [4, 4, 0, 0]
      },
      barWidth: '40%'
    }],
    grid: { left: 40, right: 20, bottom: 20, top: 40, containLabel: true }
  })
  
  window.addEventListener('resize', () => chart.resize())
}
</script>

<template>
  <div class="p-8 h-full overflow-y-auto bg-gray-50" v-loading="loading">
    <!-- 欢迎语 -->
    <div class="mb-8 animate-fade-in-down">
      <h1 class="text-3xl font-bold text-gray-900 mb-2">仪表盘</h1>
      <p class="text-gray-500">系统运营概况与数据统计</p>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      <!-- 实时在线 -->
      <div class="stat-card group">
        <div>
           <div class="stat-label">实时在线</div>
           <div class="stat-value text-gray-900">{{ stats?.onlineCount || 0 }}</div>
        </div>
        <div class="stat-icon bg-blue-50 text-blue-600 group-hover:bg-blue-600 group-hover:text-white">
           <el-icon size="24"><svg-icon name="icon-gerenzhongxin-zhihui" /></el-icon>
        </div>
      </div>
      
      <!-- 今日消息 -->
      <div class="stat-card group">
        <div>
           <div class="stat-label">今日消息</div>
           <div class="stat-value text-gray-900">{{ stats?.todayMessageCount || 0 }}</div>
        </div>
        <div class="stat-icon bg-green-50 text-green-600 group-hover:bg-green-600 group-hover:text-white">
           <el-icon size="24"><svg-icon name="icon-xiaoxi-zhihui" /></el-icon>
        </div>
      </div>
      
      <!-- 用户总量 -->
      <div class="stat-card group">
        <div>
           <div class="stat-label">总用户数</div>
           <div class="stat-value text-gray-900">{{ stats?.totalUsers || 0 }}</div>
           <div class="text-xs text-green-600 mt-1 flex items-center font-medium">
             <el-icon class="mr-1"><svg-icon name="icon-a-xiala2" style="transform: rotate(180deg)" /></el-icon>
             今日 +{{ stats?.todayNewUsers || 0 }}
           </div>
        </div>
        <div class="stat-icon bg-purple-50 text-purple-600 group-hover:bg-purple-600 group-hover:text-white">
           <el-icon size="24"><svg-icon name="icon-shujukanban" /></el-icon>
        </div>
      </div>
      
      <!-- 待办举报 -->
      <div class="stat-card group">
        <div>
           <div class="stat-label">待处理举报</div>
           <div class="stat-value text-gray-900">{{ stats?.pendingReports || 0 }}</div>
        </div>
        <div class="stat-icon bg-red-50 text-red-600 group-hover:bg-red-600 group-hover:text-white">
           <el-icon size="24"><svg-icon name="icon-jubao" /></el-icon>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">用户增长趋势</h3>
          <el-tag size="small" type="primary" effect="plain" round>近7日</el-tag>
        </div>
        <div ref="userTrendChart" class="w-full h-80"></div>
      </div>
      
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">消息活跃度</h3>
           <el-tag size="small" type="success" effect="plain" round>近7日</el-tag>
        </div>
        <div ref="messageDistChart" class="w-full h-80"></div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100">
      <h3 class="text-lg font-bold text-gray-900 mb-6">快捷管理</h3>
      <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        <router-link to="/admin/users" class="quick-link group">
          <div class="quick-icon bg-blue-50 text-blue-600 group-hover:scale-110">
            <el-icon size="20"><svg-icon name="icon-gerenzhongxin-zhihui" /></el-icon>
          </div>
          <span class="quick-text">用户管理</span>
        </router-link>
        
        <router-link to="/admin/sensitive" class="quick-link group">
          <div class="quick-icon bg-yellow-50 text-yellow-600 group-hover:scale-110">
            <el-icon size="20"><svg-icon name="icon-anquanbaozhang" /></el-icon>
          </div>
          <span class="quick-text">敏感词库</span>
        </router-link>
        
        <router-link to="/admin/reports" class="quick-link group">
          <div class="quick-icon bg-red-50 text-red-600 group-hover:scale-110">
             <el-icon size="20"><svg-icon name="icon-jubao" /></el-icon>
          </div>
          <span class="quick-text">举报处理</span>
        </router-link>
        
        <router-link to="/admin/groups" class="quick-link group">
          <div class="quick-icon bg-purple-50 text-purple-600 group-hover:scale-110">
            <el-icon size="20"><svg-icon name="icon-xiaoxi-zhihui" /></el-icon>
          </div>
          <span class="quick-text">群组审计</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stat-card {
  @apply bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex items-center justify-between transition-all hover:shadow-md cursor-pointer;
}

.stat-label {
  @apply text-sm text-gray-500 mb-1 font-medium;
}

.stat-value {
  @apply text-3xl font-bold;
}

.stat-icon {
  @apply w-12 h-12 rounded-xl flex items-center justify-center transition-all duration-300;
}

.chart-card {
  @apply bg-white p-6 rounded-2xl shadow-sm border border-gray-100;
}

.chart-header {
  @apply flex items-center justify-between mb-6;
}

.chart-title {
  @apply text-lg font-bold text-gray-900;
}

.quick-link {
  @apply flex flex-col items-center gap-3 p-6 rounded-xl border border-gray-100 bg-gray-50 hover:bg-white hover:shadow-md transition-all cursor-pointer;
}

.quick-icon {
  @apply w-12 h-12 rounded-full flex items-center justify-center transition-transform duration-300;
}

.quick-text {
  @apply text-gray-600 font-medium group-hover:text-gray-900;
}
</style>
