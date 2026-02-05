import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  // 生产环境部署在 http://域名/wealth-hub/ 下
  // 因此前端静态资源需要带上 /wealth-hub/ 前缀
  base: '/wealth-hub/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // 强制所有模块（包括 @wealth-hub/shared）使用同一份 vue 和 pinia
      // 这是解决 "Cannot read properties of undefined (reading '_s')" 错误的关键
      'vue': resolve(__dirname, 'node_modules/vue'),
      'pinia': resolve(__dirname, 'node_modules/pinia'),
    },
    // 确保 vue 和 pinia 只使用单一实例
    dedupe: ['vue', 'pinia'],
  },
  optimizeDeps: {
    // 确保这些依赖被预构建并共享
    include: ['vue', 'pinia', '@wealth-hub/shared'],
  },
  build: {
    // 确保构建时也遵循 dedupe 配置
    commonjsOptions: {
      include: [/node_modules/],
    },
  },
  server: {
    port: 3000,
    proxy: {
      // 本地开发代理：/wealth-hub/api/ -> 后端 /api/
      // 与生产环境 nginx 配置保持一致
      '/wealth-hub/api': {
        target: 'http://localhost:8766',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/wealth-hub\/api/, '/api'),
      },
    },
  },
})
