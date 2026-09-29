package com.bbtc.bluebird.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（02 §1.8）。secret 默认由首次运行自动生成写入 application.yml（ADR-010）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 密钥，≥256bit。空则运行时生成临时密钥（仅 dev）。 */
    private String secret;

    /** access 有效期（秒）。 */
    private long accessTtl = 3600;

    /** refresh 有效期（秒）。 */
    private long refreshTtl = 604800;
}
