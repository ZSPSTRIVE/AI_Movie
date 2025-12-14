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
    <!-- 页面标题 - Neo-Brutalism -->
    <div class="bg-pop-blue border-3 border-black shadow-brutal rounded-2xl px-6 py-4 inline-block">
      <h1 class="text-3xl font-black text-white uppercase">电影库</h1>
    </div>

    <!-- 筛选栏 - Neo-Brutalism -->
    <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6 space-y-4">
      <!-- 分类 -->
      <div class="flex items-start space-x-4">
        <span class="font-bold text-black w-16 py-1 shrink-0">分类:</span>
        <div class="flex flex-wrap gap-2">
          <button
            class="px-3 py-1 font-bold border-2 border-black rounded-lg transition-all"
            :class="!query.categoryId ? 'bg-pop-yellow shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.categoryId = undefined"
          >
            全部
          </button>
          <button
            v-for="cat in categoryList"
            :key="cat.id"
            class="px-3 py-1 font-bold border-2 border-black rounded-lg transition-all"
            :class="query.categoryId === cat.id ? 'bg-pop-yellow shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.categoryId = cat.id"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>

      <!-- 年份 -->
      <div class="flex items-start space-x-4">
        <span class="font-bold text-black w-16 py-1 shrink-0">年份:</span>
        <div class="flex flex-wrap gap-2">
          <button
            class="px-3 py-1 font-bold border-2 border-black rounded-lg transition-all"
            :class="!query.year ? 'bg-pop-green shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.year = undefined"
          >
            全部
          </button>
          <button
            v-for="year in yearOptions"
            :key="year"
            class="px-3 py-1 font-bold border-2 border-black rounded-lg transition-all"
            :class="query.year === year ? 'bg-pop-green shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.year = year"
          >
            {{ year }}
          </button>
        </div>
      </div>

      <!-- 地区 -->
      <div class="flex items-start space-x-4">
        <span class="font-bold text-black w-16 py-1 shrink-0">地区:</span>
        <div class="flex flex-wrap gap-2">
          <button
            class="px-3 py-1 font-bold border-2 border-black rounded-lg transition-all"
            :class="!query.region ? 'bg-pop-orange shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.region = undefined"
          >
            全部
          </button>
          <button
            v-for="region in regionOptions"
            :key="region"
            class="px-3 py-1 font-bold border-2 border-black rounded-lg transition-all"
            :class="query.region === region ? 'bg-pop-orange shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.region = region"
          >
            {{ region }}
          </button>
        </div>
      </div>

      <!-- 排序 -->
      <div class="flex items-center space-x-4">
        <span class="font-bold text-black w-16 shrink-0">排序:</span>
        <div class="flex gap-2">
          <button
            v-for="opt in sortOptions"
            :key="opt.value"
            class="px-4 py-2 font-bold border-2 border-black rounded-lg transition-all"
            :class="query.sort === opt.value ? 'bg-pop-purple text-white shadow-brutal-sm' : 'bg-white hover:bg-gray-100'"
            @click="query.sort = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- 电影列表 - Neo-Brutalism -->
    <div v-if="loading" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
      <div v-for="i in 18" :key="i" class="aspect-[2/3] rounded-xl skeleton border-3 border-black" />
    </div>

    <div v-else-if="filmList.length === 0" class="text-center py-20">
      <el-icon size="64" class="mb-6"><VideoCamera /></el-icon>
      <div class="nb-badge text-lg">暂无电影</div>
    </div>

    <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
      <div
        v-for="film in filmList"
        :key="film.id"
        class="cursor-pointer group"
        @click="goToDetail(film.id)"
      >
        <div class="relative aspect-[2/3] rounded-xl overflow-hidden bg-white border-3 border-black shadow-brutal-sm transition-all group-hover:translate-x-1 group-hover:-translate-y-1 group-hover:shadow-brutal">
          <img
            :src="film.coverUrl"
            :alt="film.title"
            class="w-full h-full object-cover"
          />
          <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent opacity-0 group-hover:opacity-100 transition-opacity">
            <div class="absolute bottom-0 left-0 right-0 p-3">
              <p class="text-sm text-white font-bold line-clamp-2">{{ film.description }}</p>
            </div>
          </div>
          <div class="absolute top-2 right-2 bg-pop-yellow text-black text-xs font-black px-2 py-1 border-2 border-black rounded">
            {{ film.rating }}
          </div>
        </div>
        <h3 class="mt-3 text-black font-bold truncate">{{ film.title }}</h3>
        <p class="text-nb-text-sub text-sm font-semibold">{{ formatPlayCount(film.playCount) }}次播放</p>
      </div>
    </div>

    <!-- 分页 - Neo-Brutalism -->
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
</template>
