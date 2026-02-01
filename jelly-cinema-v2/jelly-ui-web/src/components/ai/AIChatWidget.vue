<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useAIChat } from '@/composables/useAIChat'
import { useUserStore } from '@/stores/user'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

// Props
interface Props {
  defaultOpen?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  defaultOpen: false
})

// Stores & Composables
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 注入当前页面上下文
const context = ref({
  currentPage: route.fullPath,
  movieId: route.params.id as string
})

// 监听路由变化更新上下文
watch(() => route.fullPath, () => {
  context.value = {
    currentPage: route.fullPath,
    movieId: route.params.id as string
  }
})

const {
  messages,
  isLoading,
  sendMessage,
  stopStreaming,
  clearMessages,
  addQuickQuestion,
  hasPlayEntry,
  lastAssistantMessage
} = useAIChat({
  streaming: true,
  context: context.value
})

// UI State
const isOpen = ref(props.defaultOpen)
const inputMessage = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const showEvidence = ref<string | null>(null) // ID of message to show evidence for

// Quick Actions
const quickActions = [
  { label: '找部高分科幻片', icon: 'Film' },
  { label: '推荐类似《星际穿越》的电影', icon: 'Star' },
  { label: '为什么视频无法播放？', icon: 'Warning' },
  { label: '帮我找4K片源', icon: 'VideoPlay' }
]

// Methods
function toggleChat() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    scrollToBottom()
  }
}

async function handleSend() {
  if (!inputMessage.value.trim() || isLoading.value) return
  
  const content = inputMessage.value
  inputMessage.value = ''
  
  await sendMessage(content)
  scrollToBottom()
}

function handleQuickAction(action: string) {
  addQuickQuestion(action)
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      const scrollHeight = messagesContainer.value.scrollHeight
      messagesContainer.value.scrollTo({
        top: scrollHeight,
        behavior: 'smooth'
      })
    }
  })
}

// Watch messages to auto-scroll
watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value[messages.value.length - 1]?.content, () => {
  // Fluid scroll during streaming
  if (isLoading.value && messagesContainer.value) {
    const { scrollHeight, scrollTop, clientHeight } = messagesContainer.value
    // Only auto-scroll if user is near bottom
    if (scrollHeight - scrollTop - clientHeight < 100) {
      messagesContainer.value.scrollTop = scrollHeight
    }
  }
})

// Formatting
function formatTime(timestamp: number) {
  return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

// Play Entry Navigation
function handlePlayEntry(entry: any) {
  if (entry.type === 'route') {
    router.push(entry.value)
  } else if (entry.type === 'url' || entry.type === 'deeplink') {
    window.open(entry.value, '_blank')
  }
  isOpen.value = false // Optional: close chat on navigate
}

</script>

<template>
  <div class="ai-chat-widget">
    <!-- Floating Toggle Button -->
    <button 
      class="chat-toggle-btn"
      :class="{ 'is-open': isOpen }"
      @click="toggleChat"
    >
      <div class="btn-content">
        <el-icon v-if="!isOpen" size="24"><ChatDotRound /></el-icon>
        <el-icon v-else size="24"><Close /></el-icon>
        <span v-if="!isOpen" class="btn-label">AI 助手</span>
      </div>
    </button>

    <!-- Chat Window -->
    <transition name="chat-slide">
      <div v-if="isOpen" class="chat-window glass-panel">
        <!-- Header -->
        <div class="chat-header">
          <div class="header-left">
            <div class="ai-avatar">
              <el-icon size="20"><MagicStick /></el-icon>
            </div>
            <div class="header-info">
              <h3>果冻 AI 助手</h3>
              <span class="status-dot"></span>
              <span class="status-text">在线</span>
            </div>
          </div>
          <div class="header-actions">
            <button class="icon-btn" @click="clearMessages" title="清空对话">
              <el-icon><Delete /></el-icon>
            </button>
            <button class="icon-btn" @click="isOpen = false">
              <el-icon><ArrowDown /></el-icon>
            </button>
          </div>
        </div>

        <!-- Messages Area -->
        <div class="messages-area" ref="messagesContainer">
          <!-- Welcome Message -->
          <div v-if="messages.length === 0" class="welcome-screen">
            <div class="welcome-icon">👋</div>
            <h2>你好，{{ userStore.nickname || '朋友' }}</h2>
            <p>我是您的智能观影助手，可以帮您找电影、解决播放问题或推荐内容。</p>
            
            <div class="quick-actions-grid">
              <button 
                v-for="(action, idx) in quickActions" 
                :key="idx"
                class="quick-action-card"
                @click="handleQuickAction(action.label)"
              >
                <el-icon><component :is="action.icon" /></el-icon>
                <span>{{ action.label }}</span>
              </button>
            </div>
          </div>

          <!-- Message List -->
          <div v-else class="message-list">
            <div 
              v-for="msg in messages" 
              :key="msg.id"
              class="message-wrapper"
              :class="msg.role"
            >
              <div class="message-avatar">
                <el-avatar v-if="msg.role === 'user'" :size="32" :src="userStore.avatar" />
                <div v-else class="ai-avatar-small">
                  <el-icon><MagicStick /></el-icon>
                </div>
              </div>
              
              <div class="message-content-group">
                <div class="message-bubble">
                  <!-- Movie Card inside Message -->
                  <div v-if="msg.movie" class="mini-movie-card" @click="router.push(`/film/${msg.movie.movieId}`)">
                    <img :src="msg.movie.coverUrl" class="mini-cover" />
                    <div class="mini-info">
                      <h4>{{ msg.movie.title }}</h4>
                      <span>{{ msg.movie.year }} · {{ msg.movie.rating }}分</span>
                    </div>
                  </div>

                  <!-- Text Content -->
                  <div class="text-content" v-html="msg.content ? msg.content.replace(/\n/g, '<br>') : '...'"></div>
                  
                  <!-- Play Entry Button -->
                  <div v-if="msg.playEntry" class="play-entry-box">
                    <button 
                      class="play-btn" 
                      :class="{ 'verified': msg.playEntry.verified }"
                      @click="handlePlayEntry(msg.playEntry)"
                    >
                      <el-icon><VideoPlay /></el-icon>
                      {{ msg.playEntry.verified ? '立即播放' : '尝试播放' }}
                      <span v-if="msg.playEntry.quality" class="quality-tag">{{ msg.playEntry.quality }}</span>
                    </button>
                    <div v-if="!msg.playEntry.verified" class="unverified-tip">
                      <el-icon><Warning /></el-icon> 线路未验证，可能不稳定
                    </div>
                  </div>
                  
                  <!-- Loading Indicator for Streaming -->
                  <span v-if="msg.isStreaming" class="typing-cursor">|</span>
                </div>

                <!-- Footers: Time, Evidence Toggle -->
                <div class="message-footer">
                  <span class="time">{{ formatTime(msg.timestamp) }}</span>
                  <button 
                    v-if="msg.role === 'assistant' && msg.evidence?.length" 
                    class="evidence-toggle"
                    @click="showEvidence = showEvidence === msg.id ? null : msg.id"
                  >
                    {{ showEvidence === msg.id ? '收起证据' : '查看来源' }}
                    <el-icon><ArrowDown /></el-icon>
                  </button>
                </div>

                <!-- Evidence Panel -->
                <div v-if="showEvidence === msg.id" class="evidence-panel">
                  <div v-for="(ev, idx) in msg.evidence" :key="idx" class="evidence-item">
                    <span class="evidence-tag type-db" v-if="ev.kind === 'db'">数据库</span>
                    <span class="evidence-tag type-rag" v-if="ev.kind === 'rag'">知识库</span>
                    <p>{{ ev.snippet }}</p>
                  </div>
                </div>

                <!-- Diagnostics Panel (Auto-show on fail) -->
                <div v-if="msg.diagnostics && msg.diagnostics.status === 'fail'" class="diagnostics-panel">
                  <div class="error-header">
                    <el-icon><CircleCloseFilled /></el-icon>
                    <span>遇到问题: {{ msg.diagnostics.reasonText || '未知错误' }}</span>
                  </div>
                  <div v-if="msg.diagnostics.nextActions?.length" class="suggestions">
                    <p>建议操作：</p>
                    <ul>
                      <li v-for="action in msg.diagnostics.nextActions" :key="action">{{ action }}</li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Input Area -->
        <div class="chat-input-area">
          <div class="input-wrapper">
            <textarea
              v-model="inputMessage"
              placeholder="输入消息..."
              @keydown.enter.prevent="handleSend"
              :disabled="isLoading"
              class="chat-textarea"
            ></textarea>
            <button 
              class="send-btn" 
              :class="{ 'is-loading': isLoading }"
              :disabled="!inputMessage.trim() && !isLoading"
              @click="isLoading ? stopStreaming() : handleSend()"
            >
              <el-icon v-if="!isLoading"><Position /></el-icon>
              <el-icon v-else class="spinning"><Loading /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
/* ─── Variables ─── */
.ai-chat-widget {
  --primary-color: #3b82f6;
  --bg-glass: rgba(255, 255, 255, 0.7);
  --bg-glass-dark: rgba(15, 23, 42, 0.72);
  --border-light: rgba(255, 255, 255, 0.55);
  --shadow-lg: 0 14px 40px -18px rgba(0,0,0,0.35);
  
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 9999;
  font-family: 'Inter', system-ui, sans-serif;
}

/* ─── Toggle Button ─── */
.chat-toggle-btn {
  width: 60px;
  height: 60px;
  border-radius: 30px;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.9), rgba(59, 130, 246, 0.85));
  border: none;
  color: white;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.25);
  transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-toggle-btn:hover {
  transform: scale(1.05) translateY(-2px);
  box-shadow: 0 14px 30px rgba(59, 130, 246, 0.3);
}

.chat-toggle-btn.is-open {
  transform: rotate(90deg) scale(0.9);
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.75), rgba(71, 85, 105, 0.9));
}

.btn-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.btn-label {
  font-size: 10px;
  font-weight: 600;
}

/* ─── Chat Window ─── */
.chat-window {
  position: absolute;
  bottom: 80px;
  right: 0;
  width: 380px;
  height: 600px;
  max-height: 80vh;
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  border: 1px solid var(--border-light);
  border-radius: 24px;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transform-origin: bottom right;
}

.dark .chat-window {
  background: var(--bg-glass-dark);
  border-color: rgba(255, 255, 255, 0.1);
  color: #f1f5f9;
}

/* ─── Header ─── */
.chat-header {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255,255,255,0.25);
}

.dark .chat-header {
  border-bottom-color: rgba(255,255,255,0.05);
  background: rgba(0,0,0,0.2);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.9), rgba(34, 211, 238, 0.9));
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.header-info h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.2;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: #22c55e;
  border-radius: 50%;
  margin-right: 4px;
}

.status-text {
  font-size: 12px;
  color: #64748b;
}

.icon-btn {
  background: transparent;
  border: none;
  padding: 8px;
  border-radius: 8px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: rgba(0,0,0,0.05);
  color: #0f172a;
}

/* ─── Messages Area ─── */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  scroll-behavior: smooth;
}

.messages-area::-webkit-scrollbar {
  width: 6px;
}
.messages-area::-webkit-scrollbar-thumb {
  background: rgba(0,0,0,0.1);
  border-radius: 3px;
}

/* Welcome Screen */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding-top: 40px;
}

.welcome-icon {
  font-size: 40px;
  margin-bottom: 16px;
  animation: wave 2s infinite;
}

@keyframes wave {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(15deg); }
  75% { transform: rotate(-10deg); }
}

.welcome-screen h2 {
  font-size: 20px;
  margin-bottom: 8px;
}

.welcome-screen p {
  color: #64748b;
  font-size: 14px;
  margin-bottom: 32px;
  max-width: 80%;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.quick-action-card {
  background: white;
  border: 1px solid rgba(0,0,0,0.05);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: #334155;
  font-size: 12px;
  text-align: center;
}

.dark .quick-action-card {
  background: rgba(255,255,255,0.05);
  color: #cbd5e1;
  border-color: rgba(255,255,255,0.1);
}

.quick-action-card:hover {
  background: #f8fafc;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  color: var(--primary-color);
}

.dark .quick-action-card:hover {
  background: rgba(255,255,255,0.1);
}

/* Message List */
.message-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.ai-avatar-small {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.9), rgba(34, 211, 238, 0.9));
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.message-content-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 80%;
}

.message-wrapper.user .message-content-group {
  align-items: flex-end;
}

.message-bubble {
  background: white;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.5;
  color: #1e293b;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
  position: relative;
}

.dark .message-bubble {
  background: rgba(255,255,255,0.1);
  color: #f1f5f9;
}

.message-wrapper.user .message-bubble {
  background: var(--primary-color);
  color: white;
  border-bottom-right-radius: 4px;
}

.message-wrapper.assistant .message-bubble {
  border-top-left-radius: 4px;
}

/* Mini Movie Card */
.mini-movie-card {
  display: flex;
  gap: 10px;
  background: rgba(0,0,0,0.03);
  padding: 8px;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.mini-movie-card:hover {
  background: rgba(0,0,0,0.06);
}

.mini-cover {
  width: 40px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
}

.mini-info h4 {
  margin: 0 0 4px 0;
  font-size: 13px;
}

.mini-info span {
  font-size: 11px;
  color: #64748b;
}

/* Play Entry */
.play-entry-box {
  margin-top: 10px;
}

.play-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  background: #22c55e;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
  font-size: 13px;
  transition: all 0.2s;
}

.play-btn:hover {
  background: #16a34a;
}

.unverified-tip {
  font-size: 10px;
  color: #f59e0b;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Typing Cursor */
.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: currentColor;
  margin-left: 2px;
  vertical-align: middle;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* Evidence & Diagnostics */
.message-footer {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #94a3b8;
  padding: 0 4px;
}

.evidence-toggle {
  background: none;
  border: none;
  color: #6366f1;
  cursor: pointer;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.evidence-panel {
  background: rgba(0,0,0,0.02);
  border-radius: 8px;
  padding: 8px;
  margin-top: 8px;
  font-size: 11px;
}

.evidence-item {
  margin-bottom: 6px;
  border-left: 2px solid #cbd5e1;
  padding-left: 8px;
}

.evidence-tag {
  display: inline-block;
  padding: 1px 4px;
  border-radius: 4px;
  font-size: 10px;
  margin-bottom: 2px;
}

.type-db { background: #dbeafe; color: #1e40af; }
.type-rag { background: #f3e8ff; color: #6b21a8; }

.diagnostics-panel {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 8px;
  font-size: 12px;
  color: #b91c1c;
}

/* ─── Input Area ─── */
.chat-input-area {
  padding: 16px;
  border-top: 1px solid rgba(0,0,0,0.05);
  background: rgba(255,255,255,0.4);
}

.dark .chat-input-area {
  border-top-color: rgba(255,255,255,0.05);
  background: rgba(0,0,0,0.2);
}

.input-wrapper {
  background: white;
  border: 1px solid rgba(0,0,0,0.1);
  border-radius: 20px;
  padding: 8px 12px;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  transition: all 0.2s;
}

.dark .input-wrapper {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.1);
}

.input-wrapper:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.1);
}

.chat-textarea {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  height: 20px;
  max-height: 80px;
  padding: 0;
  font-family: inherit;
  font-size: 14px;
  line-height: 20px;
  outline: none;
  color: #1e293b;
}

.dark .chat-textarea {
  color: #f1f5f9;
}

.send-btn {
  background: var(--primary-color);
  color: white;
  border: none;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.send-btn:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Transitions */
.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.165, 0.84, 0.44, 1);
}

.chat-slide-enter-from,
.chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
</style>
