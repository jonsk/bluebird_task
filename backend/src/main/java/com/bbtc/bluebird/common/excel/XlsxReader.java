package com.bbtc.bluebird.common.excel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 极简 .xlsx 读取器：只用 JDK（zip + StAX），不引 Apache POI。
 *
 * <p>只读第一个工作表，单元格值统一转成字符串（对齐原先 {@code DataFormatter} 的口径：
 * 整数不带 {@code .0}、日期按 {@code yyyy-MM-dd}、布尔 {@code TRUE/FALSE}），
 * 够导入场景用。样式只解析「是不是日期格式」这一位。
 *
 * <p>安全：禁用 DTD 与外部实体（xlsx 是用户上传文件，防 XXE），并对解压大小设上限（防 zip 炸弹）。
 */
public final class XlsxReader {

    private static final String WORKBOOK = "xl/workbook.xml";
    private static final String WORKBOOK_RELS = "xl/_rels/workbook.xml.rels";
    private static final String SHARED_STRINGS = "xl/sharedStrings.xml";
    private static final String STYLES = "xl/styles.xml";
    private static final long MAX_ENTRY_BYTES = 32L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 128L * 1024 * 1024;
    /** Excel 序列日期原点（已含 1900 闰年 bug 的补偿）。 */
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final double SECONDS_PER_DAY = 86400d;

    private XlsxReader() {
    }

    /** 读取第一个工作表。 */
    public static XlsxSheet readFirstSheet(InputStream in) throws IOException {
        Map<String, byte[]> parts = unzip(in);
        FirstSheetRef ref = firstSheetRef(parts);
        List<String> shared = readSharedStrings(parts.get(SHARED_STRINGS));
        boolean[] dateStyles = readDateStyles(parts.get(STYLES));
        byte[] sheetXml = parts.get(ref.path());
        if (sheetXml == null) {
            throw new IOException("不是有效的 .xlsx 文件（缺少 " + ref.path() + "）");
        }
        return new XlsxSheet(ref.name(), readRows(sheetXml, shared, dateStyles));
    }

    /* ==================== zip ==================== */

    private static Map<String, byte[]> unzip(InputStream in) throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                while (name.startsWith("/")) {
                    name = name.substring(1);
                }
                byte[] data = readCapped(zis);
                total += data.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("xlsx 解压后体积超过上限，拒绝解析");
                }
                parts.put(name, data);
            }
        }
        if (parts.isEmpty()) {
            throw new IOException("不是有效的 .xlsx 文件（zip 包为空）");
        }
        return parts;
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > MAX_ENTRY_BYTES) {
                throw new IOException("xlsx 单个部件超过上限，拒绝解析");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /* ==================== 工作簿结构 ==================== */

    private record FirstSheetRef(String name, String path) {
    }

    private static FirstSheetRef firstSheetRef(Map<String, byte[]> parts) throws IOException {
        byte[] wb = parts.get(WORKBOOK);
        if (wb == null) {
            throw new IOException("不是有效的 .xlsx 文件（缺少 " + WORKBOOK + "）");
        }
        String name = null;
        String rid = null;
        try (AutoClose closer = new AutoClose(open(wb))) {
            XMLStreamReader r = closer.reader;
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(r.getLocalName())) {
                    name = attr(r, "name");
                    rid = attr(r, "id");
                    break;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("解析 " + WORKBOOK + " 失败：" + e.getMessage(), e);
        }
        if (rid == null) {
            throw new IOException("不是有效的 .xlsx 文件（工作簿里没有工作表）");
        }
        Map<String, String> targets = readRels(parts.get(WORKBOOK_RELS));
        String target = targets.get(rid);
        if (target == null) {
            throw new IOException("不是有效的 .xlsx 文件（找不到工作表 " + rid + "）");
        }
        return new FirstSheetRef(name == null ? "" : name, resolve("xl", target));
    }

    private static Map<String, String> readRels(byte[] xml) throws IOException {
        Map<String, String> out = new HashMap<>();
        if (xml == null) {
            return out;
        }
        try (AutoClose closer = new AutoClose(open(xml))) {
            XMLStreamReader r = closer.reader;
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT
                        && "Relationship".equals(r.getLocalName())) {
                    String id = attr(r, "Id");
                    String target = attr(r, "Target");
                    if (id != null && target != null) {
                        out.put(id, target);
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("解析关系表失败：" + e.getMessage(), e);
        }
        return out;
    }

    /** 相对 base 解析 part 路径，兼容 {@code /xl/...} 绝对写法。 */
    private static String resolve(String base, String target) {
        String path = target.replace('\\', '/');
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return base + "/" + path;
    }

    /* ==================== 共享字符串 / 样式 ==================== */

    private static List<String> readSharedStrings(byte[] xml) throws IOException {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        try (AutoClose closer = new AutoClose(open(xml))) {
            XMLStreamReader r = closer.reader;
            StringBuilder sb = null;
            boolean inText = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT && "si".equals(r.getLocalName())) {
                    sb = new StringBuilder();
                } else if (ev == XMLStreamConstants.START_ELEMENT && "t".equals(r.getLocalName())) {
                    inText = true;
                } else if (ev == XMLStreamConstants.CHARACTERS && inText && sb != null) {
                    sb.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT && "t".equals(r.getLocalName())) {
                    inText = false;
                } else if (ev == XMLStreamConstants.END_ELEMENT && "si".equals(r.getLocalName())) {
                    out.add(sb == null ? "" : sb.toString());
                    sb = null;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("解析共享字符串失败：" + e.getMessage(), e);
        }
        return out;
    }

    /** 每个 cellXfs 下标一项：该样式是否按日期显示。 */
    private static boolean[] readDateStyles(byte[] xml) throws IOException {
        if (xml == null) {
            return new boolean[0];
        }
        Map<Integer, String> customFormats = new HashMap<>();
        List<Integer> xfNumFmtIds = new ArrayList<>();
        boolean inCellXfs = false;
        try (AutoClose closer = new AutoClose(open(xml))) {
            XMLStreamReader r = closer.reader;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if ("numFmt".equals(local)) {
                        Integer id = parseInt(attr(r, "numFmtId"));
                        if (id != null) {
                            customFormats.put(id, attr(r, "formatCode"));
                        }
                    } else if ("cellXfs".equals(local)) {
                        inCellXfs = true;
                    } else if (inCellXfs && "xf".equals(local)) {
                        xfNumFmtIds.add(parseIntOrZero(attr(r, "numFmtId")));
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT && "cellXfs".equals(r.getLocalName())) {
                    inCellXfs = false;
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("解析样式表失败：" + e.getMessage(), e);
        }
        boolean[] out = new boolean[xfNumFmtIds.size()];
        for (int i = 0; i < out.length; i++) {
            int id = xfNumFmtIds.get(i);
            out[i] = isDateStyle(id, customFormats.get(id));
        }
        return out;
    }

    /** 内置日期格式号 + 自定义格式码启发式（去掉引号字面量后看有没有 y/m/d/h/s）。 */
    private static boolean isDateStyle(int numFmtId, String formatCode) {
        if (formatCode == null) {
            return (numFmtId >= 14 && numFmtId <= 22)
                    || (numFmtId >= 27 && numFmtId <= 36)
                    || (numFmtId >= 45 && numFmtId <= 47)
                    || (numFmtId >= 50 && numFmtId <= 58);
        }
        String code = formatCode.toLowerCase(Locale.ROOT).replaceAll("\"[^\"]*\"", "")
                .replaceAll("\\[[^\\]]*\\]", "");
        return code.indexOf('y') >= 0 || code.indexOf('d') >= 0
                || (code.indexOf('m') >= 0 && code.indexOf('h') >= 0);
    }

    /* ==================== 单元格 ==================== */

    private static List<String[]> readRows(byte[] sheetXml, List<String> shared, boolean[] dateStyles)
            throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (AutoClose closer = new AutoClose(open(sheetXml))) {
            XMLStreamReader r = closer.reader;
            TreeMap<Integer, String> cells = null;
            int rowIdx = -1;
            int nextCol = 0;
            int colIdx = -1;
            String cellType = null;
            int styleIdx = 0;
            StringBuilder value = new StringBuilder();
            StringBuilder inlineText = new StringBuilder();
            boolean inValue = false;
            boolean inInlineText = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> {
                            Integer n = parseInt(attr(r, "r"));
                            rowIdx = n == null ? rowIdx + 1 : n - 1;
                            cells = new TreeMap<>();
                            nextCol = 0;
                        }
                        case "c" -> {
                            int n = columnOf(attr(r, "r"));
                            colIdx = n < 0 ? nextCol : n;
                            nextCol = colIdx + 1;
                            styleIdx = parseIntOrZero(attr(r, "s"));
                            cellType = attr(r, "t");
                            value.setLength(0);
                            inlineText.setLength(0);
                        }
                        case "v" -> inValue = true;
                        case "t" -> inInlineText = true;
                        default -> {
                        }
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS) {
                    if (inValue) {
                        value.append(r.getText());
                    }
                    if (inInlineText) {
                        inlineText.append(r.getText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String local = r.getLocalName();
                    if ("v".equals(local)) {
                        inValue = false;
                    } else if ("t".equals(local)) {
                        inInlineText = false;
                    } else if ("c".equals(local) && cells != null && colIdx >= 0) {
                        cells.put(colIdx, cellText(cellType, value, inlineText, shared, styleIdx, dateStyles));
                        colIdx = -1;
                        cellType = null;
                    } else if ("row".equals(local) && cells != null) {
                        putRow(rows, rowIdx, cells);
                        cells = null;
                        rowIdx = -1;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("解析工作表失败：" + e.getMessage(), e);
        }
        return rows;
    }

    private static String cellText(String type, StringBuilder value, StringBuilder inlineText,
                                   List<String> shared, int styleIdx, boolean[] dateStyles) {
        if ("s".equals(type)) {
            Integer idx = parseInt(value.toString());
            return idx != null && idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
        }
        if ("inlineStr".equals(type)) {
            return inlineText.toString();
        }
        String raw = value.toString();
        if ("b".equals(type)) {
            return "1".equals(raw) ? "TRUE" : "FALSE";
        }
        if ("str".equals(type) || "e".equals(type) || "d".equals(type)) {
            return raw;
        }
        if (raw.isEmpty()) {
            return "";
        }
        boolean date = styleIdx >= 0 && styleIdx < dateStyles.length && dateStyles[styleIdx];
        return formatNumber(raw, date);
    }

    private static String formatNumber(String raw, boolean date) {
        double d;
        try {
            d = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
        if (date) {
            return formatDate(d);
        }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static String formatDate(double serial) {
        long days = (long) Math.floor(serial);
        long seconds = Math.round((serial - days) * SECONDS_PER_DAY);
        if (seconds >= SECONDS_PER_DAY) {
            days++;
            seconds -= (long) SECONDS_PER_DAY;
        }
        LocalDate date = EXCEL_EPOCH.plusDays(days);
        if (seconds == 0) {
            return date.toString();
        }
        return date + " " + LocalTime.ofSecondOfDay(seconds);
    }

    private static void putRow(List<String[]> rows, int rowIdx, TreeMap<Integer, String> cells) {
        int idx = Math.max(rowIdx, rows.size());
        while (rows.size() <= idx) {
            rows.add(null);
        }
        String[] out = new String[cells.isEmpty() ? 0 : cells.lastKey() + 1];
        cells.forEach((c, v) -> out[c] = v);
        rows.set(idx, out);
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOrZero(String raw) {
        Integer n = parseInt(raw);
        return n == null ? 0 : n;
    }

    /** 单元格引用 {@code AB12} → 列下标 0 基；不含数字时返回 -1。 */
    private static int columnOf(String ref) {
        if (ref == null) {
            return -1;
        }
        int col = 0;
        int letters = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
                letters++;
            } else {
                break;
            }
        }
        return letters == 0 ? -1 : col - 1;
    }

    /* ==================== StAX 小工具 ==================== */

    private static XMLStreamReader open(byte[] xml) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return factory.createXMLStreamReader(new ByteArrayInputStream(xml), StandardCharsets.UTF_8.name());
    }

    /** 按 localName 取属性（不依赖前缀，{@code r:id} / {@code RelId} 都能取到）。 */
    private static String attr(XMLStreamReader r, String localName) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (localName.equals(r.getAttributeLocalName(i))) {
                String value = r.getAttributeValue(i);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * StAX 的 {@link XMLStreamReader} 没有实现 {@link AutoCloseable}，包一层让 try-with-resources 能关掉。
     */
    private static final class AutoClose implements AutoCloseable {
        private final XMLStreamReader reader;

        private AutoClose(XMLStreamReader reader) {
            this.reader = reader;
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
                // 关闭失败无补救动作，解析结果已经拿到
            }
        }
    }
}
