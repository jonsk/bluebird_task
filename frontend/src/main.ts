import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@/styles/index.css'
import App from './App.vue'
import { router, setupGuard } from './router'
import { setupDirectives } from './directives/permission'

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
  app.use(createPinia())
  // TODO(M0→M1): 按需引入 Element Plus（unplugin），现为整包引入；见 03 §3.1/R8
  app.use(ElementPlus)
  app.use(router)
  setupDirectives(app)
  setupGuard()
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
