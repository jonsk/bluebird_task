package com.bbtc.bluebird.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 极简 .xlsx 写出器：只用 JDK（zip + XML），不引 Apache POI。
 *
 * <p>.xlsx 本质是一个 zip 包，里面是固定的几份 XML。本类只覆盖「表格 + 文本/数字 + 宽度 + 表头加粗」
 * 这一档需求（部门导入导出、模板），用内联字符串（inlineStr）免掉 sharedStrings，包更小。
 *
 * <p>能力边界（刻意不做）：公式、合并单元格、图片、条件格式、多类型样式。
 * 需要这些能力时再引库，而不是把整个 POI 栈塞进运行时。
 */
public final class XlsxWriter {

    private static final String NS_MAIN =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_REL =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PKG_REL =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT_WORKSHEET =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";
    /** Excel 单元格文本硬上限，超了会「文件已损坏」。 */
    private static final int MAX_CELL_CHARS = 32767;

    private final List<Sheet> sheets = new ArrayList<>();

    /** 增加一个工作表，按调用顺序写入工作簿。 */
    public Sheet addSheet(String name) {
        Sheet sheet = new Sheet(name);
        sheets.add(sheet);
        return sheet;
    }

    /** 生成 xlsx 字节。 */
    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "[Content_Types].xml", contentTypesXml());
            put(zos, "_rels/.rels", packageRelsXml());
            put(zos, "xl/workbook.xml", workbookXml());
            put(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            put(zos, "xl/styles.xml", stylesXml());
            for (int i = 0; i < sheets.size(); i++) {
                put(zos, "xl/worksheets/sheet" + (i + 1) + ".xml", sheets.get(i).toXml());
            }
            zos.finish();
            return bos.toByteArray();
        }
    }

    private static void put(ZipOutputStream zos, String name, String xml) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(xml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String contentTypesXml() {
        StringBuilder sb = new StringBuilder(512)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"").append(CT_WORKSHEET).append("\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String packageRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"" + NS_PKG_REL + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + NS_REL + "/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbookXml() {
        StringBuilder sb = new StringBuilder(256)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<workbook xmlns=\"").append(NS_MAIN).append("\" xmlns:r=\"").append(NS_REL).append("\">")
                .append("<sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escape(sheets.get(i).name))
                    .append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String workbookRelsXml() {
        StringBuilder sb = new StringBuilder(256)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"").append(NS_PKG_REL).append("\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"").append(NS_REL).append("/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"").append(NS_REL).append("/styles\" Target=\"styles.xml\"/>")
                .append("</Relationships>");
        return sb.toString();
    }

    /** 最小合法样式表：字体 0 = 常规，字体 1 = 加粗（表头用）。 */
    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"" + NS_MAIN + "\">"
                + "<fonts count=\"2\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"2\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"2\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    /** XML 文本转义，并剔除 XML 1.0 不允许的控制字符（否则 Excel 打不开）。 */
    private static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String text = raw.length() > MAX_CELL_CHARS ? raw.substring(0, MAX_CELL_CHARS) : raw;
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 一列 = 一个数组下标；写 XML 时转成 1 基的列字母/序号。 */
    private static String colLetter(int index) {
        StringBuilder sb = new StringBuilder(3);
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    /** 一个工作表。行号/列号均为 0 基，与方法调用方（业务代码）习惯一致。 */
    public static final class Sheet {

        private final String name;
        private final Map<Integer, Double> widths = new TreeMap<>();
        private final Map<Integer, Map<Integer, Cell>> rows = new TreeMap<>();

        private Sheet(String name) {
            this.name = name;
        }

        /** 列宽（单位：字符数，和 Excel 里拖出来的宽度口径一致）。 */
        public Sheet width(int col, double chars) {
            widths.put(col, chars);
            return this;
        }

        /** 表头行：加粗文本。 */
        public Sheet header(int row, String... values) {
            return texts(row, true, values);
        }

        /** 普通文本行；null 写成空串，与 POI 版行为一致（不是「空单元格」）。 */
        public Sheet text(int row, String... values) {
            return texts(row, false, values);
        }

        /** 数字单元格。 */
        public Sheet number(int row, int col, long value) {
            cell(row, col, new Cell(Long.toString(value), false, true));
            return this;
        }

        private Sheet texts(int row, boolean bold, String... values) {
            for (int c = 0; c < values.length; c++) {
                cell(row, c, new Cell(values[c] == null ? "" : values[c], bold, false));
            }
            return this;
        }

        private void cell(int row, int col, Cell cell) {
            rows.computeIfAbsent(row, k -> new TreeMap<>()).put(col, cell);
        }

        private String toXml() {
            StringBuilder sb = new StringBuilder(2048)
                    .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                    .append("<worksheet xmlns=\"").append(NS_MAIN).append("\" xmlns:r=\"").append(NS_REL).append("\">");
            appendCols(sb);
            sb.append("<sheetData>");
            for (Map.Entry<Integer, Map<Integer, Cell>> rowEntry : rows.entrySet()) {
                int rowIdx = rowEntry.getKey();
                sb.append("<row r=\"").append(rowIdx + 1).append("\">");
                for (Map.Entry<Integer, Cell> cellEntry : rowEntry.getValue().entrySet()) {
                    appendCell(sb, rowIdx, cellEntry.getKey(), cellEntry.getValue());
                }
                sb.append("</row>");
            }
            return sb.append("</sheetData></worksheet>").toString();
        }

        private void appendCols(StringBuilder sb) {
            if (widths.isEmpty()) {
                return;
            }
            sb.append("<cols>");
            for (Map.Entry<Integer, Double> e : widths.entrySet()) {
                int idx = e.getKey() + 1;
                sb.append("<col min=\"").append(idx).append("\" max=\"").append(idx)
                        .append("\" width=\"").append(e.getValue()).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }

        private static void appendCell(StringBuilder sb, int rowIdx, int colIdx, Cell cell) {
            String ref = colLetter(colIdx) + (rowIdx + 1);
            sb.append("<c r=\"").append(ref).append('"');
            if (cell.bold()) {
                sb.append(" s=\"1\"");
            }
            if (cell.number()) {
                sb.append("><v>").append(cell.value()).append("</v></c>");
            } else {
                sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(cell.value()))
                        .append("</t></is></c>");
            }
        }
    }

    private record Cell(String value, boolean bold, boolean number) {
    }
}
