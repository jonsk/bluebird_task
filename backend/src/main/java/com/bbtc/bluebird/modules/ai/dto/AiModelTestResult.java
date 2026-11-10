package com.bbtc.bluebird.modules.ai.dto;

/** 模型连通性测试结果。 */
public record AiModelTestResult(boolean ok, String message, long latencyMs, String model) {
}
