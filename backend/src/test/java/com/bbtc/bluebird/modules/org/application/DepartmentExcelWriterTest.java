package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.modules.org.domain.Department;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 写出层（导出 + 空白模板）。断言列顺序、父路径列、负责人账号回落，
 * 因为这三点是「导出 → 改 → 导入」闭环的直接输入，改错了端到端用例也未必报得清楚。
 */
class DepartmentExcelWriterTest {

    private static final DataFormatter FMT = new DataFormatter();

    @Test
    void 表头四列顺序固定() throws Exception {
        try (Workbook wb = open(DepartmentExcelWriter.template())) {
            Sheet sheet = wb.getSheetAt(0);
            Row head = sheet.getRow(0);
            assertEquals(DepartmentExcelWriter.C_NAME, FMT.formatCellValue(head.getCell(0)));
            assertEquals(DepartmentExcelWriter.C_PARENT, FMT.formatCellValue(head.getCell(1)));
            assertEquals(DepartmentExcelWriter.C_SORT, FMT.formatCellValue(head.getCell(2)));
            assertEquals(DepartmentExcelWriter.C_LEADER, FMT.formatCellValue(head.getCell(3)));
            assertEquals(4, head.getLastCellNum());
        }
    }

    @Test
    void 模板带示例行与填写说明页() throws Exception {
        try (Workbook wb = open(DepartmentExcelWriter.template())) {
            assertEquals(2, wb.getNumberOfSheets());
            Sheet main = wb.getSheetAt(0);
            assertEquals("XX公司", FMT.formatCellValue(main.getRow(1).getCell(0)));
            assertEquals("XX公司/研发中心", FMT.formatCellValue(main.getRow(3).getCell(1)));

            Sheet help = wb.getSheet("填写说明");
            assertTrue(help != null, "说明页缺失会让用户不知道全路径怎么写");
            assertEquals("部门导入说明", FMT.formatCellValue(help.getRow(0).getCell(0)));
        }
    }

    @Test
    void 导出写出部门行且父路径是全路径() throws Exception {
        List<Department> ordered = DepartmentTree.ordered(List.of(
                dept(1L, "总部", null, 0, null),
                dept(2L, "研发中心", 1L, 1, 10L),
                dept(3L, "平台组", 2L, 2, null)));

        byte[] bytes = DepartmentExcelWriter.export(ordered, Map.of(10L, "zhangsan"));

        try (Workbook wb = open(bytes)) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum(), "lastRowNum 是 0 基下标：表头 + 3 个部门");
            assertEquals("总部", cell(sheet, 1, 0));
            assertEquals("", cell(sheet, 1, 1), "顶级部门父路径留空");
            assertEquals("研发中心", cell(sheet, 2, 0));
            assertEquals("总部", cell(sheet, 2, 1));
            assertEquals("平台组", cell(sheet, 3, 0));
            assertEquals("总部/研发中心", cell(sheet, 3, 1), "导出写全路径，导入才可能唯一解析");
        }
    }

    @Test
    void 负责人列写用户名_查不到时留空() throws Exception {
        List<Department> ordered = DepartmentTree.ordered(List.of(
                dept(1L, "有负责人", null, 0, 10L),
                dept(2L, "负责人已离职", null, 0, 99L),
                dept(3L, "无负责人", null, 0, null)));

        byte[] bytes = DepartmentExcelWriter.export(ordered, Map.of(10L, "zhangsan"));

        try (Workbook wb = open(bytes)) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("zhangsan", cell(sheet, 1, 3));
            assertEquals("", cell(sheet, 2, 3), "用户表里查不到就留空，不能写出裸 id");
            assertEquals("", cell(sheet, 3, 3));
        }
    }

    @Test
    void 排序为空时导出为0() throws Exception {
        List<Department> ordered = DepartmentTree.ordered(List.of(dept(1L, "无排序", null, null, null)));

        try (Workbook wb = open(DepartmentExcelWriter.export(ordered, Map.of()))) {
            assertEquals("0", cell(wb.getSheetAt(0), 1, 2));
        }
    }

    @Test
    void 名称与排序按部门数据写出() throws Exception {
        List<Department> ordered = DepartmentTree.ordered(List.of(dept(1L, "有排序", null, 7, null)));

        try (Workbook wb = open(DepartmentExcelWriter.export(ordered, Map.of()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("有排序", cell(sheet, 1, 0));
            assertEquals("7", cell(sheet, 1, 2));
        }
    }

    @Test
    void 空部门列表只产出表头() throws Exception {
        try (Workbook wb = open(DepartmentExcelWriter.export(List.of(), Map.of()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(0, sheet.getLastRowNum());
            assertEquals(DepartmentExcelWriter.C_NAME, cell(sheet, 0, 0));
        }
    }

    private Workbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private String cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        return r == null ? "" : FMT.formatCellValue(r.getCell(col));
    }

    private Department dept(Long id, String name, Long parentId, Integer sort, Long leaderId) {
        Department d = new Department();
        d.setId(id);
        d.setName(name);
        d.setParentId(parentId);
        d.setSort(sort);
        d.setLeaderUserId(leaderId);
        return d;
    }
}
