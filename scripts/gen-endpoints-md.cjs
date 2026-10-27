// 从 openapi.yaml 生成 docs/api/endpoints.md（权威端点索引，check-contract 的文档侧来源之一）
const { readFileSync, writeFileSync } = require('node:fs');
const { join, resolve } = require('node:path');
const yaml = require('js-yaml');

const ROOT = resolve(__dirname, '..');
const api = yaml.load(readFileSync(join(ROOT, 'docs/api/openapi.yaml'), 'utf8'));

const TAG_ZH = {
  auth: '认证', user: '用户', org: '组织', task: '任务', tag: '标签',
  note: '工作日志', file: '文件', audit: '审计', admin: '后台配置', job: '调度',
};

const rows = [];
const paths = Object.keys(api.paths || {}).sort();
for (const p of paths) {
  for (const [m, op] of Object.entries(api.paths[p])) {
    if (!['get', 'post', 'put', 'patch', 'delete'].includes(m)) continue;
    const tag = (op.tags || [])[0] || '';
    const summary = (op.summary || '').replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
    rows.push(`| ${m.toUpperCase()} | /api/v1${p} | ${TAG_ZH[tag] || tag} | ${summary} |`);
  }
}

const head = `# API 端点总览（自动生成）

> 本文由 \`scripts/gen-endpoints-md.cjs\` 从 \`docs/api/openapi.yaml\` 生成，**请勿手工编辑**。
> 它是 \`scripts/check-contract.mjs\` 的文档侧来源之一：openapi 增删端点后重新生成，否则门禁会红。

| 方法 | 路径 | 模块 | 说明 |
| --- | --- | --- | --- |
`;

writeFileSync(join(ROOT, 'docs/api/endpoints.md'), head + rows.join('\n') + '\n', 'utf8');
console.log(`written docs/api/endpoints.md: ${rows.length} ops / ${paths.length} paths`);
