package com.bbtc.bluebird.modules.task.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** 任务详情（TaskVO + subtasks）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailVO extends TaskVO {
    private List<TaskVO> subtasks = new ArrayList<>();
}
