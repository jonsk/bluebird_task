package com.bbtc.bluebird.modules.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户（合并旧 sys_user + user，02 §2.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private String externalId;
    /** 外部身份源标识（V5）：casdoor 等；本地用户为 null。 */
    private String externalSource;
    private String password;
    private String name;
    private String mobile;
    private String email;
    private String avatarUrl;
    private Long deptId;
    private String roleCode;
    private String status;
    private Integer mustChangePassword;

    @TableLogic
    private Integer deleted;
}
