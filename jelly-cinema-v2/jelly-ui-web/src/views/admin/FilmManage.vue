<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get, post, put, del } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Loading, VideoPlay, Search } from '@element-plus/icons-vue'

interface Film {
  id: number
  title: string
  coverUrl: string
  videoUrl?: string
  rating: number
  year: number
  region: string
  director: string
  actors: string
  description: string
  playCount: number
  status: number
  createTime: string
}

const loading = ref(false)
const tableData = ref<Film[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')

// 编辑弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('添加影片')
const formData = ref<Partial<Film>>({})

// 视频来源类型
const videoSourceType = ref<'link' | 'upload'>('link')
const uploadLoading = ref(false)

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await get('/admin/film/list', {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    })
    tableData.value = res.data?.rows || []
    total.value = Number(res.data?.total) || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadData()
}

function handleAdd() {
  dialogTitle.value = '添加影片'
  formData.value = { status: 0, year: new Date().getFullYear(), rating: 0 }
  videoSourceType.value = 'link'
  dialogVisible.value = true
}

function handleEdit(row: Film) {
  dialogTitle.value = '编辑影片'
  formData.value = { ...row }
  // 根据视频URL判断来源类型
  videoSourceType.value = row.videoUrl?.startsWith('/uploads/') ? 'upload' : 'link'
  dialogVisible.value = true
}

// 视频上传成功回调
function handleVideoUploadSuccess(response: any) {
  if (response.code === 200 && response.data) {
    formData.value.videoUrl = response.data
    ElMessage.success('视频上传成功')
  } else {
    ElMessage.error(response.msg || '上传失败')
  }
  uploadLoading.value = false
}

function handleVideoUploadError() {
  ElMessage.error('视频上传失败')
  uploadLoading.value = false
}

function beforeVideoUpload(file: File) {
  const isVideo = file.type.startsWith('video/')
  const isLt500M = file.size / 1024 / 1024 < 500
  
  if (!isVideo) {
    ElMessage.error('请上传视频文件')
    return false
  }
  if (!isLt500M) {
    ElMessage.error('视频大小不能超过 500MB')
    return false
  }
  uploadLoading.value = true
  return true
}

// 封面图上传成功回调
function handleCoverUploadSuccess(response: any) {
  if (response.code === 200 && response.data) {
    formData.value.coverUrl = response.data
    ElMessage.success('封面图上传成功')
  } else {
    ElMessage.error(response.msg || '上传失败')
  }
}

function beforeCoverUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  
  if (!isImage) {
    ElMessage.error('请上传图片文件')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

async function handleSave() {
  if (!formData.value.title) {
    ElMessage.warning('请输入影片名称')
    return
  }
  
  try {
    await post('/admin/film/save', formData.value)
    ElMessage.success(formData.value.id ? '保存成功' : '添加成功')
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handleDelete(row: Film) {
  await ElMessageBox.confirm('确定要删除该影片吗？', '提示', { type: 'warning' })
  await del(`/admin/film/${row.id}`)
  ElMessage.success('删除成功')
  loadData()
}

async function toggleStatus(row: Film) {
  const newStatus = row.status === 0 ? 1 : 0
  await post(`/admin/film/status/${row.id}?status=${newStatus}`)
  row.status = newStatus
  ElMessage.success(newStatus === 0 ? '已上架' : '已下架')
}
</script>

<template>
  <div class="h-full flex flex-col gap-6">
    <!-- 顶部统计/操作卡片 -->
    <div class="glass-card p-6 rounded-2xl flex flex-col md:flex-row justify-between items-center gap-4 animate-fade-in-down">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-green-500 to-emerald-600 flex items-center justify-center shadow-lg shadow-green-500/30">
          <el-icon size="24" color="white"><VideoCameraFilled /></el-icon>
        </div>
        <div>
          <h2 class="text-2xl font-bold text-white tracking-wide">影片管理</h2>
          <p class="text-gray-400 text-sm mt-1">管理系统所有影视资源及元数据</p>
        </div>
      </div>
      
      <el-button 
        type="primary" 
        size="large"
        class="!rounded-xl !px-6 !font-bold shadow-lg shadow-green-500/20 hover:shadow-green-500/40 transition-all"
        @click="handleAdd"
      >
        <el-icon class="mr-2"><Plus /></el-icon>
        添加影片
      </el-button>
    </div>

    <!-- 数据区域 -->
    <div class="glass-card flex-1 rounded-2xl flex flex-col overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
       <!-- 搜索栏 -->
      <div class="p-5 border-b border-white/5 flex gap-4 bg-white/5 backdrop-blur-sm justify-between">
        <div class="flex gap-4">
           <el-input 
             v-model="keyword" 
             placeholder="搜索影片名称" 
             clearable 
             class="glass-input w-72"
             @keyup.enter="handleSearch"
           >
             <template #prefix><el-icon><Search /></el-icon></template>
           </el-input>
           <el-button class="glass-button-icon" @click="handleSearch">
             <el-icon><Search /></el-icon>
           </el-button>
        </div>
      </div>

      <!-- 影片表格 -->
      <div class="flex-1 overflow-hidden p-4">
        <el-table 
          :data="tableData" 
          v-loading="loading" 
          height="100%"
          style="width: 100%"
          :row-style="{ height: '88px' }"
        >
          <el-table-column prop="id" label="ID" width="70" align="center" />
          
          <el-table-column label="影片信息" min-width="280">
            <template #default="{ row }">
              <div class="flex items-center gap-4 py-1 group cursor-pointer hover:translate-x-1 transition-transform duration-300">
                <div class="relative w-12 h-16 rounded overflow-hidden shadow border border-white/10 group-hover:shadow-green-500/20 transition-all">
                  <el-image 
                    :src="row.coverUrl" 
                    class="w-full h-full object-cover"
                    loading="lazy"
                  />
                  <div class="absolute inset-0 bg-black/20 group-hover:bg-transparent transition-colors"></div>
                </div>
                
                <div class="flex-1 min-w-0 flex flex-col gap-1">
                  <div class="text-base font-bold text-gray-100 truncate group-hover:text-green-400 transition-colors">{{ row.title }}</div>
                  <div class="flex items-center gap-2 text-xs text-gray-400">
                    <span class="bg-white/5 px-1.5 py-0.5 rounded border border-white/5">{{ row.year }}</span>
                    <span>{{ row.region }}</span>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="director" label="导演" width="120" show-overflow-tooltip>
             <template #default="{ row }">
                <span class="text-gray-300">{{ row.director || '-' }}</span>
             </template>
          </el-table-column>

          <el-table-column prop="rating" label="评分" width="80" align="center">
            <template #default="{ row }">
              <span class="text-amber-400 font-bold text-lg">{{ row.rating }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="playCount" label="播放量" width="100" align="center">
             <template #default="{ row }">
                <span class="font-mono text-gray-400">{{ row.playCount }}</span>
             </template>
          </el-table-column>

          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag 
                effect="dark"
                :class="row.status === 0 ? '!bg-green-500/20 !border-green-500/30 !text-green-400' : '!bg-gray-500/20 !border-gray-500/30 !text-gray-400'"
              >
                {{ row.status === 0 ? '上架' : '下架' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <div class="flex items-center justify-center gap-2">
                <el-button size="small" link class="!text-amber-400 hover:!text-amber-300" @click="handleEdit(row)">编辑</el-button>
                <el-button 
                  size="small" 
                  link 
                  :class="row.status === 0 ? '!text-gray-400 hover:!text-white' : '!text-green-400 hover:!text-green-300'" 
                  @click="toggleStatus(row)"
                >
                  {{ row.status === 0 ? '下架' : '上架' }}
                </el-button>
                <el-popconfirm title="确定要删除该影片吗？" @confirm="handleDelete(row)">
                  <template #reference>
                    <el-button size="small" link class="!text-red-400 hover:!text-red-300">删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="p-4 border-t border-white/5 bg-white/5 flex justify-end">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="loadData"
          background
        />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="glass-dialog">
      <el-form :model="formData" label-width="80px">
        <el-form-item label="影片名称" required>
          <el-input v-model="formData.title" placeholder="请输入影片名称" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="上映年份">
              <el-input-number v-model="formData.year" :min="1900" :max="2030" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="评分">
              <el-input-number v-model="formData.rating" :min="0" :max="10" :step="0.1" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地区">
          <el-input v-model="formData.region" placeholder="如：中国、美国" />
        </el-form-item>
        <el-form-item label="导演">
          <el-input v-model="formData.director" placeholder="导演姓名" />
        </el-form-item>
        <el-form-item label="演员">
          <el-input v-model="formData.actors" placeholder="主要演员，用逗号分隔" />
        </el-form-item>
        <el-form-item label="封面图">
          <div class="flex gap-3 items-start w-full">
            <el-upload
              class="cover-uploader"
              action="/api/admin/upload/image"
              :show-file-list="false"
              :on-success="handleCoverUploadSuccess"
              :before-upload="beforeCoverUpload"
              accept="image/*"
            >
              <img v-if="formData.coverUrl" :src="formData.coverUrl" class="w-20 h-28 object-cover rounded border border-white/10" />
              <div v-else class="w-20 h-28 border-2 border-dashed border-white/20 rounded-lg flex items-center justify-center text-gray-500 hover:border-green-500/50 hover:text-green-500 cursor-pointer transition-all bg-white/5">
                <el-icon size="24"><Plus /></el-icon>
              </div>
            </el-upload>
            <div class="flex-1">
              <el-input v-model="formData.coverUrl" placeholder="或输入封面图片URL" size="small" />
              <div class="text-xs text-gray-500 mt-1">点击左侧上传或输入图片地址</div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="视频来源">
          <el-radio-group v-model="videoSourceType" class="mb-3">
            <el-radio-button value="link">视频链接</el-radio-button>
            <el-radio-button value="upload">上传视频</el-radio-button>
          </el-radio-group>
          
          <!-- 视频链接输入 -->
          <template v-if="videoSourceType === 'link'">
            <el-input v-model="formData.videoUrl" placeholder="输入视频播放地址" />
            <div class="text-xs text-gray-500 mt-1">支持：mp4直链、m3u8流媒体、哔哩哔哩链接、网页嵌入链接等</div>
          </template>
          
          <!-- 视频上传 -->
          <template v-else>
            <el-upload
              class="video-uploader w-full"
              action="/api/admin/upload/video"
              :show-file-list="false"
              :on-success="handleVideoUploadSuccess"
              :on-error="handleVideoUploadError"
              :before-upload="beforeVideoUpload"
              accept="video/*"
              :disabled="uploadLoading"
            >
              <div class="upload-area" :class="{ 'has-video': formData.videoUrl }">
                <template v-if="uploadLoading">
                  <el-icon class="is-loading" size="32"><Loading /></el-icon>
                  <span class="mt-2 text-sm">上传中...</span>
                </template>
                <template v-else-if="formData.videoUrl && videoSourceType === 'upload'">
                  <el-icon size="32" class="text-green-500"><VideoPlay /></el-icon>
                  <span class="mt-2 text-sm text-green-500">视频已上传</span>
                  <span class="text-xs text-gray-500 mt-1">点击重新上传</span>
                </template>
                <template v-else>
                  <el-icon size="32"><Upload /></el-icon>
                  <span class="mt-2 text-sm">点击上传视频</span>
                  <span class="text-xs text-gray-500 mt-1">支持 mp4、webm 等格式，最大 500MB</span>
                </template>
              </div>
            </el-upload>
          </template>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="formData.description" type="textarea" :rows="4" placeholder="影片简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.upload-area {
  width: 100%;
  min-height: 120px;
  border: 2px dashed rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.02);
}

.upload-area:hover {
  border-color: #10b981;
  color: #10b981;
  background: rgba(16, 185, 129, 0.05);
}

.upload-area.has-video {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.1);
}

.cover-uploader :deep(.el-upload) {
  border-radius: 8px;
  overflow: hidden;
}
</style>
