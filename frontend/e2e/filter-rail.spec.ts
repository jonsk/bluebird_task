import { expect, test } from '@playwright/test'
import { card, cardTitles, freezeTime, goView, login } from './helpers'

/**
 * E2E 验收 · 左栏筛选（E-08 自定义栏 / E-10 分类树）——03 §5.2/§5.3.4。
 *
 * 旧系统对齐后：自定义栏与六视图**同一列表内联**（Notebook 图标 + hover 改/删），
 * 「新增自定义任务栏」行在列表末尾（`.bb-add-row`），分类树置于列表下方 `.main-tabs-content`。
 * Mock 为内存 CRUD（`src/mocks/handlers.ts`），过滤语义与后端一致（分类按**子树**、自定义栏按条目）。
 */

test.beforeEach(async ({ page }) => {
  await freezeTime(page)
})

test('E-10 分类树：范围过滤、新增/改名/删除、选中联动过滤', async ({ page }) => {
  await login(page)

  const rail = page.locator('.bb-cat')
  await expect(rail).toBeVisible()

  // fixtures：3 个根分类 + 4 个子分类，默认全部展开
  await expect(rail.locator('.cat-node__label')).toHaveCount(7)
  await expect(rail.locator('.cat-node__badge', { hasText: '组织' })).toHaveCount(3)

  // 新增（个人范围，默认选项）
  await rail.locator('.bb-cat__add').click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: '新增个人分类' }).click()
  const draft = rail.getByPlaceholder('分类名称，回车保存')
  await draft.fill('E2E 分类')
  await draft.press('Enter')
  await expect(rail.locator('.cat-node__label', { hasText: 'E2E 分类' })).toHaveCount(1)
  await expect(rail.locator('.cat-node__label')).toHaveCount(8)

  // 改名
  const node = rail.locator('.cat-node', { hasText: 'E2E 分类' })
  await node.hover()
  await node.locator('.cat-node__op[title="重命名"]').click()
  const editor = rail.locator('.cat-node__input input')
  await editor.fill('E2E 分类改名')
  await editor.press('Enter')
  await expect(rail.locator('.cat-node__label', { hasText: 'E2E 分类改名' })).toHaveCount(1)

  // 选中联动：空分类 → 0 卡片 + 筛选条可见
  await rail.locator('.cat-node__label', { hasText: 'E2E 分类改名' }).click()
  await expect(page.locator('[data-test="filter-category"]')).toContainText('E2E 分类改名')
  await expect(cardTitles(page)).toHaveCount(0)

  // 选中 fixture 的 ORG 根分类「汇报」→ 子树命中 1 条（1201 归属 汇报）
  await rail.locator('.cat-node__label', { hasText: '汇报' }).click()
  await expect(page.locator('[data-test="filter-category"]')).toContainText('汇报')
  await expect(cardTitles(page)).toHaveCount(1)
  await expect(cardTitles(page).first()).toHaveText('提交季度报告')

  // 清除筛选 → 回到「我的一天」全量
  await page.locator('[data-test="filter-category"] .el-tag__close').click()
  await expect(cardTitles(page)).toHaveCount(6)

  // 删除（二次确认）
  const dup = rail.locator('.cat-node', { hasText: 'E2E 分类改名' })
  await dup.hover()
  await dup.locator('.cat-node__op[title="删除"]').click()
  await page.locator('.el-message-box:visible').getByRole('button', { name: '删除' }).click()
  await expect(rail.locator('.cat-node__label', { hasText: 'E2E 分类改名' })).toHaveCount(0)
  await expect(rail.locator('.cat-node__label')).toHaveCount(7)
})

test('E-10b 分类树：范围过滤仅显示对应范围（含层级保留）', async ({ page }) => {
  await login(page)
  const rail = page.locator('.bb-cat')

  await rail.locator('.el-radio-button', { hasText: '部门' }).click()
  // DEPARTMENT：会议 → 项目周会 → 架构评审（保留祖先层级）
  await expect(rail.locator('.cat-node__label')).toHaveCount(3)
  await expect(rail.locator('.cat-node__label').first()).toHaveText('会议')

  await rail.locator('.el-radio-button', { hasText: '组织' }).click()
  await expect(rail.locator('.cat-node__label')).toHaveCount(3)
  await expect(rail.locator('.cat-node__label').first()).toHaveText('汇报')

  await rail.locator('.el-radio-button', { hasText: '个人' }).click()
  await expect(rail.locator('.cat-node__label')).toHaveCount(1)
  await expect(rail.locator('.cat-node__label').first()).toHaveText('个人')
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
