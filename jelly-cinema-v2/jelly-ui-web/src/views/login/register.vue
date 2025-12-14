<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { register, getCaptcha, sendEmailCode } from '@/api/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, Picture, Message, Key, Avatar, VideoPlay, ChatDotRound, MagicStick } from '@element-plus/icons-vue'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

// 验证码相关
const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)

// 邮箱验证码倒计时
const emailCodeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  username: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  captcha: ''
})

const validateConfirmPassword = (rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateEmail = (rule: any, value: string, callback: any) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度为 4-20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: validateEmail, trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { min: 6, max: 6, message: '验证码为6位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  nickname: [
    { max: 30, message: '昵称最多 30 个字符', trigger: 'blur' }
  ],
  captcha: [
    { required: true, message: '请输入图片验证码', trigger: 'blur' }
  ]
}

// 获取图片验证码
async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const res = await getCaptcha()
    captchaImage.value = res.data.captchaImage
    captchaKey.value = res.data.captchaKey
    form.captcha = ''
  } catch (error) {
    ElMessage.error('获取验证码失败')
  } finally {
    captchaLoading.value = false
  }
}

// 发送邮箱验证码
async function handleSendEmailCode() {
  // 验证邮箱和图片验证码
  if (!form.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!form.captcha) {
    ElMessage.warning('请先输入图片验证码')
    return
  }
  
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(form.email)) {
    ElMessage.warning('请输入正确的邮箱格式')
    return
  }
  
  try {
    await sendEmailCode({
      email: form.email,
      businessType: 'register',
      captcha: form.captcha,
      captchaKey: captchaKey.value
    })
    ElMessage.success('验证码已发送到您的邮箱')
    
    // 刷新图片验证码
    refreshCaptcha()
    
    // 开始倒计时
    emailCodeCountdown.value = 60
    countdownTimer = setInterval(() => {
      emailCodeCountdown.value--
      if (emailCodeCountdown.value <= 0) {
        clearInterval(countdownTimer!)
        countdownTimer = null
      }
    }, 1000)
  } catch (error) {
    // 图片验证码可能错误，刷新
    refreshCaptcha()
  }
}

async function handleRegister() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      await register(form)
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (error) {
      // Error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="register-page">
    <div class="glass-bg"></div>
    
    <div class="register-container">
      <!-- 左侧品牌区 -->
      <div class="brand-panel">
        <div class="brand-inner">
          <h1 class="brand-logo">Jelly Cinema</h1>
          <p class="brand-tagline">发现精彩，畅享视界</p>
          <ul class="feature-list">
            <li>
              <el-icon><VideoPlay /></el-icon>
              <span>海量高清影视资源</span>
            </li>
            <li>
              <el-icon><ChatDotRound /></el-icon>
              <span>影迷社区深度交流</span>
            </li>
            <li>
              <el-icon><MagicStick /></el-icon>
              <span>智能个性化推荐</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-panel">
        <div class="form-wrapper">
          <div class="form-header">
            <h2>创建账号</h2>
            <p>加入 Jelly Cinema，开启精彩之旅</p>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            @keyup.enter="handleRegister"
          >
            <div class="field-row">
              <div class="field-group">
                <label class="field-label">用户名</label>
                <el-form-item prop="username">
                  <el-input
                    v-model="form.username"
                    placeholder="4-20位字母数字"
                    size="large"
                  >
                    <template #prefix><el-icon><User /></el-icon></template>
                  </el-input>
                </el-form-item>
              </div>
              <div class="field-group">
                <label class="field-label">昵称（选填）</label>
                <el-form-item prop="nickname">
                  <el-input
                    v-model="form.nickname"
                    placeholder="您的昵称"
                    size="large"
                  >
                    <template #prefix><el-icon><Avatar /></el-icon></template>
                  </el-input>
                </el-form-item>
              </div>
            </div>

            <div class="field-group">
              <label class="field-label">邮箱</label>
              <el-form-item prop="email">
                <el-input
                  v-model="form.email"
                  placeholder="请输入邮箱地址"
                  size="large"
                >
                  <template #prefix><el-icon><Message /></el-icon></template>
                </el-input>
              </el-form-item>
            </div>

            <div class="field-group">
              <label class="field-label">图片验证码</label>
              <el-form-item prop="captcha">
                <div class="captcha-wrapper">
                  <el-input
                    v-model="form.captcha"
                    placeholder="请输入验证码"
                    size="large"
                    maxlength="4"
                  >
                    <template #prefix><el-icon><Picture /></el-icon></template>
                  </el-input>
                  <div class="captcha-image" @click="refreshCaptcha">
                    <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                    <span v-else>加载中</span>
                  </div>
                </div>
              </el-form-item>
            </div>

            <div class="field-group">
              <label class="field-label">邮箱验证码</label>
              <el-form-item prop="emailCode">
                <div class="captcha-wrapper">
                  <el-input
                    v-model="form.emailCode"
                    placeholder="请输入邮箱验证码"
                    size="large"
                    maxlength="6"
                  >
                    <template #prefix><el-icon><Key /></el-icon></template>
                  </el-input>
                  <el-button
                    :disabled="emailCodeCountdown > 0"
                    @click="handleSendEmailCode"
                  >
                    {{ emailCodeCountdown > 0 ? `${emailCodeCountdown}s` : '发送验证码' }}
                  </el-button>
                </div>
              </el-form-item>
            </div>

            <div class="field-row">
              <div class="field-group">
                <label class="field-label">密码</label>
                <el-form-item prop="password">
                  <el-input
                    v-model="form.password"
                    type="password"
                    placeholder="6-20位密码"
                    size="large"
                    show-password
                  >
                    <template #prefix><el-icon><Lock /></el-icon></template>
                  </el-input>
                </el-form-item>
              </div>
              <div class="field-group">
                <label class="field-label">确认密码</label>
                <el-form-item prop="confirmPassword">
                  <el-input
                    v-model="form.confirmPassword"
                    type="password"
                    placeholder="再次输入密码"
                    size="large"
                    show-password
                  >
                    <template #prefix><el-icon><Lock /></el-icon></template>
                  </el-input>
                </el-form-item>
              </div>
            </div>

            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form>

          <div class="form-footer">
            已有账号？<router-link to="/login">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.register-container {
  width: 100%;
  max-width: 960px;
  display: grid;
  grid-template-columns: 380px 1fr;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
}

/* ─── 品牌区 ─── */
.brand-panel {
  background: linear-gradient(160deg, #0284c7 0%, #0ea5e9 50%, #06b6d4 100%);
  padding: 60px 40px;
  display: flex;
  align-items: center;
}

.brand-inner {
  color: #fff;
}

.brand-logo {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
}

.brand-tagline {
  font-size: 15px;
  opacity: 0.9;
  margin: 0 0 40px 0;
  font-weight: 400;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  opacity: 0.95;
}

.feature-list li .el-icon {
  font-size: 20px;
  opacity: 0.9;
}

/* ─── 表单区 ─── */
.form-panel {
  padding: 48px 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.form-wrapper {
  width: 100%;
  max-width: 420px;
}

.form-header {
  margin-bottom: 32px;
}

.form-header h2 {
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px 0;
}

.form-header p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

/* ─── 表单字段 ─── */
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.field-group {
  margin-bottom: 20px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 6px;
}

.captcha-wrapper {
  display: flex;
  gap: 12px;
}

.captcha-wrapper .el-input {
  flex: 1;
}

.captcha-image {
  width: 110px;
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  flex-shrink: 0;
}

.captcha-image:hover {
  border-color: #cbd5e1;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-image span {
  font-size: 12px;
  color: #94a3b8;
}

/* ─── 提交按钮 ─── */
.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
}

/* ─── 表单底部 ─── */
.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: #64748b;
}

.form-footer a {
  color: #0ea5e9;
  text-decoration: none;
  font-weight: 500;
}

.form-footer a:hover {
  text-decoration: underline;
}

/* ─── Element Plus 样式覆盖 ─── */
:deep(.el-input__wrapper) {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 !important;
  padding: 4px 12px !important;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #cbd5e1 !important;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #0ea5e9 !important;
}

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #0ea5e9, #06b6d4) !important;
  border: none !important;
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #0284c7, #0891b2) !important;
}

/* ─── 响应式 ─── */
@media (max-width: 800px) {
  .register-container {
    grid-template-columns: 1fr;
    max-width: 480px;
  }
  
  .brand-panel {
    padding: 32px 28px;
  }
  
  .brand-tagline {
    margin-bottom: 24px;
  }
  
  .feature-list {
    gap: 12px;
  }
  
  .form-panel {
    padding: 32px 28px;
  }
  
  .field-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
