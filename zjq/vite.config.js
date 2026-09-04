import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本工程既可独立运行，也可作为模块合并进 erp-web。
// 独立运行默认端口 5199；
// 若需对接真实后端（Spring Boot 8080），保持 /api 代理即可，无需改动代码。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5199,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    // 产出物放在 zjq/dist；若合并进主工程会随主工程统一打包
  }
})
