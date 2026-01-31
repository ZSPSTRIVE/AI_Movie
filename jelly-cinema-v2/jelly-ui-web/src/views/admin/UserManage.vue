<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserList, getUserDetail, banUser, unbanUser, resetUserPassword, forceLogout, createUser, updateUser, deleteUser, type UserListItem, type UserDetail } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref<UserListItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const statusFilter = ref<number | undefined>()

// 详情抽屉
const drawerVisible = ref(false)
const detailLoading = ref(false)
const userDetail = ref<UserDetail | null>(null)

// 封禁弹窗
const banDialogVisible = ref(false)
const banForm = ref({ userId: '', duration: 24, reason: '' })

// 新增/编辑用户弹窗
const userDialogVisible = ref(false)
const userDialogTitle = ref('新增用户')
const userForm = ref<{
  id?: string
  username?: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  role?: string
}>({})

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const res = await getUserList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value
    })
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total) || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadData()
}

async function showDetail(row: UserListItem) {
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getUserDetail(row.id)
    userDetail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

function openBanDialog(row: UserListItem) {
  banForm.value = { userId: row.id, duration: 24, reason: '' }
  banDialogVisible.value = true
}

async function confirmBan() {
  if (!banForm.value.reason) {
    ElMessage.warning('请输入封禁原因')
    return
  }
  
  await banUser(banForm.value)
  ElMessage.success('已封禁')
  banDialogVisible.value = false
  loadData()
}

async function handleUnban(row: UserListItem) {
  await ElMessageBox.confirm('确定要解封该用户吗？', '提示')
  await unbanUser(row.id)
  ElMessage.success('已解封')
  loadData()
}

async function handleResetPassword(row: UserListItem) {
  await ElMessageBox.confirm('确定要重置该用户的密码吗？', '提示', { type: 'warning' })
  const res = await resetUserPassword(row.id)
  ElMessageBox.alert(`新密码: ${res.data}`, '密码已重置', { type: 'success' })
}

async function handleForceLogout(row: UserListItem) {
  await ElMessageBox.confirm('确定要强制下线该用户吗？', '提示')
  await forceLogout(row.id)
  ElMessage.success('已强制下线')
}

function formatTime(time: string) {
  return time ? new Date(time).toLocaleString() : '-'
}

function handleAdd() {
  userDialogTitle.value = '新增用户'
  userForm.value = { role: 'ROLE_USER' }
  userDialogVisible.value = true
}

function handleEdit(row: UserListItem) {
  userDialogTitle.value = '编辑用户'
  userForm.value = {
    id: row.id,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone
  }
  userDialogVisible.value = true
}

async function handleSaveUser() {
  if (userForm.value.id) {
    // 编辑
    await updateUser(userForm.value.id, {
      nickname: userForm.value.nickname,
      email: userForm.value.email,
      phone: userForm.value.phone,
      role: userForm.value.role
    })
    ElMessage.success('更新成功')
  } else {
    // 新增
    if (!userForm.value.username || !userForm.value.password) {
      ElMessage.warning('请填写用户名和密码')
      return
    }
    await createUser({
      username: userForm.value.username,
      password: userForm.value.password,
      nickname: userForm.value.nickname,
      role: userForm.value.role
    })
    ElMessage.success('创建成功')
  }
  userDialogVisible.value = false
  loadData()
}

async function handleDelete(row: UserListItem) {
  await ElMessageBox.confirm('确定要删除该用户吗？此操作不可恢复！', '警告', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadData()
}
</script>

<template>
  <div class="h-full flex flex-col gap-6">
    <!-- 顶部统计/操作卡片 -->
    <div class="glass-card p-6 rounded-2xl flex flex-col md:flex-row justify-between items-center gap-4 animate-fade-in-down">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/30">
          <el-icon size="24" color="white"><UserFilled /></el-icon>
        </div>
        <div>
          <h2 class="text-2xl font-bold text-white tracking-wide">用户管理</h2>
          <p class="text-gray-400 text-sm mt-1">管理系统注册用户及权限状态</p>
        </div>
      </div>
      
      <el-button 
        type="primary" 
        size="large"
        class="!rounded-xl !px-6 !font-bold shadow-lg shadow-blue-500/20 hover:shadow-blue-500/40 transition-all"
        @click="handleAdd"
      >
        <el-icon class="mr-2"><Plus /></el-icon>
        新增用户
      </el-button>
    </div>

    <!-- 数据区域 -->
    <div class="glass-card flex-1 rounded-2xl flex flex-col overflow-hidden animate-fade-in-up" style="animation-delay: 0.1s">
      <!-- 搜索栏 -->
      <div class="p-5 border-b border-white/5 flex gap-4 bg-white/5 backdrop-blur-sm justify-between">
        <div class="flex gap-4">
           <el-input 
             v-model="keyword" 
             placeholder="搜索用户名/昵称/手机号" 
             clearable 
             class="glass-input w-64"
             @keyup.enter="handleSearch"
           >
             <template #prefix><el-icon><Search /></el-icon></template>
           </el-input>
           <el-select v-model="statusFilter" placeholder="用户状态" clearable class="glass-select w-32" @change="handleSearch">
             <el-option label="正常" :value="0" />
             <el-option label="封禁" :value="1" />
           </el-select>
           <el-button class="glass-button-icon" @click="handleSearch">
             <el-icon><Search /></el-icon>
           </el-button>
        </div>
      </div>

      <!-- 用户表格 -->
      <div class="flex-1 overflow-hidden p-4">
        <el-table 
          :data="tableData" 
          v-loading="loading" 
          height="100%"
          style="width: 100%"
          :row-style="{ height: '72px' }"
        >
          <el-table-column prop="id" label="ID" width="80" align="center" show-overflow-tooltip />
          
          <el-table-column label="用户信息" min-width="200">
            <template #default="{ row }">
              <div class="flex items-center gap-3">
                <el-avatar :size="40" :src="row.avatar" class="border-2 border-white/10">{{ row.nickname?.[0] }}</el-avatar>
                <div class="flex flex-col">
                  <span class="font-bold text-gray-200">{{ row.nickname }}</span>
                  <span class="text-xs text-gray-500">{{ row.username }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="phone" label="手机号" width="140" align="center" />
          
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag 
                effect="dark"
                :class="row.status === 0 ? '!bg-green-500/20 !border-green-500/30 !text-green-400' : '!bg-red-500/20 !border-red-500/30 !text-red-400'"
              >
                {{ row.status === 0 ? '正常' : '封禁' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="注册时间" width="160" align="center">
            <template #default="{ row }">
              <span class="text-gray-400 font-mono text-xs">{{ formatTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="240" fixed="right" align="center">
            <template #default="{ row }">
              <div class="flex items-center justify-center gap-2">
                <el-button size="small" link class="!text-blue-400 hover:!text-blue-300" @click="showDetail(row)">详情</el-button>
                <el-button size="small" link class="!text-amber-400 hover:!text-amber-300" @click="handleEdit(row)">编辑</el-button>
                
                <el-button v-if="row.status === 0" size="small" link class="!text-red-400 hover:!text-red-300" @click="openBanDialog(row)">封禁</el-button>
                <el-button v-else size="small" link class="!text-green-400 hover:!text-green-300" @click="handleUnban(row)">解封</el-button>
                
                <el-dropdown trigger="click">
                  <el-button size="small" link class="!text-gray-400 hover:!text-white">更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu class="glass-dropdown">
                      <el-dropdown-item @click="handleResetPassword(row)">重置密码</el-dropdown-item>
                      <el-dropdown-item @click="handleForceLogout(row)">强制下线</el-dropdown-item>
                      <el-dropdown-item divided @click="handleDelete(row)">
                        <span class="text-red-500">删除用户</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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

    <!-- 用户详情抽屉 (保持原有逻辑，仅调整样式建议在 index.scss 全局覆盖 el-drawer) -->
    <el-drawer v-model="drawerVisible" title="用户详情" size="400px" class="glass-drawer">
      <div v-loading="detailLoading">
        <template v-if="userDetail">
          <!-- 基本信息 -->
          <div class="text-center mb-8">
            <div class="relative inline-block">
               <el-avatar :size="80" :src="userDetail.avatar" class="ring-4 ring-white/10">{{ userDetail.nickname?.[0] }}</el-avatar>
               <div :class="['absolute bottom-0 right-0 w-4 h-4 rounded-full border-2 border-[#1E293B]', userDetail.status === 0 ? 'bg-green-500' : 'bg-red-500']"></div>
            </div>
            <h3 class="text-xl font-bold text-white mt-3">{{ userDetail.nickname }}</h3>
            <p class="text-gray-400 text-sm mt-1">{{ userDetail.signature || '暂无签名' }}</p>
          </div>

          <el-descriptions :column="1" border size="large" class="glass-descriptions">
            <el-descriptions-item label="用户ID">{{ userDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ userDetail.username }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userDetail.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userDetail.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
               <el-tag size="small" effect="dark" class="!bg-blue-500/20 !border-blue-500/30">
                 {{ userDetail.role === 'ROLE_ADMIN' ? '管理员' : '普通用户' }}
               </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="userDetail.status === 1" label="封禁原因">
              <span class="text-red-400">{{ userDetail.banReason }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(userDetail.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
    </el-drawer>

    <!-- 弹窗部分保持原有逻辑，样式由全局 CSS 控制 -->
    <el-dialog v-model="banDialogVisible" title="封禁用户" width="400px" class="glass-dialog">
      <el-form :model="banForm" label-width="80px">
        <el-form-item label="封禁时长">
          <el-radio-group v-model="banForm.duration">
            <el-radio :value="24">1天</el-radio>
            <el-radio :value="168">7天</el-radio>
            <el-radio :value="720">30天</el-radio>
            <el-radio :value="0">永久</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="封禁原因" required>
          <el-input v-model="banForm.reason" type="textarea" :rows="3" placeholder="请输入封禁原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBan">确认封禁</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialogVisible" :title="userDialogTitle" width="500px" class="glass-dialog">
      <el-form :model="userForm" label-width="80px">
        <template v-if="!userForm.id">
          <el-form-item label="用户名" required>
            <el-input v-model="userForm.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" required>
            <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>
        </template>
        <el-form-item label="昵称">
          <el-input v-model="userForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role" placeholder="请选择角色" class="w-full">
            <el-option label="普通用户" value="ROLE_USER" />
            <el-option label="管理员" value="ROLE_ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
