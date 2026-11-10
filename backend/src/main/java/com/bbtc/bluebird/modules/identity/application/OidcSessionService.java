package com.bbtc.bluebird.modules.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.identity.domain.SysOidcSession;
import com.bbtc.bluebird.modules.identity.infrastructure.SysOidcSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Casdoor 会话 id_token 的读写（V14）。
 *
 * <p>写入失败不影响登录主流程：拿不到 id_token 只是登出时退回「确认退出」页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcSessionService {

    private final SysOidcSessionMapper mapper;

    /** 记录最近一次 OIDC 登录的 id_token；为空时忽略。 */
    public void saveIdToken(Long userId, String idToken) {
        if (userId == null || idToken == null || idToken.isBlank()) {
            return;
        }
        try {
            SysOidcSession existing = findByUserId(userId);
            if (existing == null) {
                SysOidcSession row = new SysOidcSession();
                row.setUserId(userId);
                row.setIdToken(idToken);
                row.setUpdatedAt(Instant.now());
                mapper.insert(row);
            } else {
                existing.setIdToken(idToken);
                existing.setUpdatedAt(Instant.now());
                mapper.updateById(existing);
            }
        } catch (RuntimeException e) {
            log.warn("保存 OIDC id_token 失败（不影响登录）：userId={}, error={}", userId, e.getMessage());
        }
    }

    /** 取最近一次 id_token；没有则返回 null。 */
    public String findIdToken(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            SysOidcSession row = findByUserId(userId);
            return row == null ? null : row.getIdToken();
        } catch (RuntimeException e) {
            log.warn("读取 OIDC id_token 失败：userId={}, error={}", userId, e.getMessage());
            return null;
        }
    }

    /** 登出后清理，避免长期残留上游凭证。 */
    public void clear(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            mapper.delete(Wrappers.<SysOidcSession>lambdaQuery().eq(SysOidcSession::getUserId, userId));
        } catch (RuntimeException e) {
            log.warn("清理 OIDC id_token 失败：userId={}, error={}", userId, e.getMessage());
        }
    }

    private SysOidcSession findByUserId(Long userId) {
        return mapper.selectOne(Wrappers.<SysOidcSession>lambdaQuery()
                .eq(SysOidcSession::getUserId, userId)
                .last("limit 1"));
    }
}
