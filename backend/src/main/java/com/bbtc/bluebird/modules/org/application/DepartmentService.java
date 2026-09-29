package com.bbtc.bluebird.modules.org.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.dto.DeptCreateReq;
import com.bbtc.bluebird.modules.org.dto.DeptUpdateReq;
import com.bbtc.bluebird.modules.org.dto.DepartmentVO;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部门服务（02 §3.4/§3.5）。含「下属集合」解析（ADR-012，去缓存直算）。
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    public List<DepartmentVO> tree() {
        List<Department> all = departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                .orderByAsc(Department::getSort).orderByAsc(Department::getId));
        Map<Long, DepartmentVO> map = new HashMap<>();
        for (Department d : all) {
            map.put(d.getId(), new DepartmentVO(d.getId(), d.getName(), d.getParentId(),
                    d.getLeaderUserId(), d.getSort()));
        }
        List<DepartmentVO> roots = new ArrayList<>();
        for (Department d : all) {
            DepartmentVO node = map.get(d.getId());
            DepartmentVO parent = d.getParentId() == null ? null : map.get(d.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots;
    }

    @Transactional
    public Long create(DeptCreateReq req) {
        Department d = new Department();
        d.setName(req.name());
        d.setParentId(req.parentId());
        d.setLeaderUserId(req.leaderId());
        d.setSort(0);
        d.setCreatedAt(Instant.now());
        departmentMapper.insert(d);
        return d.getId();
    }

    @Transactional
    public void update(Long id, DeptUpdateReq req) {
        Department d = departmentMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        if (StringUtils.hasText(req.name())) {
            d.setName(req.name());
        }
        if (req.parentId() != null) {
            if (req.parentId().equals(id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "父部门不能是自身");
            }
            d.setParentId(req.parentId());
        }
        // leaderId 显式传 null 表示清空 —— 以字段存在与否无法区分，简化为非空才更新且标注审计
        if (req.leaderId() != null) {
            d.setLeaderUserId(req.leaderId());
        }
        d.setUpdatedAt(Instant.now());
        departmentMapper.updateById(d);
    }

    @Transactional
    public void delete(Long id) {
        Long children = departmentMapper.selectCount(
                Wrappers.<Department>lambdaQuery().eq(Department::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在子部门，无法删除");
        }
        Long members = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getDeptId, id));
        if (members != null && members > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部门下存在成员，无法删除");
        }
        departmentMapper.deleteById(id);
    }

    /** 部门 id → 名称。 */
    public Map<Long, String> nameMap() {
        Map<Long, String> m = new HashMap<>();
        for (Department d : departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                .select(Department::getId, Department::getName))) {
            m.put(d.getId(), d.getName());
        }
        return m;
    }

    /**
     * 下属集合（ADR-012）：该用户为负责人的全部部门及其递归子部门内的所有用户，排除本人。
     */
    public Set<Long> subordinates(Long leaderUserId) {
        if (leaderUserId == null) {
            return Set.of();
        }
        List<Department> all = departmentMapper.selectList(Wrappers.<Department>lambdaQuery()
                .select(Department::getId, Department::getParentId, Department::getLeaderUserId));
        Set<Long> roots = new HashSet<>();
        for (Department d : all) {
            if (leaderUserId.equals(d.getLeaderUserId())) {
                roots.add(d.getId());
            }
        }
        if (roots.isEmpty()) {
            return Set.of();
        }
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (Department d : all) {
            if (d.getParentId() != null) {
                childrenOf.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d.getId());
            }
        }
        Set<Long> deptIds = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            if (deptIds.add(cur)) {
                queue.addAll(childrenOf.getOrDefault(cur, List.of()));
            }
        }
        if (deptIds.isEmpty()) {
            return Set.of();
        }
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getDeptId)
                .in(SysUser::getDeptId, deptIds));
        Set<Long> result = new HashSet<>();
        for (SysUser u : users) {
            if (!u.getId().equals(leaderUserId)) {
                result.add(u.getId());
            }
        }
        return result;
    }
}
