<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Film } from '@/types/film'
import tvboxService from '@/services/tvboxService'
import { normalizeImageUrl } from '@/utils/image'

const route = useRoute()

const filmId = computed(() => route.params.id as string)

const loading = ref(true)
const error = ref('')
const film = ref<Film | null>(null)

async function loadDetail() {
  loading.value = true
  error.value = ''

  try {
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
        <TVBoxPlayer :film-id="filmId" :poster="film.coverUrl" />
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
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
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
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
}

.detail-cover {
  width: 220px;
  height: 320px;
  object-fit: cover;
  border-radius: 14px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.detail-meta {
  min-width: 0;
}

.detail-title {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  margin: 0;
}

.detail-sub {
  margin-top: 8px;
  color: rgba(15, 23, 42, 0.6);
  font-weight: 600;
}

.detail-desc {
  margin-top: 14px;
  color: rgba(15, 23, 42, 0.75);
  line-height: 1.6;
}

.detail-tags {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(14, 165, 233, 0.12);
  color: #0ea5e9;
  border: 1px solid rgba(14, 165, 233, 0.18);
}

.detail-player {
  border-radius: 18px;
  overflow: hidden;
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
