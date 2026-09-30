package com.bbtc.bluebird.modules.identity.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 首 ADMIN 初始化（02 §2.2 / 01 §8.2.1，R25）。
 *
 * <p>空库起步后无 ADMIN 则无法建用户（死锁）。若 {@code BOOTSTRAP_ADMIN_PASSWORD} 非空，
 * 用 BCrypt 计算哈希后插入初始 ADMIN（must_change_password=1）。
 * ADR-010 自动生成后该变量默认非空，种子分支自动执行。
 *
 * <p>此处不设部门；由 {@code DefaultDepartmentRunner}（{@code @Order(20)}，本类为 10）
 * 在同一轮启动中把无部门用户归入系统默认部门。
 */
@Slf4j
@Component
@Order(10)
@Profile("!test")
@RequiredArgsConstructor
public class SeedAdminRunner implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleCode, "ADMIN"));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        if (!StringUtils.hasText(adminPassword)) {
            log.warn("无 ADMIN 且 BOOTSTRAP_ADMIN_PASSWORD 为空，跳过种子；请设置该变量或使用引导接口（01 §8.2.1）。");
            return;
        }
        SysUser exists = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, adminUsername).last("limit 1"));
        if (exists != null) {
            log.warn("用户 {} 已存在但非 ADMIN，跳过种子。", adminUsername);
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(adminUsername);
        admin.setName(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRoleCode("ADMIN");
        admin.setStatus("ACTIVE");
        admin.setMustChangePassword(1);
        userMapper.insert(admin);
        log.info("已创建初始 ADMIN：{}（首次登录须改密）", adminUsername);
    }
}
