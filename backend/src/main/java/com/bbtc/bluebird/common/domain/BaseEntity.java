package com.bbtc.bluebird.common.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.Instant;

/**
 * 审计字段基类（02 §1.6）。仅主实体继承（关系/从属/日志表无 {@code deleted}）。
 */
@Data
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
