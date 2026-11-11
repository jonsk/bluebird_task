package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.config.application.ConfigService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.domain.UserDepartment;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.org.infrastructure.UserDepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组织同步的共用落库原语：按外部标识查部门/用户、建人员-部门关系、模式校验。
 *
 * <p>从 {@link OrgSyncService} 拆出：PUSH、SCIM、PULL 三条路径都要用同一套"先查外部标识再 upsert"
 * 的语义，散在两个服务里各写一份迟早会走偏（例如一边按 corpId、一边按 externalId）。
 */
@Component
@RequiredArgsConstructor
public class OrgDirectorySupport {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final UserDepartmentMapper userDepartmentMapper;
    private final ConfigService config;

    /** 校验当前组织同步模式，不匹配直接拒绝（PUSH/SCIM 对外端点用）。 */
    public void requireMode(String expected) {
        String mode = config.orgSyncMode();
        if (!expected.equalsIgnoreCase(mode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "组织同步模式未启用：" + expected);
        }
    }

    public Department findDeptByCorpId(String corpId) {
        return departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                .eq(Department::getCorpId, corpId).last("limit 1"));
    }

    public SysUser findUserByExternalId(String externalId) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getExternalId, externalId).last("limit 1"));
    }

    public SysUser findUserByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username).last("limit 1"));
    }

    /** 关系表幂等写入：已存在则跳过，primary 只影响首次插入。 */
    public void linkUserDept(Long userId, Long deptId, boolean primary) {
        Long cnt = userDepartmentMapper.selectCount(Wrappers.<UserDepartment>lambdaQuery()
                .eq(UserDepartment::getUserId, userId).eq(UserDepartment::getDeptId, deptId));
        if (cnt != null && cnt > 0) {
            return;
        }
        UserDepartment ud = new UserDepartment();
        ud.setUserId(userId);
        ud.setDeptId(deptId);
        ud.setIsPrimary(primary ? 1 : 0);
        userDepartmentMapper.insert(ud);
    }
}
