import { expect, test, type Locator, type Page } from '@playwright/test'
import { freezeTime, login } from './helpers'

/**
 * 编辑器 P0/P1/P2 功能回归。
 *
 * 运行在 MSW mock 上：附件上传、成员搜索都走 `src/mocks` 的内存实现；
 * 这里只验证「工具栏操作 → 文档结构 / 保存后回显」这条主链路。
 */
test.describe('工作日志编辑器能力', () => {
  test.beforeEach(async ({ page }) => {
    await freezeTime(page)
    await login(page)
  })

  async function openEditor(page: Page): Promise<{ host: Locator; editor: Locator }> {
    await page.goto('/notes')
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const host = page.locator('[data-test="note-inline-editor"]')
    await expect(host).toBeVisible()
    const editor = host.locator('.ProseMirror')
    await expect(editor).toBeVisible()
    return { host, editor }
  }

  async function resetEditor(editor: Locator): Promise<void> {
    await editor.click()
    await editor.press('ControlOrMeta+a')
    await editor.press('Delete')
  }

  test('E-01 表格 / 对齐 / 缩进 / 字号 / 行距 / 清除格式', async ({ page }) => {
    const { host, editor } = await openEditor(page)
    await resetEditor(editor)

    await page.locator('[data-test="note-table"]').click()
    await expect(editor.locator('table')).toHaveCount(1)
    await expect(editor.locator('table tr')).toHaveCount(3)
    await expect(editor.locator('table th')).toHaveCount(3)
    await expect(page.locator('[data-test="note-table-ops"]')).toBeVisible()

    await page.locator('[data-test="note-table-ops"] [title="下方加行"]').click()
    await expect(editor.locator('table tr')).toHaveCount(4)

    await resetEditor(editor)
    await page.keyboard.type('居中段落')
    const paragraph = editor.locator('p').first()
    const defaultLineHeight = await paragraph.evaluate((el) => {
      const style = window.getComputedStyle(el)
      return Number.parseFloat(style.lineHeight) / Number.parseFloat(style.fontSize)
    })
    expect(defaultLineHeight).toBeCloseTo(1.4, 2)
    await page.locator('[data-test="note-align-menu"]').click()
    await page.locator('[data-test="note-align-center"]').click()
    await expect(paragraph).toHaveAttribute('style', /text-align:\s*center/)

    await page.locator('[data-test="note-font-size"]').selectOption('20px')
    await expect(paragraph).toHaveAttribute('style', /font-size:\s*20px/)
    await page.locator('[data-test="note-line-height"]').selectOption('2')
    await expect(paragraph).toHaveAttribute('style', /line-height:\s*2/)

    await page.locator('[data-test="note-indent"]').click()
    await expect(paragraph).toHaveAttribute('data-indent', '1')

    await editor.press('ControlOrMeta+a')
    await page.locator('[title^="粗体"]').click()
    await expect(editor.locator('strong')).toHaveCount(1)
    await page.locator('[data-test="note-clear-format"]').click()
    await expect(editor.locator('strong')).toHaveCount(0)

    await editor.press('Escape')
    await expect(host).toHaveCount(0)
  })

  test('E-02 附件卡片上传、保存后重新打开仍可回显', async ({ page }) => {
    const { host, editor } = await openEditor(page)
    await resetEditor(editor)

    await page.locator('[data-test="note-attachment-input"]').setInputFiles({
      name: '季度报告.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('bluebird attachment'),
    })

    const card = editor.locator('.note-attachment').first()
    await expect(card).toBeVisible()
    await expect(card).toContainText('季度报告.pdf')
    await expect(card).toContainText('PDF')

    await editor.press('Control+Enter')
    await expect(host.locator('.note-inline-editor__save.is-saved')).toBeVisible()
    // MSW 状态在整页 reload 后会重置，这里用「退出编辑 → 再进入」验证保存后的 HTML 能被重新解析。
    await editor.press('Escape')
    await expect(host).toHaveCount(0)
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const reopened = page.locator('[data-test="note-inline-editor"]').locator('.ProseMirror')
    await expect(reopened.locator('.note-attachment')).toContainText('季度报告.pdf')
    await reopened.press('Escape')
    await expect(page.locator('[data-test="note-inline-editor"]')).toHaveCount(0)
  })

  test('E-03 目录与字体设置', async ({ page }) => {
    const { editor } = await openEditor(page)
    const fontSelect = page.locator('[data-test="note-font-family"]')
    await expect(fontSelect.locator('option').first()).toHaveText('字体')
    await expect(fontSelect).not.toContainText('系统无衬线')
    await expect(page.locator('[data-test="note-font-size"] option').first()).toHaveText('字号')
    await expect(page.locator('[data-test="note-line-height"] option').first()).toHaveText('默认 1.4')
    await resetEditor(editor)

    await page.locator('.note-toolbar select[title="段落格式"]').selectOption('h2')
    await page.keyboard.type('第一章')
    await page.keyboard.press('Enter')
    await page.locator('.note-toolbar select[title="段落格式"]').selectOption('h2')
    await page.keyboard.type('第二章')

    const outline = page.locator('[data-test="note-outline-panel"]')
    await expect(outline).toBeVisible()
    await expect(outline.locator('.note-outline__item')).toHaveCount(2)
    await expect(outline).toContainText('第一章')
    await expect(outline).toContainText('第二章')
    await outline.locator('.note-outline__item').first().click()

    await page.keyboard.press('Control+End')
    await page.keyboard.press('Enter')
    await page.locator('.note-toolbar select[title="段落格式"]').selectOption('paragraph')
    await page.keyboard.type('字体测试')
    await page.locator('[data-test="note-font-family"]').selectOption('"Songti SC", SimSun, serif')
    await expect(editor.locator('p').filter({ hasText: '字体测试' })).toHaveAttribute(
      'style',
      /font-family:\s*"Songti SC", SimSun, serif/,
    )

    await editor.press('Control+Enter')
    await expect(page.locator('[data-test="note-inline-editor"] .note-inline-editor__save.is-saved')).toBeVisible()
    await editor.press('Escape')
    await expect(page.locator('[data-test="note-inline-editor"]')).toHaveCount(0)
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const reopened = page.locator('[data-test="note-inline-editor"]').locator('.ProseMirror')
    await expect(reopened.locator('p').filter({ hasText: '字体测试' })).toHaveAttribute(
      'style',
      /font-family:\s*"Songti SC", SimSun, serif/,
    )
  })

  test('E-04 @提及 / Emoji', async ({ page }) => {
    const { host, editor } = await openEditor(page)
    await resetEditor(editor)
    await expect(page.locator('[data-test="note-find-toggle"]')).toHaveCount(0)

    await page.keyboard.type('@李')
    const mentionMenu = page.locator('[data-test="note-mention-menu"]')
    await expect(mentionMenu).toBeVisible()
    await mentionMenu.locator('.mention-item').filter({ hasText: '李四' }).click()
    await expect(editor.locator('.note-mention')).toContainText('@李四')

    await page.locator('[data-test="note-emoji-toggle"]').click()
    const picker = page.locator('[data-test="note-emoji-picker"]')
    await expect(picker).toBeVisible()
    await picker.locator('.emoji-picker__item').first().click()
    await expect(editor.locator('span[data-type="emoji"]')).toHaveCount(1)

    await editor.press('Control+Enter')
    await expect(host.locator('.note-inline-editor__save.is-saved')).toBeVisible()
    await editor.press('Escape')
    await expect(host).toHaveCount(0)
    await page.locator('[data-test="note-card-1002"] [data-test="note-card-edit"]').click()
    const reopened = page.locator('[data-test="note-inline-editor"]').locator('.ProseMirror')
    await expect(reopened.locator('.note-mention')).toContainText('@李四')
    await expect(reopened.locator('span[data-type="emoji"]')).toHaveCount(1)
    await reopened.press('Escape')
  })

  test('E-05 折叠代码支持 Emoji，目录高度随正文增长', async ({ page }) => {
    const { host, editor } = await openEditor(page)
    await resetEditor(editor)

    await expect(page.locator('.note-toolbar [title="行内代码"]')).toHaveCount(0)
    await expect(page.locator('.note-toolbar [title="代码块"]')).toHaveCount(0)

    await page.locator('[data-test="note-details"]').click()
    const details = editor.locator('[data-type="details"]').first()
    const summary = details.locator('summary').first()
    await expect(summary).toBeVisible()
    await summary.click()

    await page.locator('[data-test="note-emoji-toggle"]').click()
    const picker = page.locator('[data-test="note-emoji-picker"]')
    await expect(picker).toBeVisible()
    await picker.locator('[data-test="note-emoji-tab-people"]').click()
    await picker.locator('.emoji-picker__item').first().click()
    await expect(summary.locator('span[data-type="emoji"]')).toHaveCount(1)

    await resetEditor(editor)
    await editor.click()
    for (let i = 0; i < 40; i += 1) {
      await page.keyboard.type(`长内容行 ${i}`)
      await page.keyboard.press('Enter')
    }

    const outline = page.locator('[data-test="note-outline-panel"]')
    const body = page.locator('[data-test="note-editor-content"]')
    const [outlineBox, bodyBox] = await Promise.all([outline.boundingBox(), body.boundingBox()])
    expect(outlineBox?.height ?? 0).toBeGreaterThan(520)
    expect(Math.abs((outlineBox?.height ?? 0) - (bodyBox?.height ?? 0))).toBeLessThan(3)

    await editor.press('Escape')
    await expect(host).toHaveCount(0)
  })

  test('E-06 编辑页 Ctrl/Cmd+单击打开链接', async ({ page }) => {
    const { editor } = await openEditor(page)
    await resetEditor(editor)
    await page.keyboard.type('官网')
    await editor.press('ControlOrMeta+a')

    const dialogPromise = page.waitForEvent('dialog')
    const clickPromise = page.locator('.note-toolbar [title^="链接"]').click()
    const dialog = await dialogPromise
    expect(dialog.type()).toBe('prompt')
    await dialog.accept('https://example.com')
    await clickPromise

    const link = editor.locator('a')
    await expect(link).toHaveAttribute('href', 'https://example.com')

    const modifier = process.platform === 'darwin' ? 'Meta' : 'Control'
    const popupPromise = page.waitForEvent('popup')
    await link.click({ modifiers: [modifier] })
    const popup = await popupPromise
    await expect(popup).toHaveURL('https://example.com/')
    await popup.close()
  })
})
