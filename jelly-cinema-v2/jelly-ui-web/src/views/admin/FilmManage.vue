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
  <div class="p-6">
    <!-- 工具栏 -->
    <div class="flex justify-between mb-4">
      <div class="flex gap-4">
        <el-input v-model="keyword" placeholder="搜索影片名称" clearable style="width: 250px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <el-button type="primary" @click="handleAdd">
        <el-icon class="mr-1"><Plus /></el-icon>添加影片
      </el-button>
    </div>

    <!-- 影片表格 -->
    <el-table :data="tableData" v-loading="loading" size="small" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="影片" min-width="250">
        <template #default="{ row }">
          <div class="flex items-center gap-3">
            <img :src="row.coverUrl" :alt="row.title" class="w-12 h-16 object-cover rounded" />
            <div>
              <div class="font-medium">{{ row.title }}</div>
              <div class="text-xs text-gray-400">{{ row.year }} · {{ row.region }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="director" label="导演" width="100" />
      <el-table-column prop="rating" label="评分" width="80">
        <template #default="{ row }">
          <span class="text-orange-400">{{ row.rating }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="playCount" label="播放量" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">
            {{ row.status === 0 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 0 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
            {{ row.status === 0 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="flex justify-end mt-4">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
      />
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="formData" label-width="80px">
        <el-form-item label="影片名称" required>
          <el-input v-model="formData.title" placeholder="请输入影片名称" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="上映年份">
              <el-input-number v-model="formData.year" :min="1900" :max="2030" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="评分">
              <el-input-number v-model="formData.rating" :min="0" :max="10" :step="0.1" />
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
          <div class="flex gap-3 items-start">
            <el-upload
              class="cover-uploader"
              action="/api/admin/upload/image"
              :show-file-list="false"
              :on-success="handleCoverUploadSuccess"
              :before-upload="beforeCoverUpload"
              accept="image/*"
            >
              <img v-if="formData.coverUrl" :src="formData.coverUrl" class="w-20 h-28 object-cover rounded border" />
              <div v-else class="w-20 h-28 border-2 border-dashed border-gray-300 rounded flex items-center justify-center text-gray-400 hover:border-blue-400 cursor-pointer">
                <el-icon size="24"><Plus /></el-icon>
              </div>
            </el-upload>
            <div class="flex-1">
              <el-input v-model="formData.coverUrl" placeholder="或输入封面图片URL" size="small" />
              <div class="text-xs text-gray-400 mt-1">点击左侧上传或输入图片地址</div>
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
            <div class="text-xs text-gray-400 mt-1">支持：mp4直链、m3u8流媒体、哔哩哔哩链接、网页嵌入链接等</div>
          </template>
          
          <!-- 视频上传 -->
          <template v-else>
            <el-upload
              class="video-uploader"
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
                  <span class="mt-2 text-sm text-green-600">视频已上传</span>
                  <span class="text-xs text-gray-400 mt-1">点击重新上传</span>
                </template>
                <template v-else>
                  <el-icon size="32"><Upload /></el-icon>
                  <span class="mt-2 text-sm">点击上传视频</span>
                  <span class="text-xs text-gray-400 mt-1">支持 mp4、webm 等格式，最大 500MB</span>
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
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  color: #999;
}

.upload-area:hover {
  border-color: #409eff;
  color: #409eff;
}

.upload-area.has-video {
  border-color: #67c23a;
  background: #f0f9eb;
}

.cover-uploader :deep(.el-upload) {
  border-radius: 6px;
  overflow: hidden;
}
</style>
