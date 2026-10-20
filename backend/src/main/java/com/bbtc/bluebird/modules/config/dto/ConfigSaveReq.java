package com.bbtc.bluebird.modules.config.dto;

import java.util.Map;

/** 保存请求：key → 新值。值为 null 或掩码时视为不修改；空串表示清空。 */
public record ConfigSaveReq(Map<String, String> values) {
}
