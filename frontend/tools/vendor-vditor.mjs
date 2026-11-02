/**
 * 把 Vditor 运行期需要的静态资源拷到 `public/vditor/dist/**`（Vite 原样发布到站点根）。
 *
 * 为什么不在运行时用 CDN：Vditor 的 IR 模式依赖 lute 引擎、工具栏图标、i18n 词条，
 * 这些都是按 `options.cdn` 前缀**动态插入 <script>/<link>** 加载的，不能靠 bundler 打包。
 * 注意 Vditor 拼的是 `${cdn}/dist/js/...`，所以 `cdn` 指向包根、资源放在 `dist/` 下：
 * 站点路径为 `/vditor/dist/js/lute/lute.min.js`，配置 `cdn: '/vditor'`。
 *
 * 只拷实际会用到的子集（lute / icons / i18n / highlight.js / katex / mermaid / css / images），
 * 其余（echarts、plantuml、graphviz、mathjax…）不拷，省 ~10MB。
 *
 * 产物目录已在 .gitignore 中忽略：`pnpm install` 后由 `predev`/`prebuild` 自动生成。
 */
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')
const src = join(dirname(require.resolve('vditor/package.json')), 'dist')
const dest = join(root, 'public', 'vditor', 'dist')

const KEEP = [
  'js/lute',
  'js/icons',
  'js/i18n',
  'js/highlight.js',
  'js/katex',
  'js/mermaid',
  'css',
  'images',
]

if (!existsSync(src)) {
  console.error(`[vendor-vditor] 找不到 vditor 的 dist：${src}（先 pnpm install）`)
  process.exit(1)
}

rmSync(dest, { recursive: true, force: true })
mkdirSync(dest, { recursive: true })

for (const rel of KEEP) {
  const from = join(src, rel)
  if (!existsSync(from)) {
    console.warn(`[vendor-vditor] 跳过缺失目录：${rel}`)
    continue
  }
  cpSync(from, join(dest, rel), { recursive: true })
}

console.log(`[vendor-vditor] -> ${dest}（${KEEP.length} 个子目录）`)
