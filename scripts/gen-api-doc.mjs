#!/usr/bin/env node
/**
 * gen-api-doc.mjs — 由 openapi.yaml 生成人类可读的 API 接口文档（Markdown）
 *
 * 用法（仓根）：
 *   node scripts/gen-api-doc.mjs [输出路径]
 * 默认输出：../Task/API接口文档.md（评审工作区，不入 git）
 * 依赖：js-yaml（见 scripts/package.json）
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const require = createRequire(import.meta.url);
const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..');

let yaml;
try { yaml = require('js-yaml'); } catch {
  console.error('缺少 js-yaml：npm -C scripts install');
  process.exit(2);
}

const api = yaml.load(readFileSync(join(ROOT, 'docs/api/openapi.yaml'), 'utf8'));
const outArg = process.argv[2];
const outPath = outArg ? resolve(outArg) : resolve(ROOT, '../Task/API接口文档.md');

const schemas = (api.components || {}).schemas || {};
const responses = (api.components || {}).responses || {};
const params = (api.components || {}).parameters || {};

const base = (api.servers || []).map((s) => s.url).join(', ') || '';

function refName(sch) {
  if (!sch) return '—';
  if (sch.$ref) return '`' + sch.$ref.split('/').pop() + '`';
  if (sch.allOf) return sch.allOf.map(refName).join(' + ');
  if (sch.type === 'array') return `${refName(sch.items)}[]`;
  return sch.type || 'object';
}

function schemaTable(name, depth = 0) {
  const s = schemas[name];
  if (!s || !s.properties) return `\`${name}\`（无字段定义）`;
  const rows = ['| 字段 | 类型 | 说明 |', '|---|---|---|'];
  for (const [k, v] of Object.entries(s.properties)) {
    const type = v.type === 'array' ? `${refName(v.items)}[]` : (v.enum ? `enum: ${v.enum.join(' / ')}` : (v.type || refName(v)));
    rows.push(`| \`${k}\` | ${type} | ${(v.description || '').replace(/\|/g, '\\|')} |`);
  }
  return rows.join('\n') + (s.description ? `\n\n> ${s.description}` : '');
}

// 错误码（取自 Task/02 §1.4）
function errorCodes() {
  const p = join(ROOT, 'Task/02后端模块详细设计.md');
  if (!existsSync(p)) return [];
  const out = [];
  for (const line of readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\|\s*(\d{3,5})\s*\|\s*([A-Z_]{2,})\s*\|\s*([^|]+)\|/);
    if (m) out.push([Number(m[1]), m[2].trim(), m[3].trim()]);
  }
  return out.sort((a, b) => a[0] - b[0]);
}

const L = [];
const now = new Date().toISOString().replace('T', ' ').slice(0, 16);
L.push(`# BlueBird 任务系统 · API 接口文档（重写目标态）`);
L.push('');
L.push(`> 生成自 \`docs/api/openapi.yaml\`（单一事实源）· openapi ${api.info?.version || ''} · 生成时间 ${now}`);
L.push('> 本文件为**评审工作区副本**（不入 git）。');
L.push('');
L.push(`- **Base URL**：\`${base}\``);
L.push(`- **统一响应**：\`ApiResult{code,message,data,traceId}\`，\`code==0\` 成功；**HTTP 恒 200**（业务码表达结果，ADR-005）`);
L.push(`- **鉴权**：默认 \`Authorization: Bearer <JWT>\`；标注「匿名」者除外`);
L.push('');

const codes = errorCodes();
if (codes.length) {
  L.push('## 错误码');
  L.push('');
  L.push('| code | 名称 | 说明 |');
  L.push('|---|---|---|');
  for (const [c, n, d] of codes) L.push(`| ${c} | ${n} | ${d.replace(/\|/g, '\\|')} |`);
  L.push('');
}

// 接口（按 openapi paths 顺序，分组到 tag）
const tagOrder = (api.tags || []).map((t) => t.name);
const byTag = new Map(tagOrder.map((t) => [t, []]));
for (const [p, ops] of Object.entries(api.paths || {})) {
  for (const [m, op] of Object.entries(ops)) {
    const tag = (op.tags && op.tags[0]) || 'misc';
    if (!byTag.has(tag)) byTag.set(tag, []);
    byTag.get(tag).push({ m: m.toUpperCase(), p, op });
  }
}

L.push('## 接口');
L.push('');
for (const [tag, list] of byTag) {
  if (!list.length) continue;
  L.push(`### ${tag}`);
  L.push('');
  for (const { m, p, op } of list) {
    const anon = Array.isArray(op.security) && op.security.length === 0;
    L.push(`#### \`${m} /api/v1${p}\`${op.summary ? ' — ' + op.summary : ''}`);
    L.push('');
    L.push(`- 鉴权：${anon ? '匿名' : 'Bearer JWT'}`);
    // 参数
    const ps = [];
    for (const prm of op.parameters || []) {
      if (prm.$ref) { const r = params[prm.$ref.split('/').pop()] || {}; ps.push([r.name || '?', r.in || '?', r.required ? '是' : '否', refName(r.schema)]); }
      else ps.push([prm.name, prm.in, prm.required ? '是' : '否', prm.enum ? `enum: ${prm.enum.join('/')}` : (prm.schema?.type || '—')]);
    }
    if (ps.length) {
      L.push('- 参数：');
      L.push('');
      L.push('  | 名 | 位置 | 必填 | 类型 |');
      L.push('  |---|---|---|---|');
      for (const [n, i, r, t] of ps) L.push(`  | \`${n}\` | ${i} | ${r} | ${t} |`);
    }
    // 请求体
    const rb = op.requestBody?.content?.['application/json']?.schema;
    if (rb) L.push(`- 请求体：${refName(rb)}`);
    // 响应
    const rs = Object.entries(op.responses || {}).map(([code, r]) => {
      if (r.$ref) return `${code} → \`${r.$ref.split('/').pop()}\``;
      return `${code}${r.description ? ' (' + r.description + ')' : ''}`;
    });
    if (rs.length) L.push(`- 响应：${rs.join('；')}`);
    L.push('');
  }
}

// 数据模型
L.push('## 数据模型（schemas）');
L.push('');
for (const name of Object.keys(schemas)) {
  L.push(`### ${name}`);
  L.push('');
  L.push(schemaTable(name));
  L.push('');
}

writeFileSync(outPath, L.join('\n'), 'utf8');
console.log(`已生成：${outPath}（${L.length} 行）`);
