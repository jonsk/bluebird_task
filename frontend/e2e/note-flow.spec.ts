import { expect, test, type Locator } from '@playwright/test'
import { freezeTime, login, settle } from './helpers'

/**
 * Vditor IR（即时渲染）的可编辑区：`.vditor-ir > pre.vditor-reset[contenteditable]`。
 * 没有左右分栏，也没有 CodeMirror。
 */
function irEditor(scope: Locator): Locator {
  return scope.locator('.vditor-ir pre.vditor-reset')
}

/**
 * 输入 Markdown。
 *
 * IR 的语法转换是**按输入批次**触发的：一次性 `fill` 不触发语法解析，逐字符敲又会被
 * 编辑器每次重绘后的光标位置打乱（围栏代码块尤其明显）。走真实的 paste 事件最稳：
 * Vditor 会把纯文本按 Markdown 解析并渲染。
 */
async function typeMarkdown(editor: Locator, text: string): Promise<void> {
  await editor.click()
  await editor.press('ControlOrMeta+a')
  await editor.press('Delete')
  await editor.evaluate((el, value) => {
    const data = new DataTransfer()
    data.setData('text/plain', value)
    el.dispatchEvent(
      new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }),
    )
  }, text)
  // 等编辑器把粘贴内容解析进 DOM，后续断言/保存才拿到最新值
  await expect(editor).not.toHaveText('')
}

/**
 * 工作日志 E2E（真实交互 + MSW 契约 mock）。
 *
 * 数据基线：`src/mocks/noteHandlers.ts`（冻结时间 2026-09-28）。
 *   我的日志 1001/1002；李四 1004/1007、张三 1005、王五 1006
 *   授权：李四 → 管理员 VIEWER APPROVED；李四 → 张三 EDITOR PENDING
 *   全部日志已移到后台 `/admin/notes`（仅 ADMIN），左栏只留普通入口
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
    for (const key of ['note-mine', 'note-subordinate']) {
      await expect(page.locator(`[data-test="nav-${key}"]`)).toBeVisible()
    }
  })

  test('N-02 新建日志：保存后回到第一页并刷新列表', async ({ page }) => {
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)

    await typeMarkdown(
      irEditor(page.locator('[data-test="note-editor"]')),
      '## 联调\n- 工作日志新建与附件链路',
    )
    await page.getByRole('button', { name: '创建' }).click()

    await expect(page.locator('.el-message--success')).toContainText('已保存并回到第一页')
    // 新建后列表刷新第一页（含刚创建的一条）
    await expect(page.locator('.note-card')).toHaveCount(3)

    // 新日志直接进入列表；摘要已做 Markdown 清洗，不露 `##` / `-`
    const created = page.locator('.note-card').filter({ hasText: '工作日志新建与附件链路' })
    await expect(created).toBeVisible()
    await expect(created.locator('.note-card__summary')).not.toContainText('##')
    // 临时卡片（note-draft-card）已下线，不再出现
    await expect(page.locator('[data-test="note-draft-card"]')).toHaveCount(0)
  })

  test('N-03 行内编辑：内联面板（非弹窗）保存后卡片更新', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()

    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()
    await expect(page.locator('.el-dialog')).toHaveCount(0)
    // 编辑器必须回显详情全文（不是列表摘要）
    await expect(irEditor(host)).toContainText(/移动端适配方案/)

    await typeMarkdown(irEditor(host), '私有：移动端适配方案已改为两栏布局')
    await host.locator('[data-test="note-editor-save"]').click()
    await expect(host).toHaveCount(0)
    await expect(page.locator('[data-test="note-card-1002"]')).toContainText('已改为两栏布局')
  })

  test('N-04 两个作用域列表与左栏一致', async ({ page }) => {
    const cases: Array<{ nav: string; url: RegExp; count: number; text: string }> = [
      { nav: 'note-mine', url: /\/notes$/, count: 2, text: '工作日志的前端骨架' },
      { nav: 'note-subordinate', url: /\/notes\/subordinate$/, count: 3, text: '部门树递归查询' },
    ]
    for (const c of cases) {
      await page.locator(`[data-test="nav-${c.nav}"]`).click()
      await expect(page).toHaveURL(c.url)
      await expect(page.locator('.note-card')).toHaveCount(c.count)
      await expect(page.locator('.note-page')).toContainText(c.text)
    }
  })

  test('N-05 详情页：正文 / 附件 / 返回', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1001"]').click()
    await expect(page).toHaveURL(/\/notes\/1001$/)
    await expect(page.locator('[data-test="note-detail-content"]')).toContainText('工作日志的前端骨架')
    await expect(page.locator('.note-detail')).toContainText('附件')
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

  test('N-08 授权能力已下线：无授权入口、旧地址不可达', async ({ page }) => {
    await page.goto('/notes')
    await expect(page.locator('[data-test="nav-note-mine"]')).toBeVisible()
    // 左栏不再有「被授权日志 / 授权管理」
    await expect(page.locator('[data-test="nav-note-shared"]')).toHaveCount(0)
    await expect(page.locator('[data-test="nav-note-grants"]')).toHaveCount(0)

    // 旧书签直接访问 → 兜底 404 页（路由已删）
    await page.goto('/notes/grants')
    await expect(page.locator('.error-page h1')).toHaveText('404')
    await page.goto('/notes/shared')
    await expect(page.locator('.error-page h1')).toHaveText('404')
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

  test('N-11 筛选：关键词写入 URL 且结果收敛，清空后恢复', async ({ page }) => {
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)

    const kw = page.locator('[data-test="note-filter-bar"] input').first()
    await kw.fill('移动端')
    await expect(page).toHaveURL(/keyword=%E7%A7%BB%E5%8A%A8%E7%AB%AF|keyword=移动端/)
    await expect(page.locator('.note-card')).toHaveCount(1)
    await expect(page.locator('.note-page')).toContainText('移动端适配方案')

    await kw.fill('')
    await expect(page.locator('.note-card')).toHaveCount(2)
  })

  test('N-12 筛选无结果：渲染空态且不出现分页', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-filter-bar"] input').first().fill('绝不可能匹配的关键词zzz')
    await expect(page.locator('[data-test="note-list-empty"]')).toBeVisible()
    await expect(page.locator('.note-card')).toHaveCount(0)
    await expect(page.locator('.note-list__pager')).toHaveCount(0)
  })

  test('N-14 归档能力已下线：卡片无归档按钮', async ({ page }) => {
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)
    await expect(page.locator('[data-test="note-card-archive"]')).toHaveCount(0)
    // 左栏也没有「已归档」入口
    await expect(page.locator('[data-test="nav-note-archived"]')).toHaveCount(0)
  })

  test('N-15 删除：二次确认后卡片即时移除', async ({ page }) => {
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-delete"]').click()

    const box = page.locator('.el-message-box')
    await expect(box).toContainText('删除后不可恢复')
    await box.getByRole('button', { name: '删除' }).click()

    await expect(page.locator('.el-message--success')).toContainText('已删除')
    await expect(page.locator('[data-test="note-card-1002"]')).toHaveCount(0)
    await expect(page.locator('.note-card')).toHaveCount(1)
  })

  test('N-16 版本冲突：提示刷新并保留草稿内容', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()

    // mock 的一次性冲突钩子（模拟"保存前该日志已被他人改动"）
    await page.evaluate(() => localStorage.setItem('bb_mock_force_conflict', '1'))
    await typeMarkdown(irEditor(host), '冲突场景：这段内容必须保留')
    await host.locator('[data-test="note-editor-save"]').click()

    await expect(page.locator('.el-message--error')).toContainText('内容已被他人修改')
    // 面板不关闭、草稿仍在
    await expect(host).toBeVisible()
    await expect(irEditor(host)).toContainText(/这段内容必须保留/)
  })

  test('N-17 Ctrl+Enter 保存：不点按钮也能提交', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()
    const editor = irEditor(host)
    await typeMarkdown(editor, '快捷键保存的内容')
    await editor.press('Control+Enter')

    await expect(host).toHaveCount(0)
    await expect(page.locator('[data-test="note-card-1002"]')).toContainText('快捷键保存的内容')
  })

  test('N-18 新建后跨路由往返：回到列表仍能看到新日志', async ({ page }) => {
    await page.goto('/notes')
    await typeMarkdown(irEditor(page.locator('[data-test="note-editor"]')), '跨路由恢复用日志')
    await page.getByRole('button', { name: '创建' }).click()
    await expect(page.locator('.note-card')).toHaveCount(3)
    await expect(page.locator('[data-test="note-draft-card"]')).toHaveCount(0)

    // 离开 → 返回（HomeView 重新挂载后重新拉列表）
    await page.locator('[data-test="note-card-1001"]').click()
    await expect(page).toHaveURL(/\/notes\/1001$/)
    await page.locator('[data-test="note-detail-back"]').click()
    await expect(page).toHaveURL(/\/notes$/)

    await expect(page.locator('.note-card')).toHaveCount(3)
    await expect(page.locator('.note-card').filter({ hasText: '跨路由恢复用日志' })).toBeVisible()
  })

  test('N-19 1280 窄屏：布局不横向溢出且主区可用', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('/notes')
    await expect(page.locator('.note-card')).toHaveCount(2)
    await expect(page.locator('.note-page')).toBeVisible()
    await expect(page.locator('[data-test="nav-note-mine"]')).toBeVisible()

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - window.innerWidth,
    )
    expect(overflow).toBeLessThanOrEqual(1)
  })

  test('N-21 工具栏保持库默认项：图片/预览/加粗都在，且没有自定义按钮', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()

    // 未改 Vditor 默认工具栏：上传图片、预览、加粗都是库自带项
    const upload = host.locator('.vditor-toolbar [data-type="upload"]')
    await expect(upload).toHaveCount(1)
    await expect(host.locator('.vditor-toolbar [data-type="preview"]')).toHaveCount(1)
    await expect(host.locator('.vditor-toolbar [data-type="bold"]')).toHaveCount(1)
    // 没有自己加的业务按钮（保存走底部按钮 / Ctrl+Enter）
    await expect(host.locator('.vditor-toolbar [data-type="save"]')).toHaveCount(0)

    await typeMarkdown(irEditor(host), '工具栏默认：这段内容必须落库')
    await host.locator('[data-test="note-editor-save"]').click()
    await expect(host).toHaveCount(0)
    await expect(page.locator('[data-test="note-card-1002"]')).toContainText('工具栏默认')
  })

  test('N-22 单栏即时渲染：输入区直接出效果，公式与 mermaid 走本地资源', async ({ page }) => {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()

    // 单栏 IR：只有可编辑区，没有独立的预览栏（旧的左右分栏已下线）
    const editor = irEditor(host)
    await expect(editor).toBeVisible()
    await expect(host.locator('.md-editor-preview')).toHaveCount(0)

    await typeMarkdown(editor, '# 标题\n\n公式 $E=mc^2$\n\n```mermaid\ngraph TD\nA-->B\n```')

    // 输入区本身就出标题、公式、图，不需要点任何预览按钮
    await expect(editor.locator('h1').filter({ hasText: '标题' })).toBeVisible()
    // katex / mermaid 由 `/vditor` 同源资源异步加载，给足时间
    await expect(editor.locator('.katex').first()).toBeVisible({ timeout: 20000 })
    await expect(editor.locator('svg').first()).toBeVisible({ timeout: 20000 })

    // 零外网：编辑器相关请求都落在同源 /vditor 下
    const external = await page.evaluate(() => {
      const urls = performance.getEntriesByType('resource').map((e) => e.name)
      return urls.filter((u) => !u.startsWith(location.origin) && /vditor|unpkg|jsdelivr/.test(u))
    })
    expect(external).toEqual([])
  })

  test('N-23 详情页正文图片：带 Bearer 取图后替换为 blob（不出现破图）', async ({ page }) => {
    // mock 预置：1002 正文含 ![](/notes/1002/attachments/8801/preview)
    await page.goto('/notes/1002')
    const img = page.locator('[data-test="note-detail-content"] img').first()
    await expect(img).toBeVisible({ timeout: 15000 })
    await expect(img).toHaveAttribute('data-bb-img', 'done')
    await expect(img).toHaveAttribute('src', /^blob:/)
  })

  test('N-24 详情页含公式：Vditor.preview 走本地 katex 渲染（正文不空白）', async ({ page }) => {
    await page.goto('/notes/1001')
    await expect(page.locator('[data-test="note-detail-content"]')).toContainText('工作日志的前端骨架')
    await expect(page.locator('[data-test="note-detail-content"] .katex').first()).toBeVisible({
      timeout: 15000,
    })
  })

  test('N-20 全部日志在后台：仅 ADMIN 可见，列出全员日志', async ({ page }) => {
    // 前台左栏不再有入口
    await page.goto('/notes')
    await expect(page.locator('[data-test="nav-note-all"]')).toHaveCount(0)

    await page.goto('/admin/notes')
    await expect(page).toHaveURL(/\/admin\/notes$/)
    await expect(page.locator('[data-test="admin-nav-adminNotes"]')).toHaveClass(/is-active/)
    await expect(page.locator('.note-card')).toHaveCount(6)
    await expect(page.locator('.note-page')).toContainText('部门树递归查询')
  })
})
