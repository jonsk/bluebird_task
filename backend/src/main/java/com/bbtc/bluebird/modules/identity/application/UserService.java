package com.bbtc.bluebird.modules.identity.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.dto.CreateUserCmd;
import com.bbtc.bluebird.modules.identity.dto.PasswordUpdateReq;
import com.bbtc.bluebird.modules.identity.dto.UserDTO;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户服务（02 §2.6）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserDTO me() {
        SysUser u = userMapper.selectById(UserContext.currentUserId());
        if (u == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toDto(u);
    }

    public PageResult<UserDTO> page(Long deptId, String keyword, long page, long size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        IPage<SysUser> p = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(deptId != null, SysUser::getDeptId, deptId)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword).or().like(SysUser::getName, keyword))
                        .orderByAsc(SysUser::getId));
        return PageResult.of(p.getRecords().stream().map(this::toDto).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Transactional
    public Long create(CreateUserCmd cmd) {
        Long exists = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, cmd.username()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(cmd.username());
        u.setName(cmd.name());
        u.setDeptId(cmd.deptId());
        u.setRoleCode(StringUtils.hasText(cmd.roleCode()) ? cmd.roleCode() : "COMMON");
        u.setStatus("ACTIVE");
        if (StringUtils.hasText(cmd.password())) {
            u.setPassword(passwordEncoder.encode(cmd.password()));
            u.setMustChangePassword(1);
        } else {
            u.setMustChangePassword(1);
        }
        userMapper.insert(u);
        return u.getId();
    }

    @Transactional
    public void resetPassword(Long id, PasswordUpdateReq req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        boolean firstLogin = u.getMustChangePassword() != null && u.getMustChangePassword() == 1;
        if (!firstLogin) {
            if (!StringUtils.hasText(req.oldPassword())
                    || !passwordEncoder.matches(req.oldPassword(), u.getPassword())) {
                throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
            }
        }
        u.setPassword(passwordEncoder.encode(req.newPassword()));
        u.setMustChangePassword(0);
        userMapper.updateById(u);
        // 改密后失效旧会话
    }

    private UserDTO toDto(SysUser u) {
        return new UserDTO(u.getId(), u.getUsername(), u.getName(), maskMobile(u.getMobile()),
                u.getEmail(), u.getAvatarUrl(), u.getDeptId(), u.getRoleCode(), u.getStatus());
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
