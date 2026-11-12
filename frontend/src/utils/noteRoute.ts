import type { NoteScope } from '@/types/note'

/**
 * 列表路由映射（Note 模块前端契约）：scope → 列表页 path。
 * `visible` 无独立列表页，回落到每日工作记录。
 */
export function noteListRouteOf(scope: NoteScope): string {
  switch (scope) {
    case 'subordinate':
      return '/notes/subordinate'
    case 'all':
      return '/admin/notes'
    case 'visible':
    case 'mine':
    default:
      return '/notes'
  }
}
