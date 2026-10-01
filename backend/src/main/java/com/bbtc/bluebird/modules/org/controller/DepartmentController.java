package com.bbtc.bluebird.modules.org.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.org.application.DepartmentExcelService;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import com.bbtc.bluebird.modules.org.dto.DeptCreateReq;
import com.bbtc.bluebird.modules.org.dto.DeptImportResult;
import com.bbtc.bluebird.modules.org.dto.DeptUpdateReq;
import com.bbtc.bluebird.modules.org.dto.DepartmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 部门接口（02 §3.4）。 */
@Tag(name = "org")
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentExcelService departmentExcelService;

    @Operation(summary = "部门树")
    @GetMapping
    public ApiResult<List<DepartmentVO>> tree() {
        return ApiResult.ok(departmentService.tree());
    }

    @Operation(summary = "导出部门（.xlsx）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return xlsx(departmentExcelService.export(), "部门数据");
    }

    @Operation(summary = "下载部门导入模板（.xlsx）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        return xlsx(departmentExcelService.template(), "部门导入模板");
    }

    @Operation(summary = "导入部门（.xlsx，先全量校验，有错则整份不导入）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @com.bbtc.bluebird.modules.audit.annotation.OperateLog(module = "org", action = "import_dept")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<DeptImportResult> importDept(@RequestParam("file") MultipartFile file) {
        return ApiResult.ok(departmentExcelService.importFrom(file));
    }

    private ResponseEntity<byte[]> xlsx(byte[] body, String namePrefix) {
        String fileName = namePrefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        ContentDisposition cd = ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @Operation(summary = "新增部门（ADMIN/USER_MANAGER）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @RepeatSubmit
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody DeptCreateReq req) {
        return ApiResult.ok(departmentService.create(req));
    }

    @Operation(summary = "修改部门（含 leaderId）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @com.bbtc.bluebird.modules.audit.annotation.OperateLog(module = "org", action = "update_leader")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody DeptUpdateReq req) {
        departmentService.update(id, req);
        return ApiResult.ok();
    }

    @Operation(summary = "删除部门（有子级/有成员则拒绝）")
    @PreAuthorize("hasAnyRole('ADMIN','USER_MANAGER')")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResult.ok();
    }
}
