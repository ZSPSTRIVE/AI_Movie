<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getFilmDetail, incrementPlayCount } from '@/api/film'
import { chat } from '@/api/ai'
import tvboxService from '@/services/tvboxService'
import type { Film } from '@/types/film'
import TVBoxPlayer from '@/components/TVBoxPlayer.vue'

const route = useRoute()

const film = ref<Film | null>(null)
const loading = ref(true)
const showAiSidebar = ref(true)
const aiQuestion = ref('')
const aiResponse = ref('')
const aiLoading = ref(false)

const filmId = computed(() => String(route.params.id))

onMounted(async () => {
  try {
    // 优先使用TVBox服务
    const filmData = await tvboxService.getDetail(filmId.value)
    if (filmData) {
      film.value = filmData
    } else {
      // 降级使用原有API
      const res = await getFilmDetail(filmId.value)
      film.value = res.data
    }
    
    // 增加播放量（仅数字ID才调用后端API）
    if (film.value?.id && !isNaN(Number(film.value.id))) {
      incrementPlayCount(Number(film.value.id))
    }
  } catch (error) {
    console.error('获取电影详情失败:', error)
  } finally {
    loading.value = false
  }
})

async function handleAiAsk() {
  if (!aiQuestion.value.trim() || !film.value) return
  
  try {
    aiLoading.value = true
    aiResponse.value = ''
    
    // 构建包含电影上下文的提示
    const prompt = `关于电影《${film.value.title}》(${film.value.year}年, 导演: ${film.value.director}, 主演: ${film.value.actors}): ${aiQuestion.value}`
    
    const res = await chat({
      prompt,
      filmId: film.value.id,
      enableRag: true
    })
    
    aiResponse.value = res.data || '抱歉，AI 暂时无法回答这个问题。'
  } catch (error: any) {
    aiResponse.value = '请求失败，请稍后重试。'
  } finally {
    aiLoading.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Loading State -->
    <div v-if="loading" class="animate-pulse space-y-4">
      <div class="aspect-video bg-white rounded-2xl skeleton border-3 border-black" />
      <div class="h-8 w-1/3 skeleton rounded-xl border-2 border-black" />
      <div class="h-4 w-2/3 skeleton rounded-xl border-2 border-black" />
    </div>

    <template v-else-if="film">
      <div class="flex gap-6">
        <!-- 主内容区域 (70%) -->
        <div class="flex-1 space-y-6">
          <!-- 播放器 -->
          <div class="aspect-video bg-black border-3 border-black shadow-brutal rounded-2xl overflow-hidden">
            <TVBoxPlayer
              :film-id="filmId"
              :poster="film.coverUrl"
            />
          </div>

          <!-- 电影信息 - Neo-Brutalism -->
          <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
            <div class="bg-pop-blue border-3 border-black rounded-xl px-4 py-2 inline-block mb-4">
              <h1 class="text-2xl font-black text-white uppercase">{{ film.title }}</h1>
            </div>
            
            <div class="flex flex-wrap items-center gap-3 mb-4">
              <span class="flex items-center bg-pop-yellow border-2 border-black rounded-lg px-3 py-1 font-bold">
                <el-icon class="mr-1"><Star /></el-icon>
                {{ film.rating }}分
              </span>
              <span class="bg-gray-100 border-2 border-black rounded-lg px-3 py-1 font-bold">{{ film.year }}年</span>
              <span class="bg-gray-100 border-2 border-black rounded-lg px-3 py-1 font-bold">{{ film.region }}</span>
              <span class="bg-gray-100 border-2 border-black rounded-lg px-3 py-1 font-bold">{{ film.duration }}分钟</span>
              <span class="bg-pop-green border-2 border-black rounded-lg px-3 py-1 font-bold">{{ film.playCount }}次播放</span>
            </div>

            <div class="flex flex-wrap gap-2 mb-4">
              <span v-for="tag in film.tags" :key="tag" class="bg-pop-purple text-white text-sm font-bold px-3 py-1 border-2 border-black rounded-lg">
                {{ tag }}
              </span>
            </div>

            <div class="space-y-3 text-nb-text bg-nb-bg border-3 border-black rounded-xl p-4">
              <p><span class="font-black">导演：</span>{{ film.director }}</p>
              <p><span class="font-black">主演：</span>{{ film.actors }}</p>
              <p><span class="font-black">简介：</span>{{ film.description }}</p>
            </div>
          </div>

          <!-- 评论区 - Neo-Brutalism -->
          <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
            <div class="bg-pop-orange border-3 border-black rounded-xl px-4 py-2 inline-block mb-4">
              <h2 class="text-xl font-black text-black uppercase">评论区</h2>
            </div>
            <div class="text-center py-8">
              <el-icon size="48" class="mb-4"><ChatDotSquare /></el-icon>
              <div class="nb-badge">暂无评论</div>
            </div>
          </div>
        </div>

        <!-- AI 侧边栏 (30%) - Neo-Brutalism -->
        <div v-if="showAiSidebar" class="w-80 shrink-0">
          <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-4 sticky top-20">
            <div class="flex items-center justify-between mb-4">
              <div class="bg-pop-purple border-2 border-black rounded-lg px-3 py-1 inline-flex items-center">
                <el-icon class="mr-2 text-white"><MagicStick /></el-icon>
                <h3 class="font-black text-white uppercase">AI 助手</h3>
              </div>
              <el-button circle size="small" class="!bg-gray-100 !border-2 !border-black" @click="showAiSidebar = false">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>

            <div class="space-y-2 mb-4">
              <el-button size="small" class="w-full !bg-nb-bg !border-2 !border-black !font-bold hover:!bg-pop-yellow" @click="aiQuestion = '这个演员是谁？'">
                这个演员是谁？
              </el-button>
              <el-button size="small" class="w-full !bg-nb-bg !border-2 !border-black !font-bold hover:!bg-pop-blue hover:!text-white" @click="aiQuestion = '解析当前剧情'">
                解析当前剧情
              </el-button>
              <el-button size="small" class="w-full !bg-nb-bg !border-2 !border-black !font-bold hover:!bg-pop-green" @click="aiQuestion = '推荐类似电影'">
                推荐类似电影
              </el-button>
            </div>

            <div class="space-y-3">
              <el-input
                v-model="aiQuestion"
                type="textarea"
                :rows="3"
                placeholder="问 AI 任何关于这部电影的问题..."
                :disabled="aiLoading"
                class="nb-input"
              />
              <el-button type="primary" class="w-full !bg-pop-green !text-black !border-2 !border-black !font-bold !shadow-brutal-sm hover:!translate-x-0.5 hover:!-translate-y-0.5" :loading="aiLoading" @click="handleAiAsk">
                <el-icon v-if="!aiLoading" class="mr-1"><ChatDotSquare /></el-icon>
                {{ aiLoading ? '思考中...' : '询问 AI' }}
              </el-button>
            </div>

            <!-- AI 回复区域 -->
            <div class="mt-4 p-4 bg-nb-bg border-3 border-black rounded-xl min-h-[100px]">
              <div v-if="aiLoading" class="flex items-center justify-center h-[80px]">
                <div class="nb-badge animate-pulse">思考中...</div>
              </div>
              <p v-else-if="aiResponse" class="text-nb-text font-medium whitespace-pre-wrap">{{ aiResponse }}</p>
              <p v-else class="text-nb-text-sub font-medium">AI 回复将显示在这里...</p>
            </div>
          </div>
        </div>

        <!-- 展开 AI 侧边栏按钮 - Neo-Brutalism -->
        <div v-if="!showAiSidebar" class="fixed right-4 bottom-4">
          <el-button type="primary" circle size="large" class="!w-14 !h-14 !bg-pop-purple !border-3 !border-black !shadow-brutal hover:!translate-x-1 hover:!-translate-y-1 transition-all" @click="showAiSidebar = true">
            <el-icon size="24"><MagicStick /></el-icon>
          </el-button>
        </div>
      </div>
    </template>

    <!-- 电影不存在状态 -->
    <div v-else class="text-center py-20">
      <el-icon size="64" class="mb-6"><VideoCamera /></el-icon>
      <div class="nb-badge text-lg">电影不存在</div>
    </div>
  </div>
</template>
