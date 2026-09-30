import { expect, type Page } from '@playwright/test'

/**
 * E2E 共享辅助（03 §2.3.3）。
 *
 * 冻结点时间：fixtures 以 2026-09 为基线（如 dueAt 2026-09-24 逾期 / 2026-10-01 临期），
 * 冻结 `Date` 后逾期/临期着色与日历月份确定，视觉基线可复现。
 */
export const FIXED_TIME = new Date('2026-09-28T10:00:00+08:00')

export async function freezeTime(page: Page): Promise<void> {
  await page.clock.setFixedTime(FIXED_TIME)
}

/** 打开登录页（已冻结时间）。 */
export async function gotoLogin(page: Page): Promise<void> {
  await freezeTime(page)
  await page.goto('/login')
  await expect(page.getByRole('button', { name: '登录' })).toBeVisible()
}

/** 账密登录并落在「我的一天」，并等到布局与计数就绪（避免侧栏高度变化导致点击漂移）。 */
export async function login(page: Page, username = 'admin', password = 'admin123'): Promise<void> {
  await gotoLogin(page)
  await page.getByPlaceholder('用户名').fill(username)
  await page.getByPlaceholder('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/index$/)
  await expect(page.locator('.left-title__name')).toHaveText('管理员')
  await settle(page)
}

/**
 * 等到左栏计数与任务列表渲染完成（数据到达后布局稳定）。
 *
 * 左栏「分类树 / 自定义栏」为**异步拉取**（`GET /categories`、`GET /menus`），
 * 若不等其渲染完成就截图/断言，会与 MSW 响应竞态（曾导致视觉基线半数不含分类树）。
 * 故此处统一等待 rail 数据落地。
 */
export async function settle(page: Page): Promise<void> {
  await expect(page.locator('.layout__count')).toHaveCount(6)
  await expect(page.locator('.bb-task-list')).toBeVisible()
  await expect(page.locator('.bb-cat .cat-node')).toHaveCount(7)
  await expect(page.locator('[data-test="menu-row"]')).toHaveCount(3)
}

/** 路由路径 → 左栏 data-test 键（管理页路径含斜杠，需显式映射）。 */
const NAV_KEY: Record<string, string> = {
  '/admin/org': 'adminOrg',
  '/admin/users': 'adminOrg',
  '/admin/depts': 'adminOrg',
  '/admin/audit': 'adminAudit',
}

/**
 * 左栏导航项。接受路由名或路径（`/allTask` 与 `allTask` 等价），
 * 亦可用于左栏底部的「日历 / 用户管理 / 部门管理 / 日志」图标项。
 */
export function nav(page: Page, key: string) {
  const k = NAV_KEY[key] ?? key.replace(/^\//, '')
  return page.locator(`[data-test="nav-${k}"]`)
}

/** 点击左栏导航并等待目标路由。 */
export async function goView(page: Page, key: string, urlPattern: RegExp): Promise<void> {
  await nav(page, key).click()
  await expect(page).toHaveURL(urlPattern)
}

/** 任务卡片（按标题）。 */
export function card(page: Page, title: string) {
  return page.locator('.bb-task-card', { hasText: title })
}

/** 任务卡片标题文本列表。 */
export function cardTitles(page: Page) {
  return page.locator('.bb-task-card__title')
}

/** 右侧内联任务详情面板（旧 `.dialog-right-box`）。 */
export function detailPanel(page: Page) {
  return page.locator('.dialog-right-box')
}

/** 列表顶部内联任务编辑器（旧 `.t-b-input-box`）。 */
export function composer(page: Page) {
  return page.locator('.t-b-input-box')
}
