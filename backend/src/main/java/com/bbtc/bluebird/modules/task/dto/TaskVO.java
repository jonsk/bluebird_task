package com.bbtc.bluebird.modules.task.dto;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务视图（02 §4.5）。用类（非 record）以便 {@link com.bbtc.bluebird.modules.task.dto.TaskDetailVO} 继承。
 */
@Data
public class TaskVO {
    private Long id;
    private String title;
    private String content;
    private String status;
    private Boolean completed;
    private Instant dueAt;
    private Instant remindAt;
    private String priority;
    private Object cycleRule;
    private Instant cycleLastCompleted;
    private UserBrief owner;
    private List<UserBrief> assignees = new ArrayList<>();
    private List<UserBrief> ccUsers = new ArrayList<>();
    private List<Long> participantIds = new ArrayList<>();
    private Long version;
    private CategoryBrief category;
    private List<TagVO> tags = new ArrayList<>();
    private int subtaskCompleted;
    private int subtaskTotal;
    private List<FileRef> files = new ArrayList<>();
    private Instant createdAt;
}
