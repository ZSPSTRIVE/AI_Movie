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
  <div class="p-6">
    <!-- 工具栏 -->
    <div class="flex justify-between mb-4">
      <div class="flex gap-4">
        <el-input v-model="keyword" placeholder="搜索敏感词" clearable style="width: 200px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="typeFilter" placeholder="类型" clearable style="width: 120px" @change="handleSearch">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <div class="flex gap-2">
        <el-button type="primary" @click="addDialogVisible = true">
          <el-icon class="mr-1"><Plus /></el-icon>添加
        </el-button>
        <el-button @click="importDialogVisible = true">
          <el-icon class="mr-1"><Upload /></el-icon>批量导入
        </el-button>
        <el-button @click="handleRefreshCache">
          <el-icon class="mr-1"><Refresh /></el-icon>刷新缓存
        </el-button>
      </div>
    </div>

    <!-- 敏感词表格 -->
    <el-table :data="tableData" v-loading="loading" size="small" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="word" label="敏感词" min-width="150" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="getTypeColor(row.type)" size="small">{{ getTypeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="策略" width="120">
        <template #default="{ row }">{{ getStrategyLabel(row.strategy) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="handleStatusChange(row)" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ new Date(row.createTime).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
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

    <!-- 添加敏感词弹窗 -->
    <el-dialog v-model="addDialogVisible" title="添加敏感词" width="400px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input v-model="addForm.word" placeholder="请输入敏感词" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="addForm.type" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略">
          <el-radio-group v-model="addForm.strategy">
            <el-radio v-for="s in strategyOptions" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="批量导入敏感词" width="500px">
      <el-form :model="importForm" label-width="80px">
        <el-form-item label="敏感词" required>
          <el-input
            v-model="importForm.words"
            type="textarea"
            :rows="6"
            placeholder="请输入敏感词，多个词用逗号、换行分隔"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="importForm.type" style="width: 100%">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略">
          <el-radio-group v-model="importForm.strategy">
            <el-radio v-for="s in strategyOptions" :key="s.value" :value="s.value">{{ s.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>
