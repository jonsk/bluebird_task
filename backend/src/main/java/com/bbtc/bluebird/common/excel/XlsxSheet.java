package com.bbtc.bluebird.common.excel;

import java.util.List;

/**
 * {@link XlsxReader} 读出来的第一个工作表：按行下标取单元格文本。
 *
 * <p>行下标是 0 基（对应 Excel 的第 1 行），空行/跳过的行取到 {@code null}；
 * 列数按该行实际最后一个有值的单元格裁剪，调用方需自行做越界兜底。
 */
public final class XlsxSheet {

    private final String name;
    private final List<String[]> rows;

    XlsxSheet(String name, List<String[]> rows) {
        this.name = name;
        this.rows = List.copyOf(rows);
    }

    /** 工作表名（Excel 里的标签页名字）。 */
    public String name() {
        return name;
    }

    /** 最后一个有内容的行下标（0 基）；空表返回 -1，对齐 POI 的 {@code getLastRowNum()}。 */
    public int lastRowIndex() {
        return rows.size() - 1;
    }

    /** 取一整行；越界或空行返回 {@code null}。 */
    public String[] row(int index) {
        if (index < 0 || index >= rows.size()) {
            return null;
        }
        return rows.get(index);
    }

    /** 取单元格文本；越界或空单元格返回空串。 */
    public String cell(int rowIndex, int colIndex) {
        String[] row = row(rowIndex);
        if (row == null || colIndex < 0 || colIndex >= row.length || row[colIndex] == null) {
            return "";
        }
        return row[colIndex];
    }
}
