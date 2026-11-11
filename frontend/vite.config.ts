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

/**
 * 生产构建注入 CSP meta，覆盖「dist 被单独静态托管」的场景。
 *
 * 单 jar 部署时后端 SecurityConfig 已下发同样的响应头，此时 header 与 meta 取交集，
 * 内容完全一致所以不会收紧；独立托管（Nginx / CDN / file://）没有后端头，这层 meta
 * 就是唯一兜底。必须与后端 `SecurityConfig.java:70` 的指令逐字一致。
 *
 * 只在 build 生效：dev 下 Vite HMR 依赖 ws:// 与内联脚本，注入会直接打断开发服务器。
 */
const CSP = [
  "default-src 'self'",
  // blob: 供前端 markdownImages 把鉴权图片转成 URL.createObjectURL 后 <img src=blob:…> 渲染，缺则会整片图裂
  "img-src 'self' data: blob:",
  "style-src 'self' 'unsafe-inline'",
  "script-src 'self'",
].join('; ')

function injectCspMeta(): Plugin {
  return {
    name: 'inject-csp-meta',
    apply: 'build',
    transformIndexHtml(html) {
      const meta = `    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`
      return html.replace('    <meta charset="UTF-8" />', `    <meta charset="UTF-8" />\n${meta}`)
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
    injectCspMeta(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // fixtures 直连：docs/frontend-baseline/fixtures 位于 frontend/ 之外的仓根，需显式放行
    fs: {
      allow: [fileURLToPath(new URL('..', import.meta.url))],
    },
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
