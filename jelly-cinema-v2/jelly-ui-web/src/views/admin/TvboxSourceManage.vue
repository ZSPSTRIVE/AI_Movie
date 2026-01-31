<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get, post, put, del } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh } from '@element-plus/icons-vue'

interface TvboxSource {
  id: number
  sourceName: string
  apiUrl: string
  apiType: string
  enabled: number
  priority: number
  fetchInterval: number
  lastFetchTime: string | null
  fetchStatus: number
  filmCount: number
  errorMsg: string | null
  remark: string | null
  createTime: string
}

const loading = ref(false)
const tableData = ref<TvboxSource[]>([])

// 编辑弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('添加采集源')
const formData = ref<Partial<TvboxSource>>({})

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await get('/admin/tvbox-source/list')
    tableData.value = res.data || []
  } catch (error: any) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  dialogTitle.value = '添加采集源'
  formData.value = {
    apiType: 'json',
    enabled: 1,
    priority: 100,
    fetchInterval: 60
  }
  dialogVisible.value = true
}

function handleEdit(row: TvboxSource) {
  dialogTitle.value = '编辑采集源'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!formData.value.sourceName) {
    ElMessage.warning('请输入源名称')
    return
  }
  if (!formData.value.apiUrl) {
    ElMessage.warning('请输入API地址')
    return
  }

  try {
    if (formData.value.id) {
      await put(`/admin/tvbox-source/${formData.value.id}`, formData.value)
    } else {
      await post('/admin/tvbox-source', formData.value)
    }
    ElMessage.success(formData.value.id ? '保存成功' : '添加成功')
    dialogVisible.value = false
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handleDelete(row: TvboxSource) {
  await ElMessageBox.confirm(`确定要删除采集源"${row.sourceName}"吗？`, '提示', { type: 'warning' })
  try {
    await del(`/admin/tvbox-source/${row.id}`)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

async function toggleEnabled(row: TvboxSource) {
  try {
    await put(`/admin/tvbox-source/${row.id}/toggle`)
    row.enabled = row.enabled === 1 ? 0 : 1
    ElMessage.success(row.enabled === 1 ? '已启用' : '已禁用')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

function getFetchStatusLabel(status: number) {
  const map: Record<number, { label: string; type: string }> = {
    0: { label: '正常', type: 'success' },
    1: { label: '失败', type: 'danger' },
    2: { label: '采集中', type: 'warning' }
  }
  return map[status] || { label: '未知', type: 'info' }
}

function formatDateTime(dt: string | null) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="p-6">
    <!-- 标题和操作按钮 -->
    <div class="flex justify-between items-center mb-6">
      <h2 class="text-xl font-bold">采集源配置</h2>
      <div class="flex gap-3">
        <el-button @click="loadData">
          <el-icon class="mr-1"><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon class="mr-1"><Plus /></el-icon>
          添加采集源
        </el-button>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" size="small" stripe>
      <el-table-column prop="priority" label="优先级" width="80" sortable>
        <template #default="{ row }">
          <span class="font-mono font-bold">{{ row.priority }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="sourceName" label="源名称" width="120">
        <template #default="{ row }">
          <span class="font-medium">{{ row.sourceName }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="apiUrl" label="API地址" min-width="300">
        <template #default="{ row }">
          <el-tooltip :content="row.apiUrl" placement="top">
            <span class="text-xs text-gray-500 truncate block">{{ row.apiUrl }}</span>
          </el-tooltip>
        </template>
      </el-table-column>

      <el-table-column prop="apiType" label="类型" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.apiType.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch 
            :model-value="row.enabled === 1"
            @change="toggleEnabled(row)"
            size="small"
          />
        </template>
      </el-table-column>

      <el-table-column label="采集状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getFetchStatusLabel(row.fetchStatus).type as any" size="small">
            {{ getFetchStatusLabel(row.fetchStatus).label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="filmCount" label="电影数" width="80">
        <template #default="{ row }">
          <span class="text-blue-500 font-medium">{{ row.filmCount }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="fetchInterval" label="间隔(分)" width="90" />

      <el-table-column label="上次采集" width="160">
        <template #default="{ row }">
          <span class="text-xs text-gray-400">{{ formatDateTime(row.lastFetchTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">
            <el-icon><Edit /></el-icon>
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="源名称" required>
          <el-input v-model="formData.sourceName" placeholder="如：量子资源" />
        </el-form-item>
        <el-form-item label="API地址" required>
          <el-input v-model="formData.apiUrl" placeholder="采集API地址" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="API类型">
              <el-select v-model="formData.apiType" style="width: 100%">
                <el-option label="JSON" value="json" />
                <el-option label="XML" value="xml" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="formData.priority" :min="1" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="采集间隔">
          <el-input-number v-model="formData.fetchInterval" :min="10" :max="1440" style="width: 150px" />
          <span class="ml-2 text-gray-400">分钟</span>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="可选" />
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
.p-6 {
  padding: 1.5rem;
}
</style>
