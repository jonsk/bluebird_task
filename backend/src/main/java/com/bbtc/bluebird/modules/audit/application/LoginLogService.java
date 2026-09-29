package com.bbtc.bluebird.modules.audit.application;

import com.bbtc.bluebird.modules.audit.domain.LoginLog;
import com.bbtc.bluebird.modules.audit.infrastructure.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 登录日志记录（02 §6.4）。登录成败/锁定为关键审计，同步落库。
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogMapper mapper;

    public void record(String username, boolean success, String ip, String ua) {
        LoginLog log = new LoginLog();
        log.setUsername(username);
        log.setSuccess(success ? 1 : 0);
        log.setIp(ip);
        log.setUserAgent(ua);
        log.setCreatedAt(Instant.now());
        mapper.insert(log);
    }
}
