package com.bbtc.bluebird.modules.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 登录日志（02 §6.2）。物理追加，无 deleted 列。
 *
 * <p>V5 扩展：{@code provider}（LOCAL/CASDOOR）、{@code eventType}（LOGIN/LOGOUT）、
 * {@code externalId}（外部身份源唯一标识）。
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
    private String provider;
    private String eventType;
    private String externalId;
    private Instant createdAt;
}
