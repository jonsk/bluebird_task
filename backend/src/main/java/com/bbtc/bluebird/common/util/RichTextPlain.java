package com.bbtc.bluebird.common.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * 富文本正文 → 纯文本（只服务于列表摘要与内容长度校验，不做完整语法树解析）。
 * <p>正文一律是 Tiptap 产出的 HTML（{@code <h1>}、{@code <p>}、{@code <ul data-type="taskList">}…）；
 * 历史 Markdown 兼容层已移除（见 docs/architecture/tiptap-editor.md §9.4）。
 *
 * <p>摘要若直接截断原文，卡片上会露出 HTML 标签，因此截断前先做一次"够用就好"的剥离。
 * <p>取舍：宁可多删标记字符，也不漏（摘要只用于展示，不参与检索与存储）。
 */
public final class RichTextPlain {

    /** script/style 整体丢弃（含内容）。 */
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)\\b.*?</\\1>");
    /** 块级边界换成空格，避免相邻段落文字粘在一起。 */
    private static final Pattern BLOCK_BREAK = Pattern.compile(
            "(?i)</?(?:p|div|li|h[1-6]|blockquote|pre|details|summary|tr|td|th|ul|ol)\\b[^>]*>|<br\\s*/?>|<hr\\s*/?>");
    /** 剥离 HTML 标签。 */
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z][^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private RichTextPlain() {
    }

    /** 剥离标签并压缩空白；{@code null} 原样返回。 */
    public static String plain(String content) {
        if (content == null) {
            return null;
        }
        String s = SCRIPT_STYLE.matcher(content).replaceAll(" ");
        s = BLOCK_BREAK.matcher(s).replaceAll(" ");
        s = HTML_TAG.matcher(s).replaceAll("");
        s = HtmlUtils.htmlUnescape(s);
        s = s.replace('\u00a0', ' ');
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }
}
