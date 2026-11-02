import { defineConfig } from '@playwright/test'

/**
 * 新前端功能 E2E（03 §2.3.3）。
 *
 * - 目标：`frontend/` dev server（MSW 直连 `src/mocks/fixtures/api/**`），无需后端。
 * - 浏览器：默认复用系统 **Microsoft Edge**（`channel: 'msedge'`，无需下载 Chromium）；可用 `E2E_CHANNEL` 覆盖。
 * - 视口固定 1440×900、时区 Asia/Shanghai、`page.clock` 冻结点时间（fixtures 以 2026-09 为基线）。
 * - 不做截图回归（视觉基线已于 2026-11-02 下线）：断言以 DOM/文案/路由为主；失败时保留截图仅供排查。
 */
const PORT = Number(process.env.E2E_PORT ?? 4173)
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`
// 本地复用系统 Edge（免下载）；CI 用随包 Chromium（需 `playwright install chromium`）。
const channel = process.env.E2E_CHANNEL ?? (process.env.CI ? undefined : 'msedge')

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  timeout: 30_000,
  expect: { timeout: 8_000 },
  fullyParallel: false,
  workers: 1,
  retries: 1,
  reporter: [['list'], ['json', { outputFile: 'e2e/.report/results.json' }]],
  use: {
    baseURL,
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    channel,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: `pnpm dev --port ${PORT} --strictPort`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
