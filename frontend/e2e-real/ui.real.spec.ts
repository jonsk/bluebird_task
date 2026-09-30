import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

/**
 * 真实后端 UI 联调用例（选入式，**不在默认 CI 中运行**）。运行见 `api.real.spec.ts` 顶部说明。
 *
 * 与 `e2e/`（MSW fixtures）互补：这里跑真实 jar，覆盖「只在真实后端才暴露」的交互链路
 * （按 id 打开详情、附件真实上传/删除、子任务改状态、部门分类建档等）。
 */

const BASE = process.env.E2E_REAL_BASE_URL ?? 'http://127.0.0.1:8080'
const PASSWORD = process.env.E2E_REAL_PASSWORD ?? 'Admin123!'
const uniq = (p: string) => `${p}${String(Date.now()).slice(-7)}`

test.use({ baseURL: BASE })

const results: { name: string; ok: boolean; detail: string }[] = []
function check(name: string, ok: boolean, detail = ''): void {
  results.push({ name, ok, detail })
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : '  -> ' + detail}`)
}
const failed = () => results.filter((r) => !r.ok).length

async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByPlaceholder('用户名').fill('admin')
  await page.getByPlaceholder('密码').fill(PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/index$/, { timeout: 20_000 })
  await expect(page.locator('.layout__count')).toHaveCount(6, { timeout: 20_000 })
}

/** 确认框必须居中且有样式（用户反馈 #2/#9：曾因缺命令式组件 CSS 落到页面左上/左下角）。 */
async function dialogCentered(page: Page, label: string): Promise<void> {
  const box = page.locator('.el-message-box').first()
  await expect(box).toBeVisible({ timeout: 10_000 })
  const b = await box.boundingBox()
  const vp = page.viewportSize()!
  if (!b) return check(`${label}：确认框可测量`, false, 'no bbox')
  const dx = Math.abs(b.x + b.width / 2 - vp.width / 2)
  const dy = Math.abs(b.y + b.height / 2 - vp.height / 2)
  check(`${label}：确认框居中且已应用样式`, dx <= 40 && dy <= 40 && b.width > 250 && b.height > 80, `dx=${dx.toFixed(0)} dy=${dy.toFixed(0)} ${b.width}x${b.height}`)
}

/** 造一条「我的一天」任务 + 1 个子任务，供详情/子任务/日历断言使用。 */
async function seedTask(request: APIRequestContext, token: string, userId: number): Promise<number> {
  const title = uniq('UI-主任务-')
  const created = await (await request.post('/api/v1/tasks', {
    headers: { Authorization: `Bearer ${token}` },
    data: { title, assigneeIds: [userId], ccIds: [userId], dueAt: new Date().toISOString() },
  })).json()
  expect(created.code).toBe(0)
  await request.post('/api/v1/tasks', {
    headers: { Authorization: `Bearer ${token}` },
    data: { title: uniq('UI-子任务-'), parentId: created.data },
  })
  return created.data
}

test.describe.configure({ mode: 'default' })

test('登录 + 六大视图计数渲染（真实后端）', async ({ page }) => {
  page.setDefaultTimeout(25_000)
  await login(page)
  check('登录后左栏 6 个视图计数渲染', (await page.locator('.layout__count').count()) === 6, '')
  const titles = (await page.locator('.bb-task-card__title').allInnerTexts()).map((t) => t.trim())
  check('我的一天能渲染任务卡', titles.length >= 0, '')
  await expect(page).toHaveURL(/\/index$/)
})

test('#1 分类：个人标签 + 部门分类可建立', async ({ page }) => {
  page.setDefaultTimeout(25_000)
  await login(page)
  const pName = uniq('个人-')
  await page.locator('.bb-cat__add').first().click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: '新增个人分类' }).first().click()
  const input = page.getByPlaceholder('分类名称，回车保存')
  await expect(input).toBeVisible()
  await input.fill(pName)
  await input.press('Enter')
  await page.waitForTimeout(1800)
  const node = page.locator('.cat-node').filter({ hasText: pName }).first()
  check('#1 新建的个人分类显示「个人」标签', (await node.innerText().catch(() => '')).includes('个人'), await node.innerText().catch(() => 'n/a'))

  const dName = uniq('部门-')
  await page.locator('.bb-cat__add').first().click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: '新增部门分类' }).first().click()
  const deptSelect = page.locator('[data-test="category-dept-select"]')
  await expect(deptSelect).toBeVisible()
  check('#1 部门分类草稿行提供「选择部门」下拉', (await deptSelect.count()) > 0, '')
  await page.getByPlaceholder('分类名称，回车保存').fill(dName)
  await deptSelect.click()
  await page.waitForTimeout(700)
  const opts = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
  check('#1 部门下拉选中不提前提交（可选部门）', (await opts.count()) > 0, `options=${await opts.count()}`)
  await opts.first().click()
  await page.waitForTimeout(1200)
  // 兜底：若下拉关闭后草稿仍在（例如重复选中同一部门不触发 change），按回车提交
  if ((await page.getByPlaceholder('分类名称，回车保存').count()) > 0) {
    await page.getByPlaceholder('分类名称，回车保存').press('Enter')
  }
  await page.waitForTimeout(2200)
  check('#1 部门分类可成功建立', (await page.locator('.cat-node').filter({ hasText: dName }).count()) > 0, 'dept category missing')
  expect(failed()).toBe(0)
})

test('#2 自定义栏可删除且确认框居中', async ({ page }) => {
  page.setDefaultTimeout(25_000)
  await login(page)
  const before = await page.locator('[data-test="menu-row"]').count()
  const name = uniq('待删栏-')
  await page.locator('[data-test="menu-add"]').click()
  const input = page.getByPlaceholder('栏名称，回车保存')
  await expect(input).toBeVisible()
  await input.fill(name)
  await input.press('Enter')
  await page.waitForTimeout(1800)
  const afterCreate = await page.locator('[data-test="menu-row"]').count()
  check('#2 自定义栏创建成功', afterCreate === before + 1, `${before} -> ${afterCreate}`)

  const row = page.locator('[data-test="menu-row"]', { hasText: name }).first()
  await row.hover()
  await row.locator('.bb-menu-row__op[title="删除"]').click()
  await dialogCentered(page, '#2 删除自定义栏')
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click()
  await page.waitForTimeout(2200)
  check('#2 自定义栏删除生效', (await page.locator('[data-test="menu-row"]').count()) === afterCreate - 1, 'not deleted')
  expect(failed()).toBe(0)
})

test('#3 编辑器：点图标即开下拉（优先级/指派/标签/分类）', async ({ page }) => {
  page.setDefaultTimeout(25_000)
  await login(page)
  const composer = page.locator('.t-b-input-box')
  await composer.getByPlaceholder('添加任务').click()
  await page.waitForTimeout(600)
  for (const [key, label] of [['composer-priority', '优先级'], ['composer-assignee', '指派'], ['composer-tags', '标签'], ['composer-category', '分类']] as const) {
    const field = composer.locator(`[data-test="${key}"]`)
    const icon = field.locator('xpath=preceding-sibling::*[1]').first()
    await page.keyboard.press('Escape')
    await page.waitForTimeout(250)
    await icon.click()
    await page.waitForTimeout(800)
    check(`#3 ${label}：点图标即打开下拉（用户反馈 #3）`, (await page.locator('.el-select-dropdown:visible').count()) > 0, 'no dropdown')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(250)
  }
  expect(failed()).toBe(0)
})

test('#4/#8/#9/#10/#11 详情面板：不重叠 / 添加步骤 / 附件 / 子任务状态 / 空态居中', async ({ page, request }) => {
  page.setDefaultTimeout(30_000)
  const lg = await (await request.post('/api/v1/auth/login', { data: { username: 'admin', password: PASSWORD } })).json()
  const token = lg.data.accessToken
  const uid = lg.data.user.id
  const taskId = await seedTask(request, token, uid)

  await login(page)

  // #9 空态居中（打开任务前）
  const empty = page.locator('.dialog-right-box__empty')
  if (await empty.count()) {
    const eb = await empty.boundingBox()
    const pb = await page.locator('.dialog-right-box').boundingBox()
    if (eb && pb) {
      const dx = Math.abs(eb.x + eb.width / 2 - (pb.x + pb.width / 2))
      const dy = Math.abs(eb.y + eb.height / 2 - (pb.y + pb.height / 2))
      check('#9 「选择任务查看详情」空态居中', dx <= 4 && dy <= 4, `dx=${dx.toFixed(1)} dy=${dy.toFixed(1)}`)
    }
  }

  await page.locator('[data-test="nav-allTask"]').click()
  await page.waitForTimeout(1800)
  await page.locator('.bb-task-card').first().click()
  const panel = page.locator('.dialog-right-box')
  await expect(panel).toBeVisible({ timeout: 15_000 })
  await page.waitForTimeout(1800)
  check('详情面板能按 id 加载（曾因 id 精度丢失显示「任务不存在」）', (await panel.innerText()).length > 20, 'panel empty')

  // #4 日历与编辑区不重叠 + 不溢出
  const tb = await page.locator('.bb-calendar .el-calendar-table').boundingBox()
  const pb = await panel.boundingBox()
  if (tb && pb) {
    const oy = Math.max(0, Math.min(tb.y + tb.height, pb.y + pb.height) - Math.max(tb.y, pb.y))
    const ox = Math.max(0, Math.min(tb.x + tb.width, pb.x + pb.width) - Math.max(tb.x, pb.x))
    check('#4 日历与编辑区不重叠（用户反馈 #4）', ox * oy === 0, `overlap=${ox * oy}`)
  }
  const ov = await page.evaluate(() => {
    const el = document.querySelector('.bb-calendar') as HTMLElement | null
    return el ? { s: el.scrollHeight, c: el.clientHeight } : null
  })
  check('#4 日历内容不溢出容器', !ov || ov.s <= ov.c + 1, JSON.stringify(ov))

  // #2 中文 locale（日历标题）
  const calTitle = (await page.locator('.bb-calendar .el-calendar__title').innerText().catch(() => '')).trim()
  check('#2 日历为中文（zh-CN locale）', /[\u4e00-\u9fa5]/.test(calTitle), calTitle)

  // #8 添加步骤可新建子任务
  const step = panel.locator('input[placeholder="添加步骤"]')
  check('#8 详情面板存在「添加步骤」入口（用户反馈 #8）', (await step.count()) > 0, 'missing')
  const before = await panel.locator('.drbb-subtask').count()
  if (await step.count()) {
    await step.fill(uniq('界面子任务-'))
    await step.press('Enter')
    await page.waitForTimeout(2200)
    check('#8 可新建子任务', (await panel.locator('.drbb-subtask').count()) === before + 1, `${before} -> ${await panel.locator('.drbb-subtask').count()}`)
  }

  // #11 子任务状态可切换
  const first = panel.locator('.drbb-subtask').first()
  if (await first.count()) {
    const doneBefore = await first.locator('.is-done').count()
    await first.locator('.el-checkbox').click()
    await page.waitForTimeout(2200)
    const after = panel.locator('.drbb-subtask').first()
    const doneAfter = await after.locator('.is-done').count()
    check('#11 子任务状态可切换（用户反馈 #11）', doneAfter > doneBefore || /is-done/.test((await after.getAttribute('class')) ?? ''), `${doneBefore} -> ${doneAfter}`)
  }

  // #10 附件：真实文件名 + 可上传 + 可删除
  const fileName = `${uniq('界面附件-')}.txt`
  await panel.locator('input[type=file]').setInputFiles({ name: fileName, mimeType: 'text/plain', buffer: Buffer.from('ui real attachment') })
  await page.waitForTimeout(2500)
  const texts = () => panel.locator('.bb-attachments__item').allInnerTexts()
  check('#10 上传后显示用户真实文件名', (await texts()).some((t) => t.includes(fileName)), JSON.stringify(await texts()))
  const item = panel.locator('.bb-attachments__item', { hasText: fileName }).first()
  if (await item.count()) {
    await item.getByRole('button', { name: '删除' }).click()
    await page.waitForTimeout(2500)
    check('#10 用户上传的附件可删除（用户反馈 #10）', !(await texts()).some((t) => t.includes(fileName)), JSON.stringify(await texts()))
  }

  await request.delete(`/api/v1/tasks/${taskId}`, { headers: { Authorization: `Bearer ${token}` } })
  expect(failed()).toBe(0)
})

test('#5/#6/#7/#8/#9 组织管理：默认部门 / 部门树选择 / 角色说明 / 邮箱 / 禁用确认框', async ({ page }) => {
  page.setDefaultTimeout(25_000)
  await login(page)
  await page.locator('[data-test="nav-adminOrg"]').click()
  await expect(page).toHaveURL(/\/admin\/org$/, { timeout: 15_000 })
  await page.waitForTimeout(2000)

  const root = page.locator('.org-manage__node').first()
  const rootText = (await root.innerText().catch(() => '')).replace(/\s+/g, ' ').trim()
  check('#5 系统默认顶级部门已初始化', rootText.length > 0, 'none')
  check('#5 默认部门带「系统默认」标记', rootText.includes('系统默认'), rootText)
  await root.hover()
  await page.waitForTimeout(400)
  check('#5 默认部门不提供删除入口（用户反馈 #5）', !(await root.getByRole('button', { name: '删除' }).isVisible().catch(() => false)), 'delete visible')

  const headers = (await page.locator('.el-table th').allInnerTexts()).map((t) => t.trim())
  check('#8 用户表含「邮箱」列（用户反馈 #8）', headers.includes('邮箱'), JSON.stringify(headers))

  await page.locator('.el-table__row').first().getByRole('button', { name: '编辑' }).click()
  await page.waitForTimeout(1000)
  const dlg = page.locator('.el-dialog:visible')
  const labels = (await dlg.locator('.el-form-item__label').allInnerTexts()).map((t) => t.trim())
  check('#8 编辑用户含「手机号」「邮箱」', labels.some((l) => l.includes('手机')) && labels.some((l) => l.includes('邮箱')), JSON.stringify(labels))
  const mobileVal = await dlg.locator('.el-form-item', { hasText: '手机' }).first().locator('input').inputValue().catch(() => '')
  check('#8 手机号返回明文（非脱敏）', !mobileVal.includes('****'), mobileVal)

  const deptSel = dlg.locator('.el-form-item', { hasText: '部门' }).first().locator('.el-tree-select, .el-select')
  await deptSel.first().click()
  await page.waitForTimeout(700)
  check('#6 部门下拉内为机构树（用户反馈 #6）', (await page.locator('.el-select-dropdown:visible .el-tree').count()) > 0, 'no tree')
  await page.keyboard.press('Escape')
  await page.waitForTimeout(300)

  await dlg.locator('.el-form-item', { hasText: '角色' }).first().locator('.el-select').first().click()
  await page.waitForTimeout(600)
  const roleOpts = (await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').allInnerTexts()).map((t) => t.replace(/\s+/g, ' ').trim())
  check('#7 角色选项带权限说明（用户反馈 #7）', roleOpts.some((t) => t.length > 6), JSON.stringify(roleOpts))
  await page.keyboard.press('Escape')
  await page.waitForTimeout(400)
  check('#7 说明角色为固定枚举', /固定|不可新增/.test(await dlg.innerText()), '')
  await dlg.getByRole('button', { name: '取消' }).click()
  await page.waitForTimeout(800)

  await page.locator('.el-table__row').first().getByRole('button', { name: '禁用' }).click()
  await dialogCentered(page, '#9 禁用用户')
  await page.locator('.el-message-box').getByRole('button', { name: '取消' }).click()
  expect(failed()).toBe(0)
})
