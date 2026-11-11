import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 官方暗色变量（作用域 html.dark）：必须排在自有令牌之前，
// 这样 tokens.css 里的 --el-* / 自有 --bg-* 覆盖仍然生效，其余语义色（红黄绿提示）由官方补齐。
import 'element-plus/theme-chalk/dark/css-vars.css'
import '@/styles/index.css'
// 命令式组件样式必须显式引入：ElMessage / ElMessageBox 由 JS `import` 使用（而非模板组件），
// unplugin 的按需解析器（Components/AutoImport）只处理模板用法，不会注入它们的 CSS。
// 缺失时确认框完全没有样式，会以文档流形式渲染到页面左上/左下角（用户反馈 #2「删除框在左侧最下方」、#9「确认框在最左上角」）。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import { router, setupGuard } from './router'
import { setupDirectives } from './directives/permission'
import { useAppStore } from '@/stores/app'

/**
 * Element Plus 按需引入（unplugin-vue-components/resolvers 自动装配，03 §3.1/R8）——
 * 不再 `app.use(ElementPlus)` 整包引入；组件与样式由编译器按实际使用注入。
 */

/**
 * 仅 dev/test 且显式开启时装载 MSW（03 §2.6）。
 * `import.meta.env.DEV` 在生产被静态替换为 false，动态 import 被 tree-shake，
 * 生产包不挟持真实请求、不含 mockServiceWorker.js（DoD，R14）。
 */
async function enableMocking(): Promise<void> {
  if (!import.meta.env.DEV || import.meta.env.VITE_USE_MOCK !== 'true') return
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

async function bootstrap(): Promise<void> {
  await enableMocking()
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  // 应用持久化主题（<html data-theme>）；index.html 内联脚本已预设，此处统一状态兜底。
  useAppStore()
  app.use(router)
  setupDirectives(app)
  setupGuard()
  await router.isReady()
  app.mount('#app')
}

void bootstrap().catch((e: unknown) => {
  // 挂载失败时 Element Plus 尚未就绪，只能用控制台兜底，避免静默白屏。
  console.error('[bluebird] 应用启动失败', e)
})
