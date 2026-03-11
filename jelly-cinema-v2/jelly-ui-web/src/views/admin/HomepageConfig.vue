<script setup lang="ts">
import { ref, computed } from 'vue'
import { get, post } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Check, View } from '@element-plus/icons-vue'

interface ConfigVersion {
  id: number
  version: string
  status: string
  category: string
  configJson: string
  checksum: string
  publishNote: string
  publishedAt: string
  createTime: string
}

interface SlotConfig {
  slot_id: string
  section_type: string
  position: number
  locked: number
  status: string
  content?: {
    title: string
    cover_url: string
    rating: number
    year: number
  }
}

const loading = ref(false)
const currentDraft = ref<ConfigVersion | null>(null)
const draftSlots = ref<SlotConfig[]>([])
const activeCategory = ref('movie')
const activeSection = ref('all')

// 生成草案
async function generateDraft() {
  loading.value = true
  try {
    const res = await post('/admin/homepage/config/generate-draft', null, {
      params: { category: activeCategory.value, createdBy: 'admin' }
    })
    const draft = res.data
    currentDraft.value = draft
    if (draft.configJson) {
      draftSlots.value = JSON.parse(draft.configJson)
    }
    ElMessage.success('草案生成成功: ' + draft.version)
  } catch (error: any) {
    ElMessage.error(error.message || '生成失败')
  } finally {
    loading.value = false
  }
}

// 切换分类
function handleCategoryChange() {
  draftSlots.value = []
  currentDraft.value = null
  activeSection.value = 'all'
}

// 发布草案
async function publishDraft() {
  if (!currentDraft.value) return
  
  await ElMessageBox.confirm(
    `确定要发布版本 ${currentDraft.value.version} 吗？`,
    '发布确认',
    { type: 'warning' }
  )
  
  loading.value = true
  try {
    await post(`/admin/homepage/config/publish/${currentDraft.value.id}`)
    ElMessage.success('发布成功！首页内容已更新')
    currentDraft.value = null
    draftSlots.value = []
  } catch (error: any) {
    ElMessage.error(error.message || '发布失败')
  } finally {
    loading.value = false
  }
}

// 计算属性：按板块分组
const groupedSlots = computed(() => {
  const groups: Record<string, SlotConfig[]> = {}
  draftSlots.value.forEach(slot => {
    if (!groups[slot.section_type]) groups[slot.section_type] = []
    groups[slot.section_type].push(slot)
  })
  return groups
})

// 过滤后的板块
const filteredSlots = computed(() => {
  const allGroups = groupedSlots.value
  if (activeSection.value === 'all') {
    return allGroups
  }
  const result: Record<string, SlotConfig[]> = {}
  if (allGroups[activeSection.value]) {
    result[activeSection.value] = allGroups[activeSection.value]
  }
  return result
})

// 板块名称映射
const sectionLabels: Record<string, string> = {
  'recommend': '推荐板块',
  'hot': '热门榜单',
  'new': '新片上架',
  'trending': '趋势话题',
  'ai_best': 'AI精选(最优推荐)'
}
</script>

<template>
  <div class="homepage-config">
    <!-- 顶部操作栏 -->
    <div class="header-bar">
      <h2 class="page-title">首页内容运营</h2>
      <div class="actions">
        <!-- 板块筛选 -->
        <el-radio-group v-model="activeSection" class="section-filter" v-if="currentDraft">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="ai_best">AI精选</el-radio-button>
          <el-radio-button value="recommend">推荐</el-radio-button>
          <el-radio-button value="hot">热门</el-radio-button>
          <el-radio-button value="new">最新</el-radio-button>
          <el-radio-button value="trending">趋势</el-radio-button>
        </el-radio-group>

        <el-divider direction="vertical" />

        <el-radio-group v-model="activeCategory" @change="handleCategoryChange" class="category-switch">
          <el-radio-button value="movie">电影</el-radio-button>
          <el-radio-button value="tv_series">电视剧</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="generateDraft" :loading="loading" class="generate-btn">
          <el-icon><Refresh /></el-icon>
          <span>生成新草案</span>
        </el-button>
      </div>
    </div>

    <!-- 草案预览区域 -->
    <div v-if="currentDraft" class="draft-panel">
      <div class="draft-header">
        <div class="draft-info">
          <span class="draft-badge">草案</span>
          <span class="version-text">{{ currentDraft.version }}</span>
          <span class="checksum-text">Checksum: {{ currentDraft.checksum || 'N/A' }}</span>
        </div>
        <el-button type="success" @click="publishDraft" :loading="loading" class="publish-btn">
          <el-icon><Check /></el-icon>
          <span>发布上线</span>
        </el-button>
      </div>

      <div v-for="(slots, section) in filteredSlots" :key="section" class="section-block">
        <h3 class="section-title">
          {{ sectionLabels[section] || section.toUpperCase() }}
          <span class="section-count">({{ slots.length }})</span>
        </h3>
        <div class="slots-grid">
          <div v-for="slot in slots" :key="slot.slot_id" 
               class="slot-card"
               :class="{ 'slot-filled': slot.status === 'filled', 'slot-empty': slot.status === 'empty', 'is-ai-best': section === 'ai_best' }">
            
            <div class="slot-position">#{{ slot.position }}</div>
            
            <template v-if="slot.content">
              <el-image :src="slot.content.cover_url" fit="cover" class="slot-cover">
                <template #error>
                  <div class="image-placeholder">无封面</div>
                </template>
              </el-image>
              <div class="slot-info">
                <div class="slot-title" :title="slot.content.title">{{ slot.content.title }}</div>
                <div class="slot-meta">
                  <span>{{ slot.content.year || '未知' }}</span>
                  <span class="rating-badge">{{ slot.content.rating || 0 }}</span>
                </div>
                <div v-if="slot.content.aiScore" class="ai-score-tag">
                    AI {{ slot.content.aiScore }}
                </div>
              </div>
            </template>
            
            <div v-else class="slot-placeholder">
              <span>空缺</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <el-icon :size="64" color="#909399"><View /></el-icon>
      <p>暂无草案</p>
      <p class="hint">请点击上方"生成新草案"按钮开始</p>
    </div>
  </div>
</template>

<style scoped>
.homepage-config {}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px 24px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.category-switch :deep(.el-radio-button__inner) {
  font-weight: 500;
}

.generate-btn {
  font-weight: 600;
  padding: 10px 20px;
}

.generate-btn .el-icon {
  margin-right: 6px;
}

.draft-panel {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
}

.draft-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 24px;
}

.draft-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.draft-badge {
  background: var(--color-warning);
  color: #fff;
  padding: 4px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
}

.version-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.checksum-text {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
  font-family: 'SF Mono', 'Courier New', monospace;
}

.publish-btn {
  font-weight: 600;
  padding: 10px 24px;
}

.publish-btn .el-icon {
  margin-right: 6px;
}

.section-block {
  margin-bottom: 32px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px 0;
  padding: 8px 16px;
  background: var(--bg-base);
  border-left: 4px solid var(--color-primary);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

.slots-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

@media (max-width: 1400px) {
  .slots-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

.slot-card {
  background: var(--bg-base);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px;
  position: relative;
  transition: all var(--duration-fast) var(--ease-apple);
}

.slot-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.slot-card.slot-filled {
  background: rgba(52, 199, 89, 0.08);
  border-color: var(--color-success);
}

.slot-card.slot-empty {
  background: rgba(255, 59, 48, 0.06);
  border-color: var(--color-danger);
}

.slot-position {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  font-weight: 400;
  color: var(--text-tertiary);
  font-family: monospace;
}

.slot-cover {
  width: 100%;
  height: 140px;
  border-radius: var(--radius-sm);
  margin-bottom: 10px;
  background: var(--border-color);
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 400;
  background: var(--bg-base);
}

.slot-info {
  text-align: center;
}

.slot-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.slot-meta {
  font-size: 11px;
  font-weight: 400;
  color: var(--text-secondary);
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.slot-placeholder {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 14px;
  font-style: italic;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.empty-state p {
  margin: 16px 0 0 0;
  font-size: 16px;
  font-weight: 400;
  color: var(--text-secondary);
}

.empty-state .hint {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-tertiary);
  margin-top: 8px;
}

.section-count {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-tertiary);
  margin-left: 8px;
}

.rating-badge {
  background: var(--color-warning);
  color: white;
  padding: 1px 4px;
  border-radius: var(--radius-sm);
  font-weight: 600;
}

.ai-score-tag {
  margin-top: 6px;
  background: var(--color-info);
  color: white;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: var(--radius-full);
  display: inline-block;
  font-weight: 600;
}

.slot-card.is-ai-best {
  border-color: var(--color-info);
  background: rgba(88, 86, 214, 0.06);
}

.section-filter :deep(.el-radio-button__inner) {
  padding: 8px 16px;
}
</style>
