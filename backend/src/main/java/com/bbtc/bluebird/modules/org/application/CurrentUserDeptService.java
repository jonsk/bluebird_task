package com.bbtc.bluebird.modules.org.application;

import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前用户部门解析（唯一来源）。
 *
 * <p>禁止使用 {@code UserContext.deptId} 快照：note 的 INTERNAL 判定必须读库里的当前部门。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserDeptService {

    private final SysUserMapper userMapper;

    /** 用户当前部门 id；用户不存在或无部门返回 {@code null}。 */
    public Long deptIdOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = userMapper.selectById(userId);
        return u == null ? null : u.getDeptId();
    }
}
