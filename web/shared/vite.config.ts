import { defineConfig } from 'vite'
import { resolve } from 'path'
import dts from 'vite-plugin-dts'

export default defineConfig({
  plugins: [
    dts({
      insertTypesEntry: true,
    }),
  ],
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'WealthHubShared',
      formats: ['es'],
      fileName: 'index',
    },
    rollupOptions: {
      // 将 vue、pinia、axios 都标记为外部依赖
      // 确保 shared 包使用与主应用相同的 vue/pinia 实例
      // 否则 Pinia store 会因为 vue 实例不匹配而报错：Cannot read properties of undefined (reading '_s')
      external: ['vue', 'axios', 'pinia'],
    },
  },
})
