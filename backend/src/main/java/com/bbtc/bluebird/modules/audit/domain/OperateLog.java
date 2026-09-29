package com.bbtc.bluebird.modules.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 操作日志（02 §6.2）。只追加，无改/删接口。
 */
@Data
@TableName("audit_operate_log")
public class OperateLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String module;
    private String action;
    private String uri;
    private String method;
    private String reqParams;
    private Long userId;
    private String ip;
    private String userAgent;
    private Long duration;
    private Integer status;
    private String msg;
    private Instant createdAt;
}
