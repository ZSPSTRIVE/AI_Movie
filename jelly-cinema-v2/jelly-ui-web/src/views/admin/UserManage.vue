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
    total.value = res.data?.total || 0
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
  <div class="p-6">
    <!-- 搜索栏 -->
    <div class="flex justify-between mb-4">
      <div class="flex gap-4">
        <el-input v-model="keyword" placeholder="搜索用户名/昵称/手机号" clearable style="width: 250px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="用户状态" clearable style="width: 120px" @change="handleSearch">
          <el-option label="正常" :value="0" />
          <el-option label="封禁" :value="1" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
      <el-button type="primary" @click="handleAdd">
        <el-icon class="mr-1"><Plus /></el-icon>新增用户
      </el-button>
    </div>

    <!-- 用户表格 -->
    <el-table :data="tableData" v-loading="loading" size="small" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="flex items-center gap-2">
            <el-avatar :size="32" :src="row.avatar">{{ row.nickname?.[0] }}</el-avatar>
            <div>
              <div class="font-medium">{{ row.nickname }}</div>
              <div class="text-xs text-gray-400">{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
            {{ row.status === 0 ? '正常' : '封禁' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="最后登录" width="160">
        <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="showDetail(row)">详情</el-button>
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 0" link type="danger" size="small" @click="openBanDialog(row)">封禁</el-button>
          <el-button v-else link type="success" size="small" @click="handleUnban(row)">解封</el-button>
          <el-dropdown trigger="click">
            <el-button link type="primary" size="small">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleResetPassword(row)">重置密码</el-dropdown-item>
                <el-dropdown-item @click="handleForceLogout(row)">强制下线</el-dropdown-item>
                <el-dropdown-item divided @click="handleDelete(row)">
                  <span class="text-red-500">删除用户</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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

    <!-- 用户详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="用户详情" size="400px">
      <div v-loading="detailLoading">
        <template v-if="userDetail">
          <!-- 基本信息 -->
          <div class="text-center mb-6">
            <el-avatar :size="72" :src="userDetail.avatar">{{ userDetail.nickname?.[0] }}</el-avatar>
            <h3 class="text-lg font-medium mt-2">{{ userDetail.nickname }}</h3>
            <p class="text-gray-400 text-sm">{{ userDetail.signature || '暂无签名' }}</p>
          </div>

          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="用户ID">{{ userDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="用户名">{{ userDetail.username }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userDetail.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userDetail.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="userDetail.status === 0 ? 'success' : 'danger'" size="small">
                {{ userDetail.status === 0 ? '正常' : '封禁' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="userDetail.status === 1" label="封禁原因">
              {{ userDetail.banReason }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(userDetail.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 好友列表 -->
          <h4 class="font-medium mt-6 mb-2">好友列表 ({{ userDetail.friends?.length || 0 }})</h4>
          <div class="flex flex-wrap gap-2">
            <el-tag v-for="f in userDetail.friends" :key="f.id" size="small">{{ f.nickname }}</el-tag>
            <span v-if="!userDetail.friends?.length" class="text-gray-400 text-sm">暂无好友</span>
          </div>

          <!-- 群组列表 -->
          <h4 class="font-medium mt-6 mb-2">加入的群组 ({{ userDetail.groups?.length || 0 }})</h4>
          <div class="flex flex-wrap gap-2">
            <el-tag v-for="g in userDetail.groups" :key="g.id" type="info" size="small">{{ g.name }}</el-tag>
            <span v-if="!userDetail.groups?.length" class="text-gray-400 text-sm">暂无群组</span>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 封禁弹窗 -->
    <el-dialog v-model="banDialogVisible" title="封禁用户" width="400px">
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

    <!-- 新增/编辑用户弹窗 -->
    <el-dialog v-model="userDialogVisible" :title="userDialogTitle" width="500px">
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
          <el-select v-model="userForm.role" placeholder="请选择角色">
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
