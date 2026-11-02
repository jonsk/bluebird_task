import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * NoteCard：纯展示卡片。断言：
 * 1) 点击卡片 emit open(id)；2) canEdit / canManage 控制按钮显隐；
 * 3) 操作区点击不冒泡成 open。置顶能力已下线，不再有置顶按钮/徽标。
 */

const ElButton = {
  name: 'ElButton',
  props: ['text', 'size', 'type', 'icon', 'title', 'disabled'],
  emits: ['click'],
  template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>',
}
const ElIcon = { name: 'ElIcon', template: '<i><slot /></i>' }
const ElTag = { name: 'ElTag', template: '<span class="tag"><slot /></span>' }

vi.mock('@element-plus/icons-vue', () => {
  const icon = { template: '<i />' }
  return {
    Delete: icon,
    Edit: icon,
  }
})

import NoteCard from '@/components/note/NoteCard.vue'

type Note = Record<string, unknown>

function base(over: Note = {}): Note {
  return {
    id: 1001,
    logDate: '2026-09-28',
    ownerName: '张三',
    contentSummary: '今日完成日志模块',
    canEdit: false,
    canManage: false,
    ...over,
  }
}

function mountCard(note: Note) {
  return mount(NoteCard, {
    props: { note },
    global: { components: { ElButton, ElIcon, ElTag } },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('NoteCard', () => {
  it('渲染日期/作者/摘要，点击卡片 emit open', async () => {
    const w = mountCard(base())
    expect(w.text()).toContain('2026-09-28')
    expect(w.text()).toContain('张三')
    expect(w.text()).toContain('今日完成日志模块')
    await w.trigger('click')
    expect(w.emitted('open')).toEqual([[1001]])
  })

  it('无摘要时显示占位文案', () => {
    const w = mountCard(base({ contentSummary: '' }))
    expect(w.text()).toContain('（无内容）')
  })

  it('canEdit=false 且 canManage=false：不渲染操作区', () => {
    const w = mountCard(base())
    expect(w.find('[data-test="note-card-delete"]').exists()).toBe(false)
  })

  it('canManage 但非 canEdit：只有删除', () => {
    const w = mountCard(base({ canManage: true }))
    expect(w.find('[data-test="note-card-delete"]').exists()).toBe(true)
  })

  it('删除按钮 emit remove，且操作区点击不会触发 open', async () => {
    const w = mountCard(base({ canEdit: true, canManage: true }))
    await w.find('[data-test="note-card-delete"]').trigger('click')
    expect(w.emitted('remove')).toEqual([[1001]])
    expect(w.emitted('open')).toBeUndefined()
  })

  it('编辑按钮 emit edit（canEdit 才渲染）', async () => {
    const w = mountCard(base({ canEdit: true }))
    await w.find('[data-test="note-card-edit"]').trigger('click')
    expect(w.emitted('edit')).toEqual([[1001]])
    expect(mountCard(base()).find('[data-test="note-card-edit"]').exists()).toBe(false)
  })

  it('active 时加 is-active 类', () => {
    const w = mount(NoteCard, {
      props: { note: base(), active: true },
      global: { components: { ElButton, ElIcon, ElTag } },
    })
    expect(w.classes()).toContain('is-active')
  })
})
