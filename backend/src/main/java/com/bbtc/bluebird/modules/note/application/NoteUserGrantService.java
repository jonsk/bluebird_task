package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.NoteUserGrant;
import com.bbtc.bluebird.modules.note.dto.NoteUserGrantReq;
import com.bbtc.bluebird.modules.note.dto.NoteUserGrantVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteUserGrantMapper;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户级全量授权（owner 把所有工作日志权限授予指定用户）。
 *
 * <p>三种发起模式：OWNER（owner 主动，自动生效）、SUPERIOR（上级发起、下属确认）、ADMIN（代授权，自动生效）。
 */
@Service
@RequiredArgsConstructor
public class NoteUserGrantService {

    private static final int MAX_PENDING_PER_GRANTOR = 5;

    private final NoteUserGrantMapper grantMapper;
    private final SysUserMapper userMapper;
    private final DepartmentService departmentService;

    /* ==================== 发起 ==================== */

    @Transactional
    public NoteUserGrantVO create(NoteUserGrantReq req) {
        Long me = UserContext.currentUserId();
        String mode = req.mode();
        if (!StringUtils.hasText(mode)) {
            throw modeInvalid("mode 不能为空");
        }
        String role = StringUtils.hasText(req.role()) ? req.role() : NoteUserGrant.VIEWER;
        if (!NoteUserGrant.VIEWER.equals(role) && !NoteUserGrant.EDITOR.equals(role)) {
            throw modeInvalid("role 非法");
        }

        Long grantorId;
        Long granteeId;
        String status;
        Long approvedBy;
        Instant approvedAt = null;

        switch (mode) {
            case NoteUserGrant.MODE_OWNER -> {
                if (req.grantorId() != null) {
                    throw modeInvalid("OWNER 模式不允许指定 grantorId");
                }
                if (req.granteeId() == null) {
                    throw modeInvalid("OWNER 模式必须指定 granteeId");
                }
                if (me.equals(req.granteeId())) {
                    throw new BusinessException(ErrorCode.NOTE_GRANT_SELF);
                }
                grantorId = me;
                granteeId = req.granteeId();
                status = NoteUserGrant.APPROVED;
                approvedBy = me;
                approvedAt = Instant.now();
            }
            case NoteUserGrant.MODE_SUPERIOR -> {
                if (req.grantorId() == null) {
                    throw modeInvalid("SUPERIOR 模式必须指定 grantorId");
                }
                if (req.granteeId() != null) {
                    throw modeInvalid("SUPERIOR 模式不接受 granteeId（服务端推导为发起人）");
                }
                if (me.equals(req.grantorId())) {
                    throw new BusinessException(ErrorCode.NOTE_GRANT_SELF);
                }
                if (!departmentService.subordinates(me).contains(req.grantorId())) {
                    throw new HttpStatusException(HttpStatus.FORBIDDEN, ErrorCode.NOTE_GRANT_NOT_SUBORDINATE);
                }
                grantorId = req.grantorId();
                granteeId = me;
                status = NoteUserGrant.PENDING;
                approvedBy = null;
            }
            case NoteUserGrant.MODE_ADMIN -> {
                if (!UserContext.isAdmin()) {
                    throw HttpStatusException.forbidden();
                }
                if (req.grantorId() == null || req.granteeId() == null) {
                    throw modeInvalid("ADMIN 模式必须指定 grantorId 与 granteeId");
                }
                if (req.grantorId().equals(req.granteeId())) {
                    throw new BusinessException(ErrorCode.NOTE_GRANT_SELF);
                }
                grantorId = req.grantorId();
                granteeId = req.granteeId();
                status = NoteUserGrant.APPROVED;
                approvedBy = me;
                approvedAt = Instant.now();
            }
            default -> throw modeInvalid("未知 mode");
        }

        requireActive(grantorId);
        requireActive(granteeId);

        Long active = grantMapper.selectCount(Wrappers.<NoteUserGrant>lambdaQuery()
                .eq(NoteUserGrant::getGrantorId, grantorId)
                .eq(NoteUserGrant::getGranteeId, granteeId)
                .in(NoteUserGrant::getStatus, NoteUserGrant.PENDING, NoteUserGrant.APPROVED));
        if (active != null && active > 0) {
            throw new BusinessException(ErrorCode.NOTE_GRANT_DUPLICATE);
        }
        Long pending = grantMapper.selectCount(Wrappers.<NoteUserGrant>lambdaQuery()
                .eq(NoteUserGrant::getGrantorId, grantorId)
                .eq(NoteUserGrant::getStatus, NoteUserGrant.PENDING));
        if (pending != null && pending >= MAX_PENDING_PER_GRANTOR) {
            throw HttpStatusException.badRequest("每个授权人最多 5 条待确认授权");
        }

        NoteUserGrant g = new NoteUserGrant();
        g.setGrantorId(grantorId);
        g.setGranteeId(granteeId);
        g.setRole(role);
        g.setStatus(status);
        g.setMode(mode);
        g.setCreatedAt(Instant.now());
        g.setCreatedBy(me);
        g.setApprovedBy(approvedBy);
        g.setApprovedAt(approvedAt);
        grantMapper.insert(g);
        return toVo(g);
    }

    /* ==================== 列表 ==================== */

    public List<NoteUserGrantVO> list(String tab, String status, String role, String mode) {
        Long me = UserContext.currentUserId();
        boolean admin = UserContext.isAdmin();
        String t = StringUtils.hasText(tab) ? tab : "created";
        var w = Wrappers.<NoteUserGrant>lambdaQuery();
        switch (t) {
            case "created" -> w.eq(NoteUserGrant::getCreatedBy, me);
            case "granted" -> w.eq(NoteUserGrant::getGrantorId, me);
            case "incoming" -> w.eq(NoteUserGrant::getGranteeId, me)
                    .and(x -> x.ne(NoteUserGrant::getCreatedBy, me).or().isNull(NoteUserGrant::getCreatedBy));
            case "all" -> {
                if (!admin) {
                    throw HttpStatusException.forbidden();
                }
            }
            default -> throw HttpStatusException.badRequest("非法 tab");
        }
        if (StringUtils.hasText(status)) {
            w.eq(NoteUserGrant::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            w.eq(NoteUserGrant::getRole, role);
        }
        if (StringUtils.hasText(mode)) {
            w.eq(NoteUserGrant::getMode, mode);
        }
        w.orderByDesc(NoteUserGrant::getCreatedAt);
        return grantMapper.selectList(w).stream().map(this::toVo).toList();
    }

    /* ==================== 状态机 ==================== */

    @Transactional
    public NoteUserGrantVO approve(Long id) {
        NoteUserGrant g = require(id);
        Long me = UserContext.currentUserId();
        if (!me.equals(g.getGrantorId())) {
            throw HttpStatusException.forbidden();
        }
        requirePending(g);
        g.setStatus(NoteUserGrant.APPROVED);
        g.setApprovedBy(me);
        g.setApprovedAt(Instant.now());
        grantMapper.updateById(g);
        return toVo(g);
    }

    @Transactional
    public NoteUserGrantVO reject(Long id) {
        NoteUserGrant g = require(id);
        Long me = UserContext.currentUserId();
        if (!me.equals(g.getGrantorId())) {
            throw HttpStatusException.forbidden();
        }
        requirePending(g);
        g.setStatus(NoteUserGrant.REJECTED);
        grantMapper.updateById(g);
        return toVo(g);
    }

    @Transactional
    public NoteUserGrantVO upgrade(Long id) {
        NoteUserGrant g = require(id);
        Long me = UserContext.currentUserId();
        if (!me.equals(g.getGrantorId())) {
            throw HttpStatusException.forbidden();
        }
        if (!NoteUserGrant.APPROVED.equals(g.getStatus()) || !NoteUserGrant.VIEWER.equals(g.getRole())) {
            throw new BusinessException(ErrorCode.NOTE_GRANT_FLOW_FORBIDDEN);
        }
        g.setRole(NoteUserGrant.EDITOR);
        grantMapper.updateById(g);
        return toVo(g);
    }

    @Transactional
    public NoteUserGrantVO revoke(Long id) {
        NoteUserGrant g = require(id);
        Long me = UserContext.currentUserId();
        boolean admin = UserContext.isAdmin();
        boolean allowed;
        if (NoteUserGrant.PENDING.equals(g.getStatus())) {
            allowed = me.equals(g.getGrantorId()) || me.equals(g.getCreatedBy()) || admin;
        } else if (NoteUserGrant.APPROVED.equals(g.getStatus())) {
            allowed = me.equals(g.getGrantorId()) || admin;
        } else {
            throw new BusinessException(ErrorCode.NOTE_GRANT_FLOW_FORBIDDEN);
        }
        if (!allowed) {
            throw HttpStatusException.forbidden();
        }
        g.setStatus(NoteUserGrant.REVOKED);
        g.setRevokedBy(me);
        g.setRevokedAt(Instant.now());
        grantMapper.updateById(g);
        return toVo(g);
    }

    /* ==================== 工具 ==================== */

    private NoteUserGrant require(Long id) {
        NoteUserGrant g = grantMapper.selectById(id);
        if (g == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, ErrorCode.NOTE_GRANT_NOT_FOUND);
        }
        return g;
    }

    private void requirePending(NoteUserGrant g) {
        if (!NoteUserGrant.PENDING.equals(g.getStatus())) {
            throw new BusinessException(ErrorCode.NOTE_GRANT_FLOW_FORBIDDEN);
        }
    }

    private void requireActive(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw HttpStatusException.badRequest("用户不存在");
        }
        if (!"ACTIVE".equals(u.getStatus())) {
            throw HttpStatusException.badRequest("用户已禁用");
        }
    }

    private HttpStatusException modeInvalid(String message) {
        return new HttpStatusException(HttpStatus.BAD_REQUEST, ErrorCode.NOTE_GRANT_MODE_INVALID, message);
    }

    private NoteUserGrantVO toVo(NoteUserGrant g) {
        Long me = UserContext.currentUserId();
        boolean admin = UserContext.isAdmin();
        Set<Long> ids = new HashSet<>();
        ids.add(g.getGrantorId());
        ids.add(g.getGranteeId());
        ids.add(g.getCreatedBy());
        Map<Long, String> names = new HashMap<>();
        ids.remove(null);
        if (!ids.isEmpty()) {
            userMapper.selectBatchIds(ids).forEach(u -> names.put(u.getId(), u.getName()));
        }
        String direction = me.equals(g.getGrantorId()) ? "outgoing"
                : me.equals(g.getGranteeId()) ? "incoming" : "none";
        boolean pending = NoteUserGrant.PENDING.equals(g.getStatus());
        boolean approved = NoteUserGrant.APPROVED.equals(g.getStatus());
        boolean isGrantor = me.equals(g.getGrantorId());
        boolean isCreator = me.equals(g.getCreatedBy());

        boolean canApprove = pending && isGrantor;
        boolean canReject = canApprove;
        boolean canUpgrade = approved && isGrantor && NoteUserGrant.VIEWER.equals(g.getRole());
        boolean canRevoke = (pending && (isGrantor || isCreator || admin))
                || (approved && (isGrantor || admin));

        return new NoteUserGrantVO(g.getId(), g.getGrantorId(), names.get(g.getGrantorId()),
                g.getGranteeId(), names.get(g.getGranteeId()), g.getRole(), g.getStatus(),
                g.getMode(), direction, g.getCreatedBy(), names.get(g.getCreatedBy()),
                g.getCreatedAt(), g.getApprovedAt(), g.getApprovedBy(), g.getRevokedAt(), g.getRevokedBy(),
                canApprove, canReject, canUpgrade, canRevoke);
    }
}
