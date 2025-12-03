<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRecommendFilm, getHotRank } from '@/api/film'
import type { Film } from '@/types/film'

const router = useRouter()

const recommendList = ref<Film[]>([])
const hotRankList = ref<Film[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [recRes, hotRes] = await Promise.all([
      getRecommendFilm(12),
      getHotRank(10)
    ])
    recommendList.value = recRes.data || []
    hotRankList.value = hotRes.data || []
  } finally {
    loading.value = false
  }
})

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
  <div class="space-y-8">
    <!-- Hero Banner - Neo-Brutalism -->
    <section class="relative h-80 border-3 border-black shadow-brutal rounded-2xl overflow-hidden bg-pop-blue">
      <div class="absolute inset-0 flex items-center justify-center">
        <div class="text-center animate-bounce-in">
          <div class="inline-block bg-pop-yellow border-3 border-black shadow-brutal rounded-xl px-8 py-4 mb-6 -rotate-2">
            <h1 class="text-4xl font-black text-black uppercase">发现精彩影视内容</h1>
          </div>
          <div class="bg-white border-3 border-black rounded-xl px-6 py-3 inline-block rotate-1">
            <p class="text-lg font-bold text-black">海量电影、电视剧、综艺，尽在果冻影院</p>
          </div>
        </div>
      </div>
      <!-- 装饰图形 -->
      <div class="absolute top-4 left-4 w-12 h-12 bg-pop-pink border-2 border-black rounded-full" />
      <div class="absolute bottom-4 right-4 w-16 h-16 bg-pop-green border-2 border-black rotate-12" />
    </section>

    <!-- 推荐电影 - Neo-Brutalism -->
    <section>
      <div class="flex items-center justify-between mb-6">
        <div class="bg-pop-orange border-3 border-black rounded-xl px-4 py-2 inline-block">
          <h2 class="text-2xl font-black text-black uppercase">为你推荐</h2>
        </div>
        <router-link to="/film" class="font-bold text-black hover:text-pop-blue underline decoration-3 underline-offset-4">
          查看全部 →
        </router-link>
      </div>

      <div v-if="loading" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div v-for="i in 6" :key="i" class="aspect-[2/3] rounded-xl skeleton border-3 border-black" />
      </div>

      <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
        <div
          v-for="film in recommendList"
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
            <!-- 评分 - Neo-Brutalism -->
            <div class="absolute top-2 right-2 bg-pop-yellow text-black text-xs font-black px-2 py-1 border-2 border-black rounded">
              ⭐ {{ film.rating }}
            </div>
          </div>
          <h3 class="mt-3 text-black font-bold truncate">{{ film.title }}</h3>
          <p class="text-nb-text-sub text-sm font-semibold">{{ formatPlayCount(film.playCount) }}次播放</p>
        </div>
      </div>
    </section>

    <!-- 热门榜单 - Neo-Brutalism -->
    <section class="grid md:grid-cols-3 gap-6">
      <!-- 热播榜 -->
      <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
        <div class="bg-pop-red border-3 border-black rounded-xl px-4 py-2 inline-flex items-center mb-4">
          <el-icon class="mr-2 text-white"><TrendCharts /></el-icon>
          <h3 class="text-xl font-black text-white uppercase">热播榜</h3>
        </div>
        <div class="space-y-3">
          <div
            v-for="(film, index) in hotRankList.slice(0, 5)"
            :key="film.id"
            class="flex items-center space-x-3 cursor-pointer p-2 rounded-lg border-2 border-transparent hover:border-black hover:bg-pop-yellow/20 transition-all"
            @click="goToDetail(film.id)"
          >
            <span
              class="w-8 h-8 rounded-lg flex items-center justify-center text-sm font-black border-2 border-black"
              :class="index < 3 ? 'bg-pop-red text-white' : 'bg-gray-100 text-black'"
            >
              {{ index + 1 }}
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-black font-bold truncate">{{ film.title }}</p>
              <p class="text-nb-text-sub text-sm">{{ film.rating }}分</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 新上线 -->
      <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
        <div class="bg-pop-blue border-3 border-black rounded-xl px-4 py-2 inline-flex items-center mb-4">
          <el-icon class="mr-2 text-white"><Calendar /></el-icon>
          <h3 class="text-xl font-black text-white uppercase">新上线</h3>
        </div>
        <div class="space-y-3">
          <div
            v-for="(film, index) in recommendList.slice(0, 5)"
            :key="film.id"
            class="flex items-center space-x-3 cursor-pointer p-2 rounded-lg border-2 border-transparent hover:border-black hover:bg-pop-blue/20 transition-all"
            @click="goToDetail(film.id)"
          >
            <span class="w-8 h-8 rounded-lg flex items-center justify-center text-sm font-black bg-gray-100 text-black border-2 border-black">
              {{ index + 1 }}
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-black font-bold truncate">{{ film.title }}</p>
              <p class="text-nb-text-sub text-sm">{{ film.year }}年</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 高分推荐 -->
      <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
        <div class="bg-pop-yellow border-3 border-black rounded-xl px-4 py-2 inline-flex items-center mb-4">
          <el-icon class="mr-2 text-black"><Star /></el-icon>
          <h3 class="text-xl font-black text-black uppercase">高分推荐</h3>
        </div>
        <div class="space-y-3">
          <div
            v-for="(film, index) in hotRankList.slice(5, 10)"
            :key="film.id"
            class="flex items-center space-x-3 cursor-pointer p-2 rounded-lg border-2 border-transparent hover:border-black hover:bg-pop-yellow/20 transition-all"
            @click="goToDetail(film.id)"
          >
            <span class="w-8 h-8 rounded-lg flex items-center justify-center text-sm font-black bg-gray-100 text-black border-2 border-black">
              {{ index + 1 }}
            </span>
            <div class="flex-1 min-w-0">
              <p class="text-black font-bold truncate">{{ film.title }}</p>
              <p class="text-nb-text-sub text-sm">{{ film.rating }}分</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
