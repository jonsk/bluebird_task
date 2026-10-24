package com.bbtc.bluebird.modules.file.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.spi.AttachmentReferenceGuard;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.dto.FileVO;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.file.infrastructure.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 附件服务。
 *
 * <p>职责边界：file 模块只做「与实体无关」的判定（canBind/canReadGeneric/canDelete，均创建者/ADMIN）；
 * note/task 可见性由各自模块在调用前校验；引用保护通过 {@link AttachmentReferenceGuard} 扩展点。
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private final AttachmentMapper attachmentMapper;
    private final FileStorage fileStorage;
    private final AttachmentAccessService accessService;
    private final List<AttachmentReferenceGuard> referenceGuards;

    @Value("${app.storage.max-size:50MB}")
    private String maxSize;

    @Value("${app.storage.allowed-ext:}")
    private String allowedExt;

    public record Download(String fileName, Resource resource) {
    }

    /** 上传配置（maxSize 字节；allowedExt 空数组=不限制）。 */
    public record FileConfig(long maxSize, List<String> allowedExt) {
    }

    /* ==================== 上传 / 读取 ==================== */

    @Transactional
    public FileVO upload(MultipartFile file, Long taskId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件为空");
        }
        FileStorage.StoredFile stored;
        try {
            stored = fileStorage.store(file.getOriginalFilename(), file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取上传文件失败");
        }
        Attachment a = new Attachment();
        a.setTaskId(taskId);
        a.setFileName(stored.fileName());
        a.setRelativePath(stored.relativePath());
        a.setSize(stored.size());
        a.setMd5(stored.md5());
        a.setCreatedBy(UserContext.currentUserId());
        a.setCreatedAt(Instant.now());
        attachmentMapper.insert(a);
        return toVo(a, taskId);
    }

    /** 直接加载附件（不做权限校验；调用方负责可见性）。 */
    public Download load(Long id) {
        Attachment a = require(id);
        return new Download(a.getFileName(), fileStorage.load(a.getRelativePath()));
    }

    public Attachment require(Long id) {
        Attachment a = attachmentMapper.selectById(id);
        if (a == null) {
            throw HttpStatusException.notFound();
        }
        return a;
    }

    /* ==================== 判定 ==================== */

    public boolean canBind(Long id, Long userId) {
        return accessService.canBind(attachmentMapper.selectById(id), userId);
    }

    /** 通用下载/预览：仅创建者/ADMIN，否则 403。 */
    public Download loadGeneric(Long id) {
        Attachment a = require(id);
        if (!accessService.canReadGeneric(a, UserContext.currentUserId())) {
            throw HttpStatusException.forbidden();
        }
        return new Download(a.getFileName(), fileStorage.load(a.getRelativePath()));
    }

    /* ==================== 删除 ==================== */

    /** 通用物理删除：canDelete + 双引用保护（被 note/task 任一引用 → 409）。 */
    @Transactional
    public void delete(Long id) {
        Attachment a = require(id);
        if (!accessService.canDelete(a, UserContext.currentUserId())) {
            throw HttpStatusException.forbidden();
        }
        if (referencedByNote(a.getId()) || referencedByTask(a.getId())) {
            throw new HttpStatusException(HttpStatus.CONFLICT, ErrorCode.NOTE_ATTACHMENT_BOUND,
                    "附件仍被引用，请先解除关联");
        }
        attachmentMapper.deleteById(id);
        fileStorage.delete(a.getRelativePath());
    }

    /** task 删除级联：有 note 引用只解 task_id；无引用物理删行 + 删盘。 */
    @Transactional
    public void deleteByTask(Long taskId) {
        List<Attachment> list = attachmentMapper.selectList(
                Wrappers.<Attachment>lambdaQuery().eq(Attachment::getTaskId, taskId));
        for (Attachment a : list) {
            if (referencedByNote(a.getId())) {
                detachTask(a);
            } else {
                attachmentMapper.deleteById(a.getId());
                fileStorage.delete(a.getRelativePath());
            }
        }
    }

    /**
     * 移除单个 task 附件（由 task 专用删除端点调用，端点侧已校验 assertWritable + canRemoveFromTask）。
     *
     * @throws HttpStatusException 404：附件不属于该 task
     */
    @Transactional
    public void unbindTaskAttachments(Long taskId, Collection<Long> attachmentIds) {
        if (attachmentIds == null) {
            return;
        }
        for (Long id : attachmentIds.stream().distinct().toList()) {
            Attachment a = attachmentMapper.selectById(id);
            if (a == null || !taskId.equals(a.getTaskId())) {
                throw new HttpStatusException(HttpStatus.NOT_FOUND, ErrorCode.TASK_ATTACHMENT_NOT_LINKED);
            }
            if (referencedByNote(a.getId())) {
                detachTask(a);
            } else {
                attachmentMapper.deleteById(a.getId());
                fileStorage.delete(a.getRelativePath());
            }
        }
    }

    /**
     * 增量绑定附件到 task（不做全量替换，{@code fileIds} 为 null/空 → 不动附件）。
     *
     * @throws HttpStatusException 404 不存在；403 assertCanBind 失败；
     *                             409 已被其他 task 绑定
     */
    @Transactional
    public void bindToTask(List<Long> fileIds, Long taskId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        Long me = UserContext.currentUserId();
        for (Long id : fileIds.stream().distinct().toList()) {
            Attachment a = attachmentMapper.selectById(id);
            if (a == null) {
                throw HttpStatusException.notFound();
            }
            if (taskId.equals(a.getTaskId())) {
                continue; // 已属本 task：no-op（不重复校验 canBind）
            }
            if (a.getTaskId() != null) {
                throw new HttpStatusException(HttpStatus.CONFLICT, ErrorCode.NOTE_ATTACHMENT_BOUND);
            }
            if (!accessService.canBind(a, me)) {
                throw HttpStatusException.forbidden();
            }
            a.setTaskId(taskId);
            attachmentMapper.updateById(a);
        }
    }

    /* ==================== 查询 ==================== */

    public List<Attachment> listByTask(Long taskId) {
        return attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery().eq(Attachment::getTaskId, taskId));
    }

    /** 任务附件视图（含 task 专用 downloadUrl 与 canRemoveFromTask）。 */
    public List<FileVO> listTaskFiles(Long taskId, boolean taskWritable) {
        Long me = UserContext.currentUserId();
        boolean admin = UserContext.isAdmin();
        return listByTask(taskId).stream().map(a -> new FileVO(a.getId(), a.getFileName(), a.getSize(),
                a.getMd5(), taskDownloadUrl(taskId, a.getId()), a.getCreatedBy(),
                admin || me.equals(a.getCreatedBy()),
                taskWritable && (admin || (a.getCreatedBy() != null && a.getCreatedBy().equals(me)))))
                .toList();
    }

    public FileConfig config() {
        List<String> ext = allowedExt == null || allowedExt.isBlank()
                ? List.of()
                : List.of(allowedExt.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
        return new FileConfig(parseSize(maxSize), ext);
    }

    /* ==================== 内部 ==================== */

    public static String taskDownloadUrl(Long taskId, Long attachmentId) {
        return "/tasks/" + taskId + "/attachments/" + attachmentId + "/download";
    }

    public static String taskPreviewUrl(Long taskId, Long attachmentId) {
        return "/tasks/" + taskId + "/attachments/" + attachmentId + "/preview";
    }

    private FileVO toVo(Attachment a, Long taskId) {
        Long me = UserContext.currentUserId();
        boolean admin = UserContext.isAdmin();
        boolean mine = a.getCreatedBy() != null && a.getCreatedBy().equals(me);
        String url = taskId == null ? "/files/" + a.getId() : taskDownloadUrl(taskId, a.getId());
        Boolean canRemoveFromTask = taskId == null ? null : (admin || mine);
        return new FileVO(a.getId(), a.getFileName(), a.getSize(), a.getMd5(), url,
                a.getCreatedBy(), admin || mine, canRemoveFromTask);
    }

    private void detachTask(Attachment a) {
        a.setTaskId(null);
        // updateById 忽略 null：用 UpdateWrapper 强制置空
        attachmentMapper.update(null, Wrappers.<Attachment>lambdaUpdate()
                .eq(Attachment::getId, a.getId())
                .set(Attachment::getTaskId, null));
    }

    private boolean referencedByNote(Long attachmentId) {
        return referenceGuards.stream().anyMatch(g -> g.referencedByNote(attachmentId));
    }

    private boolean referencedByTask(Long attachmentId) {
        return referenceGuards.stream().anyMatch(g -> g.referencedByTask(attachmentId));
    }

    private long parseSize(String value) {
        if (value == null || value.isBlank()) {
            return Long.MAX_VALUE;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        long mul = 1;
        if (v.endsWith("KB")) {
            mul = 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("MB")) {
            mul = 1024 * 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("GB")) {
            mul = 1024L * 1024 * 1024;
            v = v.substring(0, v.length() - 2);
        } else if (v.endsWith("B")) {
            v = v.substring(0, v.length() - 1);
        }
        try {
            return Long.parseLong(v.trim()) * mul;
        } catch (NumberFormatException e) {
            return 50L * 1024 * 1024;
        }
    }
}
