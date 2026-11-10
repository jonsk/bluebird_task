package com.bbtc.bluebird.modules.weeklyreport.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportPreference;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeeklyReportPreferenceMapper extends BaseMapper<WeeklyReportPreference> {
}
