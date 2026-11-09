export interface NoteEmojiItem {
  name: string
  emoji?: string
  shortcodes?: string[]
  tags?: string[]
  group?: string
}

/**
 * 日志编辑器常用的 Emoji 子集。
 *
 * 不直接使用官方 Emoji 扩展内置的全量数据（数万条、体积很大）：
 * 这里保留工作沟通最常用的一批，输入 `:名称` 或从工具栏选择都可以。
 */
export const NOTE_EMOJIS: NoteEmojiItem[] = [
  { name: 'smile', emoji: '😄', shortcodes: ['smile'], tags: ['happy', 'joy'], group: 'people' },
  { name: 'laugh', emoji: '😆', shortcodes: ['laugh'], tags: ['happy'], group: 'people' },
  { name: 'wink', emoji: '😉', shortcodes: ['wink'], tags: ['happy'], group: 'people' },
  { name: 'blush', emoji: '😊', shortcodes: ['blush'], tags: ['happy'], group: 'people' },
  { name: 'thinking', emoji: '🤔', shortcodes: ['thinking'], tags: ['think'], group: 'people' },
  { name: 'neutral', emoji: '😐', shortcodes: ['neutral'], tags: ['meh'], group: 'people' },
  { name: 'cry', emoji: '😢', shortcodes: ['cry'], tags: ['sad'], group: 'people' },
  { name: 'angry', emoji: '😠', shortcodes: ['angry'], tags: ['mad'], group: 'people' },
  { name: 'sweat', emoji: '😅', shortcodes: ['sweat'], tags: ['relief'], group: 'people' },
  { name: 'thumbsup', emoji: '👍', shortcodes: ['thumbsup', '+1'], tags: ['ok', 'yes'], group: 'people' },
  { name: 'thumbsdown', emoji: '👎', shortcodes: ['thumbsdown', '-1'], tags: ['no'], group: 'people' },
  { name: 'clap', emoji: '👏', shortcodes: ['clap'], tags: ['applause'], group: 'people' },
  { name: 'pray', emoji: '🙏', shortcodes: ['pray'], tags: ['thanks'], group: 'people' },
  { name: 'muscle', emoji: '💪', shortcodes: ['muscle'], tags: ['strong'], group: 'people' },
  { name: 'wave', emoji: '👋', shortcodes: ['wave'], tags: ['hello'], group: 'people' },
  { name: 'ok_hand', emoji: '👌', shortcodes: ['ok_hand'], tags: ['ok'], group: 'people' },
  { name: 'eyes', emoji: '👀', shortcodes: ['eyes'], tags: ['look'], group: 'people' },
  { name: 'heart', emoji: '❤️', shortcodes: ['heart'], tags: ['love'], group: 'symbols' },
  { name: 'broken_heart', emoji: '💔', shortcodes: ['broken_heart'], tags: ['sad'], group: 'symbols' },
  { name: 'star', emoji: '⭐', shortcodes: ['star'], tags: ['favorite'], group: 'symbols' },
  { name: 'fire', emoji: '🔥', shortcodes: ['fire'], tags: ['hot'], group: 'symbols' },
  { name: 'sparkles', emoji: '✨', shortcodes: ['sparkles'], tags: ['new'], group: 'symbols' },
  { name: 'tada', emoji: '🎉', shortcodes: ['tada'], tags: ['party'], group: 'symbols' },
  { name: 'rocket', emoji: '🚀', shortcodes: ['rocket'], tags: ['launch'], group: 'symbols' },
  { name: 'bulb', emoji: '💡', shortcodes: ['bulb'], tags: ['idea'], group: 'symbols' },
  { name: 'warning', emoji: '⚠️', shortcodes: ['warning'], tags: ['warn'], group: 'symbols' },
  { name: 'check', emoji: '✅', shortcodes: ['check', 'white_check_mark'], tags: ['done'], group: 'symbols' },
  { name: 'x', emoji: '❌', shortcodes: ['x'], tags: ['no'], group: 'symbols' },
  { name: 'question', emoji: '❓', shortcodes: ['question'], tags: ['help'], group: 'symbols' },
  { name: 'exclamation', emoji: '❗', shortcodes: ['exclamation'], tags: ['important'], group: 'symbols' },
  { name: 'plus', emoji: '➕', shortcodes: ['plus'], tags: ['add'], group: 'symbols' },
  { name: 'minus', emoji: '➖', shortcodes: ['minus'], tags: ['remove'], group: 'symbols' },
  { name: 'arrow_right', emoji: '➡️', shortcodes: ['arrow_right'], tags: ['next'], group: 'symbols' },
  { name: 'arrow_left', emoji: '⬅️', shortcodes: ['arrow_left'], tags: ['back'], group: 'symbols' },
  { name: 'arrow_up', emoji: '⬆️', shortcodes: ['arrow_up'], tags: ['up'], group: 'symbols' },
  { name: 'arrow_down', emoji: '⬇️', shortcodes: ['arrow_down'], tags: ['down'], group: 'symbols' },
  { name: 'memo', emoji: '📝', shortcodes: ['memo'], tags: ['note'], group: 'objects' },
  { name: 'book', emoji: '📖', shortcodes: ['book'], tags: ['read'], group: 'objects' },
  { name: 'books', emoji: '📚', shortcodes: ['books'], tags: ['library'], group: 'objects' },
  { name: 'calendar', emoji: '📅', shortcodes: ['calendar'], tags: ['date'], group: 'objects' },
  { name: 'clock', emoji: '🕒', shortcodes: ['clock'], tags: ['time'], group: 'objects' },
  { name: 'pushpin', emoji: '📌', shortcodes: ['pushpin'], tags: ['pin'], group: 'objects' },
  { name: 'paperclip', emoji: '📎', shortcodes: ['paperclip'], tags: ['attach'], group: 'objects' },
  { name: 'link', emoji: '🔗', shortcodes: ['link'], tags: ['url'], group: 'objects' },
  { name: 'lock', emoji: '🔒', shortcodes: ['lock'], tags: ['secure'], group: 'objects' },
  { name: 'key', emoji: '🔑', shortcodes: ['key'], tags: ['access'], group: 'objects' },
  { name: 'computer', emoji: '💻', shortcodes: ['computer'], tags: ['code'], group: 'objects' },
  { name: 'phone', emoji: '📱', shortcodes: ['phone'], tags: ['mobile'], group: 'objects' },
  { name: 'email', emoji: '✉️', shortcodes: ['email'], tags: ['mail'], group: 'objects' },
  { name: 'chart', emoji: '📈', shortcodes: ['chart'], tags: ['growth'], group: 'objects' },
  { name: 'package', emoji: '📦', shortcodes: ['package'], tags: ['box'], group: 'objects' },
  { name: 'bug', emoji: '🐛', shortcodes: ['bug'], tags: ['issue'], group: 'nature' },
  { name: 'coffee', emoji: '☕', shortcodes: ['coffee'], tags: ['break'], group: 'food' },
  { name: 'beer', emoji: '🍺', shortcodes: ['beer'], tags: ['drink'], group: 'food' },
  { name: 'cake', emoji: '🎂', shortcodes: ['cake'], tags: ['birthday'], group: 'food' },
  { name: 'sun', emoji: '☀️', shortcodes: ['sun'], tags: ['weather'], group: 'nature' },
  { name: 'cloud', emoji: '☁️', shortcodes: ['cloud'], tags: ['weather'], group: 'nature' },
  { name: 'rain', emoji: '🌧️', shortcodes: ['rain'], tags: ['weather'], group: 'nature' },
  { name: 'snow', emoji: '❄️', shortcodes: ['snow'], tags: ['weather'], group: 'nature' },
  { name: 'cat', emoji: '🐱', shortcodes: ['cat'], tags: ['pet'], group: 'nature' },
  { name: 'dog', emoji: '🐶', shortcodes: ['dog'], tags: ['pet'], group: 'nature' },
]
