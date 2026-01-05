import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 生产环境基础路径，如果部署在子目录，修改为 '/子目录名/'
  base: '/',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    // 使用 esbuild 压缩（更快，Vite 默认）
    minify: 'esbuild',
    // 如果需要移除 console，可以安装 terser 并改用 terser
    // minify: 'terser',
    // terserOptions: {
    //   compress: {
    //     drop_console: true,
    //     drop_debugger: true
    //   }
    // }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

