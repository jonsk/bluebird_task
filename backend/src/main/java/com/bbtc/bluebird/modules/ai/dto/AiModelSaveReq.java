package com.bbtc.bluebird.modules.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑模型。apiKey 为空或 ****** 表示不修改。 */
public record AiModelSaveReq(
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 80, message = "模型名称不能超过 80 个字符") String name,
        @NotBlank(message = "供应商不能为空")
        @Size(max = 40, message = "供应商不能超过 40 个字符") String provider,
        @NotBlank(message = "接口地址不能为空")
        @Size(max = 500, message = "接口地址不能超过 500 个字符") String baseUrl,
        @Size(max = 5000, message = "API Key 不能超过 5000 个字符") String apiKey,
        @NotBlank(message = "模型标识不能为空")
        @Size(max = 120, message = "模型标识不能超过 120 个字符") String model,
        @DecimalMin(value = "0.0", message = "温度不能小于 0.0")
        @DecimalMax(value = "2.0", message = "温度不能大于 2.0") Double temperature,
        @Min(value = 1, message = "最大 Token 数不能小于 1")
        @Max(value = 32768, message = "最大 Token 数不能大于 32768") Integer maxTokens,
        /** 只校验下限：上限交给 AI 网关与各平台自身超时，不写死在代码里。 */
        @Min(value = 5, message = "超时时间不能小于 5 秒") Integer timeoutSeconds,
        Boolean enabled) {
}
