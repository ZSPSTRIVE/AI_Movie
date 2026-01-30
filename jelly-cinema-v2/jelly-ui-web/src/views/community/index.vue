<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPostList, vote, createPost, type Post } from '@/api/community'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const postList = ref<Post[]>([])
const total = ref(0)
const query = ref({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})

const showCreateDialog = ref(false)
const createForm = ref({
  title: '',
  contentHtml: ''
})
const creating = ref(false)

async function handleCreatePost() {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  if (!createForm.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!createForm.value.contentHtml.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  
  creating.value = true
  try {
    await createPost({
      title: createForm.value.title,
      contentHtml: `<p>${createForm.value.contentHtml}</p>`
    })
    ElMessage.success('发布成功')
    showCreateDialog.value = false
    createForm.value = { title: '', contentHtml: '' }
    fetchPosts()
  } catch (e) {
    // handled by interceptor
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  fetchPosts()
})

async function fetchPosts() {
  loading.value = true
  try {
    const res = await getPostList(query.value)
    console.log('帖子列表响应:', JSON.stringify(res, null, 2))
    console.log('res.data:', res.data)
    console.log('res.data.rows:', res.data?.rows)
    postList.value = res.data?.rows || []
    total.value = res.data?.total || 0
    console.log('postList length:', postList.value.length)
  } catch (e) {
    console.error('获取帖子失败:', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.value.pageNum = 1
  fetchPosts()
}

function goToDetail(id: string | number) {
  router.push(`/community/post/${id}`)
}

async function handleVote(post: Post, type: number) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  
  try {
    const newType = post.voteStatus === type ? 0 : type
    await vote(post.id, newType)
    
    // 更新本地状态
    if (post.voteStatus === 1) post.voteUp--
    if (post.voteStatus === -1) post.voteDown--
    if (newType === 1) post.voteUp++
    if (newType === -1) post.voteDown++
    post.voteStatus = newType
  } catch (e) {
    // handled by interceptor
  }
}

function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return date.toLocaleDateString()
}
</script>

<template>
  <div class="space-y-6">
    <!-- Header - Glassmorphism -->
    <div class="flex items-center justify-between">
      <h1 class="glass-section-title">社区讨论</h1>
      <button class="glass-btn" @click="showCreateDialog = true">
        <el-icon class="mr-2"><Edit /></el-icon>
        发布帖子
      </button>
    </div>

    <!-- Search - Glassmorphism -->
    <div class="flex gap-4">
      <el-input
        v-model="query.keyword"
        placeholder="搜索帖子"
        clearable
        size="large"
        @keyup.enter="handleSearch"
        class="max-w-md"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <!-- Post List - Glassmorphism -->
    <div v-if="loading" class="space-y-4">
      <div v-for="i in 3" :key="i" class="glass-post-card h-32" style="animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;" />
    </div>

    <div v-else-if="postList.length === 0" class="text-center py-20">
      <el-icon size="64" class="mb-6" style="color: var(--glass-text-muted);"><ChatDotSquare /></el-icon>
      <div class="text-lg font-medium" style="color: var(--glass-text);">暂无帖子，快来发表第一个吧！</div>
    </div>

    <div v-else class="space-y-4">
      <div
        v-for="post in postList"
        :key="post.id"
        class="glass-post-card"
        @click="goToDetail(post.id)"
      >
        <div class="flex gap-4">
          <!-- Vote - Glass Compact -->
          <div class="glass-vote-compact" @click.stop>
            <button
              class="vote-btn"
              :class="{ 'active-up': post.voteStatus === 1 }"
              @click="handleVote(post, 1)"
            >
              <el-icon><CaretTop /></el-icon>
            </button>
            <span class="vote-count">
              {{ post.voteUp - post.voteDown }}
            </span>
            <button
              class="vote-btn"
              :class="{ 'active-down': post.voteStatus === -1 }"
              @click="handleVote(post, -1)"
            >
              <el-icon><CaretBottom /></el-icon>
            </button>
          </div>

          <!-- Content -->
          <div class="flex-1 min-w-0">
            <h3 class="post-title">{{ post.title }}</h3>
            <p class="post-excerpt">{{ post.contentSummary }}</p>
            
            <div class="post-meta">
              <div class="meta-item">
                <el-avatar :size="20" :src="post.userAvatar" />
                <span>{{ post.username || '匿名用户' }}</span>
              </div>
              <div class="meta-item">
                <el-icon><Clock /></el-icon>
                <span>{{ formatTime(post.createTime) }}</span>
              </div>
              <div class="meta-item">
                <el-icon><View /></el-icon>
                <span>{{ post.viewCount }}</span>
              </div>
              <div class="meta-item">
                <el-icon><ChatLineRound /></el-icon>
                <span>{{ post.commentCount }}</span>
              </div>
              <div v-if="post.filmTitle" class="meta-item" style="background: linear-gradient(135deg, rgba(14, 165, 233, 0.15), rgba(6, 182, 212, 0.12)); color: var(--glass-primary);">
                <el-icon><Film /></el-icon>
                <span>{{ post.filmTitle }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="total > query.pageSize" class="flex justify-center mt-8">
      <el-pagination
        v-model:current-page="query.pageNum"
        :page-size="query.pageSize"
        :total="total"
        layout="prev, pager, next"
        background
        @current-change="fetchPosts"
      />
    </div>

    <!-- Create Dialog - Neo-Brutalism -->
    <el-dialog v-model="showCreateDialog" title="发布帖子" width="600px" class="nb-dialog">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="createForm.title" placeholder="请输入帖子标题" maxlength="200" show-word-limit size="large" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createForm.contentHtml" type="textarea" :rows="8" placeholder="请输入帖子内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="!border-2 !border-black !font-bold" @click="showCreateDialog = false">取消</el-button>
        <el-button class="!bg-pop-green !text-black !border-2 !border-black !font-bold" :loading="creating" @click="handleCreatePost">
          <el-icon class="mr-2"><Promotion /></el-icon>发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
