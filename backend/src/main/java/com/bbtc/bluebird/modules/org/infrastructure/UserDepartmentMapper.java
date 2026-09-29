package com.bbtc.bluebird.modules.org.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserDepartmentMapper extends BaseMapper<UserDepartment> {
}
