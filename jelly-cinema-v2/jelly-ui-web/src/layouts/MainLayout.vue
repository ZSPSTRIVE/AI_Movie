<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useMessageStore } from '@/stores/message'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const messageStore = useMessageStore()

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
  if (searchKeyword.value.trim()) {
    router.push({ name: 'Search', query: { keyword: searchKeyword.value } })
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
      <router-view />
    </main>

    <!-- Footer - Glass -->
    <footer class="glass-footer">
      <div class="footer-content">
        <p class="footer-brand">© 2024 果冻影院 Jelly Cinema</p>
        <p class="footer-slogan">影视 + 社交 + AI 一体化平台</p>
      </div>
    </footer>
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
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.05);
}

.header-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 40px;
}

.logo {
  text-decoration: none;
}

.logo-text {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-links {
  display: flex;
  gap: 8px;
}

.nav-link {
  padding: 10px 18px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.6);
  text-decoration: none;
  transition: all 0.3s;
}

.nav-link:hover {
  background: rgba(14, 165, 233, 0.08);
  color: #0ea5e9;
}

.nav-link.active {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.12), rgba(6, 182, 212, 0.1));
  color: #0ea5e9;
}

/* ─── Search ─── */
.search-wrapper {
  flex: 1;
  max-width: 400px;
  margin: 0 32px;
}

.glass-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5);
  transition: all 0.3s;
}

.glass-search:focus-within {
  background: rgba(255, 255, 255, 0.8);
  border-color: rgba(14, 165, 233, 0.3);
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.1);
}

.search-icon {
  color: rgba(0, 0, 0, 0.4);
  font-size: 18px;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 15px;
  color: rgba(0, 0, 0, 0.8);
  outline: none;
}

.search-input::placeholder {
  color: rgba(0, 0, 0, 0.35);
}

/* ─── Header Right ─── */
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.glass-icon-btn {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 12px;
  color: rgba(0, 0, 0, 0.6);
  cursor: pointer;
  transition: all 0.3s;
}

.glass-icon-btn:hover {
  background: rgba(255, 255, 255, 0.8);
  color: #0ea5e9;
}

.user-avatar-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 16px 6px 6px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.3s;
}

.user-avatar-btn:hover {
  background: rgba(255, 255, 255, 0.8);
}

.avatar-img {
  border: 2px solid rgba(255, 255, 255, 0.8);
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.7);
}

.glass-btn-primary {
  padding: 12px 24px;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 4px 16px rgba(14, 165, 233, 0.3);
}

.glass-btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(14, 165, 233, 0.4);
}

/* ─── Main Content ─── */
.main-content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px;
  min-height: calc(100vh - 200px);
}

/* ─── Footer ─── */
.glass-footer {
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  border-top: 1px solid rgba(255, 255, 255, 0.4);
  padding: 40px 24px;
  margin-top: 48px;
}

.footer-content {
  max-width: 1280px;
  margin: 0 auto;
  text-align: center;
}

.footer-brand {
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.footer-slogan {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
}

/* ─── Dropdown Override ─── */
:deep(.glass-dropdown) {
  background: rgba(255, 255, 255, 0.9) !important;
  backdrop-filter: blur(20px) !important;
  border: 1px solid rgba(255, 255, 255, 0.5) !important;
  border-radius: 16px !important;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12) !important;
  padding: 8px !important;
}

:deep(.glass-dropdown .el-dropdown-menu__item) {
  border-radius: 10px !important;
  padding: 10px 16px !important;
  margin: 2px 0 !important;
}

:deep(.glass-dropdown .el-dropdown-menu__item:hover) {
  background: rgba(14, 165, 233, 0.08) !important;
  color: #0ea5e9 !important;
}

:deep(.logout-item) {
  color: #ef4444 !important;
}

:deep(.logout-item:hover) {
  background: rgba(239, 68, 68, 0.08) !important;
  color: #ef4444 !important;
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
}
</style>
