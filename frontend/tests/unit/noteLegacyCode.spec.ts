import { describe, expect, it } from 'vitest'
import { generateJSON } from '@tiptap/core'
import { buildNoteExtensions } from '@/utils/noteEditorExtensions'

describe('旧日志代码内容兼容', () => {
  it('仍能解析行内代码和代码块，重新保存不会丢内容', () => {
    const doc = generateJSON(
      '<p>运行 <code>pnpm test</code></p><pre><code class="language-ts">const a = 1</code></pre>',
      buildNoteExtensions({ mode: 'render' }),
    )

    expect(doc.content?.[0]?.content?.[1]).toMatchObject({
      type: 'text',
      text: 'pnpm test',
      marks: [{ type: 'code' }],
    })
    expect(doc.content?.[1]).toMatchObject({
      type: 'codeBlock',
      attrs: { language: 'ts' },
    })
  })
})
