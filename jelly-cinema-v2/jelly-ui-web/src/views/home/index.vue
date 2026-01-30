<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRecommendFilm, getHotRank } from '@/api/film'
import tvboxService from '@/services/tvboxService'
import type { Film } from '@/types/film'

const router = useRouter()

const recommendList = ref<Film[]>([])
const hotRankList = ref<Film[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    // 优先使用 TVBox 数据
    const tvboxData = await tvboxService.getRecommend(18)
    if (tvboxData && tvboxData.length > 0) {
      recommendList.value = tvboxData
    } else {
      // 降级使用原有API
      const recRes = await getRecommendFilm(18)
      recommendList.value = recRes.data || []
    }
    
    // 热门榜单
    const hotRes = await getHotRank(10)
    hotRankList.value = hotRes.data || []
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
})

function goToDetail(id: number) {
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

    <!-- 推荐电影 - Glass -->
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
            <img :src="film.coverUrl" :alt="film.title" />
            <div class="film-overlay">
              <p class="film-desc">{{ film.description }}</p>
            </div>
            <div v-if="film.isVip" class="vip-badge">VIP</div>
            <div class="film-rating">{{ film.rating }}</div>
          </div>
          <h3 class="film-title">{{ film.title }}</h3>
          <p class="film-meta">
            <span>{{ formatPlayCount(film.playCount) }}次</span>
          </p>
        </div>
      </div>
    </section>

    <!-- 热门榜单 - Glass -->
    <section class="rank-section mt-12">
      <!-- 热播榜 -->
      <div class="glass-rank-card">
        <div class="rank-header rank-hot">
          <el-icon><TrendCharts /></el-icon>
          <h3>热播榜</h3>
        </div>
        <div class="rank-list">
          <div
            v-for="(film, index) in hotRankList.slice(0, 5)"
            :key="film.id"
            class="rank-item"
            @click="goToDetail(film.id)"
          >
            <span class="rank-num" :class="{ top: index < 3 }">{{ index + 1 }}</span>
            <div class="rank-info">
              <p class="rank-name">{{ film.title }}</p>
              <p class="rank-score">{{ film.rating }}分</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 新上线 -->
      <div class="glass-rank-card">
        <div class="rank-header rank-new">
          <el-icon><Calendar /></el-icon>
          <h3>新上线</h3>
        </div>
        <div class="rank-list">
          <div
            v-for="(film, index) in recommendList.slice(0, 5)"
            :key="film.id"
            class="rank-item"
            @click="goToDetail(film.id)"
          >
            <span class="rank-num">{{ index + 1 }}</span>
            <div class="rank-info">
              <p class="rank-name">{{ film.title }}</p>
              <p class="rank-score">{{ film.year }}年</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 高分推荐 -->
      <div class="glass-rank-card">
        <div class="rank-header rank-top">
          <el-icon><Star /></el-icon>
          <h3>高分推荐</h3>
        </div>
        <div class="rank-list">
          <div
            v-for="(film, index) in hotRankList.slice(5, 10)"
            :key="film.id"
            class="rank-item"
            @click="goToDetail(film.id)"
          >
            <span class="rank-num">{{ index + 1 }}</span>
            <div class="rank-info">
              <p class="rank-name">{{ film.title }}</p>
              <p class="rank-score">{{ film.rating }}分</p>
            </div>
          </div>
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
}

.page-container {
  width: 100%;
  max-width: none;
  margin: 0 auto;
  padding: 24px 20px 60px 20px;
}

@media (max-width: 768px) {
  .page-container {
    padding: 24px 12px 60px 12px;
  }
}

.home-page {
  display: flex;
  flex-direction: column;
  gap: 40px;
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

/* ─── Responsive ─── */
@media (max-width: 1024px) {
  .film-grid {
    grid-template-columns: repeat(4, 1fr);
  }
  
  .rank-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .film-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }
  
  .hero-title {
    font-size: 28px;
  }
  
  .hero-subtitle {
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  .film-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
