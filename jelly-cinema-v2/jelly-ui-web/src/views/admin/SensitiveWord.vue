<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getSensitiveWordList, addSensitiveWord, batchImportSensitiveWords, deleteSensitiveWord, updateSensitiveWordStatus, refreshSensitiveWordCache, type SensitiveWord } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref<SensitiveWord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const typeFilter = ref<number | undefined>()

// 添加弹窗
const addDialogVisible = ref(false)
const addForm = ref({ word: '', type: 5, strategy: 1 })

// 批量导入弹窗
const importDialogVisible = ref(false)
const importForm = ref({ words: '', type: 5, strategy: 1 })

const typeOptions = [
  { value: 1, label: '政治', color: 'danger' },
  { value: 2, label: '色情', color: 'warning' },
  { value: 3, label: '暴恐', color: 'danger' },
  { value: 4, label: '广告', color: 'info' },
  { value: 5, label: '其他', color: '' }
]

const strategyOptions = [
  { value: 1, label: '替换为***' },
  { value: 2, label: '直接拦截' }
]

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getSensitiveWordList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      type: typeFilter.value
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

async function handleAdd() {
  if (!addForm.value.word.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  
  await addSensitiveWord(addForm.value)
  ElMessage.success('添加成功')
  addDialogVisible.value = false
  addForm.value = { word: '', type: 5, strategy: 1 }
  loadData()
}

async function handleImport() {
  if (!importForm.value.words.trim()) {
    ElMessage.warning('请输入敏感词')
    return
  }
  
  const res = await batchImportSensitiveWords(importForm.value)
  ElMessage.success(`成功导入 ${res.data} 个敏感词`)
  importDialogVisible.value = false
  importForm.value = { words: '', type: 5, strategy: 1 }
  loadData()
}

async function handleDelete(row: SensitiveWord) {
  await ElMessageBox.confirm(`确定要删除敏感词"${row.word}"吗？`, '提示')
  await deleteSensitiveWord(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleStatusChange(row: SensitiveWord) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateSensitiveWordStatus({ id: row.id, status: newStatus })
  row.status = newStatus
  ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
}

async function handleRefreshCache() {
  await refreshSensitiveWordCache()
  ElMessage.success('缓存已刷新')
}

function getTypeLabel(type: number) {
  return typeOptions.find(t => t.value === type)?.label || '未知'
}

function getTypeColor(type: number) {
  return typeOptions.find(t => t.value === type)?.color || ''
}

function getStrategyLabel(strategy: number) {
  return strategyOptions.find(s => s.value === strategy)?.label || '未知'
}
</script>

<template>
  <div class="h-full flex flex-col gap-6 p-6 bg-gray-50">
    <!-- 顶部标题卡片 -->
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex flex-col md:flex-row justify-between items-center gap-4 animate-fade-in-down">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-purple-50 flex items-center justify-center text-purple-600">
          <el-icon size="24"><svg-icon name="icon-bianji" /></el-icon>
        </div>
        <div>
          <h2 class="text-2xl font-bold text-gray-900 tracking-wide">敏感词库</h2>
          <p class="text-gray-500 text-sm mt-1">管理系统违禁词汇及过滤策略</p>
        </div>
      </div>
      
      <div class="flex gap-3">
        <el-button type="warning" plain class="!rounded-xl" @click="handleRefreshCache">
             <el-icon class="mr-1"><Refresh /></el-icon>刷新缓存
        </el-button>
        <el-button plain class="!rounded-xl" @click="importDialogVisible = true">
           <el-icon class="mr-1"><svg-icon name="icon-shangchuantupian" /></el-icon>批量导入
        </el-button>
        <el-button type="primary" class="!rounded-xl !font-bold" @click="addDialogVisible = true">
          <el-icon class="mr-1"><Plus /></el-icon>添加敏感词
        </el-button>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="bg-white flex-1 rounded-2xl shadow-sm border border-gray-100 flex flex-col overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
       <!-- 搜索栏 -->
      <div class="p-5 border-b border-gray-100 flex gap-4 bg-gray-50/50 justify-between">
         <div class="flex gap-4">
           <el-input v-model="keyword" placeholder="搜索敏感词" clearable class="w-64" @keyup.enter="handleSearch">
             <template #prefix><el-icon><Search /></el-icon></template>
           </el-input>
           <el-select v-model="typeFilter" placeholder="类型" clearable class="w-32" @change="handleSearch">
             <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
           </el-select>
           <el-button circle @click="handleSearch">
             <el-icon><Search /></el-icon>
           </el-button>
         </div>
      </div>

      <div class="flex-1 overflow-hidden p-4">
        <el-table 
          :data="tableData" 
          v-loading="loading" 
          height="100%"
          style="width: 100%"
          :row-style="{ height: '60px' }"
        >
          <el-table-column prop="id" label="ID" width="80" align="center" />
          
          <el-table-column prop="word" label="敏感词" min-width="200">
             <template #default="{ row }">
               <span class="font-medium text-gray-800">{{ row.word }}</span>
             </template>
          </el-table-column>
          
          <el-table-column label="类型" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="getTypeColor(row.type)" effect="light">{{ getTypeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="策略" width="140" align="center">
            <template #default="{ row }">
               <el-tag :type="row.strategy === 2 ? 'danger' : 'info'" effect="plain" round>
                 {{ getStrategyLabel(row.strategy) }}
               </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-switch :model-value="row.status === 1" @change="handleStatusChange(row)" />
            </template>
          </el-table-column>
          
          <el-table-column label="创建时间" width="180" align="center">
            <template #default="{ row }">
              <span class="text-gray-500 font-mono text-xs">{{ new Date(row.createTime).toLocaleString() }}</span>
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="p-4 border-t border-gray-100 flex justify-end">
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

    <!-- 添加敏感词弹窗 -->
    <el-dialog v-model="addDialogVisible" title="添加敏感词" width="450px" class="rounded-xl">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input v-model="addForm.word" placeholder="请输入敏感词内容" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="addForm.type" class="w-full">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="过滤策略">
          <el-radio-group v-model="addForm.strategy">
            <el-radio v-for="s in strategyOptions" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确定添加</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="批量导入敏感词" width="550px">
      <div class="mb-4 bg-blue-50 text-blue-600 p-3 rounded text-sm flex items-center">
         <el-icon class="mr-2"><InfoFilled /></el-icon>
         支持批量粘贴，多个敏感词请用逗号或换行分隔
      </div>
      <el-form :model="importForm" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input
            v-model="importForm.words"
            type="textarea"
            :rows="8"
            placeholder="例如：
词汇1
词汇2,词汇3"
          />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
             <el-form-item label="类型">
                <el-select v-model="importForm.type" class="w-full">
                  <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
             </el-form-item>
          </el-col>
          <el-col :span="12">
             <el-form-item label="策略">
                <el-select v-model="importForm.strategy" class="w-full">
                   <el-option v-for="s in strategyOptions" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
             </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
