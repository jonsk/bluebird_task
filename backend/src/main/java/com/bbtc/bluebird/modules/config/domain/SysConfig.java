package com.bbtc.bluebird.modules.config.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 运行期配置（sys_config，通用 KV）。
 *
 * <p>与 application.yml 的关系：**本表优先，yml 兜底**。密文列 {@code encrypted=1} 时
 * {@code configValue} 为 AES-GCM Base64（见 {@code ConfigCrypto}），任何读接口都不返回明文。
 */
@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String groupKey;
    private String configKey;
    private String configValue;
    private String valueType;
    private Integer encrypted;
    private String description;
    private Long updatedBy;
    private Instant updatedAt;
}
