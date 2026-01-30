<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getFilmList, getCategoryList } from '@/api/film'
import type { Film, Category, FilmQuery } from '@/types/film'

const router = useRouter()

const filmList = ref<Film[]>([])
const categoryList = ref<Category[]>([])
const loading = ref(true)
const total = ref(0)

const query = ref<FilmQuery>({
  pageNum: 1,
  pageSize: 18,
  categoryId: undefined,
  year: undefined,
  region: undefined,
  sort: 'hot'
})

const yearOptions = [2024, 2023, 2022, 2021, 2020, 2019, 2018]
const regionOptions = ['中国大陆', '中国香港', '中国台湾', '美国', '日本', '韩国', '英国', '法国']
const sortOptions = [
  { label: '最热', value: 'hot' },
  { label: '最新', value: 'new' },
  { label: '评分', value: 'rating' }
]

onMounted(async () => {
  const [catRes] = await Promise.all([
    getCategoryList(),
    fetchFilms()
  ])
  categoryList.value = catRes.data || []
})

watch(query, () => {
  query.value.pageNum = 1
  fetchFilms()
}, { deep: true })

async function fetchFilms() {
  loading.value = true
  try {
    const res = await getFilmList(query.value)
    filmList.value = res.data?.rows || []
    total.value = Number(res.data?.total) || 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  query.value.pageNum = page
  fetchFilms()
}

function goToDetail(id: number) {
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
    <!-- 页面标题 - Glassmorphism -->
    <h1 class="glass-section-title">电影库</h1>

    <!-- 筛选栏 - Glassmorphism -->
    <div class="glass-filter-bar">
      <!-- 分类 -->
      <div class="filter-row">
        <span class="filter-label">分类:</span>
        <div class="filter-options">
          <button
            class="filter-option"
            :class="{ active: !query.categoryId }"
            @click="query.categoryId = undefined"
          >
            全部
          </button>
          <button
            v-for="cat in categoryList"
            :key="cat.id"
            class="filter-option"
            :class="{ active: query.categoryId === cat.id }"
            @click="query.categoryId = cat.id"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>

      <!-- 年份 -->
      <div class="filter-row">
        <span class="filter-label">年份:</span>
        <div class="filter-options">
          <button
            class="filter-option"
            :class="{ active: !query.year }"
            @click="query.year = undefined"
          >
            全部
          </button>
          <button
            v-for="year in yearOptions"
            :key="year"
            class="filter-option"
            :class="{ active: query.year === year }"
            @click="query.year = year"
          >
            {{ year }}
          </button>
        </div>
      </div>

      <!-- 地区 -->
      <div class="filter-row">
        <span class="filter-label">地区:</span>
        <div class="filter-options">
          <button
            class="filter-option"
            :class="{ active: !query.region }"
            @click="query.region = undefined"
          >
            全部
          </button>
          <button
            v-for="region in regionOptions"
            :key="region"
            class="filter-option"
            :class="{ active: query.region === region }"
            @click="query.region = region"
          >
            {{ region }}
          </button>
        </div>
      </div>

      <!-- 排序 -->
      <div class="filter-row">
        <span class="filter-label">排序:</span>
        <div class="filter-options">
          <button
            v-for="opt in sortOptions"
            :key="opt.value"
            class="filter-option"
            :class="{ active: query.sort === opt.value }"
            @click="query.sort = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </div>


    <!-- 电影列表 - 6列竖版 -->
    <div v-if="loading" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
      <div v-for="i in 18" :key="i" class="aspect-[2/3] rounded-xl" style="background: linear-gradient(90deg, rgba(255,255,255,0.1) 25%, rgba(255,255,255,0.2) 50%, rgba(255,255,255,0.1) 75%); background-size: 200% 100%; animation: skeleton-loading 1.5s ease-in-out infinite;" />
    </div>

    <div v-else-if="filmList.length === 0" class="text-center py-20">
      <el-icon size="64" class="mb-6" style="color: var(--glass-text-muted);"><VideoCamera /></el-icon>
      <div class="text-lg font-medium" style="color: var(--glass-text);">暂无电影</div>
    </div>

    <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
      <div
        v-for="film in filmList"
        :key="film.id"
        class="cursor-pointer group"
        @click="goToDetail(film.id)"
      >
        <!-- 竖版海报卡片 -->
        <div class="relative aspect-[2/3] rounded-xl overflow-hidden transition-all duration-300 film-card">
          <img
            :src="film.coverUrl"
            :alt="film.title"
            class="w-full h-full object-cover"
          />
          <!-- 遮罩层 -->
          <div class="absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <div class="absolute bottom-0 left-0 right-0 p-3">
              <p class="text-xs text-white/90 font-medium line-clamp-3 leading-relaxed">{{ film.description }}</p>
            </div>
          </div>
          <!-- 评分 - 左上角 -->
          <div class="absolute top-2 left-2 px-2 py-1 text-xs font-bold rounded" style="background: rgba(0, 0, 0, 0.6); backdrop-filter: blur(8px); color: #fbbf24;">
            {{ film.rating }}
          </div>
          <!-- VIP 标签 - 右上角 -->
          <div v-if="film.isVip" class="absolute top-2 right-2 px-2 py-0.5 text-xs font-bold rounded" style="background: linear-gradient(135deg, #f59e0b, #d97706); color: white;">
            VIP
          </div>
        </div>
        <!-- 电影信息 -->
        <div class="mt-2 px-0.5">
          <h3 class="text-sm font-bold truncate" style="color: #0f172a; line-height: 1.3;">{{ film.title }}</h3>
          <p class="text-xs font-medium mt-0.5" style="color: #94a3b8;">{{ formatPlayCount(film.playCount) }}次</p>
        </div>
      </div>
    </div>

    <!-- 分页 -->
   <div class="flex justify-center mt-8">
      <el-pagination
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="handlePageChange"
      />
    </div>
    </div>
  </div><!-- .film-page -->
</template>

<style scoped>
/* 满屏布局 */
.film-page {
  width: 100%;
  min-height: 100vh;
}

.page-container {
  width: 100%;
  padding: 24px 20px;
}

@media (max-width: 768px) {
  .page-container {
    padding: 24px 12px;
  }
}

@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 竖版电影卡片 */
.film-card {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.group:hover .film-card {
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  border-color: rgba(255, 255, 255, 0.3);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .film-card {
    border-radius: 8px;
  }
}
</style>
