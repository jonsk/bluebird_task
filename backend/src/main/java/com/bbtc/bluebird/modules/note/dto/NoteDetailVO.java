package com.bbtc.bluebird.modules.note.dto;

import com.bbtc.bluebird.modules.task.dto.TagVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 详情视图：{@code NoteVO} + 引用/被引用/附件 + ownerTags（唯一出现 ownerTags 的地方）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDetailVO extends NoteVO {

    private List<NoteRelationVO> relations = new ArrayList<>();
    private List<NoteBacklinkVO> backlinks = new ArrayList<>();
    private List<NoteAttachmentVO> attachments = new ArrayList<>();
    /** owner 完整标签；仅 canEdit / canManage 时返回。 */
    private List<TagVO> ownerTags;
    /** 字段级可编辑性（正文/日期/可见性/标签），前端据此禁用控件 */
    private NoteFieldPermissionsVO fieldPermissions = NoteFieldPermissionsVO.none();
}
