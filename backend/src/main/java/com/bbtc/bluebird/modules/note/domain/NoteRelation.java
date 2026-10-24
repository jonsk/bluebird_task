package com.bbtc.bluebird.modules.note.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 引用关系（{@code source → target}，本期仅 REFERENCE）。 */
@Data
@TableName("note_relation")
public class NoteRelation {

    public static final String TARGET_NOTE = "NOTE";
    public static final String TARGET_TASK = "TASK";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sourceNoteId;
    private String targetType;
    private Long targetId;
    /** 引用时间（可晚于 note 创建时间）。 */
    private Instant createdAt;
    /** 引用人（EDITOR/ADMIN 也能添加，非必为 source owner）。 */
    private Long createdBy;
}
