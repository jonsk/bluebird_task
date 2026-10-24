package com.bbtc.bluebird.modules.file.application;

import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 与实体无关的附件判定（**不含** note/task 可见性与引用保护）。
 *
 * <p>note/task 可见性由各自模块在调用前校验，避免 file → note/task 反向依赖。
 */
@Service
@RequiredArgsConstructor
public class AttachmentAccessService {

    private final AttachmentMapper attachmentMapper;

    /** 可绑定：附件创建者本人 / ADMIN（不看可见性、不看引用）。 */
    public boolean canBind(Attachment a, Long userId) {
        return a != null && (UserContext.isAdmin()
                || (a.getCreatedBy() != null && a.getCreatedBy().equals(userId)));
    }

    /** 通用读（{@code GET /files/{id}}、{@code /preview}）：创建者 / ADMIN。 */
    public boolean canReadGeneric(Attachment a, Long userId) {
        return canBind(a, userId);
    }

    /** 可物理删除：创建者 / ADMIN（不含引用保护）。 */
    public boolean canDelete(Attachment a, Long userId) {
        return canBind(a, userId);
    }

    public Attachment require(Long id) {
        return attachmentMapper.selectById(id);
    }
}
