import { describe, expect, it } from 'vitest'
import { firstFontFamily, withCurrentValue, type SelectOption } from '@/utils/toolbarSelect'

const options: SelectOption[] = [
  { label: '字体', value: '' },
  { label: '宋体', value: '"Songti SC", SimSun, serif' },
]

describe('toolbarSelect', () => {
  it('空值原样返回，落到占位项', () => {
    expect(withCurrentValue(options, '')).toEqual(options)
  })

  it('当前值已在选项表里时不再补充', () => {
    const current = '"Songti SC", SimSun, serif'
    expect(withCurrentValue(options, current)).toEqual(options)
  })

  it('当前值不在选项表里时补到最前面，避免下拉框空白', () => {
    const result = withCurrentValue(options, '微软雅黑')
    expect(result).toHaveLength(3)
    expect(result[0]).toEqual({ label: '微软雅黑', value: '微软雅黑' })
  })

  it('未知字体用第一个族名做显示名', () => {
    const result = withCurrentValue(options, '"Microsoft YaHei", 微软雅黑, sans-serif', firstFontFamily)
    expect(result[0]).toEqual({ label: 'Microsoft YaHei', value: '"Microsoft YaHei", 微软雅黑, sans-serif' })
  })

  it('族名带单引号也能去掉', () => {
    expect(firstFontFamily("'Microsoft YaHei', sans-serif")).toBe('Microsoft YaHei')
  })

  it('不修改传入的选项数组', () => {
    withCurrentValue(options, '微软雅黑')
    expect(options).toHaveLength(2)
  })
})
