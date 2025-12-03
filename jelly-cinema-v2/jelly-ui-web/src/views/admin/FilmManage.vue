<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get, post, put, del } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

interface Film {
  id: number
  title: string
  poster: string
  coverUrl?: string
  videoUrl?: string  // 视频链接
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
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
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
  formData.value = { status: 1, year: new Date().getFullYear() }
  dialogVisible.value = true
}

function handleEdit(row: Film) {
  dialogTitle.value = '编辑影片'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!formData.value.title) {
    ElMessage.warning('请输入影片名称')
    return
  }
  
  try {
    if (formData.value.id) {
      await put(`/admin/film/${formData.value.id}`, formData.value)
      ElMessage.success('保存成功')
    } else {
      await post('/admin/film', formData.value)
      ElMessage.success('添加成功')
    }
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
  const newStatus = row.status === 1 ? 0 : 1
  await put(`/admin/film/${row.id}/status`, { status: newStatus })
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已上架' : '已下架')
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
            <img :src="row.poster" :alt="row.title" class="w-12 h-16 object-cover rounded" />
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
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="toggleStatus(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
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
        <el-form-item label="海报URL">
          <el-input v-model="formData.poster" placeholder="海报图片地址" />
        </el-form-item>
        <el-form-item label="视频链接">
          <el-input v-model="formData.videoUrl" placeholder="视频播放地址（支持 mp4、m3u8 等格式）" />
          <div class="text-xs text-gray-400 mt-1">支持直链或 HLS 流媒体地址</div>
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
