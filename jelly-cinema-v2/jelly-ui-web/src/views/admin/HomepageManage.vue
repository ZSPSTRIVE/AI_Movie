<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { get, post, put, del } from '@/utils/request'
import { normalizeImageUrl } from '@/utils/image'
import { ElMessage, ElMessageBox } from 'element-plus'

interface HomepageContent {
  id: number
  contentType: string
  sectionType: string
  tvboxId?: string
  title: string
  coverUrl: string
  description: string
  sourceName: string
  rating: number
  year: number
  region: string
  actors: string
  director: string
  sortOrder: number
  aiScore?: number
  aiReason?: string
  aiBest?: number
  status: number
  createTime: string
}

interface FilmCandidate {
  id: number | string
  title: string
  coverUrl: string
  description: string
  categoryName?: string
  rating?: number
  year?: number
  region?: string
  director?: string
  actors?: string
}

interface PromptPreset {
  label: string
  keyword: string
  contentType?: string
  sectionType?: string
}

const loading = ref(false)
const refreshLoading = ref(false)
const aiSortLoading = ref(false)
const tableData = ref<HomepageContent[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const contentType = ref('')
const sectionType = ref('')

const candidateKeyword = ref('')
const candidateLoading = ref(false)
const candidateResults = ref<FilmCandidate[]>([])
const candidateSection = ref('recommend')
const candidateContentType = ref('')
const candidateSortOrder = ref<number | undefined>()
const replaceExisting = ref(false)
const importingMap = ref<Record<string, boolean>>({})

const contentTypeOptions = [
  { label: '全部', value: '' },
  { label: '电影', value: 'movie' },
  { label: '电视剧', value: 'tv_series' },
  { label: '综艺', value: 'variety' },
  { label: '动漫', value: 'anime' }
]

const sectionTypeOptions = [
  { label: '全部', value: '' },
  { label: '推荐', value: 'recommend' },
  { label: '热门', value: 'hot' },
  { label: '新上线', value: 'new' },
  { label: '趋势', value: 'trending' }
]

const promptPresets: PromptPreset[] = [
  { label: 'AI 推荐新片', keyword: '2026 新片', contentType: 'movie', sectionType: 'new' },
  { label: 'AI 推荐剧集', keyword: '2026 电视剧', contentType: 'tv_series', sectionType: 'recommend' },
  { label: '悬疑热度片单', keyword: '悬疑', contentType: 'movie', sectionType: 'trending' },
  { label: '综艺补位', keyword: '综艺', contentType: 'variety', sectionType: 'hot' }
]

const activeCount = computed(() => tableData.value.filter(item => item.status === 1).length)
const aiBestCount = computed(() => tableData.value.filter(item => item.aiBest === 1 || (item.aiScore || 0) >= 70).length)
const latestYear = computed(() => {
  if (!tableData.value.length) return '--'
  return Math.max(...tableData.value.map(item => Number(item.year) || 0))
})
const currentSectionLabel = computed(() => getSectionTypeLabel(candidateSection.value))

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await get('/admin/homepage/list', {
      contentType: contentType.value || undefined,
      sectionType: sectionType.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    const rows = (res.data?.rows || []) as HomepageContent[]
    tableData.value = rows.map(item => ({
      ...item,
      coverUrl: normalizeImageUrl(item.coverUrl, item.title)
    }))
    total.value = Number(res.data?.total) || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  pageNum.value = 1
  loadData()
}

async function handleRefresh() {
  await ElMessageBox.confirm(
    '确定要刷新首页内容吗？系统会从采集源拉取候选内容并回写片库。',
    '刷新确认',
    { type: 'warning' }
  )

  refreshLoading.value = true
  try {
    await post('/admin/homepage/refresh')
    ElMessage.success('刷新任务已启动，片库与首页内容会异步更新')
    setTimeout(loadData, 3000)
  } catch (error: any) {
    ElMessage.error(error.message || '刷新失败')
  } finally {
    refreshLoading.value = false
  }
}

async function handleAiSort() {
  await ElMessageBox.confirm(
    '确定要进行 AI 智能排序吗？系统会优先调用 AI 服务，无可用模型时自动回退到本地排序策略。',
    'AI 排序确认',
    { type: 'info' }
  )

  aiSortLoading.value = true
  try {
    await post('/admin/homepage/ai-sort')
    ElMessage.success('AI 排序任务已启动，请稍后刷新查看结果')
    setTimeout(loadData, 3000)
  } catch (error: any) {
    ElMessage.error(error.message || 'AI 排序失败')
  } finally {
    aiSortLoading.value = false
  }
}

async function searchCandidates(keyword = candidateKeyword.value) {
  const query = keyword.trim()
  if (!query) {
    candidateResults.value = []
    ElMessage.warning('请输入选片需求或关键词')
    return
  }

  candidateLoading.value = true
  try {
    const res = await get('/film/search', { keyword: query })
    const data = (res.data || []) as FilmCandidate[]
    candidateResults.value = data.map(item => ({
      ...item,
      coverUrl: normalizeImageUrl(item.coverUrl, item.title)
    }))
    if (!candidateResults.value.length) {
      ElMessage.info('没有找到匹配片源，可以尝试更换关键词')
    }
  } catch (error: any) {
    candidateResults.value = []
    ElMessage.error(error.message || '候选片单加载失败')
  } finally {
    candidateLoading.value = false
  }
}

async function applyPrompt(preset: PromptPreset) {
  candidateKeyword.value = preset.keyword
  candidateContentType.value = preset.contentType || ''
  candidateSection.value = preset.sectionType || 'recommend'
  await searchCandidates(preset.keyword)
}

async function importCandidate(candidate: FilmCandidate) {
  if (replaceExisting.value && candidateSortOrder.value === undefined) {
    ElMessage.warning('替换模式下请先指定要替换的排序位')
    return
  }

  const requestKey = String(candidate.id)
  importingMap.value[requestKey] = true
  try {
    await post('/admin/homepage/import-film', {
      filmId: Number(candidate.id),
      sectionType: candidateSection.value,
      contentType: candidateContentType.value || undefined,
      sortOrder: replaceExisting.value ? candidateSortOrder.value : undefined,
      replaceExisting: replaceExisting.value
    })
    ElMessage.success(
      replaceExisting.value
        ? `已将《${candidate.title}》替换到 ${currentSectionLabel.value} 位`
        : `已将《${candidate.title}》加入 ${currentSectionLabel.value}`
    )
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '导入首页失败')
  } finally {
    importingMap.value[requestKey] = false
  }
}

async function toggleStatus(row: HomepageContent) {
  try {
    await put(`/admin/homepage/${row.id}/toggle-status`)
    row.status = row.status === 1 ? 0 : 1
    ElMessage.success(row.status === 1 ? '已上架' : '已下架')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function toggleBest(row: HomepageContent) {
  const next = row.aiBest === 1 ? 'false' : 'true'
  try {
    await put(`/admin/homepage/${row.id}/mark-best?isBest=${next}`)
    row.aiBest = row.aiBest === 1 ? 0 : 1
    ElMessage.success(row.aiBest === 1 ? '已加入 AI 精选' : '已移出 AI 精选')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function updateSort(row: HomepageContent, direction: 'up' | 'down') {
  const newOrder = direction === 'up' ? row.sortOrder - 1 : row.sortOrder + 1
  if (newOrder < 0) return

  try {
    await put(`/admin/homepage/${row.id}/sort?sortOrder=${newOrder}`)
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '排序失败')
  }
}

async function handleDelete(row: HomepageContent) {
  await ElMessageBox.confirm(`确定删除《${row.title}》吗？`, '删除确认', { type: 'warning' })
  try {
    await del(`/admin/homepage/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

function getContentTypeLabel(type: string) {
  const map: Record<string, string> = {
    movie: '电影',
    tv_series: '电视剧',
    variety: '综艺',
    anime: '动漫'
  }
  return map[type] || type || '未分类'
}

function getSectionTypeLabel(type: string) {
  const map: Record<string, string> = {
    recommend: '推荐',
    hot: '热门',
    new: '新上线',
    trending: '趋势'
  }
  return map[type] || type || '未配置'
}
</script>

<template>
  <div class="homepage-manage min-h-full p-6 md:p-8">
    <section class="hero-panel glass-panel">
      <div class="hero-copy">
        <div class="hero-badge">
          <span class="hero-dot"></span>
          Apple-style Content Desk
        </div>
        <h1 class="hero-title">首页内容管理</h1>
        <p class="hero-subtitle">
          把“采集、入库、AI 排序、首页投放”收敛到一个工作台。
          现在支持按需求搜片、从片库一键加入首页，以及按指定位置替换现有资源。
        </p>
      </div>

      <div class="hero-actions">
        <el-button :loading="refreshLoading" class="hero-action hero-action--secondary" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新候选库
        </el-button>
        <el-button :loading="aiSortLoading" class="hero-action hero-action--primary" @click="handleAiSort">
          <el-icon><svg-icon name="icon-zhuanshuguwen" /></el-icon>
          AI 智能排序
        </el-button>
      </div>

      <div class="hero-stats">
        <div class="stat-card">
          <span class="stat-label">当前页内容</span>
          <strong class="stat-value">{{ total }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">已上架</span>
          <strong class="stat-value">{{ activeCount }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">AI 精选</span>
          <strong class="stat-value">{{ aiBestCount }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">最新年份</span>
          <strong class="stat-value">{{ latestYear }}</strong>
        </div>
      </div>
    </section>

    <section class="workspace-grid">
      <div class="glass-panel curation-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">AI 选片台</p>
            <h2>按需求找片，然后直接投放到首页</h2>
          </div>
          <p class="panel-tip">输入题材、年份或片名，后端会优先查片库，缺数据时自动从采集源补库。</p>
        </div>

        <div class="search-shell">
          <el-input
            v-model="candidateKeyword"
            placeholder="例如：2026 新片、电视剧、悬疑、天堂岛疑云"
            size="large"
            clearable
            @keyup.enter="searchCandidates()"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button :loading="candidateLoading" class="search-btn" @click="searchCandidates()">
            搜索候选
          </el-button>
        </div>

        <div class="prompt-row">
          <button
            v-for="preset in promptPresets"
            :key="preset.label"
            class="prompt-chip"
            type="button"
            @click="applyPrompt(preset)"
          >
            {{ preset.label }}
          </button>
        </div>

        <div class="delivery-panel">
          <div class="delivery-grid">
            <div class="delivery-field">
              <span class="field-label">投放板块</span>
              <el-select v-model="candidateSection" class="w-full">
                <el-option
                  v-for="item in sectionTypeOptions.filter(option => option.value)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>

            <div class="delivery-field">
              <span class="field-label">内容类型</span>
              <el-select v-model="candidateContentType" clearable class="w-full" placeholder="自动判断">
                <el-option
                  v-for="item in contentTypeOptions.filter(option => option.value)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>

            <div class="delivery-field">
              <span class="field-label">替换排序位</span>
              <el-input-number v-model="candidateSortOrder" :min="0" class="w-full" placeholder="为空则追加" />
            </div>

            <div class="delivery-field delivery-field--switch">
              <span class="field-label">替换模式</span>
              <el-switch
                v-model="replaceExisting"
                active-text="按排序位替换"
                inactive-text="直接追加"
              />
            </div>
          </div>
        </div>

        <div v-loading="candidateLoading" class="candidate-zone">
          <div v-if="candidateResults.length" class="candidate-grid">
            <article
              v-for="candidate in candidateResults"
              :key="candidate.id"
              class="candidate-card"
            >
              <div class="candidate-cover">
                <img :src="candidate.coverUrl" :alt="candidate.title" />
                <div class="candidate-score">{{ candidate.rating || 0 }}</div>
              </div>
              <div class="candidate-body">
                <div class="candidate-meta">
                  <span class="candidate-year">{{ candidate.year || '--' }}</span>
                  <span>{{ candidate.region || '未知地区' }}</span>
                </div>
                <h3>{{ candidate.title }}</h3>
                <p class="candidate-desc">{{ candidate.description || '暂无简介' }}</p>
                <div class="candidate-footer">
                  <span class="candidate-tag">{{ candidate.categoryName || '片库内容' }}</span>
                  <el-button
                    size="small"
                    class="candidate-action"
                    :loading="Boolean(importingMap[String(candidate.id)])"
                    @click="importCandidate(candidate)"
                  >
                    {{ replaceExisting ? '替换首页位' : '加入首页' }}
                  </el-button>
                </div>
              </div>
            </article>
          </div>
          <el-empty
            v-else
            description="先输入需求词，再从候选片单里一键加入首页"
          />
        </div>
      </div>

      <div class="glass-panel library-panel">
        <div class="panel-head panel-head--compact">
          <div>
            <p class="eyebrow">Current Mix</p>
            <h2>首页资源编排</h2>
          </div>
          <div class="filter-bar">
            <el-select v-model="contentType" placeholder="全部内容" clearable class="filter-select" @change="handleFilter">
              <el-option v-for="item in contentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="sectionType" placeholder="全部板块" clearable class="filter-select" @change="handleFilter">
              <el-option v-for="item in sectionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button circle @click="loadData">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="table-shell">
          <el-table
            :data="tableData"
            v-loading="loading"
            height="100%"
            style="width: 100%"
            class="homepage-table"
          >
            <el-table-column prop="sortOrder" label="排序" width="88" align="center">
              <template #default="{ row }">
                <div class="sort-box">
                  <el-button link size="small" @click="updateSort(row, 'up')">
                    <el-icon><ArrowUp /></el-icon>
                  </el-button>
                  <strong>{{ row.sortOrder }}</strong>
                  <el-button link size="small" @click="updateSort(row, 'down')">
                    <el-icon><ArrowDown /></el-icon>
                  </el-button>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="影片信息" min-width="320">
              <template #default="{ row }">
                <div class="film-cell">
                  <div class="film-poster">
                    <el-image :src="row.coverUrl" fit="cover">
                      <template #error>
                        <div class="film-poster__fallback">
                          <el-icon><Picture /></el-icon>
                        </div>
                      </template>
                    </el-image>
                  </div>
                  <div class="film-copy">
                    <div class="film-title-row">
                      <h3>{{ row.title }}</h3>
                      <span v-if="row.aiBest === 1" class="best-badge">AI 精选</span>
                    </div>
                    <p class="film-meta">
                      <span>{{ row.year || '--' }}</span>
                      <span>{{ row.region || '未知地区' }}</span>
                      <span>{{ row.sourceName || '片库导入' }}</span>
                    </p>
                    <p class="film-desc">{{ row.description || '暂无简介' }}</p>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="分类" width="112" align="center">
              <template #default="{ row }">
                <el-tag effect="plain" round>{{ getContentTypeLabel(row.contentType) }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column label="板块" width="112" align="center">
              <template #default="{ row }">
                <el-tag type="warning" effect="plain" round>{{ getSectionTypeLabel(row.sectionType) }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column label="评分" width="96" align="center">
              <template #default="{ row }">
                <span class="rating-value">{{ row.rating || 0 }}</span>
              </template>
            </el-table-column>

            <el-table-column label="AI 推荐值" width="132" align="center">
              <template #default="{ row }">
                <div v-if="row.aiScore" class="ai-score-box">
                  <strong>{{ row.aiScore }}</strong>
                  <el-tooltip v-if="row.aiReason" :content="row.aiReason" placement="top">
                    <span class="ai-reason">查看理由</span>
                  </el-tooltip>
                </div>
                <span v-else class="muted-text">-</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" round>
                  {{ row.status === 1 ? '上架' : '下架' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-row">
                  <el-tooltip :content="row.status === 1 ? '下架' : '上架'">
                    <el-button circle size="small" plain :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
                      <el-icon><VideoPause v-if="row.status === 1" /><VideoPlay v-else /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip :content="row.aiBest === 1 ? '移出 AI 精选' : '加入 AI 精选'">
                    <el-button circle size="small" plain type="primary" @click="toggleBest(row)">
                      <el-icon><Star /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-popconfirm title="确定删除这条记录吗？" @confirm="handleDelete(row)">
                    <template #reference>
                      <el-button circle size="small" plain type="danger">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pager-shell">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @change="loadData"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.homepage-manage {
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.94), transparent 38%),
    radial-gradient(circle at top right, rgba(235, 241, 255, 0.9), transparent 34%),
    linear-gradient(180deg, #f4f5f7 0%, #eef2f6 100%);
}

.glass-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(249, 250, 252, 0.86));
  box-shadow:
    0 24px 60px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(24px);
}

.hero-panel {
  padding: 32px;
  margin-bottom: 24px;
}

.hero-panel::after {
  content: '';
  position: absolute;
  right: -90px;
  top: -80px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(85, 132, 255, 0.18), transparent 65%);
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #445066;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #458cff, #0f6fff);
}

.hero-title {
  margin: 18px 0 12px;
  font-size: clamp(2rem, 3vw, 3rem);
  line-height: 1;
  color: #111827;
  letter-spacing: -0.04em;
}

.hero-subtitle {
  max-width: 760px;
  color: #5a667c;
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 28px;
}

.hero-action {
  min-height: 44px;
  padding: 0 18px;
  border-radius: 999px;
  border: none;
}

.hero-action--primary {
  color: #fff;
  background: linear-gradient(135deg, #111827, #2563eb);
}

.hero-action--secondary {
  color: #111827;
  background: rgba(255, 255, 255, 0.74);
}

.hero-stats {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 28px;
}

.stat-card {
  padding: 18px 20px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(226, 232, 240, 0.72);
}

.stat-label {
  display: block;
  color: #6b7280;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.stat-value {
  display: block;
  margin-top: 10px;
  color: #0f172a;
  font-size: 28px;
  line-height: 1;
}

.workspace-grid {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(360px, 440px) minmax(0, 1fr);
}

.curation-panel,
.library-panel {
  padding: 24px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.panel-head h2 {
  margin-top: 8px;
  font-size: 24px;
  color: #111827;
  letter-spacing: -0.03em;
}

.eyebrow {
  color: #72809a;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.panel-tip {
  max-width: 320px;
  color: #667085;
  font-size: 13px;
  line-height: 1.7;
}

.search-shell {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.search-btn {
  min-height: 44px;
  padding: 0 18px;
  border-radius: 16px;
  border: none;
  background: linear-gradient(135deg, #111827, #334155);
  color: #fff;
}

.prompt-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.prompt-chip {
  border: 1px solid rgba(203, 213, 225, 0.86);
  background: rgba(255, 255, 255, 0.8);
  color: #334155;
  border-radius: 999px;
  padding: 10px 14px;
  font-size: 13px;
  transition: all 0.2s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(37, 99, 235, 0.35);
  color: #0f172a;
}

.delivery-panel {
  margin-top: 18px;
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(244, 247, 252, 0.88), rgba(255, 255, 255, 0.72));
  border: 1px solid rgba(226, 232, 240, 0.85);
}

.delivery-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.delivery-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.delivery-field--switch {
  justify-content: flex-end;
}

.field-label {
  color: #667085;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.candidate-zone {
  min-height: 420px;
  margin-top: 20px;
}

.candidate-grid {
  display: grid;
  gap: 14px;
}

.candidate-card {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  border-radius: 22px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  background: rgba(255, 255, 255, 0.72);
}

.candidate-cover {
  position: relative;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border-radius: 18px;
  background: #e5e7eb;
}

.candidate-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.candidate-score {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.76);
  color: #fff;
  font-size: 12px;
}

.candidate-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.candidate-meta {
  display: flex;
  gap: 8px;
  color: #72809a;
  font-size: 12px;
}

.candidate-year {
  color: #111827;
  font-weight: 600;
}

.candidate-body h3 {
  margin-top: 8px;
  color: #111827;
  font-size: 17px;
  line-height: 1.35;
}

.candidate-desc {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.candidate-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 14px;
}

.candidate-tag {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 12px;
}

.candidate-action {
  border-radius: 999px;
  border: none;
  background: #111827;
  color: #fff;
}

.panel-head--compact {
  margin-bottom: 16px;
}

.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
}

.filter-select {
  width: 140px;
}

.table-shell {
  height: calc(100vh - 410px);
  min-height: 520px;
}

.homepage-table :deep(.el-table__header th) {
  background: rgba(248, 250, 252, 0.9) !important;
  color: #64748b;
  font-weight: 600;
}

.homepage-table :deep(.el-table__row) {
  --el-table-tr-bg-color: rgba(255, 255, 255, 0.14);
}

.sort-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.sort-box strong {
  color: #111827;
  font-size: 20px;
}

.film-cell {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.film-poster {
  width: 68px;
  height: 96px;
  overflow: hidden;
  border-radius: 16px;
  background: #e5e7eb;
}

.film-poster :deep(.el-image) {
  width: 100%;
  height: 100%;
}

.film-poster__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #94a3b8;
}

.film-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.film-title-row h3 {
  color: #111827;
  font-size: 16px;
  line-height: 1.35;
}

.best-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #c2410c;
  font-size: 12px;
}

.film-meta {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  color: #72809a;
  font-size: 12px;
}

.film-desc {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rating-value {
  color: #ea580c;
  font-size: 18px;
  font-weight: 700;
}

.ai-score-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.ai-score-box strong {
  color: #4338ca;
  font-size: 18px;
}

.ai-reason,
.muted-text {
  color: #94a3b8;
  font-size: 12px;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.pager-shell {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

@media (max-width: 1280px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .table-shell {
    height: auto;
    min-height: 480px;
  }
}

@media (max-width: 768px) {
  .homepage-manage {
    padding: 16px;
  }

  .hero-panel,
  .curation-panel,
  .library-panel {
    padding: 20px;
  }

  .hero-stats,
  .delivery-grid,
  .search-shell {
    grid-template-columns: 1fr;
  }

  .panel-head,
  .panel-head--compact,
  .candidate-footer,
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .candidate-card {
    grid-template-columns: 1fr;
  }
}
</style>
