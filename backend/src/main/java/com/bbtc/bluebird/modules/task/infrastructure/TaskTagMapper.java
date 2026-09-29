package com.bbtc.bluebird.modules.task.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.task.domain.TaskTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskTagMapper extends BaseMapper<TaskTag> {
}
