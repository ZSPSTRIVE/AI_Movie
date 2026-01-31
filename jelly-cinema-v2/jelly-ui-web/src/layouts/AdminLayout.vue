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
  { path: '/admin/homepage', icon: 'HomeFilled', title: '首页管理' },
  { path: '/admin/tvbox-sources', icon: 'Connection', title: '采集源配置' },
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
  <div class="admin-layout">
    <!-- 侧边栏 - Glass Stone -->
    <aside :class="['sidebar glass-card border-r-0 mr-4 my-4 ml-4 flex flex-col transition-all duration-300', { 'w-64': !isCollapsed, 'w-24': isCollapsed }]">
      <!-- Logo -->
      <div class="h-20 flex items-center justify-center border-b border-white/10">
        <router-link to="/admin" class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-600 to-orange-700 flex items-center justify-center shadow-lg shadow-orange-900/50">
            <el-icon size="24" color="#FFF"><Film /></el-icon>
          </div>
          <span v-if="!isCollapsed" class="text-xl font-bold tracking-wide text-white drop-shadow-md">JELLY ADMIN</span>
        </router-link>
      </div>

      <!-- 菜单 -->
      <nav class="flex-1 py-6 space-y-2 px-3">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="['flex items-center gap-4 px-4 py-3.5 rounded-xl transition-all duration-300 group', { 
            'bg-gradient-to-r from-amber-700/80 to-orange-600/80 text-white shadow-lg shadow-orange-900/20 ring-1 ring-orange-500/50': route.path === item.path,
            'text-gray-400 hover:bg-white/5 hover:text-white': route.path !== item.path
          }]"
        >
          <el-icon size="20" :class="route.path === item.path ? 'text-white' : 'text-gray-500 group-hover:text-amber-500 transition-colors'"><component :is="item.icon" /></el-icon>
          <span v-if="!isCollapsed" class="font-medium tracking-wide">{{ item.title }}</span>
        </router-link>
      </nav>

      <!-- 底部 -->
      <div class="p-4 border-t border-white/10">
        <router-link to="/" class="flex items-center gap-3 px-4 py-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 transition-all text-gray-300 hover:text-white justify-center group">
          <el-icon size="18" class="group-hover:text-amber-500 transition-colors"><House /></el-icon>
          <span v-if="!isCollapsed" class="font-medium">返回前台</span>
        </router-link>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="flex-1 flex flex-col overflow-hidden mr-4 my-4">
      <!-- 顶部栏 - Glass Stone -->
      <header class="h-20 px-8 flex items-center justify-between glass-card mb-4 rounded-2xl">
        <div class="flex items-center gap-6">
          <button class="w-10 h-10 rounded-xl bg-white/5 hover:bg-white/10 flex items-center justify-center text-gray-400 hover:text-white transition-all border border-white/10" @click="isCollapsed = !isCollapsed">
            <el-icon size="20"><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
          </button>
          
          <!-- 面包屑 -->
          <el-breadcrumb separator="/" class="glass-breadcrumb">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              <span class="font-medium text-gray-300">{{ item.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="flex items-center gap-6">
          <div class="flex items-center gap-4">
             <el-button circle class="!bg-white/5 !border-white/10 !text-gray-400 hover:!text-amber-500 hover:!bg-white/10 hover:!border-amber-500/30">
                <el-icon><Bell /></el-icon>
             </el-button>
             <el-button circle class="!bg-white/5 !border-white/10 !text-gray-400 hover:!text-amber-500 hover:!bg-white/10 hover:!border-amber-500/30">
                <el-icon><Setting /></el-icon>
             </el-button>
          </div>
          
          <div class="h-8 w-px bg-white/10"></div>

          <el-dropdown trigger="click">
            <div class="flex items-center gap-3 cursor-pointer py-1 px-2 rounded-xl hover:bg-white/5 transition-all">
              <el-avatar :size="36" :src="userStore.avatar" class="!bg-amber-700 !text-white ring-2 ring-amber-500/30">{{ userStore.nickname?.[0] }}</el-avatar>
              <div class="flex flex-col">
                 <span class="text-sm font-bold text-gray-200">{{ userStore.nickname }}</span>
                 <span class="text-xs text-gray-500">Administrator</span>
              </div>
              <el-icon class="text-gray-500"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="glass-dropdown">
                <el-dropdown-item @click="router.push('/user')">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" class="!text-red-400 hover:!bg-red-900/20">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="flex-1 overflow-auto glass-card rounded-2xl relative">
        <div class="absolute inset-0 overflow-auto p-6 scroll-smooth">
          <router-view v-slot="{ Component }">
             <transition name="fade-transform" mode="out-in">
               <component :is="Component" />
             </transition>
          </router-view>
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
