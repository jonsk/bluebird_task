package com.bbtc.bluebird;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 部门 Excel 导入 / 导出 / 模板（03 §组织管理）。
 *
 * <p>覆盖：模板表头、导入建层级、幂等重导（同名同上级＝更新）、**任一行有误则整份不落库**、
 * 以及「导出 → 原样再导入」闭环。
 */
@SpringBootTest(properties = {
        "app.config.release=false",
        "app.bootstrap.admin-username=admin",
        "app.bootstrap.admin-password=Test123456"
})
@AutoConfigureMockMvc
class DepartmentExcelTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-deptexcel-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private String login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Test123456\"}".getBytes(StandardCharsets.UTF_8)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    /** 造一个与模板同结构的 xlsx（第 0 行表头）。 */
    private byte[] xlsx(String[][] dataRows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("部门");
            Row head = sheet.createRow(0);
            String[] headers = {"部门名称", "上级部门（全路径）", "排序", "负责人账号"};
            for (int c = 0; c < headers.length; c++) {
                head.createCell(c).setCellValue(headers[c]);
            }
            for (int i = 0; i < dataRows.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int c = 0; c < dataRows[i].length; c++) {
                    row.createCell(c).setCellValue(dataRows[i][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode importXlsx(String token, byte[] bytes) throws Exception {
        String body = mvc.perform(multipart("/api/v1/departments/import")
                        .file(new MockMultipartFile("file", "depts.xlsx", XLSX, bytes))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private byte[] download(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** 读回 xlsx 的数据行（跳过表头），返回「名称|上级|排序|负责人」。 */
    private List<String> readRows(byte[] bytes) throws Exception {
        List<String> out = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < 4; c++) {
                    cells.add(row.getCell(c) == null ? "" : fmt.formatCellValue(row.getCell(c)).trim());
                }
                out.add(String.join("|", cells));
            }
        }
        return out;
    }

    @Test
    void templateHasDocumentedHeaders() throws Exception {
        String token = login();
        byte[] tpl = download(token, "/api/v1/departments/import-template");
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(tpl))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("部门名称", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("上级部门（全路径）", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("排序", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("负责人账号", sheet.getRow(0).getCell(3).getStringCellValue());
            assertTrue(wb.getNumberOfSheets() >= 2, "模板应含「填写说明」工作表");
            assertEquals("填写说明", wb.getSheetName(1));
        }
    }

    @Test
    void importBuildsHierarchyAndExportRoundTrips() throws Exception {
        String token = login();
        byte[] file = xlsx(new String[][]{
                {"导入测试中心", "", "1", ""},
                {"导入一组", "导入测试中心", "2", ""},
                {"导入二组", "导入测试中心/导入一组", "3", ""},
        });

        JsonNode r = importXlsx(token, file);
        assertTrue(r.path("ok").asBoolean(), "应导入成功：" + r);
        assertEquals(3, r.path("created").asInt());
        assertEquals(0, r.path("updated").asInt());

        // 层级正确
        String tree = mvc.perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode roots = objectMapper.readTree(tree).path("data");
        JsonNode center = null;
        for (JsonNode n : roots) {
            if ("导入测试中心".equals(n.path("name").asText())) {
                center = n;
            }
        }
        assertTrue(center != null, "应存在「导入测试中心」：" + roots);
        assertEquals("导入一组", center.path("children").get(0).path("name").asText());
        assertEquals("导入二组", center.path("children").get(0).path("children").get(0).path("name").asText());

        // 幂等：同名同上级再导 → 全部为更新
        JsonNode again = importXlsx(token, file);
        assertTrue(again.path("ok").asBoolean());
        assertEquals(0, again.path("created").asInt());
        assertEquals(3, again.path("updated").asInt());

        // 导出 → 读回，父路径应为全路径
        List<String> rows = readRows(download(token, "/api/v1/departments/export"));
        assertTrue(rows.contains("导入二组|导入测试中心/导入一组|3|"), "导出应写全路径：" + rows);
        assertTrue(rows.contains("导入测试中心||1|"), "顶级行的上级应为空：" + rows);

        // 导出可直接回填导入（闭环）
        byte[] exported = download(token, "/api/v1/departments/export");
        JsonNode reimport = importXlsx(token, exported);
        assertTrue(reimport.path("ok").asBoolean(), "导出文件应能原样导入：" + reimport);
        assertEquals(0, reimport.path("created").asInt(), "导出再导入不应重复创建");
    }

    @Test
    void anyBadRowRejectsWholeFile() throws Exception {
        String token = login();
        byte[] file = xlsx(new String[][]{
                {"不该被创建", "", "1", ""},
                {"子部门", "不存在的上级部门", "2", ""},
        });

        JsonNode r = importXlsx(token, file);
        assertFalse(r.path("ok").asBoolean(), "有错行时不应报告成功：" + r);
        assertEquals(0, r.path("created").asInt());
        assertEquals(0, r.path("updated").asInt());
        assertTrue(r.path("failed").asInt() >= 1);
        assertEquals(3, r.path("errors").get(0).path("row").asInt(), "错误应定位到 Excel 行号（表头第 1 行）");

        // 关键：合法行也不能落库（整份原子）
        String tree = mvc.perform(get("/api/v1/departments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(tree.contains("不该被创建"), "校验失败时不得写入任何部门：" + tree);
    }

    @Test
    void badFileGivesActionableMessage() throws Exception {
        String token = login();
        String body = mvc.perform(multipart("/api/v1/departments/import")
                        .file(new MockMultipartFile("file", "not-excel.xlsx", XLSX, "not an excel".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String message = objectMapper.readTree(body).path("message").asText();
        // 回归：业务异常的具体原因曾被 GlobalExceptionHandler 丢弃，只剩「参数校验失败」
        assertTrue(message.contains("Excel 解析失败"), "应回传可操作的原因，实际：" + message);
    }
}
