package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.dto.DeptImportResult;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bbtc.bluebird.modules.org.application.DepartmentExcelWriter.C_NAME;
import static com.bbtc.bluebird.modules.org.application.DepartmentExcelWriter.C_SORT;

/**
 * 部门 Excel 导入 / 导出 / 模板（.xlsx）。
 *
 * <p>列：{@code 部门名称}、{@code 上级部门（全路径）}、{@code 排序}、{@code 负责人账号}。
 * 「上级部门」用**全路径**（如 {@code XX公司/研发中心}）而不是单个名称：大型组织里同名部门
 * 很常见（多个子公司都有「综合部」），只按名称无法唯一确定父级；同时也支持「全组织唯一」的
 * 简单名称，方便小组织手填。导出写全路径，因此**导出→改→导入**可闭环。
 *
 * <p>导入为**先全量校验、有任一错误则整份不落库**（在一个事务内），避免半份组织树。
 * 写 Excel 的部分在 {@link DepartmentExcelWriter}，树路径派生在 {@link DepartmentTree}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentExcelService {

    private static final int MAX_ROWS = 5000;
    private static final int MAX_NAME_LEN = 64;
    private static final String PATH_SEP = "/";
    /** 新部门的排序默认值（与建部门接口一致）。 */
    private static final int DEFAULT_SORT = 0;

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    /* ==================== 导出 ==================== */

    @Transactional(readOnly = true)
    public byte[] export() {
        List<Department> all = departmentMapper.selectList(Wrappers.<Department>lambdaQuery());
        return DepartmentExcelWriter.export(DepartmentTree.ordered(all), usernameById());
    }

    @Transactional(readOnly = true)
    public byte[] template() {
        return DepartmentExcelWriter.template();
    }

    /* ==================== 导入 ==================== */

    @Transactional
    public DeptImportResult importFrom(MultipartFile file) {
        List<ParsedRow> rows = readRows(file);

        // ── 现有数据索引 ──
        List<Department> existing = departmentMapper.selectList(Wrappers.<Department>lambdaQuery());
        Map<String, Department> byKey = new HashMap<>();
        Map<String, List<Long>> idsByName = new HashMap<>();
        for (Department d : existing) {
            byKey.put(key(d.getParentId(), d.getName()), d);
            idsByName.computeIfAbsent(d.getName(), k -> new ArrayList<>()).add(d.getId());
        }
        Map<Long, String> paths = DepartmentTree.paths(existing);
        // 全路径 → id（含本文件内新建的行，值先占位、落库时替换）
        Map<String, Long> idByPath = new HashMap<>();
        paths.forEach((id, p) -> idByPath.put(p, id));
        Map<String, Long> idByUsername = usernames();

        List<DeptImportResult.RowError> errors = new ArrayList<>();
        List<Validated> plan = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        long placeholder = -1L;

        for (ParsedRow row : rows) {
            String name = row.name();
            if (name.isEmpty()) {
                errors.add(new DeptImportResult.RowError(row.excelRow(), C_NAME + "不能为空"));
                continue;
            }
            if (name.length() > MAX_NAME_LEN) {
                errors.add(new DeptImportResult.RowError(row.excelRow(), C_NAME + "最长 " + MAX_NAME_LEN + " 字"));
                continue;
            }
            Integer sort = null;
            if (!row.sort().isEmpty()) {
                try {
                    sort = Integer.valueOf(row.sort());
                    if (sort < 0) {
                        errors.add(new DeptImportResult.RowError(row.excelRow(), C_SORT + "必须是非负整数"));
                        continue;
                    }
                } catch (NumberFormatException e) {
                    errors.add(new DeptImportResult.RowError(row.excelRow(),
                            C_SORT + "必须是整数，实际为「" + row.sort() + "」"));
                    continue;
                }
            }
            Long leaderId = null;
            if (!row.leader().isEmpty()) {
                leaderId = idByUsername.get(row.leader());
                if (leaderId == null) {
                    errors.add(new DeptImportResult.RowError(row.excelRow(), "负责人账号「" + row.leader() + "」不存在"));
                    continue;
                }
            }
            ParentRef parent = resolveParent(row, idByPath, idsByName, paths, errors);
            if (parent == null) {
                continue;
            }

            String fileKey = key(parent.id(), name);
            if (!seenInFile.add(fileKey)) {
                errors.add(new DeptImportResult.RowError(row.excelRow(), "同一上级下「" + name + "」在文件中重复出现"));
                continue;
            }

            Department matched = byKey.get(fileKey);
            if (matched == null) {
                // 新建：登记全路径与名称索引，供后续行作为上级引用（占位 id 落库时替换）
                long stub = placeholder--;
                String fullPath = parent.path() == null || parent.path().isEmpty()
                        ? name : parent.path() + PATH_SEP + name;
                idByPath.putIfAbsent(fullPath, stub);
                idsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(stub);
            } else if (matched.getIsSystem() != null && matched.getIsSystem() == 1) {
                // 系统默认部门允许改排序/负责人，但不允许被本导入改名（名称即匹配键，天然不会改）
                log.debug("导入命中系统默认部门 id={}", matched.getId());
            }
            plan.add(new Validated(row.excelRow(), name, parent.path(), sort, leaderId, matched));
        }

        if (!errors.isEmpty()) {
            log.info("部门导入校验未通过：共 {} 行，{} 行有误，未落库", rows.size(), errors.size());
            return DeptImportResult.rejected(rows.size(), errors);
        }

        return persist(plan, paths);
    }

    /** 读文件 + 解析表头以下的数据行（不做业务校验）。 */
    private List<ParsedRow> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要导入的 Excel 文件");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!fileName.endsWith(".xlsx")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 .xlsx 格式，请使用「下载导入模板」");
        }
        List<ParsedRow> rows;
        try (InputStream in = file.getInputStream(); Workbook wb = new XSSFWorkbook(in)) {
            rows = parse(wb);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Excel 解析失败，请确认使用导入模板：" + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未读到任何数据行（第 1 行应为表头）");
        }
        return rows;
    }

    /**
     * 解析「上级部门」列：全路径优先，其次全组织唯一名称。
     *
     * @return null 表示已登记错误、本行应跳过
     */
    private ParentRef resolveParent(ParsedRow row, Map<String, Long> idByPath,
                                    Map<String, List<Long>> idsByName, Map<Long, String> paths,
                                    List<DeptImportResult.RowError> errors) {
        String parentRaw = row.parent().replaceAll("/+", PATH_SEP).replaceAll("^/|/$", "");
        if (parentRaw.isEmpty()) {
            return new ParentRef(null, null);
        }
        Long byPathId = idByPath.get(parentRaw);
        if (byPathId != null) {
            return new ParentRef(byPathId, parentRaw);
        }
        String simpleName = row.parent().trim();
        List<Long> sameName = idsByName.get(simpleName);
        if (sameName == null || sameName.isEmpty()) {
            errors.add(new DeptImportResult.RowError(row.excelRow(),
                    "上级部门「" + simpleName + "」不存在（父部门须已存在或在本文件中排在前面）"));
            return null;
        }
        if (sameName.size() > 1) {
            errors.add(new DeptImportResult.RowError(row.excelRow(),
                    "上级部门名称「" + simpleName + "」不唯一，请改填全路径"));
            return null;
        }
        Long only = sameName.get(0);
        return new ParentRef(only, paths.get(only));
    }

    /** 校验全部通过后按文件顺序落库（父先子后）。 */
    private DeptImportResult persist(List<Validated> plan, Map<Long, String> paths) {
        int created = 0;
        int updated = 0;
        Map<String, Long> realIdByPath = new HashMap<>();
        paths.forEach((id, p) -> realIdByPath.put(p, id));
        for (Validated v : plan) {
            Long parentId = v.parentPath() == null ? null : realIdByPath.get(v.parentPath());
            String fullPath = v.parentPath() == null || v.parentPath().isEmpty()
                    ? v.name() : v.parentPath() + PATH_SEP + v.name();
            if (v.matched() == null) {
                Department d = new Department();
                d.setName(v.name());
                d.setParentId(parentId);
                d.setSort(v.sort() == null ? DEFAULT_SORT : v.sort());
                d.setLeaderUserId(v.leaderId());
                d.setCreatedAt(Instant.now());
                departmentMapper.insert(d);
                realIdByPath.put(fullPath, d.getId());
                created++;
            } else {
                Department patch = new Department();
                patch.setId(v.matched().getId());
                patch.setSort(v.sort() == null
                        ? (v.matched().getSort() == null ? DEFAULT_SORT : v.matched().getSort()) : v.sort());
                if (v.leaderId() != null) {
                    patch.setLeaderUserId(v.leaderId());
                }
                patch.setUpdatedAt(Instant.now());
                departmentMapper.updateById(patch);
                realIdByPath.put(fullPath, v.matched().getId());
                updated++;
            }
        }
        log.info("部门导入完成：共 {} 行，新增 {}，更新 {}", plan.size(), created, updated);
        return DeptImportResult.applied(plan.size(), created, updated);
    }

    /* ==================== 解析与小工具 ==================== */

    private List<ParsedRow> parse(Workbook wb) {
        Sheet sheet = wb.getSheetAt(0);
        DataFormatter fmt = new DataFormatter();
        List<ParsedRow> out = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String name = cell(fmt, row, 0);
            String parent = cell(fmt, row, 1);
            String sort = cell(fmt, row, 2);
            String leader = cell(fmt, row, 3);
            if (name.isEmpty() && parent.isEmpty() && sort.isEmpty() && leader.isEmpty()) {
                continue;
            }
            if (out.size() >= MAX_ROWS) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "单次最多导入 " + MAX_ROWS + " 行");
            }
            out.add(new ParsedRow(i + 1, name, parent, sort, leader));
        }
        return out;
    }

    private static String cell(DataFormatter fmt, Row row, int idx) {
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    private static String key(Long parentId, String name) {
        return (parentId == null ? "0" : String.valueOf(parentId)) + "\u0000" + name;
    }

    private Map<String, Long> usernames() {
        Map<String, Long> out = new HashMap<>();
        userMapper.selectList(Wrappers.<SysUser>lambdaQuery().select(SysUser::getId, SysUser::getUsername))
                .forEach(u -> {
                    if (u.getUsername() != null) {
                        out.putIfAbsent(u.getUsername(), u.getId());
                    }
                });
        return out;
    }

    /** id → username（导出用，避免逐行查库）。 */
    private Map<Long, String> usernameById() {
        Map<Long, String> out = new HashMap<>();
        userMapper.selectList(Wrappers.<SysUser>lambdaQuery().select(SysUser::getId, SysUser::getUsername))
                .forEach(u -> {
                    if (u.getUsername() != null) {
                        out.put(u.getId(), u.getUsername());
                    }
                });
        return out;
    }

    /** 解析出的原始行。 */
    private record ParsedRow(int excelRow, String name, String parent, String sort, String leader) {
    }

    /** 解析出的上级引用：id 可为本文件内的占位 id。 */
    private record ParentRef(Long id, String path) {
    }

    /** 校验通过、待落库的行。 */
    private record Validated(int excelRow, String name, String parentPath, Integer sort,
                             Long leaderId, Department matched) {
    }
}
