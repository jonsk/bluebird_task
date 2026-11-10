package com.bbtc.bluebird.modules.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑模型。apiKey 为空或 ****** 表示不修改。 */
public record AiModelSaveReq(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 40) String provider,
        @NotBlank @Size(max = 500) String baseUrl,
        @Size(max = 5000) String apiKey,
        @NotBlank @Size(max = 120) String model,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @Min(1) @Max(32768) Integer maxTokens,
        /** 只校验下限：上限交给 AI 网关与各平台自身超时，不写死在代码里。 */
        @Min(5) Integer timeoutSeconds,
        Boolean enabled) {
}
