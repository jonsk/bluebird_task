import { Extension } from '@tiptap/core'
import { Plugin } from '@tiptap/pm/state'

/**
 * 编辑态下 Ctrl/Cmd + 单击链接时新标签页打开。
 *
 * 编辑态的 Link 扩展关闭了 openOnClick，普通单击只用于定位光标；
 * 这里只接管带修饰键的点击，避免影响正常的链接文字编辑。
 */
export const NoteLinkClick = Extension.create({
  name: 'noteLinkClick',

  addProseMirrorPlugins() {
    return [
      new Plugin({
        props: {
          handleClick(view, _pos, event) {
            if (!view.editable) return false
            if (!(event.metaKey || event.ctrlKey)) return false

            const target = event.target
            if (!(target instanceof Element)) return false

            const anchor = target.closest('a')
            if (!(anchor instanceof HTMLAnchorElement) || !view.dom.contains(anchor)) return false

            const href = anchor.getAttribute('href')
            if (!href || /^javascript:/i.test(href)) return false

            event.preventDefault()
            window.open(href, '_blank', 'noopener,noreferrer')
            return true
          },
        },
      }),
    ]
  },
})
