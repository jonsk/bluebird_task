package com.bbtc.bluebird.modules.weeklyreport.job;

import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 周报自动生成任务。
 *
 * <p>管理员可配置「星期 + 时间」，因此不能用固定 cron 绑定配置值（改时间要重启）。
 * 这里每分钟唤醒一次做轻量比对，命中配置时刻才执行；幂等由周报唯一索引保证，
 * 同一分钟内重复触发也只会有一条周报。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportAutoJob {

    private final WeeklyReportService service;
    private final ConfigService config;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Shanghai")
    public void run() {
        if (!config.getBool("weeklyreport", "auto-enabled", true)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String day = now.getDayOfWeek().name().substring(0, 3);
        String configuredDay = config.get("weeklyreport", "auto-day", "FRI").trim().toUpperCase();
        String configuredTime = config.get("weeklyreport", "auto-time", "18:00").trim();
        if (!day.equals(configuredDay) || !timeMatches(now, configuredTime)) {
            return;
        }
        try {
            int created = service.autoGenerate();
            if (created > 0) {
                log.info("周报自动生成任务完成：新建 {} 份草稿", created);
            }
        } catch (Exception e) {
            log.warn("周报自动生成任务失败：{}", e.getMessage());
        }
    }

    /** 兼容 18:00 / 8:00 两种写法；非法值直接视为不匹配。 */
    private boolean timeMatches(LocalDateTime now, String configuredTime) {
        try {
            LocalTime target = LocalTime.parse(configuredTime);
            return now.getHour() == target.getHour() && now.getMinute() == target.getMinute();
        } catch (Exception e) {
            return false;
        }
    }
}
