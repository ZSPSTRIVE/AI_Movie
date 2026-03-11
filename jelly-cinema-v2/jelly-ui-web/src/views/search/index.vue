<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchFilm } from '@/api/film'
import tvboxService from '@/services/tvboxService'
import { normalizeImageUrl } from '@/utils/image'
import type { Film } from '@/types/film'
import { ElMessage } from 'element-plus'

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
  const kw = keyword.value.trim()
  if (!kw) {
    filmList.value = []
    return
  }
  
  loading.value = true
  try {
    // 先尝试后端 API 搜索
    const res = await searchFilm(kw)
    let results = res.data || []
    
    // 后端无结果时 fallback 到 TVBox 搜索
    if (results.length === 0) {
      results = await tvboxService.search(kw)
    }
    
    filmList.value = results.map((film) => ({
      ...film,
      coverUrl: normalizeImageUrl(film.coverUrl, film.title),
    }))
  } catch (e: any) {
    // 后端异常时直接用 TVBox 搜索兜底
    try {
      const tvResults = await tvboxService.search(kw)
      filmList.value = tvResults.map((film) => ({
        ...film,
        coverUrl: normalizeImageUrl(film.coverUrl, film.title),
      }))
    } catch {
      filmList.value = []
      ElMessage.error(e?.message || '搜索失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}

function goToDetail(id: string | number) {
  router.push(`/film/${id}`)
}
</script>

<template>
  <div class="space-y-6">
    <!-- 标题 -->
    <div>
      <h1 class="text-2xl font-semibold" style="color: var(--text-primary);">
        搜索结果：<span style="color: var(--text-secondary);">{{ keyword }}</span>
      </h1>
    </div>

    <div v-if="loading" class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
      <div v-for="i in 6" :key="i" class="aspect-[2/3] rounded-xl skeleton-loading" />
    </div>

    <div v-else-if="filmList.length === 0" class="text-center py-20">
      <div class="text-4xl mb-6 font-light" style="color: var(--text-tertiary);">无结果</div>
      <div class="text-base" style="color: var(--text-secondary);">未找到与「{{ keyword }}」相关的内容</div>
    </div>

    <div v-else class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
      <div
        v-for="film in filmList"
        :key="film.id"
        class="cursor-pointer group stagger-item"
        @click="goToDetail(film.id)"
      >
        <div class="relative aspect-[2/3] rounded-xl overflow-hidden transition-all group-hover:-translate-y-1" style="background: var(--bg-card);">
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
          <div class="absolute top-2 right-2 text-xs font-medium px-2 py-1 rounded-md" style="background: rgba(0,0,0,0.6); color: #fff; backdrop-filter: blur(8px);">
            {{ film.rating }}
          </div>
        </div>
        <h3 class="mt-3 font-semibold truncate" style="color: var(--text-primary);">{{ film.title }}</h3>
      </div>
    </div>
  </div>
</template>
