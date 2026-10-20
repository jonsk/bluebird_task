package com.bbtc.bluebird.modules.org.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 组织同步日志（org_sync_log）。
 *
 * <p>每次同步一条，含统计；与 {@code sys_job_log}（仅手动重跑、无统计）职责不同。
 */
@Data
@TableName("org_sync_log")
public class OrgSyncLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String source;
    private String syncType;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer total;
    private Integer inserted;
    private Integer updated;
    private Integer skipped;
    private String errorMessage;
}
