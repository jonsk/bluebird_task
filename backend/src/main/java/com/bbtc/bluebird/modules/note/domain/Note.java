package com.bbtc.bluebird.modules.note.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 工作日志（note）。纯文本 + 工作日 log_date + 乐观锁 + 逻辑删除。
 *
 * <p>可见性固定为「仅自己 + 被授权者」，不再有 visibility/archived 分支；
 * 上级读下属（员工日志）由 {@code scope=subordinate} 与开关决定，属制度性例外。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note")
public class Note extends BaseEntity {

    /** 日粒度工作日志（默认；历史数据全部是这一档）。 */
    public static final String PERIOD_DAY = "DAY";
    /** 周粒度工作记录：一周一条，period_start = 该周周一。 */
    public static final String PERIOD_WEEK = "WEEK";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String content;
    /** 工作日 YYYY-MM-DD（按日聚合，一天可多条）；周记录写该周周一，保证 NOT NULL。 */
    private String logDate;
    /** DAY | WEEK。周记录的日期口径见 {@link #periodStart}。 */
    private String periodType;
    /** 周期起点 YYYY-MM-DD：DAY = log_date，WEEK = 该周周一（ISO 周，Asia/Shanghai）。 */
    private String periodStart;
    private Long ownerId;
    /** owner 部门快照（用于详情展示部门名）。 */
    private Long deptId;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    /** 回收站：删除时间（历史已删行为 null，界面按「未知」展示）。 */
    private Instant deletedAt;

    /** 回收站：删除人（历史已删行为 null）。 */
    private Long deletedBy;
}
