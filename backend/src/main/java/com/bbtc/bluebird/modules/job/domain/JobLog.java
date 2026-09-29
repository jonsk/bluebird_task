package com.bbtc.bluebird.modules.job.domain;

import com.baomidou.mybatisplus.annotation.IdType;
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

    private String jobName;
    private Integer status;
    private Long duration;
    private String msg;
    private Instant startedAt;
    private Instant finishedAt;
}
