package com.bbtc.bluebird.modules.config.dto;

import java.util.List;

/**
 * 配置项视图。
 *
 * @param value  非密文为真实值；密文为掩码 {@code ******}（永不返回明文）
 * @param source DB | YML | DEFAULT（值的实际来源）
 */
public record ConfigItemVO(
        String key,
        String label,
        String value,
        String type,
        boolean secret,
        boolean hasSecret,
        String source,
        String description,
        List<String> options,
        boolean requiresRestart) {
}
