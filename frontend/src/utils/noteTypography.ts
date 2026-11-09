import { Extension } from '@tiptap/core'

/**
 * 块级排版属性：字体、字号、行距。
 *
 * 不用 TextStyle mark 实现，是因为这三项在日志里通常作用于整段；
 * 挂在 block 上既能在没有选中文字时直接生效，也能完整写进 HTML 的 style。
 * 只接受受控的 CSS 值，避免把任意字符串拼进 style。
 */
export interface NoteTypographyOptions {
  types: string[]
}

const FONT_SIZE = /^\d+(?:\.\d+)?(?:px|rem|em|%)$/
const LINE_HEIGHT = /^\d+(?:\.\d+)?(?:px|rem|em|%)?$/

function clean(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (!trimmed || /[;<>]/.test(trimmed)) return null
  return trimmed
}

function styleValue(element: HTMLElement, name: string): string | null {
  const value = element.style.getPropertyValue(name)
  return value ? value.trim() : null
}

export const NoteTypography = Extension.create<NoteTypographyOptions>({
  name: 'noteTypography',

  addOptions() {
    return { types: ['paragraph', 'heading'] }
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          fontFamily: {
            default: null,
            parseHTML: (element) => clean(styleValue(element, 'font-family')),
            renderHTML: (attributes) => {
              const value = clean(attributes.fontFamily)
              return value ? { style: `font-family: ${value}` } : {}
            },
          },
          fontSize: {
            default: null,
            parseHTML: (element) => {
              const value = clean(styleValue(element, 'font-size'))
              return value && FONT_SIZE.test(value) ? value : null
            },
            renderHTML: (attributes) => {
              const value = clean(attributes.fontSize)
              return value && FONT_SIZE.test(value) ? { style: `font-size: ${value}` } : {}
            },
          },
          lineHeight: {
            default: null,
            parseHTML: (element) => {
              const value = clean(styleValue(element, 'line-height'))
              return value && LINE_HEIGHT.test(value) ? value : null
            },
            renderHTML: (attributes) => {
              const value = clean(attributes.lineHeight)
              return value && LINE_HEIGHT.test(value) ? { style: `line-height: ${value}` } : {}
            },
          },
        },
      },
    ]
  },
})
