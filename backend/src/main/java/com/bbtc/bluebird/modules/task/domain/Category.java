package com.bbtc.bluebird.modules.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.Instant;

/** 分类（个人/部门/组织共享，ADR-015，02 §4.2）。 */
@Data
@TableName("category")
public class Category {

    public static final String PERSONAL = "PERSONAL";
    public static final String DEPARTMENT = "DEPARTMENT";
    public static final String ORG = "ORG";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long parentId;
    private String name;
    private String scope;
    private Long ownerId;
    private Long deptId;
    private Integer sort;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    private Instant createdAt;
    private Instant updatedAt;
}
