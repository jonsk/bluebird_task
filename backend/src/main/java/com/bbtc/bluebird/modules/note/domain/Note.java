package com.bbtc.bluebird.modules.note.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作日志（note）。纯文本 + 工作日 log_date + 可见性 + 乐观锁 + 逻辑删除。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note")
public class Note extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String content;
    /** 工作日 YYYY-MM-DD（按日聚合，一天可多条）。 */
    private String logDate;
    /** PRIVATE | INTERNAL | PUBLIC，见 {@link Visibility}。 */
    private String visibility;
    private Integer pinned;
    private Integer archived;
    private Long ownerId;
    /** owner 部门快照（INTERNAL 用；仅在「转为 INTERNAL」时刷新）。 */
    private Long deptId;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
