<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRecommendFilm, getHotRank } from '@/api/film'
import { 
  getHomepageRecommend, 
  getHomepageHot,
  getHomepageAiBest,
  getHomepageNew,
  getHomepageTrending,
  getHomepageSections
} from '@/api/homepage'
import tvboxService from '@/services/tvboxService'
import { normalizeImageUrl } from '@/utils/image'
import type { Film } from '@/types/film' // Assuming type Film is compatible or needs update. using any for now to be safe with new fields
import { ElMessage } from 'element-plus'

const router = useRouter()

// 定义扩展的Film类型以支持新字段
interface HomepageFilm extends Film {
  aiScore?: number
  aiReason?: string
  aiBest?: number
  trendingScore?: number
}

const recommendList = ref<HomepageFilm[]>([])
const hotRankList = ref<HomepageFilm[]>([])
const aiBestList = ref<HomepageFilm[]>([])
const newReleaseList = ref<HomepageFilm[]>([])
const trendingList = ref<HomepageFilm[]>([])

const loading = ref(true)

onMounted(async () => {
  try {
    loading.value = true
    
    // 并行获取所有板块数据
    try {
      // 1. 获取分板块数据(如果后端实现了聚合接口)
      const sectionRes = await getHomepageSections()
      if (sectionRes.data) {
        aiBestList.value = transfromData(sectionRes.data.ai_best)
        hotRankList.value = transfromData(sectionRes.data.hot)
        newReleaseList.value = transfromData(sectionRes.data.new)
        trendingList.value = transfromData(sectionRes.data.trending)
        recommendList.value = transfromData(sectionRes.data.recommend)
      } else {
        throw new Error('聚合接口返回空')
      }
    } catch (sectionError) {
      console.warn('聚合接口获取失败，尝试单独获取:', sectionError)
      
      // 降级：单独获取各个板块
      const [aiRes, hotRes, newRes, trendRes, recRes] = await Promise.allSettled([
        getHomepageAiBest(6),
        getHomepageHot(10),
        getHomepageNew(12),
        getHomepageTrending(8),
        getHomepageRecommend(12)
      ])
      
      if (aiRes.status === 'fulfilled' && aiRes.value.data) aiBestList.value = transfromData(aiRes.value.data)
      if (hotRes.status === 'fulfilled' && hotRes.value.data) hotRankList.value = transfromData(hotRes.value.data)
      if (newRes.status === 'fulfilled' && newRes.value.data) newReleaseList.value = transfromData(newRes.value.data)
      if (trendRes.status === 'fulfilled' && trendRes.value.data) trendingList.value = transfromData(trendRes.value.data)
      if (recRes.status === 'fulfilled' && recRes.value.data) recommendList.value = transfromData(recRes.value.data)
    }

    // 如果推荐数据仍为空（可能是初始化系统），尝试使用TVBox兜底
    if (recommendList.value.length === 0) {
       const tvboxData = await tvboxService.getRecommend(12)
       recommendList.value = tvboxData
    }

    // 同样为热门、新片、趋势添加兜底
    if (hotRankList.value.length === 0) {
       const hotData = await tvboxService.getRecommend(10)
       hotRankList.value = hotData
    }
    
    if (newReleaseList.value.length === 0) {
       const newData = await tvboxService.getList(1, 12)
       newReleaseList.value = newData.list
    }

    if (trendingList.value.length === 0) {
       const trendData = await tvboxService.getRecommend(8)
       trendingList.value = trendData
    }

  } catch (error) {
    console.error('Failed to load homepage data:', error)
    ElMessage.error('加载部分首页数据失败')
  } finally {
    loading.value = false
  }
})

// 数据转换工具
function transfromData(data: any[]): HomepageFilm[] {
  if (!data) return []
  return data.map((item: any) => ({
    id: item.tvboxId || item.id,
    title: item.title,
    coverUrl: normalizeImageUrl(item.coverUrl, item.title),
    rating: item.rating,
    year: item.year,
    region: item.region,
    description: item.description,
    playCount: 0,
    aiScore: item.aiScore,
    aiReason: item.aiReason,
    aiBest: item.aiBest,
    trendingScore: item.trendingScore
    // ... maps other fields
  }))
}

function goToDetail(id: string | number) {
  router.push(`/film/${id}`)
}

function formatPlayCount(count: number): string {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万'
  }
  return count.toString()
}
</script>

<template>
  <div class="home-page">
    <div class="page-container">
    <!-- Hero Banner - Apple Clean -->
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">发现精彩</h1>
        <h2 class="hero-subtitle-lg">果冻影院，你的专属影视平台</h2>
        <p class="hero-subtitle">海量电影、电视剧、综艺，总有一部适合你。</p>
        <router-link to="/film" class="hero-btn">
          开始探索
        </router-link>
      </div>
    </section>

    <!-- AI 精选推荐 - Dark Premium Style -->
    <section class="section ai-section" v-if="aiBestList.length > 0">
      <div class="section-header">
        <h2 class="section-title ai-title">
            <el-icon><MagicStick /></el-icon> AI 精选推荐
        </h2>
        <span class="ai-badge">基于深度学习分析</span>
      </div>
      <div class="ai-grid">
        <div v-for="film in aiBestList" :key="film.id" class="ai-card stagger-item hover-lift" @click="goToDetail(film.id)">
            <div class="ai-poster-wrapper">
                <img :src="film.coverUrl" :alt="film.title" v-img-fallback="film.title" class="ai-poster" />
                <div class="ai-score-overlay">
                    <span class="score-val">{{ film.aiScore }}</span>
                    <span class="score-label">AI评分</span>
                </div>
            </div>
            <div class="ai-info">
                <h3 class="ai-film-title">{{ film.title }}</h3>
                <p class="ai-reason">
                    <el-icon><ChatLineRound /></el-icon> {{ film.aiReason || '暂无推荐理由' }}
                </p>
            </div>
        </div>
      </div>
    </section>

    <!-- 热门 & 趋势 (双栏布局) -->
    <div class="dual-section-row">
        <!-- 热门榜单 -->
        <section class="section half-section">
            <div class="section-header">
                <h2 class="section-title">热门榜单</h2>
                <router-link to="/film?sort=hot" class="section-link">更多</router-link>
            </div>
            <div class="rank-list-compact">
                 <div v-for="(film, index) in hotRankList.slice(0, 5)" :key="film.id" class="rank-item-compact" @click="goToDetail(film.id)">
                    <span class="rank-num" :class="{ top: index < 3 }">{{ index + 1 }}</span>
                    <img :src="film.coverUrl" v-img-fallback="film.title" class="rank-thumb" />
                    <div class="rank-info">
                         <div class="rank-title">{{ film.title }}</div>
                         <div class="rank-sub">{{ film.year }} · {{ film.rating }}分</div>
                    </div>
                 </div>
            </div>
        </section>

        <!-- 趋势话题 -->
        <section class="section half-section">
            <div class="section-header">
                <h2 class="section-title">趋势话题</h2>
            </div>
            <div class="trending-grid">
                <div v-for="film in trendingList.slice(0, 4)" :key="film.id" class="trend-card" @click="goToDetail(film.id)">
                    <div class="trend-poster">
                        <img :src="film.coverUrl" v-img-fallback="film.title" />
                        <div class="trend-tag">热议</div>
                    </div>
                    <div class="trend-title">{{ film.title }}</div>
                </div>
            </div>
        </section>
    </div>

    <!-- 新片上架 -->
    <section class="section">
      <div class="section-header">
        <h2 class="section-title">新片上架</h2>
        <router-link to="/film?sort=new" class="section-link">查看全部 →</router-link>
      </div>

      <!-- 横向滚动容器 -->
      <div class="scroll-container">
        <div class="scroll-wrapper">
             <div v-for="film in newReleaseList" :key="film.id" class="film-card-mini" @click="goToDetail(film.id)">
                <div class="film-poster-mini">
                    <img :src="film.coverUrl" v-img-fallback="film.title" loading="lazy" />
                    <div class="film-rating-mini">{{ film.rating }}</div>
                </div>
                <div class="film-title-mini">{{ film.title }}</div>
             </div>
        </div>
      </div>
    </section>

    <!-- 为你推荐 -->
    <section class="section">
      <div class="section-header">
        <h2 class="section-title">为你推荐</h2>
        <router-link to="/film" class="section-link">
          查看全部 →
        </router-link>
      </div>

      <div v-if="loading" class="film-grid">
        <div v-for="i in 6" :key="i" class="film-skeleton"></div>
      </div>

      <div v-else class="film-grid">
        <div
          v-for="film in recommendList"
          :key="film.id"
          class="film-card stagger-item hover-lift"
          @click="goToDetail(film.id)"
        >
          <div class="film-poster">
            <img :src="film.coverUrl" :alt="film.title" v-img-fallback="film.title" loading="lazy" />
            <div class="film-overlay">
              <p class="film-desc">{{ film.description }}</p>
            </div>
            <div v-if="film.isVip" class="vip-badge">VIP</div>
            <div class="film-rating">{{ film.rating }}</div>
            <div v-if="film.aiScore" class="ai-tiny-badge">AI {{ film.aiScore }}</div>
          </div>
          <h3 class="film-title">{{ film.title }}</h3>
          <p class="film-meta">
            <span>{{ formatPlayCount(film.playCount) }}次播放</span>
          </p>
        </div>
      </div>
    </section>

    </div><!-- .page-container -->
  </div>
</template>

<style scoped>
/* ─── Page ─── */
.home-page {
  width: 100%;
  min-height: 100vh;
}

.page-container {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 22px 60px 22px;
}

@media (max-width: 768px) {
  .page-container { padding: 0 16px 40px 16px; }
}

.home-page .section {
  margin-bottom: 48px;
}

/* ─── Hero ─── */
.hero-section {
  text-align: center;
  padding: 80px 20px 64px 20px;
}

.hero-content {
  max-width: 680px;
  margin: 0 auto;
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.03em;
  line-height: 1.08;
}

.hero-subtitle-lg {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px 0;
  letter-spacing: -0.02em;
}

.hero-subtitle {
  font-size: 17px;
  font-weight: 400;
  color: var(--text-tertiary);
  margin: 0 0 32px 0;
  line-height: 1.5;
}

.hero-btn {
  display: inline-block;
  padding: 12px 28px;
  background: var(--color-primary);
  color: var(--text-inverse);
  text-decoration: none;
  border-radius: var(--radius-full);
  font-size: 17px;
  font-weight: 400;
  transition: all var(--duration-base) var(--ease-apple);
}

.hero-btn:hover {
  opacity: 0.85;
  color: var(--text-inverse);
}

/* ─── Section ─── */
.section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.02em;
}

.section-link {
  font-size: 17px;
  font-weight: 400;
  color: var(--color-primary);
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-apple);
}

.section-link:hover {
  text-decoration: underline;
}

/* ─── Film Grid ─── */
.film-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

@media (max-width: 1280px) { .film-grid { grid-template-columns: repeat(5, 1fr); } }
@media (max-width: 1024px) { .film-grid { grid-template-columns: repeat(4, 1fr); } }
@media (max-width: 768px)  { .film-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 480px)  { .film-grid { grid-template-columns: repeat(2, 1fr); } }

.film-skeleton {
  aspect-ratio: 2/3;
  border-radius: var(--radius-lg);
  background: linear-gradient(90deg, var(--bg-base) 25%, var(--bg-card) 50%, var(--bg-base) 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s ease-in-out infinite;
}

@keyframes skeleton-loading {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.film-card {
  cursor: pointer;
}

.film-poster {
  position: relative;
  aspect-ratio: 2/3;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--bg-card);
  transition: all var(--duration-slow) var(--ease-apple);
}

.film-card:hover .film-poster {
  transform: scale(1.02);
  box-shadow: var(--shadow-lg);
}

.film-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.film-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.80) 0%, rgba(0,0,0,0.2) 50%, transparent 100%);
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-apple);
  display: flex;
  align-items: flex-end;
  padding: 12px;
}

.film-card:hover .film-overlay {
  opacity: 1;
}

.film-desc {
  font-size: 12px;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.95);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
}

.vip-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 3px 8px;
  background: var(--color-warning);
  color: var(--text-inverse);
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--radius-sm);
}

.film-rating {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(8px);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-warning);
}

.ai-tiny-badge {
  position: absolute;
  bottom: 8px;
  right: 8px;
  padding: 2px 6px;
  background: var(--color-info);
  color: var(--text-inverse);
  font-size: 10px;
  font-weight: 500;
  border-radius: var(--radius-sm);
}

.film-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 10px 0 4px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.film-meta {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
  margin: 0;
}

/* ─── AI Section ─── */
.ai-section {
  background: linear-gradient(135deg, #1d1d1f 0%, #2d2d30 100%);
  border-radius: var(--radius-xl);
  padding: 32px;
  color: white;
  margin-bottom: 48px;
  position: relative;
  overflow: hidden;
}

.ai-section::before {
  content: '';
  position: absolute;
  top: -50%; right: -30%;
  width: 500px; height: 500px;
  background: radial-gradient(circle, rgba(88, 86, 214, 0.15) 0%, transparent 70%);
  pointer-events: none;
}

.ai-title {
  color: white;
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-badge {
  background: rgba(255, 255, 255, 0.15);
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 400;
  margin-left: 12px;
  backdrop-filter: blur(4px);
}

.ai-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  position: relative;
  z-index: 2;
}

.ai-card {
  background: rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-lg);
  padding: 14px;
  display: flex;
  gap: 16px;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-apple);
}

.ai-card:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateY(-2px);
}

.ai-poster-wrapper {
  width: 80px; height: 120px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  position: relative;
}

.ai-poster {
  width: 100%; height: 100%;
  object-fit: cover;
}

.ai-score-overlay {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  background: rgba(0,0,0,0.65);
  backdrop-filter: blur(4px);
  padding: 4px 0;
  text-align: center;
  display: flex;
  flex-direction: column;
}

.score-val {
  color: #A78BFA;
  font-weight: 700;
  font-size: 14px;
  line-height: 1;
}

.score-label {
  color: #ccc;
  font-size: 9px;
  font-weight: 400;
}

.ai-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.ai-film-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: white;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ai-reason {
  font-size: 13px;
  font-weight: 400;
  color: #C7D2FE;
  line-height: 1.5;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ─── Dual Section ─── */
.dual-section-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 30px;
}

.half-section {
  background: var(--bg-card);
  padding: 24px;
  border-radius: var(--radius-xl);
}

/* ─── Compact Rank List ─── */
.rank-list-compact {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rank-item-compact {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 8px;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-apple);
}

.rank-item-compact:hover {
  background: var(--color-primary-bg);
}

.rank-num {
  width: 28px; height: 28px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: var(--bg-base);
  color: var(--text-tertiary);
}

.rank-num.top {
  background: var(--color-primary);
  color: var(--text-inverse);
}

.rank-thumb {
  width: 48px; height: 64px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}

.rank-info .rank-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.rank-info .rank-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
}

/* ─── Trending Grid ─── */
.trending-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.trend-card {
  cursor: pointer;
}

.trend-poster {
  aspect-ratio: 16/9;
  background: var(--bg-base);
  border-radius: var(--radius-sm);
  overflow: hidden;
  position: relative;
}

.trend-poster img {
  width: 100%; height: 100%;
  object-fit: cover;
  transition: transform var(--duration-slow) var(--ease-apple);
}

.trend-card:hover .trend-poster img {
  transform: scale(1.05);
}

.trend-tag {
  position: absolute;
  top: 6px; right: 6px;
  padding: 2px 8px;
  background: var(--color-danger);
  color: var(--text-inverse);
  font-size: 11px;
  font-weight: 500;
  border-radius: var(--radius-sm);
}

.trend-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-top: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── Scroll Container (新片上架) ─── */
.scroll-container {
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.scroll-container::-webkit-scrollbar {
  display: none;
}

.scroll-wrapper {
  display: flex;
  gap: 16px;
  padding-bottom: 8px;
}

.film-card-mini {
  flex-shrink: 0;
  width: 140px;
  cursor: pointer;
}

.film-poster-mini {
  position: relative;
  aspect-ratio: 2/3;
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: all var(--duration-base) var(--ease-apple);
}

.film-card-mini:hover .film-poster-mini {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.film-poster-mini img {
  width: 100%; height: 100%;
  object-fit: cover;
}

.film-rating-mini {
  position: absolute;
  top: 6px; left: 6px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-warning);
}

.film-title-mini {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-top: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── Responsive ─── */
@media (max-width: 1024px) {
  .ai-grid { grid-template-columns: repeat(2, 1fr); }
  .dual-section-row { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .hero-section { padding: 48px 16px 40px 16px; }
  .hero-title { font-size: 36px; }
  .hero-subtitle-lg { font-size: 20px; }
  .ai-grid { grid-template-columns: 1fr; }
  .section-title { font-size: 22px; }
  .rank-list { gap: 8px; }
}
</style>
