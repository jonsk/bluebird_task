// lib.mjs — shared setup for component-level capture
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const REPO = path.resolve(__dirname, '..', '..');
export const SHOTS = path.join(REPO, 'docs', 'frontend-baseline', 'screenshots');
export const BASE = process.env.BB_BASE || 'http://10.14.37.187:8081';
export const VIEWPORT = { width: 1440, height: 900 };

export function readDataset() {
  return JSON.parse(fs.readFileSync(path.join(REPO, 'docs/frontend-baseline/fixtures/legacy-api/dataset.json'), 'utf8'));
}
function expand(n, ds) {
  if (Array.isArray(n)) return n.map((x) => expand(x, ds));
  if (n && typeof n === 'object') { if (n.$ref) return ds.tasks[n.$ref.slice(6)]; const o = {}; for (const [k, v] of Object.entries(n)) o[k] = expand(v, ds); return o; }
  if (typeof n === 'string' && n.startsWith('@task:')) return ds.tasks[n.slice(6)];
  return n;
}
function resolve(m, u, ds) {
  const U = new URL(u);
  for (const ep of ds.endpoints) {
    if (ep.method.toUpperCase() !== m.toUpperCase()) continue;
    if (ep.path !== U.pathname) continue;
    if (ep.matchQuery && !U.search.includes(ep.matchQuery)) continue;
    return expand(ep.body, ds);
  }
  return [];
}

export async function setup({ video = false } = {}) {
  const ds = readDataset();
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const opts = { viewport: VIEWPORT, deviceScaleFactor: 1, locale: 'zh-CN' };
  if (video) opts.recordVideo = { dir: path.join(SHOTS, 'recordings'), size: VIEWPORT };
  const context = await browser.newContext(opts);
  await context.route('**/api-server/**', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(resolve(r.request().method(), r.request().url(), ds)) }));
  const page = await context.newPage();
  await page.addInitScript(() => {
    const c = document.createElement('style');
    c.textContent = '*{transition:none!important;animation:none!important}';
    document.addEventListener('DOMContentLoaded', () => document.head.appendChild(c));
  });
  return { browser, context, page, ds };
}

export async function login(page) {
  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(1000);
  await page.locator('.el-tabs__item', { hasText: '账号密码' }).first().click();
  await page.waitForTimeout(500);
  await page.locator('input[placeholder="账号"]').fill('admin');
  await page.locator('input[placeholder="密码"]').fill('admin123');
  await page.locator('button', { hasText: '登' }).first().click();
  await page.waitForTimeout(3000);
}

export const shot = (page, name) => page.screenshot({ path: path.join(SHOTS, name) });
export async function shotEl(page, sel, name) {
  const loc = page.locator(sel).first();
  if (await loc.count()) { await loc.screenshot({ path: path.join(SHOTS, name) }); return true; }
  return false;
}
