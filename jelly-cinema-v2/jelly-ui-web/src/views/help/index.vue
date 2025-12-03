<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

// FAQ 列表
const faqs = [
  {
    question: '如何修改个人资料？',
    answer: '点击右上角头像，进入"个人中心"，在个人信息页面点击"编辑资料"即可修改您的昵称、头像、个性签名等信息。'
  },
  {
    question: '如何添加好友？',
    answer: '在聊天页面，点击左侧的"+"按钮，选择"添加好友/群"，输入对方的用户ID或手机号进行搜索，发送好友申请等待对方同意即可。'
  },
  {
    question: '如何创建群聊？',
    answer: '在聊天页面，点击左侧的"+"按钮，选择"创建群聊"，设置群名称并选择要邀请的好友，点击确定即可创建群聊。'
  },
  {
    question: '消息发送失败怎么办？',
    answer: '请检查网络连接是否正常，如果问题持续存在，请尝试刷新页面或重新登录。如果仍然无法解决，请联系客服。'
  },
  {
    question: '如何修改聊天设置？',
    answer: '在聊天页面，点击左下角的设置按钮，可以设置消息通知、提示音、隐私设置等选项。'
  },
  {
    question: '如何举报违规内容？',
    answer: '在聊天或帖子中，右键点击违规内容，选择"举报"选项，填写举报原因提交即可。我们会尽快处理您的举报。'
  }
]

// 反馈表单
const feedbackForm = ref({
  type: 'suggestion',
  title: '',
  content: '',
  contact: ''
})

const feedbackTypes = [
  { value: 'suggestion', label: '功能建议' },
  { value: 'bug', label: 'Bug 反馈' },
  { value: 'complaint', label: '投诉建议' },
  { value: 'other', label: '其他' }
]

const submitting = ref(false)

async function submitFeedback() {
  if (!feedbackForm.value.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!feedbackForm.value.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  
  submitting.value = true
  try {
    // 模拟提交
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('反馈提交成功，感谢您的宝贵意见！')
    // 重置表单
    feedbackForm.value = {
      type: 'suggestion',
      title: '',
      content: '',
      contact: ''
    }
  } finally {
    submitting.value = false
  }
}

// 当前展开的 FAQ
const activeNames = ref<string[]>([])
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <!-- 头部 -->
    <div class="bg-pop-green border-3 border-black rounded-2xl p-8 text-center shadow-brutal">
      <div class="text-6xl mb-4">💬</div>
      <h1 class="text-4xl font-black text-black uppercase mb-2">帮助与反馈</h1>
      <p class="text-black/70 text-lg">有问题？我们来帮您解决！</p>
    </div>

    <!-- 常见问题 -->
    <div class="bg-white border-3 border-black rounded-2xl p-6 shadow-brutal">
      <h2 class="text-2xl font-black mb-6 flex items-center">
        <span class="bg-pop-yellow border-2 border-black rounded-lg px-4 py-2 mr-3">❓</span>
        常见问题
      </h2>
      
      <el-collapse v-model="activeNames" class="!border-none">
        <el-collapse-item
          v-for="(faq, index) in faqs"
          :key="index"
          :name="String(index)"
          class="!border-2 !border-black !rounded-xl !mb-3 overflow-hidden"
        >
          <template #title>
            <span class="font-bold text-nb-text">{{ faq.question }}</span>
          </template>
          <div class="text-gray-600 bg-nb-bg p-4 -mx-4 -mb-4 mt-2 border-t-2 border-black">
            {{ faq.answer }}
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 联系方式 -->
    <div class="bg-white border-3 border-black rounded-2xl p-6 shadow-brutal">
      <h2 class="text-2xl font-black mb-6 flex items-center">
        <span class="bg-pop-blue text-white border-2 border-black rounded-lg px-4 py-2 mr-3">📞</span>
        联系我们
      </h2>
      
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="bg-nb-bg border-2 border-black rounded-xl p-4 text-center">
          <div class="text-3xl mb-2">📧</div>
          <h3 class="font-bold mb-1">邮箱</h3>
          <p class="text-gray-600 text-sm">support@jellycinema.com</p>
        </div>
        <div class="bg-nb-bg border-2 border-black rounded-xl p-4 text-center">
          <div class="text-3xl mb-2">💻</div>
          <h3 class="font-bold mb-1">在线客服</h3>
          <p class="text-gray-600 text-sm">工作日 9:00-18:00</p>
        </div>
        <div class="bg-nb-bg border-2 border-black rounded-xl p-4 text-center">
          <div class="text-3xl mb-2">📱</div>
          <h3 class="font-bold mb-1">微信公众号</h3>
          <p class="text-gray-600 text-sm">JellyCinema</p>
        </div>
      </div>
    </div>

    <!-- 反馈表单 -->
    <div class="bg-white border-3 border-black rounded-2xl p-6 shadow-brutal">
      <h2 class="text-2xl font-black mb-6 flex items-center">
        <span class="bg-pop-orange border-2 border-black rounded-lg px-4 py-2 mr-3">✍️</span>
        提交反馈
      </h2>
      
      <el-form :model="feedbackForm" label-position="top">
        <el-form-item label="反馈类型">
          <el-radio-group v-model="feedbackForm.type" class="!flex !flex-wrap !gap-3">
            <el-radio
              v-for="type in feedbackTypes"
              :key="type.value"
              :value="type.value"
              class="!border-2 !border-black !rounded-lg !px-4 !py-2 !m-0"
            >
              {{ type.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item label="标题" required>
          <el-input
            v-model="feedbackForm.title"
            placeholder="请简要描述您的问题或建议"
            maxlength="50"
            show-word-limit
            class="!border-2 !border-black !rounded-xl"
          />
        </el-form-item>
        
        <el-form-item label="详细描述" required>
          <el-input
            v-model="feedbackForm.content"
            type="textarea"
            :rows="5"
            placeholder="请详细描述您遇到的问题或建议，以便我们更好地为您服务"
            maxlength="500"
            show-word-limit
            class="!border-2 !border-black !rounded-xl"
          />
        </el-form-item>
        
        <el-form-item label="联系方式（选填）">
          <el-input
            v-model="feedbackForm.contact"
            placeholder="请留下您的邮箱或手机号，方便我们回复您"
            class="!border-2 !border-black !rounded-xl"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            class="w-full !bg-pop-blue !text-white !border-3 !border-black !font-black !shadow-brutal-sm"
            @click="submitFeedback"
          >
            提交反馈
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
:deep(.el-collapse-item__header) {
  background: transparent;
  border: none;
  padding: 16px;
  font-size: 16px;
}

:deep(.el-collapse-item__wrap) {
  border: none;
}

:deep(.el-collapse-item__content) {
  padding: 0;
}

:deep(.el-radio.is-checked .el-radio__inner) {
  background: #4A90E2;
  border-color: #000;
}
</style>
