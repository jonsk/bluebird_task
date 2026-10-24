import type { NoteScope } from '@/types/note'

/**
 * 列表路由映射（Note 模块前端契约）：scope → 列表页 path。
 * `visible` 无独立列表页，回落到我的日志；`archived` 为归档专用段。
 */
export function noteListRouteOf(scope: NoteScope | 'archived'): string {
  switch (scope) {
    case 'explore':
      return '/notes/explore'
    case 'shared':
      return '/notes/shared'
    case 'internal':
      return '/notes/internal'
    case 'subordinate':
      return '/notes/subordinate'
    case 'all':
      return '/notes/all'
    case 'archived':
      return '/notes/archived'
    case 'visible':
    case 'mine':
    default:
      return '/notes'
  }
}
