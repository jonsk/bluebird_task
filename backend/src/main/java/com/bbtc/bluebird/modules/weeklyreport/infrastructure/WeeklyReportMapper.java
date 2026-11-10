package com.bbtc.bluebird.modules.weeklyreport.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReport;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeeklyReportMapper extends BaseMapper<WeeklyReport> {
}
