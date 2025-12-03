<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const searchKeyword = ref('')

const isLogin = computed(() => userStore.isLogin)
const userAvatar = computed(() => userStore.avatar)
const userNickname = computed(() => userStore.nickname)

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
  <div class="min-h-screen bg-nb-bg">
    <!-- 顶部导航栏 - Neo-Brutalism -->
    <header class="sticky top-0 z-50 bg-pop-yellow border-b-3 border-black">
      <div class="max-w-7xl mx-auto px-4 h-18 flex items-center justify-between">
        <!-- Logo & Navigation -->
        <div class="flex items-center space-x-8">
          <router-link to="/" class="flex items-center space-x-2 hover:animate-shake">
            <span class="text-2xl font-black text-black uppercase">果冻影院</span>
          </router-link>
          
          <nav class="hidden md:flex space-x-2">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="px-4 py-2 font-bold text-black border-2 border-transparent rounded-lg hover:border-black hover:bg-white transition-all"
              active-class="!bg-white !border-black shadow-brutal-sm"
            >
              {{ item.label }}
            </router-link>
          </nav>
        </div>

        <!-- Search Bar - Neo-Brutalism -->
        <div class="flex-1 max-w-md mx-8">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索电影、帖子、用户..."
            clearable
            size="large"
            class="nb-input"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- User Area -->
        <div class="flex items-center space-x-4">
          <!-- 消息通知 -->
          <el-badge :value="3" :max="99" v-if="isLogin">
            <el-button circle class="!bg-white !border-2 !border-black" @click="goToChat">
              <el-icon><Bell /></el-icon>
            </el-button>
          </el-badge>

          <!-- 用户菜单 -->
          <template v-if="isLogin">
            <el-dropdown trigger="click">
              <div class="flex items-center space-x-2 cursor-pointer bg-white border-2 border-black rounded-full px-3 py-1 hover:shadow-brutal-sm transition-all">
                <el-avatar :size="32" :src="userAvatar" class="!border-2 !border-black" />
                <span class="text-black font-bold hidden lg:inline">{{ userNickname }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu class="!border-3 !border-black !shadow-brutal !rounded-xl">
                  <el-dropdown-item @click="router.push('/user')">
                    <el-icon><User /></el-icon>
                    个人中心
                  </el-dropdown-item>
                  <el-dropdown-item @click="router.push('/chat')">
                    <el-icon><ChatDotRound /></el-icon>
                    消息中心
                  </el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" @click="router.push('/admin')">
                    <el-icon><Setting /></el-icon>
                    管理后台
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout" class="!text-pop-red">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <router-link to="/login">
              <el-button type="primary" class="!bg-pop-blue !text-white !border-2 !border-black !font-bold !shadow-brutal-sm hover:!translate-x-0.5 hover:!-translate-y-0.5 transition-all">
                登录
              </el-button>
            </router-link>
          </template>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="max-w-7xl mx-auto px-4 py-6">
      <router-view />
    </main>

    <!-- Footer - Neo-Brutalism -->
    <footer class="bg-black border-t-3 border-black py-8 mt-12">
      <div class="max-w-7xl mx-auto px-4 text-center">
        <div class="inline-block bg-pop-yellow border-3 border-white rounded-xl px-6 py-3 mb-4">
          <p class="text-black font-black">© 2024 果冻影院 Jelly Cinema</p>
        </div>
        <p class="text-white font-bold">影视 + 社交 + AI 一体化平台</p>
      </div>
    </footer>
  </div>
</template>
