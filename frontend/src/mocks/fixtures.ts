/**
 * 契约 fixtures 直连（`docs/frontend-baseline/fixtures/api/**`）。
 *
 * 依据 03 §2.3.2 / `fixtures/README.md §5`：新旧前端共用**同一份 fixtures**。
 * MSW handler 不再内联 mock 数据，而是直接消费基线 fixtures（单一事实源）——
 * 改动 fixture 即同步到 dev mock、单元测试与 E2E，消除「内联数据 ↔ fixture」漂移。
 *
 * 键名规则：`<目录>/<METHOD>.<name>`（去掉 `.json`），如 `tasks/GET.list`、`_errors/10001.param`。
 */

/** 后端统一响应体（02 §1.3）。 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

const PREFIX = '../../../docs/frontend-baseline/fixtures/api/'

const modules = import.meta.glob('../../../docs/frontend-baseline/fixtures/api/**/*.json', {
  eager: true,
  import: 'default',
}) as Record<string, ApiResult>

const registry: Record<string, ApiResult> = {}
for (const [path, mod] of Object.entries(modules)) {
  registry[path.slice(PREFIX.length).replace(/\.json$/, '')] = mod
}

/** 按 fixture 键取完整信封（`ApiResult`）。 */
export function envelope<T = unknown>(key: string): ApiResult<T> {
  const hit = registry[key]
  if (!hit) {
    throw new Error(`[mocks] fixture not found: ${key}；已注册：${Object.keys(registry).sort().join(', ')}`)
  }
  return hit as ApiResult<T>
}

/** 按 fixture 键取成功态 `data`。 */
export function dataOf<T = unknown>(key: string): T {
  return envelope<T>(key).data
}

/** 取错误态信封（`_errors/<code>.<name>`，`code != 0`）。 */
export function errorEnvelope(code: number | string, name: string): ApiResult {
  return envelope(`_errors/${code}.${name}`)
}

/** 已注册 fixture 键（升序），供覆盖度测试断言。 */
export function fixtureKeys(): string[] {
  return Object.keys(registry).sort()
}
