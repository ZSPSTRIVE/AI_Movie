<template>
  <div class="movie-card">
    <div class="movie-cover" @click="handleClick">
      <img 
        :src="coverUrl" 
        :alt="movie.title"
        @error="handleImageError"
      />
      <div class="movie-rating" v-if="movie.rating">
        <span class="rating-icon">⭐</span>
        <span class="rating-value">{{ movie.rating.toFixed(1) }}</span>
      </div>
      <div class="movie-quality" v-if="playEntry?.quality">
        {{ playEntry.quality }}
      </div>
    </div>
    
    <div class="movie-info">
      <h3 class="movie-title" @click="handleClick">{{ movie.title }}</h3>
      
      <div class="movie-meta">
        <span v-if="movie.year" class="meta-item">{{ movie.year }}</span>
        <span v-if="movie.region" class="meta-item">{{ movie.region }}</span>
        <span v-if="movie.genres?.length" class="meta-item">
          {{ movie.genres.slice(0, 2).join('/') }}
        </span>
      </div>
      
      <div class="movie-actions">
        <button 
          v-if="playEntry?.verified" 
          class="play-btn primary"
          @click="handlePlay"
        >
          <span class="play-icon">▶</span>
          立即播放
        </button>
        <button 
          class="detail-btn"
          @click="handleDetail"
        >
          查看详情
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { MovieInfo, PlayEntry } from '@/api/ai'

const props = defineProps<{
  movie: MovieInfo
  playEntry?: PlayEntry
}>()

const router = useRouter()
const emit = defineEmits<{
  (e: 'play', movie: MovieInfo): void
  (e: 'detail', movie: MovieInfo): void
}>()

// 封面图处理
const coverUrl = computed(() => {
  if (props.movie.coverUrl) {
    return props.movie.coverUrl
  }
  // 默认占位图
  return `https://via.placeholder.com/300x400/1a1a2e/eee?text=${encodeURIComponent(props.movie.title || 'Movie')}`
})

// 图片加载失败处理
function handleImageError(e: Event) {
  const target = e.target as HTMLImageElement
  target.src = `https://via.placeholder.com/300x400/1a1a2e/eee?text=${encodeURIComponent(props.movie.title || 'Movie')}`
}

// 点击卡片
function handleClick() {
  handleDetail()
}

// 播放
function handlePlay() {
  emit('play', props.movie)
  
  if (props.playEntry?.type === 'route' && props.playEntry.value) {
    router.push(props.playEntry.value)
  } else if (props.playEntry?.type === 'url' && props.playEntry.value) {
    window.open(props.playEntry.value, '_blank')
  } else {
    // 默认跳转到详情页播放
    router.push(`/film/${props.movie.movieId}`)
  }
}

// 查看详情
function handleDetail() {
  emit('detail', props.movie)
  router.push(`/film/${props.movie.movieId}`)
}
</script>

<style scoped>
.movie-card {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: linear-gradient(135deg, rgba(30, 30, 50, 0.9), rgba(20, 20, 40, 0.95));
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  transition: all 0.3s ease;
}

.movie-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
  border-color: rgba(100, 140, 255, 0.3);
}

.movie-cover {
  position: relative;
  flex-shrink: 0;
  width: 120px;
  height: 170px;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
}

.movie-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.movie-cover:hover img {
  transform: scale(1.05);
}

.movie-rating {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.7);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #FFD700;
}

.movie-quality {
  position: absolute;
  bottom: 8px;
  left: 8px;
  padding: 2px 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  color: white;
  text-transform: uppercase;
}

.movie-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.movie-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  transition: color 0.2s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.movie-title:hover {
  color: #818cf8;
}

.movie-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-item {
  padding: 2px 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.movie-actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
}

.play-btn,
.detail-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.play-btn.primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
}

.play-btn.primary:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.4);
}

.play-icon {
  font-size: 12px;
}

.detail-btn {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.detail-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: white;
}

/* 响应式 */
@media (max-width: 480px) {
  .movie-card {
    flex-direction: column;
    text-align: center;
  }
  
  .movie-cover {
    width: 100%;
    height: 200px;
    margin: 0 auto;
  }
  
  .movie-meta {
    justify-content: center;
  }
  
  .movie-actions {
    justify-content: center;
  }
}
</style>
