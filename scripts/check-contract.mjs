#!/usr/bin/env node
/**
 * check-contract.mjs — 契约一致性 CI 门禁（0306/Q6）
 *
 * 校验 `docs/api/openapi.yaml`（API 单一事实源）与设计文档/基线之间的一致性，不一致即「红」（退出码 1）：
 *   1) openapi 内部自洽：所有 `#/components/**` 的 `$ref` 均可解析；
 *   2) 端点一致：`openapi.paths` ↔ 设计文档接口表（`Task/01 §9`、`Task/02` 各模块）逐条 diff（双向）；
 *   3) 错误码一致：`Task/02 §1.4 ErrorCode` 枚举 ↔ `docs/frontend-baseline/fixtures/api/_errors/*.json`（双向）。
 *
 * 用法（仓根）：
 *   node scripts/check-contract.mjs
 * 依赖 `js-yaml`（若未随仓安装，可用 NODE_PATH 指向已装目录）：
 *   NODE_PATH=X:\TEMP\opencode\node_modules node scripts/check-contract.mjs
 */

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const require = createRequire(import.meta.url);
const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');

// ---- 校验豁免（有充分理由的、经评审确认的偏差；键为归一化 "METHOD /path"）----
const ENDPOINT_IGNORE = new Set([
  // 示例：'GET /internal/health',
]);

// 写操作（POST/PUT/PATCH）可无请求体的合理豁免（无 body 语义）
const BODY_EXEMPT = new Set([
  'POST /auth/logout',
  'POST /org/sync',
  'POST /jobs/{name}/run',
  'POST /tasks/{id}/collect',
]);

const C = { red: '\x1b[31m', green: '\x1b[32m', yellow: '\x1b[33m', dim: '\x1b[2m', reset: '\x1b[0m' };
const fail = [];
const note = [];

// ---------- 载入 js-yaml ----------
let yaml;
try {
  yaml = require('js-yaml');
} catch {
  console.error(`${C.red}[setup] 缺少依赖 js-yaml。${C.reset}
  安装：  npm i -D js-yaml
  或指定：NODE_PATH=<含 js-yaml 的 node_modules> node scripts/check-contract.mjs`);
  process.exit(2);
}

// ---------- 工具 ----------
const normPath = (p) => p.replace(/^\/api\/v1/, '').replace(/\?.*$/, '').replace(/\/+$/, '') || '/';
const opKey = (m, p) => `${m.toUpperCase()} ${normPath(p)}`;

function walkRefs(node, cb) {
  if (Array.isArray(node)) return node.forEach((n) => walkRefs(n, cb));
  if (node && typeof node === 'object') {
    for (const [k, v] of Object.entries(node)) {
      if (k === '$ref' && typeof v === 'string') cb(v);
      else walkRefs(v, cb);
    }
  }
}

function resolvePointer(doc, ref) {
  if (!ref.startsWith('#/')) return null;
  return ref.slice(2).split('/').reduce((acc, seg) => (acc == null ? acc : acc[seg.replace(/~1/g, '/').replace(/~0/g, '~')]), doc);
}

// ---------- 0. openapi 解析 ----------
const openapiPath = join(ROOT, 'docs/api/openapi.yaml');
if (!existsSync(openapiPath)) {
  console.error(`${C.red}[setup] 未找到 ${openapiPath}${C.reset}`);
  process.exit(2);
}
let api;
try {
  api = yaml.load(readFileSync(openapiPath, 'utf8'));
} catch (e) {
  console.error(`${C.red}[fail] openapi.yaml 解析失败：${e.message}${C.reset}`);
  process.exit(1);
}

// 0.1 服务器前缀
const servers = (api.servers || []).map((s) => s.url || '');
if (!servers.some((u) => u.includes('/api/v1'))) {
  fail.push(`openapi.servers 未声明 '/api/v1' 前缀（实际：${JSON.stringify(servers)}）`);
}

// 0.2 $ref 完整性
const refs = new Set();
walkRefs(api, (r) => refs.add(r));
const brokenRefs = [...refs].filter((r) => r.startsWith('#/') && resolvePointer(api, r) == null);
if (brokenRefs.length) fail.push(`openapi 存在无法解析的 $ref（${brokenRefs.length}）：\n    - ${brokenRefs.join('\n    - ')}`);

// ---------- 1. 端点一致 ----------
const apiOps = new Set();
for (const [p, ops] of Object.entries(api.paths || {})) {
  for (const m of Object.keys(ops)) apiOps.add(opKey(m, p));
}

const DOC_SOURCES = ['Task/02后端模块详细设计.md', 'Task/01蓝鸟重构方案.md'];
const docOps = new Map(); // key -> sourceFile
const rowRe = /^\|\s*(GET|POST|PUT|DELETE|PATCH)\s*\|\s*(\/api\/v1\/[^|\s]*)/;
for (const rel of DOC_SOURCES) {
  const abs = join(ROOT, rel);
  if (!existsSync(abs)) { note.push(`跳过（不存在）：${rel}`); continue; }
  for (const line of readFileSync(abs, 'utf8').split(/\r?\n/)) {
    const m = line.match(rowRe);
    if (m) docOps.set(opKey(m[1], m[2]), rel);
  }
}

const missingInApi = [...docOps.keys()].filter((k) => !apiOps.has(k) && !ENDPOINT_IGNORE.has(k)).sort();
const missingInDoc = [...apiOps].filter((k) => !docOps.has(k) && !ENDPOINT_IGNORE.has(k)).sort();
if (missingInApi.length)
  fail.push(`设计文档已列、但 openapi 缺失的端点（${missingInApi.length}）：\n    - ${missingInApi.map((k) => `${k}  ${C.dim}(${docOps.get(k)})${C.reset}`).join('\n    - ')}`);
if (missingInDoc.length)
  fail.push(`openapi 已定义、但设计文档接口表未列的端点（${missingInDoc.length}）：\n    - ${missingInDoc.join('\n    - ')}`);

// ---------- 2. 写操作请求体 ----------
const noBody = [];
for (const [p, ops] of Object.entries(api.paths || {})) {
  for (const [m, op] of Object.entries(ops)) {
    const key = opKey(m, p);
    if (['POST', 'PUT', 'PATCH'].includes(m.toUpperCase()) && !op.requestBody && !BODY_EXEMPT.has(key)) noBody.push(key);
  }
}
if (noBody.length)
  fail.push(`写操作缺请求体 schema（${noBody.length}）：\n    - ${noBody.sort().join('\n    - ')}`);

// ---------- 3. 错误码一致 ----------
const errDocPath = join(ROOT, 'Task/02后端模块详细设计.md');
const errDoc = new Map(); // code -> name
{
  const txt = readFileSync(errDocPath, 'utf8');
  // 表格行：| 10000 | SYSTEM_ERROR | ... |
  const re = /^\|\s*(\d{3,5})\s*\|\s*([A-Z_]{2,})\s*\|/;
  for (const line of txt.split(/\r?\n/)) {
    const m = line.match(re);
    if (m) errDoc.set(Number(m[1]), m[2].trim());
  }
}
const errDocSet = new Set([...errDoc.keys()].filter((c) => c !== 0));

const errDir = join(ROOT, 'docs/frontend-baseline/fixtures/api/_errors');
const errFixture = new Map(); // code -> filename
if (existsSync(errDir)) {
  for (const f of readdirSync(errDir).filter((n) => n.endsWith('.json'))) {
    try {
      const j = JSON.parse(readFileSync(join(errDir, f), 'utf8'));
      if (typeof j.code === 'number') errFixture.set(j.code, f);
      else fail.push(`错误态 fixture 缺少数值 code：${f}`);
    } catch (e) {
      fail.push(`错误态 fixture JSON 非法：${f}（${e.message}）`);
    }
  }
} else {
  note.push(`跳过错误态校验（不存在 ${errDir}）`);
}
const errMissingFixture = [...errDocSet].filter((c) => !errFixture.has(c)).sort((a, b) => a - b);
const errExtraFixture = [...errFixture.keys()].filter((c) => !errDocSet.has(c)).sort((a, b) => a - b);
if (errMissingFixture.length)
  fail.push(`ErrorCode 已定义、但缺 fixture：${errMissingFixture.map((c) => `${c} ${errDoc.get(c)}`).join(' / ')}`);
if (errExtraFixture.length)
  fail.push(`fixture 含 ErrorCode 未定义之码：${errExtraFixture.map((c) => `${c} (${errFixture.get(c)})`).join(' / ')}`);

// ---------- 报告 ----------
const line = '─'.repeat(60);
console.log(line);
console.log(`${C.dim}openapi:${C.reset} paths=${Object.keys(api.paths || {}).length} ops=${apiOps.size} schemas=${Object.keys((api.components || {}).schemas || {}).length} $refs=${refs.size}`);
console.log(`${C.dim}docs:${C.reset} 端点=${docOps.size}  ·  ErrorCode(非0)=${errDocSet.size}  ·  _errors fixtures=${errFixture.size}`);
if (note.length) console.log(`${C.yellow}note:${C.reset}\n  - ${note.join('\n  - ')}`);
console.log(line);

if (fail.length) {
  console.log(`${C.red}✗ 契约不一致（${fail.length} 项）：${C.reset}`);
  for (const f of fail) console.log(`  ${C.red}• ${f}${C.reset}`);
  console.log(line);
  process.exit(1);
}

console.log(`${C.green}✓ 契约一致：端点 与 openapi 吻合；ErrorCode 与错误态 fixtures 吻合；$ref 全部可解析。${C.reset}`);
process.exit(0);
