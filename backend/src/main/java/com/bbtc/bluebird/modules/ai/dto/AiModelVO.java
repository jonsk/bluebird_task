package com.bbtc.bluebird.modules.ai.dto;

import java.time.Instant;

/** 后台模型列表项。API Key 只返回“是否已配置”，不返回明文或密文。 */
public record AiModelVO(
        Long id,
        String name,
        String provider,
        String baseUrl,
        String model,
        Double temperature,
        Integer maxTokens,
        Integer timeoutSeconds,
        boolean enabled,
        boolean active,
        boolean hasApiKey,
        Instant createdAt,
        Instant updatedAt) {
}
