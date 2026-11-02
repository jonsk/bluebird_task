package com.bbtc.bluebird.common.util;

import java.util.Map;

/**
 * 按扩展名推断 Content-Type（附件预览用）。
 *
 * <p>附件表不存 MIME，落盘只有相对路径 + 文件名；预览端点必须给出真实类型，
 * 否则浏览器按 {@code application/octet-stream} 处理会退化成下载。
 * 未命中的扩展名一律 {@code application/octet-stream}（宁可下载，不猜类型）。
 */
public final class ContentTypes {

    private static final Map<String, String> BY_EXT = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("txt", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("md", "text/markdown"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mp3", "audio/mpeg"));

    public static final String DEFAULT = "application/octet-stream";

    private ContentTypes() {
    }

    public static String of(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return DEFAULT;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return DEFAULT;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return BY_EXT.getOrDefault(ext, DEFAULT);
    }

    /**
     * 允许在浏览器内联展示的类型。
     *
     * <p>刻意排除 {@code text/html} 与 {@code image/svg+xml}：SVG 可携带脚本，
     * 内联渲染等于把上传内容当页面执行，同源下可直接读 token。
     */
    public static boolean isInlineSafe(String contentType) {
        if (contentType == null) {
            return false;
        }
        String t = contentType.toLowerCase(java.util.Locale.ROOT);
        if (t.startsWith("text/html") || t.startsWith("image/svg")) {
            return false;
        }
        return t.startsWith("image/") || t.startsWith("text/") || t.startsWith("application/pdf")
                || t.startsWith("video/") || t.startsWith("audio/") || t.startsWith("application/json");
    }
}
