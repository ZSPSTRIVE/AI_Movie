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
.homepage-config {
  padding: 24px;
  background: #f5f7fa;
  min-height: 100vh;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
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

/* 草案面板 */
.draft-panel {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.draft-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 24px;
}

.draft-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.draft-badge {
  background: linear-gradient(135deg, #ff9a44 0%, #fc6076 100%);
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.version-text {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.checksum-text {
  font-size: 12px;
  color: #909399;
  font-family: 'Courier New', monospace;
}

.publish-btn {
  font-weight: 600;
  padding: 10px 24px;
}

.publish-btn .el-icon {
  margin-right: 6px;
}

/* 板块 */
.section-block {
  margin-bottom: 32px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
  padding: 8px 16px;
  background: #f0f2f5;
  border-left: 4px solid #409eff;
  border-radius: 0 8px 8px 0;
}

/* 坑位网格 */
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

/* 坑位卡片 */
.slot-card {
  background: #fafafa;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  position: relative;
  transition: all 0.2s;
}

.slot-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.slot-card.slot-filled {
  background: #f0f9eb;
  border-color: #67c23a;
}

.slot-card.slot-empty {
  background: #fef0f0;
  border-color: #f56c6c;
}

.slot-position {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  color: #909399;
  font-family: monospace;
}

.slot-cover {
  width: 100%;
  height: 140px;
  border-radius: 6px;
  margin-bottom: 10px;
  background: #e4e7ed;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 12px;
  background: #f0f2f5;
}

.slot-info {
  text-align: center;
}

.slot-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.slot-meta {
  font-size: 11px;
  color: #606266;
}

.slot-placeholder {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 14px;
  font-style: italic;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.empty-state p {
  margin: 16px 0 0 0;
  font-size: 16px;
  color: #606266;
}

.empty-state .hint {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.section-count {
  font-size: 14px;
  color: #909399;
  margin-left: 8px;
  font-weight: normal;
}

.slot-meta {
  font-size: 11px;
  color: #606266;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.rating-badge {
  background: #ff9a44;
  color: white;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: bold;
}

.ai-score-tag {
  margin-top: 6px;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  color: white;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 10px;
  display: inline-block;
  font-weight: 600;
}

.slot-card.is-ai-best {
  border-color: #8b5cf6;
  background: #f5f3ff;
}

.section-filter :deep(.el-radio-button__inner) {
    padding: 8px 16px;
}
</style>
