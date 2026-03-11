<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import { ElMessage } from 'element-plus'
import AIChatWidget from '@/components/ai/AIChatWidget.vue'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const userStore = useUserStore()
const messageStore = useMessageStore()
const { isDark, toggleTheme } = useTheme()

const searchKeyword = ref('')

const isLogin = computed(() => userStore.isLogin)
const userAvatar = computed(() => userStore.avatar)
const userNickname = computed(() => userStore.nickname)
const unreadCount = computed(() => messageStore.totalUnreadCount)

// 登录后加载未读消息数
onMounted(() => {
  if (userStore.isLogin) {
    messageStore.loadAllUnreadCounts()
  }
})

// 监听登录状态变化
watch(() => userStore.isLogin, (isLogin) => {
  if (isLogin) {
    messageStore.loadAllUnreadCounts()
  } else {
    messageStore.clearAllUnread()
  }
})

const navItems = [
  { path: '/', label: '首页', icon: 'HomeFilled' },
  { path: '/film', label: '电影', icon: 'Film' },
  { path: '/community', label: '社区', icon: 'ChatDotRound' },
  { path: '/ai-lab', label: 'AI 实验室', icon: 'MagicStick' }
]

function handleSearch() {
  const kw = searchKeyword.value.trim()
  if (kw) {
    router.push({ name: 'Search', query: { keyword: kw } })
  }
}

function handleLogout() {
  userStore.doLogout()
  ElMessage.success('已退出登录')
  router.push('/')
}

function goToChat() {
  router.push('/chat')
}
</script>

<template>
  <div class="layout-wrapper">
    <!-- 动态背景 -->
    <div class="glass-bg"></div>

    <!-- 顶部导航栏 - Glassmorphism -->
    <header class="glass-header">
      <div class="header-content">
        <!-- Logo & Navigation -->
        <div class="header-left">
          <router-link to="/" class="logo">
            <span class="logo-text">果冻影院</span>
          </router-link>
          
          <nav class="nav-links">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="nav-link"
              active-class="active"
            >
              {{ item.label }}
            </router-link>
          </nav>
        </div>

        <!-- Search Bar - Glass -->
        <div class="search-wrapper">
          <div class="glass-search">
            <el-icon class="search-icon"><Search /></el-icon>
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索电影、帖子、用户..."
              class="search-input"
              @keyup.enter="handleSearch"
            />
          </div>
        </div>

        <!-- User Area -->
        <div class="header-right">
          <!-- 主题切换 -->
          <button class="glass-icon-btn" @click="toggleTheme" :title="isDark ? '切换亮色模式' : '切换暗色模式'">
            <el-icon><Sunny v-if="isDark" /><Moon v-else /></el-icon>
          </button>

          <!-- 消息通知 -->
          <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0" v-if="isLogin">
            <button class="glass-icon-btn" @click="goToChat">
              <el-icon><Bell /></el-icon>
            </button>
          </el-badge>

          <!-- 用户菜单 -->
          <template v-if="isLogin">
            <el-dropdown trigger="click">
              <div class="user-avatar-btn">
                <el-avatar :size="36" :src="userAvatar" class="avatar-img" />
                <span class="user-name">{{ userNickname }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu class="glass-dropdown">
                  <el-dropdown-item @click="router.push('/user')">
                    <el-icon><User /></el-icon>
                    个人中心
                  </el-dropdown-item>
                  <el-dropdown-item @click="router.push('/growth')">
                    <el-icon><Present /></el-icon>
                    积分商城
                  </el-dropdown-item>
                  <el-dropdown-item @click="router.push('/chat')">
                    <el-icon><ChatDotRound /></el-icon>
                    消息中心
                  </el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" @click="router.push('/admin')">
                    <el-icon><Setting /></el-icon>
                    管理后台
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout" class="logout-item">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <router-link to="/login">
              <button class="glass-btn-primary">登录</button>
            </router-link>
          </template>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" :key="$route.path" />
        </transition>
      </router-view>
    </main>

    <!-- Footer - Glass -->
    <footer class="glass-footer">
      <div class="footer-content">
        <p class="footer-brand">© 2024 果冻影院 Jelly Cinema</p>
        <p class="footer-slogan">影视 + 社交 + AI 一体化平台</p>
      </div>
    </footer>
    
    <!-- AI Chat Widget -->
    <AIChatWidget />
  </div>
</template>

<style scoped>
.layout-wrapper {
  min-height: 100vh;
  position: relative;
}

/* ─── Glass Header ─── */
.glass-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--glass-bg);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid var(--border-light);
}

.header-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 22px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.logo {
  text-decoration: none;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.nav-links {
  display: flex;
  gap: 0;
}

.nav-link {
  padding: 6px 12px;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
  text-decoration: none;
  transition: all var(--duration-base) var(--ease-apple);
  letter-spacing: 0.01em;
}

.nav-link:hover {
  color: var(--text-primary);
}

.nav-link.active {
  color: var(--text-primary);
  font-weight: 500;
}

/* ─── Search ─── */
.search-wrapper {
  flex: 1;
  max-width: 320px;
  margin: 0 24px;
}

.glass-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  background: var(--glass-bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-full);
  transition: all var(--duration-base) var(--ease-apple);
}

.glass-search:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.12);
}

.search-icon {
  color: var(--text-tertiary);
  font-size: 16px;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-primary);
  outline: none;
  font-family: inherit;
}

.search-input::placeholder {
  color: var(--text-tertiary);
}

/* ─── Header Right ─── */
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.glass-icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-apple);
  font-size: 16px;
}

.glass-icon-btn:hover {
  color: var(--text-primary);
}

.user-avatar-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 12px 2px 2px;
  background: transparent;
  border: none;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-apple);
}

.user-avatar-btn:hover {
  opacity: 0.7;
}

.avatar-img {
  border: none;
}

.user-name {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-primary);
}

.glass-btn-primary {
  padding: 6px 16px;
  background: var(--color-primary);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-apple);
}

.glass-btn-primary:hover {
  opacity: 0.85;
}

/* ─── Main Content ─── */
.main-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 40px 22px;
  min-height: calc(100vh - 200px);
}

/* ─── Footer ─── */
.glass-footer {
  background: transparent;
  border-top: 1px solid var(--border-light);
  padding: 20px 22px;
  margin-top: 0;
}

.footer-content {
  max-width: 1280px;
  margin: 0 auto;
  text-align: center;
}

.footer-brand {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}

.footer-slogan {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
}

/* ─── Dropdown Override ─── */
:deep(.glass-dropdown) {
  background: var(--glass-bg-popover) !important;
  backdrop-filter: blur(var(--glass-blur-heavy)) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-xl) !important;
  padding: 6px !important;
}

:deep(.glass-dropdown .el-dropdown-menu__item) {
  border-radius: var(--radius-sm) !important;
  padding: 10px 16px !important;
  margin: 2px 0 !important;
  font-weight: 400 !important;
  font-size: 14px !important;
}

:deep(.glass-dropdown .el-dropdown-menu__item:hover) {
  background: var(--color-primary-bg) !important;
  color: var(--color-primary) !important;
}

:deep(.logout-item) {
  color: var(--color-danger) !important;
}

:deep(.logout-item:hover) {
  background: rgba(255, 59, 48, 0.08) !important;
  color: var(--color-danger) !important;
}

/* ─── Responsive ─── */
@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
  
  .search-wrapper {
    display: none;
  }
  
  .user-name {
    display: none;
  }

  .header-content {
    padding: 0 12px;
  }

  .main-content {
    padding: 0 12px;
  }

  .footer-content {
    padding: 16px 12px;
  }
}
</style>
