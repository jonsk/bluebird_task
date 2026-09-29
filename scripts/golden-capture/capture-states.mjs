// capture-states.mjs — empty & error state screenshots for the six views
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, '..', '..');
const SHOTS = path.join(REPO, 'docs', 'frontend-baseline', 'screenshots');
const BASE = process.env.BB_BASE || 'http://10.14.37.187:8081';
const VIEWPORT = { width: 1440, height: 900 };
const ds = JSON.parse(fs.readFileSync(path.join(REPO, 'docs/frontend-baseline/fixtures/legacy-api/dataset.json'), 'utf8'));

function expand(n) {
  if (Array.isArray(n)) return n.map(expand);
  if (n && typeof n === 'object') { if (n.$ref) return ds.tasks[n.$ref.slice(6)]; const o = {}; for (const [k, v] of Object.entries(n)) o[k] = expand(v); return o; }
  if (typeof n === 'string' && n.startsWith('@task:')) return ds.tasks[n.slice(6)];
  return n;
}
const LIST = /\/task\/record\/(day|week|join|do|collect|link)$/;
const COUNT = /\/task\/record\/count$/;

function baseResponse(method, url) {
  const U = new URL(url);
  for (const ep of ds.endpoints) {
    if (ep.method.toUpperCase() !== method.toUpperCase()) continue;
    if (ep.path !== U.pathname) continue;
    if (ep.matchQuery && !U.search.includes(ep.matchQuery)) continue;
    return { status: 200, body: expand(ep.body) };
  }
  return { status: 200, body: [] };
}

async function capture(mode) {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1, locale: 'zh-CN' });
  await ctx.route('**/api-server/**', (r) => {
    const req = r.request();
    const url = req.url();
    // login / myself / tags / wechat / menu / category: always normal
    const passthrough = baseResponse(req.method(), url);
    if (mode === 'empty' && (LIST.test(url.split('?')[0]) || COUNT.test(url.split('?')[0]))) {
      const p = url.split('?')[0];
      if (COUNT.test(p)) return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ all: 0, week: 0, join: 0, do: 0, day: 0, collect: 0 }) });
      // page-shaped vs array-shaped
      const body = /\/(do|link)$/.test(p) ? [] : { records: [], total: 0, size: 9999, current: 1, pages: 0 };
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    }
    if (mode === 'error' && (LIST.test(url.split('?')[0]) || COUNT.test(url.split('?')[0]))) {
      // 旧后端错误形态：HTTP 200 + body.code=500（前端拦截器据 body.code 弹 ElMessage）
      return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: '服务器忙，请稍后重试（fixture 注入的异常态）' }) });
    }
    if (mode === 'httperr' && (LIST.test(url.split('?')[0]) || COUNT.test(url.split('?')[0]))) {
      // HTTP 层 500：旧拦截器静默 reject（无可见提示）——用于记录既有缺陷
      return r.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: 'HTTP500' }) });
    }
    return r.fulfill({ status: passthrough.status, contentType: 'application/json', body: JSON.stringify(passthrough.body) });
  });
  const page = await ctx.newPage();
  await page.addInitScript(() => { const c = document.createElement('style'); c.textContent = '*{transition:none!important;animation:none!important}'; document.addEventListener('DOMContentLoaded', () => document.head.appendChild(c)); });
  // login (normal)
  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1000);
  await page.locator('.el-tabs__item', { hasText: '账号密码' }).first().click();
  await page.waitForTimeout(400);
  await page.locator('input[placeholder="账号"]').fill('admin');
  await page.locator('input[placeholder="密码"]').fill('admin123');
  await page.locator('button', { hasText: '登' }).first().click();
  await page.waitForTimeout(2500);

  const views = [['/index', 'day'], ['/myWeek', 'week'], ['/myJoin', 'joined'], ['/myDo', 'assigned'], ['/myCollect', 'collect'], ['/allTask', 'all']];
  for (const [route, name] of views) {
    await page.goto(BASE + '/#' + route, { waitUntil: 'domcontentloaded', timeout: 40000 }).catch(() => {});
    if (mode === 'error' || mode === 'httperr') { await page.waitForTimeout(300); await page.reload({ waitUntil: 'networkidle' }).catch(() => {}); }
    await page.waitForTimeout(mode === 'error' ? 1600 : 1800);
    const file = `state-${mode}-${name}-1440x900.png`;
    await page.screenshot({ path: path.join(SHOTS, file) });
    const toast = await page.locator('.el-message:visible').count();
    console.log(`${mode} ${route} -> ${file} (toast=${toast})`);
  }
  await browser.close();
}

async function captureLoginError() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: 1, locale: 'zh-CN' });
  await ctx.route('**/api-server/**', (r) => {
    const url = r.request().url();
    if (/\/api\/user\/login$/.test(url.split('?')[0])) {
      return r.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: '用户名或密码错误' }) });
    }
    return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(baseResponse(r.request().method(), url).body) });
  });
  const page = await ctx.newPage();
  await page.addInitScript(() => { const c = document.createElement('style'); c.textContent = '*{transition:none!important;animation:none!important}'; document.addEventListener('DOMContentLoaded', () => document.head.appendChild(c)); });
  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1000);
  await page.locator('.el-tabs__item', { hasText: '账号密码' }).first().click();
  await page.waitForTimeout(400);
  await page.locator('input[placeholder="账号"]').fill('admin');
  await page.locator('input[placeholder="密码"]').fill('wrong-pass');
  await page.locator('button', { hasText: '登' }).first().click();
  await page.waitForTimeout(900);
  const toast = await page.locator('.el-message:visible').count();
  await page.screenshot({ path: path.join(SHOTS, 'state-error-login-1440x900.png') });
  console.log(`login-error -> state-error-login-1440x900.png (toast=${toast})`);
  await browser.close();
}

const modeArg = process.argv[2];
if (!modeArg || modeArg === 'empty') await capture('empty');
if (!modeArg || modeArg === 'error') await capture('error');
if (modeArg === 'httperr') await capture('httperr');
if (!modeArg || modeArg === 'login') await captureLoginError();
console.log('done');
