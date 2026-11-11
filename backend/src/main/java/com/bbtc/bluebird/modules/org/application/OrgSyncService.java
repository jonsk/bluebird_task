package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.org.dto.OrgPullData;
import com.bbtc.bluebird.modules.org.dto.OrgSyncState;
import com.bbtc.bluebird.modules.org.dto.SyncStatusVO;
import com.bbtc.bluebird.modules.org.infrastructure.adapter.OrgSyncAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * PULL 模式同步编排（02 §3.3）：加锁 → 解析适配器 → 拉取 → 交给 {@link OrgSyncApplier} 落库 → 记日志。
 *
 * <p>职责边界：本类只做编排与统计汇总，不直接写任何表。落库在 {@link OrgSyncApplier}（独立 Bean，
 * 保证 {@code @Transactional} 走代理）；PUSH 在 {@link OrgPushService}；SCIM 在 {@link ScimSyncService}。
 * 三者共用的查找/关系原语在 {@link OrgDirectorySupport}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgSyncService {

    private final ConfigService config;
    private final OrgSyncLogService syncLogService;
    private final OrgSyncApplier applier;
    private final List<OrgSyncAdapter> adapters;

    /** 单实例进程内锁（ADR-016）：重复触发直接拒绝，不排队。 */
    private final ReentrantLock lock = new ReentrantLock();

    public String mode() {
        return config.orgSyncMode();
    }

    /** PULL 模式手动/定时同步。 */
    public String sync() {
        String mode = mode();
        if (!"PULL".equalsIgnoreCase(mode)) {
            return "当前模式 " + mode + " 不支持 PULL 同步";
        }
        if (!lock.tryLock()) {
            throw new BusinessException(ErrorCode.SYNC_IN_PROGRESS);
        }
        OrgSyncState.setRunning(true);
        String source = StringUtils.hasText(config.orgSyncAdapter())
                ? config.orgSyncAdapter().toUpperCase() : "PULL";
        Long logId = syncLogService.start(source, "FULL");
        try {
            OrgSyncAdapter adapter = resolveAdapter();
            if (adapter == null) {
                String msg = "PULL 同步完成（未找到适配器：" + config.orgSyncAdapter() + "，空操作）";
                OrgSyncState.record(Instant.now(), msg);
                syncLogService.finish(logId, OrgSyncLogService.STATUS_SUCCESS, 0, 0, 0, 0, null);
                return msg;
            }
            OrgPullData data = adapter.pull();
            OrgSyncApplier.Result r = applier.apply(data);
            String result = String.format(
                    "PULL 同步完成：部门 %d（新增 %d / 更新 %d），用户 %d（新增 %d / 更新 %d / 跳过 %d）",
                    r.deptTotal(), r.deptInserted(), r.deptUpdated(),
                    r.userTotal(), r.userInserted(), r.userUpdated(), r.userSkipped());
            OrgSyncState.record(Instant.now(), result);
            syncLogService.finish(logId, OrgSyncLogService.STATUS_SUCCESS, r.deptTotal() + r.userTotal(),
                    r.deptInserted() + r.userInserted(), r.deptUpdated() + r.userUpdated(), r.userSkipped(), null);
            log.info(result);
            return result;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            OrgSyncState.record(Instant.now(), "同步失败：" + msg);
            syncLogService.finish(logId, OrgSyncLogService.STATUS_FAILURE, 0, 0, 0, 0, msg);
            log.warn("PULL 同步失败：{}", msg);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步失败：" + msg);
        } finally {
            OrgSyncState.setRunning(false);
            lock.unlock();
        }
    }

    public SyncStatusVO status() {
        return new SyncStatusVO(OrgSyncState.running(), OrgSyncState.lastSyncAt(), OrgSyncState.lastResult(), mode());
    }

    private OrgSyncAdapter resolveAdapter() {
        String wanted = config.orgSyncAdapter();
        if (!StringUtils.hasText(wanted)) {
            return null;
        }
        return adapters.stream()
                .filter(a -> a.source().equalsIgnoreCase(wanted))
                .findFirst()
                .orElse(null);
    }
}
