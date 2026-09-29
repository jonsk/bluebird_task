package com.bbtc.bluebird.modules.audit.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.audit.domain.OperateLog;
import com.bbtc.bluebird.modules.audit.dto.OperateLogVO;
import com.bbtc.bluebird.modules.audit.infrastructure.OperateLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 操作日志（02 §6.6/§6.7）。关键事件同步落库；本期统一同步写（本地缓冲双写可二期）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperateLogService {

    private final OperateLogMapper mapper;

    public record Entry(String module, String action, String uri, String method, String reqParams,
                        Long userId, String ip, String userAgent, long duration, boolean success, String msg) {
    }

    public void record(Entry entry) {
        try {
            OperateLog log = new OperateLog();
            log.setModule(entry.module());
            log.setAction(entry.action());
            log.setUri(entry.uri());
            log.setMethod(entry.method());
            log.setReqParams(entry.reqParams());
            log.setUserId(entry.userId());
            log.setIp(entry.ip());
            log.setUserAgent(entry.userAgent());
            log.setDuration(entry.duration());
            log.setStatus(entry.success() ? 1 : 0);
            log.setMsg(entry.msg());
            log.setCreatedAt(Instant.now());
            mapper.insert(log);
        } catch (Exception e) {
            // 审计不应阻塞主流程
            log.warn("操作日志写入失败：{}", e.getMessage());
        }
    }

    public PageResult<OperateLogVO> page(long page, long size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        IPage<OperateLog> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<OperateLog>lambdaQuery().orderByDesc(OperateLog::getId));
        return PageResult.of(p.getRecords().stream().map(this::toVo).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    private OperateLogVO toVo(OperateLog o) {
        return new OperateLogVO(o.getId(), o.getModule(), o.getAction(), o.getUri(), o.getMethod(),
                o.getUserId(), o.getIp(), o.getUserAgent(), o.getDuration(),
                o.getStatus() != null && o.getStatus() == 1 ? "SUCCESS" : "FAILURE",
                o.getMsg(), o.getCreatedAt());
    }
}
