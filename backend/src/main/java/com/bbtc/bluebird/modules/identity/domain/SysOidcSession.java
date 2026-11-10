package com.bbtc.bluebird.modules.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * Casdoor 登录会话的 id_token（V14）。
 *
 * <p>仅用于登出时拼 {@code id_token_hint}，让 Casdoor 静默清除 SSO 会话，
 * 而不是弹「确认退出」页；每用户保留最近一次登录的值。
 */
@Data
@TableName("sys_oidc_session")
public class SysOidcSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String idToken;
    private Instant updatedAt;
}
