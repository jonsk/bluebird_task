package com.bbtc.bluebird.modules.weeklyreport.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 周报个人偏好：只保存「补充规则」，不能覆盖系统固定的 JSON 结构与安全约束。
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
}
