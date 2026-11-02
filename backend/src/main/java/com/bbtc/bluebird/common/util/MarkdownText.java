package com.bbtc.bluebird.common.util;

import java.util.regex.Pattern;

/**
 * Markdown → 纯文本（只服务于列表摘要，不做完整语法树解析）。
 *
 * <p>日志正文自 Markdown 化之后，{@code contentSummary} 若直接截断原文，
 * 卡片上会露出 {@code ##}、{@code **}、{@code ```} 这类标记（甚至把代码围栏切成两半）。
 * 这里先做一轮"够用就好"的剥离，再交给调用方截断。
 *
 * <p>取舍：宁可多删标记字符，也不漏（摘要只用于展示，不参与检索与存储）。
 */
public final class MarkdownText {

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

    /** 剥离 Markdown 标记并压缩空白；{@code null} 原样返回。 */
    public static String plain(String markdown) {
        if (markdown == null) {
            return null;
        }
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
        s = WHITESPACE.matcher(s).replaceAll(" ");
        return s.trim();
    }
}
