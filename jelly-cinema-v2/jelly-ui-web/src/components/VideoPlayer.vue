<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import Hls from 'hls.js'

const props = defineProps<{
  src: string
  poster?: string
}>()

const videoRef = ref<HTMLVideoElement | null>(null)
const hlsInstance = ref<Hls | null>(null)

// 解析视频类型
const videoType = computed(() => {
  const url = props.src || ''
  
  if (!url) return 'none'
  
  // 本地上传的视频（/uploads/ 或 /api/ 开头）
  if (url.startsWith('/uploads/') || url.startsWith('/api/')) {
    if (url.includes('.m3u8')) return 'hls'
    return 'direct'
  }
  
  // 直接视频链接（优先判断，避免误判为iframe）
  if (url.match(/\.(mp4|webm|ogg|mov|mkv|avi|flv)(\?|#|$)/i)) {
    return 'direct'
  }
  
  // HLS 流媒体
  if (url.includes('.m3u8')) {
    return 'hls'
  }
  
  // 哔哩哔哩
  if (url.includes('bilibili.com') || url.includes('b23.tv')) {
    return 'bilibili'
  }
  
  // 优酷
  if (url.includes('youku.com') || url.includes('v.youku.com')) {
    return 'youku'
  }
  
  // 腾讯视频
  if (url.includes('qq.com') || url.includes('v.qq.com')) {
    return 'qq'
  }
  
  // 爱奇艺
  if (url.includes('iqiyi.com')) {
    return 'iqiyi'
  }
  
  // 芒果TV
  if (url.includes('mgtv.com')) {
    return 'mgtv'
  }
  
  // YouTube
  if (url.includes('youtube.com') || url.includes('youtu.be')) {
    return 'youtube'
  }
  
  // iframe 嵌入链接
  if (url.includes('iframe') || url.includes('embed') || url.includes('player')) {
    return 'iframe'
  }
  
  // http/https 开头的其他链接，尝试直接播放
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return 'direct'
  }
  
  // 默认尝试 iframe
  return 'iframe'
})

// 获取嵌入URL
const embedUrl = computed(() => {
  const url = props.src || ''
  
  switch (videoType.value) {
    case 'bilibili': {
      // 从 bilibili 链接提取 BV 号
      const bvMatch = url.match(/BV[a-zA-Z0-9]+/)
      if (bvMatch) {
        return `//player.bilibili.com/player.html?bvid=${bvMatch[0]}&high_quality=1&danmaku=0`
      }
      // 提取 av 号
      const avMatch = url.match(/av(\d+)/)
      if (avMatch) {
        return `//player.bilibili.com/player.html?aid=${avMatch[1]}&high_quality=1&danmaku=0`
      }
      return url
    }
    
    case 'youku': {
      const idMatch = url.match(/id_([a-zA-Z0-9=]+)/)
      if (idMatch) {
        return `//player.youku.com/embed/${idMatch[1]}`
      }
      return url
    }
    
    case 'qq': {
      const vidMatch = url.match(/\/([a-zA-Z0-9]+)\.html/)
      if (vidMatch) {
        return `//v.qq.com/txp/iframe/player.html?vid=${vidMatch[1]}`
      }
      return url
    }
    
    case 'iqiyi': {
      // 爱奇艺需要特殊处理，这里返回原链接用iframe
      return url
    }
    
    case 'youtube': {
      const idMatch = url.match(/(?:v=|youtu\.be\/)([a-zA-Z0-9_-]+)/)
      if (idMatch) {
        return `//www.youtube.com/embed/${idMatch[1]}`
      }
      return url
    }
    
    case 'iframe':
      return url
    
    default:
      return url
  }
})

// 初始化 HLS 播放器
function initHls() {
  if (videoType.value === 'hls' && videoRef.value && props.src) {
    if (Hls.isSupported()) {
      hlsInstance.value = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
      })
      hlsInstance.value.loadSource(props.src)
      hlsInstance.value.attachMedia(videoRef.value)
    } else if (videoRef.value.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari 原生支持 HLS
      videoRef.value.src = props.src
    }
  }
}

onMounted(() => {
  initHls()
})

onUnmounted(() => {
  if (hlsInstance.value) {
    hlsInstance.value.destroy()
    hlsInstance.value = null
  }
})
</script>

<template>
  <div class="video-player">
    <!-- 直接视频播放 -->
    <video
      v-if="videoType === 'direct'"
      :src="src"
      :poster="poster"
      controls
      class="video-element"
      playsinline
    />
    
    <!-- HLS 流媒体播放 -->
    <video
      v-else-if="videoType === 'hls'"
      ref="videoRef"
      :poster="poster"
      controls
      class="video-element"
      playsinline
    />
    
    <!-- 第三方视频嵌入（哔哩哔哩、优酷、腾讯等） -->
    <iframe
      v-else
      :src="embedUrl"
      class="video-iframe"
      frameborder="0"
      allowfullscreen
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
      referrerpolicy="no-referrer-when-downgrade"
    />
    
    <!-- 无视频源提示 -->
    <div v-if="!src || videoType === 'none'" class="no-video">
      <div class="no-video-icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <polygon points="23 7 16 12 23 17 23 7"/>
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
        </svg>
      </div>
      <p>暂无播放源</p>
    </div>
  </div>
</template>

<style scoped>
.video-player {
  width: 100%;
  height: 100%;
  background: #000;
  position: relative;
}

.video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #000;
}

.video-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #000;
}

.no-video {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e293b, #334155);
  color: #94a3b8;
}

.no-video-icon {
  margin-bottom: 16px;
  opacity: 0.6;
}

.no-video p {
  font-size: 16px;
  font-weight: 500;
}
</style>
