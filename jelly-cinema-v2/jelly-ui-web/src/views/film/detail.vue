<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Film } from '@/types/film'
import tvboxService from '@/services/tvboxService'
import { getFilmDetail } from '@/api/film'
import { normalizeImageUrl } from '@/utils/image'
import VideoPlayer from '@/components/VideoPlayer.vue'

const route = useRoute()

const filmId = computed(() => route.params.id as string)
const isNumericId = computed(() => /^\d+$/.test(filmId.value))

const loading = ref(true)
const error = ref('')
const film = ref<Film | null>(null)
const tvboxFallbackId = ref<string | null>(null)

const useLocalPlayer = computed(() => isNumericId.value && !!film.value?.videoUrl)
const useTvboxPlayer = computed(() => !useLocalPlayer.value && (!isNumericId.value || !!tvboxFallbackId.value))
const tvboxPlayerId = computed(() => {
  if (!isNumericId.value) return filmId.value
  return tvboxFallbackId.value || ''
})

async function loadDetail() {
  loading.value = true
  error.value = ''
  tvboxFallbackId.value = null

  try {
    if (isNumericId.value) {
      const res = await getFilmDetail(filmId.value)
      const data = res.data
      if (!data) {
        error.value = '电影不存在或已下架'
        film.value = null
        return
      }
      film.value = {
        ...data,
        coverUrl: normalizeImageUrl(data.coverUrl, data.title)
      }

      if (!film.value.videoUrl) {
        const searchResults = await tvboxService.search(film.value.title)
        if (searchResults && searchResults.length > 0) {
          tvboxFallbackId.value = String(searchResults[0].id)
        }
      }
    } else {
      const data = await tvboxService.getDetail(filmId.value)
      if (!data) {
        error.value = '电影不存在或已下架'
        film.value = null
        return
      }
      film.value = {
        ...data,
        coverUrl: normalizeImageUrl(data.coverUrl, data.title)
      }
    }
  } catch (e: any) {
    error.value = e?.message || '加载电影详情失败'
    film.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
watch(() => route.params.id, loadDetail)
</script>

<template>
  <div class="space-y-6">
    <div v-if="loading" class="detail-loading">
      <el-icon class="is-loading" :size="48"><Loading /></el-icon>
      <p class="mt-4">正在加载电影详情...</p>
    </div>

    <div v-else-if="error" class="detail-error">
      <el-icon :size="48" color="#ef4444"><WarningFilled /></el-icon>
      <p class="mt-4">{{ error }}</p>
      <el-button class="mt-4" type="primary" @click="loadDetail">重试</el-button>
    </div>

    <div v-else-if="film" class="detail-wrapper">
      <div class="detail-hero">
        <img
          :src="film.coverUrl"
          :alt="film.title"
          v-img-fallback="film.title"
          class="detail-cover"
        />

        <div class="detail-meta">
          <h1 class="detail-title">{{ film.title }}</h1>
          <div class="detail-sub">
            <span>{{ film.year }}</span>
            <span v-if="film.region">· {{ film.region }}</span>
            <span v-if="film.rating">· ⭐ {{ film.rating }}</span>
          </div>
          <p class="detail-desc">{{ film.description }}</p>
          <div class="detail-tags" v-if="film.tags && film.tags.length">
            <span v-for="(t, i) in film.tags" :key="i" class="tag">{{ t }}</span>
          </div>
        </div>
      </div>

      <div class="detail-player">
        <VideoPlayer v-if="useLocalPlayer" :src="film.videoUrl" :poster="film.coverUrl" />
        <TVBoxPlayer v-else-if="useTvboxPlayer" :film-id="tvboxPlayerId" :title="film.title" :poster="film.coverUrl" />
        <div v-else class="detail-player-empty">暂无可用播放源</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-loading,
.detail-error {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xl);
  background: var(--glass-bg-card);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(var(--glass-blur));
}

.detail-wrapper {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-hero {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 18px;
  padding: 18px;
  border-radius: var(--radius-xl);
  background: var(--glass-bg-card);
  border: 1px solid var(--border-color);
  backdrop-filter: blur(var(--glass-blur));
  box-shadow: var(--shadow-sm);
}

.detail-cover {
  width: 220px;
  height: 320px;
  object-fit: cover;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
}

.detail-meta {
  min-width: 0;
}

.detail-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.detail-sub {
  margin-top: 8px;
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 14px;
}

.detail-desc {
  margin-top: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 14px;
  font-weight: 400;
}

.detail-tags {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 6px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 500;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  border: 1px solid var(--color-primary-bg);
}

.detail-player {
  border-radius: var(--radius-xl);
  overflow: hidden;
  min-height: 320px;
  background: var(--bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-player-empty {
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 15px;
}

@media (max-width: 768px) {
  .detail-hero {
    grid-template-columns: 1fr;
  }

  .detail-cover {
    width: 100%;
    height: auto;
    aspect-ratio: 2 / 3;
  }
}
</style>
