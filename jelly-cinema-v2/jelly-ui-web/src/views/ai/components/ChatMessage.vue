<template>
  <div class="chat-message" :class="[`role-${message.role}`, { streaming: message.isStreaming }]">
    <!-- 头像 -->
    <div class="message-avatar">
      <span v-if="message.role === 'user'" class="avatar-icon">👤</span>
      <span v-else class="avatar-icon ai-avatar">🤖</span>
    </div>
    
    <!-- 消息内容 -->
    <div class="message-body">
      <!-- 文本内容 -->
      <div class="message-text" v-html="formattedContent"></div>
      
      <!-- 电影卡片 -->
      <MovieCard 
        v-if="message.movie && message.role === 'assistant'"
        :movie="message.movie"
        :play-entry="message.playEntry"
        class="message-movie-card"
      />
      
      <!-- 证据来源 -->
      <EvidenceList
        v-if="message.evidence?.length && message.role === 'assistant'"
        :evidence="message.evidence"
      />
      
      <!-- 诊断面板 -->
      <DiagnosticsPanel
        v-if="message.diagnostics && message.role === 'assistant'"
        :diagnostics="message.diagnostics"
        @action="handleDiagAction"
      />
      
      <!-- 快捷建议 -->
      <div 
        v-if="suggestions?.length && message.role === 'assistant'" 
        class="message-suggestions"
      >
        <button
          v-for="(sug, i) in suggestions"
          :key="i"
          class="suggestion-btn"
          @click="$emit('suggestion', sug)"
        >
          {{ sug }}
        </button>
      </div>
      
      <!-- 时间戳 -->
      <div class="message-time">
        {{ formatTime(message.timestamp) }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage as ChatMessageType } from '@/api/ai'
import MovieCard from './MovieCard.vue'
import EvidenceList from './EvidenceList.vue'
import DiagnosticsPanel from './DiagnosticsPanel.vue'

const props = defineProps<{
  message: ChatMessageType
  suggestions?: string[]
}>()

const emit = defineEmits<{
  (e: 'suggestion', text: string): void
  (e: 'action', action: string): void
}>()

// 格式化内容（支持简单 Markdown）
const formattedContent = computed(() => {
  let text = props.message.content || ''
  
  // 转义 HTML
  text = text.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  
  // 粗体 **text**
  text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  
  // 斜体 *text*
  text = text.replace(/\*(.+?)\*/g, '<em>$1</em>')
  
  // 换行
  text = text.replace(/\n/g, '<br>')
  
  return text
})

// 格式化时间
function formatTime(timestamp: number): string {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 处理诊断操作
function handleDiagAction(action: string) {
  emit('action', action)
}
</script>

<style scoped>
.chat-message {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.chat-message.role-user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 20px;
}

.role-user .message-avatar {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
}

.role-assistant .message-avatar {
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
}

.ai-avatar {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.message-body {
  flex: 1;
  max-width: 80%;
  min-width: 0;
}

.message-text {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.6;
  word-wrap: break-word;
}

.role-user .message-text {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: white;
  border-bottom-right-radius: 6px;
}

.role-assistant .message-text {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-bottom-left-radius: 6px;
}

.streaming .message-text::after {
  content: '▋';
  animation: blink 1s infinite;
  color: #8b5cf6;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.message-movie-card {
  margin-top: 12px;
}

.message-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.suggestion-btn {
  padding: 8px 16px;
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.3);
  border-radius: 20px;
  font-size: 13px;
  color: #a78bfa;
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-btn:hover {
  background: rgba(139, 92, 246, 0.25);
  border-color: rgba(139, 92, 246, 0.5);
  color: #c4b5fd;
  transform: translateY(-2px);
}

.message-time {
  margin-top: 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
}

.role-user .message-time {
  text-align: right;
}
</style>
