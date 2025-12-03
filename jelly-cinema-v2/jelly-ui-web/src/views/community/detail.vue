<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPostDetail, getCommentList, createComment, vote, likeComment, unlikeComment, type Post, type Comment } from '@/api/community'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = route.params.id as string  // 保持字符串，避免大数字精度丢失
const loading = ref(true)
const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const commentInput = ref('')
const replyTo = ref<Comment | null>(null)
const submitting = ref(false)

onMounted(async () => {
  await Promise.all([loadPost(), loadComments()])
  loading.value = false
})

async function loadPost() {
  try {
    const res = await getPostDetail(postId)
    post.value = res.data
  } catch (e) {
    ElMessage.error('帖子不存在')
    router.push('/community')
  }
}

async function loadComments() {
  const res = await getCommentList(postId, { pageNum: 1, pageSize: 100 })
  comments.value = res.data?.rows || []
}

async function handleVote(type: number) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  if (!post.value) return
  
  const newType = post.value.voteStatus === type ? 0 : type
  await vote(post.value.id, newType)
  
  if (post.value.voteStatus === 1) post.value.voteUp--
  if (post.value.voteStatus === -1) post.value.voteDown--
  if (newType === 1) post.value.voteUp++
  if (newType === -1) post.value.voteDown++
  post.value.voteStatus = newType
}

async function submitComment() {
  if (!commentInput.value.trim()) return
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  
  submitting.value = true
  try {
    await createComment({
      postId,
      content: commentInput.value.trim(),
      parentId: replyTo.value?.id,
      replyUserId: replyTo.value?.userId
    })
    
    ElMessage.success('评论成功')
    commentInput.value = ''
    replyTo.value = null
    await loadComments()
  } finally {
    submitting.value = false
  }
}

function setReply(comment: Comment) {
  replyTo.value = comment
}

async function toggleLike(comment: Comment) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  
  if (comment.liked) {
    await unlikeComment(comment.id)
    comment.likeCount--
  } else {
    await likeComment(comment.id)
    comment.likeCount++
  }
  comment.liked = !comment.liked
}

function formatTime(time: string): string {
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}
</script>

<template>
  <div class="max-w-4xl mx-auto">
    <!-- 返回 -->
    <div class="mb-6">
      <el-button text @click="router.push('/community')">
        <el-icon class="mr-1"><ArrowLeft /></el-icon>
        返回社区
      </el-button>
    </div>
    
    <!-- Loading -->
    <div v-if="loading" class="space-y-4">
      <div class="bg-dark-card rounded-xl p-6 skeleton h-64" />
    </div>
    
    <template v-else-if="post">
      <!-- 帖子内容 -->
      <div class="bg-dark-card rounded-xl p-6 mb-6">
        <div class="flex gap-6">
          <!-- 投票 -->
          <div class="flex flex-col items-center gap-1">
            <el-button
              circle
              :type="post.voteStatus === 1 ? 'primary' : 'default'"
              @click="handleVote(1)"
            >
              <el-icon><CaretTop /></el-icon>
            </el-button>
            <span class="text-xl font-bold" :class="post.voteUp - post.voteDown > 0 ? 'text-primary' : 'text-gray-400'">
              {{ post.voteUp - post.voteDown }}
            </span>
            <el-button
              circle
              :type="post.voteStatus === -1 ? 'danger' : 'default'"
              @click="handleVote(-1)"
            >
              <el-icon><CaretBottom /></el-icon>
            </el-button>
          </div>
          
          <!-- 内容 -->
          <div class="flex-1">
            <h1 class="text-2xl font-bold text-white mb-4">{{ post.title }}</h1>
            
            <div class="flex items-center gap-4 text-sm text-gray-400 mb-6">
              <div class="flex items-center gap-2">
                <el-avatar :size="24" :src="post.userAvatar" />
                <span>{{ post.username || '匿名用户' }}</span>
              </div>
              <span>{{ formatTime(post.createTime) }}</span>
              <span>
                <el-icon class="mr-1"><View /></el-icon>
                {{ post.viewCount }} 阅读
              </span>
            </div>
            
            <div class="prose prose-invert max-w-none text-gray-200" v-html="post.contentHtml || post.contentSummary" />
            
            <div v-if="post.filmTitle" class="mt-6 p-4 bg-dark-bg rounded-lg">
              <span class="text-gray-400">相关电影：</span>
              <router-link :to="`/film/${post.filmId}`" class="text-primary hover:underline">
                {{ post.filmTitle }}
              </router-link>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 评论区 -->
      <div class="bg-dark-card rounded-xl p-6">
        <h2 class="text-lg font-bold text-white mb-6">
          评论 ({{ post.commentCount }})
        </h2>
        
        <!-- 评论输入 -->
        <div class="mb-6">
          <div v-if="replyTo" class="mb-2 text-sm text-gray-400">
            回复 @{{ replyTo.username }}
            <el-button text size="small" @click="replyTo = null">取消</el-button>
          </div>
          <div class="flex gap-2">
            <el-input
              v-model="commentInput"
              type="textarea"
              :rows="3"
              :placeholder="replyTo ? `回复 @${replyTo.username}...` : '写下你的评论...'"
            />
          </div>
          <div class="flex justify-end mt-2">
            <el-button type="primary" :loading="submitting" @click="submitComment">
              发表评论
            </el-button>
          </div>
        </div>
        
        <!-- 评论列表 -->
        <div v-if="comments.length === 0" class="text-center py-8">
          <el-empty description="暂无评论，快来抢沙发！" :image-size="80" />
        </div>
        
        <div v-else class="space-y-6">
          <div v-for="comment in comments" :key="comment.id" class="border-b border-dark-border pb-4 last:border-0">
            <div class="flex gap-3">
              <el-avatar :size="40" :src="comment.userAvatar">{{ comment.username?.[0] }}</el-avatar>
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-1">
                  <span class="font-medium text-white">{{ comment.username }}</span>
                  <span class="text-xs text-gray-500">{{ formatTime(comment.createTime) }}</span>
                </div>
                
                <p class="text-gray-300 mb-2">
                  <span v-if="comment.replyUsername" class="text-primary">@{{ comment.replyUsername }} </span>
                  {{ comment.content }}
                </p>
                
                <div class="flex items-center gap-4 text-sm text-gray-500">
                  <button class="hover:text-primary flex items-center gap-1" @click="toggleLike(comment)">
                    <el-icon :class="{ 'text-primary': comment.liked }"><Star /></el-icon>
                    {{ comment.likeCount }}
                  </button>
                  <button class="hover:text-primary" @click="setReply(comment)">
                    <el-icon class="mr-1"><ChatRound /></el-icon>
                    回复
                  </button>
                </div>
                
                <!-- 子评论 -->
                <div v-if="comment.children?.length" class="mt-4 pl-4 border-l-2 border-dark-border space-y-4">
                  <div v-for="child in comment.children" :key="child.id" class="flex gap-3">
                    <el-avatar :size="32" :src="child.userAvatar">{{ child.username?.[0] }}</el-avatar>
                    <div>
                      <div class="flex items-center gap-2 mb-1">
                        <span class="font-medium text-white text-sm">{{ child.username }}</span>
                        <span class="text-xs text-gray-500">{{ formatTime(child.createTime) }}</span>
                      </div>
                      <p class="text-gray-300 text-sm">
                        <span v-if="child.replyUsername" class="text-primary">@{{ child.replyUsername }} </span>
                        {{ child.content }}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
