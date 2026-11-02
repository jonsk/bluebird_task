package com.bbtc.bluebird.modules.recycle.application;

import com.bbtc.bluebird.common.domain.PageParam;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.application.NoteCommandService;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.recycle.dto.RecycleBatchReq;
import com.bbtc.bluebird.modules.recycle.dto.RecycleBatchResult;
import com.bbtc.bluebird.modules.recycle.dto.RecycleItemVO;
import com.bbtc.bluebird.modules.recycle.dto.RecycleRow;
import com.bbtc.bluebird.modules.recycle.infrastructure.RecycleMapper;
import com.bbtc.bluebird.modules.task.application.TaskApplicationService;
import com.bbtc.bluebird.modules.task.application.TaskAuthService;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 回收站（2026-10-30）。
 *
 * <p>业务口径（用户拍板）：
 * <ol>
 *   <li>可见范围：**只看自己删的**（{@code deleted_by = 当前用户}），ADMIN 也不额外放大；</li>
 *   <li>保留期：永久，只能手动彻底删除；</li>
 *   <li>彻底删除 / 清空：**仅 ADMIN**（控制器 {@code @PreAuthorize} 把关）；</li>
 *   <li>升级前的历史已删行照常显示，时间为「未知」。</li>
 * </ol>
 *
 * <p>恢复与彻底删除的级联细节见 {@link #restoreTask}/{@link #purgeTask}：任务按子树处理，
 * 恢复前要求父链上的行仍然存在（否则会出现"孤儿子树"）。
 */
@Service
@RequiredArgsConstructor
public class RecycleService {

    private static final String TYPE_TASK = "TASK";
    private static final String TYPE_NOTE = "NOTE";

    private final RecycleMapper recycleMapper;
    private final TaskMapper taskMapper;
    private final NoteMapper noteMapper;
    private final TaskAuthService taskAuthService;
    private final TaskApplicationService taskApplicationService;
    private final NoteCommandService noteCommandService;
    private final SysUserMapper userMapper;

    /* ==================== 列表 ==================== */

    public PageResult<RecycleItemVO> page(String type, String keyword, long page, long size) {
        Long me = UserContext.currentUserId();
        String normalizedType = normalizeType(type);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        PageParam p = PageParam.of(page, size);

        long total = recycleMapper.countPage(normalizedType, me, kw);
        List<RecycleRow> rows = recycleMapper.selectPage(
                normalizedType, me, kw, p.size(), (p.page() - 1) * p.size());

        Map<Long, String> names = userNames(rows);
        List<RecycleItemVO> list = new ArrayList<>(rows.size());
        for (RecycleRow row : rows) {
            boolean isTask = TYPE_TASK.equals(row.getType());
            String reason = isTask ? taskUnrestorableReason(row.getId()) : null;
            list.add(new RecycleItemVO(
                    row.getType(),
                    row.getId(),
                    summarize(row.getTitle()),
                    row.getDeletedAt(),
                    row.getDeletedBy(),
                    row.getDeletedBy() == null ? null : names.get(row.getDeletedBy()),
                    reason == null,
                    reason));
        }
        return PageResult.of(list, total, p.page(), p.size());
    }

    /* ==================== 恢复 ==================== */

    @Transactional
    public void restoreTask(Long id) {
        Long me = UserContext.currentUserId();
        Task task = taskMapper.selectDeletedById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.RECYCLE_NOT_FOUND);
        }
        if (!taskAuthService.isWritable(task, me)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // 父链上的祖先若也已删，一并恢复；若祖先行已被彻底删除 → 禁止恢复（避免孤儿子树）。
        Long pid = task.getParentId();
        Set<Long> guard = new HashSet<>();
        while (pid != null && guard.add(pid)) {
            Task parent = taskMapper.selectRawById(pid);
            if (parent == null) {
                throw new BusinessException(ErrorCode.RECYCLE_PARENT_DELETED);
            }
            if (isDeleted(parent)) {
                taskMapper.restoreDeleted(parent.getId());
            }
            pid = parent.getParentId();
        }
        for (Long tid : taskMapper.selectSubtreeIds(id)) {
            taskMapper.restoreDeleted(tid);
        }
    }

    @Transactional
    public void restoreNote(Long id) {
        Note note = noteMapper.selectDeletedById(id);
        if (note == null) {
            throw new BusinessException(ErrorCode.RECYCLE_NOT_FOUND);
        }
        Long me = UserContext.currentUserId();
        if (!UserContext.isAdmin() && (me == null || !me.equals(note.getOwnerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        noteMapper.restoreDeleted(id);
    }

    /* ==================== 彻底删除 / 清空（仅 ADMIN，控制器把关） ==================== */

    @Transactional
    public void purgeTask(Long id) {
        if (taskMapper.selectDeletedById(id) == null) {
            throw new BusinessException(ErrorCode.RECYCLE_NOT_FOUND);
        }
        taskApplicationService.purge(id);
    }

    @Transactional
    public void purgeNote(Long id) {
        if (noteMapper.selectDeletedById(id) == null) {
            throw new BusinessException(ErrorCode.RECYCLE_NOT_FOUND);
        }
        noteCommandService.purge(id);
    }

    @Transactional
    public int empty(String type) {
        String normalizedType = normalizeType(type);
        int affected = 0;
        if (!"note".equals(normalizedType)) {
            for (Long id : recycleMapper.selectAllDeletedTaskIds()) {
                taskApplicationService.purge(id);
                affected++;
            }
        }
        if (!"task".equals(normalizedType)) {
            for (Long id : recycleMapper.selectAllDeletedNoteIds()) {
                noteCommandService.purge(id);
                affected++;
            }
        }
        return affected;
    }

    /* ==================== 批量（逐条容错） ==================== */

    public RecycleBatchResult restore(List<RecycleBatchReq.RecycleRef> items) {
        return batch(items, this::restoreOne);
    }

    public RecycleBatchResult purge(List<RecycleBatchReq.RecycleRef> items) {
        return batch(items, this::purgeOne);
    }

    private void restoreOne(String type, Long id) {
        if (TYPE_TASK.equals(type)) {
            restoreTask(id);
        } else {
            restoreNote(id);
        }
    }

    private void purgeOne(String type, Long id) {
        if (TYPE_TASK.equals(type)) {
            purgeTask(id);
        } else {
            purgeNote(id);
        }
    }

    private RecycleBatchResult batch(List<RecycleBatchReq.RecycleRef> items,
                                     java.util.function.BiConsumer<String, Long> action) {
        int affected = 0;
        List<RecycleBatchResult.Skipped> skipped = new ArrayList<>();
        for (RecycleBatchReq.RecycleRef item : items) {
            if (item == null || item.id() == null) {
                continue;
            }
            String type = normalizeItemType(item.type());
            try {
                action.accept(type, item.id());
                affected++;
            } catch (BusinessException e) {
                skipped.add(new RecycleBatchResult.Skipped(type, item.id(), e.getMessage()));
            } catch (RuntimeException e) {
                skipped.add(new RecycleBatchResult.Skipped(type, item.id(), "操作失败：" + e.getMessage()));
            }
        }
        return RecycleBatchResult.of(affected, skipped);
    }

    /* ==================== 内部工具 ==================== */

    private String taskUnrestorableReason(Long taskId) {
        Task task = taskMapper.selectDeletedById(taskId);
        if (task == null) {
            return "记录已被彻底删除";
        }
        Long pid = task.getParentId();
        Set<Long> guard = new HashSet<>();
        while (pid != null && guard.add(pid)) {
            Task parent = taskMapper.selectRawById(pid);
            if (parent == null) {
                return "上级任务已被彻底删除，无法恢复";
            }
            pid = parent.getParentId();
        }
        return null;
    }

    private Map<Long, String> userNames(List<RecycleRow> rows) {
        Set<Long> ids = new HashSet<>();
        for (RecycleRow row : rows) {
            if (row.getDeletedBy() != null) {
                ids.add(row.getDeletedBy());
            }
        }
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (SysUser user : userMapper.selectBatchIds(ids)) {
            out.put(user.getId(), StringUtils.hasText(user.getName()) ? user.getName() : user.getUsername());
        }
        return out;
    }

    private static boolean isDeleted(Task task) {
        return task.getDeleted() != null && task.getDeleted() == 1;
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "all";
        }
        String t = type.trim().toLowerCase();
        return switch (t) {
            case "task", "tasks" -> "task";
            case "note", "notes" -> "note";
            default -> "all";
        };
    }

    private static String normalizeItemType(String type) {
        String t = normalizeType(type);
        if ("task".equals(t)) {
            return TYPE_TASK;
        }
        if ("note".equals(t)) {
            return TYPE_NOTE;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "type 只能是 task 或 note");
    }

    private static String summarize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > 60 ? s.substring(0, 60) + '…' : s;
    }
}
