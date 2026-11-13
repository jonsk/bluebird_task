import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import NoteMiniList from '@/components/note/NoteMiniList.vue'

const notes = [
  { id: 1, logDate: '2026-11-16', contentSummary: '完成周记录改版' },
  { id: 2, logDate: '2026-11-23', contentSummary: '' },
]

const stubs = {
  'el-skeleton': true,
  'el-empty': true,
  'el-button': true,
}

describe('NoteMiniList', () => {
  it('周记录显示周期范围，未来组可加标签并隐藏查看全部', () => {
    const w = mount(NoteMiniList, {
      props: {
        notes,
        period: 'WEEK',
        showFooter: false,
        tagText: '未来',
      },
      global: { stubs },
    })

    expect(w.text()).toContain('2026-11-16 ~ 2026-11-22')
    expect(w.findAll('.note-mini-list__tag')).toHaveLength(2)
    expect(w.find('.note-mini-list__footer').exists()).toBe(false)
  })

  it('默认保留「查看全部」入口', () => {
    const w = mount(NoteMiniList, {
      props: { notes: notes.slice(0, 1), period: 'WEEK' },
      global: { stubs },
    })

    expect(w.find('.note-mini-list__footer').exists()).toBe(true)
  })
})
