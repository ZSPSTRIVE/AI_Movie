<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { generateOutline, type NovelOutlineRequest } from '@/api/ai'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { MagicStick, User, Service, Setting, Document, Loading, List, Refresh } from '@element-plus/icons-vue'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: true
})

const activeTab = ref('chat')

const router = useRouter()

// ===== AI 对话 =====
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

function goToFilmDetail(filmId?: string | number | null) {
  if (filmId === undefined || filmId === null || filmId === '') {
    return
  }
  router.push(`/film/${filmId}`)
}

// 处理Markdown中的链接点击，使用Vue Router导航而不是页面刷新
function handleMarkdownClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  // 检查是否点击的是链接
  if (target.tagName === 'A') {
    const href = target.getAttribute('href')
    if (href && href.startsWith('/')) {
      // 是站内链接，使用Vue Router导航
      event.preventDefault()
      router.push(href)
    }
  }
}

const chatMessages = ref<ChatMessage[]>([])
const chatInput = ref('')
const chatLoading = ref(false)
const enableRag = ref(false)
const chatContainerRef = ref<HTMLElement>()

async function handleSendMessage() {
  if (!chatInput.value.trim() || chatLoading.value) return
  
  const userMessage = chatInput.value.trim()
  chatMessages.value.push({ role: 'user', content: userMessage })
  chatInput.value = ''
  chatLoading.value = true
  
  // 添加空的 AI 回复
  chatMessages.value.push({ role: 'assistant', content: '' })
  const assistantIndex = chatMessages.value.length - 1
  
  await nextTick()
  scrollToBottom()
  
  try {
    const baseUrl = '/api'
    const token = localStorage.getItem('token')
    const response = await fetch(`${baseUrl}/ai/chat/completions?prompt=${encodeURIComponent(userMessage)}&enableRag=${enableRag.value}`, {
      headers: token ? { 'Authorization': token } : undefined
    })
    
    if (!response.ok) {
      throw new Error('请求失败')
    }
    
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    
    if (reader) {
      let fullContent = ''
      
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        const chunk = decoder.decode(value, { stream: true })
        fullContent += chunk
        
        // 实时处理并显示：每次接收后立即清理data:前缀
        const cleanContent = fullContent
          .replace(/data:\s*/g, '')  // 移除所有 "data:" 
          .replace(/\[DONE\]/g, '')  // 移除结束标记
          .replace(/\r?\n/g, '')     // 移除换行符
        
        // 实时更新显示
        chatMessages.value[assistantIndex].content = cleanContent
        scrollToBottom()
      }
    }
    chatLoading.value = false
  } catch (e) {
    chatMessages.value[assistantIndex].content = '抱歉，发生了错误，请重试。'
    chatLoading.value = false
  }
}

function scrollToBottom() {
  if (chatContainerRef.value) {
    chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
  }
}

function clearChat() {
  chatMessages.value = []
}

// ===== 小说创作 =====
const novelForm = ref<NovelOutlineRequest>({
  theme: '',
  style: '轻松',
  protagonist: '主角',
  chapterCount: 10,
  extraRequirements: ''
})
const novelOutlineStr = ref('')
const novelOutlineData = computed(() => {
  try {
    if (!novelOutlineStr.value) return null
    // 尝试提取 JSON 部分（如果有 Markdown 代码块）
    let jsonStr = novelOutlineStr.value
    const match = jsonStr.match(/```json([\s\S]*?)```/)
    if (match) {
      jsonStr = match[1]
    }
    return JSON.parse(jsonStr)
  } catch (e) {
    return null
  }
})

const novelLoading = ref(false)

async function handleGenerateOutline() {
  if (!novelForm.value.theme.trim()) return
  
  novelLoading.value = true
  try {
    const res = await generateOutline(novelForm.value)
    novelOutlineStr.value = res.data
  } finally {
    novelLoading.value = false
  }
}

const styleOptions = ['轻松', '严肃', '悬疑', '浪漫', '热血', '治愈']

// ===== RAG 知识库 =====
const ragQuery = ref('')
const ragResults = ref<Array<{
  film_id?: number
  title: string
  content: string
  score: number
  knowledge_base?: string
  source_type?: string
}>>([])
const ragLoading = ref(false)
const syncLoading = ref(false)
const syncStatus = ref<'idle' | 'success' | 'error'>('idle')
const syncMessage = ref('')
const syncProgress = ref('')

interface RagSyncJobStatus {
  success?: boolean
  accepted?: boolean
  started?: boolean
  running?: boolean
  state?: 'idle' | 'running' | 'success' | 'partial_success' | 'failed'
  limit?: number
  total?: number
  processed?: number
  synced?: number
  failed?: number
  message?: string
}

// Python RAG 服务代理地址（由后端转发，避免浏览器 CORS）
const RAG_SERVICE_URL = '/api/ai/rag/python'
const isRagAvailable = computed(() => ragServiceStatus.value === 'online' || ragServiceStatus.value === 'degraded')
const DEFAULT_SYNC_LIMIT = 100
let syncStatusPollTimer: number | null = null

function stopSyncStatusPolling() {
  if (syncStatusPollTimer !== null) {
    window.clearInterval(syncStatusPollTimer)
    syncStatusPollTimer = null
  }
}

function applySyncJobStatus(data: RagSyncJobStatus) {
  const processed = data.processed ?? 0
  const total = data.total ?? 0
  const synced = data.synced ?? 0
  const failed = data.failed ?? 0
  const state = data.state ?? 'idle'
  const baseMessage = data.message || '同步任务状态未知'

  syncProgress.value = total > 0
    ? `进度 ${processed}/${total}，成功 ${synced}，失败 ${failed}`
    : ''

  if (state === 'running' || data.running) {
    syncLoading.value = true
    syncStatus.value = 'idle'
    syncMessage.value = baseMessage
    return
  }

  syncLoading.value = false
  if (state === 'success' || state === 'partial_success') {
    syncStatus.value = 'success'
    syncMessage.value = total > 0 ? `${baseMessage}，成功 ${synced} 条` : baseMessage
  } else if (state === 'failed') {
    syncStatus.value = 'error'
    syncMessage.value = total > 0 ? `${baseMessage}，失败 ${failed} 条` : baseMessage
  } else {
    syncStatus.value = 'idle'
    syncMessage.value = baseMessage
  }
}

async function fetchSyncStatus(showError = false) {
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`${RAG_SERVICE_URL}/sync/status`, {
      method: 'GET',
      headers: token ? { 'Authorization': token } : undefined
    })
    if (!res.ok) {
      throw new Error('无法获取同步状态')
    }
    const data: RagSyncJobStatus = await res.json()
    applySyncJobStatus(data)
    if (data.state === 'running' || data.running) {
      if (syncStatusPollTimer === null) {
        startSyncStatusPolling()
      }
    } else {
      stopSyncStatusPolling()
    }
  } catch (e: any) {
    stopSyncStatusPolling()
    syncLoading.value = false
    if (showError) {
      syncStatus.value = 'error'
      syncMessage.value = e.message || '获取同步状态失败'
      ElMessage.error(syncMessage.value)
    }
  }
}

function startSyncStatusPolling() {
  stopSyncStatusPolling()
  syncStatusPollTimer = window.setInterval(() => {
    void fetchSyncStatus(false)
  }, 2000)
}

// 同步电影数据到向量库
async function handleSyncFilms() {
  syncLoading.value = true
  syncStatus.value = 'idle'
  syncMessage.value = ''
  syncProgress.value = ''
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`${RAG_SERVICE_URL}/sync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': token } : {})
      },
      body: JSON.stringify({ limit: DEFAULT_SYNC_LIMIT })
    })
    if (!res.ok) {
      const errorBody = await res.json().catch(() => null)
      throw new Error(errorBody?.message || 'RAG 服务不可用')
    }
    const data: RagSyncJobStatus = await res.json()
    if (data.success !== false) {
      applySyncJobStatus(data)
      startSyncStatusPolling()
      ElMessage.success(data.message || `已启动 ${DEFAULT_SYNC_LIMIT} 条电影的后台同步`)
    } else {
      throw new Error(data.message || '同步失败')
    }
  } catch (e: any) {
    syncStatus.value = 'error'
    syncMessage.value = e.message || '同步失败，请检查电影服务和 Python RAG 服务是否运行'
    syncProgress.value = ''
    ElMessage.error(syncMessage.value)
  } finally {
    if (syncStatus.value === 'error') {
      syncLoading.value = false
    }
  }
}

// 知识库检索
async function handleRagSearch() {
  if (!ragQuery.value.trim()) return
  
  ragLoading.value = true
  ragResults.value = []
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`${RAG_SERVICE_URL}/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': token } : {})
      },
      body: JSON.stringify({ query: ragQuery.value, top_k: 5 })
    })
    if (!res.ok) {
      throw new Error('RAG 服务不可用')
    }
    const data = await res.json()
    ragResults.value = data.results || []
    if (ragResults.value.length === 0) {
      ElMessage.info('未找到相关内容')
    }
  } catch (e: any) {
    ElMessage.error('检索失败，请检查 Python 服务是否运行')
  } finally {
    ragLoading.value = false
  }
}

// 检查 RAG 服务状态
const ragServiceStatus = ref<'checking' | 'online' | 'degraded' | 'offline'>('checking')

async function checkRagService() {
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`${RAG_SERVICE_URL}/health`, {
      method: 'GET',
      headers: token ? { 'Authorization': token } : undefined
    })
    if (!res.ok) {
      ragServiceStatus.value = 'offline'
      return
    }
    const data = await res.json()
    const postgresOk = data?.components?.postgres?.status === 'connected'
    const milvusOk = data?.components?.milvus?.status === 'connected'
    if (!postgresOk) {
      ragServiceStatus.value = 'offline'
      return
    }
    ragServiceStatus.value = milvusOk ? 'online' : 'degraded'
  } catch {
    ragServiceStatus.value = 'offline'
  }
}

onMounted(() => {
  checkRagService()
  void fetchSyncStatus(false)
})

onUnmounted(() => {
  stopSyncStatusPolling()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 标题 -->
    <div class="bg-info shadow-lg rounded-2xl px-6 py-4 inline-block">
      <h1 class="text-3xl font-bold text-white flex items-center">
        <el-icon class="mr-3"><MagicStick /></el-icon>
         AI 实验室
      </h1>
    </div>

    <el-tabs v-model="activeTab" class="nb-tabs">
      <!-- AI 对话 -->
      <el-tab-pane label="AI 对话" name="chat">
        <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-lg rounded-2xl overflow-hidden flex flex-col h-[600px]">
          <!-- 对话区域 -->
          <div ref="chatContainerRef" class="flex-1 overflow-y-auto p-6 space-y-6 bg-gray-50 dark:bg-gray-900">
            <div v-if="chatMessages.length === 0" class="h-full flex items-center justify-center">
              <div class="text-center">
                <div class="text-8xl mb-6"></div>
                <div class="nb-badge text-lg mb-4">开始与 AI 助手对话吧！</div>
                <p class="font-medium text-gray-500 dark:text-gray-400">可以询问电影知识、剧情解析等问题</p>
              </div>
            </div>
            
            <div
              v-for="(msg, index) in chatMessages"
              :key="index"
              class="flex items-start gap-3"
              :class="msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'"
            >
              <!-- 头像 -->
              <div 
                class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 border border-gray-200 dark:border-gray-700"
                :class="msg.role === 'user' ? 'bg-primary' : 'bg-info'"
              >
                <el-icon color="white" size="20">
                  <User v-if="msg.role === 'user'" />
                  <Service v-else />
                </el-icon>
              </div>
              
              <!-- 气泡 -->
              <div
                class="ai-chat-bubble rounded-xl px-4 py-3 max-w-[85%] border border-gray-200 dark:border-gray-700 shadow-sm"
                :class="msg.role === 'user' ? 'bg-primary-50 dark:bg-primary-900/30 text-gray-900 dark:text-gray-100' : 'bg-white dark:bg-gray-800'"
              >
                <!-- AI 回复：流式输出时显示纯文本，完成后渲染 Markdown -->
                <template v-if="msg.role === 'assistant'">
                  <!-- 正在输出中 -->
                  <p v-if="chatLoading && index === chatMessages.length - 1" class="text-sm font-medium whitespace-pre-wrap break-words ai-chat-text">
                    {{ msg.content }}<span class="animate-pulse text-primary">▊</span>
                  </p>
                  <!-- 输出完成，渲染 Markdown -->
                  <div v-else class="markdown-body text-sm break-words font-medium" v-html="md.render(msg.content)" @click="handleMarkdownClick"></div>
                </template>
                <!-- 用户消息 -->
                <p v-else class="text-sm font-medium whitespace-pre-wrap break-words">{{ msg.content }}</p>
              </div>
            </div>
          </div>
          
          <!-- 输入区域 -->
          <div class="border-t border-gray-200 dark:border-gray-700 p-4 bg-white dark:bg-gray-800">
            <div class="flex items-center gap-4 mb-3">
              <label class="flex items-center gap-2 cursor-pointer bg-primary/10 border border-primary/30 rounded-lg px-3 py-1">
                <input type="checkbox" v-model="enableRag" class="w-4 h-4" />
                <span class="font-medium text-sm">启用 RAG 检索</span>
              </label>
              <div class="flex-1"></div>
              <el-button class="!border !border-gray-200 dark:!border-gray-700 !font-medium" size="small" @click="clearChat">
                 清空对话
              </el-button>
            </div>
            <div class="flex gap-3">
              <el-input
                v-model="chatInput"
                type="textarea"
                :rows="1"
                :autosize="{ minRows: 1, maxRows: 4 }"
                placeholder="输入你的问题... (按 Enter 发送)"
                @keyup.enter.exact="handleSendMessage"
                :disabled="chatLoading"
                class="flex-1"
                size="large"
              />
              <el-button class="!bg-primary !text-white !border !border-primary !font-semibold !px-6" :loading="chatLoading" @click="handleSendMessage">
                 发送
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 小说创作 -->
      <el-tab-pane label="小说创作" name="novel">
        <div class="grid lg:grid-cols-3 gap-6 h-[calc(100vh-200px)] min-h-[600px]">
          <!-- 左侧：设置 -->
          <div class="bg-dark-card rounded-xl p-6 lg:col-span-1 h-full overflow-y-auto">
            <div class="sticky top-0">
              <h3 class="text-lg font-bold text-white mb-6 flex items-center">
                <el-icon class="mr-2 text-primary"><Setting /></el-icon>
                创作设置
              </h3>
              <el-form :model="novelForm" label-position="top">
                <el-form-item label="小说主题">
                  <el-input v-model="novelForm.theme" placeholder="如：穿越到修仙世界的程序员" type="textarea" :rows="2" />
                </el-form-item>
                
                <div class="grid grid-cols-2 gap-4">
                  <el-form-item label="风格流派">
                    <el-select v-model="novelForm.style" class="w-full">
                      <el-option v-for="s in styleOptions" :key="s" :label="s" :value="s" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="章节数量">
                    <el-input-number v-model="novelForm.chapterCount" :min="5" :max="50" class="!w-full" />
                  </el-form-item>
                </div>

                <el-form-item label="主角名称">
                  <el-input v-model="novelForm.protagonist" placeholder="主角名称" />
                </el-form-item>
                
                <el-form-item label="额外要求">
                  <el-input v-model="novelForm.extraRequirements" type="textarea" :rows="4" placeholder="例如：主角要有一个特殊的金手指，反派要智商在线..." />
                </el-form-item>
                
                <el-form-item class="mt-8">
                  <el-button type="primary" :loading="novelLoading" @click="handleGenerateOutline" class="w-full py-5 text-lg">
                    <el-icon class="mr-2"><MagicStick /></el-icon>
                    生成大纲
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </div>
          
          <!-- 右侧：大纲结果 -->
          <div class="bg-dark-card rounded-xl p-6 lg:col-span-2 h-full overflow-hidden flex flex-col">
            <h3 class="text-lg font-bold text-white mb-4 flex items-center shrink-0">
              <el-icon class="mr-2 text-purple-400"><Document /></el-icon>
              小说大纲
            </h3>
            
            <div class="flex-1 overflow-y-auto pr-2 custom-scrollbar">
              <div v-if="novelLoading" class="h-full flex flex-col items-center justify-center text-gray-400">
                <el-icon class="is-loading text-4xl mb-4"><Loading /></el-icon>
                <p>AI 正在构思剧情，请稍候...</p>
              </div>
              
              <div v-else-if="novelOutlineData" class="space-y-6">
                <!-- 标题和简介 -->
                <div class="text-center mb-8">
                  <h1 class="text-3xl font-bold text-white mb-4">{{ novelOutlineData.title }}</h1>
                  <div class="bg-dark-bg p-4 rounded-xl text-left">
                    <h4 class="text-primary font-bold mb-2">故事简介</h4>
                    <p class="text-gray-300 leading-relaxed">{{ novelOutlineData.synopsis }}</p>
                  </div>
                </div>

                <!-- 世界观 -->
                <div class="bg-dark-bg p-4 rounded-xl">
                  <h4 class="text-primary font-bold mb-2">世界观设定</h4>
                  <p class="text-gray-300 leading-relaxed">{{ novelOutlineData.worldSetting }}</p>
                </div>

                <!-- 角色介绍 -->
                <div>
                  <h4 class="text-primary font-bold mb-3 flex items-center">
                    <el-icon class="mr-1"><User /></el-icon> 主要角色
                  </h4>
                  <div class="grid sm:grid-cols-2 gap-4">
                    <div v-for="(char, idx) in novelOutlineData.characters" :key="idx" class="bg-dark-bg p-4 rounded-xl border border-dark-border">
                      <div class="flex justify-between items-start mb-2">
                        <span class="font-bold text-white text-lg">{{ char.name }}</span>
                        <el-tag size="small" effect="dark">{{ char.role }}</el-tag>
                      </div>
                      <p class="text-sm text-gray-400">{{ char.description }}</p>
                    </div>
                  </div>
                </div>

                <!-- 章节大纲 -->
                <div>
                  <h4 class="text-primary font-bold mb-3 flex items-center">
                    <el-icon class="mr-1"><List /></el-icon> 章节大纲
                  </h4>
                  <div class="space-y-3">
                    <div v-for="chapter in novelOutlineData.chapters" :key="chapter.index" class="bg-dark-bg p-4 rounded-xl flex gap-4 hover:bg-gray-800 transition-colors">
                      <div class="shrink-0 w-12 h-12 bg-primary/10 rounded-lg flex items-center justify-center text-primary font-bold text-xl">
                        {{ chapter.index }}
                      </div>
                      <div>
                        <h5 class="font-bold text-white mb-1">{{ chapter.title }}</h5>
                        <p class="text-sm text-gray-400">{{ chapter.summary }}</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 原始文本回退（解析失败时显示） -->
              <div v-else-if="novelOutlineStr" class="prose prose-invert max-w-none">
                <pre class="text-sm text-gray-300 whitespace-pre-wrap bg-dark-bg p-4 rounded-lg">{{ novelOutlineStr }}</pre>
              </div>
              
              <el-empty v-else description="在左侧输入设定，让 AI 为你生成大纲" :image-size="120" />
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 知识库 RAG -->
      <el-tab-pane label="知识库 RAG" name="knowledge">
        <div class="grid lg:grid-cols-2 gap-6">
          <!-- 左侧：服务状态 & 同步 -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-lg rounded-2xl p-6">
            <h3 class="text-xl font-bold mb-6 flex items-center gap-2">
              RAG 服务控制
            </h3>
            
            <!-- 服务状态 -->
            <div class="mb-6 p-4 rounded-xl border border-gray-200 dark:border-gray-700" :class="{
              'bg-success/10': ragServiceStatus === 'online',
              'bg-warning/10': ragServiceStatus === 'degraded',
              'bg-danger/10': ragServiceStatus === 'offline',
              'bg-gray-100 dark:bg-gray-700': ragServiceStatus === 'checking'
            }">
              <div class="flex items-center gap-3">
                <span class="w-3 h-3 rounded-full" :class="{
                  'bg-success': ragServiceStatus === 'online',
                  'bg-warning': ragServiceStatus === 'degraded',
                  'bg-danger': ragServiceStatus === 'offline',
                  'bg-gray-400': ragServiceStatus === 'checking'
                }"></span>
                <div>
                  <div class="font-semibold">Python RAG 服务</div>
                  <div class="text-sm text-gray-500 dark:text-gray-400">
                    {{ ragServiceStatus === 'online' ? '运行中 (向量检索可用)' :
                       ragServiceStatus === 'degraded' ? '运行中 (降级模式：关键词检索可用)' :
                       ragServiceStatus === 'offline' ? '未连接 - 请启动服务' : '检查中...' }}
                  </div>
                </div>
                <el-button size="small" @click="checkRagService" class="ml-auto">刷新</el-button>
              </div>
            </div>
            
            <!-- 同步电影 -->
            <div class="mb-6">
              <h4 class="font-bold mb-3">同步电影数据到向量库</h4>
              <p class="text-sm text-gray-600 mb-4">
                将 MySQL 中的电影数据 (t_film) 向量化后存入 Milvus，用于语义检索。
              </p>
              <el-button 
                type="primary" 
                :loading="syncLoading" 
                :disabled="!isRagAvailable"
                @click="handleSyncFilms"
                class="!bg-primary !border !border-primary !font-medium"
              >
                <el-icon class="mr-1"><Refresh /></el-icon>
                {{ syncLoading ? '后台同步中...' : '开始同步' }}
              </el-button>
              <div v-if="syncProgress" class="mt-3 text-sm text-gray-500 dark:text-gray-400">
                {{ syncProgress }}
              </div>
              <el-alert 
                v-if="syncStatus !== 'idle'" 
                :type="syncStatus === 'success' ? 'success' : 'error'"
                :title="syncMessage"
                class="mt-4"
                show-icon
                closable
              />
            </div>
            
            <!-- 使用说明 -->
            <div class="bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
              <h4 class="font-bold mb-2">使用说明</h4>
              <ol class="text-sm text-gray-700 space-y-2 list-decimal list-inside">
                <li>启动 Python 服务: <code class="bg-gray-200 px-1 rounded">cd jelly-rag-python && uvicorn main:app --port 8500</code></li>
                <li>点击"开始同步"将电影数据向量化</li>
                <li>在右侧输入查询进行语义搜索</li>
                <li>在 AI 对话中勾选"启用 RAG"可自动检索知识库</li>
              </ol>
            </div>
          </div>
          
          <!-- 右侧：知识库检索 -->
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-lg rounded-2xl p-6 flex flex-col">
            <h3 class="text-xl font-bold mb-6 flex items-center gap-2">
              知识库检索测试
            </h3>
            
            <!-- 搜索框 -->
            <div class="flex gap-3 mb-6">
              <el-input 
                v-model="ragQuery" 
                placeholder="输入查询内容，如：科幻电影、刘德华..." 
                size="large"
                :disabled="!isRagAvailable"
                @keyup.enter="handleRagSearch"
              />
              <el-button 
                type="primary" 
                :loading="ragLoading" 
                :disabled="!isRagAvailable || !ragQuery.trim()"
                @click="handleRagSearch"
                class="!bg-success !text-white !border !border-success !font-medium"
              >
                搜索
              </el-button>
            </div>
            
            <!-- 搜索结果 -->
            <div class="flex-1 overflow-y-auto space-y-4" style="max-height: 400px;">
              <div v-if="ragLoading" class="flex items-center justify-center py-12">
                <el-icon class="is-loading text-4xl text-info"><Loading /></el-icon>
              </div>
              
              <div v-else-if="ragResults.length > 0">
                <div 
                  v-for="(result, idx) in ragResults" 
                  :key="idx"
                  class="p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 hover:shadow-md transition-shadow cursor-pointer rag-result"
                  @click="goToFilmDetail(result.film_id)"
                >
                  <div class="flex items-start justify-between mb-2">
                    <h4 class="font-semibold text-lg">{{ result.title }}</h4>
                    <span class="text-xs bg-info text-white px-2 py-1 rounded-full">
                      相关度: {{ (result.score * 100).toFixed(1) }}%
                    </span>
                  </div>
                  <p class="text-sm text-gray-600 line-clamp-3">{{ result.content }}</p>
                  <div v-if="result.film_id" class="mt-2 text-xs text-gray-400">电影 ID: {{ result.film_id }}</div>
                  <div v-else-if="result.knowledge_base" class="mt-2 text-xs text-gray-400">知识源: {{ result.knowledge_base }}</div>
                </div>
              </div>
              
              <el-empty v-else description="输入内容进行向量语义搜索" :image-size="80" />
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.markdown-body {
  color: var(--text-primary);
  word-wrap: break-word;
  overflow-wrap: break-word;
  white-space: normal;
  line-height: 1.7;
  letter-spacing: 0.01em;
}

.ai-chat-text {
  color: var(--text-primary);
}

.markdown-body :deep(p) {
  margin-bottom: 0.5em;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(ul), .markdown-body :deep(ol) {
  padding-left: 1.5em;
  margin-bottom: 0.5em;
}
.markdown-body :deep(li) {
  list-style: disc;
}
.markdown-body :deep(code) {
  background-color: var(--color-primary-bg);
  padding: 0.2em 0.4em;
  border-radius: var(--radius-sm);
  font-family: 'SF Mono', 'Fira Code', monospace;
}
.markdown-body :deep(pre) {
  background-color: rgba(0, 0, 0, 0.06);
  padding: 1em;
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin-bottom: 0.5em;
}
.markdown-body :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

html.dark .markdown-body :deep(pre) {
  background-color: rgba(0, 0, 0, 0.3);
}

.rag-result {
  position: relative;
}

.rag-result::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0));
  opacity: 0.7;
  pointer-events: none;
}
</style>
