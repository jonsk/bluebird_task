import { expect, test, type Page } from '@playwright/test'
import { freezeTime } from './helpers'

/**
 * 每周工作记录改版：
 * - 主区只有编辑器，没有主列表/搜索/「完成编辑」；
 * - Esc 不退出编辑；
 * - 首次进入打开当前周；右栏未来组为空时隐藏；
 * - 「+ 新建」可按默认下一周创建未来周记录。
 *
 * 直接以 `?redirect=/weekly-notes` 登录，避免登录后再 `page.goto`（mock 会话在内存中，整页刷新会丢）。
 */
async function loginToWeekly(page: Page): Promise<void> {
  await freezeTime(page)
  await page.goto('/login?redirect=/weekly-notes')
  await page.getByPlaceholder('用户名').fill('admin')
  await page.getByPlaceholder('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/weekly-notes$/)
}

/** 首屏请求偶尔早于 mock worker 就绪；页面提供重试按钮，这里只点一次。 */
async function waitForWeeklyReady(page: Page): Promise<void> {
  const editor = page.locator('[data-test="weekly-inline-editor"]')
  const retry = page.locator('[data-test="weekly-retry"]')
  await expect(editor.or(retry)).toBeVisible()
  if (await retry.isVisible()) await retry.click()
  await expect(editor).toBeVisible()
}

test.describe('每周工作记录', () => {
  test.beforeEach(async ({ page }) => {
    await loginToWeekly(page)
    await waitForWeeklyReady(page)
  })

  test('W-01 当前周自动打开，Esc 不退出且无主列表', async ({ page }) => {
    const editor = page.locator('[data-test="weekly-inline-editor"]')
    await expect(page.locator('[data-test="weekly-range"]')).toHaveText('2026-09-28 ~ 2026-10-04')
    await expect(page.locator('[data-test="note-inline-done"]')).toHaveCount(0)
    await expect(page.locator('.note-card')).toHaveCount(0)
    await expect(page.locator('[data-test="weekly-inline-status"]')).toBeVisible()

    const prose = editor.locator('.note-editor__prose')
    await expect(prose).toContainText('2026年9月28日 周一')
    await prose.click()
    await page.keyboard.press('Escape')
    await expect(editor).toBeVisible()
  })

  test('W-02 未来组为空时隐藏，新建下一周后出现并打开', async ({ page }) => {
    await expect(page.locator('[data-test="weekly-future-group"]')).toHaveCount(0)

    await page.locator('[data-test="weekly-create"]').click()
    const dialog = page.getByRole('dialog', { name: '新建未来周记录' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText('2026-10-05 ~ 2026-10-11')).toBeVisible()

    await page.locator('[data-test="weekly-create-submit"]').click()

    await expect(dialog).toBeHidden()
    const editor = page.locator('[data-test="weekly-inline-editor"]')
    await expect(page.locator('[data-test="weekly-range"]')).toHaveText('2026-10-05 ~ 2026-10-11')
    await expect(editor.locator('.note-editor__prose')).toContainText('2026年10月5日 周一')

    const future = page.locator('[data-test="weekly-future-group"]')
    await expect(future).toBeVisible()
    await expect(future.locator('.note-side__group-title')).toHaveText('未来')
    await expect(future.getByText('2026-10-05 ~ 2026-10-11')).toBeVisible()
  })
})
