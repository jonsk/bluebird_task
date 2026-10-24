package com.bbtc.bluebird.modules.note.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 日志-附件关联（关系表，无 deleted）。附件本体在 {@code attachment}，可被多个 note 引用。 */
@Data
@TableName("note_attachment_ref")
public class NoteAttachmentRef {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long noteId;
    private Long attachmentId;
    private Integer sort;
}
