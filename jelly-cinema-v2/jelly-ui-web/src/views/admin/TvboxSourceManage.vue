<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get, post, put, del } from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

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
  <div class="h-full flex flex-col gap-6 p-6 bg-gray-50">
    <!-- 顶部标题卡片 -->
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex flex-col md:flex-row justify-between items-center gap-4 animate-fade-in-down">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-cyan-50 flex items-center justify-center text-cyan-600">
          <el-icon size="24"><svg-icon name="icon-api" /></el-icon>
        </div>
        <div>
          <h2 class="text-2xl font-bold text-gray-900 tracking-wide">采集源配置</h2>
          <p class="text-gray-500 text-sm mt-1">配置 TVBox 第三方资源接口</p>
        </div>
      </div>
      
      <div class="flex gap-3">
        <el-button @click="loadData" plain class="!rounded-xl">
           <el-icon class="mr-1"><Refresh /></el-icon>刷新状态
        </el-button>
        <el-button type="primary" class="!rounded-xl !font-bold" @click="handleAdd">
          <el-icon class="mr-1"><Plus /></el-icon>添加采集源
        </el-button>
      </div>
    </div>

    <!-- 数据表格区域 -->
    <div class="bg-white flex-1 rounded-2xl shadow-sm border border-gray-100 overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
      <el-table 
        :data="tableData" 
        v-loading="loading" 
        height="100%"
        style="width: 100%"
        :row-style="{ height: '64px' }"
      >
        <el-table-column prop="priority" label="优先级" width="80" sortable align="center">
          <template #default="{ row }">
            <span class="font-mono font-bold bg-gray-100 text-gray-600 px-2 py-1 rounded">{{ row.priority }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="sourceName" label="源名称" width="160">
          <template #default="{ row }">
            <span class="font-bold text-gray-800">{{ row.sourceName }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="apiUrl" label="API地址" min-width="300">
          <template #default="{ row }">
            <div class="flex items-center gap-2 cursor-pointer text-gray-500 hover:text-blue-600 transition-colors" @click="ElMessage.info(row.apiUrl)">
               <el-tag size="small" type="info" effect="plain">{{ row.apiType.toUpperCase() }}</el-tag>
               <span class="truncate">{{ row.apiUrl }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch 
              :model-value="row.enabled === 1"
              @change="toggleEnabled(row)"
              inline-prompt
              active-text="开"
              inactive-text="关"
            />
          </template>
        </el-table-column>

        <el-table-column label="采集状态" width="120" align="center">
          <template #default="{ row }">
            <el-popover v-if="row.fetchStatus === 1" placement="top" :width="200" trigger="hover" :content="row.errorMsg || '未知错误'">
               <template #reference>
                  <el-tag type="danger" effect="plain" class="cursor-help">采集失败 <el-icon><svg-icon name="icon-bangzhuwendang" /></el-icon></el-tag>
               </template>
            </el-popover>
            <el-tag v-else :type="getFetchStatusLabel(row.fetchStatus).type as any" effect="light">
              {{ getFetchStatusLabel(row.fetchStatus).label }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="filmCount" label="资源量" width="100" align="center">
          <template #default="{ row }">
            <span class="text-blue-600 font-bold">{{ row.filmCount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="其它信息" width="180">
          <template #default="{ row }">
             <div class="flex flex-col text-xs text-gray-400">
                <span>间隔: {{ row.fetchInterval }}分钟</span>
                <span>上次: {{ formatDateTime(row.lastFetchTime).split(' ')[0] }}</span>
             </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-2">
               <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
               <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="formData" label-width="100px">
        <el-form-item label="源名称" required>
          <el-input v-model="formData.sourceName" placeholder="给采集源起个名字" />
        </el-form-item>
        <el-form-item label="API地址" required>
          <el-input v-model="formData.apiUrl" placeholder="https://example.com/api.json" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="API类型">
              <el-select v-model="formData.apiType" style="width: 100%">
                <el-option label="JSON 接口" value="json" />
                <el-option label="XML 接口" value="xml" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="formData.priority" :min="1" :max="999" style="width: 100%" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="采集间隔">
           <div class="flex items-center gap-2">
              <el-input-number v-model="formData.fetchInterval" :min="10" :max="1440" controls-position="right" />
              <span class="text-gray-500">分钟</span>
           </div>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存配置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
