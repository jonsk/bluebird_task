package com.bbtc.bluebird.modules.job.application;

import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.org.application.OrgSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 组织同步定时任务（PULL 模式，02 §3.3）。仅 mode=PULL 生效。
 *
 * <p>模式与同步时间均可由后台配置（{@code orgsync.mode} / {@code orgsync.cron}）调整：
 * cron 在启动时由 {@code @Scheduled} 绑定，改 cron 需重启；模式每次执行时读取，实时生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrgSyncJob {

    private final OrgSyncService orgSyncService;
    private final ConfigService config;

    @Scheduled(cron = "${app.job.orgsync-cron:0 0 1 * * *}", zone = "Asia/Shanghai")
    public void run() {
        if (!"PULL".equalsIgnoreCase(config.orgSyncMode())) {
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
