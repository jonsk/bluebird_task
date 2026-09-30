import { expect, test } from '@playwright/test'
import { card, cardTitles, composer, detailPanel, freezeTime, goView, gotoLogin, login, nav } from './helpers'

/**
 * E2E 验收（新前端 · MSW 直连 `docs/frontend-baseline/fixtures/api/**`）。
 *
 * 场景编号对齐 `docs/frontend-baseline/e2e/scenarios.md`（E-01..E-17）。
 * 旧前端基线（前半段）见 `scripts/golden-capture/e2e-baseline.mjs`；本文件为后半段「新前端验收」。
 *
 * 选择器已按**旧系统对齐后的 UI**校准：无顶部 header（用户块在左栏 `.left-title__name`）、
 * 内联编辑器 `.t-b-input-box`、右侧内联详情面板 `.dialog-right-box`。
 */

test.beforeEach(async ({ page }) => {
  await freezeTime(page)
})

test('E-01 登录（账密）', async ({ page }) => {
  await login(page)
  await expect(page.locator('.left-title__name')).toHaveText('管理员')
  await expect(nav(page, '/index')).toHaveClass(/item-active/)
})

test('E-01b 登录失败提示且停留登录页', async ({ page }) => {
  await gotoLogin(page)
  await page.getByPlaceholder('用户名').fill('admin')
  await page.getByPlaceholder('密码').fill('wrong-password')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.locator('.el-message--error')).toContainText('用户名或密码错误')
  await expect(page).toHaveURL(/\/login$/)
})

test('E-02 外部登录默认关闭（provider=LOCAL）', async ({ page }) => {
  await gotoLogin(page)
  await expect(page.locator('.demo-tabs')).toHaveCount(0)
  await expect(page.getByText('企业微信')).toHaveCount(0)
})

test('E-03 六大视图切换：计数（fixtures count）与列表（fixtures list 过滤）一致', async ({ page }) => {
  await login(page)
  const views = [
    { href: '/index', url: /\/index$/, count: 4, cards: 6 },
    { href: '/myWeek', url: /\/myWeek$/, count: 5, cards: 6 },
    { href: '/myJoin', url: /\/myJoin$/, count: 2, cards: 2 },
    { href: '/myDo', url: /\/myDo$/, count: 3, cards: 0 },
    { href: '/myCollect', url: /\/myCollect$/, count: 1, cards: 1 },
    { href: '/allTask', url: /\/allTask$/, count: 9, cards: 7 },
  ]
  for (const v of views) {
    await expect(nav(page, v.href).locator('.layout__count')).toHaveText(String(v.count))
    await goView(page, v.href, v.url)
    await expect(cardTitles(page)).toHaveCount(v.cards)
  }
})

test('E-04 新建主任务（列表顶部内联编辑器）', async ({ page }) => {
  await login(page)
  const box = composer(page)
  await box.getByPlaceholder('添加任务').fill('E2E 新建任务')
  await box.getByRole('button', { name: '添加' }).click()
  await expect(page.locator('.el-message--success')).toBeVisible()
  await expect(card(page, 'E2E 新建任务')).toHaveCount(1)
})

test('E-05 子任务列表（详情面板，只读列举）', async ({ page }) => {
  await login(page)
  await card(page, '提交季度报告').click()
  const panel = detailPanel(page)
  await expect(panel).toBeVisible()
  await expect(panel.locator('.drbb-subtask')).toHaveCount(3)
  await expect(panel.locator('.drbb-subtask').first()).toContainText('收集数据')
})

test('E-06 完成任务（全部任务视图置已完成）', async ({ page }) => {
  await login(page)
  await goView(page, '/allTask', /\/allTask$/)
  await card(page, '提交季度报告').locator('.el-checkbox').click()
  await expect(card(page, '提交季度报告')).toHaveClass(/is-completed/)
})

test('E-07 收藏（出现在「我的收藏」）', async ({ page }) => {
  await login(page)
  await goView(page, '/allTask', /\/allTask$/)
  await card(page, '逾期演示任务').locator('.bb-task-card__op').first().click()
  await goView(page, '/myCollect', /\/myCollect$/)
  await expect(cardTitles(page)).toHaveCount(2)
  await expect(card(page, '逾期演示任务')).toHaveCount(1)
})

test('E-09 删除任务（二次确认，文案不可恢复）', async ({ page }) => {
  await login(page)
  await card(page, '逾期演示任务').locator('.bb-task-card__op').last().click()
  const box = page.locator('.el-message-box:visible')
  await expect(box).toContainText('不可恢复')
  await box.getByRole('button', { name: '删除' }).click()
  await expect(card(page, '逾期演示任务')).toHaveCount(0)
})

test('E-11 标签数据（fixtures 直连，顶部标签入口）', async ({ page }) => {
  await login(page)
  await page.locator('.r-b-t-r-item').click()
  const inputs = page.locator('.bb-tag-config__input input')
  await expect(inputs).toHaveCount(4) // 3 个 fixtures 标签 + 1 个新增输入框
  await expect(inputs.first()).toHaveValue('重要')
})

test('E-12 人员选择（fixtures 用户源，编辑器指派）', async ({ page }) => {
  await login(page)
  const box = composer(page)
  await box.getByPlaceholder('添加任务').click()
  await box.locator('[data-test="composer-assignee"]').click()
  const options = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
  await expect(options).toHaveCount(4)
  await expect(options.filter({ hasText: '张三' })).toHaveCount(1)
  await expect(options.filter({ hasText: '王五' })).toHaveCount(1)
})

test('E-13 附件上传（详情面板）', async ({ page }) => {
  await login(page)
  await card(page, '提交季度报告').click()
  const panel = detailPanel(page)
  await expect(panel.locator('.bb-attachments__item')).toHaveCount(1)
  await panel.locator('input[type=file]').setInputFiles({
    name: 'e2e-upload.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('bluebird e2e'),
  })
  await expect(page.locator('.el-message--success')).toContainText('上传成功')
  await expect(panel.locator('.bb-attachments__item')).toHaveCount(2)
})

test('E-14 用户管理（ADMIN）', async ({ page }) => {
  await login(page)
  await goView(page, '/admin/users', /\/admin\/users$/)
  await expect(page.locator('.el-table__row')).toHaveCount(4)
})

test('E-15 审计日志（操作 + 登录）', async ({ page }) => {
  await login(page)
  await goView(page, '/admin/audit', /\/admin\/audit$/)
  const rows = page.locator('.el-tab-pane:visible .el-table__row')
  await expect(rows).toHaveCount(2)
  await expect(rows.first()).toContainText('complete')
  await page.getByRole('tab', { name: '登录日志' }).click()
  await expect(rows).toHaveCount(2)
  await expect(rows.first()).toContainText('lisi')
})

test('E-16 周期任务（列表标记 + 日历按实例展开）', async ({ page }) => {
  await login(page)
  await goView(page, '/allTask', /\/allTask$/)
  await expect(page.locator('[data-test="cycle"]')).toHaveCount(3)
  await goView(page, '/calendar', /\/calendar$/)
  await expect(page.locator('.calendar-view__task')).toHaveCount(3)
  await expect(page.locator('.calendar-view__task').first()).toContainText('每日站会')
})

test('E-17 未登录跳转登录页（带 redirect）', async ({ page }) => {
  await page.goto('/index')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByRole('button', { name: '登录' })).toBeVisible()
})
