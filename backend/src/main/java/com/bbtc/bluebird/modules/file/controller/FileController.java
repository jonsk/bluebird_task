package com.bbtc.bluebird.modules.file.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.modules.file.application.FileService;
import com.bbtc.bluebird.modules.file.dto.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 通用附件接口（最小权限：创建者/ADMIN）。
 *
 * <p>task 附件走 {@code /api/v1/tasks/{taskId}/attachments} 专用端点；note 附件走
 * {@code /api/v1/notes/{id}/attachments}。本端点拒绝携带 {@code taskId} 的上传。
 */
@Tag(name = "file")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传附件（仅 taskId=null，note 上传前置）")
    @RepeatSubmit
    @PostMapping
    public ApiResult<FileVO> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "taskId", required = false) Long taskId) {
        if (taskId != null) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, ErrorCode.TASK_ATTACHMENT_FORBIDDEN);
        }
        return ApiResult.ok(fileService.upload(file, null));
    }

    @Operation(summary = "上传限制（maxSize 字节，allowedExt 空=不限制）")
    @GetMapping("/config")
    public ApiResult<FileService.FileConfig> config() {
        return ApiResult.ok(fileService.config());
    }

    @Operation(summary = "下载附件（仅创建者/ADMIN）")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return stream(fileService.loadGeneric(id), true);
    }

    @Operation(summary = "预览附件（仅创建者/ADMIN）")
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        return stream(fileService.loadGeneric(id), false);
    }

    @Operation(summary = "物理删除附件（被引用则 409）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return ApiResult.ok();
    }

    private ResponseEntity<Resource> stream(FileService.Download d, boolean attachment) {
        ContentDisposition cd = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(d.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(d.resource());
    }
}
