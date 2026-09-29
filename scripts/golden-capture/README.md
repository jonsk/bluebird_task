# golden-capture — 旧前端黄金截图/录屏 harness

用 Playwright 驱动**旧前端**（线上隔离环境），拦截 `/api-server/**` 注入 `docs/frontend-baseline/fixtures/legacy-api/dataset.json`，导出**确定性**黄金截图与录屏（`Task/03前端模块详细设计.md §2.3.2`）。

## 前置

- Node 18+；本机有 **Microsoft Edge**（用 `channel: 'msedge'`，**无需**下载 Chromium）；录屏需 ffmpeg：`npx playwright install ffmpeg`。
- 旧系统可访问：`http://10.14.37.187:8081/`（可用 `BB_BASE` 覆盖）。

## 使用

```bash
cd scripts/golden-capture
npm install
npx playwright install ffmpeg      # 仅录屏需要
node capture.mjs                   # 采集截图 + 录屏 → docs/frontend-baseline/screenshots/
node capture-components.mjs        # 组件级：左栏/日历/新增块/各下拉/人员选择/标签配置
node verify.mjs                    # 断言各视图卡片渲染数（防回归）
node probe.mjs                     # 探针：登录并 dump 线上真实接口形态（勿提交其输出）
```

## 设计

- **不触达线上后端**：`context.route('**/api-server/**')` 对已知接口返回 fixture，未匹配返回空数组。
- **数据引用**：`dataset.json` 中 `"@task:101"` 在运行时展开为 `tasks["101"]`。
- **确定性**：固定视口 `1440×900`、禁用动画；登录响应亦被 fixture 覆盖。
- **安全**：线上真实 `corpSecret`/密码哈希**不入库**（详见 `../../docs/frontend-baseline/fixtures/legacy-api/README.md`）。

## 产物

- 截图 → `../../docs/frontend-baseline/screenshots/*.png`
- 录屏 → `../../docs/frontend-baseline/screenshots/recordings/*.webm`
- `node_modules/`、`package-lock.json`、`out/` 为本地产物（见 `.gitignore`）。

## clone 后复现

```bash
cd scripts/golden-capture && npm install
npx playwright install ffmpeg
node capture.mjs
```
