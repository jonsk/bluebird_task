package com.bbtc.bluebird.modules.identity.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首 ADMIN 一次性引导兜底（01 §8.2.1，R25）。仅在系统中尚无 ADMIN 时可用。
 */
@Slf4j
@Tag(name = "auth")
@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
public class BootstrapController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public record BootstrapReq(String username, @NotBlank String password) {
    }

    @Operation(summary = "一次性引导创建首 ADMIN（仅空库可用）")
    @Transactional
    @PostMapping("/admin")
    public ApiResult<Long> bootstrap(@Valid @RequestBody BootstrapReq req) {
        Long adminCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleCode, "ADMIN"));
        if (adminCount != null && adminCount > 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统已存在 ADMIN，引导接口关闭");
        }
        String username = StringUtils.hasText(req.username()) ? req.username() : "admin";
        SysUser exists = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).last("limit 1"));
        if (exists != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        SysUser admin = new SysUser();
        admin.setUsername(username);
        admin.setName(username);
        admin.setPassword(passwordEncoder.encode(req.password()));
        admin.setRoleCode("ADMIN");
        admin.setStatus("ACTIVE");
        admin.setMustChangePassword(1);
        userMapper.insert(admin);
        log.info("引导创建首 ADMIN：{}", username);
        return ApiResult.ok(admin.getId());
    }
}
