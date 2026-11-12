package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.common.excel.XlsxWriter;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.org.domain.Department;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 部门 Excel 的**写**（导出 + 空白模板），与导入解析分开。
 *
 * <p>列名常量放在这里：导入侧的错误文案直接引用同一份常量，避免改列名后只改一半。
 *
 * <p>写出走 {@link XlsxWriter}（JDK zip+XML），不再依赖 Apache POI；
 * 原先 POI 只为这两张表进包，成本约 12 MB。
 */
final class DepartmentExcelWriter {

    static final String C_NAME = "部门名称";
    static final String C_PARENT = "上级部门（全路径）";
    static final String C_SORT = "排序";
    static final String C_LEADER = "负责人账号";
    private static final String[] HEADERS = {C_NAME, C_PARENT, C_SORT, C_LEADER};

    private static final int MAX_ROWS = 5000;
    private static final int MAX_NAME_LEN = 64;
    /** 新部门的排序默认值（与建部门接口一致）。 */
    private static final int DEFAULT_SORT = 0;
    private static final String SHEET_NAME = "部门";

    private DepartmentExcelWriter() {
    }

    /** 导出已排好序的部门（调用方负责 tree order 与用户名映射）。 */
    static byte[] export(List<Department> ordered, Map<Long, String> usernameById) {
        try {
            XlsxWriter wb = new XlsxWriter();
            XlsxWriter.Sheet sheet = wb.addSheet(SHEET_NAME);
            writeHeader(sheet);
            Map<Long, String> paths = DepartmentTree.paths(ordered);
            int r = 1;
            for (Department d : ordered) {
                sheet.text(r,
                        d.getName() == null ? "" : d.getName(),
                        DepartmentTree.parentPathOf(d, paths),
                        null,
                        d.getLeaderUserId() == null ? ""
                                : usernameById.getOrDefault(d.getLeaderUserId(), ""));
                sheet.number(r, 2, d.getSort() == null ? DEFAULT_SORT : d.getSort());
                r++;
            }
            applyColumnWidths(sheet);
            return wb.toBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成 Excel 失败：" + e.getMessage());
        }
    }

    /** 空白模板：表头 + 三行示例 + 填写说明页。 */
    static byte[] template() {
        try {
            XlsxWriter wb = new XlsxWriter();
            XlsxWriter.Sheet sheet = wb.addSheet(SHEET_NAME);
            writeHeader(sheet);
            // 示例行（导入前删除或改写）
            String[][] samples = {
                    {"XX公司", "", "0", ""},
                    {"研发中心", "XX公司", "1", ""},
                    {"平台组", "XX公司/研发中心", "1", ""},
            };
            for (int i = 0; i < samples.length; i++) {
                sheet.text(i + 1, samples[i]);
            }
            applyColumnWidths(sheet);
            writeHelpSheet(wb);
            return wb.toBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成导入模板失败：" + e.getMessage());
        }
    }

    private static void applyColumnWidths(XlsxWriter.Sheet sheet) {
        for (int c = 0; c < HEADERS.length; c++) {
            sheet.width(c, c == 1 ? 32 : 18);
        }
    }

    private static void writeHeader(XlsxWriter.Sheet sheet) {
        sheet.header(0, HEADERS);
    }

    private static void writeHelpSheet(XlsxWriter wb) {
        XlsxWriter.Sheet help = wb.addSheet("填写说明");
        String[][] lines = {
                {"部门导入说明", ""},
                {"", ""},
                {"列", "说明"},
                {C_NAME, "必填，最长 " + MAX_NAME_LEN + " 字；同一上级下不可重名"},
                {C_PARENT, "可空。留空＝顶级部门；填上级的**全路径**（如 XX公司/研发中心）"},
                {C_PARENT, "也支持全组织唯一的简单名称（如只有一个「研发中心」时可只写「研发中心」）"},
                {C_SORT, "可空，非负整数，越小越靠前；留空按 0"},
                {C_LEADER, "可空，须是系统内已存在的**用户名**（不是姓名）"},
                {"", ""},
                {"导入规则", ""},
                {"1", "仅支持 .xlsx；单次最多 " + MAX_ROWS + " 行"},
                {"2", "**先父后子**：父部门必须已存在，或在本文件中排在子部门之前"},
                {"3", "同一上级下同名的部门视为**更新**（改排序/负责人），不会重复创建"},
                {"4", "只要有一行校验不通过，**整份都不会导入**，请按错误提示修正后重传"},
                {"5", "系统默认部门可以改排序/负责人，但不会被本导入改名"},
        };
        for (int i = 0; i < lines.length; i++) {
            help.text(i, lines[i][0], lines[i][1]);
        }
        help.width(0, 24);
        help.width(1, 80);
    }
}
