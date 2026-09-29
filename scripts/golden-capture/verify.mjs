// verify.mjs — assert the captured pages actually rendered task cards
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, '..', '..');
const dataset = JSON.parse(fs.readFileSync(path.join(REPO, 'docs/frontend-baseline/fixtures/legacy-api/dataset.json'), 'utf8'));
const BASE = process.env.BB_BASE || 'http://10.14.37.187:8081';
function expand(n){ if(Array.isArray(n)) return n.map(expand); if(n&&typeof n==='object'){ if(n.$ref) return dataset.tasks[n.$ref.slice(6)]; const o={}; for(const[k,v]of Object.entries(n))o[k]=expand(v); return o;} if(typeof n==='string'&&n.startsWith('@task:'))return dataset.tasks[n.slice(6)]; return n; }
function resolve(method,url){const u=new URL(url);for(const ep of dataset.endpoints){if(ep.method.toUpperCase()!==method.toUpperCase())continue;if(ep.path!==u.pathname)continue;if(ep.matchQuery&&!u.search.includes(ep.matchQuery))continue;return expand(ep.body);}return [];}
const browser=await chromium.launch({channel:'msedge',headless:true});
const ctx=await browser.newContext({viewport:{width:1440,height:900},locale:'zh-CN'});
await ctx.route('**/api-server/**',r=>{const b=resolve(r.request().method(),r.request().url());return r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(b)});});
const page=await ctx.newPage();
await page.goto(BASE+'/#/login',{waitUntil:'networkidle',timeout:60000});
await page.waitForTimeout(1000);
await page.locator('.el-tabs__item',{hasText:'账号密码'}).first().click();
await page.waitForTimeout(500);
await page.locator('input[placeholder="账号"]').fill('admin');
await page.locator('input[placeholder="密码"]').fill('admin123');
await page.locator('button',{hasText:'登'}).first().click();
await page.waitForTimeout(3000);
const routes=['/index','/myWeek','/myJoin','/myDo','/myCollect','/allTask'];
for(const r of routes){
  await page.goto(BASE+'/#'+r,{waitUntil:'networkidle',timeout:40000});
  await page.waitForTimeout(2000);
  const cards=await page.locator('.task-card').count();
  const titles=await page.locator('.task-card .truncate').allInnerTexts().catch(()=>[]);
  console.log(r.padEnd(11), 'cards=',cards, '|', titles.join(' / '));
}
await browser.close();
