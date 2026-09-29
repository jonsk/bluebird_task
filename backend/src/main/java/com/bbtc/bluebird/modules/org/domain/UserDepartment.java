package com.bbtc.bluebird.modules.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-部门多归属（02 §3.2）。关系表，无 deleted。
 */
@Data
@TableName("sys_user_department")
public class UserDepartment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long deptId;
    private Integer isPrimary;
}
