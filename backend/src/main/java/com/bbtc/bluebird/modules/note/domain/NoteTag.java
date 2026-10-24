package com.bbtc.bluebird.modules.note.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 日志-标签（关系表，无 deleted）。与 task 共用 {@code tag} 表。 */
@Data
@TableName("note_tag")
public class NoteTag {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long noteId;
    private Long tagId;
}
