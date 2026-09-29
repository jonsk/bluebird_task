import type { App } from 'vue'

declare global {
  interface ImportMetaEnv {
    readonly VITE_API_BASE_URL: string
    readonly VITE_USE_MOCK: string
  }
  interface ImportMeta {
    readonly env: ImportMetaEnv
  }
}

export type AppInstance = App
