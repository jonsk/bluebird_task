package com.bbtc.bluebird.modules.job.application;

import com.bbtc.bluebird.modules.job.domain.JobLog;
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
}
