import { describe, it, expect } from 'vitest'
import { noteListRouteOf } from '@/utils/noteRoute'
import { ERROR_CODE, messageOf } from '@/utils/errorCode'

/**
 * note 路由映射（7 个列表页 + 归档）与 note 错误码文案（§8.4）。
 * 路由映射错会让"左栏点了却停在旧页"，错误码漏映射会让 403/404 显示成通用文案。
 */
describe('noteListRouteOf', () => {
  it.each([
    ['mine', '/notes'],
    ['explore', '/notes/explore'],
    ['shared', '/notes/shared'],
    ['internal', '/notes/internal'],
    ['subordinate', '/notes/subordinate'],
    ['all', '/notes/all'],
    ['archived', '/notes/archived'],
  ] as const)('%s → %s', (scope, path) => {
    expect(noteListRouteOf(scope)).toBe(path)
  })

  it('visible 无独立列表页，回落到我的日志', () => {
    expect(noteListRouteOf('visible')).toBe('/notes')
  })
})

describe('note 错误码文案', () => {
  it('note 域 50001–50021 均有中文文案（不落到"操作失败"）', () => {
    const codes = [
      ERROR_CODE.NOTE_NOT_FOUND,
      ERROR_CODE.NOTE_TAG_INVALID,
      ERROR_CODE.NOTE_GRANT_NOT_SUBORDINATE,
      ERROR_CODE.NOTE_SCOPE_DISABLED,
      ERROR_CODE.NOTE_DEPT_REQUIRED,
      ERROR_CODE.VERSION_CONFLICT,
    ]
    for (const code of codes) {
      const msg = messageOf(code)
      expect(msg).toBeTruthy()
      expect(msg).not.toBe('操作失败')
    }
  })
})
