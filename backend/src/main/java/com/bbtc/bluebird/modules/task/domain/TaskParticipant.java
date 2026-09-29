package com.bbtc.bluebird.modules.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 任务参与人（ASSIGNEE/CC，02 §4.2）。关系表，无 deleted。 */
@Data
@TableName("task_participant")
public class TaskParticipant {

    public static final String ASSIGNEE = "ASSIGNEE";
    public static final String CC = "CC";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;
    private Long userId;
    private String role;
}
