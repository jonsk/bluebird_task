import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * NoteCard：纯展示卡片。断言 4 类：
 * 1) 点击卡片 emit open(id)；2) canEdit / canManage 控制按钮显隐；
 * 3) 置顶/归档按钮按当前状态 emit pin|unpin、archive|unarchive；
 * 4) 操作区点击不冒泡成 open。
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
    StarFilled: icon,
    Star: icon,
    Folder: icon,
    FolderOpened: icon,
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
    pinned: false,
    archived: false,
    canEdit: false,
    canManage: false,
    tags: [],
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
    expect(w.find('[data-test="note-card-pin"]').exists()).toBe(false)
    expect(w.find('[data-test="note-card-archive"]').exists()).toBe(false)
    expect(w.find('[data-test="note-card-delete"]').exists()).toBe(false)
  })

  it('canManage 但非 canEdit：只有删除，没有置顶/归档', () => {
    const w = mountCard(base({ canManage: true }))
    expect(w.find('[data-test="note-card-delete"]').exists()).toBe(true)
    expect(w.find('[data-test="note-card-pin"]').exists()).toBe(false)
    expect(w.find('[data-test="note-card-archive"]').exists()).toBe(false)
  })

  it('未置顶点置顶 → emit pin；已置顶点 → emit unpin', async () => {
    const w = mountCard(base({ canEdit: true }))
    await w.find('[data-test="note-card-pin"]').trigger('click')
    expect(w.emitted('pin')).toEqual([[1001]])
    expect(w.emitted('unpin')).toBeUndefined()

    const w2 = mountCard(base({ canEdit: true, pinned: true }))
    await w2.find('[data-test="note-card-pin"]').trigger('click')
    expect(w2.emitted('unpin')).toEqual([[1001]])
  })

  it('归档按钮按 archived 分流，并在置顶时渲染 is-on 与徽标', async () => {
    const w = mountCard(base({ canEdit: true, archived: false }))
    await w.find('[data-test="note-card-archive"]').trigger('click')
    expect(w.emitted('archive')).toEqual([[1001]])

    const w2 = mountCard(base({ canEdit: true, archived: true }))
    await w2.find('[data-test="note-card-archive"]').trigger('click')
    expect(w2.emitted('unarchive')).toEqual([[1001]])
    expect(w2.text()).toContain('已归档')

    const w3 = mountCard(base({ canEdit: true, pinned: true }))
    const pin = w3.find('[data-test="note-card-pin"]')
    expect(pin.classes()).toContain('is-on')
    expect(w3.find('.note-card__badge--pin').exists()).toBe(true)
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

  it('渲染标签列表', () => {
    const w = mountCard(base({ tags: [{ id: 1, name: '周报' }, { id: 2, name: '复盘' }] }))
    expect(w.findAll('.tag').map((t) => t.text())).toEqual(['周报', '复盘'])
  })

  it('active 时加 is-active 类', () => {
    const w = mount(NoteCard, {
      props: { note: base(), active: true },
      global: { components: { ElButton, ElIcon, ElTag } },
    })
    expect(w.classes()).toContain('is-active')
  })
})
