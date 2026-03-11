<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import AIChatWidget from '@/components/ai/AIChatWidget.vue'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

const menuItems = [
  { path: '/admin', icon: 'icon-shujukanban', title: '仪表盘' },
  { path: '/admin/users', icon: 'icon-gerenzhongxin-zhihui', title: '用户管理' },
  { path: '/admin/homepage', icon: 'icon-shouye-zhihui', title: '首页管理' },
  { path: '/admin/tvbox-sources', icon: 'icon-api', title: '采集源配置' },
  { path: '/admin/sensitive', icon: 'icon-anquanbaozhang', title: '敏感词库' },
  { path: '/admin/reports', icon: 'icon-jubao', title: '举报处理' },
  { path: '/admin/groups', icon: 'icon-xiaoxi-zhihui', title: '群组审计' },
]

const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta?.title)
  return matched.map(item => ({
    title: item.meta?.title as string,
    path: item.path
  }))
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    
    // 调用 store 的 logout action (确保它是 Promise 或者同步执行无误)
    // 如果 logout 是异步的，加 await；如果是同步的，也不影响。
    // 通常 Pinia action 处理异步请求需要 await
    await userStore.doLogout()
    
    router.push('/login')
  } catch (error) {
    // 用户取消或 logout 失败（如果是 API 调用错误，通常也在 store 内部处理了，这里 catch 住避免崩溃）
  }
}
</script>

<template>
  <div class="admin-layout-root flex h-screen">
    <!-- 侧边栏 -->
    <aside :class="['admin-sidebar', { 'admin-sidebar--collapsed': isCollapsed }]">
      <!-- Logo -->
      <div class="sidebar-logo">
        <router-link to="/admin" class="flex items-center gap-3 overflow-hidden px-4">
          <div class="logo-icon">
            <svg-icon name="icon-a-24Hanbao" size="20" color="#FFF" />
          </div>
          <span v-if="!isCollapsed" class="logo-label">JELLY ADMIN</span>
        </router-link>
      </div>

      <!-- 菜单 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="['sidebar-menu-item', { 'sidebar-menu-item--active': route.path === item.path }]"
        >
          <el-icon size="18"><svg-icon :name="item.icon" /></el-icon>
          <span v-if="!isCollapsed" class="menu-label">{{ item.title }}</span>
        </router-link>
      </nav>

      <!-- 底部 -->
      <div class="sidebar-bottom">
        <router-link to="/" class="sidebar-menu-item" title="返回前台">
          <el-icon size="18"><svg-icon name="icon-qiantai" /></el-icon>
          <span v-if="!isCollapsed" class="menu-label">返回前台</span>
        </router-link>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 顶部栏 -->
      <header class="admin-header">
        <div class="flex items-center gap-4">
          <button class="header-toggle-btn" @click="isCollapsed = !isCollapsed">
            <el-icon size="18"><svg-icon :name="!isCollapsed ? 'icon-a-xiala2' : 'icon-a-24Hanbao'" :style="{ transform: !isCollapsed ? 'rotate(90deg)' : '' }" /></el-icon>
          </button>
          
          <!-- 面包屑 -->
          <el-breadcrumb separator="/" class="hidden md:block">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              <span class="breadcrumb-text">{{ item.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
             <button class="header-action-btn">
                <el-icon size="18"><svg-icon name="icon-tishi" /></el-icon>
             </button>
             <button class="header-action-btn">
                <el-icon size="18"><svg-icon name="icon-guanliyuanzhongxin" /></el-icon>
             </button>
          </div>
          
          <div class="header-divider"></div>

          <el-dropdown trigger="click">
            <div class="header-user-btn">
              <el-avatar :size="32" :src="userStore.avatar" class="user-avatar-fallback">{{ userStore.nickname?.[0]?.toUpperCase() }}</el-avatar>
              <div class="hidden md:flex flex-col text-right">
                 <span class="user-name-text">{{ userStore.nickname }}</span>
                 <span class="user-role-text">Administrator</span>
              </div>
              <el-icon class="text-gray-400"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="min-w-[140px]">
                <el-dropdown-item @click="router.push('/user')">
                  <el-icon><svg-icon name="icon-gerenzhongxin-zhihui" /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" class="logout-item">
                  <el-icon><svg-icon name="icon-guanbi" /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="admin-main">
        <div class="absolute inset-0 overflow-auto scroll-smooth">
          <router-view v-slot="{ Component }">
             <transition name="page" mode="out-in">
               <component :is="Component" :key="$route.path" />
             </transition>
          </router-view>
        </div>
      </main>
    </div>
    
    <!-- AI Chat Widget -->
    <AIChatWidget />
  </div>
</template>

<style scoped>
/* ─── Sidebar ─── */
.admin-sidebar {
  width: 256px;
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width var(--duration-base) var(--ease-apple);
  z-index: 20;
}

.admin-sidebar--collapsed {
  width: 80px;
}

.sidebar-logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--border-color);
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-label {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
}

.sidebar-menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all var(--duration-base) var(--ease-apple);
  margin-bottom: 4px;
  position: relative;
}

.sidebar-menu-item:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.sidebar-menu-item--active {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-weight: 600;
}

.sidebar-menu-item--active::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--color-primary);
  border-radius: 0 2px 2px 0;
}

.menu-label {
  white-space: nowrap;
}

.sidebar-bottom {
  padding: 12px;
  border-top: 1px solid var(--border-color);
}

/* ─── Header ─── */
.admin-header {
  height: 64px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: var(--shadow-sm);
  z-index: 10;
}

.header-toggle-btn {
  padding: 8px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-apple);
}

.header-toggle-btn:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
}

.breadcrumb-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
}

.header-action-btn {
  padding: 8px;
  border-radius: var(--radius-full);
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-apple);
}

.header-action-btn:hover {
  color: var(--color-primary);
  background: var(--color-primary-bg);
}

.header-divider {
  height: 24px;
  width: 1px;
  background: var(--border-color);
  margin: 0 8px;
}

.header-user-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-apple);
}

.header-user-btn:hover {
  background: var(--color-primary-bg);
}

.user-avatar-fallback {
  background: var(--color-primary-bg) !important;
  color: var(--color-primary) !important;
  font-weight: 600 !important;
}

.user-name-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
}

.user-role-text {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-tertiary);
}

/* ─── Main ─── */
.admin-main {
  flex: 1;
  overflow: hidden;
  position: relative;
  background: var(--bg-base);
}

/* ─── Logout ─── */
:deep(.logout-item) {
  color: var(--color-danger) !important;
}

:deep(.logout-item:hover) {
  background: rgba(255, 59, 48, 0.08) !important;
  color: var(--color-danger) !important;
}
</style>

<style>
/* 后台字体风格：苹果字体栈 + 黑色加粗 */
.admin-layout-root {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'PingFang SC',
    'Noto Sans SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  color: var(--text-primary);
}

/* 标题加粗纯黑 */
.admin-layout-root h1,
.admin-layout-root h2,
.admin-layout-root h3 {
  font-weight: 700 !important;
  color: var(--text-primary) !important;
}

/* 表格增强 */
.admin-layout-root .el-table {
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
}
</style>
