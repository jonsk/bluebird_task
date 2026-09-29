import { expect, test } from '@playwright/test'
import { card, cardTitles, freezeTime, goView, login } from './helpers'

/**
 * E2E 验收 · 左栏筛选（E-08 自定义栏 / E-10 分类树）——03 §5.2/§5.3.4。
 *
 * 补齐此前因目标态 UI 缺失而留空的场景；Mock 为内存 CRUD（`src/mocks/handlers.ts`），
 * 过滤语义与后端一致（分类按**子树**、自定义栏按条目）。
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

test('E-08 自定义栏：新建栏 → 移动任务入栏 → 按栏过滤', async ({ page }) => {
  await login(page)
  await goView(page, '/allTask', /\/allTask$/)
  await expect(cardTitles(page)).toHaveCount(7)

  const rail = page.locator('.bb-menu')
  await expect(rail.locator('.bb-menu__item')).toHaveCount(3)
  await expect(rail.locator('.bb-menu__item', { hasText: '本周重点' }).locator('.bb-menu__count')).toHaveText('2')

  // 新建栏
  await rail.locator('.bb-menu__add').click()
  const draft = rail.getByPlaceholder('栏名称，回车保存')
  await draft.fill('E2E 栏')
  await draft.press('Enter')
  const created = rail.locator('.bb-menu__item', { hasText: 'E2E 栏' })
  await expect(created).toHaveCount(1)
  await expect(created.locator('.bb-menu__count')).toHaveText('0')

  // 移动任务到栏（详情抽屉 → POST /menus/{id}/items）
  await card(page, '提交季度报告').click()
  const drawer = page.locator('.el-drawer:visible')
  await expect(drawer).toBeVisible()
  await drawer.locator('[data-test="move-menu"]').click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: 'E2E 栏' }).click()
  // 以栏内条目数收敛为准（toast 为瞬态提示，不作为断言依据）
  await expect(created.locator('.bb-menu__count')).toHaveText('1')

  await page.keyboard.press('Escape')
  await expect(drawer).toBeHidden()

  // 按栏过滤
  await created.click()
  await expect(page).toHaveURL(/\/allTask$/)
  await expect(page.locator('[data-test="filter-menu"]')).toContainText('E2E 栏')
  await expect(cardTitles(page)).toHaveCount(1)
  await expect(cardTitles(page).first()).toHaveText('提交季度报告')

  // 切回 fixture 栏「本周重点」（含 1201/1206 → 全部任务视图下 2 条）
  await rail.locator('.bb-menu__item', { hasText: '本周重点' }).click()
  await expect(page.locator('[data-test="filter-menu"]')).toContainText('本周重点')
  await expect(cardTitles(page)).toHaveCount(2)

  // 清除
  await page.locator('[data-test="filter-menu"] .el-tag__close').click()
  await expect(cardTitles(page)).toHaveCount(7)
})
