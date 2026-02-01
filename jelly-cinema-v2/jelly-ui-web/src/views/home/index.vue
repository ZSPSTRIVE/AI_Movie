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
    <!-- Hero Banner - Glass -->
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">发现精彩影视内容</h1>
        <p class="hero-subtitle">海量电影、电视剧、综艺，尽在果冻影院</p>
        <router-link to="/film" class="hero-btn">
          开始探索
        </router-link>
      </div>
      <div class="hero-shapes">
        <div class="hero-shape shape-1"></div>
        <div class="hero-shape shape-2"></div>
        <div class="hero-shape shape-3"></div>
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
        <div v-for="film in aiBestList" :key="film.id" class="ai-card" @click="goToDetail(film.id)">
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
          class="film-card"
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
/* ─── Page Container ─── */
.home-page {
  width: 100%;
  min-height: 100vh;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.page-container {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 32px 60px 32px;
}

@media (max-width: 768px) {
  .page-container {
    padding: 16px 16px 60px 16px;
  }
}

.home-page .section {
  margin-bottom: 32px;
}

/* ─── Hero Section ─── */
.hero-section {
  position: relative;
  height: 380px;
  border-radius: 28px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.15), rgba(6, 182, 212, 0.12));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 48px;
}

.hero-content {
  position: relative;
  z-index: 10;
  text-align: center;
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0 0 12px 0;
}

.hero-subtitle {
  font-size: 18px;
  color: #64748b;
  margin: 0 0 28px 0;
}

.hero-btn {
  display: inline-block;
  padding: 14px 36px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: white;
  text-decoration: none;
  border-radius: 18px;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 4px 20px rgba(14, 165, 233, 0.35);
  transition: all 0.3s;
}

.hero-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(14, 165, 233, 0.45);
}

.hero-shapes {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hero-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
}

.hero-shape.shape-1 {
  width: 300px;
  height: 300px;
  background: rgba(14, 165, 233, 0.25);
  top: -100px;
  left: -50px;
}

.hero-shape.shape-2 {
  width: 200px;
  height: 200px;
  background: rgba(6, 182, 212, 0.2);
  bottom: -50px;
  right: -30px;
}

.hero-shape.shape-3 {
  width: 150px;
  height: 150px;
  background: rgba(56, 189, 248, 0.15);
  top: 50%;
  right: 20%;
}

/* ─── Section ─── */
.section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.section-link {
  font-size: 15px;
  font-weight: 500;
  color: #0ea5e9;
  text-decoration: none;
  transition: color 0.3s;
}

.section-link:hover {
  color: #0284c7;
}

/* ─── Film Grid ─── */
.film-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

@media (max-width: 1280px) {
  .film-grid {
    grid-template-columns: repeat(5, 1fr);
  }
}

@media (max-width: 1024px) {
  .film-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}

@media (max-width: 768px) {
  .film-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 480px) {
  .film-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.film-skeleton {
  aspect-ratio: 2/3;
  border-radius: 12px;
  background: linear-gradient(90deg, rgba(255,255,255,0.1) 25%, rgba(255,255,255,0.2) 50%, rgba(255,255,255,0.1) 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s ease-in-out infinite;
}

@keyframes skeleton-loading {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.film-card {
  cursor: pointer;
}

.film-poster {
  position: relative;
  aspect-ratio: 2/3;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

.film-card:hover .film-poster {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border-color: rgba(255, 255, 255, 0.35);
}

.film-poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.film-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.3) 50%, transparent 100%);
  opacity: 0;
  transition: opacity 0.3s;
  display: flex;
  align-items: flex-end;
  padding: 12px;
}

.film-card:hover .film-overlay {
  opacity: 1;
}

.film-desc {
  font-size: 12px;
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
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
  font-size: 11px;
  font-weight: 700;
  border-radius: 6px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}

.film-rating {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #fbbf24;
}

.film-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin: 10px 0 4px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.film-meta {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
  margin: 0;
}

/* ─── Responsive ─── */
@media (max-width: 1024px) {
  .film-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ─── Rank Section ─── */
.rank-section {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.glass-rank-card {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(20px);
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  padding: 24px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06);
  transition: all 0.3s;
}

.glass-rank-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 48px rgba(0, 0, 0, 0.1);
}

.rank-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 14px;
  margin-bottom: 20px;
}

.rank-header h3 {
  font-size: 17px;
  font-weight: 600;
  margin: 0;
}

.rank-hot {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(249, 115, 22, 0.12));
  color: #ef4444;
}

.rank-new {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.15), rgba(6, 182, 212, 0.12));
  color: #0ea5e9;
}

.rank-top {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.15), rgba(251, 191, 36, 0.12));
  color: #f59e0b;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.rank-item:hover {
  background: rgba(14, 165, 233, 0.08);
}

.rank-num {
  width: 28px;
  height: 28px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  background: rgba(0, 0, 0, 0.05);
  color: #64748b;
}

.rank-num.top {
  background: linear-gradient(135deg, #ef4444, #f97316);
  color: white;
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-name {
  font-size: 15px;
  font-weight: 500;
  color: #334155;
  margin: 0 0 2px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-score {
  font-size: 13px;
  color: #94a3b8;
  margin: 0;
}

/* ─── AI Section ─── */
.ai-section {
    background: linear-gradient(145deg, #1e1b4b 0%, #312e81 100%);
    border-radius: 20px;
    padding: 24px;
    color: white;
    margin-bottom: 30px;
    box-shadow: 0 10px 30px rgba(49, 46, 129, 0.4);
    position: relative;
    overflow: hidden;
}

.ai-section::before {
    content: '';
    position: absolute;
    top: -50%;
    left: -50%;
    width: 200%;
    height: 200%;
    background: radial-gradient(circle, rgba(139, 92, 246, 0.2) 0%, transparent 60%);
    pointer-events: none;
}

.ai-title {
    color: white;
    display: flex;
    align-items: center;
    gap: 10px;
}

.ai-badge {
    background: rgba(255, 255, 255, 0.2);
    padding: 2px 10px;
    border-radius: 12px;
    font-size: 12px;
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
    background: rgba(255, 255, 255, 0.1);
    border-radius: 12px;
    padding: 12px;
    display: flex;
    gap: 16px;
    cursor: pointer;
    transition: all 0.3s;
    border: 1px solid rgba(255, 255, 255, 0.1);
}

.ai-card:hover {
    background: rgba(255, 255, 255, 0.2);
    transform: translateY(-2px);
}

.ai-poster-wrapper {
    width: 80px;
    height: 120px;
    border-radius: 8px;
    overflow: hidden;
    flex-shrink: 0;
    position: relative;
}

.ai-poster {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.ai-score-overlay {
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    background: rgba(0,0,0,0.7);
    padding: 2px 0;
    text-align: center;
    display: flex;
    flex-direction: column;
}

.score-val {
    color: #a78bfa;
    font-weight: bold;
    font-size: 14px;
    line-height: 1;
}

.score-label {
    color: #ccc;
    font-size: 8px;
    transform: scale(0.8);
}

.ai-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
}

.ai-film-title {
    font-size: 16px;
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
    color: #c7d2fe;
    line-height: 1.4;
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
    background: #fff;
    padding: 20px;
    border-radius: 16px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.03);
}

/* ─── Compact Rank List ─── */
.rank-list-compact {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.rank-item-compact {
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    padding: 6px;
    border-radius: 8px;
    transition: background 0.2s;
}

.rank-item-compact:hover {
    background: #f8fafc;
}

.rank-thumb {
    width: 48px;
    height: 64px;
    border-radius: 4px;
    object-fit: cover;
}

.rank-info .rank-title {
    font-size: 14px;
    font-weight: 500;
    color: #334155;
    margin-bottom: 4px;
}

.rank-info .rank-sub {
    font-size: 12px;
    color: #94a3b8;
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
    background: #eee;
    border-radius: 8px;
    overflow: hidden;
    position: relative;
    margin-bottom: 6px;
}

.trend-poster img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.3s;
}

.trend-card:hover img {
    transform: scale(1.05);
}

.trend-tag {
    position: absolute;
    top: 6px;
    left: 6px;
    background: #ef4444;
    color: white;
    font-size: 10px;
    padding: 2px 6px;
    border-radius: 4px;
}

.trend-title {
    font-size: 13px;
    font-weight: 500;
    color: #334155;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

/* ─── Scroll Container ─── */
.scroll-container {
    overflow-x: auto;
    padding-bottom: 12px;
    scrollbar-width: none; /* Firefox */
}
.scroll-container::-webkit-scrollbar {
    display: none; /* Chrome */
}

.scroll-wrapper {
    display: flex;
    gap: 16px;
}

.film-card-mini {
    width: 140px;
    flex-shrink: 0;
    cursor: pointer;
}

.film-poster-mini {
    aspect-ratio: 2/3;
    border-radius: 8px;
    overflow: hidden;
    position: relative;
    margin-bottom: 8px;
}

.film-poster-mini img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.film-rating-mini {
    position: absolute;
    bottom: 4px;
    right: 4px;
    background: rgba(0,0,0,0.6);
    color: #fbbf24;
    font-size: 10px;
    padding: 2px 4px;
    border-radius: 4px;
}

.film-title-mini {
    font-size: 13px;
    color: #334155;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.ai-tiny-badge {
    position: absolute;
    top: 6px;
    left: 6px;
    background: #8b5cf6;
    color: white;
    font-size: 10px;
    padding: 2px 6px;
    border-radius: 10px;
    box-shadow: 0 2px 4px rgba(0,0,0,0.2);
}

/* Responsive adjustments */
@media (max-width: 1024px) {
    .ai-grid {
        grid-template-columns: repeat(2, 1fr);
    }
    .dual-section-row {
        grid-template-columns: 1fr;
    }
}

@media (max-width: 640px) {
    .ai-grid {
        grid-template-columns: 1fr;
    }
}
</style>
