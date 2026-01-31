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
  msgPageNum.value = 1
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
  try {
    const { value: reason } = await ElMessageBox.prompt(
      '请输入解散原因（将通知所有群成员，此操作不可撤销）',
      '强制解散群组',
      {
        confirmButtonText: '确认解散',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
        inputPlaceholder: '请输入解散原因',
        inputValidator: (val) => !!val.trim() || '请输入解散原因',
        type: 'warning'
      }
    )
    
    await dismissGroup({ groupId: row.id, reason })
    ElMessage.success('群组已解散')
    loadData()
  } catch (e) {
    // cancelled
  }
}

function formatTime(time: string) {
  return time ? new Date(time).toLocaleString() : '-'
}
</script>

<template>
  <div class="h-full flex flex-col gap-6 p-6 bg-gray-50">
    <!-- 顶部标题卡片 -->
    <div class="bg-white p-6 rounded-2xl shadow-sm border border-gray-100 flex items-center gap-4 animate-fade-in-down">
      <div class="w-12 h-12 rounded-xl bg-indigo-50 flex items-center justify-center text-indigo-600">
        <el-icon size="24"><svg-icon name="icon-xiaoxi-zhihui" /></el-icon>
      </div>
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-wide">群组审计</h2>
        <p class="text-gray-500 text-sm mt-1">监控群组状态与违规内容处理</p>
      </div>
    </div>

    <!-- 数据区域 -->
    <div class="bg-white flex-1 rounded-2xl shadow-sm border border-gray-100 flex flex-col overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
      <!-- 搜索栏 -->
      <div class="p-5 border-b border-gray-100 flex gap-4 bg-gray-50/50">
        <el-input v-model="keyword" placeholder="搜索群号/群名称" clearable class="w-72" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <!-- 群组表格 -->
      <div class="flex-1 overflow-hidden p-4">
        <el-table 
          :data="tableData" 
          v-loading="loading" 
          height="100%"
          style="width: 100%"
          :row-style="{ height: '72px' }"
        >
          <el-table-column prop="groupNo" label="群号" width="120" align="center">
             <template #default="{ row }">
               <span class="font-mono text-gray-500">{{ row.groupNo }}</span>
             </template>
          </el-table-column>
          
          <el-table-column label="群组信息" min-width="200">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <el-avatar :size="40" :src="row.avatar" shape="square" class="bg-gray-100 rounded-lg">{{ row.name?.[0] }}</el-avatar>
                <div class="flex flex-col">
                  <span class="font-medium text-gray-900">{{ row.name }}</span>
                  <span class="text-xs text-gray-400">ID: {{ row.id }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column label="群主" width="150" align="center">
            <template #default="{ row }">
               <div class="flex items-center justify-center gap-2">
                 <el-tag size="small" type="info" effect="plain">Owner</el-tag>
                 <span class="text-gray-700">{{ row.ownerNickname }}</span>
               </div>
            </template>
          </el-table-column>
          
          <el-table-column label="成员数" width="100" align="center">
            <template #default="{ row }">
               <el-tag type="info" round effect="light">{{ row.memberCount }} 人</el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="light">
                {{ row.status === 1 ? '正常' : '已解散' }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="创建时间" width="160" align="center">
            <template #default="{ row }">
               <span class="text-xs text-gray-400">{{ formatTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <div class="flex items-center justify-center gap-2">
                 <el-button link type="primary" size="small" @click="viewMessages(row)">
                   <el-icon class="mr-1"><svg-icon name="icon-xiaoxi-zhihui" /></el-icon>审计记录
                 </el-button>
                 <el-button v-if="row.status === 1" link type="danger" size="small" @click="handleDismiss(row)">解散</el-button>
              </div>
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

    <!-- 消息审计弹窗 -->
    <el-dialog v-model="messageDialogVisible" :title="`群聊记录审计 - ${currentGroup?.name}`" width="800px" class="rounded-xl">
      <div class="bg-gray-50 rounded-lg p-4 h-[500px] flex flex-col">
         <!-- 聊天内容区域 -->
         <div v-loading="messageLoading" class="flex-1 overflow-y-auto space-y-4 pr-2">
            <el-empty v-if="!messageLoading && messages.length === 0" description="暂无聊天记录" />
            
            <div v-for="msg in messages" :key="msg.id" class="flex gap-3">
               <el-avatar :size="36" :src="msg.senderAvatar" class="flex-shrink-0 bg-white border border-gray-200">{{ msg.senderName?.[0] }}</el-avatar>
               <div class="flex flex-col max-w-[80%]">
                  <div class="flex items-center gap-2 mb-1">
                     <span class="text-xs text-gray-500 font-medium">{{ msg.senderName }}</span>
                     <span class="text-[10px] text-gray-400">{{ formatTime(msg.createTime) }}</span>
                  </div>
                  <div class="bg-white border border-gray-200 px-3 py-2 rounded-r-lg rounded-bl-lg text-sm text-gray-700 shadow-sm break-all">
                     {{ msg.content }}
                  </div>
               </div>
            </div>
         </div>
         
         <!-- 分页 -->
         <div class="mt-4 pt-3 border-t border-gray-200 flex justify-center">
            <el-pagination
              v-model:current-page="msgPageNum"
              :page-size="msgPageSize"
              :total="msgTotal"
              layout="prev, pager, next, total"
              @change="loadMessages"
              small
            />
         </div>
      </div>
    </el-dialog>
  </div>
</template>
