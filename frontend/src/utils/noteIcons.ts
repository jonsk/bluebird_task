/**
 * 日志编辑器统一图标集。
 *
 * 工具栏、气泡菜单、斜杠菜单共用同一套 24×24 线性图标，避免出现
 * “列表按钮是一个点、代码块是 { }”这类字符占位不一致的问题。
 * 路径按 Lucide 的线性风格整理，颜色统一继承 currentColor。
 */
export type NoteIconName =
  | 'paragraph'
  | 'heading1'
  | 'heading2'
  | 'heading3'
  | 'bold'
  | 'italic'
  | 'underline'
  | 'strike'
  | 'code'
  | 'textColor'
  | 'highlight'
  | 'bulletList'
  | 'orderedList'
  | 'taskList'
  | 'link'
  | 'quote'
  | 'codeBlock'
  | 'horizontalRule'
  | 'details'
  | 'image'
  | 'undo'
  | 'redo'
  | 'clear'
  | 'table'
  | 'alignLeft'
  | 'alignCenter'
  | 'alignRight'
  | 'alignJustify'
  | 'indent'
  | 'outdent'
  | 'toc'
  | 'emoji'
  | 'attachment'

export const noteIconPaths: Record<NoteIconName, string[]> = {
  paragraph: ['M13 4v16', 'M17 4v16', 'M19 4H9.5a4.5 4.5 0 0 0 0 9H13'],
  heading1: ['M4 18V6', 'M12 18V6', 'M4 12h8', 'M17 10l3-2v10'],
  heading2: ['M4 18V6', 'M12 18V6', 'M4 12h8', 'M16 10a2 2 0 0 1 4 0c0 2-4 3-4 8h4'],
  heading3: [
    'M4 18V6',
    'M12 18V6',
    'M4 12h8',
    'M16 9a2 2 0 0 1 4 0c0 1.5-2 2-3 2 1 0 3 .5 3 2.5a2.5 2.5 0 0 1-5 0',
  ],
  bold: ['M6 12h9a4 4 0 0 1 0 8H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h7a4 4 0 0 1 0 8'],
  italic: ['M19 4h-9', 'M14 20H5', 'M15 4 9 20'],
  underline: ['M6 4v6a6 6 0 0 0 12 0V4', 'M4 20h16'],
  strike: ['M16 4H9a3 3 0 0 0-2.83 4', 'M14 12a4 4 0 0 1 0 8H6', 'M4 12h16'],
  code: ['m16 18 6-6-6-6', 'm8 6-6 6 6 6'],
  textColor: ['M4 20h16', 'M7 16 12 4l5 12', 'M9 12h6'],
  highlight: ['m9 11-6 6v3h3l6-6', 'M14 4l6 6', 'm12 6 6 6'],
  bulletList: [
    'M8 6h13',
    'M8 12h13',
    'M8 18h13',
    'M3 6a1 1 0 1 0 2 0a1 1 0 1 0-2 0',
    'M3 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0',
    'M3 18a1 1 0 1 0 2 0a1 1 0 1 0-2 0',
  ],
  orderedList: [
    'M10 6h11',
    'M10 12h11',
    'M10 18h11',
    'M4 6h1v4',
    'M4 10h2',
    'M6 18H4c0-1 2-2 2-3s-1-1.5-2-1.5',
  ],
  taskList: ['M10 6h11', 'M10 12h11', 'M10 18h11', 'm3 6 1 1 2-2', 'm3 12 1 1 2-2', 'm3 18 1 1 2-2'],
  link: [
    'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71',
    'M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71',
  ],
  quote: ['M8 7H5a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h3V7Z', 'M20 7h-3a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h3V7Z', 'M8 14v3', 'M20 14v3'],
  codeBlock: ['M4 4h16v16H4z', 'm10 9-3 3 3 3', 'm14 9 3 3-3 3'],
  horizontalRule: ['M4 12h16'],
  details: ['M4 4h16v16H4z', 'M15 4v16', 'm9 10 2 2-2 2'],
  image: [
    'M4 5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z',
    'm4 15 4-4 4 4 2-2 6 6',
    'M8 8h.01',
  ],
  undo: ['M9 14 4 9l5-5', 'M4 9h10a6 6 0 0 1 0 12h-3'],
  redo: ['m15 14 5-5-5-5', 'M20 9H10a6 6 0 0 0 0 12h3'],
  clear: ['m7 21-4.3-4.3a1 1 0 0 1 0-1.4L14 4a2 2 0 0 1 2.8 0l3.2 3.2a2 2 0 0 1 0 2.8L9 21Z', 'M22 21H7', 'm5 11 9 9'],
  table: ['M3 3h18v18H3z', 'M3 9h18', 'M3 15h18', 'M9 3v18', 'M15 3v18'],
  alignLeft: ['M4 6h16', 'M4 12h10', 'M4 18h13'],
  alignCenter: ['M4 6h16', 'M7 12h10', 'M6 18h12'],
  alignRight: ['M4 6h16', 'M10 12h10', 'M7 18h13'],
  alignJustify: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  indent: ['M3 6h18', 'M3 12h8', 'M3 18h18', 'm15 9 3 3-3 3'],
  outdent: ['M3 6h18', 'M3 12h8', 'M3 18h18', 'm18 9-3 3 3 3'],
  toc: ['M4 6h2', 'M8 6h12', 'M4 12h2', 'M8 12h9', 'M4 18h2', 'M8 18h12'],
  emoji: [
    'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z',
    'M8.5 9h.01',
    'M15.5 9h.01',
    'M8.5 14.5a4.5 4.5 0 0 0 7 0',
  ],
  attachment: [
    'm21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48',
  ],
}
