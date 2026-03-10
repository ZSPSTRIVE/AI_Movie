<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getCategoryList } from '@/api/film'
import tvboxService from '@/services/tvboxService'
import { normalizeImageUrl } from '@/utils/image'
import type { Film, Category, FilmQuery } from '@/types/film'

const router = useRouter()
const route = useRoute()

const filmList = ref<Film[]>([])
const categoryList = ref<Category[]>([])
const loading = ref(true)
const total = ref(0)

const initialSort = computed(() => {
  const sortParam = route.query.sort as string
  if (['hot', 'new', 'rating'].includes(sortParam)) {
    return sortParam
  }
  return 'hot'
})

const query = ref<FilmQuery>({
  pageNum: 1,
  pageSize: 18,
  categoryId: undefined,
  year: undefined,
  region: undefined,
  sort: initialSort.value
})

const yearOptions = [2024, 2023, 2022, 2021, 2020, 2019, 2018]
const regionOptions = ['中国大陆', '中国香港', '中国台湾', '美国', '日本', '韩国', '英国', '法国']
const sortOptions = [
  { label: '最热', value: 'hot' },
  { label: '最新', value: 'new' },
  { label: '评分', value: 'rating' }
]

watch(() => route.query.sort, (newSort) => {
  if (newSort && ['hot', 'new', 'rating'].includes(newSort as string)) {
    query.value.sort = newSort as string
  }
})

onMounted(async () => {
  getCategoryList().then(res => {
    categoryList.value = res.data || []
  }).catch(() => {})
  await fetchFilms()
})

watch(query, () => {
  query.value.pageNum = 1
  fetchFilms()
}, { deep: true })

async function fetchFilms() {
  loading.value = true
  try {
    // 使用与首页相同的数据源 - getRecommend
    const films = await tvboxService.getRecommend(100)
    
    if (films && films.length > 0) {
      let filteredList = [...films]
      
      // 按分类过滤
      if (query.value.categoryId) {
        const matchCat = categoryList.value.find(c => c.id === query.value.categoryId)
        if (matchCat) {
          filteredList = filteredList.filter(f => 
            f.categoryId === query.value.categoryId ||
            f.genre === matchCat.name ||
            f.tags?.includes(matchCat.name)
          )
        }
      }
      // 按年份过滤
      if (query.value.year) {
        filteredList = filteredList.filter(f => f.year === query.value.year)
      }
      // 按地区过滤
      if (query.value.region) {
        filteredList = filteredList.filter(f => f.region?.includes(query.value.region!))
      }
      
      // 排序
      if (query.value.sort === 'rating') {
        filteredList.sort((a, b) => (b.rating || 0) - (a.rating || 0))
      } else if (query.value.sort === 'new') {
        filteredList.sort((a, b) => (b.year || 0) - (a.year || 0))
      } else {
        filteredList.sort((a, b) => (b.playCount || 0) - (a.playCount || 0))
      }
      
      // 分页
      const pageNum = query.value.pageNum || 1
      const pageSize = query.value.pageSize || 18
      const startIndex = (pageNum - 1) * pageSize
      const pagedList = filteredList.slice(startIndex, startIndex + pageSize)
      
      filmList.value = pagedList.map((film) => ({
        ...film,
        coverUrl: normalizeImageUrl(film.coverUrl, film.title),
      }))
      total.value = filteredList.length
    } else {
      filmList.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取数据失败:', error)
    filmList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  query.value.pageNum = page
  fetchFilms()
}

function goToDetail(id: string | number) {
  router.push(`/film/${id}`)
}

function formatPlayCount(count: number): string {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万'
  }
  return count.toString()
}
</script>

<template>
  <div class="space-y-6">
    <h1 class="glass-section-title">电影库</h1>

    <!-- 筛选栏 -->
    <div class="glass-filter-bar">
      <div class="filter-row">
        <span class="filter-label">分类:</span>
        <div class="filter-options">
          <button class="filter-option" :class="{ active: !query.categoryId }" @click="query.categoryId = undefined">全部</button>
          <button v-for="cat in categoryList" :key="cat.id" class="filter-option" :class="{ active: query.categoryId === cat.id }" @click="query.categoryId = cat.id">{{ cat.name }}</button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">年份:</span>
        <div class="filter-options">
          <button class="filter-option" :class="{ active: !query.year }" @click="query.year = undefined">全部</button>
          <button v-for="year in yearOptions" :key="year" class="filter-option" :class="{ active: query.year === year }" @click="query.year = year">{{ year }}</button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">地区:</span>
        <div class="filter-options">
          <button class="filter-option" :class="{ active: !query.region }" @click="query.region = undefined">全部</button>
          <button v-for="region in regionOptions" :key="region" class="filter-option" :class="{ active: query.region === region }" @click="query.region = region">{{ region }}</button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">排序:</span>
        <div class="filter-options">
          <button v-for="opt in sortOptions" :key="opt.value" class="filter-option" :class="{ active: query.sort === opt.value }" @click="query.sort = opt.value">{{ opt.label }}</button>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
      <div v-for="i in 18" :key="i" class="aspect-[2/3] rounded-xl skeleton-loading" />
    </div>

    <!-- 空状态 -->
    <div v-else-if="filmList.length === 0" class="text-center py-20">
      <div class="text-6xl mb-6">🎬</div>
      <div class="text-lg font-medium" style="color: var(--glass-text);">暂无电影</div>
    </div>

    <!-- 电影列表 -->
    <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
      <div v-for="film in filmList" :key="film.id" class="cursor-pointer group" @click="goToDetail(film.id)">
        <div class="relative aspect-[2/3] rounded-xl overflow-hidden transition-all duration-300 film-card">
          <img :src="film.coverUrl" :alt="film.title" v-img-fallback="film.title" loading="lazy" class="w-full h-full object-cover" />
          <div class="absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <div class="absolute bottom-0 left-0 right-0 p-3">
              <p class="text-xs text-white/90 font-medium line-clamp-3 leading-relaxed">{{ film.description }}</p>
            </div>
          </div>
          <div class="absolute top-2 left-2 px-2 py-1 text-xs font-bold rounded" style="background: rgba(0, 0, 0, 0.6); backdrop-filter: blur(8px); color: #fbbf24;">{{ film.rating }}</div>
          <div v-if="film.isVip" class="absolute top-2 right-2 px-2 py-0.5 text-xs font-bold rounded" style="background: linear-gradient(135deg, #f59e0b, #d97706); color: white;">VIP</div>
        </div>
        <div class="mt-2 px-0.5">
          <h3 class="text-sm font-bold truncate" style="color: #0f172a;">{{ film.title }}</h3>
          <p class="text-xs font-medium mt-0.5" style="color: #94a3b8;">{{ formatPlayCount(film.playCount) }}次</p>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="flex justify-center mt-8">
      <el-pagination v-model:current-page="query.pageNum" :page-size="query.pageSize" :total="total" layout="prev, pager, next" background @current-change="handlePageChange" />
    </div>
  </div>
</template>

<style scoped>
/* 页面标题 */
.glass-section-title {
  font-family: 'Inter', 'DM Sans', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Source Han Sans SC', sans-serif;
  font-size: 1.75rem;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 1rem;
  letter-spacing: -0.02em;
}

/* 筛选栏容器 */
.glass-filter-bar {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95) 0%, rgba(248, 250, 252, 0.9) 100%);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 20px 24px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04), 0 1px 3px rgba(0, 0, 0, 0.02);
  margin-bottom: 24px;
}

/* 筛选行 */
.filter-row {
  display: flex;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(226, 232, 240, 0.5);
}
.filter-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.filter-row:first-child {
  padding-top: 0;
}

/* 筛选标签 */
.filter-label {
  font-family: 'Inter', 'DM Sans', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Source Han Sans SC', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #64748b !important;
  min-width: 56px;
  padding-top: 6px;
  flex-shrink: 0;
}

/* 筛选选项容器 */
.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}

/* 筛选选项按钮 */
.filter-option {
  font-family: 'Inter', 'DM Sans', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Source Han Sans SC', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: #475569 !important;
  background: rgba(241, 245, 249, 0.8);
  border: 1px solid transparent;
  padding: 6px 14px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.filter-option:hover {
  background: rgba(226, 232, 240, 0.9);
  color: #334155 !important;
  transform: translateY(-1px);
}
.filter-option.active {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: white;
  border-color: transparent;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

/* 骨架屏加载 */
.skeleton-loading {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s ease-in-out infinite;
}
@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 电影卡片 */
.film-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.6);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}
.group:hover .film-card {
  transform: translateY(-6px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.12);
  border-color: rgba(99, 102, 241, 0.3);
}

/* 响应式 */
@media (max-width: 768px) {
  .glass-filter-bar {
    padding: 16px;
    border-radius: 12px;
  }
  .filter-label {
    min-width: 48px;
    font-size: 13px;
  }
  .filter-option {
    font-size: 12px;
    padding: 5px 12px;
  }
  .glass-section-title {
    font-size: 1.5rem;
  }
}
</style>

