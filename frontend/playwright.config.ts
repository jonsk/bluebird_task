import { defineConfig } from '@playwright/test'

/**
 * 新前端 E2E + 视觉回归基线（03 §2.3.3 / §2.5）。
 *
 * - 目标：`frontend/` dev server（MSW 直连 `docs/frontend-baseline/fixtures/api/**`），无需后端。
 * - 浏览器：默认复用系统 **Microsoft Edge**（`channel: 'msedge'`，无需下载 Chromium）；可用 `E2E_CHANNEL` 覆盖。
 * - 视口固定 1440×900、时区 Asia/Shanghai、`page.clock` 冻结点时间（fixtures 以 2026-09 为基线）。
 * - 视觉基线落 `e2e/__screenshots__/`（**入库**，与旧前端黄金截图不同——后者按用户指令不入库）。
 */
const PORT = Number(process.env.E2E_PORT ?? 4173)
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`
// 本地复用系统 Edge（免下载）；CI 用随包 Chromium（需 `playwright install chromium`）。
const channel = process.env.E2E_CHANNEL ?? (process.env.CI ? undefined : 'msedge')

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  timeout: 30_000,
  expect: {
    timeout: 8_000,
    // 视觉回归容差：保留 Playwright 默认逐像素 threshold(0.2，吸收字体/抗锯齿差异，
    // 保证 Edge(本地)/Chromium(CI) 一致) + 2% 差异像素比。
    // ⚠️ 代价：浅色文本类改动可落在 threshold 之下而不触发失败（曾出现「基线未含分类树但对比通过」）。
    // 因此**左栏/布局类改动必须显式重生基线**：`pnpm test:e2e:update`（必要时先删除旧 PNG 强制重写）。
    toHaveScreenshot: { maxDiffPixelRatio: 0.02, animations: 'disabled' },
  },
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
  snapshotPathTemplate: '{testDir}/__screenshots__/{arg}{ext}',
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: `pnpm dev --port ${PORT} --strictPort`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
