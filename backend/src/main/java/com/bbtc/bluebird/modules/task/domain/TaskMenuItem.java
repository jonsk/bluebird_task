package com.bbtc.bluebird.modules.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 自定义栏条目（关系表，无 deleted）。 */
@Data
@TableName("task_menu_item")
public class TaskMenuItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long menuId;
    private Long taskId;
    private Integer sort;
}
