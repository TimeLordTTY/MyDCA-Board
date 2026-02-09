import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  // 生产环境部署在 http://域名/wealth-hub-mobile/ 下
  base: '/wealth-hub-mobile/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
    dedupe: ['vue', 'pinia', 'vue-router'],
    // npm workspaces 模式下，需要从根目录查找依赖
    preserveSymlinks: false,
  },
  server: {
    port: 3001, // Mobile端使用不同端口
    proxy: {
      '/api': {
        target: 'http://localhost:8766',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        manualChunks: {
          'vant': ['vant'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
        },
      },
    },
  },
})
