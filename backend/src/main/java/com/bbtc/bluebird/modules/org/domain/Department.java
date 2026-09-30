package com.bbtc.bluebird.modules.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 部门（02 §3.2）。主实体，含 deleted；无审计基类字段（表仅 created_at/updated_at）。
 */
@Data
@TableName("sys_department")
public class Department {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;
    private String name;
    private Integer sort;
    private String corpId;
    private Long leaderUserId;
    /** 系统默认顶级部门标记：1 = 可改名、不可删除（用户反馈 #5）。 */
    private Integer isSystem;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;
    private Instant updatedAt;
}
