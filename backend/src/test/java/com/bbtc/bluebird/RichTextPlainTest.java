package com.bbtc.bluebird;

import com.bbtc.bluebird.common.util.ContentTypes;
import com.bbtc.bluebird.common.util.RichTextPlain;
import com.bbtc.bluebird.modules.note.application.NoteQueryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 富文本摘要与附件预览类型（零起步，纯函数）。 */
class RichTextPlainTest {

    @Test
    void stripsTiptapHtmlIntoReadableText() {
        assertEquals("今日进展 完成接口", RichTextPlain.plain("<h2>今日进展</h2><p><strong>完成</strong>接口</p>"));
        assertEquals("第一项 第二项", RichTextPlain.plain(
                "<ul data-type=\"taskList\">"
                        + "<li data-type=\"taskItem\" data-checked=\"true\"><label><input type=\"checkbox\"><span></span></label><div><p>第一项</p></div></li>"
                        + "<li data-type=\"taskItem\" data-checked=\"false\"><label><input type=\"checkbox\"><span></span></label><div><p>第二项</p></div></li>"
                        + "</ul>"));
        assertEquals("折叠标题 折叠内容", RichTextPlain.plain(
                "<details open><summary>折叠标题</summary><p>折叠内容</p></details>"));
        assertEquals("A&B 引用", RichTextPlain.plain("<p>A&amp;B</p><blockquote>引用</blockquote>"));
        assertEquals("正文", RichTextPlain.plain("<p>正文</p><script>alert(1)</script>"));
    }

    @Test
    void htmlSummaryStripsTagsBeforeTruncating() {
        assertEquals("标题 正文", NoteQueryService.summary("<h1>标题</h1><p>正文</p>"));
        String summary = NoteQueryService.summary("<p>" + "甲".repeat(500) + "</p>");
        assertEquals(200, summary.codePointCount(0, summary.length()));
        assertFalse(summary.contains("<p>"));
        assertFalse(summary.contains("&"));
    }

    @Test
    void nullSafe() {
        assertEquals(null, RichTextPlain.plain(null));
    }

    @Test
    void summaryStripsThenTruncatesTo200CodePoints() {
        String longText = "<h1>标题</h1>" + "甲".repeat(500);
        String summary = NoteQueryService.summary(longText);
        assertEquals(200, summary.codePointCount(0, summary.length()));
        assertEquals("标题 甲", summary.substring(0, 4));
    }

    @Test
    void contentTypeByExtension() {
        assertEquals("image/png", ContentTypes.of("shot.PNG"));
        assertEquals("application/pdf", ContentTypes.of("a.pdf"));
        assertEquals("application/octet-stream", ContentTypes.of("noext"));
        assertTrue(ContentTypes.isInlineSafe("image/png"));
        assertFalse(ContentTypes.isInlineSafe("image/svg+xml"));
        assertFalse(ContentTypes.isInlineSafe("text/html"));
    }
}
