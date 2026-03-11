<script setup lang="ts">
import { ref } from 'vue'
import { VideoPlay, ChatDotRound, MagicStick, ChatLineSquare, Reading, Lock } from '@element-plus/icons-vue'

const version = ref('2.0.0')
const buildDate = ref('2025-12-03')

const features = [
  { icon: VideoPlay, title: '高清影视', desc: '海量正版高清影视资源，流畅播放体验' },
  { icon: ChatDotRound, title: '即时聊天', desc: '与好友实时聊天，分享观影心得' },
  { icon: MagicStick, title: 'AI 助手', desc: '智能 AI 助手，为您推荐个性化内容' },
  { icon: ChatLineSquare, title: '社区互动', desc: '发帖评论，与影迷交流讨论' },
  { icon: Reading, title: 'AI 小说', desc: 'AI 驱动的小说创作与阅读体验' },
  { icon: Lock, title: '安全可靠', desc: '多重安全防护，保护您的隐私' }
]

const techStack = [
  'Vue 3', 'TypeScript', 'Tailwind CSS', 'Element Plus',
  'Spring Boot', 'Spring Cloud', 'MySQL', 'Redis', 'RabbitMQ', 'WebSocket'
]
</script>

<template>
  <div class="about-page">
    <!-- 头部 -->
    <div class="about-hero">
      <h1 class="about-hero-title">Jelly Cinema</h1>
      <p class="about-hero-subtitle">您的专属影视娱乐平台</p>
      <div class="about-hero-meta">
        <span class="meta-tag">v{{ version }}</span>
        <span class="meta-tag">{{ buildDate }}</span>
      </div>
    </div>

    <!-- 功能特点 -->
    <section class="about-section">
      <h2 class="about-section-title">功能特点</h2>
      <div class="features-grid">
        <div
          v-for="feature in features"
          :key="feature.title"
          class="feature-card stagger-item"
        >
          <div class="feature-icon">
            <el-icon :size="24"><component :is="feature.icon" /></el-icon>
          </div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="about-section">
      <h2 class="about-section-title">技术栈</h2>
      <div class="tech-grid">
        <span v-for="tech in techStack" :key="tech" class="tech-tag">{{ tech }}</span>
      </div>
    </section>

    <!-- 版权信息 -->
    <footer class="about-footer">
      <p>© 2025 Jelly Cinema. All rights reserved.</p>
    </footer>
  </div>
</template>

<style scoped>
.about-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 22px 60px;
}

.about-hero {
  text-align: center;
  padding: 80px 0 48px;
}

.about-hero-title {
  font-size: 48px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.03em;
  margin: 0 0 8px;
}

.about-hero-subtitle {
  font-size: 21px;
  font-weight: 400;
  color: var(--text-secondary);
  margin: 0 0 20px;
}

.about-hero-meta {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.meta-tag {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-tertiary);
  background: var(--bg-base);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  padding: 4px 14px;
}

.about-section {
  margin-bottom: 48px;
}

.about-section-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin: 0 0 24px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

@media (max-width: 768px) {
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .about-hero-title { font-size: 36px; }
  .about-hero-subtitle { font-size: 17px; }
}

@media (max-width: 480px) {
  .features-grid { grid-template-columns: 1fr; }
}

.feature-card {
  padding: 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  transition: transform var(--duration-base) var(--ease-apple);
}

.feature-card:hover {
  transform: scale(1.02);
}

.feature-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-bg);
  color: var(--color-primary);
  border-radius: var(--radius-lg);
  margin-bottom: 14px;
}

.feature-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px;
}

.feature-desc {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
}

.tech-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tech-tag {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--bg-base);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  padding: 6px 16px;
  transition: all var(--duration-fast) var(--ease-apple);
}

.tech-tag:hover {
  color: var(--color-primary);
  border-color: var(--color-primary-bg-hover);
  background: var(--color-primary-bg);
}

.about-footer {
  text-align: center;
  padding: 24px 0;
  font-size: 13px;
  color: var(--text-tertiary);
}

.about-footer p {
  margin: 0;
}
</style>
