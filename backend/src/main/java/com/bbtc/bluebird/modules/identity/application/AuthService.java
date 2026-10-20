package com.bbtc.bluebird.modules.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.state.LoginAttemptState;
import com.bbtc.bluebird.modules.audit.application.LoginLogService;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.dto.LoginReq;
import com.bbtc.bluebird.modules.identity.dto.TokenVO;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务（02 §2.3）：账号密码登录 + 防暴破 + 登录/登出日志。
 *
 * <p>provider=OIDC（Casdoor）时账密登录**仅对 admin 开放**（应急兜底），其余用户必须走 SSO。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BOOTSTRAP_ADMIN = "admin";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptState loginAttemptState;
    private final LoginLogService loginLogService;
    private final ConfigService config;

    public TokenVO login(LoginReq req, String ip, String ua) {
        if ("OIDC".equals(config.provider()) && !BOOTSTRAP_ADMIN.equals(req.username())) {
            loginLogService.record(req.username(), false, ip, ua);
            throw new BusinessException(ErrorCode.LOCAL_LOGIN_DISABLED);
        }
        String key = req.username();
        if (loginAttemptState.isLocked(key)) {
            loginLogService.record(req.username(), false, ip, ua);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, req.username())
                .last("limit 1"));
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(req.password(), user.getPassword())) {
            loginAttemptState.recordFailure(key);
            loginLogService.record(req.username(), false, ip, ua);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            loginLogService.record(req.username(), false, ip, ua);
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        loginAttemptState.clear(key);
        TokenVO token = tokenService.issue(user);
        loginLogService.record(req.username(), true, ip, ua);
        return token;
    }

    public TokenVO refresh(String refreshToken) {
        Long userId = tokenService.validateRefresh(refreshToken);
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return tokenService.issue(user);
    }

    /** 登出：撤销 refresh 并记一条 LOGOUT 审计。 */
    public void logout(Long userId, String ip, String ua) {
        if (userId == null) {
            return;
        }
        SysUser user = userMapper.selectById(userId);
        tokenService.revoke(userId);
        if (user != null) {
            String provider = "CASDOOR".equals(user.getExternalSource())
                    ? LoginLogService.PROVIDER_CASDOOR
                    : LoginLogService.PROVIDER_LOCAL;
            loginLogService.record(user.getUsername(), true, ip, ua,
                    provider, LoginLogService.EVENT_LOGOUT, user.getExternalId());
        }
    }
}
