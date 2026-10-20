package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.org.domain.OrgSyncLog;
import com.bbtc.bluebird.modules.org.dto.OrgSyncLogVO;
import com.bbtc.bluebird.modules.org.infrastructure.OrgSyncLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 同步日志读写（org_sync_log）。 */
@Service
@RequiredArgsConstructor
public class OrgSyncLogService {

    /** 状态取值（与 `openapi.yaml` 的 OrgSyncLogVO.status 枚举一致）。 */
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    private final OrgSyncLogMapper mapper;

    /** 开始一次同步，返回日志 id。 */
    public Long start(String source, String syncType) {
        OrgSyncLog log = new OrgSyncLog();
        log.setSource(source);
        log.setSyncType(syncType);
        log.setStatus(STATUS_RUNNING);
        log.setStartedAt(Instant.now());
        mapper.insert(log);
        return log.getId();
    }

    /** 结束一次同步。 */
    public void finish(Long id, String status, int total, int inserted, int updated, int skipped, String error) {
        if (id == null) {
            return;
        }
        OrgSyncLog log = mapper.selectById(id);
        if (log == null) {
            return;
        }
        log.setStatus(status);
        log.setFinishedAt(Instant.now());
        log.setTotal(total);
        log.setInserted(inserted);
        log.setUpdated(updated);
        log.setSkipped(skipped);
        log.setErrorMessage(error);
        mapper.updateById(log);
    }

    public PageResult<OrgSyncLogVO> page(long page, long size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        IPage<OrgSyncLog> p = mapper.selectPage(new Page<>(page, size),
                Wrappers.<OrgSyncLog>lambdaQuery().orderByDesc(OrgSyncLog::getId));
        return PageResult.of(p.getRecords().stream()
                        .map(l -> new OrgSyncLogVO(l.getId(), l.getSource(), l.getSyncType(), l.getStatus(),
                                l.getStartedAt(), l.getFinishedAt(), l.getTotal(), l.getInserted(),
                                l.getUpdated(), l.getSkipped(), l.getErrorMessage()))
                        .toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 最近一条同步记录（概览页用）。 */
    public OrgSyncLogVO latest() {
        OrgSyncLog l = mapper.selectOne(Wrappers.<OrgSyncLog>lambdaQuery()
                .orderByDesc(OrgSyncLog::getId).last("limit 1"));
        if (l == null) {
            return null;
        }
        return new OrgSyncLogVO(l.getId(), l.getSource(), l.getSyncType(), l.getStatus(),
                l.getStartedAt(), l.getFinishedAt(), l.getTotal(), l.getInserted(),
                l.getUpdated(), l.getSkipped(), l.getErrorMessage());
    }
}
