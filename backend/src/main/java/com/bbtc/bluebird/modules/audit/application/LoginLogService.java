package com.bbtc.bluebird.modules.audit.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.audit.domain.LoginLog;
import com.bbtc.bluebird.modules.audit.dto.LoginLogVO;
import com.bbtc.bluebird.modules.audit.infrastructure.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * 登录/退出日志记录（02 §6.4）。登录成败、锁定、登出均落库。
 *
 * <p>V5 起区分 {@code provider}（LOCAL/CASDOOR）与 {@code eventType}（LOGIN/LOGOUT），
 * 供后台按来源与事件筛选。
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_CASDOOR = "CASDOOR";
    public static final String EVENT_LOGIN = "LOGIN";
    public static final String EVENT_LOGOUT = "LOGOUT";

    private final LoginLogMapper mapper;

    /** 本地账密登录（兼容旧调用）。 */
    public void record(String username, boolean success, String ip, String ua) {
        record(username, success, ip, ua, PROVIDER_LOCAL, EVENT_LOGIN, null);
    }

    public void record(String username, boolean success, String ip, String ua,
                       String provider, String eventType, String externalId) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setSuccess(success ? 1 : 0);
        log.setIp(ip);
        log.setUserAgent(ua);
        log.setProvider(provider);
        log.setEventType(eventType);
        log.setExternalId(externalId);
        log.setCreatedAt(Instant.now());
        mapper.insert(log);
    }

    public PageResult<LoginLogVO> page(long page, long size) {
        return page(page, size, null, null);
    }

    public PageResult<LoginLogVO> page(long page, long size, String provider, String eventType) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        IPage<LoginLog> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<LoginLog>lambdaQuery()
                        .eq(StringUtils.hasText(provider), LoginLog::getProvider, provider)
                        .eq(StringUtils.hasText(eventType), LoginLog::getEventType, eventType)
                        .orderByDesc(LoginLog::getId));
        return PageResult.of(p.getRecords().stream()
                        .map(l -> new LoginLogVO(l.getId(), l.getUsername(),
                                l.getSuccess() != null && l.getSuccess() == 1, l.getIp(), l.getUserAgent(),
                                l.getProvider(), l.getEventType(), l.getExternalId(), l.getCreatedAt()))
                        .toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }
}
