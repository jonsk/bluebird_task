package com.bbtc.bluebird.modules.org.infrastructure.adapter;

import com.bbtc.bluebird.modules.org.dto.OrgPullData;

/**
 * 组织同步「拉取」适配器（PULL 模式）。
 *
 * <p>实现方只负责**取数并映射为中间模型**；落库（upsert / 软删 / 关联）统一由
 * {@code OrgSyncService} 处理，保证各 IdP 行为一致。
 */
public interface OrgSyncAdapter {

    /** 适配器名，与配置 {@code orgsync.adapter} 对应（小写）。 */
    String source();

    /** 拉取全量部门与用户。 */
    OrgPullData pull();
}
