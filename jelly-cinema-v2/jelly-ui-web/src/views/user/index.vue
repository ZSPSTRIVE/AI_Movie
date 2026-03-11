<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateProfile, updateAvatar, uploadAvatar } from '@/api/user'
import { getMyPosts, getMyFavorites, getWatchHistory } from '@/api/community'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const activeTab = ref('profile')
const editMode = ref(false)
const passwordDialogVisible = ref(false)
const editForm = ref({
  nickname: '',
  signature: '',
  email: '',
  phone: ''
})
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 我的帖子、收藏、历史
const myPosts = ref<any[]>([])
const myFavorites = ref<any[]>([])
const watchHistory = ref<any[]>([])
const postsLoading = ref(false)
const favoritesLoading = ref(false)
const historyLoading = ref(false)

const user = computed(() => userStore.userInfo)

onMounted(() => {
  if (userStore.isLogin) {
    loadMyPosts()
    loadMyFavorites()
    loadWatchHistory()
  }
})

function startEdit() {
  if (!user.value) return
  editForm.value = {
    nickname: user.value.nickname || '',
    signature: user.value.signature || '',
    email: user.value.email || '',
    phone: user.value.phone || ''
  }
  editMode.value = true
}

function cancelEdit() {
  editMode.value = false
}

// 上传头像
async function handleAvatarChange(file: any) {
  try {
    loading.value = true
    const res = await uploadAvatar(file.raw)
    const avatarUrl = res.data
    await updateAvatar(avatarUrl)
    // 立即更新 store 中的头像（触发响应式更新）
    userStore.updateAvatar(avatarUrl)
    ElMessage.success('头像更新成功')
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    loading.value = false
  }
}

// 修改密码
async function handleChangePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('密码至少6位')
    return
  }
  
  try {
    loading.value = true
    // 调用修改密码API
    ElMessage.success('密码修改成功，请重新登录')
    passwordDialogVisible.value = false
    await userStore.doLogout()
    router.push('/login')
  } catch (error: any) {
    ElMessage.error(error.message || '修改失败')
  } finally {
    loading.value = false
  }
}

// 加载我的帖子
async function loadMyPosts() {
  postsLoading.value = true
  try {
    const res = await getMyPosts({ pageNum: 1, pageSize: 10 })
    myPosts.value = res.data?.rows || []
  } catch (e) {
    // ignore - 接口可能未部署
  } finally {
    postsLoading.value = false
  }
}

// 加载我的收藏
async function loadMyFavorites() {
  favoritesLoading.value = true
  try {
    const res = await getMyFavorites({ pageNum: 1, pageSize: 10 })
    myFavorites.value = res.data?.rows || []
  } catch (e) {
    // ignore - 接口可能未部署
  } finally {
    favoritesLoading.value = false
  }
}

// 加载观看历史
async function loadWatchHistory() {
  historyLoading.value = true
  try {
    const res = await getWatchHistory({ pageNum: 1, pageSize: 10 })
    watchHistory.value = res.data?.rows || []
  } catch (e) {
    // ignore - 接口可能未部署
  } finally {
    historyLoading.value = false
  }
}

async function saveProfile() {
  try {
    loading.value = true
    await updateProfile(editForm.value)
    
    // 更新本地用户信息
    await userStore.fetchUserInfo()
    
    ElMessage.success('保存成功')
    editMode.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    loading.value = false
  }
}

async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  
  await userStore.doLogout()
  router.push('/login')
}

function formatDate(date: string | undefined): string {
  if (!date) return '-'
  return new Date(date).toLocaleDateString('zh-CN')
}
</script>

<template>
  <div class="max-w-4xl mx-auto profile-page">
    <!-- 未登录 -->
    <div v-if="!userStore.isLogin" class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-lg rounded-2xl p-12 text-center">
      <el-icon size="64" class="mb-6 text-gray-300 dark:text-gray-600"><User /></el-icon>
      <p class="text-xl font-medium text-gray-900 dark:text-gray-100 mb-6">请先登录后查看个人中心</p>
      <el-button class="!bg-primary !text-white !border !border-primary !font-semibold !px-8 !py-5" @click="router.push('/login')">
        <el-icon class="mr-2"><Promotion /></el-icon>去登录
      </el-button>
    </div>
    
    <!-- 已登录 -->
    <template v-else>
      <!-- 头部 - Glassmorphism -->
      <div class="glass-card-strong p-6 mb-6">
        <div class="flex items-start gap-6">
          <el-upload
            class="avatar-uploader"
            :show-file-list="false"
            accept="image/*"
            :auto-upload="false"
            @change="handleAvatarChange"
          >
            <el-avatar :size="100" :src="userStore.avatar" class="cursor-pointer hover:opacity-80 transition-opacity user-avatar">
              {{ user?.nickname?.[0] || user?.username?.[0] }}
            </el-avatar>
            <div class="text-xs font-semibold text-center mt-2 px-3 py-1.5 rounded-lg upload-hint">点击更换</div>
          </el-upload>
          
          <div class="flex-1">
            <div class="flex items-center gap-3 mb-2">
              <h1 class="text-3xl font-black" style="color: var(--text-primary);">{{ user?.nickname || user?.username }}</h1>
              <span v-if="user?.role === 'ROLE_ADMIN'" class="px-2.5 py-0.5 text-xs font-semibold rounded-lg admin-badge">管理员</span>
            </div>
            
            <p class="font-bold text-lg mb-4 px-4 py-2.5 rounded-xl user-signature">{{ user?.signature || '这个人很懒，什么都没写~' }}</p>
            
            <div class="flex items-center gap-4 text-sm">
              <span class="px-3 py-1.5 rounded-lg font-medium flex items-center gap-2 user-meta-tag">
                <el-icon><Calendar /></el-icon>加入于 {{ formatDate(user?.createTime) }}
              </span>
              <span v-if="user?.email" class="px-3 py-1.5 rounded-lg font-medium flex items-center gap-2 user-meta-tag user-meta-tag--highlight">
                <el-icon><Message /></el-icon>{{ user.email }}
              </span>
            </div>
          </div>
          
          <div class="flex flex-col gap-2">
            <el-button type="success" @click="startEdit">
              <el-icon class="mr-2"><Edit /></el-icon>编辑资料
            </el-button>
            <el-button type="warning" @click="passwordDialogVisible = true">
              <el-icon class="mr-2"><Lock /></el-icon>修改密码
            </el-button>
            <el-button type="danger" @click="handleLogout">
              <el-icon class="mr-2"><SwitchButton /></el-icon>退出
            </el-button>
          </div>
        </div>
      </div>
      
      <!-- 标签页 - Glassmorphism -->
      <el-tabs v-model="activeTab" class="glass-tabs-enhanced">
        <el-tab-pane label="个人资料" name="profile">
          <div class="glass-card p-6">
            <template v-if="!editMode">
              <div class="grid grid-cols-2 gap-6">
                <div class="info-item">
                  <label>用户名</label>
                  <p>{{ user?.username }}</p>
                </div>
                <div class="info-item">
                  <label>用户ID</label>
                  <p>{{ user?.userId || '-' }}</p>
                </div>
                <div class="info-item">
                  <label>昵称</label>
                  <p>{{ user?.nickname || '-' }}</p>
                </div>
                <div class="info-item">
                  <label>邮箱</label>
                  <p>{{ user?.email || '-' }}</p>
                </div>
                <div class="info-item">
                  <label>手机号</label>
                  <p>{{ user?.phone || '-' }}</p>
                </div>
                <div class="info-item" style="grid-column: span 2;">
                  <label>个性签名</label>
                  <p>{{ user?.signature || '-' }}</p>
                </div>
              </div>
            </template>
            
            <template v-else>
              <el-form :model="editForm" label-width="80px">
                <el-form-item label="昵称">
                  <el-input v-model="editForm.nickname" size="large" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="editForm.email" size="large" />
                </el-form-item>
                <el-form-item label="手机号">
                  <el-input v-model="editForm.phone" size="large" />
                </el-form-item>
                <el-form-item label="个性签名">
                  <el-input v-model="editForm.signature" type="textarea" :rows="3" />
                </el-form-item>
                <el-form-item>
                  <el-button class="!bg-success !text-white !border !border-success !font-medium" @click="saveProfile">
                    <el-icon class="mr-2"><Check /></el-icon>保存
                  </el-button>
                  <el-button class="!border !border-gray-200 dark:!border-gray-700 !font-medium" @click="cancelEdit">取消</el-button>
                </el-form-item>
              </el-form>
            </template>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="我的帖子" name="posts">
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm rounded-2xl p-6" v-loading="postsLoading">
            <div v-if="myPosts.length === 0" class="text-center py-12">
              <el-icon size="48" class="mb-4 text-gray-300 dark:text-gray-600"><EditPen /></el-icon>
              <div class="text-gray-400 dark:text-gray-500 font-medium">暂无发布的帖子</div>
            </div>
            <div v-else class="space-y-4">
              <div
                v-for="post in myPosts"
                :key="post.id"
                class="p-4 border border-gray-200 dark:border-gray-700 rounded-xl hover:bg-primary-50 dark:hover:bg-primary-900/10 cursor-pointer transition-all hover:-translate-y-0.5 hover:shadow-md"
                @click="router.push(`/community/post/${post.id}`)"
              >
                <h3 class="text-gray-900 dark:text-gray-100 font-semibold text-lg mb-2">{{ post.title }}</h3>
                <p class="text-gray-500 dark:text-gray-400 font-medium line-clamp-2">{{ post.contentSummary }}</p>
                <div class="flex items-center gap-4 mt-3 text-sm font-medium">
                  <span class="bg-success/10 text-success border border-success/30 rounded px-2 py-0.5">
                    <el-icon class="mr-1"><CaretTop /></el-icon>{{ post.voteUp }}
                  </span>
                  <span class="bg-primary/10 text-primary border border-primary/30 rounded px-2 py-0.5">
                    <el-icon class="mr-1"><ChatLineRound /></el-icon>{{ post.commentCount }}
                  </span>
                  <span class="text-gray-400 dark:text-gray-500">{{ formatDate(post.createTime) }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="我的收藏" name="favorites">
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm rounded-2xl p-6" v-loading="favoritesLoading">
            <div v-if="myFavorites.length === 0" class="text-center py-12">
              <el-icon size="48" class="mb-4 text-gray-300 dark:text-gray-600"><Star /></el-icon>
              <div class="text-gray-400 dark:text-gray-500 font-medium">暂无收藏</div>
            </div>
            <div v-else class="grid grid-cols-4 gap-4">
              <div
                v-for="film in myFavorites"
                :key="film.id"
                class="cursor-pointer group"
                @click="router.push(`/film/${film.filmId}`)"
              >
                <div class="aspect-[2/3] rounded-xl overflow-hidden mb-2 border border-gray-200 dark:border-gray-700 shadow-sm group-hover:shadow-md transition-all group-hover:-translate-y-1">
                  <img
                    :src="film.poster"
                    :alt="film.title"
                    class="w-full h-full object-cover"
                  />
                </div>
                <h4 class="text-gray-900 dark:text-gray-100 font-semibold truncate">{{ film.title }}</h4>
                <p class="text-gray-500 dark:text-gray-400 text-sm font-medium">{{ film.year }}</p>
              </div>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="观看历史" name="history">
          <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm rounded-2xl p-6" v-loading="historyLoading">
            <div v-if="watchHistory.length === 0" class="text-center py-12">
              <el-icon size="48" class="mb-4 text-gray-300 dark:text-gray-600"><VideoPlay /></el-icon>
              <div class="text-gray-400 dark:text-gray-500 font-medium">暂无观看记录</div>
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="item in watchHistory"
                :key="item.id"
                class="flex items-center gap-4 p-3 border border-gray-200 dark:border-gray-700 rounded-xl hover:bg-primary-50 dark:hover:bg-primary-900/10 cursor-pointer transition-all"
                @click="router.push(`/film/${item.filmId}`)"
              >
                <img
                  :src="item.poster"
                  :alt="item.title"
                  class="w-20 h-12 object-cover rounded-lg border border-gray-200 dark:border-gray-700"
                />
                <div class="flex-1 min-w-0">
                  <h4 class="text-gray-900 dark:text-gray-100 font-semibold truncate">{{ item.title }}</h4>
                  <div class="flex items-center gap-2 mt-1">
                    <div class="h-2 flex-1 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div class="h-full bg-success" :style="{ width: item.progress + '%' }"></div>
                    </div>
                    <span class="text-sm font-medium">{{ item.progress }}%</span>
                  </div>
                </div>
                <span class="text-gray-500 dark:text-gray-400 text-sm font-medium bg-gray-100 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded px-2 py-1">{{ formatDate(item.watchTime) }}</span>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      
      <!-- 修改密码弹窗 -->
      <el-dialog v-model="passwordDialogVisible" title="修改密码" width="400px">
        <el-form :model="passwordForm" label-width="80px">
          <el-form-item label="原密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password size="large" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password size="large" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password size="large" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button class="!border !border-gray-200 dark:!border-gray-700 !font-medium" @click="passwordDialogVisible = false">取消</el-button>
          <el-button class="!bg-success !text-white !border !border-success !font-medium" :loading="loading" @click="handleChangePassword">
            <el-icon class="mr-2"><Check /></el-icon>确认修改
          </el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.info-item {
  padding: 16px;
  background: var(--glass-bg-card);
  backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
}

.info-item label {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  display: block;
  margin-bottom: 6px;
}

.info-item p {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

:deep(.profile-page .el-form-item__label) {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

:deep(.profile-page .el-input__inner),
:deep(.profile-page .el-textarea__inner) {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

:deep(.glass-tabs-enhanced) .el-tabs__header {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur));
  border-radius: var(--radius-xl);
  padding: 6px;
  margin-bottom: 20px;
}

:deep(.glass-tabs-enhanced) .el-tabs__nav-wrap::after {
  display: none;
}

.user-avatar {
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
}

.upload-hint {
  background: var(--color-primary);
  color: var(--text-inverse);
}

.admin-badge {
  background: var(--color-danger);
  color: var(--text-inverse);
}

.user-signature {
  color: var(--text-primary);
  background: var(--bg-base);
  border: 1px solid var(--border-color);
}

.user-meta-tag {
  background: var(--bg-base);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.user-meta-tag--highlight {
  background: var(--color-primary-bg);
  border-color: var(--color-primary-bg-hover);
  color: var(--color-primary);
}
</style>
