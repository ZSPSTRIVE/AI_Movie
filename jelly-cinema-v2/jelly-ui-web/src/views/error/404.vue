<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="min-h-screen bg-dark-bg flex items-center justify-center">
    <div class="text-center">
      <h1 class="text-9xl font-bold text-primary">404</h1>
      <p class="text-2xl text-gray-400 mt-4">页面不存在</p>
      <p class="text-gray-500 mt-2">抱歉，您访问的页面不存在或已被删除</p>
      <el-button type="primary" class="mt-8" @click="goHome">
        返回首页
      </el-button>
    </div>
  </div>
</template>
