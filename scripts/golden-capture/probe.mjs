// probe.mjs — exploratory: login to legacy BlueBird and dump API traffic + screenshot
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.BB_BASE || 'http://10.14.37.187:8081';
const OUT = process.env.BB_OUT || 'X:/TEMP/opencode/bb-probe';
fs.mkdirSync(OUT, { recursive: true });

const apiLog = [];

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' });
const page = await context.newPage();

page.on('console', (m) => { if (m.type() === 'error') console.log('[console.error]', m.text()); });
page.on('response', async (res) => {
  const url = res.url();
  if (url.includes('/api-server/')) {
    let body = null;
    try { body = await res.text(); } catch {}
    apiLog.push({ method: res.request().method(), url, status: res.status(), body: body?.slice(0, 4000) });
  }
});

console.log('goto login...');
await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 60000 });
await page.waitForTimeout(1500);
await page.screenshot({ path: path.join(OUT, '00-login.png') });

// switch to account-login tab (label is 账号密码)
try {
  const tab = page.locator('.el-tabs__item', { hasText: '账号密码' });
  if (await tab.count()) { await tab.first().click(); await page.waitForTimeout(800); }
  else { console.log('account tab not found, count=', await tab.count()); }
} catch (e) { console.log('tab err', e.message); }

const u = page.locator('input[placeholder="账号"]');
await u.waitFor({ state: 'visible', timeout: 15000 });
await u.fill('admin');
await page.locator('input[placeholder="密码"]').first().fill('admin123');
await page.screenshot({ path: path.join(OUT, '01-login-filled.png') });

await page.locator('button', { hasText: '登' }).first().click();
await page.waitForTimeout(4000);
console.log('after login url =', page.url());
await page.screenshot({ path: path.join(OUT, '02-after-login.png') });

// try each route
const routes = ['/index', '/myWeek', '/myJoin', '/myDo', '/myCollect', '/allTask'];
for (const r of routes) {
  try {
    await page.goto(BASE + '/#' + r, { waitUntil: 'networkidle', timeout: 40000 });
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(OUT, `route${r.replace('/', '-')}.png`) });
    console.log('captured', r, 'title=', await page.title());
  } catch (e) { console.log('route err', r, e.message); }
}

fs.writeFileSync(path.join(OUT, 'api-log.json'), JSON.stringify(apiLog, null, 2));
console.log('API calls captured:', apiLog.length);
await browser.close();
