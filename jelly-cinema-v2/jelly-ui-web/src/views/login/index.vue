<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    try {
      await userStore.doLogin(form)
      ElMessage.success('登录成功')
      
      const redirect = route.query.redirect as string
      router.push(redirect || '/')
    } catch (error) {
      // Error handled by interceptor
    } finally {
      loading.value = false
    }
  })
}
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
