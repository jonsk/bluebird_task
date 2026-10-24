package com.bbtc.bluebird.modules.note.dto;

import com.bbtc.bluebird.modules.task.dto.TagVO;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 工作日志基础视图（列表/详情共用；**不含 ownerTags**）。
 *
 * <p>{@code contentSummary} 固定取正文前 200 码位（保留换行，不补省略号），前端不二次截断。
 */
@Data
public class NoteVO {

    private Long id;
    private String content;
    private String contentSummary;
    /** YYYY-MM-DD */
    private String logDate;
    private String visibility;
    private boolean pinned;
    private boolean archived;
    private Long ownerId;
    private String ownerName;
    private String ownerDept;
    private Long version;
    private boolean canEdit;
    private boolean canManage;
    /** VIEWER | EDITOR | OWNER | ADMIN | null */
    private String grantRole;
    private Instant createdAt;
    private Instant updatedAt;
    /** 本 note 已选标签。 */
    private List<TagVO> tags;
}
