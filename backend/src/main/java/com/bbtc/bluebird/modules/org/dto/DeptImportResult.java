package com.bbtc.bluebird.modules.org.dto;

import java.util.List;

/**
 * 部门 Excel 导入结果（03 §组织管理）。
 *
 * <p>{@code ok=false} 表示**整份未导入**（先全量校验、有错则不落库），
 * {@code errors} 给出每一行的原因；{@code row} 是 Excel 中的 1-based 行号（表头为第 1 行）。
 */
public record DeptImportResult(
        boolean ok,
        int total,
        int created,
        int updated,
        int failed,
        List<RowError> errors) {

    /** 单行错误。 */
    public record RowError(int row, String message) {
    }

    public static DeptImportResult rejected(int total, List<RowError> errors) {
        return new DeptImportResult(false, total, 0, 0, errors.size(), List.copyOf(errors));
    }

    public static DeptImportResult applied(int total, int created, int updated) {
        return new DeptImportResult(true, total, created, updated, 0, List.of());
    }
}
