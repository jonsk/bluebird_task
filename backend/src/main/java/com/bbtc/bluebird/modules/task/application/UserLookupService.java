package com.bbtc.bluebird.modules.task.application;

import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.task.dto.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 用户摘要解析（id→name），供任务视图组装。 */
@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final SysUserMapper userMapper;

    public Map<Long, UserBrief> briefs(Collection<Long> ids) {
        Map<Long, UserBrief> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<SysUser> users = userMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<SysUser>lambdaQuery().select(SysUser::getId, SysUser::getName).in(SysUser::getId, ids));
        for (SysUser u : users) {
            map.put(u.getId(), new UserBrief(u.getId(), u.getName()));
        }
        return map;
    }

    public UserBrief brief(Long id) {
        if (id == null) {
            return null;
        }
        SysUser u = userMapper.selectById(id);
        return u == null ? null : new UserBrief(u.getId(), u.getName());
    }

    public List<UserBrief> briefList(Collection<Long> ids) {
        List<UserBrief> list = new ArrayList<>();
        Map<Long, UserBrief> map = briefs(ids);
        if (ids != null) {
            for (Long id : ids) {
                UserBrief b = map.get(id);
                if (b != null) {
                    list.add(b);
                }
            }
        }
        return list;
    }
}
