// capture-extra.mjs — component-level shots (task detail drawer)
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, '..', '..');
const dataset = JSON.parse(fs.readFileSync(path.join(REPO, 'docs/frontend-baseline/fixtures/legacy-api/dataset.json'), 'utf8'));
const SHOTS = path.join(REPO, 'docs', 'frontend-baseline', 'screenshots');
const BASE = process.env.BB_BASE || 'http://localhost:8081';
function expand(n){ if(Array.isArray(n))return n.map(expand); if(n&&typeof n==='object'){ if(n.$ref)return dataset.tasks[n.$ref.slice(6)]; const o={}; for(const[k,v]of Object.entries(n))o[k]=expand(v); return o;} if(typeof n==='string'&&n.startsWith('@task:'))return dataset.tasks[n.slice(6)]; return n; }
function resolve(m,u){const U=new URL(u);for(const ep of dataset.endpoints){if(ep.method.toUpperCase()!==m.toUpperCase())continue;if(ep.path!==U.pathname)continue;if(ep.matchQuery&&!U.search.includes(ep.matchQuery))continue;return expand(ep.body);}return [];}
const browser=await chromium.launch({channel:'msedge',headless:true});
const ctx=await browser.newContext({viewport:{width:1440,height:900},deviceScaleFactor:1,locale:'zh-CN'});
await ctx.route('**/api-server/**',r=>r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(resolve(r.request().method(),r.request().url()))}));
const page=await ctx.newPage();
await page.addInitScript(()=>{const c=document.createElement('style');c.textContent='*{transition:none!important;animation:none!important}';document.addEventListener('DOMContentLoaded',()=>document.head.appendChild(c));});
await page.goto(BASE+'/#/login',{waitUntil:'networkidle',timeout:60000});
await page.waitForTimeout(1000);
await page.locator('.el-tabs__item',{hasText:'账号密码'}).first().click();
await page.waitForTimeout(500);
await page.locator('input[placeholder="账号"]').fill('admin');
await page.locator('input[placeholder="密码"]').fill(process.env.BB_PASSWORD || '');
await page.locator('button',{hasText:'登'}).first().click();
await page.waitForTimeout(3000);
await page.goto(BASE+'/#/index',{waitUntil:'networkidle',timeout:40000});
await page.waitForTimeout(2000);
// click first task content to open right detail box
await page.locator('.task-card .truncate').first().click();
await page.waitForTimeout(2500);
const visible = await page.locator('.dialog-right-box').count();
console.log('dialog-right-box count =', visible);
await page.screenshot({ path: path.join(SHOTS, 'task-detail-drawer-1440x900.png') });
// also dump any newly visible large panel text length
console.log('url', page.url());
await browser.close();
