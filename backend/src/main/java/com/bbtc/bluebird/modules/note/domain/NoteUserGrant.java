package com.bbtc.bluebird.modules.note.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户级全量授权（owner 把所有工作日志的查看/编辑权限授予指定用户）。
 *
 * <p>关系表，无 deleted；状态机 PENDING → APPROVED / REJECTED / REVOKED。
 */
@Data
@TableName("note_user_grant")
public class NoteUserGrant {

    public static final String VIEWER = "VIEWER";
    public static final String EDITOR = "EDITOR";

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String REVOKED = "REVOKED";

    public static final String MODE_OWNER = "OWNER";
    public static final String MODE_SUPERIOR = "SUPERIOR";
    public static final String MODE_ADMIN = "ADMIN";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long grantorId;
    private Long granteeId;
    private String role;
    private String status;
    private String mode;
    private Instant createdAt;
    private Long createdBy;
    private Instant approvedAt;
    private Long approvedBy;
    private Instant revokedAt;
    private Long revokedBy;
}
