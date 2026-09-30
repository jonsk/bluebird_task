// capture.mjs — deterministic golden capture of the legacy BlueBird frontend.
// Intercepts all /api-server/** and serves docs/frontend-baseline/fixtures/legacy-api/dataset.json,
// so screenshots are reproducible and no real secrets/live data are hit.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, '..', '..');
const FIXTURES = path.join(REPO, 'docs', 'frontend-baseline', 'fixtures', 'legacy-api', 'dataset.json');
const SHOTS = path.join(REPO, 'docs', 'frontend-baseline', 'screenshots');
const RECS = path.join(SHOTS, 'recordings');

const BASE = process.env.BB_BASE || 'http://localhost:8081';
const VIEWPORT = { width: 1440, height: 900 };

const dataset = JSON.parse(fs.readFileSync(FIXTURES, 'utf8'));

function expand(node) {
  if (Array.isArray(node)) return node.map(expand);
  if (node && typeof node === 'object') {
    if (typeof node.$ref === 'string' && node.$ref.startsWith('@task:')) {
      return dataset.tasks[node.$ref.slice(6)];
    }
    const out = {};
    for (const [k, v] of Object.entries(node)) out[k] = expand(v);
    return out;
  }
  if (typeof node === 'string' && node.startsWith('@task:')) {
    return dataset.tasks[node.slice(6)];
  }
  return node;
}

function resolveResponse(method, urlStr) {
  const u = new URL(urlStr);
  const p = u.pathname;
  const q = u.search;
  for (const ep of dataset.endpoints) {
    if (ep.method.toUpperCase() !== method.toUpperCase()) continue;
    if (ep.path !== p) continue;
    if (ep.matchQuery && !q.includes(ep.matchQuery)) continue;
    return { status: 200, body: expand(ep.body), name: ep.name };
  }
  return null;
}

async function installRoutes(context) {
  await context.route('**/api-server/**', async (route) => {
    const req = route.request();
    const hit = resolveResponse(req.method(), req.url());
    if (!hit) {
      // Unknown API: return an empty benign payload, never touch the live server.
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
    let body = hit.body;
    if (typeof body === 'string') body = JSON.stringify(body);
    else body = JSON.stringify(body);
    return route.fulfill({ status: hit.status, contentType: 'application/json', body });
  });
}

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: 1,
  locale: 'zh-CN',
  recordVideo: { dir: RECS, size: VIEWPORT },
});
await installRoutes(context);
const page = await context.newPage();
await page.addInitScript(() => {
  const css = document.createElement('style');
  css.textContent = '*{transition:none!important;animation:none!important;caret-color:transparent!important}';
  document.addEventListener('DOMContentLoaded', () => document.head.appendChild(css));
});

async function login() {
  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1200);
  const tab = page.locator('.el-tabs__item', { hasText: '账号密码' });
  if (await tab.count()) { await tab.first().click(); await page.waitForTimeout(600); }
  await page.screenshot({ path: path.join(SHOTS, 'login-account-1440x900.png') });
  const u = page.locator('input[placeholder="账号"]');
  await u.waitFor({ state: 'visible', timeout: 15000 });
  await u.fill('admin');
  await page.locator('input[placeholder="密码"]').first().fill(process.env.BB_PASSWORD || '');
  await page.locator('button', { hasText: '登' }).first().click();
  await page.waitForTimeout(3500);
  console.log('after login url =', page.url());
}

async function shotRoute(routeKey, fileBase) {
  await page.goto(BASE + '/#' + routeKey, { waitUntil: 'networkidle', timeout: 45000 });
  await page.waitForTimeout(2200);
  await page.screenshot({ path: path.join(SHOTS, `${fileBase}-1440x900.png`) });
  console.log('shot', fileBase);
}

await login();
await shotRoute('/index', 'tasklist-day');
await shotRoute('/myWeek', 'tasklist-week');
await shotRoute('/myJoin', 'tasklist-joined');
await shotRoute('/myDo', 'tasklist-assigned');
await shotRoute('/myCollect', 'tasklist-collect');
await shotRoute('/allTask', 'tasklist-all');

// Interaction recording: complete a task (checkbox) on 我的一天
try {
  await page.goto(BASE + '/#/index', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  const cb = page.locator('.tast-item .el-checkbox').first();
  if (await cb.count()) {
    await cb.click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(SHOTS, 'tasklist-day-complete-1440x900.png') });
    console.log('recorded completion interaction');
  }
} catch (e) { console.log('interaction err', e.message); }

await context.close();
await browser.close();
console.log('done. shots in', SHOTS);
