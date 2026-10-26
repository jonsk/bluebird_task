package com.bbtc.bluebird.modules.note.application;

import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.dto.NoteFieldPermissionsVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 日志权限：isVisible / canEdit / canManage 的**唯一 Java 事实来源**。
 *
 * <p>与 {@code NoteVisibilitySqlBuilder.visiblePredicate} 逐分支等价。
 * 不可见资源统一 404（不暴露存在性）。
 *
 * <p>可见性只有三条：自己 ⊂ 上级（开关）⊂ ADMIN；没有授权、没有部门档、没有公开档。
 */
@Service
@RequiredArgsConstructor
public class NoteAuthService {

    private final NoteMapper noteMapper;
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
     * 只校验「存在且未删除」（不校验可见性），供写操作自行区分 404 / 403。
     */
    public Note requireExisting(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || isTrue(note.getDeleted())) {
            throw HttpStatusException.notFound();
        }
        return note;
    }

    /**
     * 可见性判定：deleted → admin → owner → 下属（开关）。
     *
     * <p>上级读下属由开关决定，属制度性例外；其余人一律不可见。
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
        if (managerCanReadSubordinate && note.getOwnerId() != null) {
            return departmentService.subordinates(me).contains(note.getOwnerId());
        }
        return false;
    }

    /** 编辑权：owner / admin 恒可；其余 false。 */
    public boolean canEdit(Note note, Long me) {
        if (note == null || isTrue(note.getDeleted())) {
            return false;
        }
        return isAdmin() || (me != null && me.equals(note.getOwnerId()));
    }

    /** 管理权：tagIds/pin/delete/引用移除/附件移除。 */
    public boolean canManage(Note note, Long me) {
        return canEdit(note, me);
    }

    public boolean isWritable(Note note, Long me) {
        return canEdit(note, me);
    }

    /** 当前用户对该 note 的角色：OWNER / ADMIN / null。 */
    public String roleOf(Note note, Long me) {
        if (me == null) {
            return null;
        }
        if (me.equals(note.getOwnerId())) {
            return "OWNER";
        }
        return isAdmin() ? "ADMIN" : null;
    }

    /**
     * 字段级可编辑性（详情回传给前端，写路径校验的唯一镜像）。
     *
     * <p>删授权后只剩两态：owner/ADMIN → 全可改；其余 → 全只读。
     */
    public NoteFieldPermissionsVO fieldPermissions(Note note, Long me) {
        if (note == null || isTrue(note.getDeleted()) || me == null) {
            return NoteFieldPermissionsVO.none();
        }
        return canManage(note, me) ? NoteFieldPermissionsVO.all() : NoteFieldPermissionsVO.none();
    }

    /** 写操作前置：不可见 404；只读角色 403。 */
    public void assertWritable(Note note, Long me) {
        if (!isVisible(note, me)) {
            throw HttpStatusException.notFound();
        }
        if (canManage(note, me)) {
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

    public boolean isAdmin() {
        return UserContext.isAdmin();
    }

    private boolean isTrue(Integer v) {
        return v != null && v == 1;
    }
}
