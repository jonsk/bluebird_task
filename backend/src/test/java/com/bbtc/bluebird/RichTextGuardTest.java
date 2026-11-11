package com.bbtc.bluebird;

import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.util.RichTextGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 富文本写入前的危险载荷拒绝（只拒绝、不改写，合法 Tiptap HTML 必须原样放行）。 */
class RichTextGuardTest {

    /** 真实编辑器产物：details 折叠、任务列表、表格、高亮、@提及、外链、图片、代码块文本。 */
    private static final String LEGAL_TIPTAP_HTML = """
            <h1>2026年11月11日</h1>
            <details data-type="details" open="open"><summary>明细</summary><div data-type="detailsContent"><p>内容</p></div></details>
            <ul data-type="taskList"><li data-type="taskItem" data-checked="false"><label><input type="checkbox"><span></span></label><div><p>待办</p></div></li></ul>
            <table class="note-table"><tbody><tr><td style="color: rgb(255, 0, 0)"><p>高亮</p></td></tr></tbody></table>
            <p><span data-type="mention" data-id="12">@张三</span> 完成了 <a href="https://example.com/a" target="_blank" rel="noopener noreferrer">需求</a></p>
            <img src="/notes/1/attachments/2/preview" class="note-image" alt="截图">
            <p>&lt;script&gt;alert(1)&lt;/script&gt; 只是正文里讨论的代码片段</p>
            """;

    @Test
    void acceptsLegalTiptapHtmlAndPlainText() {
        assertDoesNotThrow(() -> RichTextGuard.assertSafe(LEGAL_TIPTAP_HTML));
        assertDoesNotThrow(() -> RichTextGuard.assertSafe("## 今日进展\n- [ ] 待办"));
        assertDoesNotThrow(() -> RichTextGuard.assertSafe(null));
        assertDoesNotThrow(() -> RichTextGuard.assertSafe(""));
        // 用户正文里写 onXXX= / javascript: 作为纯文本，不带标签不应误判。
        assertDoesNotThrow(() -> RichTextGuard.assertSafe("<p>onclick = foo; javascript:void(0)</p>"));
        // 历史数据里的 base64 位图仍允许。
        assertDoesNotThrow(() -> RichTextGuard.assertSafe("<img src=\"data:image/png;base64,iVBORw0KGgo=\" />"));
    }

    @Test
    void rejectsDangerousTags() {
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<p>ok</p><script>alert(1)</script>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<iframe src=\"https://evil.com\"></iframe>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<svg onload=\"alert(1)\"></svg>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<style>body{display:none}</style>"));
        // 标签被截断、没有闭合的 > 也要拦。
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<p>ok</p><script"));
    }

    @Test
    void rejectsEventHandlersAndUnsafeUrls() {
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<img src=\"x\" onerror=\"alert(1)\">"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<a href=\"javascript:alert(1)\">点我</a>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<a href=\"java&#x73;cript:alert(1)\">点我</a>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<a href=\"java&#9;script:alert(1)\">点我</a>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<a href=\"data:text/html,<script>x</script>\">点我</a>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<div style=\"background:url(javascript:alert(1))\">x</div>"));
        assertThrows(HttpStatusException.class, () -> RichTextGuard.assertSafe("<iframe srcdoc=\"<p>x</p>\"></iframe>"));
    }
}
