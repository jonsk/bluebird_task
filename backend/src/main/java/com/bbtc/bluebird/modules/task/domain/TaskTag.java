package com.bbtc.bluebird.modules.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 任务-标签（关系表，无 deleted）。 */
@Data
@TableName("task_tag")
public class TaskTag {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;
    private Long tagId;
}
