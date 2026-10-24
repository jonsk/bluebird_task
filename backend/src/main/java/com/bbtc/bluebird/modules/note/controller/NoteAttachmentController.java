package com.bbtc.bluebird.modules.note.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.audit.annotation.OperateLog;
import com.bbtc.bluebird.modules.note.application.NoteAttachmentService;
import com.bbtc.bluebird.modules.note.dto.NoteAttachmentBindReq;
import com.bbtc.bluebird.modules.note.dto.NoteAttachmentVO;
import com.bbtc.bluebird.modules.file.application.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 日志附件接口（专用下载端点；下载一律需 Bearer，前端走 axios blob）。 */
@Tag(name = "note")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteAttachmentController {

    private final NoteAttachmentService attachmentService;

    @Operation(summary = "关联附件")
    @PostMapping("/{id}/attachments")
    public ApiResult<NoteAttachmentVO> bind(@PathVariable Long id,
                                            @Valid @RequestBody NoteAttachmentBindReq req) {
        return ApiResult.ok(attachmentService.bind(id, req.attachmentId()));
    }

    @Operation(summary = "附件列表")
    @GetMapping("/{id}/attachments")
    public ApiResult<List<NoteAttachmentVO>> list(@PathVariable Long id) {
        return ApiResult.ok(attachmentService.list(id));
    }

    @Operation(summary = "移除附件关联")
    @OperateLog(module = "note", action = "attachment-remove")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResult<Void> remove(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachmentService.remove(id, attachmentId);
        return ApiResult.ok();
    }

    @Operation(summary = "下载附件")
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        FileService.Download d = attachmentService.download(id, attachmentId);
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(d.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(d.resource());
    }
}
