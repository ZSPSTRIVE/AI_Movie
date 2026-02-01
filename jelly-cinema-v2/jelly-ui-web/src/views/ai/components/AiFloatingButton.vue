<template>
  <Teleport to="body">
    <!-- 悬浮按钮 -->
    <button 
      class="ai-floating-btn"
      :class="{ active: isOpen }"
      @click="toggleChat"
    >
      <span class="btn-icon" v-if="!isOpen">🤖</span>
      <span class="btn-icon close-icon" v-else>✕</span>
      <span class="btn-pulse" v-if="!isOpen"></span>
    </button>
    
    <!-- 对话面板 -->
    <Transition name="slide-up">
      <div v-if="isOpen" class="ai-chat-panel">
        <div class="panel-header">
          <div class="header-title">
            <span class="title-icon">🎬</span>
            <span>果冻 AI 助手</span>
          </div>
          <div class="header-actions">
            <button class="action-btn" @click="clearMessages" title="清空对话">
              🗑️
            </button>
          </div>
        </div>
        
        <div class="panel-body" ref="messagesContainer">
          <!-- 欢迎消息 -->
          <div v-if="messages.length === 0" class="welcome-section">
            <div class="welcome-icon">🎬</div>
            <h3 class="welcome-title">你好，我是果冻 AI</h3>
            <p class="welcome-desc">我可以帮你搜索电影、推荐好片、解决播放问题</p>
            
            <div class="quick-actions">
              <button 
                v-for="action in quickActions" 
                :key="action"
                class="quick-btn"
                @click="sendMessage(action)"
              >
                {{ action }}
              </button>
            </div>
          </div>
          
          <!-- 消息列表 -->
          <ChatMessage
            v-for="msg in messages"
            :key="msg.id"
            :message="msg"
            :suggestions="msg === messages[messages.length - 1] ? lastSuggestions : undefined"
            @suggestion="sendMessage"
            @action="handleAction"
          />
          
          <!-- 加载指示器 -->
          <div v-if="isLoading" class="loading-indicator">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
        
        <div class="panel-footer">
          <div class="input-wrapper">
            <input
              v-model="inputText"
              type="text"
              class="chat-input"
              placeholder="问我任何关于电影的问题..."
              @keydown.enter="handleSend"
              :disabled="isLoading"
            />
            <button 
              class="send-btn"
              @click="handleSend"
              :disabled="!inputText.trim() || isLoading"
            >
              发送
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import ChatMessage from './ChatMessage.vue'
import { createChatStream } from '@/api/ai'
import type { ChatMessage as ChatMessageType, ChatResponse } from '@/api/ai'

const isOpen = ref(false)
const inputText = ref('')
const messages = ref<ChatMessageType[]>([])
const isLoading = ref(false)
const lastSuggestions = ref<string[]>([])
const messagesContainer = ref<HTMLElement | null>(null)

let abortController: { abort: () => void } | null = null

const quickActions = [
  '推荐科幻电影',
  '最近有什么新片',
  '播放卡顿怎么办'
]

// 切换面板
function toggleChat() {
  isOpen.value = !isOpen.value
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 发送消息
function sendMessage(text: string) {
  if (!text.trim() || isLoading.value) return
  
  // 添加用户消息
  const userMsg: ChatMessageType = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: text,
    timestamp: Date.now()
  }
  messages.value.push(userMsg)
  inputText.value = ''
  scrollToBottom()
  
  // 创建 AI 消息占位
  const aiMsgId = `ai-${Date.now()}`
  const aiMsg: ChatMessageType = {
    id: aiMsgId,
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    isStreaming: true
  }
  messages.value.push(aiMsg)
  isLoading.value = true
  lastSuggestions.value = []
  
  // 调用流式 API
  abortController = createChatStream(
    { message: text },
    {
      onMessage: (chunk, accumulated) => {
        const msg = messages.value.find(m => m.id === aiMsgId)
        if (msg) {
          msg.content = accumulated
          scrollToBottom()
        }
      },
      onComplete: (response: ChatResponse) => {
        const msg = messages.value.find(m => m.id === aiMsgId)
        if (msg) {
          msg.isStreaming = false
          msg.intent = response.intent
          msg.movie = response.movie
          msg.playEntry = response.playEntry
          msg.evidence = response.evidence
          msg.diagnostics = response.diagnostics
          lastSuggestions.value = response.suggestions || []
        }
        isLoading.value = false
        scrollToBottom()
      },
      onError: (error) => {
        const msg = messages.value.find(m => m.id === aiMsgId)
        if (msg) {
          msg.isStreaming = false
          msg.content = '抱歉，出现了一点问题，请稍后重试。'
        }
        isLoading.value = false
        console.error('Chat error:', error)
      }
    }
  )
}

function handleSend() {
  sendMessage(inputText.value)
}

function handleAction(action: string) {
  sendMessage(action)
}

function clearMessages() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  messages.value = []
  lastSuggestions.value = []
  isLoading.value = false
}

// 监听打开状态，自动聚焦输入框
watch(isOpen, (val) => {
  if (val) {
    nextTick(() => {
      const input = document.querySelector('.chat-input') as HTMLInputElement
      input?.focus()
    })
  }
})
</script>

<style scoped>
/* 悬浮按钮 */
.ai-floating-btn {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.4);
  cursor: pointer;
  z-index: 9999;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.ai-floating-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 12px 40px rgba(99, 102, 241, 0.5);
}

.ai-floating-btn.active {
  background: linear-gradient(135deg, #ef4444, #dc2626);
}

.btn-icon {
  font-size: 28px;
  line-height: 1;
}

.close-icon {
  font-size: 22px;
  color: white;
}

.btn-pulse {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 2px solid rgba(139, 92, 246, 0.5);
  animation: pulse-ring 2s ease-out infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.4); opacity: 0; }
}

/* 对话面板 */
.ai-chat-panel {
  position: fixed;
  bottom: 100px;
  right: 24px;
  width: 400px;
  max-width: calc(100vw - 48px);
  height: 600px;
  max-height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.5);
  z-index: 9998;
  overflow: hidden;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

/* Header */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: white;
}

.title-icon {
  font-size: 22px;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* Body */
.panel-body {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
}

.panel-body::-webkit-scrollbar {
  width: 6px;
}

.panel-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

/* Welcome */
.welcome-section {
  text-align: center;
  padding: 40px 20px;
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.welcome-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
  color: white;
}

.welcome-desc {
  margin: 0 0 24px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
}

.quick-btn {
  padding: 10px 18px;
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.3);
  border-radius: 20px;
  font-size: 14px;
  color: #a78bfa;
  cursor: pointer;
  transition: all 0.2s ease;
}

.quick-btn:hover {
  background: rgba(139, 92, 246, 0.25);
  transform: translateY(-2px);
}

/* Loading */
.loading-indicator {
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 16px;
}

.loading-indicator .dot {
  width: 8px;
  height: 8px;
  background: #8b5cf6;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-indicator .dot:nth-child(1) { animation-delay: -0.32s; }
.loading-indicator .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* Footer */
.panel-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(0, 0, 0, 0.2);
}

.input-wrapper {
  display: flex;
  gap: 10px;
}

.chat-input {
  flex: 1;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  font-size: 14px;
  color: white;
  outline: none;
  transition: all 0.2s ease;
}

.chat-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.chat-input:focus {
  border-color: rgba(139, 92, 246, 0.5);
  background: rgba(255, 255, 255, 0.1);
}

.send-btn {
  padding: 12px 20px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
