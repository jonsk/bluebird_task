import { expect, test } from '@playwright/test'
import { freezeTime, goAdmin, gotoLogin, nav } from './helpers'

/**
 * 权限分支 e2e（依赖 mock 多账号身份 `src/mocks/identity.ts`）
 *
 * 覆盖此前只能靠后端矩阵脚本验证的三条前端表现：
 *   P-01 非管理员直接访问 /admin/notes → 路由守卫跳 403 并保留来源
 *   P-02 普通用户左栏**没有**「全部日志」「下属日志」入口
 *   P-03 无下属权限时直接访问 /notes/subordinate → 403 空态且文案提示无权限
 *   P-04 ADMIN 从后台进入「全部日志」；P-05 审计员无后台入口
 *
 * 说明：mock 只做权限门控，数据归属仍是 admin 视角，因此这里只断言权限行为，不断言列表内容。
 */

async function loginAs(page: import('@playwright/test').Page, username: string, password: string) {
  await gotoLogin(page)
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/index$/)
}

test.beforeEach(async ({ page }) => {
  await freezeTime(page)
})

test.describe('工作日志 · 权限分支', () => {
  test('P-01 非管理员访问后台「全部日志」→ 路由守卫跳 403 并保留来源', async ({ page }) => {
    await loginAs(page, 'common', 'Test@1234')
    await page.goto('/admin/notes')
    // routes.ts 里 /admin/notes 的 meta.roles=['ADMIN'] → 守卫重定向到 /403?from=...
    await expect(page).toHaveURL(/\/403/)
    await expect(page.locator('.error-page h1')).toHaveText('403')
    await expect(page.locator('.error-page__from')).toContainText('/admin/notes')
  })

  test('P-02 普通用户左栏无「全部日志 / 下属日志」入口', async ({ page }) => {
    await loginAs(page, 'common', 'Test@1234')
    await page.goto('/notes')
    await expect(page.locator('[data-test="nav-note-mine"]')).toBeVisible()
    await expect(page.locator('[data-test="nav-note-all"]')).toHaveCount(0)
    await expect(page.locator('[data-test="nav-note-subordinate"]')).toHaveCount(0)
    await expect(page.locator('[data-test="nav-note-grants"]')).toBeVisible()
  })

  test('P-03 无下属权限访问「员工日志」→ 403 空态 + 无权限文案', async ({ page }) => {
    await loginAs(page, 'common', 'Test@1234')
    await page.goto('/notes/subordinate')
    const empty = page.locator('[data-test="note-page-empty"]')
    await expect(empty).toBeVisible()
    await expect(empty).toContainText('未开启「上级可读员工日志」')
    await expect(page.locator('[data-test="note-list-error"]')).toHaveCount(0)
    // 403 下不再叠加「标签加载失败」
    await expect(page.locator('.note-page')).not.toContainText('标签加载失败')
  })

  test('P-04 ADMIN 从后台进入「全部日志」可正常加载', async ({ page }) => {
    await loginAs(page, 'admin', 'admin123')
    await goAdmin(page, '/admin/notes')
    await expect(page).toHaveURL(/\/admin\/notes$/)
    await expect(page.locator('.note-card').first()).toBeVisible()
    await expect(page.locator('[data-test="note-page-empty"]')).toHaveCount(0)
  })

  test('P-05 审计员无「全部日志」，被授权日志正常', async ({ page }) => {
    await loginAs(page, 'auditor', 'Test@1234')
    await page.goto('/notes')
    await expect(page.locator('[data-test="nav-note-all"]')).toHaveCount(0)
    await nav(page, 'note-shared').click()
    await expect(page).toHaveURL(/\/notes\/shared$/)
    await expect(page.locator('[data-test="note-list-error"]')).toHaveCount(0)
  })
})
