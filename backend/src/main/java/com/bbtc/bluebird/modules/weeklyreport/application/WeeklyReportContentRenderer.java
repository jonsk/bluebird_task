package com.bbtc.bluebird.modules.weeklyreport.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 把模型输出的结构化 JSON 渲染为可编辑 HTML（Tiptap 可直接解析）。
 *
 * <p>模型输出一律按不可信文本处理：所有字段先做 HTML 转义再拼接，不允许模型注入标签或属性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportContentRenderer {

    private final ObjectMapper objectMapper;

    public record Rendered(String html, String summary) {
    }

    public JsonNode parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            if (firstBreak > 0) {
                text = text.substring(firstBreak + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            log.warn("周报 AI 输出不是合法 JSON：{}", e.getMessage());
            return null;
        }
    }

    /** 原始文本兜底：模型没按 JSON 输出时，至少保留可用正文。 */
    public Rendered renderRaw(String raw) {
        String text = raw == null ? "" : raw.trim();
        String body = HtmlUtils.htmlEscape(text).replace("\r\n", "\n").replace("\n", "<br>");
        return new Rendered("<h2>本周概述</h2><p>" + body + "</p>", firstLine(text));
    }

    public Rendered render(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        StringBuilder html = new StringBuilder();
        String summary = text(root.get("summary"));
        if (StringUtils.hasText(summary)) {
            html.append("<h2>本周概述</h2><p>").append(escape(summary)).append("</p>");
        }
        appendList(html, "主要成果", root.get("achievements"));
        appendList(html, "进行中", root.get("inProgress"));
        appendList(html, "风险与阻塞", root.get("risks"));
        appendList(html, "下周计划", root.get("nextWeek"));
        appendMetrics(html, root.get("metrics"));
        if (html.isEmpty()) {
            return null;
        }
        return new Rendered(html.toString(), StringUtils.hasText(summary) ? summary : firstLine(html.toString()));
    }

    /** 从已存 JSON 中取概述，供列表展示。 */
    public String summaryOf(String aiResultJson) {
        JsonNode root = parse(aiResultJson);
        if (root == null) {
            return null;
        }
        String summary = text(root.get("summary"));
        return StringUtils.hasText(summary) ? summary : null;
    }

    private void appendList(StringBuilder html, String title, JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return;
        }
        StringBuilder items = new StringBuilder();
        for (JsonNode item : node) {
            String text = text(item);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            List<String> ids = noteIds(item);
            items.append("<li>").append(escape(stripInlineCitations(text, ids)))
                    .append(citations(ids)).append("</li>");
        }
        if (!items.isEmpty()) {
            html.append("<h2>").append(title).append("</h2><ul>").append(items).append("</ul>");
        }
    }

    private void appendMetrics(StringBuilder html, JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return;
        }
        StringBuilder items = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String value = text(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }
            items.append("<li>").append(escape(entry.getKey())).append("：")
                    .append(escape(value)).append("</li>");
        }
        if (!items.isEmpty()) {
            html.append("<h2>数据指标</h2><ul>").append(items).append("</ul>");
        }
    }

    /** 条目可能是字符串，也可能是 {text, noteIds} 对象。 */
    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isObject()) {
            JsonNode text = node.get("text");
            return text == null || text.isNull() ? "" : text.asText().trim();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return "";
    }

    /** 条目引用的日志 ID：只接受纯数字，避免把模型输出直接拼进链接。 */
    private List<String> noteIds(JsonNode item) {
        if (item == null || !item.isObject()) {
            return List.of();
        }
        JsonNode ids = item.get("noteIds");
        if (ids == null || !ids.isArray() || ids.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode id : ids) {
            String value = id.asText("").trim();
            if (!value.matches("\\d{1,19}")) {
                continue;
            }
            out.add(value);
            if (out.size() >= 6) {
                break;
            }
        }
        return out;
    }

    /** 引用渲染成站内链接，点一下就能回到对应日志。 */
    private String citations(List<String> ids) {
        if (ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("（");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append('、');
            }
            String id = ids.get(i);
            sb.append("<a href=\"/notes/").append(id).append("\">#").append(id).append("</a>");
        }
        return sb.append("）").toString();
    }

    /**
     * 模型有时会把日志 ID 也写进正文，渲染时又补一份，界面上就成了「##369」。
     * 正文里的括号引用（含不带 # 的写法）统一剥掉，只保留后面统一渲染的链接。
     */
    private String stripInlineCitations(String text, List<String> ids) {
        if (ids.isEmpty()) {
            return text;
        }
        String result = text;
        for (String id : ids) {
            result = result.replaceAll("[（(]\\s*#?\\s*" + id + "\\s*[）)]", "");
        }
        return result.trim();
    }

    private String firstLine(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) {
            return null;
        }
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120);
    }

    private String escape(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text);
    }
}
