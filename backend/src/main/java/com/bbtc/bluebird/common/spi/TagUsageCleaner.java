package com.bbtc.bluebird.common.spi;

/**
 * 标签删除时的关联清理扩展点（避免 task ↔ note 模块互相依赖）。
 *
 * <p>{@code TagService.delete()} 依次调用全部实现；调用方事务包裹，任一实现失败整体回滚。
 * 实现需自行清理本实体与 {@code tag} 的关系表（如 {@code task_tag} / {@code note_tag}）。
 */
public interface TagUsageCleaner {

    /** 删除指定标签在本实体中的所有关联（不含 {@code tag} 行本身）。 */
    void cleanByTagId(Long tagId);
}
