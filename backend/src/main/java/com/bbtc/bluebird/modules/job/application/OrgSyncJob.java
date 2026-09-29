package com.bbtc.bluebird.modules.job.application;

import com.bbtc.bluebird.modules.org.application.OrgSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 组织同步定时任务（PULL 模式，每日 01:00，02 §3.3）。仅 mode=PULL 生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgSyncJob {

    private final OrgSyncService orgSyncService;

    @Value("${app.orgsync.mode:NONE}")
    private String mode;

    @Scheduled(cron = "${app.job.orgsync-cron:0 0 1 * * *}", zone = "Asia/Shanghai")
    public void run() {
        if (!"PULL".equalsIgnoreCase(mode)) {
            return;
        }
        try {
            String result = orgSyncService.sync();
            log.info("组织同步任务完成：{}", result);
        } catch (Exception e) {
            log.warn("组织同步任务失败：{}", e.getMessage());
        }
    }
}
