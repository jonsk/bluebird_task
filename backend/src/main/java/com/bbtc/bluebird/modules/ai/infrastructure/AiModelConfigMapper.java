package com.bbtc.bluebird.modules.ai.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.ai.domain.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
