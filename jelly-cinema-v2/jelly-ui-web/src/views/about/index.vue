<script setup lang="ts">
import { ref } from 'vue'

const version = ref('2.0.0')
const buildDate = ref('2025-12-03')

const features = [
  { icon: '🎬', title: '高清影视', desc: '海量正版高清影视资源，流畅播放体验' },
  { icon: '💬', title: '即时聊天', desc: '与好友实时聊天，分享观影心得' },
  { icon: '🤖', title: 'AI 助手', desc: '智能 AI 助手，为您推荐个性化内容' },
  { icon: '📝', title: '社区互动', desc: '发帖评论，与影迷交流讨论' },
  { icon: '📚', title: 'AI 小说', desc: 'AI 驱动的小说创作与阅读体验' },
  { icon: '🔒', title: '安全可靠', desc: '多重安全防护，保护您的隐私' }
]

const team = [
  { name: 'Jelly', role: '项目负责人', avatar: '🍮' },
  { name: 'AI Assistant', role: '技术支持', avatar: '🤖' }
]
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-8">
    <!-- 头部 -->
    <div class="bg-pop-blue border-3 border-black rounded-2xl p-8 text-center shadow-brutal">
      <div class="text-6xl mb-4">🍮</div>
      <h1 class="text-4xl font-black text-white uppercase mb-2">Jelly Cinema</h1>
      <p class="text-white/80 text-lg">果冻影院 - 您的专属影视娱乐平台</p>
      <div class="mt-4 flex justify-center gap-4">
        <span class="bg-white border-2 border-black rounded-lg px-4 py-2 font-bold">
          版本 v{{ version }}
        </span>
        <span class="bg-pop-yellow border-2 border-black rounded-lg px-4 py-2 font-bold">
          {{ buildDate }}
        </span>
      </div>
    </div>

    <!-- 功能特点 -->
    <div class="bg-white border-3 border-black rounded-2xl p-6 shadow-brutal">
      <h2 class="text-2xl font-black mb-6 flex items-center">
        <span class="bg-pop-orange border-2 border-black rounded-lg px-4 py-2 mr-3">✨</span>
        功能特点
      </h2>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="feature in features"
          :key="feature.title"
          class="bg-nb-bg border-2 border-black rounded-xl p-4 hover:bg-pop-yellow/20 transition-colors"
        >
          <div class="text-3xl mb-2">{{ feature.icon }}</div>
          <h3 class="font-bold text-lg mb-1">{{ feature.title }}</h3>
          <p class="text-gray-600 text-sm">{{ feature.desc }}</p>
        </div>
      </div>
    </div>

    <!-- 技术栈 -->
    <div class="bg-white border-3 border-black rounded-2xl p-6 shadow-brutal">
      <h2 class="text-2xl font-black mb-6 flex items-center">
        <span class="bg-pop-purple text-white border-2 border-black rounded-lg px-4 py-2 mr-3">🛠️</span>
        技术栈
      </h2>
      <div class="flex flex-wrap gap-3">
        <span class="bg-green-100 border-2 border-black rounded-lg px-4 py-2 font-bold">Vue 3</span>
        <span class="bg-blue-100 border-2 border-black rounded-lg px-4 py-2 font-bold">TypeScript</span>
        <span class="bg-cyan-100 border-2 border-black rounded-lg px-4 py-2 font-bold">Tailwind CSS</span>
        <span class="bg-purple-100 border-2 border-black rounded-lg px-4 py-2 font-bold">Element Plus</span>
        <span class="bg-red-100 border-2 border-black rounded-lg px-4 py-2 font-bold">Spring Boot</span>
        <span class="bg-orange-100 border-2 border-black rounded-lg px-4 py-2 font-bold">Spring Cloud</span>
        <span class="bg-yellow-100 border-2 border-black rounded-lg px-4 py-2 font-bold">MySQL</span>
        <span class="bg-pink-100 border-2 border-black rounded-lg px-4 py-2 font-bold">Redis</span>
        <span class="bg-indigo-100 border-2 border-black rounded-lg px-4 py-2 font-bold">RabbitMQ</span>
        <span class="bg-gray-100 border-2 border-black rounded-lg px-4 py-2 font-bold">WebSocket</span>
      </div>
    </div>

    <!-- 团队 -->
    <div class="bg-white border-3 border-black rounded-2xl p-6 shadow-brutal">
      <h2 class="text-2xl font-black mb-6 flex items-center">
        <span class="bg-pop-green border-2 border-black rounded-lg px-4 py-2 mr-3">👥</span>
        开发团队
      </h2>
      <div class="flex justify-center gap-8">
        <div
          v-for="member in team"
          :key="member.name"
          class="text-center"
        >
          <div class="w-20 h-20 bg-nb-bg border-3 border-black rounded-full flex items-center justify-center text-4xl mb-3 mx-auto">
            {{ member.avatar }}
          </div>
          <h3 class="font-bold text-lg">{{ member.name }}</h3>
          <p class="text-gray-500 text-sm">{{ member.role }}</p>
        </div>
      </div>
    </div>

    <!-- 版权信息 -->
    <div class="text-center text-gray-500 text-sm pb-8">
      <p>© 2025 Jelly Cinema. All rights reserved.</p>
      <p class="mt-1">Made with ❤️ by Jelly Team</p>
    </div>
  </div>
</template>
