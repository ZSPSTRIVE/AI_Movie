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
      <div class="flex gap-4 mt-4">
        <el-button type="primary" @click="retry">重试加载</el-button>
        <el-button type="warning" @click="switchSource" v-if="episodes.length > 0">切换线路</el-button>
      </div>
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
const failedUrls = ref<Set<string>>(new Set())
const isAutoSwitching = ref(false)
let hasReloadedPlayDataOnce = false

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
  failedUrls.value = new Set()
  isAutoSwitching.value = false
  
  try {
    const data = await tvboxService.getPlayUrl(props.filmId)
    console.log('Play data received:', data)
    
    if (!data.playUrl && (!data.episodes || data.episodes.length === 0)) {
      // 有些源第一次会返回空，自动再拉取一次
      if (!hasReloadedPlayDataOnce) {
        hasReloadedPlayDataOnce = true
        loading.value = false
        await loadPlayData()
        return
      }
      error.value = '暂无可用播放源'
      loading.value = false
      return
    }
    
    episodes.value = data.episodes || []
    currentEpisode.value = 0
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
  
  if (isHlsStream && Hls.isSupported()) {
    console.log('Using HLS.js - Direct Playback')
    
    destroyHls()
    
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
      backBufferLength: 90,
      maxBufferLength: 30,
      maxMaxBufferLength: 60,
      // 直接播放，不使用代理
      xhrSetup: (xhr: XMLHttpRequest) => {
        xhr.withCredentials = false
      },
    })
    
    console.log('Loading M3U8 directly:', url)
    hlsInstance.loadSource(url)
    hlsInstance.attachMedia(video)
    
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      console.log('HLS manifest parsed successfully!')
      videoLoading.value = false
      video.play().catch(e => console.log('Auto-play blocked:', e.message))
    })
    
    hlsInstance.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        console.error('HLS fatal error:', data.type, data.details)
        videoLoading.value = false
        
        if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hlsInstance?.recoverMediaError()
        } else {
          error.value = '播放失败，正在切换线路...'
          handlePlaybackFailed(url)
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
      error.value = '播放失败，正在切换线路...'
    }
    handlePlaybackFailed(url)
  }, { once: true })
}

function getNextPlayableIndex(fromIndex: number): number {
  if (!episodes.value.length) return -1
  for (let i = fromIndex + 1; i < episodes.value.length; i++) {
    const nextUrl = episodes.value[i]?.url
    if (nextUrl && !failedUrls.value.has(nextUrl)) {
      return i
    }
  }
  return -1
}

function handlePlaybackFailed(failedUrl: string) {
  failedUrls.value.add(failedUrl)
  if (isAutoSwitching.value) return

  const nextIndex = getNextPlayableIndex(currentEpisode.value)
  if (nextIndex === -1) {
    error.value = '暂无可用播放源'
    currentPlayUrl.value = ''
    return
  }

  isAutoSwitching.value = true
  currentEpisode.value = nextIndex
  const nextUrl = episodes.value[nextIndex]?.url
  if (nextUrl) {
    currentPlayUrl.value = nextUrl
    initPlayer(nextUrl)
  }
  isAutoSwitching.value = false
}

function switchEpisode(index: number) {
  if (index === currentEpisode.value) return
  
  currentEpisode.value = index
  const url = episodes.value[index]?.url
  if (url) {
    failedUrls.value.delete(url)
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
  hasReloadedPlayDataOnce = false
  loadPlayData()
}

// 提取当前源名称
function getSourceName(name: string): string {
  const match = name.match(/^(.*?) - /)
  return match ? match[1] : 'Default'
}

// 提取剧集后缀 (e.g. "第1集")
function getEpisodeSuffix(name: string): string {
  const match = name.match(/ - (.*)$/)
  return match ? match[1] : name
}

// 切换源 (切换到下一个可用的 API 源)
function switchSource() {
  if (episodes.value.length === 0) return

  const currentEp = episodes.value[currentEpisode.value]
  if (!currentEp) return

  const currentSourceName = getSourceName(currentEp.name)
  const currentSuffix = getEpisodeSuffix(currentEp.name)

  // 寻找下一个不同源的相同剧集
  // 1. 先找相同剧集 (Suffix) 但不同 Source
  let nextIndex = episodes.value.findIndex((ep, index) => {
    if (index === currentEpisode.value) return false
    const source = getSourceName(ep.name)
    const suffix = getEpisodeSuffix(ep.name)
    // 必须是不同源，且剧集名相同 (e.g. 都是 "第1集")
    return source !== currentSourceName && suffix === currentSuffix
  })

  // 2. 如果没找到同剧集的，就找任意不同源的第一个
  if (nextIndex === -1) {
    nextIndex = episodes.value.findIndex(ep => getSourceName(ep.name) !== currentSourceName)
  }

  if (nextIndex !== -1) {
    console.log(`Switching source from ${currentSourceName} to ${getSourceName(episodes.value[nextIndex].name)}`)
    switchEpisode(nextIndex)
  } else {
    // 只有一个源，尝试重新加载
    retry()
  }
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
