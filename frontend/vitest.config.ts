import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['tests/unit/**/*.spec.ts'],
    passWithNoTests: false,
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'json-summary'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.{ts,vue}'],
      // 纯声明 / 桩数据 / 入口装配不计入：它们没有可分支的逻辑，
      // 算进去只会稀释比例，让门禁失去信号。
      exclude: [
        'src/types/**',
        'src/mocks/**',
        'src/**/*.d.ts',
        'src/main.ts',
        'src/App.vue',
        'src/router/**',
      ],
      // 门禁只防「回落」，不是质量目标：取实测基线略下方，正常重构不会误伤。
      // 2026-11 实测（已排除 types/mocks/main/router）：行 35.4% / 语句 35.4% / 函数 36.2% / 分支 70.8%。
      thresholds: {
        lines: 33,
        statements: 33,
        functions: 34,
        branches: 68,
      },
    },
  },
})
