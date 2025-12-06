<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { register, getCaptcha, sendEmailCode } from '@/api/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

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
  <div class="min-h-screen bg-dark-bg flex items-center justify-center">
    <div class="w-full max-w-md">
      <!-- Logo -->
      <div class="text-center mb-8">
        <h1 class="text-4xl font-bold text-primary mb-2">🍮 果冻影院</h1>
        <p class="text-gray-400">影视 + 社交 + AI 一体化平台</p>
      </div>

      <!-- Register Form -->
      <div class="bg-dark-card rounded-xl p-8">
        <h2 class="text-2xl font-bold text-white text-center mb-6">注册</h2>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleRegister"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              prefix-icon="User"
            />
          </el-form-item>

          <el-form-item prop="nickname">
            <el-input
              v-model="form.nickname"
              placeholder="昵称（选填）"
              prefix-icon="Avatar"
            />
          </el-form-item>

          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              placeholder="邮箱"
              prefix-icon="Message"
              type="email"
            />
          </el-form-item>

          <!-- 图片验证码 -->
          <el-form-item prop="captcha">
            <div class="flex gap-2 w-full">
              <el-input
                v-model="form.captcha"
                placeholder="图片验证码"
                prefix-icon="Picture"
                class="flex-1"
                maxlength="4"
              />
              <div 
                class="w-28 h-10 border border-gray-600 rounded overflow-hidden cursor-pointer bg-gray-700 flex items-center justify-center"
                @click="refreshCaptcha"
              >
                <img 
                  v-if="captchaImage" 
                  :src="captchaImage" 
                  alt="验证码" 
                  class="h-full w-full object-cover"
                />
                <span v-else class="text-gray-400 text-sm">加载中...</span>
              </div>
            </div>
          </el-form-item>

          <!-- 邮箱验证码 -->
          <el-form-item prop="emailCode">
            <div class="flex gap-2 w-full">
              <el-input
                v-model="form.emailCode"
                placeholder="邮箱验证码"
                prefix-icon="Key"
                class="flex-1"
                maxlength="6"
              />
              <el-button
                :disabled="emailCodeCountdown > 0"
                @click="handleSendEmailCode"
              >
                {{ emailCodeCountdown > 0 ? `${emailCodeCountdown}s` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="w-full"
              :loading="loading"
              @click="handleRegister"
            >
              注册
            </el-button>
          </el-form-item>
        </el-form>

        <div class="text-center text-gray-400">
          已有账号？
          <router-link to="/login" class="text-primary hover:underline">
            立即登录
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
