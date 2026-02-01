<template>
  <div class="evidence-list" v-if="evidence?.length">
    <div class="evidence-header">
      <span class="header-icon">📚</span>
      <span class="header-title">参考来源</span>
    </div>
    
    <div class="evidence-items">
      <div 
        v-for="(item, index) in evidence" 
        :key="index"
        class="evidence-item"
        :class="[`kind-${item.kind}`]"
      >
        <div class="item-badge">
          {{ kindLabel(item.kind) }}
        </div>
        <div class="item-content">
          <div class="item-snippet">{{ item.snippet }}</div>
          <div class="item-meta" v-if="item.ref || item.updatedAt">
            <span v-if="item.ref" class="meta-ref">{{ item.ref }}</span>
            <span v-if="item.updatedAt" class="meta-time">{{ formatTime(item.updatedAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Evidence } from '@/api/ai'

defineProps<{
  evidence?: Evidence[]
}>()

function kindLabel(kind: string): string {
  const labels: Record<string, string> = {
    'db': '数据库',
    'api': 'API',
    'rag': '知识库'
  }
  return labels[kind] || kind.toUpperCase()
}

function formatTime(isoString: string): string {
  try {
    const date = new Date(isoString)
    return date.toLocaleDateString('zh-CN')
  } catch {
    return isoString
  }
}
</script>

<style scoped>
.evidence-list {
  margin-top: 12px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.evidence-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}

.header-icon {
  font-size: 14px;
}

.evidence-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.evidence-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  border-left: 3px solid rgba(100, 140, 255, 0.5);
  transition: background 0.2s ease;
}

.evidence-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.evidence-item.kind-db {
  border-left-color: #10b981;
}

.evidence-item.kind-api {
  border-left-color: #f59e0b;
}

.evidence-item.kind-rag {
  border-left-color: #8b5cf6;
}

.item-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 4px;
  text-transform: uppercase;
}

.kind-db .item-badge {
  background: rgba(16, 185, 129, 0.2);
  color: #10b981;
}

.kind-api .item-badge {
  background: rgba(245, 158, 11, 0.2);
  color: #f59e0b;
}

.kind-rag .item-badge {
  background: rgba(139, 92, 246, 0.2);
  color: #8b5cf6;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-snippet {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
  line-height: 1.5;
}

.item-meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
}

.meta-ref {
  font-family: monospace;
}
</style>
