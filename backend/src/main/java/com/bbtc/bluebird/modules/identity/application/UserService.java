package com.bbtc.bluebird.modules.identity.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.bbtc.bluebird.modules.identity.dto.UserUpdateReq;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户服务（02 §2.6）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;

    public UserDTO me() {
        SysUser u = userMapper.selectById(UserContext.currentUserId());
        if (u == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toDto(u, deptNames(List.of(u)));
    }

    /**
     * 用户分页查询。
     *
     * @param includeSubDept 为 true 时 {@code deptId} 视为**部门子树**（含下级部门）——
     *                       大型组织中间层节点通常没有直属成员，见 {@link DepartmentService#deptAndDescendants}
     */
    public PageResult<UserDTO> page(Long deptId, String keyword, boolean includeSubDept, long page, long size) {
        if (size > 100) size = 100;
        if (page < 1) page = 1;
        Set<Long> deptScope = null;
        if (deptId != null) {
            deptScope = includeSubDept ? departmentService.deptAndDescendants(deptId) : Set.of(deptId);
            if (deptScope.isEmpty()) {
                return PageResult.of(List.of(), 0, page, size);
            }
        }
        final Set<Long> scope = deptScope;
        IPage<SysUser> p = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .in(scope != null, SysUser::getDeptId, scope == null ? Set.of() : scope)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword).or().like(SysUser::getName, keyword))
                        .orderByAsc(SysUser::getId));
        Map<Long, String> names = deptNames(p.getRecords());
        return PageResult.of(p.getRecords().stream().map(u -> toDto(u, names)).toList(),
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
        u.setMobile(trimToNull(cmd.mobile()));
        u.setEmail(trimToNull(cmd.email()));
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
    public void update(Long id, UserUpdateReq req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (StringUtils.hasText(req.name())) {
            u.setName(req.name());
        }
        // 用户必须归属部门（用户反馈 #5）：仅在显式传值且非空时更新，避免误清空
        if (req.deptId() != null) {
            u.setDeptId(req.deptId());
        }
        if (StringUtils.hasText(req.roleCode())) {
            u.setRoleCode(req.roleCode());
        }
        if (StringUtils.hasText(req.status())) {
            u.setStatus(req.status());
        }
        // 手机号 / 邮箱可编辑（用户反馈 #8）；空串表示清空
        boolean touchMobile = req.mobile() != null;
        boolean touchEmail = req.email() != null;
        if (touchMobile) {
            u.setMobile(trimToNull(req.mobile()));
        }
        if (touchEmail) {
            u.setEmail(trimToNull(req.email()));
        }
        userMapper.updateById(u);
        // updateById 默认忽略 null 字段（MyBatis-Plus FieldStrategy.NOT_NULL），因此「清空」写不进去。
        // 显式传值（含空串）时用 UpdateWrapper 强制 set，兑现「空串清空」语义。
        if (touchMobile || touchEmail) {
            LambdaUpdateWrapper<SysUser> uw = Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId, u.getId());
            if (touchMobile) {
                uw.set(SysUser::getMobile, trimToNull(req.mobile()));
            }
            if (touchEmail) {
                uw.set(SysUser::getEmail, trimToNull(req.email()));
            }
            userMapper.update(null, uw);
        }
    }

    @Transactional
    public void disable(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        u.setStatus("DISABLED");
        userMapper.updateById(u);
    }

    @Transactional
    public void resetPassword(Long id, PasswordUpdateReq req) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 授权：仅本人，或 ADMIN / USER_MANAGER 代为重置。
        // 修复：原实现无任何归属校验，任意登录用户可重置他人（新建用户 must_change_password=1 时免原口令）→ 越权接管账号。
        Long me = UserContext.currentUserId();
        boolean self = Objects.equals(me, id);
        if (!self) {
            String role = UserContext.currentRoleCode();
            if (!"ADMIN".equals(role) && !"USER_MANAGER".equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        boolean firstLogin = u.getMustChangePassword() != null && u.getMustChangePassword() == 1;
        // 本人改密：非首登须校验原口令。管理员代为重置：不校验原口令（无从得知），但强制下次登录改密。
        if (self) {
            if (!firstLogin) {
                if (!StringUtils.hasText(req.oldPassword())
                        || !passwordEncoder.matches(req.oldPassword(), u.getPassword())) {
                    throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
                }
            }
            u.setMustChangePassword(0);
        } else {
            u.setMustChangePassword(1);
        }
        u.setPassword(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(u);
        // 改密后失效旧会话
    }

    private Map<Long, String> deptNames(List<SysUser> users) {
        Set<Long> ids = new HashSet<>();
        for (SysUser u : users) {
            if (u.getDeptId() != null) {
                ids.add(u.getDeptId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                            .select(Department::getId, Department::getName).in(Department::getId, ids))
                    .forEach(d -> names.put(d.getId(), d.getName()));
        }
        return names;
    }

    private UserDTO toDto(SysUser u, Map<Long, String> deptNames) {
        return new UserDTO(u.getId(), u.getUsername(), u.getName(), visibleMobile(u),
                u.getEmail(), u.getAvatarUrl(), u.getDeptId(),
                u.getDeptId() == null ? null : deptNames.get(u.getDeptId()),
                u.getRoleCode(), u.getStatus());
    }

    /**
     * 手机号可见性（用户反馈 #8「编辑用户时无法编辑手机号」）：
     * 原实现一律脱敏（138****0000），管理端拿不到原值自然无法编辑。
     * 现改为：ADMIN / USER_MANAGER 返回明文（其职责就是维护用户资料），本人可见自己的明文，其余角色仍脱敏。
     */
    private String visibleMobile(SysUser u) {
        String role = UserContext.currentRoleCode();
        boolean manager = "ADMIN".equals(role) || "USER_MANAGER".equals(role);
        boolean self = u.getId() != null && u.getId().equals(UserContext.currentUserId());
        return manager || self ? u.getMobile() : maskMobile(u.getMobile());
    }

    /** 空串/纯空白 → null（前端以空串表达「清空字段」）。 */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
