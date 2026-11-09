package com.bbtc.bluebird.common.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * 日志正文 → 纯文本（只服务于列表摘要与内容长度校验，不做完整语法树解析）。
 *
 * <p>正文同时存在两种历史形态：
 * <ul>
 *   <li>Tiptap 富文本：HTML（{@code <h1>}、{@code <p>}、{@code <ul data-type="taskList">}…）；</li>
 *   <li>编辑器升级前的历史数据：Markdown 源码。</li>
 * </ul>
 *
 * <p>摘要若直接截断原文，卡片上会露出 HTML 标签或 {@code ##}、{@code **}、{@code ```} 这类标记。
 * 这里先按形态做一轮"够用就好"的剥离，再交给调用方截断。
 *
 * <p>取舍：宁可多删标记字符，也不漏（摘要只用于展示，不参与检索与存储）。
 */
public final class MarkdownText {

    /** Tiptap 产出的 HTML 必以块级标签开头；用这个特征区分历史 Markdown。 */
    private static final Pattern HTML_START = Pattern.compile(
            "^\\s*<(?:p|h[1-6]|ul|ol|li|blockquote|pre|div|details|summary|table|thead|tbody|tr|td|th|hr|img|figure|section|article)\\b",
            Pattern.CASE_INSENSITIVE);
    /** script/style 整体丢弃（含内容）。 */
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)\\b.*?</\\1>");
    /** 块级边界换成空格，避免相邻段落文字粘在一起。 */
    private static final Pattern BLOCK_BREAK = Pattern.compile(
            "(?i)</?(?:p|div|li|h[1-6]|blockquote|pre|details|summary|tr|td|th|ul|ol)\\b[^>]*>|<br\\s*/?>|<hr\\s*/?>");
    /** 围栏代码块整体丢弃（含内容）：摘要里保留半截代码没有意义。 */
    private static final Pattern FENCE = Pattern.compile("(?s)```.*?```|~~~.*?~~~");
    /** ![alt](url) → alt */
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    /** [text](url) → text */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    /** 引用式链接 [text][id] → text */
    private static final Pattern REF_LINK = Pattern.compile("\\[([^\\]]*)]\\[[^\\]]*]");
    /** 裸 HTML 标签 */
    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z][^>]*>");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");
    private static final Pattern QUOTE = Pattern.compile("(?m)^\\s{0,3}(?:>\\s?)+");
    private static final Pattern LIST = Pattern.compile("(?m)^\\s*(?:[-*+]|\\d{1,3}[.)])\\s+");
    private static final Pattern TABLE_DIVIDER =
            Pattern.compile("(?m)^\\s*\\|?\\s*:?-{2,}:?(\\s*\\|\\s*:?-{2,}:?)*\\s*\\|?\\s*$");
    private static final Pattern HR = Pattern.compile("(?m)^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|~~|\\*|_)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MarkdownText() {
    }

    /** 是否为 Tiptap/HTML 正文。 */
    public static boolean looksLikeHtml(String content) {
        return content != null && HTML_START.matcher(content).find();
    }

    /** 剥离标记并压缩空白；{@code null} 原样返回。 */
    public static String plain(String content) {
        if (content == null) {
            return null;
        }
        String s = looksLikeHtml(content) ? plainHtml(content) : plainMarkdown(content);
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    private static String plainHtml(String html) {
        String s = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        s = BLOCK_BREAK.matcher(s).replaceAll(" ");
        s = HTML_TAG.matcher(s).replaceAll("");
        s = HtmlUtils.htmlUnescape(s);
        return s.replace('\u00a0', ' ');
    }

    private static String plainMarkdown(String markdown) {
        String s = markdown;
        s = FENCE.matcher(s).replaceAll(" ");
        s = IMAGE.matcher(s).replaceAll("$1");
        s = LINK.matcher(s).replaceAll("$1");
        s = REF_LINK.matcher(s).replaceAll("$1");
        s = HTML_TAG.matcher(s).replaceAll("");
        s = TABLE_DIVIDER.matcher(s).replaceAll(" ");
        s = HR.matcher(s).replaceAll(" ");
        s = HEADING.matcher(s).replaceAll("");
        s = QUOTE.matcher(s).replaceAll("");
        s = LIST.matcher(s).replaceAll("");
        s = s.replace("`", "");
        s = s.replace("|", " ");
        s = EMPHASIS.matcher(s).replaceAll("");
        return s;
    }
}
