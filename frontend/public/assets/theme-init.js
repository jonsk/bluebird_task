/*
 * 首帧应用持久化主题，避免刷新闪白/FOUC（与 stores/app.ts 的 bb-theme 键一致）。
 * 独立文件而非 index.html 内联：CSP 为 script-src 'self'，内联脚本会被浏览器拦截，
 * 导致主题要等 Vue 挂载后才生效，刷新出现白闪。改这里就要同步 theme 枚举。
 */
(function () {
  try {
    var AUTH_PATHS = ['/login', '/oauth/success'];
    var path = location.pathname || '/';
    // 登录 / 回调页固定浅色，与路由 meta.forceLight 一致；深色用户直接访问也不会闪暗
    if (AUTH_PATHS.indexOf(path.replace(/\/$/, '')) !== -1) {
      document.documentElement.setAttribute('data-theme', 'light-classic');
      document.documentElement.classList.remove('dark');
      return;
    }
    var t = localStorage.getItem('bb-theme');
    // 旧版本只有 'dark'，映射到深色·柔和
    if (t === 'dark') t = 'dark-soft';
    if (['light-classic', 'dark-soft', 'dark-vivid'].indexOf(t) === -1) t = 'light-classic';
    document.documentElement.setAttribute('data-theme', t);
    // Element Plus 官方暗色变量挂在 .dark 上，首帧同步类名，避免组件先按浅色渲染
    document.documentElement.classList.toggle('dark', t !== 'light-classic');
  } catch (e) {
    /* 隐私模式/禁用 localStorage 时静默回退到默认主题 */
  }
})();
