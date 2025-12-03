<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

const menuItems = [
  { path: '/admin', icon: 'Odometer', title: '仪表盘' },
  { path: '/admin/users', icon: 'User', title: '用户管理' },
  { path: '/admin/films', icon: 'Film', title: '影片管理' },
  { path: '/admin/sensitive', icon: 'Edit', title: '敏感词库' },
  { path: '/admin/reports', icon: 'Warning', title: '举报处理' },
  { path: '/admin/groups', icon: 'ChatDotRound', title: '群组审计' },
]

const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta?.title)
  return matched.map(item => ({
    title: item.meta?.title as string,
    path: item.path
  }))
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout bg-nb-bg">
    <!-- 侧边栏 - Neo-Brutalism -->
    <aside :class="['sidebar bg-white border-r-3 border-black shadow-brutal flex flex-col transition-all duration-300', { 'w-64': !isCollapsed, 'w-20': isCollapsed }]">
      <!-- Logo -->
      <div class="h-18 flex items-center justify-center border-b-3 border-black bg-pop-yellow">
        <router-link to="/admin" class="flex items-center gap-2 hover:animate-shake">
          <span v-if="!isCollapsed" class="text-lg font-black text-black uppercase">Admin Panel</span>
          <span v-else class="text-xl font-black text-black">A</span>
        </router-link>
      </div>

      <!-- 菜单 -->
      <nav class="flex-1 py-4 space-y-2 px-2">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="['flex items-center gap-3 px-4 py-3 rounded-xl border-2 border-transparent transition-all font-bold', { 
            'bg-pop-blue text-white border-black shadow-brutal-sm translate-x-1 -translate-y-1': route.path === item.path,
            'text-nb-text hover:bg-gray-100 hover:border-black': route.path !== item.path
          }]"
        >
          <el-icon size="20"><component :is="item.icon" /></el-icon>
          <span v-if="!isCollapsed">{{ item.title }}</span>
        </router-link>
      </nav>

      <!-- 底部 -->
      <div class="border-t-3 border-black py-4 px-2 bg-gray-100">
        <router-link to="/" class="flex items-center gap-3 px-4 py-3 rounded-xl border-2 border-black bg-white hover:bg-pop-green hover:shadow-brutal-sm transition-all font-bold text-black justify-center">
          <el-icon size="20"><House /></el-icon>
          <span v-if="!isCollapsed">返回前台</span>
        </router-link>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 顶部栏 - Neo-Brutalism -->
      <header class="h-18 px-6 flex items-center justify-between bg-white border-b-3 border-black">
        <div class="flex items-center gap-4">
          <el-button circle class="!border-2 !border-black !bg-pop-purple !text-white hover:!translate-x-0.5 hover:!-translate-y-0.5 transition-all" @click="isCollapsed = !isCollapsed">
            <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
          </el-button>
          
          <!-- 面包屑 -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              <span class="font-bold text-black">{{ item.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="flex items-center gap-4">
          <el-dropdown trigger="click">
            <div class="flex items-center gap-2 cursor-pointer bg-pop-yellow border-2 border-black rounded-full px-3 py-1 hover:shadow-brutal-sm transition-all">
              <el-avatar :size="32" :src="userStore.avatar" class="!border-2 !border-black bg-white">{{ userStore.nickname?.[0] }}</el-avatar>
              <span class="font-bold text-black">{{ userStore.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="!border-3 !border-black !shadow-brutal !rounded-xl">
                <el-dropdown-item @click="router.push('/user')">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" class="!text-pop-red">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="flex-1 overflow-auto p-6 bg-nb-bg">
        <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-6 min-h-full">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  @apply flex h-screen;
}
</style>
