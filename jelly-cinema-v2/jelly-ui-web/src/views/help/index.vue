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
  <div class="help-page">
    <!-- 头部 -->
    <div class="help-hero">
      <h1 class="hero-title">帮助与反馈</h1>
      <p class="hero-subtitle">有问题？我们来帮您解决</p>
    </div>

    <!-- 常见问题 -->
    <section class="help-section">
      <h2 class="section-title">常见问题</h2>
      
      <el-collapse v-model="activeNames" class="faq-collapse">
        <el-collapse-item
          v-for="(faq, index) in faqs"
          :key="index"
          :name="String(index)"
          class="faq-item"
        >
          <template #title>
            <span class="faq-question">{{ faq.question }}</span>
          </template>
          <div class="faq-answer">
            {{ faq.answer }}
          </div>
        </el-collapse-item>
      </el-collapse>
    </section>

    <!-- 联系方式 -->
    <section class="help-section">
      <h2 class="section-title">联系我们</h2>
      
      <div class="contact-grid">
        <div class="contact-card">
          <div class="contact-label">邮箱</div>
          <p class="contact-value">3080714093@qq.com</p>
        </div>
        <div class="contact-card">
          <div class="contact-label">在线客服</div>
          <p class="contact-value">工作日 9:00 - 18:00</p>
        </div>
        
      </div>
    </section>

    <!-- 反馈表单 -->
    <section class="help-section">
      <h2 class="section-title">提交反馈</h2>
      
      <el-form :model="feedbackForm" label-position="top" class="feedback-form">
        <el-form-item label="反馈类型">
          <el-radio-group v-model="feedbackForm.type" class="radio-group">
            <el-radio
              v-for="type in feedbackTypes"
              :key="type.value"
              :value="type.value"
              class="radio-option"
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
          />
        </el-form-item>
        
        <el-form-item label="联系方式（选填）">
          <el-input
            v-model="feedbackForm.contact"
            placeholder="请留下您的邮箱或手机号，方便我们回复您"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="submitting"
            class="submit-btn"
            @click="submitFeedback"
          >
            提交反馈
          </el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.help-page {
  max-width: 780px;
  margin: 0 auto;
  padding: 40px 20px 80px;
}

.help-hero {
  text-align: center;
  padding: 48px 0 40px;
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 17px;
  color: var(--text-secondary);
  font-weight: 400;
}

.help-section {
  margin-bottom: 48px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.01em;
  margin-bottom: 20px;
}

/* FAQ */
.faq-collapse {
  border: none !important;
}

.faq-item {
  border: 1px solid var(--border-color) !important;
  border-radius: 12px !important;
  margin-bottom: 8px !important;
  overflow: hidden;
  background: var(--bg-card);
}

.faq-question {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.faq-answer {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary);
  padding: 0 20px 16px;
}

:deep(.el-collapse-item__header) {
  background: transparent;
  border: none;
  padding: 16px 20px;
  font-size: 15px;
  height: auto;
  line-height: 1.5;
}

:deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

:deep(.el-collapse-item__content) {
  padding: 0;
}

/* Contact */
.contact-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.contact-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px 16px;
  text-align: center;
  transition: background 0.2s var(--ease-apple);
}

.contact-card:hover {
  background: var(--bg-elevated);
}

.contact-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.contact-value {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

/* Feedback Form */
.feedback-form {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
}

.radio-group {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 8px !important;
}

.radio-option {
  border: 1px solid var(--border-color) !important;
  border-radius: 8px !important;
  padding: 6px 14px !important;
  margin: 0 !important;
  transition: border-color 0.2s;
}

.radio-option:hover {
  border-color: var(--color-primary) !important;
}

:deep(.el-radio.is-checked .el-radio__inner) {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  border-radius: 8px !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: none !important;
}

:deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.submit-btn {
  width: 100%;
  border-radius: 980px !important;
  height: 44px;
  font-size: 15px;
  font-weight: 500;
}

/* Responsive */
@media (max-width: 768px) {
  .help-page {
    padding: 20px 16px 60px;
  }

  .help-hero {
    padding: 32px 0 28px;
  }

  .hero-title {
    font-size: 28px;
  }

  .contact-grid {
    grid-template-columns: 1fr;
  }

  .feedback-form {
    padding: 16px;
  }
}
</style>
