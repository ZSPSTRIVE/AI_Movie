<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserSetting, updateUserSetting, type UserSetting } from '@/api/im'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

// 设置选项
const settings = ref({
  // 消息通知
  enableNotification: true,
  enableSound: true,
  // 隐私设置
  showOnlineStatus: true,
  allowStrangerMsg: false,
  // 聊天设置
  enterToSend: true,
  showReadStatus: true
})

// 用户信息
const userInfo = computed(() => userStore.userInfo)

// 监听抽屉打开，加载设置
watch(() => props.visible, async (val) => {
  if (val) {
    await loadSettings()
  }
})

// 退出登录
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', { type: 'warning' })
    userStore.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch {
    // 取消
  }
}

// 保存单个设置项
async function saveSetting(key: keyof typeof settings.value) {
  try {
    await updateUserSetting({ [key]: settings.value[key] })
    // 同时保存到本地存储（用于快速加载）
    localStorage.setItem('chat_settings', JSON.stringify(settings.value))
    ElMessage.success('设置已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

// 加载设置
async function loadSettings() {
  // 先从本地存储快速加载
  const saved = localStorage.getItem('chat_settings')
  if (saved) {
    try {
      Object.assign(settings.value, JSON.parse(saved))
    } catch {
      // 忽略解析错误
    }
  }
  
  // 然后从服务器获取最新设置
  loading.value = true
  try {
    const res = await getUserSetting()
    if (res.data) {
      const data = res.data
      settings.value = {
        enableNotification: data.enableNotification === 1,
        enableSound: data.enableSound === 1,
        showOnlineStatus: data.showOnlineStatus === 1,
        allowStrangerMsg: data.allowStrangerMsg === 1,
        enterToSend: data.enterToSend === 1,
        showReadStatus: data.showReadStatus === 1
      }
      // 更新本地存储
      localStorage.setItem('chat_settings', JSON.stringify(settings.value))
    }
  } catch (e) {
    // 静默失败，使用本地缓存
    console.warn('加载用户设置失败，使用本地缓存')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="设置"
    size="360px"
    class="nb-drawer"
  >
    <div v-loading="loading" class="space-y-6">
      <!-- 个人信息 -->
      <div class="bg-primary rounded-2xl p-4">
        <div class="flex items-center gap-4">
          <el-avatar :size="60" :src="userStore.avatar" class="!border-2 !border-white/30">
            {{ userInfo?.nickname?.[0] }}
          </el-avatar>
          <div class="flex-1 text-white">
            <h3 class="font-bold text-lg">{{ userInfo?.nickname }}</h3>
            <p class="text-sm opacity-80">ID: {{ userInfo?.userId }}</p>
          </div>
          <router-link to="/user/profile">
            <el-button size="small" class="!bg-white !text-gray-900 !border !border-gray-200 !font-medium">
              编辑资料
            </el-button>
          </router-link>
        </div>
      </div>

      <!-- 消息通知设置 -->
      <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <div class="flex items-center mb-4">
          <span class="bg-warning/20 text-warning border border-warning/30 rounded-lg px-3 py-1 font-semibold">消息通知</span>
        </div>
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-900 dark:text-gray-100">开启消息通知</span>
            <el-switch v-model="settings.enableNotification" @change="saveSetting('enableNotification')" />
          </div>
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-900 dark:text-gray-100">消息提示音</span>
            <el-switch v-model="settings.enableSound" @change="saveSetting('enableSound')" />
          </div>
        </div>
      </div>

      <!-- 隐私设置 -->
      <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <div class="flex items-center mb-4">
          <span class="bg-info/20 text-info border border-info/30 rounded-lg px-3 py-1 font-semibold">隐私设置</span>
        </div>
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-900 dark:text-gray-100">显示在线状态</span>
            <el-switch v-model="settings.showOnlineStatus" @change="saveSetting('showOnlineStatus')" />
          </div>
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-900 dark:text-gray-100">允许陌生人消息</span>
            <el-switch v-model="settings.allowStrangerMsg" @change="saveSetting('allowStrangerMsg')" />
          </div>
        </div>
      </div>

      <!-- 聊天设置 -->
      <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4">
        <div class="flex items-center mb-4">
          <span class="bg-success/20 text-success border border-success/30 rounded-lg px-3 py-1 font-semibold">聊天设置</span>
        </div>
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-900 dark:text-gray-100">按 Enter 发送消息</span>
            <el-switch v-model="settings.enterToSend" @change="saveSetting('enterToSend')" />
          </div>
          <div class="flex items-center justify-between">
            <span class="font-medium text-gray-900 dark:text-gray-100">显示已读状态</span>
            <el-switch v-model="settings.showReadStatus" @change="saveSetting('showReadStatus')" />
          </div>
        </div>
      </div>

      <!-- 其他操作 -->
      <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-4 space-y-3">
        <router-link to="/about" class="block">
          <div class="flex items-center justify-between py-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 rounded-lg px-2 -mx-2 transition-colors">
            <span class="font-medium text-gray-900 dark:text-gray-100">关于我们</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </router-link>
        <router-link to="/help" class="block">
          <div class="flex items-center justify-between py-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700 rounded-lg px-2 -mx-2 transition-colors">
            <span class="font-medium text-gray-900 dark:text-gray-100">帮助与反馈</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </router-link>
      </div>

      <!-- 退出登录 -->
      <el-button
        class="w-full !bg-danger !text-white !border !border-danger !font-semibold"
        @click="handleLogout"
      >
        退出登录
      </el-button>
        
      <!-- 版本信息 -->
      <div class="text-center text-gray-400 text-sm">
        Jelly Cinema v2.0.0
      </div>
    </div>
  </el-drawer>
</template>
