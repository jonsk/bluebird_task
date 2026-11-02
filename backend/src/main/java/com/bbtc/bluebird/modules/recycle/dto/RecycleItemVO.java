package com.bbtc.bluebird.modules.recycle.dto;

import java.time.Instant;

/**
 * 回收站条目。
 *
 * @param type             TASK | NOTE
 * @param title            任务标题 / 日志内容摘要
 * @param deletedAt        删除时间；升级前删除的历史记录为 null（界面显示「未知」）
 * @param deletedByName    删除人显示名（deletedBy 对应用户不存在时为 null）
 * @param restorable       是否可恢复（父链已被彻底删除、或已被物理删除时为 false）
 * @param unrestorableReason 不可恢复的原因文案，可直接展示
 */
public record RecycleItemVO(
        String type,
        Long id,
        String title,
        Instant deletedAt,
        Long deletedBy,
        String deletedByName,
        boolean restorable,
        String unrestorableReason) {
}
