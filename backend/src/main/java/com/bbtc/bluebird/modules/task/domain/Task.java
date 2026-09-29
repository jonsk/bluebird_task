package com.bbtc.bluebird.modules.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 任务（主任务/子任务，02 §4.2）。含乐观锁 version 与逻辑删除 deleted。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;
    private String title;
    private String content;
    private String status;
    private Integer completed;
    private String priority;
    private Instant dueAt;
    private Instant remindAt;
    private String cycleRule;
    private Instant cycleLastCompleted;
    private Long categoryId;
    private Long ownerId;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
