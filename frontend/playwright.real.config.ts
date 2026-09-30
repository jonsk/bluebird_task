import { defineConfig } from '@playwright/test'

/**
 * 真实后端联调配置（**选入式**，默认 CI 不跑）。
 *
 * 与 `playwright.config.ts`（MSW fixtures）的区别：
 * - **不启动 webServer**：需要你先启动 jar（默认 `http://127.0.0.1:8080`）。
 * - 不冻结时间、不做像素比对：这里验证的是真实后端的接口契约与交互链路。
 *
 * 运行：
 *   pnpm test:e2e:real                # API + UI
 *   pnpm test:e2e:real -- api         # 只跑 API
 *   E2E_REAL_BASE_URL=http://host:8080 pnpm test:e2e:real
 */
const baseURL = process.env.E2E_REAL_BASE_URL ?? 'http://127.0.0.1:8080'
// 本地复用系统 Edge（免下载）；CI/无 Edge 时用随包 Chromium
const channel = process.env.E2E_CHANNEL ?? (process.env.CI ? undefined : 'msedge')

export default defineConfig({
  testDir: './e2e-real',
  outputDir: './test-results-real',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL,
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    channel,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
