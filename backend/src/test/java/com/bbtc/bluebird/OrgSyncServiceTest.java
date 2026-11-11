package com.bbtc.bluebird;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.org.application.OrgSyncApplier;
import com.bbtc.bluebird.modules.org.application.OrgSyncLogService;
import com.bbtc.bluebird.modules.org.application.OrgSyncService;
import com.bbtc.bluebird.modules.org.dto.OrgPullData;
import com.bbtc.bluebird.modules.org.dto.OrgSyncState;
import com.bbtc.bluebird.modules.org.infrastructure.adapter.OrgSyncAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PULL 同步编排（拆分后 {@link OrgSyncService} 只剩这条路径）：模式拦截、适配器缺失、
 * 失败记账与锁复位、成功统计汇总。
 *
 * <p>PUSH / SCIM 的落库分支见 {@link OrgPushServiceTest}、{@link ScimSyncServiceTest}。
 */
class OrgSyncServiceTest {

    private ConfigService config;
    private OrgSyncLogService syncLogService;
    private OrgSyncApplier applier;

    @BeforeEach
    void setUp() {
        config = mock(ConfigService.class);
        syncLogService = mock(OrgSyncLogService.class);
        applier = mock(OrgSyncApplier.class);
    }

    @AfterEach
    void tearDown() {
        OrgSyncState.setRunning(false);
    }

    @Test
    void 非PULL模式直接返回提示且不启动日志() {
        when(config.orgSyncMode()).thenReturn("PUSH");

        String result = service(List.of()).sync();

        assertTrue(result.contains("不支持 PULL 同步"), result);
        verify(syncLogService, never()).start(any(), any());
    }

    @Test
    void 找不到适配器按空操作成功记录() {
        when(config.orgSyncMode()).thenReturn("PULL");
        when(config.orgSyncAdapter()).thenReturn("nosuch");
        when(syncLogService.start(any(), any())).thenReturn(42L);

        String result = service(List.of()).sync();

        assertTrue(result.contains("未找到适配器"), result);
        verify(syncLogService).finish(42L, OrgSyncLogService.STATUS_SUCCESS, 0, 0, 0, 0, null);
        assertFalse(OrgSyncState.running(), "同步结束必须复位运行标记");
        assertTrue(OrgSyncState.lastResult().contains("未找到适配器"));
    }

    @Test
    void 适配器异常时记录失败并包成业务异常() {
        when(config.orgSyncMode()).thenReturn("PULL");
        when(config.orgSyncAdapter()).thenReturn("casdoor");
        when(syncLogService.start(any(), any())).thenReturn(43L);
        OrgSyncAdapter broken = adapter("CASDOOR", () -> {
            throw new IllegalStateException("连接超时");
        });

        BusinessException e = assertThrows(BusinessException.class, () -> service(List.of(broken)).sync());

        assertEquals(ErrorCode.SYSTEM_ERROR, e.getCode());
        assertTrue(e.getMessage().contains("连接超时"), e.getMessage());
        verify(syncLogService).finish(43L, OrgSyncLogService.STATUS_FAILURE, 0, 0, 0, 0, "连接超时");
        assertFalse(OrgSyncState.running());
    }

    @Test
    void 业务异常原样抛出不被二次包装() {
        when(config.orgSyncMode()).thenReturn("PULL");
        when(config.orgSyncAdapter()).thenReturn("casdoor");
        when(syncLogService.start(any(), any())).thenReturn(46L);
        OrgSyncAdapter broken = adapter("CASDOOR", () -> {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "外部数据不合法");
        });

        BusinessException e = assertThrows(BusinessException.class, () -> service(List.of(broken)).sync());

        assertEquals(ErrorCode.PARAM_ERROR, e.getCode());
        assertEquals("外部数据不合法", e.getMessage());
    }

    @Test
    void 成功时把统计汇总写进日志() {
        when(config.orgSyncMode()).thenReturn("PULL");
        when(config.orgSyncAdapter()).thenReturn("casdoor");
        when(syncLogService.start(any(), any())).thenReturn(44L);
        OrgPullData data = new OrgPullData("CASDOOR", List.of(), List.of());
        when(applier.apply(data)).thenReturn(new OrgSyncApplier.Result(1, 1, 0, 2, 1, 1, 0));

        String result = service(List.of(adapter("CASDOOR", () -> data))).sync();

        assertTrue(result.contains("部门 1（新增 1 / 更新 0）"), result);
        verify(syncLogService).finish(eq(44L), eq(OrgSyncLogService.STATUS_SUCCESS),
                eq(3), eq(2), eq(1), eq(0), isNull());
    }

    @Test
    void 状态查询带出当前模式() {
        when(config.orgSyncMode()).thenReturn("SCIM");

        var vo = service(List.of()).status();

        assertEquals("SCIM", vo.mode());
        assertFalse(vo.running());
    }

    private OrgSyncService service(List<OrgSyncAdapter> adapters) {
        return new OrgSyncService(config, syncLogService, applier, adapters);
    }

    private OrgSyncAdapter adapter(String source, PullBody body) {
        return new OrgSyncAdapter() {
            @Override
            public String source() {
                return source;
            }

            @Override
            public OrgPullData pull() {
                return body.pull();
            }
        };
    }

    @FunctionalInterface
    private interface PullBody {
        OrgPullData pull();
    }
}
