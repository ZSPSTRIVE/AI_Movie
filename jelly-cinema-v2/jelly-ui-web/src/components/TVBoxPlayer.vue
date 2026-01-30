<template>
  <div class="tvbox-player">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading" :size="48"><Loading /></el-icon>
      <p class="loading-text">正在加载播放源...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error && !currentPlayUrl" class="error-container">
      <el-icon :size="48" color="#f56c6c"><WarningFilled /></el-icon>
      <p class="error-text">{{ error }}</p>
      <el-button class="mt-4" type="primary" @click="retry">重试</el-button>
    </div>

    <!-- 播放器区域 -->
    <div v-else class="player-wrapper">
      <!-- 视频播放器 -->
      <div class="video-container">
        <video
          ref="videoRef"
          controls
          playsinline
          class="video-player"
          :poster="!isPlaying ? poster : ''"
          crossorigin="anonymous"
          @playing="isPlaying = true"
          @pause="isPlaying = false"
          @error="handleVideoError"
        >
          您的浏览器不支持视频播放
        </video>
        
        <!-- 视频加载中 -->
        <div v-if="videoLoading" class="video-overlay">
          <el-icon class="is-loading" :size="48"><Loading /></el-icon>
          <p>正在加载视频...</p>
        </div>
      </div>

      <!-- 剧集选择 -->
      <div v-if="episodes.length > 0" class="episodes-section">
        <div class="episodes-header">
          <h3 class="episodes-title">选集 (共{{ episodes.length }}集)</h3>
          <span class="current-playing" v-if="episodes.length > 1">
            正在播放: {{ episodes[currentEpisode]?.name || '第1集' }}
          </span>
        </div>
        <div class="episodes-grid">
          <div
            v-for="(episode, index) in episodes"
            :key="index"
            class="episode-item"
            :class="{ active: currentEpisode === index }"
            @click="switchEpisode(index)"
          >
            {{ episode.name }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import type { PropType } from 'vue'
import tvboxService from '@/services/tvboxService'
import Hls from 'hls.js'

const props = defineProps({
  filmId: {
    type: [String, Number] as PropType<string | number>,
    required: true,
  },
  poster: {
    type: String,
    default: '',
  },
})

const videoRef = ref<HTMLVideoElement | null>(null)
const loading = ref(true)
const videoLoading = ref(false)
const isPlaying = ref(false)
const error = ref('')
const currentPlayUrl = ref('')
const episodes = ref<Array<{ name: string; url: string }>>([])
const currentEpisode = ref(0)
let hlsInstance: Hls | null = null

onMounted(async () => {
  await loadPlayData()
})

onUnmounted(() => {
  destroyHls()
})

function destroyHls() {
  if (hlsInstance) {
    hlsInstance.destroy()
    hlsInstance = null
  }
}

async function loadPlayData() {
  loading.value = true
  error.value = ''
  
  try {
    const data = await tvboxService.getPlayUrl(props.filmId)
    console.log('Play data received:', data)
    
    if (!data.playUrl && (!data.episodes || data.episodes.length === 0)) {
      error.value = '暂无可用播放源'
      loading.value = false
      return
    }
    
    episodes.value = data.episodes || []
    currentPlayUrl.value = data.playUrl || (data.episodes?.[0]?.url || '')
    
    loading.value = false
    
    // 等待DOM更新后再设置播放器
    await nextTick()
    
    // 使用setTimeout确保video元素已渲染
    setTimeout(() => {
      if (currentPlayUrl.value) {
        initPlayer(currentPlayUrl.value)
      }
    }, 100)
    
  } catch (err: any) {
    console.error('Load play data error:', err)
    error.value = err.message || '加载播放链接失败'
    loading.value = false
  }
}

function initPlayer(url: string) {
  const video = videoRef.value
  if (!video) {
    console.error('Video element not ready, retrying...')
    setTimeout(() => initPlayer(url), 200)
    return
  }
  
  videoLoading.value = true
  error.value = ''
  destroyHls()
  
  console.log('Initializing player with URL:', url)
  
  // 判断是否是HLS流
  const isHlsStream = url.includes('.m3u8') || url.includes('/play/') || url.includes('index.m3u8')
  
  // 使用代理URL绕过CORS限制
  let playUrl = url
  if (isHlsStream && url.startsWith('http')) {
    playUrl = `http://localhost:3001/api/tvbox/m3u8?url=${encodeURIComponent(url)}`
    console.log('Using proxy URL:', playUrl)
  }
  
  if (isHlsStream && Hls.isSupported()) {
    console.log('Using HLS.js')
    
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
      backBufferLength: 90,
      maxBufferLength: 30,
      maxMaxBufferLength: 60,
    })
    
    hlsInstance.loadSource(playUrl)
    hlsInstance.attachMedia(video)
    
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      console.log('HLS manifest parsed')
      videoLoading.value = false
      video.play().catch(e => console.log('Auto-play blocked:', e.message))
    })
    
    hlsInstance.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        console.error('HLS fatal error:', data.type, data.details)
        videoLoading.value = false
        
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          // 网络错误，尝试重新加载
          setTimeout(() => hlsInstance?.startLoad(), 1000)
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hlsInstance?.recoverMediaError()
        } else {
          error.value = '播放失败，请尝试其他线路'
        }
      }
    })
    
    hlsInstance.on(Hls.Events.FRAG_LOADED, () => {
      videoLoading.value = false
    })
    
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari原生HLS支持
    console.log('Using native HLS (Safari)')
    video.src = url
    video.addEventListener('loadeddata', () => {
      videoLoading.value = false
      video.play().catch(console.error)
    }, { once: true })
    
  } else {
    // 普通视频格式
    console.log('Using standard video')
    video.src = url
    video.addEventListener('loadeddata', () => {
      videoLoading.value = false
      video.play().catch(console.error)
    }, { once: true })
  }
  
  video.addEventListener('error', () => {
    videoLoading.value = false
    if (!error.value) {
      error.value = '视频加载失败'
    }
  }, { once: true })
}

function switchEpisode(index: number) {
  if (index === currentEpisode.value) return
  
  currentEpisode.value = index
  const url = episodes.value[index]?.url
  if (url) {
    currentPlayUrl.value = url
    initPlayer(url)
  }
}

function handleVideoError() {
  videoLoading.value = false
  console.error('Video error occurred')
}

function retry() {
  error.value = ''
  loadPlayData()
}
</script>

<style scoped>
.tvbox-player {
  width: 100%;
}

.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  background: linear-gradient(135deg, #1e293b, #0f172a);
  border-radius: 16px;
  color: white;
}

.loading-text,
.error-text {
  margin-top: 16px;
  color: rgba(255, 255, 255, 0.7);
}

.player-wrapper {
  width: 100%;
}

.video-container {
  position: relative;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 16/9;
}

.video-player {
  width: 100%;
  height: 100%;
  display: block;
}

.video-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  gap: 16px;
}

/* 剧集区域 */
.episodes-section {
  margin-top: 20px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.episodes-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.episodes-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.current-playing {
  font-size: 13px;
  color: #0ea5e9;
  font-weight: 500;
}

.episodes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 10px;
  max-height: 200px;
  overflow-y: auto;
}

.episode-item {
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  text-align: center;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.episode-item:hover {
  background: rgba(14, 165, 233, 0.15);
  border-color: rgba(14, 165, 233, 0.3);
  color: #0ea5e9;
}

.episode-item.active {
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  border-color: transparent;
  color: white;
  box-shadow: 0 2px 8px rgba(14, 165, 233, 0.3);
}
</style>
