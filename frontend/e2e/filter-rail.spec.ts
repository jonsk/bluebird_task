import { expect, test } from '@playwright/test'
import { card, cardTitles, freezeTime, goView, login } from './helpers'

/**
 * E2E 验收 · 左栏筛选（E-08 自定义栏）——03 §5.2。
 *
 * 旧系统对齐后：自定义栏与各视图**同一列表内联**（Notebook 图标 + hover 改/删），
 * 「新增自定义任务栏」行在列表末尾（`.bb-add-row`）。
 * Mock 为内存 CRUD（`src/mocks/handlers.ts`），过滤语义与后端一致（自定义栏按条目）。
 */

test.beforeEach(async ({ page }) => {
  await freezeTime(page)
})

test('E-08 自定义栏：新建栏（内联行）→ 卡片添加到栏 → 按栏过滤；重命名/删除', async ({ page }) => {
  await login(page)
  await goView(page, '/allTask', /\/allTask$/)
  await expect(cardTitles(page)).toHaveCount(7)

  const rows = page.locator('[data-test="menu-row"]')
  await expect(rows).toHaveCount(3)
  await expect(rows.filter({ hasText: '本周重点' })).toHaveCount(1)

  // 新建栏（列表末尾「新增自定义任务栏」行）
  await page.locator('[data-test="menu-add"]').click()
  const draft = page.getByPlaceholder('栏名称，回车保存')
  await draft.fill('E2E 栏')
  await draft.press('Enter')
  await expect(rows).toHaveCount(4)
  await expect(rows.filter({ hasText: 'E2E 栏' })).toHaveCount(1)

  // 卡片「添加到自定义任务栏」（旧卡片上的 el-dropdown）
  await card(page, '提交季度报告').locator('[data-test="move-menu"]').click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: 'E2E 栏' }).click()
  await expect(page.getByText('已添加到自定义任务栏')).toBeVisible()

  // 按栏过滤
  await rows.filter({ hasText: 'E2E 栏' }).click()
  await expect(page).toHaveURL(/\/allTask$/)
  await expect(page.locator('[data-test="filter-menu"]')).toContainText('E2E 栏')
  await expect(cardTitles(page)).toHaveCount(1)
  await expect(cardTitles(page).first()).toHaveText('提交季度报告')

  // 切回 fixture 栏「本周重点」（含 1201/1206 → 全部任务视图下 2 条）
  await rows.filter({ hasText: '本周重点' }).click()
  await expect(page.locator('[data-test="filter-menu"]')).toContainText('本周重点')
  await expect(cardTitles(page)).toHaveCount(2)

  // 清除
  await page.locator('[data-test="filter-menu"] .el-tag__close').click()
  await expect(cardTitles(page)).toHaveCount(7)

  // 重命名（hover 显示操作）
  const created = rows.filter({ hasText: 'E2E 栏' })
  await created.hover()
  await created.locator('.bb-menu-row__op[title="重命名"]').click()
  // 进入编辑态后名称变成 el-input（不再是 text），不能再按 hasText 定位所在行
  const nameInput = page.locator('[data-test="menu-row"] .bb-menu-row__input input')
  await nameInput.fill('E2E 栏改名')
  await nameInput.press('Enter')
  await expect(rows.filter({ hasText: 'E2E 栏改名' })).toHaveCount(1)

  // 删除（二次确认）
  const renamed = rows.filter({ hasText: 'E2E 栏改名' })
  await renamed.hover()
  await renamed.locator('.bb-menu-row__op[title="删除"]').click()
  await page.locator('.el-message-box:visible').getByRole('button', { name: '删除' }).click()
  await expect(rows).toHaveCount(3)
})
