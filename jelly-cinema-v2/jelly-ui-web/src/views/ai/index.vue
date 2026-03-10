<script setup lang="ts">
import { ref, nextTick, onMounted, computed } from 'vue'
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

function goToFilmDetail(filmId: string | number) {
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
  film_id: number
  title: string
  content: string
  score: number
}>>([])
const ragLoading = ref(false)
const syncLoading = ref(false)
const syncStatus = ref<'idle' | 'success' | 'error'>('idle')
const syncMessage = ref('')

// Python RAG 服务代理地址（由后端转发，避免浏览器 CORS）
const RAG_SERVICE_URL = '/api/ai/rag/python'

// 同步电影数据到向量库
async function handleSyncFilms() {
  syncLoading.value = true
  syncStatus.value = 'idle'
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`${RAG_SERVICE_URL}/sync`, {
      method: 'POST',
      headers: token ? { 'Authorization': token } : undefined
    })
    if (!res.ok) {
      throw new Error('RAG 服务不可用')
    }
    const data = await res.json()
    if (data.success) {
      syncStatus.value = 'success'
      syncMessage.value = `同步成功！共同步 ${data.count} 部电影`
      ElMessage.success(syncMessage.value)
    } else {
      throw new Error(data.message || '同步失败')
    }
  } catch (e: any) {
    syncStatus.value = 'error'
    syncMessage.value = e.message || '同步失败，请检查 Python 服务是否运行'
    ElMessage.error(syncMessage.value)
  } finally {
    syncLoading.value = false
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
const ragServiceStatus = ref<'checking' | 'online' | 'offline'>('checking')

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
    ragServiceStatus.value = data.status === 'healthy' ? 'online' : 'offline'
  } catch {
    ragServiceStatus.value = 'offline'
  }
}

onMounted(() => {
  checkRagService()
})
</script>

<template>
  <div class="space-y-6">
    <!-- 标题 - Neo-Brutalism -->
    <div class="bg-pop-purple border-3 border-black shadow-brutal rounded-2xl px-6 py-4 inline-block">
      <h1 class="text-3xl font-black text-white uppercase flex items-center">
        <el-icon class="mr-3"><MagicStick /></el-icon>
         AI 实验室
      </h1>
    </div>

    <el-tabs v-model="activeTab" class="nb-tabs">
      <!-- AI 对话 - Neo-Brutalism -->
      <el-tab-pane label="💬 AI 对话" name="chat">
        <div class="bg-white border-3 border-black shadow-brutal rounded-2xl overflow-hidden flex flex-col h-[600px]">
          <!-- 对话区域 -->
          <div ref="chatContainerRef" class="flex-1 overflow-y-auto p-6 space-y-6 bg-nb-bg">
            <div v-if="chatMessages.length === 0" class="h-full flex items-center justify-center">
              <div class="text-center">
                <div class="text-8xl mb-6"></div>
                <div class="nb-badge text-lg mb-4">开始与 AI 助手对话吧！</div>
                <p class="font-bold text-nb-text-sub">可以询问电影知识、剧情解析等问题</p>
              </div>
            </div>
            
            <div
              v-for="(msg, index) in chatMessages"
              :key="index"
              class="flex items-start gap-3"
              :class="msg.role === 'user' ? 'flex-row-reverse' : 'flex-row'"
            >
              <!-- 头像 - Neo-Brutalism -->
              <div 
                class="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 border-2 border-black"
                :class="msg.role === 'user' ? 'bg-pop-blue' : 'bg-pop-purple'"
              >
                <el-icon color="white" size="20">
                  <User v-if="msg.role === 'user'" />
                  <Service v-else />
                </el-icon>
              </div>
              
              <!-- 气泡 - Neo-Brutalism -->
              <div
                class="ai-chat-bubble rounded-xl px-4 py-3 max-w-[85%] border-2 border-black shadow-brutal-sm"
                :class="msg.role === 'user' ? 'bg-pop-yellow text-black' : 'bg-white'"
              >
                <!-- AI 回复：流式输出时显示纯文本，完成后渲染 Markdown -->
                <template v-if="msg.role === 'assistant'">
                  <!-- 正在输出中 -->
                  <p v-if="chatLoading && index === chatMessages.length - 1" class="text-sm font-medium whitespace-pre-wrap break-words ai-chat-text">
                    {{ msg.content }}<span class="animate-pulse text-pop-blue">▊</span>
                  </p>
                  <!-- 输出完成，渲染 Markdown -->
                  <div v-else class="markdown-body text-sm break-words font-medium" v-html="md.render(msg.content)" @click="handleMarkdownClick"></div>
                </template>
                <!-- 用户消息 -->
                <p v-else class="text-sm font-bold whitespace-pre-wrap break-words">{{ msg.content }}</p>
              </div>
            </div>
          </div>
          
          <!-- 输入区域 - Neo-Brutalism -->
          <div class="border-t-3 border-black p-4 bg-white">
            <div class="flex items-center gap-4 mb-3">
              <label class="flex items-center gap-2 cursor-pointer bg-pop-blue/10 border-2 border-black rounded-lg px-3 py-1">
                <input type="checkbox" v-model="enableRag" class="w-4 h-4" />
                <span class="font-bold text-sm">🔍 启用 RAG 检索</span>
              </label>
              <div class="flex-1"></div>
              <el-button class="!border-2 !border-black !font-bold" size="small" @click="clearChat">
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
              <el-button class="!bg-pop-green !text-black !border-3 !border-black !font-black !px-6 !shadow-brutal-sm" :loading="chatLoading" @click="handleSendMessage">
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
                    <h4 class="text-primary font-bold mb-2">📝 故事简介</h4>
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
      <el-tab-pane label="📚 知识库 RAG" name="knowledge">
        <div class="grid lg:grid-cols-2 gap-6">
          <!-- 左侧：服务状态 & 同步 -->
          <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6">
            <h3 class="text-xl font-black mb-6 flex items-center gap-2">
              ⚙️ RAG 服务控制
            </h3>
            
            <!-- 服务状态 -->
            <div class="mb-6 p-4 rounded-xl border-2 border-black" :class="{
              'bg-green-100': ragServiceStatus === 'online',
              'bg-red-100': ragServiceStatus === 'offline',
              'bg-gray-100': ragServiceStatus === 'checking'
            }">
              <div class="flex items-center gap-3">
                <span class="text-2xl">
                  {{ ragServiceStatus === 'online' ? '✅' : ragServiceStatus === 'offline' ? '❌' : '⏳' }}
                </span>
                <div>
                  <div class="font-bold">Python RAG 服务</div>
                  <div class="text-sm text-gray-600">
                    {{ ragServiceStatus === 'online' ? '运行中 (已通过后端代理)' : 
                       ragServiceStatus === 'offline' ? '未连接 - 请启动服务' : '检查中...' }}
                  </div>
                </div>
                <el-button size="small" @click="checkRagService" class="ml-auto">刷新</el-button>
              </div>
            </div>
            
            <!-- 同步电影 -->
            <div class="mb-6">
              <h4 class="font-bold mb-3">🎬 同步电影数据到向量库</h4>
              <p class="text-sm text-gray-600 mb-4">
                将 MySQL 中的电影数据 (t_film) 向量化后存入 Milvus，用于语义检索。
              </p>
              <el-button 
                type="primary" 
                :loading="syncLoading" 
                :disabled="ragServiceStatus !== 'online'"
                @click="handleSyncFilms"
                class="!bg-pop-blue !border-2 !border-black !font-bold"
              >
                <el-icon class="mr-1"><Refresh /></el-icon>
                {{ syncLoading ? '同步中...' : '开始同步' }}
              </el-button>
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
            <div class="bg-gray-50 border-2 border-black rounded-xl p-4">
              <h4 class="font-bold mb-2">💡 使用说明</h4>
              <ol class="text-sm text-gray-700 space-y-2 list-decimal list-inside">
                <li>启动 Python 服务: <code class="bg-gray-200 px-1 rounded">cd jelly-rag-python && uvicorn main:app --port 8500</code></li>
                <li>点击"开始同步"将电影数据向量化</li>
                <li>在右侧输入查询进行语义搜索</li>
                <li>在 AI 对话中勾选"启用 RAG"可自动检索知识库</li>
              </ol>
            </div>
          </div>
          
          <!-- 右侧：知识库检索 -->
          <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6 flex flex-col">
            <h3 class="text-xl font-black mb-6 flex items-center gap-2">
              🔍 知识库检索测试
            </h3>
            
            <!-- 搜索框 -->
            <div class="flex gap-3 mb-6">
              <el-input 
                v-model="ragQuery" 
                placeholder="输入查询内容，如：科幻电影、刘德华..." 
                size="large"
                :disabled="ragServiceStatus !== 'online'"
                @keyup.enter="handleRagSearch"
              />
              <el-button 
                type="primary" 
                :loading="ragLoading" 
                :disabled="ragServiceStatus !== 'online' || !ragQuery.trim()"
                @click="handleRagSearch"
                class="!bg-pop-green !text-black !border-2 !border-black !font-bold"
              >
                搜索
              </el-button>
            </div>
            
            <!-- 搜索结果 -->
            <div class="flex-1 overflow-y-auto space-y-4" style="max-height: 400px;">
              <div v-if="ragLoading" class="flex items-center justify-center py-12">
                <el-icon class="is-loading text-4xl text-pop-purple"><Loading /></el-icon>
              </div>
              
              <div v-else-if="ragResults.length > 0">
                <div 
                  v-for="(result, idx) in ragResults" 
                  :key="idx"
                  class="p-4 rounded-xl border-2 border-black bg-nb-bg hover:shadow-brutal-sm transition-shadow cursor-pointer rag-result"
                  @click="goToFilmDetail(result.film_id)"
                >
                  <div class="flex items-start justify-between mb-2">
                    <h4 class="font-bold text-lg">{{ result.title }}</h4>
                    <span class="text-xs bg-pop-purple text-white px-2 py-1 rounded-full">
                      相关度: {{ (result.score * 100).toFixed(1) }}%
                    </span>
                  </div>
                  <p class="text-sm text-gray-600 line-clamp-3">{{ result.content }}</p>
                  <div class="mt-2 text-xs text-gray-400">ID: {{ result.film_id }}</div>
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
  color: #1a1a1a;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Source Han Sans SC', 'Noto Sans SC', 'Helvetica Neue', sans-serif;
  word-wrap: break-word;
  overflow-wrap: break-word;
  white-space: normal;
  line-height: 1.7;
  letter-spacing: 0.01em;
}

.ai-chat-bubble {
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Source Han Sans SC', 'Noto Sans SC', 'Helvetica Neue', sans-serif;
}

.ai-chat-text {
  color: #1a1a1a;
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
  background-color: rgba(255, 255, 255, 0.1);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: monospace;
}
.markdown-body :deep(pre) {
  background-color: rgba(0, 0, 0, 0.3);
  padding: 1em;
  border-radius: 8px;
  overflow-x: auto;
  margin-bottom: 0.5em;
}
.markdown-body :deep(pre code) {
  background-color: transparent;
  padding: 0;
}

.rag-result {
  position: relative;
}

.rag-result::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0));
  opacity: 0.7;
  pointer-events: none;
}
</style>
