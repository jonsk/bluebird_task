package com.bbtc.bluebird.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.identity.domain.SysOidcSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysOidcSessionMapper extends BaseMapper<SysOidcSession> {
}
