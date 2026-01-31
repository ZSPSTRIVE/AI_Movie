<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCaptcha, checkEmailVerify, sendLoginEmailCode } from '@/api/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, Picture, Message, Warning, VideoPlay, ChatDotRound, MagicStick } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

// 验证码相关
const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)

// 邮箱验证相关
const needEmailVerify = ref(false)
const maskedEmail = ref('')
const emailCodeCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  username: '',
  password: '',
  captcha: '',
  captchaKey: '',
  emailCode: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { min: 4, max: 4, message: '验证码为4位', trigger: 'blur' }
  ]
}

// 获取图片验证码
async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const res = await getCaptcha()
    captchaImage.value = res.data.captchaImage
    captchaKey.value = res.data.captchaKey
    form.captchaKey = res.data.captchaKey
    form.captcha = ''
  } catch (error) {
    ElMessage.error('获取验证码失败')
  } finally {
    captchaLoading.value = false
  }
}

// 检查是否需要邮箱验证
async function checkNeedEmailVerify() {
  if (!form.username) return
  try {
    const res = await checkEmailVerify(form.username)
    needEmailVerify.value = res.data.needEmailVerify
    if (res.data.maskedEmail) {
      maskedEmail.value = res.data.maskedEmail
    }
  } catch (error) {
    // 忽略错误
  }
}

// 发送邮箱验证码
async function sendEmailCode() {
  if (!form.username || !form.captcha || !form.captchaKey) {
    ElMessage.warning('请先输入用户名和图片验证码')
    return
  }
  
  try {
    await sendLoginEmailCode(form.username, form.captcha, form.captchaKey)
    ElMessage.success('验证码已发送')
    
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
    // 验证码可能错误，刷新
    refreshCaptcha()
  }
}

async function handleLogin() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    // 检查邮箱验证
    if (needEmailVerify.value && !form.emailCode) {
      ElMessage.warning('请输入邮箱验证码')
      return
    }
    
    loading.value = true
    try {
      await userStore.doLogin(form)
      ElMessage.success('登录成功')
      
      const redirect = route.query.redirect as string
      router.push(redirect || '/')
    } catch (error: any) {
      // 检查是否需要邮箱验证 (code 4011)
      if (error?.response?.data?.code === 4011 || error?.message?.includes('邮箱验证码')) {
        needEmailVerify.value = true
        await checkNeedEmailVerify()
      }
      // 刷新验证码
      refreshCaptcha()
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
  <div class="login-page">
    <div class="glass-bg"></div>
    
    <div class="login-container">
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
            <h2>登录</h2>
            <p>欢迎回来，请登录您的账号</p>
          </div>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            @keyup.enter="handleLogin"
          >
            <div class="field-group">
              <label class="field-label">用户名</label>
              <el-form-item prop="username">
                <el-input
                  v-model="form.username"
                  placeholder="请输入用户名"
                  size="large"
                >
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>
            </div>

            <div class="field-group">
              <label class="field-label">密码</label>
              <el-form-item prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码"
                  size="large"
                  show-password
                >
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>
            </div>

            <div class="field-group">
              <label class="field-label">验证码</label>
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

            <!-- 邮箱验证码 -->
            <div class="field-group" v-if="needEmailVerify">
              <div class="security-alert">
                <el-icon><Warning /></el-icon>
                检测到异常登录，请验证邮箱 {{ maskedEmail }}
              </div>
              <label class="field-label">邮箱验证码</label>
              <el-form-item>
                <div class="captcha-wrapper">
                  <el-input
                    v-model="form.emailCode"
                    placeholder="请输入邮箱验证码"
                    size="large"
                    maxlength="6"
                  >
                    <template #prefix><el-icon><Message /></el-icon></template>
                  </el-input>
                  <el-button
                    :disabled="emailCodeCountdown > 0"
                    @click="sendEmailCode"
                  >
                    {{ emailCodeCountdown > 0 ? `${emailCodeCountdown}s` : '发送验证码' }}
                  </el-button>
                </div>
              </el-form-item>
            </div>

            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form>

          <div class="form-footer">
            还没有账号？<router-link to="/register">立即注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-container {
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.1);
}

/* ─── 品牌区 ─── */
.brand-panel {
  background: linear-gradient(160deg, #0284c7 0%, #0ea5e9 50%, #06b6d4 100%);
  padding: 60px 48px;
  display: flex;
  align-items: center;
}

.brand-inner {
  color: #fff;
}

.brand-logo {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
}

.brand-tagline {
  font-size: 16px;
  opacity: 0.9;
  margin: 0 0 48px 0;
  font-weight: 400;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 15px;
  opacity: 0.95;
}

.feature-list li .el-icon {
  font-size: 22px;
  opacity: 0.9;
}

/* ─── 表单区 ─── */
.form-panel {
  padding: 60px 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.form-wrapper {
  width: 100%;
  max-width: 340px;
}

.form-header {
  margin-bottom: 36px;
}

.form-header h2 {
  font-size: 28px;
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
.field-group {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 8px;
}

.captcha-wrapper {
  display: flex;
  gap: 12px;
}

.captcha-wrapper .el-input {
  flex: 1;
}

.captcha-image {
  width: 120px;
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

/* ─── 安全提示 ─── */
.security-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #c2410c;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  padding: 12px 14px;
  border-radius: 8px;
  margin-bottom: 16px;
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

:deep(.el-input__inner) {
  color: #000 !important;
  caret-color: #000 !important;
}

:deep(.el-input__inner::placeholder) {
  color: #94a3b8 !important;
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
  .login-container {
    grid-template-columns: 1fr;
    max-width: 420px;
    min-height: auto;
  }
  
  .brand-panel {
    padding: 40px 32px;
  }
  
  .brand-tagline {
    margin-bottom: 32px;
  }
  
  .feature-list {
    gap: 14px;
  }
  
  .form-panel {
    padding: 40px 32px;
  }
}
</style>
