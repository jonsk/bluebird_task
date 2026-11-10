/*
 * 首帧应用持久化主题，避免刷新闪白/FOUC（与 stores/app.ts 的 bb-theme 键一致）。
 * 独立文件而非 index.html 内联：CSP 禁 script-src 'self'，内联脚本会被浏览器拦截，
 * 导致主题要等 Vue 挂载后才生效，刷新出现白闪。改这里就要同步 theme 枚举与 HIGHLIGHT_STYLE。
 */
(function () {
  try {
    var AUTH_PATHS = ['/login', '/oauth/success'];
    var path = location.pathname || '/';
    // 登录 / 回调页固定浅色，与路由 meta.forceLight 一致；深色用户直接访问也不会闪暗
    if (AUTH_PATHS.indexOf(path.replace(/\/$/, '')) !== -1) {
      document.documentElement.setAttribute('data-theme', 'light-soft');
      document.documentElement.setAttribute('data-hl', 'soft');
      document.documentElement.classList.remove('dark');
      return;
    }
    var t = localStorage.getItem('bb-theme');
    // 旧 key：light-classic -> light-soft；dark -> dark-soft
    if (t === 'light-classic') t = 'light-soft';
    if (t === 'dark') t = 'dark-soft';
    if (['light-soft', 'light-vivid', 'light-contrast', 'dark-soft', 'dark-vivid'].indexOf(t) === -1) {
      t = 'light-soft';
    }
    document.documentElement.setAttribute('data-theme', t);
    // 高亮显示族（编辑器四色高亮用哪套映射），与 stores/app.ts 的 HIGHLIGHT_STYLE 一致
    document.documentElement.setAttribute(
      'data-hl',
      t === 'light-soft' || t === 'dark-soft' ? 'soft' : 'vivid',
    );
    // Element Plus 官方暗色变量挂在 .dark 上，首帧同步类名，避免组件先按浅色渲染
    document.documentElement.classList.toggle('dark', t.indexOf('dark') === 0);
  } catch (e) {
    /* 隐私模式/禁用 localStorage 时静默回退到默认主题 */
  }
})();
