package com.bbtc.bluebird.modules.job.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.job.domain.JobLog;
import com.bbtc.bluebird.modules.job.dto.JobLogVO;
import com.bbtc.bluebird.modules.job.infrastructure.JobLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 调度任务日志服务（每次执行写 sys_job_log）。 */
@Service
@RequiredArgsConstructor
public class JobLogService {

    private final JobLogMapper mapper;

    public void record(String jobName, long startedAtMillis, boolean success, String msg) {
        JobLog log = new JobLog();
        log.setJobName(jobName);
        log.setStatus(success ? 1 : 0);
        log.setDuration(System.currentTimeMillis() - startedAtMillis);
        log.setMsg(msg);
        log.setStartedAt(Instant.ofEpochMilli(startedAtMillis));
        log.setFinishedAt(Instant.now());
        mapper.insert(log);
    }

    public PageResult<JobLogVO> page(long page, long size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        IPage<JobLog> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<JobLog>lambdaQuery().orderByDesc(JobLog::getId));
        return PageResult.of(p.getRecords().stream()
                        .map(l -> new JobLogVO(l.getId(), l.getJobName(),
                                l.getStatus() != null && l.getStatus() == 1, l.getDuration(), l.getMsg(),
                                l.getStartedAt(), l.getFinishedAt()))
                        .toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }
}
