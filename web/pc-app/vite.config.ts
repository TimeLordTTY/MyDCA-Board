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
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8766',
        changeOrigin: true,
      },
    },
  },
})
