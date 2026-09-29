package com.bbtc.bluebird.modules.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/** 自定义栏（用户私有，02 §4.2）。 */
@Data
@TableName("task_menu")
public class TaskMenu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String name;
    private Integer sort;

    @Version
    private Integer version;
}
