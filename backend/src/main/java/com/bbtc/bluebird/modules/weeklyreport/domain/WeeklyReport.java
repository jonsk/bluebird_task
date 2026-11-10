package com.bbtc.bluebird.modules.weeklyreport.domain;

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
 * 个人周报（每人每周最多一条）。
 *
 * <p>正文以 HTML 保存（与工作日志编辑器同 schema），{@code aiResultJson} 保留模型原始结构化结果，
 * 便于追溯「哪句话来自哪条日志」以及重新排版。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weekly_report")
public class WeeklyReport extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long ownerId;
    /** 周一，YYYY-MM-DD（Asia/Shanghai）。 */
    private String weekStart;
    /** 周日，YYYY-MM-DD。 */
    private String weekEnd;
    /** GENERATING | DRAFT | DONE | FAILED。 */
    private String status;

    private String contentHtml;
    private String aiResultJson;
    /** 参与生成的日志 ID（JSON 数组字符串）。 */
    private String sourceNoteIds;
    /** 参与生成的日志版本快照（JSON 对象字符串）。 */
    private String sourceNoteVersions;

    private Long modelConfigId;
    private String modelName;
    private String promptVersion;
    private String userPromptSnapshot;
    private Integer inputTokens;
    private Integer outputTokens;
    private String errorMessage;
    private Instant generatedAt;
    private Instant completedAt;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;
}
