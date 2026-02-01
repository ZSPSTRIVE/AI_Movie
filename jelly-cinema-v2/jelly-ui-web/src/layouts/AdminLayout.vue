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
    await userStore.logout()
    
    router.push('/login')
  } catch (error) {
    // 用户取消或 logout 失败（如果是 API 调用错误，通常也在 store 内部处理了，这里 catch 住避免崩溃）
    console.log('Logout cancelled or failed', error)
  }
}
</script>

<template>
  <div class="admin-layout-root flex h-screen bg-gray-50">
    <!-- 侧边栏 - Light Theme -->
    <aside :class="['bg-white border-r border-gray-200 flex flex-col transition-all duration-300 z-20', { 'w-64': !isCollapsed, 'w-20': isCollapsed }]">
      <!-- Logo -->
      <div class="h-16 flex items-center justify-center border-b border-gray-100">
        <router-link to="/admin" class="flex items-center gap-3 overflow-hidden px-4">
          <div class="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center shrink-0">
            <svg-icon name="icon-a-24Hanbao" size="22" color="#FFF" />
          </div>
          <span v-if="!isCollapsed" class="text-lg font-bold text-gray-800 whitespace-nowrap">JELLY ADMIN</span>
        </router-link>
      </div>

      <!-- 菜单 -->
      <nav class="flex-1 py-4 space-y-1 px-3 overflow-y-auto">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          :class="['flex items-center gap-3 px-3 py-3 rounded-lg transition-all duration-200 group mb-1', { 
            'bg-indigo-50 text-indigo-600': route.path === item.path,
            'text-gray-600 hover:bg-gray-50 hover:text-gray-900': route.path !== item.path
          }]"
        >
          <el-icon size="18" :class="route.path === item.path ? 'text-indigo-600' : 'text-gray-400 group-hover:text-gray-600'"><svg-icon :name="item.icon" /></el-icon>
          <span v-if="!isCollapsed" class="font-medium text-sm whitespace-nowrap">{{ item.title }}</span>
        </router-link>
      </nav>

      <!-- 底部 -->
      <div class="p-4 border-t border-gray-100">
        <router-link to="/" class="flex items-center gap-3 px-3 py-2 rounded-lg text-gray-500 hover:bg-gray-50 hover:text-gray-700 transition-all justify-center group" title="返回前台">
          <el-icon size="18"><svg-icon name="icon-qiantai" /></el-icon>
          <span v-if="!isCollapsed" class="font-medium text-sm">返回前台</span>
        </router-link>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <!-- 顶部栏 - Light Theme -->
      <header class="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6 shadow-sm z-10">
        <div class="flex items-center gap-4">
          <button class="p-2 rounded-lg hover:bg-gray-100 text-gray-500 transition-colors" @click="isCollapsed = !isCollapsed">
            <el-icon size="20"><svg-icon :name="!isCollapsed ? 'icon-a-xiala2' : 'icon-a-24Hanbao'" :style="{ transform: !isCollapsed ? 'rotate(90deg)' : '' }" /></el-icon>
          </button>
          
          <!-- 面包屑 -->
          <el-breadcrumb separator="/" class="hidden md:block">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path" :to="item.path">
              <span class="font-medium text-gray-600">{{ item.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
             <button class="p-2 rounded-full text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 transition-all">
                <el-icon size="18"><svg-icon name="icon-tishi" /></el-icon>
             </button>
             <button class="p-2 rounded-full text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 transition-all">
                <el-icon size="18"><svg-icon name="icon-guanliyuanzhongxin" /></el-icon>
             </button>
          </div>
          
          <div class="h-6 w-px bg-gray-200 mx-2"></div>

          <el-dropdown trigger="click">
            <div class="flex items-center gap-3 cursor-pointer py-1 px-2 rounded-lg hover:bg-gray-50 transition-all">
              <el-avatar :size="32" :src="userStore.avatar" class="bg-indigo-100 text-indigo-600 font-bold">{{ userStore.nickname?.[0]?.toUpperCase() }}</el-avatar>
              <div class="hidden md:flex flex-col text-right">
                 <span class="text-sm font-semibold text-gray-700 leading-tight">{{ userStore.nickname }}</span>
                 <span class="text-xs text-gray-400">Administrator</span>
              </div>
              <el-icon class="text-gray-400"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="min-w-[140px]">
                <el-dropdown-item @click="router.push('/user')">
                  <el-icon><svg-icon name="icon-gerenzhongxin-zhihui" /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" class="text-red-500 hover:bg-red-50">
                  <el-icon><svg-icon name="icon-guanbi" /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="flex-1 overflow-hidden relative bg-gray-50">
        <div class="absolute inset-0 overflow-auto scroll-smooth">
          <router-view v-slot="{ Component }">
             <transition name="fade" mode="out-in">
               <component :is="Component" />
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
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

<style>
/* 全局后台字体风格：黑体 + 加粗 + 纯黑 */
.admin-layout-root {
  font-family: "SimHei", "Heiti SC", "Microsoft YaHei", sans-serif;
  color: #000;
  font-weight: 600; /* 全局基准字重 */
}

/* 强制覆盖 Element Plus 组件内部字体 */
.admin-layout-root .el-button,
.admin-layout-root .el-input,
.admin-layout-root .el-form-item__label,
.admin-layout-root .el-table,
.admin-layout-root .el-dialog__title,
.admin-layout-root .el-drawer__header {
  font-family: "SimHei", "Heiti SC", "Microsoft YaHei", sans-serif !important;
  font-weight: 600 !important;
  color: #000 !important;
}

/* 强化表格内容 */
.admin-layout-root .el-table {
  --el-table-text-color: #000;
  --el-table-header-text-color: #000;
}

/* 覆盖 Tailwind 的灰色文本，使其更黑 */
.admin-layout-root .text-gray-500,
.admin-layout-root .text-gray-400,
.admin-layout-root .text-gray-600 {
  color: #222 !important; /* 深黑灰，保证可读性但接近纯黑 */
}

/* 标题极粗纯黑 */
.admin-layout-root h1, 
.admin-layout-root h2, 
.admin-layout-root h3, 
.admin-layout-root .font-bold {
  font-weight: 800 !important;
  color: #000 !important;
}

/* 侧边栏菜单文字增强 */
.admin-layout-root nav span {
  font-weight: 700 !important;
}
</style>
