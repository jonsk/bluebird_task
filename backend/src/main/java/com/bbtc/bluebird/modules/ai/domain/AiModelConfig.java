package com.bbtc.bluebird.modules.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bbtc.bluebird.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** OpenAI 兼容模型配置。全局最多一条 active=1。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String provider;
    private String baseUrl;
    private String apiKeyCipher;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    /** 1=启用，0=停用。停用模型不能成为 active。 */
    private Integer enabled;
    /** 1=当前周报使用的模型。 */
    private Integer active;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
