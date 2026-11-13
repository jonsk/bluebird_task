import TaskItem from '@tiptap/extension-task-item'

/**
 * 待办项 NodeView 覆盖：不保留无障碍隐藏 span。
 *
 * Tiptap 3.33 的 TaskItem NodeView 每次 update 都会执行
 * `checkboxStyler.textContent = label`。这不是单纯改文本——它在编辑器 DOM 内部
 * 先删掉旧的文本节点再插入新的，Chromium 在该结构变更后恢复选区会退回一格。
 * 组合输入（中文标点，如「。」）上屏时最明显，表现为打完标点光标左移一格，
 * 连续出现会累积成落后 2、4、6 格。普通段落与有序/无序列表没有这段代码，故不受影响。
 *
 * 这里在 NodeView 创建、dom 尚未插入文档的阶段摘掉 span：此时 ProseMirror 的
 * DOMObserver 观察不到该变更，不会触发重绘（在文档内直接删除会被重绘还原，
 * 甚至因反复增删卡死）。无障碍名称由 checkbox 自身的 aria-label 提供，
 * 上游的 updateA11Y 仍在维护它，屏幕阅读器不受影响。
 */
export const NoteTaskItem = TaskItem.extend({
  addNodeView() {
    const parentFactory = this.parent?.()
    if (!parentFactory) {
      return null
    }

    return (props) => {
      const nodeView = parentFactory(props)
      nodeView.dom?.querySelector('label > span')?.remove()
      return nodeView
    }
  },
})
