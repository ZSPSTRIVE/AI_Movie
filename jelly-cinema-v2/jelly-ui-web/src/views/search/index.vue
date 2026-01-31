<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchFilm } from '@/api/film'
import { normalizeImageUrl } from '@/utils/image'
import type { Film } from '@/types/film'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const filmList = ref<Film[]>([])
const loading = ref(false)

watch(() => route.query.keyword, (val) => {
  if (val) {
    keyword.value = val as string
    doSearch()
  }
}, { immediate: true })

async function doSearch() {
  if (!keyword.value.trim()) return
  
  loading.value = true
  try {
    const res = await searchFilm(keyword.value)
    filmList.value = (res.data || []).map((film) => ({
      ...film,
      coverUrl: normalizeImageUrl(film.coverUrl, film.title),
    }))
  } finally {
    loading.value = false
  }
}

function goToDetail(id: number) {
  router.push(`/film/${id}`)
}
</script>

<template>
  <div class="space-y-6">
    <!-- 标题 - Neo-Brutalism -->
    <div class="bg-pop-yellow border-3 border-black shadow-brutal rounded-2xl px-6 py-4 inline-block">
      <h1 class="text-3xl font-black text-black uppercase">
        🔍 搜索结果: <span class="text-pop-blue">{{ keyword }}</span>
      </h1>
    </div>

    <div v-if="loading" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
      <div v-for="i in 6" :key="i" class="aspect-[2/3] rounded-xl skeleton border-3 border-black" />
    </div>

    <div v-else-if="filmList.length === 0" class="text-center py-20">
      <div class="text-8xl mb-6">🤔</div>
      <div class="nb-badge text-lg">未找到与「{{ keyword }}」相关的内容</div>
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
            v-img-fallback="film.title"
            class="w-full h-full object-cover"
          />
          <div class="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent opacity-0 group-hover:opacity-100 transition-opacity">
            <div class="absolute bottom-0 left-0 right-0 p-3">
              <p class="text-sm text-white font-bold line-clamp-2">{{ film.description }}</p>
            </div>
          </div>
          <div class="absolute top-2 right-2 bg-pop-yellow text-black text-xs font-black px-2 py-1 border-2 border-black rounded">
            ⭐ {{ film.rating }}
          </div>
        </div>
        <h3 class="mt-3 text-black font-bold truncate">{{ film.title }}</h3>
      </div>
    </div>
  </div>
</template>
