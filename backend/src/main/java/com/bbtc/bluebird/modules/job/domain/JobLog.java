package com.bbtc.bluebird.modules.job.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** 调度任务日志（sys_job_log，02 §5.6/0306 Q6）。 */
@Data
@TableName("sys_job_log")
public class JobLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** DDL 列名为 {@code job}（02 §5.6 的建表语句为准）。 */
    @TableField("job")
    private String jobName;

    private Integer status;
    private Long duration;

    /** DDL 列名为 {@code error}（02 §5.6 的建表语句为准）。 */
    @TableField("error")
    private String msg;

    private Instant startedAt;
    private Instant finishedAt;
}
