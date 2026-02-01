<template>
  <div class="diagnostics-panel" :class="[`status-${diagnostics.status}`]">
    <div class="diag-header">
      <span class="status-icon">{{ statusIcon }}</span>
      <span class="status-text">{{ statusText }}</span>
    </div>
    
    <div class="diag-content" v-if="diagnostics.reasonText">
      <p class="reason-text">{{ diagnostics.reasonText }}</p>
    </div>
    
    <div class="diag-actions" v-if="diagnostics.nextActions?.length">
      <span class="actions-label">建议操作：</span>
      <div class="action-buttons">
        <button 
          v-for="(action, index) in diagnostics.nextActions" 
          :key="index"
          class="action-btn"
          @click="$emit('action', action)"
        >
          {{ action }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Diagnostics } from '@/api/ai'

const props = defineProps<{
  diagnostics: Diagnostics
}>()

defineEmits<{
  (e: 'action', action: string): void
}>()

const statusIcon = computed(() => {
  const icons = {
    'ok': '✅',
    'partial': '⚠️',
    'fail': '❌'
  }
  return icons[props.diagnostics.status] || '❓'
})

const statusText = computed(() => {
  const texts = {
    'ok': '诊断正常',
    'partial': '发现问题',
    'fail': '诊断失败'
  }
  return texts[props.diagnostics.status] || '未知状态'
})
</script>

<style scoped>
.diagnostics-panel {
  padding: 16px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.diagnostics-panel.status-ok {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1), rgba(16, 185, 129, 0.05));
  border-color: rgba(16, 185, 129, 0.3);
}

.diagnostics-panel.status-partial {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1), rgba(245, 158, 11, 0.05));
  border-color: rgba(245, 158, 11, 0.3);
}

.diagnostics-panel.status-fail {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.1), rgba(239, 68, 68, 0.05));
  border-color: rgba(239, 68, 68, 0.3);
}

.diag-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.status-ok .diag-header {
  color: #10b981;
}

.status-partial .diag-header {
  color: #f59e0b;
}

.status-fail .diag-header {
  color: #ef4444;
}

.status-icon {
  font-size: 18px;
}

.diag-content {
  margin-top: 12px;
}

.reason-text {
  margin: 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.6;
}

.diag-actions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.actions-label {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-btn {
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(100, 140, 255, 0.5);
  color: white;
}
</style>
