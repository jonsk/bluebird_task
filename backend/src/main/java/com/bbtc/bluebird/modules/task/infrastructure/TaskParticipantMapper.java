package com.bbtc.bluebird.modules.task.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskParticipantMapper extends BaseMapper<TaskParticipant> {
}
