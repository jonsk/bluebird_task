import { describe, expect, it } from 'vitest'
import { ERROR_CODE, messageOf } from '@/utils/errorCode'

describe('errorCode', () => {
  it('关键错误码与后端对齐（02 §1.4）', () => {
    expect(ERROR_CODE.UNAUTHENTICATED).toBe(10002)
    expect(ERROR_CODE.VERSION_CONFLICT).toBe(10006)
    expect(ERROR_CODE.TOO_MANY_REQUESTS).toBe(10007)
    expect(ERROR_CODE.ACCOUNT_LOCKED).toBe(20006)
    expect(ERROR_CODE.TOKEN_INVALID).toBe(20005)
  })

  it('messageOf 返回可读文案', () => {
    expect(messageOf(ERROR_CODE.VERSION_CONFLICT)).toContain('刷新')
    expect(messageOf(999999, '兜底')).toBe('兜底')
  })
})
