<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get, post, put, del } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

interface HomepageContent {
  id: number
  contentType: string
  sectionType: string
  tvboxId: string
  title: string
  coverUrl: string
  description: string
  sourceName: string
  rating: number
  year: number
  region: string
  actors: string
  director: string
  sortOrder: number
  aiScore: number
  aiReason: string
  status: number
  createTime: string
}

const loading = ref(false)
const refreshLoading = ref(false)
const aiSortLoading = ref(false)
const tableData = ref<HomepageContent[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// 筛选
const contentType = ref('')
const sectionType = ref('')

const contentTypeOptions = [
  { label: '全部', value: '' },
  { label: '电影', value: 'movie' },
  { label: '电视剧', value: 'tv_series' },
  { label: '综艺', value: 'variety' },
  { label: '动漫', value: 'anime' }
]

const sectionTypeOptions = [
  { label: '全部', value: '' },
  { label: '推荐', value: 'recommend' },
  { label: '热门', value: 'hot' },
  { label: '新上线', value: 'new' },
  { label: '趋势', value: 'trending' }
]

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await get('/admin/homepage/list', {
      contentType: contentType.value || undefined,
      sectionType: sectionType.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    tableData.value = res.data?.rows || []
    total.value = Number(res.data?.total) || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  pageNum.value = 1
  loadData()
}

async function handleRefresh() {
  await ElMessageBox.confirm(
    '确定要刷新首页内容吗？这将从TVBox采集源获取最新数据。',
    '刷新确认',
    { type: 'warning' }
  )
  
  refreshLoading.value = true
  try {
    await post('/admin/homepage/refresh')
    ElMessage.success('刷新任务已启动，请稍后刷新查看结果')
    setTimeout(loadData, 3000)
  } catch (error: any) {
    ElMessage.error(error.message || '刷新失败')
  } finally {
    refreshLoading.value = false
  }
}

async function handleAiSort() {
  await ElMessageBox.confirm(
    '确定要进行AI智能排序吗？这将调用大模型API分析并重新排序内容。',
    'AI排序确认',
    { type: 'info' }
  )
  
  aiSortLoading.value = true
  try {
    await post('/admin/homepage/ai-sort')
    ElMessage.success('AI排序任务已启动，请稍后刷新查看结果')
    setTimeout(loadData, 5000)
  } catch (error: any) {
    ElMessage.error(error.message || 'AI排序失败')
  } finally {
    aiSortLoading.value = false
  }
}

async function toggleStatus(row: HomepageContent) {
  try {
    await put(`/admin/homepage/${row.id}/toggle-status`)
    row.status = row.status === 1 ? 0 : 1
    ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function updateSort(row: HomepageContent, direction: 'up' | 'down') {
  const newOrder = direction === 'up' ? row.sortOrder - 1 : row.sortOrder + 1
  if (newOrder < 0) return
  
  try {
    await put(`/admin/homepage/${row.id}/sort?sortOrder=${newOrder}`)
    row.sortOrder = newOrder
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '排序失败')
  }
}

async function handleDelete(row: HomepageContent) {
  await ElMessageBox.confirm('确定要删除该内容吗？', '提示', { type: 'warning' })
  try {
    await del(`/admin/homepage/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

function getContentTypeLabel(type: string) {
  const map: Record<string, string> = {
    movie: '电影', tv_series: '电视剧', variety: '综艺', anime: '动漫'
  }
  return map[type] || type
}

function getSectionTypeLabel(type: string) {
  const map: Record<string, string> = {
    recommend: '推荐', hot: '热门', new: '新上线', trending: '趋势'
  }
  return map[type] || type
}
</script>

<template>
  <div class="h-full flex flex-col gap-6 p-6 bg-gray-50">
    <!-- 顶部操作栏 -->
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex flex-col md:flex-row justify-between items-center gap-4 animate-fade-in-down">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-orange-50 flex items-center justify-center text-orange-600">
          <el-icon size="24"><svg-icon name="icon-shouye-zhihui" /></el-icon>
        </div>
        <div>
          <h2 class="text-2xl font-bold text-gray-900 tracking-wide">首页内容管理</h2>
          <p class="text-gray-500 text-sm mt-1">管理和排序首页展示的影视内容</p>
        </div>
      </div>
      
      <div class="flex gap-3">
        <el-button 
          :loading="refreshLoading"
          plain
          class="!rounded-xl"
          @click="handleRefresh"
        >
          <el-icon class="mr-2"><Refresh /></el-icon>
          刷新内容库
        </el-button>
        <el-button 
          type="primary"
          :loading="aiSortLoading"
          class="!rounded-xl !font-bold bg-gradient-to-r from-purple-600 to-indigo-600 border-none hover:opacity-90 transition-opacity"
          @click="handleAiSort"
        >
          <el-icon class="mr-2"><svg-icon name="icon-zhuanshuguwen" /></el-icon>
          AI 智能排序
        </el-button>
      </div>
    </div>

    <!-- 筛选与数据区 -->
    <div class="bg-white flex-1 rounded-2xl shadow-sm border border-gray-100 flex flex-col overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
      <!-- 筛选栏 -->
      <div class="p-5 border-b border-gray-100 flex gap-4 bg-gray-50/50">
        <el-select v-model="contentType" placeholder="全部内容" clearable class="w-40" @change="handleFilter">
          <template #prefix><el-icon><Film /></el-icon></template>
          <el-option v-for="item in contentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="sectionType" placeholder="全部板块" clearable class="w-40" @change="handleFilter">
          <template #prefix><el-icon><Menu /></el-icon></template>
          <el-option v-for="item in sectionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="loadData">
          <el-icon><Search /></el-icon>
        </el-button>
      </div>

      <!-- 数据表格 -->
      <div class="flex-1 overflow-hidden p-4">
        <el-table 
          :data="tableData" 
          v-loading="loading" 
          height="100%"
          style="width: 100%"
          :row-style="{ height: '88px' }"
        >
          <el-table-column prop="sortOrder" label="排序" width="80" align="center">
            <template #default="{ row }">
              <div class="flex flex-col items-center gap-1 group">
                <el-button link size="small" class="!text-gray-400 hover:!text-blue-500 opacity-0 group-hover:opacity-100 transition-all" @click="updateSort(row, 'up')">
                  <el-icon><ArrowUp /></el-icon>
                </el-button>
                <span class="font-mono text-lg font-bold text-gray-700">{{ row.sortOrder }}</span>
                <el-button link size="small" class="!text-gray-400 hover:!text-blue-500 opacity-0 group-hover:opacity-100 transition-all" @click="updateSort(row, 'down')">
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="影片信息" min-width="320">
            <template #default="{ row }">
              <div class="flex items-center gap-4 py-1 group cursor-pointer hover:translate-x-1 transition-transform duration-300">
                <div class="relative w-14 h-20 rounded overflow-hidden shadow border border-gray-200 group-hover:shadow-md transition-all">
                  <el-image 
                    :src="row.coverUrl" 
                    class="w-full h-full object-cover"
                    loading="lazy"
                  >
                    <template #error>
                      <div class="flex items-center justify-center w-full h-full bg-gray-100 text-gray-400">
                        <el-icon><svg-icon name="icon-shangchuantupian" /></el-icon>
                      </div>
                    </template>
                  </el-image>
                </div>
                
                <div class="flex-1 min-w-0 flex flex-col gap-1">
                  <div class="text-base font-bold text-gray-900 truncate group-hover:text-blue-600 transition-colors">{{ row.title }}</div>
                  <div class="flex items-center gap-2 text-xs text-gray-500">
                    <span class="bg-gray-100 px-1.5 py-0.5 rounded text-gray-600">{{ row.year }}</span>
                    <span>{{ row.region }}</span>
                    <span class="text-blue-500">{{ row.sourceName }}</span>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="分类" width="100">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ getContentTypeLabel(row.contentType) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="板块" width="100">
            <template #default="{ row }">
              <el-tag type="warning" effect="plain">{{ getSectionTypeLabel(row.sectionType) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="rating" label="评分" width="100" align="center">
            <template #default="{ row }">
              <div class="flex items-center justify-center gap-1 font-bold text-orange-500 text-lg">
                <span>{{ row.rating }}</span>
                <el-icon size="14"><svg-icon name="icon-xihuan" /></el-icon>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="AI 推荐值" width="120" align="center">
            <template #default="{ row }">
              <div v-if="row.aiScore" class="flex flex-col items-center">
                <span class="text-lg font-bold text-purple-600">{{ row.aiScore }}</span>
                <el-tooltip v-if="row.aiReason" :content="row.aiReason" placement="top">
                   <div class="text-xs text-gray-400 flex items-center gap-1 cursor-help hover:text-gray-600">
                     Why? <el-icon><svg-icon name="icon-bangzhuwendang" /></el-icon>
                   </div>
                </el-tooltip>
              </div>
              <span v-else class="text-gray-300">-</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
                 {{ row.status === 1 ? '上架' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="{ row }">
              <div class="flex items-center justify-center gap-2">
                <el-tooltip :content="row.status === 1 ? '下架' : '上架'" placement="top">
                   <el-button circle size="small" :type="row.status === 1 ? 'warning' : 'success'" plain @click="toggleStatus(row)">
                     <el-icon><VideoPause v-if="row.status === 1" /><VideoPlay v-else /></el-icon>
                   </el-button>
                </el-tooltip>
                
                <el-popconfirm title="确定要删除这条记录吗?" @confirm="handleDelete(row)">
                  <template #reference>
                    <el-button circle size="small" type="danger" plain>
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="p-4 border-t border-gray-100 flex justify-end">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @change="loadData"
          background
        />
      </div>
    </div>
  </div>
</template>
