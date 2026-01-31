import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5174,
    host: true,
    open: true,
    proxy: {
      // TVBox采集源和首页内容管理 -> jelly-film (9201)
      '/api/admin/tvbox-source': {
        target: 'http://localhost:9201',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/api/admin/homepage': {
        target: 'http://localhost:9201',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 影片相关 -> jelly-film (9201)
      '/api/film': {
        target: 'http://localhost:9201',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/api/admin/film': {
        target: 'http://localhost:9201',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // AI相关 -> jelly-ai (9500) 
      '/api/ai': {
        target: 'http://localhost:9500',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 社区模块 -> jelly-community (9301)
      '/api/post': {
        target: 'http://localhost:9301',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/api/comment': {
        target: 'http://localhost:9301',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 认证模块 -> jelly-auth (9100)
      '/api/auth': {
        target: 'http://localhost:9100',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 其他admin请求 -> jelly-admin (9600)
      '/api/admin': {
        target: 'http://localhost:9600',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 默认其他请求 -> gateway (8080)
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    },
    cors: true
  },
  build: {
    outDir: 'dist',
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    }
  }
})
