package com.bbtc.bluebird.modules.task.dto;

/** 自定义栏条目（引用任务摘要）。 */
public record MenuItemDTO(Long id, Long menuId, Long taskId, Integer sort, TaskBrief task) {
}
