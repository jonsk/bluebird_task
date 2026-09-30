import { expect, test } from '@playwright/test'
import { card, composer, freezeTime, goView, gotoLogin, login } from './helpers'

/**
 * 视觉回归基线（03 §2.5.1，1440×900 / 固定 MSW fixtures / 冻结时间 → 确定性）。
 *
 * 与旧前端黄金截图（`docs/frontend-baseline/screenshots/`，按用户指令不入库）不同：
 * 新前端为**回归基线**，落 `e2e/__screenshots__/` 并**入库**，后续 PR 同视口对比（像素 diff）。
 * 更新基线：`pnpm test:e2e:update`。
 *
 * ⚠️ 容差会吸收浅色文本差异（`maxDiffPixelRatio: 0.02` + Playwright 默认 `threshold: 0.2`），
 * 因此**左栏/布局类改动必须显式重生基线**（必要时先删除旧 PNG）。
 */
test.describe('视觉回归基线（1440×900）', () => {
  test.beforeEach(async ({ page }) => {
    await freezeTime(page)
  })

  test('登录页', async ({ page }) => {
    await gotoLogin(page)
    await expect(page.locator('.login-box')).toBeVisible()
    await expect(page).toHaveScreenshot('login-account-1440x900.png')
  })

  test('左栏（旧 .left-box 对齐）', async ({ page }) => {
    await login(page)
    await expect(page.locator('.left-box')).toHaveScreenshot('component-sidebar-leftbox-1440x900.png')
  })

  test('我的一天（逾期/临期/已完成态）', async ({ page }) => {
    await login(page)
    await expect(page.locator('.bb-task-card')).toHaveCount(6)
    await expect(page).toHaveScreenshot('tasklist-day-1440x900.png')
  })

  test('全部任务（含右栏日历 + 详情面板空态）', async ({ page }) => {
    await login(page)
    await goView(page, '/allTask', /\/allTask$/)
    await expect(page.locator('.bb-task-card')).toHaveCount(7)
    await expect(page).toHaveScreenshot('tasklist-all-1440x900.png')
  })

  test('任务详情面板（右侧内联）', async ({ page }) => {
    await login(page)
    await card(page, '提交季度报告').click()
    await expect(page.locator('.dialog-right-box .drbb-subtask')).toHaveCount(3)
    await expect(page).toHaveScreenshot('task-detail-panel-1440x900.png')
  })

  test('新建任务编辑器（内联展开）', async ({ page }) => {
    await login(page)
    await composer(page).getByPlaceholder('添加任务').click()
    await expect(composer(page).locator('.t-b-i-box-config')).toBeVisible()
    await expect(composer(page)).toHaveScreenshot('task-composer-1440x900.png')
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
    await expect(page.locator('.el-tab-pane:visible .el-table__row')).toHaveCount(2)
    await expect(page).toHaveScreenshot('admin-audit-1440x900.png')
  })
})
