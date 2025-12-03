<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getGroupList, getGroupMessages, dismissGroup, type GroupListItem } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref<GroupListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')

// 消息审计弹窗
const messageDialogVisible = ref(false)
const messageLoading = ref(false)
const messages = ref<any[]>([])
const currentGroup = ref<GroupListItem | null>(null)
const msgPageNum = ref(1)
const msgPageSize = ref(50)
const msgTotal = ref(0)

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getGroupList({
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

async function viewMessages(row: GroupListItem) {
  currentGroup.value = row
  messageDialogVisible.value = true
  await loadMessages()
}

async function loadMessages() {
  if (!currentGroup.value) return
  
  messageLoading.value = true
  try {
    const res = await getGroupMessages(currentGroup.value.id, {
      pageNum: msgPageNum.value,
      pageSize: msgPageSize.value
    })
    messages.value = res.data?.records || []
    msgTotal.value = res.data?.total || 0
  } finally {
    messageLoading.value = false
  }
}

async function handleDismiss(row: GroupListItem) {
  const { value: reason } = await ElMessageBox.prompt(
    '请输入解散原因（将通知所有群成员）',
    '强制解散群组',
    {
      confirmButtonText: '确认解散',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入解散原因',
      inputValidator: (val) => !!val || '请输入解散原因',
      type: 'warning'
    }
  )
  
  await dismissGroup({ groupId: row.id, reason })
  ElMessage.success('群组已解散')
  loadData()
}

function formatTime(time: string) {
  return time ? new Date(time).toLocaleString() : '-'
}
</script>

<template>
  <div class="p-6">
    <!-- 搜索栏 -->
    <div class="flex gap-4 mb-4">
      <el-input v-model="keyword" placeholder="搜索群号/群名" clearable style="width: 250px" @keyup.enter="handleSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <!-- 群组表格 -->
    <el-table :data="tableData" v-loading="loading" size="small" stripe>
      <el-table-column prop="groupNo" label="群号" width="120" />
      <el-table-column label="群组" min-width="200">
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <el-avatar :size="36" :src="row.avatar" shape="square">{{ row.name?.[0] }}</el-avatar>
            <span class="font-medium">{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="群主" width="150">
        <template #default="{ row }">{{ row.ownerNickname }}</template>
      </el-table-column>
      <el-table-column label="成员数" width="100">
        <template #default="{ row }">{{ row.memberCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '已解散' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="viewMessages(row)">查看聊天记录</el-button>
          <el-button v-if="row.status === 1" link type="danger" size="small" @click="handleDismiss(row)">解散</el-button>
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

    <!-- 消息审计弹窗 -->
    <el-dialog v-model="messageDialogVisible" :title="`聊天记录 - ${currentGroup?.name}`" width="700px">
      <div v-loading="messageLoading" class="max-h-96 overflow-y-auto space-y-3">
        <el-empty v-if="messages.length === 0" description="暂无聊天记录" />
        
        <div v-for="msg in messages" :key="msg.id" class="flex gap-3 p-3 bg-dark-card rounded">
          <el-avatar :size="36" :src="msg.senderAvatar">{{ msg.senderName?.[0] }}</el-avatar>
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-1">
              <span class="font-medium text-sm">{{ msg.senderName }}</span>
              <span class="text-xs text-gray-500">{{ formatTime(msg.createTime) }}</span>
            </div>
            <div class="text-sm text-gray-300">{{ msg.content }}</div>
          </div>
        </div>
      </div>
      
      <div class="flex justify-center mt-4">
        <el-pagination
          v-model:current-page="msgPageNum"
          :page-size="msgPageSize"
          :total="msgTotal"
          layout="prev, pager, next"
          @change="loadMessages"
        />
      </div>
    </el-dialog>
  </div>
</template>
