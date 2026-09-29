package com.bbtc.bluebird.modules.file.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.file.infrastructure.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/** 附件服务（02 §5.5/§5.6）。 */
@Service
@RequiredArgsConstructor
public class FileService {

    private final AttachmentMapper attachmentMapper;
    private final FileStorage fileStorage;

    public record Download(String fileName, Resource resource) {
    }

    private record FileRefVO(Long id, String fileName, Long size, String md5) {
    }

    @Transactional
    public Object upload(MultipartFile file, Long taskId) {
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
        return new FileRefVO(a.getId(), a.getFileName(), a.getSize(), a.getMd5());
    }

    public Download download(Long id) {
        Attachment a = require(id);
        return new Download(a.getFileName(), fileStorage.load(a.getRelativePath()));
    }

    @Transactional
    public void delete(Long id) {
        Attachment a = require(id);
        attachmentMapper.deleteById(id);
        fileStorage.delete(a.getRelativePath());
    }

    /** 任务删除级联：物理删行 + 磁盘（02 §4.7）。 */
    @Transactional
    public void deleteByTask(Long taskId) {
        List<Attachment> list = attachmentMapper.selectList(
                Wrappers.<Attachment>lambdaQuery().eq(Attachment::getTaskId, taskId));
        for (Attachment a : list) {
            attachmentMapper.deleteById(a.getId());
            fileStorage.delete(a.getRelativePath());
        }
    }

    /** 将已上传附件绑定到任务（建/改任务时）。 */
    @Transactional
    public void bindToTask(List<Long> fileIds, Long taskId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        for (Long id : fileIds) {
            Attachment a = attachmentMapper.selectById(id);
            if (a != null) {
                a.setTaskId(taskId);
                attachmentMapper.updateById(a);
            }
        }
    }

    public List<Object> listRefs(Long taskId) {
        return attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery().eq(Attachment::getTaskId, taskId))
                .stream().map(a -> (Object) new FileRefVO(a.getId(), a.getFileName(), a.getSize(), a.getMd5()))
                .toList();
    }

    public List<Attachment> listByTask(Long taskId) {
        return attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery().eq(Attachment::getTaskId, taskId));
    }

    private Attachment require(Long id) {
        Attachment a = attachmentMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return a;
    }
}
