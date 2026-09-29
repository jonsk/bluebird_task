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
  await expect(page.locator('.layout__brand')).toHaveText('BlueBird 任务')
  await settle(page)
}

/** 等到左栏计数与任务列表渲染完成（数据到达后布局稳定）。 */
export async function settle(page: Page): Promise<void> {
  await expect(page.locator('.layout__count')).toHaveCount(6)
  await expect(page.locator('.bb-task-list')).toBeVisible()
}

/** 左栏导航项（按 href，避免文案/计数变化导致误匹配）。 */
export function nav(page: Page, href: string) {
  return page.locator(`.layout__nav-item[href="${href}"]`)
}

/** 点击左栏导航并等待目标路由（含列表稳定）。 */
export async function goView(page: Page, href: string, urlPattern: RegExp): Promise<void> {
  await nav(page, href).click()
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
