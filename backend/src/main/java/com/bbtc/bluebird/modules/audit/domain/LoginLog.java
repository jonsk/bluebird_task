package com.bbtc.bluebird.modules.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 登录日志（02 §6.2）。物理追加，无 deleted 列。
 */
@Data
@TableName("audit_login_log")
public class LoginLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private Integer success;
    private String ip;
    private String userAgent;
    private Instant createdAt;
}
