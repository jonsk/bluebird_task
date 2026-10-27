package com.bbtc.bluebird.modules.note.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** 详情视图：{@code NoteVO} + 关联 / 反链 / 附件 + 字段级权限。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoteDetailVO extends NoteVO {

    private List<NoteRelationVO> relations = new ArrayList<>();
    private List<NoteBacklinkVO> backlinks = new ArrayList<>();
    private List<NoteAttachmentVO> attachments = new ArrayList<>();
    /** 字段级可编辑性（正文）：前端据此禁用编辑区，写路径校验同源。 */
    private NoteFieldPermissionsVO fieldPermissions = NoteFieldPermissionsVO.none();
}
