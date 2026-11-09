import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import NoteList from '@/components/note/NoteList.vue'

const notes = [
  {
    id: 1001,
    logDate: '2026-09-28',
    ownerName: '张三',
    contentSummary: '今日完成日志模块',
    canEdit: false,
    canManage: false,
  },
]

const stubs = {
  NoteCard: {
    props: ['note'],
    template: '<div class="note-card-stub">{{ note.id }}</div>',
  },
  'el-skeleton': true,
  'el-empty': true,
  'el-button': true,
  'el-pagination': true,
  'el-icon': true,
}

function mountList(loading: boolean) {
  return mount(NoteList, {
    props: { notes, loading, page: 1, total: 1 },
    global: { stubs },
  })
}

describe('NoteList loading', () => {
  it('首屏无数据时展示骨架屏', () => {
    const w = mount(NoteList, {
      props: { notes: [], loading: true, page: 1, total: 0 },
      global: { stubs },
    })
    expect(w.find('.note-list__loading').exists()).toBe(true)
  })

  it('已有数据后台刷新时保留卡片，不切骨架屏', () => {
    const w = mountList(true)
    expect(w.find('.note-list__loading').exists()).toBe(false)
    expect(w.findAll('.note-card-stub')).toHaveLength(1)
  })
})
