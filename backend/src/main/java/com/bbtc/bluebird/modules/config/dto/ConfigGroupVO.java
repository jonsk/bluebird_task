package com.bbtc.bluebird.modules.config.dto;

import java.util.List;

/** 配置分组视图（前端按 group 渲染一张卡片）。 */
public record ConfigGroupVO(
        String group,
        String label,
        String description,
        List<ConfigItemVO> items) {
}
