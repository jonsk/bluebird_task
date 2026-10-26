package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteUserGrant;
import com.bbtc.bluebird.modules.note.domain.Visibility;
import com.bbtc.bluebird.modules.note.dto.NoteFieldPermissionsVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteUserGrantMapper;
import com.bbtc.bluebird.modules.org.application.DepartmentPolicy;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 日志权限：isVisible / canEdit / canManage 的**唯一 Java 事实来源**。
 *
 * <p>与 {@code NoteVisibilitySqlBuilder.visiblePredicate} 逐分支等价（§6.3 十五行矩阵约束）。
 * 不可见资源统一 404（不暴露存在性）。
 */
@Service
@RequiredArgsConstructor
public class NoteAuthService {

    private final NoteMapper noteMapper;
    private final NoteUserGrantMapper grantMapper;
    private final DepartmentPolicy departmentPolicy;
    private final DepartmentService departmentService;

    @Value("${app.security.manager-can-read-subordinate:false}")
    private boolean managerCanReadSubordinate;

    public Note requireVisible(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || !isVisible(note, UserContext.currentUserId())) {
            throw HttpStatusException.notFound();
        }
        return note;
    }

    /**
     * 只校验「存在且未删除」（不校验可见性），供写操作自行区分 404 / 50013 / 403。
     *
     * <p>写路径必须先调用本方法、再调用 {@link #assertWritable}：否则归档日志对非 owner/ADMIN
     * 会先被 404 短路，{@code 50013}（归档只读）永远不可达（验收矩阵 27a）。
     */
    public Note requireExisting(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || isTrue(note.getDeleted())) {
            throw HttpStatusException.notFound();
        }
        return note;
    }

    /**
     * 可见性判定（§3.3）：
     * deleted → admin → owner → archived → grant → INTERNAL 部门 → PUBLIC → 下属（开关）。
     */
    public boolean isVisible(Note note, Long me) {
        if (note == null || isTrue(note.getDeleted())) {
            return false;
        }
        if (me == null) {
            return false;
        }
        if (isAdmin()) {
            return true;
        }
        if (me.equals(note.getOwnerId())) {
            return true;
        }
        if (isTrue(note.getArchived())) {
            return false;
        }
        if (hasApprovedGrant(note.getOwnerId(), me)) {
            return true;
        }
        if (Visibility.INTERNAL.name().equals(note.getVisibility()) && note.getDeptId() != null) {
            Set<Long> depts = departmentPolicy.visibleDeptIds(me);
            if (depts.contains(note.getDeptId())) {
                return true;
            }
        }
        if (Visibility.PUBLIC.name().equals(note.getVisibility())) {
            return true;
        }
        if (managerCanReadSubordinate && note.getOwnerId() != null) {
            return departmentService.subordinates(me).contains(note.getOwnerId());
        }
        return false;
    }

    /** 编辑权：owner/admin 恒可；grant EDITOR 仅未归档；其余 false。 */
    public boolean canEdit(Note note, Long me) {
        if (note == null || isTrue(note.getDeleted())) {
            return false;
        }
        if (isAdmin() || me.equals(note.getOwnerId())) {
            return true;
        }
        return NoteUserGrant.EDITOR.equals(grantRole(note, me)) && !isTrue(note.getArchived());
    }

    /** 管理权：visibility/tagIds/pin/archive/delete/引用移除/附件移除。 */
    public boolean canManage(Note note, Long me) {
        if (note == null || isTrue(note.getDeleted())) {
            return false;
        }
        return isAdmin() || me.equals(note.getOwnerId());
    }

    public boolean isWritable(Note note, Long me) {
        return canEdit(note, me);
    }

    /** 当前用户对该 note 的角色：OWNER / ADMIN / VIEWER / EDITOR / null。 */
    public String grantRole(Note note, Long me) {
        if (me == null) {
            return null;
        }
        if (me.equals(note.getOwnerId())) {
            return "OWNER";
        }
        if (isAdmin()) {
            return "ADMIN";
        }
        NoteUserGrant g = approvedGrant(note.getOwnerId(), me);
        return g == null ? null : g.getRole();
    }

    /**
     * 字段级可编辑性（详情回传给前端，写路径校验的唯一镜像）。
     *
     * <p>与 {@link #assertWritable} / {@code NoteCommandService.update} 的分支一一对应：
     * 归档 → 正文与日期也不可改（50013「已归档日志不允许编辑」）；
     * EDITOR → 仅正文与日期；VIEWER 及其它只读可见者 → 全 false。
     */
    public NoteFieldPermissionsVO fieldPermissions(Note note, Long me) {
        if (note == null || isTrue(note.getDeleted()) || me == null) {
            return NoteFieldPermissionsVO.none();
        }
        if (canManage(note, me)) {
            // owner / ADMIN：归档后仍可改（业务上允许 owner 归档后修订）
            return NoteFieldPermissionsVO.all();
        }
        if (NoteUserGrant.EDITOR.equals(grantRole(note, me)) && !isTrue(note.getArchived())) {
            return new NoteFieldPermissionsVO(true, true, false, false);
        }
        return NoteFieldPermissionsVO.none();
    }

    /** 写操作前置：不可见 404；归档日志的 EDITOR 50013；只读角色 403。 */
    public void assertWritable(Note note, Long me) {
        if (!isVisible(note, me)) {
            // 归档日志对非 owner/ADMIN 不可见：已获 EDITOR 授权者明确被告知「归档只读」，而不是 404
            if (isTrue(note.getArchived()) && NoteUserGrant.EDITOR.equals(grantRole(note, me))) {
                throw new BusinessException(ErrorCode.NOTE_FIELD_FORBIDDEN, "已归档日志不允许编辑");
            }
            throw HttpStatusException.notFound();
        }
        if (canManage(note, me)) {
            return;
        }
        if (NoteUserGrant.EDITOR.equals(grantRole(note, me))) {
            if (isTrue(note.getArchived())) {
                throw new BusinessException(ErrorCode.NOTE_FIELD_FORBIDDEN, "已归档日志不允许编辑");
            }
            return;
        }
        throw HttpStatusException.forbidden();
    }

    /** 管理操作前置：不可见 404；非 owner/admin 403。 */
    public Note requireManageable(Long id) {
        Note note = requireVisible(id);
        if (!canManage(note, UserContext.currentUserId())) {
            throw HttpStatusException.forbidden();
        }
        return note;
    }

    public boolean hasApprovedGrant(Long grantorId, Long granteeId) {
        return approvedGrant(grantorId, granteeId) != null;
    }

    public NoteUserGrant approvedGrant(Long grantorId, Long granteeId) {
        if (grantorId == null || granteeId == null) {
            return null;
        }
        return grantMapper.selectOne(Wrappers.<NoteUserGrant>lambdaQuery()
                .eq(NoteUserGrant::getGrantorId, grantorId)
                .eq(NoteUserGrant::getGranteeId, granteeId)
                .eq(NoteUserGrant::getStatus, NoteUserGrant.APPROVED)
                .last("LIMIT 1"));
    }

    public boolean isAdmin() {
        return UserContext.isAdmin();
    }

    private boolean isTrue(Integer v) {
        return v != null && v == 1;
    }
}
