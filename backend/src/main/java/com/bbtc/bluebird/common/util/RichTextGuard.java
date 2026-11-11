package com.bbtc.bluebird.common.util;

import com.bbtc.bluebird.common.exception.HttpStatusException;
import org.springframework.web.util.HtmlUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本写入前的「危险载荷拒绝」工具（纵深防御，不是完整 HTML 清洗）。
 *
 * <p>背景：日志正文与周报 contentHtml 以 HTML 原样落库，渲染侧目前依赖 Tiptap 的
 * inert DOMParser + ProseMirror schema 白名单。这是单点防线——攻击者可以绕过前端直接
 * 调 API 写入含 {@code <script>} / {@code onerror} / {@code javascript:} 的正文，一旦将来
 * 换了渲染器、导出静态页或把内容塞进 {@code v-html}，就会变成存储型 XSS。
 *
 * <p>策略：<b>只拒绝、不改写</b>。完整 HTML 清洗（jsoup Safelist）会规范化标签、剥离
 * Tiptap 依赖的 {@code data-type} / {@code data-checked} / {@code style} / 自定义节点，
 * 破坏 details 折叠、任务列表、表格与高亮，还会让落库内容与编辑器 getHTML() 不一致，
 * 进而影响乐观锁与「是否未保存」判断。因此这里只扫描 HTML 标签部分，命中明确危险特征时
 * 返回 400；合法富文本与历史数据一字不改。
 *
 * <p>只扫描 {@code <...>} 标签区域，是刻意设计：用户正文里的代码片段会被 Tiptap 转义成
 * {@code &lt;script&gt;}，属于纯文本、浏览器不会二次解析，不该被误判为攻击。
 * 属性值单独做实体解码，用来拦 {@code java&#x73;cript:} 这类编码绕过。
 */
public final class RichTextGuard {

    private static final Set<String> DANGEROUS_TAGS = Set.of(
            "script", "style", "iframe", "object", "embed", "applet", "link", "meta", "base",
            "form", "svg", "math", "template", "noscript", "frame", "frameset", "marquee", "portal");

    private static final Set<String> URL_ATTRS = Set.of(
            "href", "src", "action", "formaction", "poster", "background", "cite", "longdesc", "xlink:href");

    /** 标签整体（跨行也能匹配）。 */
    private static final Pattern TAG = Pattern.compile("<[^>]*>", Pattern.DOTALL);
    private static final Pattern TAG_NAME = Pattern.compile("<\\s*/?\\s*([a-z][a-z0-9-]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile(
            "([a-z_:][a-z0-9_:.-]*)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** 兜底：标签未闭合（结尾被截断）时也能拦住危险标签名。 */
    private static final Pattern LOOSE_DANGEROUS_TAG = Pattern.compile(
            "<\\s*/?\\s*(?:script|style|iframe|object|embed|applet|link|meta|base|form|svg|math|template|noscript|frame|frameset|marquee|portal)\\b",
            Pattern.CASE_INSENSITIVE);
    /** WHATWG URL 会忽略 tab/newline/CR，判定协议前必须剔除。 */
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u0020\\u007f]+");
    private static final Pattern ANY_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_DATA_IMAGE = Pattern.compile(
            "^data:image/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSAFE_CSS = Pattern.compile(
            "expression\\s*\\(|@import|url\\s*\\(\\s*['\"]?\\s*javascript\\s*:", Pattern.CASE_INSENSITIVE);

    private RichTextGuard() {
    }

    /** 命中危险载荷时抛 400；否则原样放行（不改写内容）。 */
    public static void assertSafe(String html) {
        if (html == null || html.isEmpty()) {
            return;
        }
        if (LOOSE_DANGEROUS_TAG.matcher(html).find()) {
            reject();
        }

        Matcher tags = TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            Matcher name = TAG_NAME.matcher(tag);
            if (name.find() && DANGEROUS_TAGS.contains(name.group(1).toLowerCase(Locale.ROOT))) {
                reject();
            }
            // 标签里的 tab/newline 绕过（`java\nscript:`）统一在这里抹平。
            String compactTag = CONTROL.matcher(tag).replaceAll("").toLowerCase(Locale.ROOT);
            if (compactTag.contains("javascript:") || compactTag.contains("vbscript:")) {
                reject();
            }

            Matcher attrs = ATTR.matcher(tag);
            while (attrs.find()) {
                String key = attrs.group(1).toLowerCase(Locale.ROOT);
                String value = CONTROL.matcher(HtmlUtils.htmlUnescape(unquote(attrs.group(2))))
                        .replaceAll("")
                        .toLowerCase(Locale.ROOT);
                if (key.startsWith("on") || key.equals("srcdoc") || key.equals("ping")) {
                    reject();
                }
                if (URL_ATTRS.contains(key) && !isSafeUrl(value)) {
                    reject();
                }
                if (key.equals("style") && UNSAFE_CSS.matcher(value).find()) {
                    reject();
                }
            }
        }
    }

    private static boolean isSafeUrl(String value) {
        if (value.isEmpty()) {
            return true;
        }
        if (SAFE_DATA_IMAGE.matcher(value).matches()) {
            return true;
        }
        if (ANY_SCHEME.matcher(value).find()) {
            return value.startsWith("http:")
                    || value.startsWith("https:")
                    || value.startsWith("mailto:")
                    || value.startsWith("tel:");
        }
        // 相对路径、站内绝对路径、#锚点都允许。
        return true;
    }

    private static String unquote(String raw) {
        if (raw.length() >= 2
                && ((raw.startsWith("\"") && raw.endsWith("\""))
                || (raw.startsWith("'") && raw.endsWith("'")))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static void reject() {
        throw HttpStatusException.badRequest("正文包含不被允许的 HTML 内容");
    }
}
