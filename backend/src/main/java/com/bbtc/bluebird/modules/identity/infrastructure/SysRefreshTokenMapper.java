package com.bbtc.bluebird.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.identity.domain.SysRefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysRefreshTokenMapper extends BaseMapper<SysRefreshToken> {
}
