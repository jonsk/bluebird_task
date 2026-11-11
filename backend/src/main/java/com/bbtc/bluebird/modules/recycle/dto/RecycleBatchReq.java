package com.bbtc.bluebird.modules.recycle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 批量操作（恢复 / 彻底删除）请求体。 */
public record RecycleBatchReq(@NotEmpty(message = "请至少选择一条记录") @Valid List<RecycleRef> items) {

    /** 单条引用：type=TASK|NOTE（大小写不敏感）。 */
    public record RecycleRef(String type, Long id) {
    }
}
