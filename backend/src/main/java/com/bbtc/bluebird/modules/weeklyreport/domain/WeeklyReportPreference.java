package com.bbtc.bluebird.modules.weeklyreport.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 周报个人偏好：补充规则 + 生成类型。
 *
 * <p>补充规则只能影响行文，不能覆盖系统固定的 JSON 结构与安全约束；
 * 生成类型决定手动生成与定时生成使用每日记录还是每周记录，默认每周记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weekly_report_preference")
public class WeeklyReportPreference extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    /** concise | detailed | result | plan（提示行文风格）。 */
    private String style;
    private String focus;
    private String customRules;
    /** DAILY | WEEKLY：手动生成与定时生成默认使用的数据来源。 */
    private String reportType;
}
