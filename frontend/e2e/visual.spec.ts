import { expect, test } from '@playwright/test'
import { card, freezeTime, goView, gotoLogin, login } from './helpers'

/**
 * 视觉回归基线（03 §2.5.1，1440×900 / 固定 MSW fixtures / 冻结时间 → 确定性）。
 *
 * 与旧前端黄金截图（`docs/frontend-baseline/screenshots/`，按用户指令不入库）不同：
 * 新前端为**回归基线**，落 `e2e/__screenshots__/` 并**入库**，后续 PR 同视口对比（像素 diff）。
 * 更新基线：`pnpm test:e2e:update`。
 */
test.describe('视觉回归基线（1440×900）', () => {
  test.beforeEach(async ({ page }) => {
    await freezeTime(page)
  })

  test('登录页', async ({ page }) => {
    await gotoLogin(page)
    await expect(page.locator('.login-card')).toBeVisible()
    await expect(page).toHaveScreenshot('login-account-1440x900.png')
  })

  test('我的一天（逾期/临期/已完成态）', async ({ page }) => {
    await login(page)
    await expect(page.locator('.bb-task-card')).toHaveCount(6)
    await expect(page).toHaveScreenshot('tasklist-day-1440x900.png')
  })

  test('全部任务', async ({ page }) => {
    await login(page)
    await goView(page, '/allTask', /\/allTask$/)
    await expect(page.locator('.bb-task-card')).toHaveCount(7)
    await expect(page).toHaveScreenshot('tasklist-all-1440x900.png')
  })

  test('任务详情抽屉', async ({ page }) => {
    await login(page)
    await card(page, '提交季度报告').click()
    await expect(page.locator('.el-drawer:visible .bb-detail__subtask')).toHaveCount(3)
    await expect(page).toHaveScreenshot('task-detail-drawer-1440x900.png')
  })

  test('新建任务编辑器', async ({ page }) => {
    await login(page)
    await page.getByRole('button', { name: '新建任务' }).click()
    await expect(page.locator('.el-dialog:visible')).toBeVisible()
    await expect(page).toHaveScreenshot('task-composer-1440x900.png')
  })

  test('日历（周期实例展开）', async ({ page }) => {
    await login(page)
    await goView(page, '/calendar', /\/calendar$/)
    await expect(page.locator('.calendar-view__task')).toHaveCount(3)
    await expect(page).toHaveScreenshot('calendar-1440x900.png')
  })

  test('用户管理', async ({ page }) => {
    await login(page)
    await goView(page, '/admin/users', /\/admin\/users$/)
    await expect(page.locator('.el-table__row')).toHaveCount(4)
    await expect(page).toHaveScreenshot('admin-users-1440x900.png')
  })

  test('审计日志', async ({ page }) => {
    await login(page)
    await goView(page, '/admin/audit', /\/admin\/audit$/)
    await expect(page.locator('.el-table__row')).toHaveCount(2)
    await expect(page).toHaveScreenshot('admin-audit-1440x900.png')
  })
})
