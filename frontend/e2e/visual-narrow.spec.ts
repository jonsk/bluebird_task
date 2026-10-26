import { expect, test } from '@playwright/test'
import { freezeTime, goView, login } from './helpers'

/**
 * 视觉回归基线 · 补充场景（1280×800 窄屏 + 空态 + 行内编辑 + 授权页）
 *
 * 与 1440×900 主基线同源（同一 MSW fixtures + 冻结时间），用于守住：
 * 1) 最小宽度 1280 下无横向溢出、列表/右栏比例正确；
 * 2) 空态（筛选无结果）不塌陷、分页不出现；
 * 3) 行内编辑宿主（我的日志）展开后的编辑器高度与按钮位置；
 * 4) 授权管理列表的三态标签。
 *
 * 更新基线：`pnpm test:e2e:update`
 */
test.describe('视觉回归基线 · 窄屏与状态（1280×800）', () => {
  test.use({ viewport: { width: 1280, height: 800 } })

  test.beforeEach(async ({ page }) => {
    await freezeTime(page)
  })

  test('我的日志（1280 窄屏）', async ({ page }) => {
    await login(page)
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)
    await expect(page).toHaveScreenshot('notes-home-1280x800.png')
  })

  test('日志详情（1280 窄屏，正文/引用/附件）', async ({ page }) => {
    await login(page)
    await page.goto('/notes/1001')
    await expect(page.locator('[data-test="note-detail-content"]')).toBeVisible()
    await expect(page).toHaveScreenshot('notes-detail-1280x800.png')
  })

  test('筛选空态（无分页、不塌陷）', async ({ page }) => {
    await login(page)
    await page.goto('/notes')
    await page.locator('[data-test="note-filter-bar"] input').first().fill('绝不可能匹配的关键词zzz')
    await expect(page.locator('[data-test="note-list-empty"]')).toBeVisible()
    await expect(page.locator('.note-list__pager')).toHaveCount(0)
    await expect(page).toHaveScreenshot('notes-empty-1280x800.png')
  })

  test('行内编辑（我的日志宿主）', async ({ page }) => {
    await login(page)
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1001"] [data-test="note-card-edit"]').click()
    await expect(page.locator('[data-test="note-inline-editor"]')).toBeVisible()
    await expect(page).toHaveScreenshot('notes-inline-editor-1280x800.png')
  })

  test('授权管理（状态徽标列表）', async ({ page }) => {
    await login(page)
    await goView(page, 'note-grants', /\/notes\/grants$/)
    await expect(page.locator('[data-test="grant-table"] .el-table__row').first()).toBeVisible()
    await expect(page).toHaveScreenshot('notes-grants-1280x800.png')
  })
})
