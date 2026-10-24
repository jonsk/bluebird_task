package com.bbtc.bluebird.modules.task.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.audit.annotation.OperateLog;
import com.bbtc.bluebird.modules.file.application.FileService;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.dto.FileVO;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.task.application.TaskAuthService;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * task 专用附件端点（类级 {@code /api/v1/tasks}，避免拼到 {@code /files} 路径下）。
 *
 * <p>权限：上传/删除 = 任务可写；下载/预览 = 任务可见 + 归属匹配；不满足一律 404（不泄露存在性），
 * 仅「可见但不可写」返回 403。
 */
@Tag(name = "task")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskAttachmentController {

    private final FileService fileService;
    private final TaskAuthService taskAuthService;
    private final TaskMapper taskMapper;
    private final AttachmentMapper attachmentMapper;

    @Operation(summary = "上传任务附件")
    @RepeatSubmit
    @PostMapping("/{taskId}/attachments")
    public ApiResult<FileVO> upload(@PathVariable Long taskId,
                                    @org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file) {
        Long me = UserContext.currentUserId();
        Task task = requireVisibleTask(taskId, me);
        if (!taskAuthService.isWritable(task, me)) {
            throw HttpStatusException.forbidden();
        }
        FileVO vo = fileService.upload(file, taskId);
        return ApiResult.ok(vo);
    }

    @Operation(summary = "任务附件下载")
    @GetMapping("/{taskId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long taskId, @PathVariable Long attachmentId) {
        requireLinked(taskId, attachmentId);
        return stream(fileService.load(attachmentId), true);
    }

    @Operation(summary = "任务附件预览")
    @GetMapping("/{taskId}/attachments/{attachmentId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long taskId, @PathVariable Long attachmentId) {
        requireLinked(taskId, attachmentId);
        return stream(fileService.load(attachmentId), false);
    }

    @Operation(summary = "移除任务附件")
    @OperateLog(module = "task", action = "attachment-remove")
    @DeleteMapping("/{taskId}/attachments/{attachmentId}")
    public ApiResult<Void> remove(@PathVariable Long taskId, @PathVariable Long attachmentId) {
        Long me = UserContext.currentUserId();
        Task task = requireVisibleTask(taskId, me);
        if (!taskAuthService.isWritable(task, me)) {
            throw HttpStatusException.forbidden();
        }
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || !taskId.equals(a.getTaskId())) {
            throw HttpStatusException.notFound();
        }
        boolean canRemove = UserContext.isAdmin()
                || (a.getCreatedBy() != null && a.getCreatedBy().equals(me));
        if (!canRemove) {
            throw HttpStatusException.forbidden();
        }
        fileService.unbindTaskAttachments(taskId, List.of(attachmentId));
        return ApiResult.ok();
    }

    private Task requireVisibleTask(Long taskId, Long me) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !taskAuthService.isVisible(task, me)) {
            throw HttpStatusException.notFound();
        }
        return task;
    }

    private void requireLinked(Long taskId, Long attachmentId) {
        Long me = UserContext.currentUserId();
        requireVisibleTask(taskId, me);
        Attachment a = attachmentMapper.selectById(attachmentId);
        if (a == null || !taskId.equals(a.getTaskId())) {
            throw HttpStatusException.notFound();
        }
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
