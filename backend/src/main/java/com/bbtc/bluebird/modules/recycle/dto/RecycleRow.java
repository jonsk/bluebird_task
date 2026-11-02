package com.bbtc.bluebird.modules.recycle.dto;

import lombok.Data;

import java.time.Instant;

/** 回收站合并查询的行（task/note 的 deleted=1 记录投影）。 */
@Data
public class RecycleRow {

    /** TASK | NOTE */
    private String type;
    private Long id;
    /** 任务标题 / 日志内容前 60 字。 */
    private String title;
    private Instant deletedAt;
    private Long deletedBy;
}
