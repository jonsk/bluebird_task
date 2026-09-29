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
node capture.mjs                   # 六大视图截图 + 录屏 → docs/frontend-baseline/screenshots/
node capture-components.mjs        # 组件级：左栏/日历/新增块/各下拉/人员选择/标签配置
node capture-extra.mjs             # 详情抽屉打开态
node capture-states.mjs [empty|error|httperr|login]   # 空态 / 异常态截图
node e2e-baseline.mjs              # E2E 基线：对旧前端跑 E-01..E-17（记录真实发出的写端点）
node verify.mjs                    # 断言各视图卡片渲染数（防回归）
node probe.mjs                     # 探针：登录并 dump 线上真实接口形态（勿提交其输出）
```

## 设计

- **不触达线上后端**：`context.route('**/api-server/**')` 对已知接口返回 fixture，未匹配返回空数组。
- **数据引用**：`dataset.json` 中 `"@task:101"` 在运行时展开为 `tasks["101"]`。
- **确定性**：固定视口 `1440×900`、禁用动画；登录响应亦被 fixture 覆盖。
- **安全**：线上真实 `corpSecret`/密码哈希**不入库**（详见 `../../docs/frontend-baseline/fixtures/legacy-api/README.md`）。

## E2E 基线（`e2e-baseline.mjs`）

- 目的：落实 `03 §2.3.3`「**先对旧前端跑通作基线，再对新前端验收**」的**前半段**（原缺可执行脚本）。
- 与截图 harness 同套 fixtures；在路由层**记录**真实发出的写操作端点（`/task/record/add|complete|del`、`/sysTag/add`、`/admin/user/list` 等），并支持 `failRule` 注入失败以验证**失败回滚**。
- 结果：控制台摘要 + `../../docs/frontend-baseline/screenshots/generated/e2e-baseline.json`（不入库）；状态回填至 `../../docs/frontend-baseline/e2e/scenarios.md`。

## 产物

- 截图 → `../../docs/frontend-baseline/screenshots/*.png`
- 录屏 → `../../docs/frontend-baseline/screenshots/recordings/*.webm`
- E2E 结果 → `../../docs/frontend-baseline/screenshots/generated/e2e-baseline.json`
- 本地忽略产物：`node_modules/`、`generated/`（见 `.gitignore`）；`package.json`/`package-lock.json` **入库**以便复现。

## clone 后复现

```bash
cd scripts/golden-capture && npm install
npx playwright install ffmpeg
node capture.mjs
```
