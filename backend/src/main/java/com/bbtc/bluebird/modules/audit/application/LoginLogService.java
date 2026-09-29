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

    public PageResult<LoginLogVO> page(long page, long size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        IPage<LoginLog> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<LoginLog>lambdaQuery().orderByDesc(LoginLog::getId));
        return PageResult.of(p.getRecords().stream()
                        .map(l -> new LoginLogVO(l.getId(), l.getUsername(),
                                l.getSuccess() != null && l.getSuccess() == 1, l.getIp(), l.getUserAgent(),
                                l.getCreatedAt()))
                        .toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }
}
