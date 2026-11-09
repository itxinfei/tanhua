import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(import.meta.dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 手动分包：大依赖独立成块。echarts 仅 dashboard、wukongim 仅客服工作台使用，
        // 独立后配合路由懒加载实现按需拉取；EP/vue 生态独立成块提升浏览器缓存命中率
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          const modulePath = id.replace(/\\/g, '/')
          // zrender 是 echarts 的渲染底座，必须同包，避免运行时跨块解析失败
          if (/node_modules\/(?:echarts|zrender)\//.test(modulePath)) return 'vendor-echarts'
          if (/node_modules\/wukongimjssdk\//.test(modulePath)) return 'vendor-im'
          if (/node_modules\/(?:element-plus|@element-plus)\//.test(modulePath)) return 'vendor-element-plus'
          if (/node_modules\/(?:vue|vue-router|pinia|@vue)\//.test(modulePath)) return 'vendor-vue'
          return 'vendor'
        },
      },
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': {
        // 后端网关部署在 192.168.20.133:8090（见 docs/部署文档.md）
        target: 'http://192.168.20.133:8090',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})