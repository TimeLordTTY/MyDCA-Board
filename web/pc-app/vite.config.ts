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
    // 确保 vue 和 pinia 只使用单一实例（从 workspace 根 node_modules 解析）
    dedupe: ['vue', 'pinia', 'vue-router'],
    // npm workspaces 模式下，需要从根目录查找依赖
    preserveSymlinks: false,
  },
  optimizeDeps: {
    // 确保这些依赖被预构建并共享
    include: ['vue', 'pinia', '@wealth-hub/shared'],
    // npm workspaces 模式下，从根目录查找依赖
    force: false,
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
