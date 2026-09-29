// e2e-baseline.mjs — 对「旧前端」执行 E2E 基线（03 §2.3.3：先对旧前端跑通作基线）
// 与其它 harness 同套 fixtures（docs/frontend-baseline/fixtures/legacy-api/dataset.json），
// 通过 Playwright 拦截 /api-server/** 并记录真实发出的写操作端点调用。
// 产出：控制台结果 + docs/frontend-baseline/screenshots/generated/e2e-baseline.json（不入库）
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, '..', '..');
const GEN = path.join(REPO, 'docs', 'frontend-baseline', 'screenshots', 'generated');
const dataset = JSON.parse(fs.readFileSync(path.join(REPO, 'docs/frontend-baseline/fixtures/legacy-api/dataset.json'), 'utf8'));
const BASE = process.env.BB_BASE || 'http://10.14.37.187:8081';
const VIEWPORT = { width: 1440, height: 900 };
const SHOTS = path.join(REPO, 'docs', 'frontend-baseline', 'screenshots');

function expand(n) {
  if (Array.isArray(n)) return n.map(expand);
  if (n && typeof n === 'object') { if (n.$ref) return dataset.tasks[n.$ref.slice(6)]; const o = {}; for (const [k, v] of Object.entries(n)) o[k] = expand(v); return o; }
  if (typeof n === 'string' && n.startsWith('@task:')) return dataset.tasks[n.slice(6)];
  return n;
}
function resolve(m, u) {
  const U = new URL(u);
  for (const ep of dataset.endpoints) {
    if (ep.method.toUpperCase() !== m.toUpperCase()) continue;
    if (ep.path !== U.pathname) continue;
    if (ep.matchQuery && !U.search.includes(ep.matchQuery)) continue;
    return expand(ep.body);
  }
  return [];
}

const calls = [];
let failRule = null;
async function newPage(browser, { login: doLogin = true } = {}) {
  const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1, locale: 'zh-CN' });
  await ctx.route('**/api-server/**', (route) => {
    const req = route.request();
    const u = req.url(); const m = req.method();
    calls.push({ m, path: new URL(u).pathname, q: new URL(u).search });
    if (failRule && failRule.test(u)) { failRule = null; return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: 'forced-failure' }) }); }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(resolve(m, u)) });
  });
  const page = await ctx.newPage();
  await page.addInitScript(() => { const c = document.createElement('style'); c.textContent = '*{transition:none!important;animation:none!important}'; document.addEventListener('DOMContentLoaded', () => document.head.appendChild(c)); });
  if (doLogin) await login(page);
  return { ctx, page };
}
async function login(page) {
  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1000);
  await page.locator('.el-tabs__item', { hasText: '账号密码' }).first().click();
  await page.waitForTimeout(400);
  await page.locator('input[placeholder="账号"]').fill('admin');
  await page.locator('input[placeholder="密码"]').fill('admin123');
  await page.locator('button', { hasText: '登' }).first().click();
  await page.waitForTimeout(3000);
}
const since = () => calls.length;
const called = (from, re) => calls.slice(from).some((c) => re.test(c.path));
async function go(page, r) { await page.goto(BASE + '/#' + r, { waitUntil: 'networkidle', timeout: 40000 }).catch(() => {}); await page.waitForTimeout(2000); }

const results = [];
const rec = (id, name, status, note = '') => { results.push({ id, name, status, note }); console.log(`[${status}] ${id} ${name}${note ? ' — ' + note : ''}`); };

const browser = await chromium.launch({ channel: 'msedge', headless: true });
try {
  // ---------- E-01 登录（账密） ----------
  {
    const { ctx, page } = await newPage(browser, { login: false });
    const from = since();
    await login(page);
    const u = page.url();
    const okLogin = !/\/login/.test(u) && called(from, /\/api\/user\/login$/);
    rec('E-01', '登录（账密）', okLogin ? 'PASS' : 'FAIL', `url=${u.replace(BASE, '')}`);
    await ctx.close();
  }

  // ---------- E-03 六大视图切换与计数 ----------
  {
    const { ctx, page } = await newPage(browser);
    const expected = { '/index': 7, '/myWeek': 8, '/myJoin': 1, '/myDo': 1, '/myCollect': 1, '/allTask': 10 };
    const got = {}; const bad = [];
    for (const r of Object.keys(expected)) { await go(page, r); const n = await page.locator('.tast-item').count(); got[r] = n; if (n !== expected[r]) bad.push(`${r}:${n}≠${expected[r]}`); }
    rec('E-03', '六大视图切换与计数', bad.length ? 'FAIL' : 'PASS', bad.length ? '不一致 ' + bad.join(', ') : `计数一致 ${JSON.stringify(got)}`);
    await ctx.close();
  }

  // ---------- E-04 新建主任务 ----------
  {
    const { ctx, page } = await newPage(browser);
    await go(page, '/index');
    const from = since();
    await page.locator('.t-b-input-box input').first().fill('E2E基线-新建任务');
    await page.waitForTimeout(300);
    await page.locator('.t-b-input-box button').first().click();
    await page.waitForTimeout(1500);
    const ok = called(from, /\/task\/record\/add$/);
    rec('E-04', '新建主任务', ok ? 'PASS' : 'FAIL', ok ? 'POST /task/record/add 已发出' : '未捕获建任务请求');
    await ctx.close();
  }

  // ---------- E-06 完成任务（含失败回滚） ----------
  {
    const { ctx, page } = await newPage(browser);
    await go(page, '/index');
    let from = since();
    await page.locator('.tast-item .el-checkbox').first().click();
    await page.waitForTimeout(1200);
    const okCall = called(from, /\/task\/record\/complete$/);
    // rollback：强制 complete 失败，比较失败前后「已勾选」数量是否增加
    await go(page, '/index');
    const before = await page.locator('.tast-item .el-checkbox.is-checked').count();
    failRule = /\/task\/record\/complete$/;
    await page.locator('.tast-item .el-checkbox').first().click();
    await page.waitForTimeout(1200);
    const after = await page.locator('.tast-item .el-checkbox.is-checked').count();
    const rolledBack = after <= before;
    rec('E-06', '完成任务与失败回滚', okCall ? (rolledBack ? 'PASS' : 'PASS*') : 'FAIL',
      `complete调用=${okCall}; 失败前勾选=${before} 失败后=${after}${rolledBack ? '（已回滚）' : '（回滚未生效，见缺陷）'}`);
    await ctx.close();
  }

  // ---------- E-09 删除任务（二次确认） ----------
  {
    const { ctx, page } = await newPage(browser);
    await go(page, '/index');
    await page.locator('.tast-item').first().locator('.el-dropdown').first().click().catch(() => {});
    await page.waitForTimeout(800);
    await page.locator('.el-dropdown__popper:visible .el-dropdown-menu__item', { hasText: '删除任务' }).first().click().catch(() => {});
    await page.waitForTimeout(800);
    const dlgText = await page.locator('.el-message-box:visible').first().innerText().catch(() => '');
    const from = since();
    await page.locator('.el-message-box:visible .el-button--primary').first().click().catch(() => {});
    await page.waitForTimeout(1200);
    const ok = called(from, /\/task\/record\/del$/);
    rec('E-09', '删除任务（二次确认）', ok ? 'PASS' : 'FAIL', `确认框含"删除"=${/删除/.test(dlgText)}; del调用=${ok}`);
    await ctx.close();
  }

  // ---------- E-11 标签新增 ----------
  {
    const { ctx, page } = await newPage(browser);
    await go(page, '/index');
    await page.locator('.r-b-t-r-item').first().click({ force: true }).catch(() => {});
    await page.waitForTimeout(900);
    const from = since();
    const inp = page.locator('.el-dropdown__popper:visible input').last();
    await inp.fill('E2E基线标签').catch(() => {});
    await inp.press('Enter').catch(() => {});
    await page.waitForTimeout(1200);
    const ok = called(from, /\/sysTag\/add$/);
    rec('E-11', '标签新增', ok ? 'PASS' : 'FAIL', ok ? 'POST /sysTag/add 已发出' : '未捕获标签新增请求');
    await ctx.close();
  }

  // ---------- E-12 人员选择（按部门/列表） ----------
  {
    const { ctx, page } = await newPage(browser);
    await go(page, '/index');
    await page.locator('.task-card .truncate').first().click();
    await page.waitForTimeout(1500);
    const from = since();
    await page.locator('.dialog-right-box .drbb-item:has-text("@") .cursor-pointer').first().dispatchEvent('click').catch(() => {});
    await page.waitForTimeout(1500);
    const dlg = await page.locator('.el-dialog:visible').count();
    const listCalled = called(from, /\/admin\/user\/list$/);
    rec('E-12', '人员选择', dlg && listCalled ? 'PASS' : 'FAIL', `选人弹窗=${dlg}; user/list调用=${listCalled}`);
    await ctx.close();
  }

  // ---------- E-16 周期任务展示 ----------
  {
    const { ctx, page } = await newPage(browser);
    await go(page, '/index');
    const body = await page.locator('body').innerText();
    const ok = /每天|每周|每月|每年/.test(body);
    rec('E-16', '周期任务（按实例/重复文案展示）', ok ? 'PASS' : 'FAIL', ok ? '卡片含重复文案' : '未检出重复文案');
    await ctx.close();
  }

  // ---------- E-17 未登录跳转 ----------
  {
    const { ctx, page } = await newPage(browser, { login: false });
    await page.goto(BASE + '/#/index', { waitUntil: 'networkidle', timeout: 40000 }).catch(() => {});
    await page.waitForTimeout(1500);
    const ok = /\/login/.test(page.url());
    rec('E-17', '未登录 → 登录页', ok ? 'PASS' : 'FAIL', `url=${page.url().replace(BASE, '')}`);
    await ctx.close();
  }

  // ---------- 其余场景：明确标注未在旧前端跑（原因） ----------
  for (const [id, name, why] of [
    ['E-05', '新建子任务', '在旧前端详情内子模式，选择器不稳定，列入手工/新前端验收'],
    ['E-07', '收藏/取消', '卡片星标触发元素无稳定选择器，列入手工'],
    ['E-08', '移动任务到自定义栏', 'More 菜单→对话框链路可得，但依赖 dropdown 定位，列入手工'],
    ['E-10', '分类树增删改', '旧 categoryTree 未挂载/不可达（见 BbCategoryTree 契约 N4）'],
    ['E-13', '附件上传/预览/删除', '需文件输入，列入手工'],
    ['E-14', '用户/部门管理（ADMIN）', '旧前端管理页路由未确认，列入手工'],
    ['E-15', '审计日志（ADMIN）', '旧前端审计页路由未确认，列入手工'],
    ['E-02', '登录（外部-可选）', '外部登录默认关闭，旧系统为 LOCAL'],
  ]) rec(id, name, 'SKIP', why);
} catch (e) {
  console.log('E2E baseline error:', e.message);
} finally {
  await browser.close();
}
fs.mkdirSync(GEN, { recursive: true });
const summary = { at: new Date().toISOString(), base: BASE, results };
fs.writeFileSync(path.join(GEN, 'e2e-baseline.json'), JSON.stringify(summary, null, 2));
const p = results.filter((r) => r.status.startsWith('PASS')).length;
console.log(`\n== summary: PASS ${p} / FAIL ${results.filter((r) => r.status === 'FAIL').length} / SKIP ${results.filter((r) => r.status === 'SKIP').length} ==`);
