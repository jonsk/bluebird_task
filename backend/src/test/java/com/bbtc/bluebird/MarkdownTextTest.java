package com.bbtc.bluebird;

import com.bbtc.bluebird.common.util.ContentTypes;
import com.bbtc.bluebird.common.util.MarkdownText;
import com.bbtc.bluebird.modules.note.application.NoteQueryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Markdown 化后的摘要与附件预览类型（零起步，纯函数）。 */
class MarkdownTextTest {

    @Test
    void stripsCommonMarkdownMarkers() {
        assertEquals("今日进展", MarkdownText.plain("## 今日进展"));
        assertEquals("阻塞：等测试环境", MarkdownText.plain("**阻塞**：等测试环境"));
        assertEquals("第一条 第二条", MarkdownText.plain("1. 第一条\n2. 第二条"));
        assertEquals("引用内容", MarkdownText.plain("> 引用内容"));
        assertEquals("见 需求文档", MarkdownText.plain("见 [需求文档](https://example.com/a)"));
        assertEquals("执行 pnpm test 校验", MarkdownText.plain("执行 `pnpm test` 校验"));
        assertEquals("截图", MarkdownText.plain("![截图](/notes/1/attachments/2/preview)"));
        assertEquals("", MarkdownText.plain("---"));
    }

    @Test
    void dropsFencedCodeBlock() {
        assertEquals("前 后", MarkdownText.plain("前\n```java\nint a = 1;\n```\n后"));
    }

    @Test
    void nullSafe() {
        assertEquals(null, MarkdownText.plain(null));
    }

    @Test
    void summaryStripsThenTruncatesTo200CodePoints() {
        assertEquals("标题 正文", NoteQueryService.summary("# 标题\n\n正文"));
        String longText = "# 标题\n" + "甲".repeat(500);
        String summary = NoteQueryService.summary(longText);
        assertEquals(200, summary.codePointCount(0, summary.length()));
        assertFalse(summary.contains("#"));
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
