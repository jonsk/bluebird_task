/*
 * 首帧应用持久化主题，避免刷新闪白/FOUC（与 stores/app.ts 的 bb-theme 键一致）。
 * 独立文件而非 index.html 内联：CSP 为 script-src 'self'，内联脚本会被浏览器拦截，
 * 导致主题要等 Vue 挂载后才生效，刷新出现白闪。改这里就要同步 theme 枚举。
 */
(function () {
  try {
    var t = localStorage.getItem('bb-theme');
    if (['light-classic', 'dark'].indexOf(t) === -1) t = 'light-classic';
    document.documentElement.setAttribute('data-theme', t);
    // Element Plus 官方暗色变量挂在 .dark 上，首帧同步类名，避免组件先按浅色渲染
    document.documentElement.classList.toggle('dark', t === 'dark');
  } catch (e) {
    /* 隐私模式/禁用 localStorage 时静默回退到默认主题 */
  }
})();
