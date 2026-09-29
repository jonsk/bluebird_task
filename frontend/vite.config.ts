import { fileURLToPath, URL } from 'node:url'
import { existsSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const rootDir = fileURLToPath(new URL('.', import.meta.url))

/**
 * DoD 门禁（03 §2.8/R14）：生产 dist 不得含 mockServiceWorker.js。
 * 该文件由 `msw init public` 生成并被 publicDir 拷贝，构建后强制剔除。
 */
function stripMswWorker(): Plugin {
  return {
    name: 'strip-msw-worker',
    apply: 'build',
    closeBundle() {
      const target = resolve(rootDir, 'dist/mockServiceWorker.js')
      if (existsSync(target)) {
        rmSync(target)
      }
    },
  }
}

// 单制品部署（ADR-009）：构建产物 frontend/dist 于打包期注入后端 static/。
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入（03 §3.1/R8）：组件与样式仅打包实际使用者
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'auto-imports.d.ts',
      eslintrc: { enabled: false },
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'components.d.ts',
      dirs: [],
    }),
    stripMswWorker(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 未启用 MSW 时，开发直连本地后端
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
  },
})
