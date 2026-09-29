package com.bbtc.bluebird.common.domain;

import jakarta.validation.constraints.Min;

/**
 * 分页参数（02 §1.6）。size 上限 100。
 */
public record PageParam(
        @Min(1) long page,
        @Min(1) long size) {

    public PageParam {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
    }

    public static PageParam of(long page, long size) {
        return new PageParam(page, size);
    }
}
