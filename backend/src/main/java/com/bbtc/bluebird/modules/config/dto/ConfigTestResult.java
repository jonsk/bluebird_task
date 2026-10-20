package com.bbtc.bluebird.modules.config.dto;

/** Casdoor 连通性测试结果。 */
public record ConfigTestResult(
        boolean ok,
        String message,
        long latencyMs,
        String issuer,
        String authorizationEndpoint,
        String tokenEndpoint) {
}
