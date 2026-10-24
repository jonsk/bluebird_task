import { expect, test } from '@playwright/test'
import { freezeTime, login, settle } from './helpers'

/**
 * 工作日志 E2E（真实交互 + MSW 契约 mock）。
 *
 * 数据基线：`src/mocks/noteHandlers.ts`（冻结时间 2026-09-28）。
 *   我的日志 1001/1002（1003 已归档）
 *   李四 1004/1007、张三 1005、王五 1006
 *   授权：李四 → 管理员 VIEWER APPROVED；李四 → 张三 EDITOR PENDING
 */
test.describe('工作日志', () => {
  test.beforeEach(async ({ page }) => {
    await freezeTime(page)
    await login(page)
  })

  /** 左栏「工作日志」分组：进入 note 路由后必须自动展开（否则看得见标题却点不到入口）。 */
  test('N-01 左栏分组：跨模块进入工作日志后自动展开且导航项可见', async ({ page }) => {
    // 先手动折叠（模拟"上次在任务模块收起了日志组"，折叠态持久化在 localStorage）
    await page.locator('[data-test="nav-group-note-head"]').click()
    await expect(page.locator('[data-test="nav-note-mine"]')).toBeHidden()

    await page.goto('/notes')
    await expect(page).toHaveURL(/\/notes$/)
    await expect(page.locator('.note-card')).toHaveCount(2)
    for (const key of ['note-mine', 'note-explore', 'note-shared', 'note-internal', 'note-subordinate', 'note-archived', 'note-grants']) {
      await expect(page.locator(`[data-test="nav-${key}"]`)).toBeVisible()
    }
  })

  test('N-02 新建日志：临时卡片 + 第一页刷新 + 附件上传 + 完成', async ({ page }) => {
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)

    await page.fill('[data-test="note-editor"] textarea', '联调：工作日志新建与附件链路')
    await page.getByRole('button', { name: '创建' }).click()

    const draft = page.locator('[data-test="note-draft-card"]')
    await expect(draft).toBeVisible()
    await expect(draft).toContainText('联调：工作日志新建与附件链路')
    await expect(page.locator('.el-message--success')).toContainText('已保存并回到第一页')
    // 新建后列表刷新第一页（含刚创建的一条）
    await expect(page.locator('.note-card')).toHaveCount(3)

    // 附件区就在临时卡片里（这是"新建后仍能补附件"的唯一入口）
    await page.setInputFiles('[data-test="note-draft-card"] [data-test="note-attachment-input"]', {
      name: '联调记录.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('note e2e attachment'),
    })
    await expect(draft).toContainText('联调记录.txt')

    await page.locator('[data-test="note-draft-complete"]').click()
    await expect(page.locator('[data-test="note-draft-card"]')).toHaveCount(0)
  })

  test('N-03 行内编辑：内联面板（非弹窗）保存后卡片更新', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()

    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()
    await expect(page.locator('.el-dialog')).toHaveCount(0)
    // 编辑器必须回显详情全文（不是列表摘要）
    await expect(host.locator('textarea')).toHaveValue(/移动端适配方案/)

    await host.locator('textarea').fill('私有：移动端适配方案已改为两栏布局')
    await host.getByRole('button', { name: '保存' }).click()
    await expect(host).toHaveCount(0)
    await expect(page.locator('[data-test="note-card-1002"]')).toContainText('已改为两栏布局')
  })

  test('N-04 七个作用域列表与左栏一致', async ({ page }) => {
    const cases: Array<{ nav: string; url: RegExp; count: number; text: string }> = [
      { nav: 'note-explore', url: /\/notes\/explore$/, count: 2, text: '市场部 Q3 复盘方法论' },
      { nav: 'note-shared', url: /\/notes\/shared$/, count: 2, text: '被授权给管理员查看' },
      { nav: 'note-internal', url: /\/notes\/internal$/, count: 4, text: '懒加载与缓存淘汰' },
      { nav: 'note-subordinate', url: /\/notes\/subordinate$/, count: 4, text: '部门树递归查询' },
      { nav: 'note-archived', url: /\/notes\/archived$/, count: 1, text: '旧的日志接口草案' },
    ]
    for (const c of cases) {
      await page.locator(`[data-test="nav-${c.nav}"]`).click()
      await expect(page).toHaveURL(c.url)
      await expect(page.locator('.note-card')).toHaveCount(c.count)
      await expect(page.locator('.note-page')).toContainText(c.text)
    }
  })

  test('N-05 详情页：正文 / 引用 / 返回', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1001"]').click()
    await expect(page).toHaveURL(/\/notes\/1001$/)
    await expect(page.locator('[data-test="note-detail-content"]')).toContainText('工作日志的前端骨架')
    await expect(page.locator('.note-detail')).toContainText('引用（1）')
    await expect(page.locator('.note-detail')).toContainText('被引用（0）')
    await page.locator('[data-test="note-detail-back"]').click()
    await expect(page).toHaveURL(/\/notes$/)
  })

  test('N-06 详情 404：渲染空态且不给重试', async ({ page }) => {
    await page.goto('/notes/999999')
    await expect(page.locator('.note-detail')).toContainText('未找到该工作日志')
    await expect(page.locator('.note-detail').getByRole('button', { name: '重试' })).toHaveCount(0)
  })

  test('N-07 日历：月计数 + 点日跳列表并带 date 过滤', async ({ page }) => {
    await page.locator('[data-test="nav-note-mine"]').click()
    await expect(page).toHaveURL(/\/notes$/)
    await page.locator('[data-test="nav-calendar"]').click()
    await expect(page).toHaveURL(/\/notes\/calendar/)
    await expect(page.locator('[data-test="note-day-2026-09-28"]')).toContainText('1 条')
    await expect(page.locator('[data-test="note-day-2026-09-27"]')).toContainText('1 条')

    await page.locator('[data-test="note-day-2026-09-27"]').click()
    await expect(page).toHaveURL(/\/notes\?date=2026-09-27$/)
    await expect(page.locator('.note-card')).toHaveCount(1)
    await expect(page.locator('.note-page')).toContainText('移动端适配方案')
  })

  test('N-08 授权管理：Tab 切换 + 升级为可编辑', async ({ page }) => {
    await page.locator('[data-test="nav-note-grants"]').click()
    await expect(page).toHaveURL(/\/notes\/grants$/)
    const table = page.locator('[data-test="grant-table"]')
    // 默认「我发起的」：管理员 → 王五（只读，已生效）
    await expect(table).toContainText('王五')
    await expect(table).toContainText('只读')
    await page.getByRole('button', { name: '升级为可编辑' }).first().click()
    await expect(table).toContainText('可编辑')

    // 「涉及我的」：李四 → 管理员
    await page.getByRole('tab', { name: '涉及我的' }).click()
    await expect(table).toContainText('李四')
  })

  test('N-09 右栏当日日志：随选中日变化', async ({ page }) => {
    await page.goto('/notes')
    const side = page.locator('.note-side__list')
    await expect(side).toContainText('今日日志')
    await expect(side).toContainText('工作日志的前端骨架')
  })

  test('N-10 任务模块不受影响：左栏任务分组仍可用', async ({ page }) => {
    await settle(page)
    await page.locator('[data-test="nav-allTask"]').click()
    await expect(page).toHaveURL(/\/allTask$/)
    await expect(page.locator('.bb-task-card')).toHaveCount(7)
  })
})
