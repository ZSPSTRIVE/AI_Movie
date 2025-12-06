<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCaptcha, checkEmailVerify, sendLoginEmailCode } from '@/api/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

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
  <div class="min-h-screen bg-nb-bg flex items-center justify-center p-4 relative overflow-hidden">
    <!-- 装饰元素 -->
    <div class="absolute top-10 left-10 w-24 h-24 bg-pop-yellow border-3 border-black rounded-full animate-bounce-in" />
    <div class="absolute bottom-20 right-20 w-16 h-16 bg-pop-pink border-3 border-black rotate-45 animate-bounce-in" style="animation-delay: 0.2s" />
    <div class="absolute top-1/3 right-10 w-12 h-12 bg-pop-blue border-3 border-black rounded-lg animate-bounce-in" style="animation-delay: 0.4s" />
    
    <div class="w-full max-w-md relative z-10">
      <!-- Logo - Neo-Brutalism -->
      <div class="text-center mb-8 animate-bounce-in">
        <div class="inline-block bg-pop-yellow border-3 border-black shadow-brutal rounded-2xl px-6 py-4 mb-4">
          <h1 class="text-5xl font-black text-black uppercase tracking-tight">果冻影院</h1>
        </div>
        <p class="text-lg font-bold text-nb-text">影视 + 社交 + AI 一体化平台</p>
      </div>

      <!-- Login Form - Neo-Brutalism -->
      <div class="bg-white border-3 border-black shadow-brutal rounded-2xl p-8 animate-bounce-in" style="animation-delay: 0.1s">
        <div class="bg-pop-blue border-3 border-black rounded-xl px-4 py-2 mb-6 inline-block">
          <h2 class="text-2xl font-black text-white uppercase">登录</h2>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              prefix-icon="User"
              class="nb-input"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              prefix-icon="Lock"
              show-password
              class="nb-input"
            />
          </el-form-item>

          <!-- 图片验证码 -->
          <el-form-item prop="captcha">
            <div class="flex gap-2 w-full">
              <el-input
                v-model="form.captcha"
                placeholder="验证码"
                prefix-icon="Picture"
                class="nb-input flex-1"
                maxlength="4"
              />
              <div 
                class="w-28 h-10 border-3 border-black rounded-lg overflow-hidden cursor-pointer bg-white flex items-center justify-center"
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

          <!-- 邮箱验证码（异常登录时显示） -->
          <el-form-item v-if="needEmailVerify">
            <div class="w-full">
              <div class="text-sm text-orange-600 mb-2 p-2 bg-orange-50 border border-orange-200 rounded">
                ⚠️ 检测到异常登录，请验证邮箱 {{ maskedEmail }}
              </div>
              <div class="flex gap-2">
                <el-input
                  v-model="form.emailCode"
                  placeholder="邮箱验证码"
                  prefix-icon="Message"
                  class="nb-input flex-1"
                  maxlength="6"
                />
                <el-button
                  :disabled="emailCodeCountdown > 0"
                  class="!h-10 !border-3 !border-black"
                  @click="sendEmailCode"
                >
                  {{ emailCodeCountdown > 0 ? `${emailCodeCountdown}s` : '发送验证码' }}
                </el-button>
              </div>
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="w-full !h-12 !text-lg !font-black !uppercase !bg-pop-green hover:!bg-pop-yellow !text-black !border-3 !border-black !shadow-brutal hover:!translate-x-1 hover:!-translate-y-1 transition-all"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="text-center mt-6 p-4 bg-nb-bg border-3 border-black rounded-xl">
          <span class="font-bold text-nb-text">还没有账号？</span>
          <router-link to="/register" class="font-black text-pop-blue hover:text-pop-purple underline decoration-3 underline-offset-4 ml-2">
            立即注册
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>
