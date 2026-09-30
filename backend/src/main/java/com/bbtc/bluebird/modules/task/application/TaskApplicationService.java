package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.JsonUtils;
import com.bbtc.bluebird.modules.file.application.FileService;
import com.bbtc.bluebird.modules.task.domain.CycleRule;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskCollect;
import com.bbtc.bluebird.modules.task.domain.TaskMenuItem;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import com.bbtc.bluebird.modules.task.domain.TaskTag;
import com.bbtc.bluebird.modules.task.dto.TaskCmd;
import com.bbtc.bluebird.modules.task.dto.TaskCompleteCmd;
import com.bbtc.bluebird.modules.task.infrastructure.TaskCollectMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuItemMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskParticipantMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskTagMapper;
import com.bbtc.bluebird.modules.task.util.CycleExpander;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/** 任务写操作（02 §4.3/§4.6/§4.7）。 */
@Service
@RequiredArgsConstructor
public class TaskApplicationService {

    private final TaskMapper taskMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskTagMapper taskTagMapper;
    private final TaskCollectMapper collectMapper;
    private final TaskMenuItemMapper menuItemMapper;
    private final TaskAuthService authService;
    private final FileService fileService;

    @Transactional
    public Long create(TaskCmd cmd) {
        Long me = UserContext.currentUserId();
        Task t = new Task();
        t.setTitle(cmd.title());
        t.setContent(cmd.content());
        t.setStatus("ACTIVE");
        t.setCompleted(0);
        t.setPriority(StringUtils.hasText(cmd.priority()) ? cmd.priority() : "MEDIUM");
        t.setDueAt(cmd.dueAt());
        t.setRemindAt(cmd.remindAt());
        t.setCategoryId(cmd.categoryId());
        t.setOwnerId(me);
        t.setParentId(cmd.parentId());
        t.setVersion(0);
        if (cmd.cycleRule() != null) {
            t.setCycleRule(JsonUtils.toJson(cmd.cycleRule()));
            if (t.getDueAt() == null) {
                t.setDueAt(cmd.cycleRule().dtstart());
            }
        }
        taskMapper.insert(t);

        replaceParticipants(t.getId(), cmd.assigneeIds(), cmd.ccIds());
        replaceTags(t.getId(), cmd.tagIds());
        fileService.bindToTask(cmd.fileIds(), t.getId());
        return t.getId();
    }

    @Transactional
    public void update(Long id, TaskCmd cmd) {
        Task t = authService.require(id);
        authService.assertWritable(id, UserContext.currentUserId());
        requireVersion(cmd.version());

        if (StringUtils.hasText(cmd.title())) {
            t.setTitle(cmd.title());
        }
        t.setContent(cmd.content());
        t.setDueAt(cmd.dueAt());
        t.setRemindAt(cmd.remindAt());
        if (StringUtils.hasText(cmd.priority())) {
            t.setPriority(cmd.priority());
        }
        t.setCategoryId(cmd.categoryId());
        if (cmd.cycleRule() != null) {
            t.setCycleRule(JsonUtils.toJson(cmd.cycleRule()));
        } else {
            // 清空周期即转普通任务（02 §4.3）
            t.setCycleRule(null);
        }
        t.setVersion(cmd.version().intValue());

        int rows = taskMapper.updateById(t);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        replaceParticipants(id, cmd.assigneeIds(), cmd.ccIds());
        replaceTags(id, cmd.tagIds());
        fileService.bindToTask(cmd.fileIds(), id);
    }

    @Transactional
    public void delete(Long id) {
        Task t = authService.require(id);
        authService.assertWritable(id, UserContext.currentUserId());
        // 级联软删子任务
        List<Task> children = taskMapper.selectList(Wrappers.<Task>lambdaQuery().eq(Task::getParentId, id));
        for (Task c : children) {
            cascadeCleanup(c.getId());
            taskMapper.deleteById(c.getId());
        }
        cascadeCleanup(id);
        taskMapper.deleteById(id);
    }

    private void cascadeCleanup(Long taskId) {
        participantMapper.delete(Wrappers.<TaskParticipant>lambdaQuery().eq(TaskParticipant::getTaskId, taskId));
        taskTagMapper.delete(Wrappers.<TaskTag>lambdaQuery().eq(TaskTag::getTaskId, taskId));
        collectMapper.delete(Wrappers.<TaskCollect>lambdaQuery().eq(TaskCollect::getTaskId, taskId));
        menuItemMapper.delete(Wrappers.<TaskMenuItem>lambdaQuery().eq(TaskMenuItem::getTaskId, taskId));
        fileService.deleteByTask(taskId);
    }

    @Transactional
    public void complete(Long id, TaskCompleteCmd cmd) {
        Task t = authService.require(id);
        authService.assertWritable(id, UserContext.currentUserId());
        if (!"ACTIVE".equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_COMPLETED);
        }
        if (t.getCycleRule() != null) {
            completeRecurring(t, cmd);
            return;
        }
        requireVersion(cmd.version());
        if (t.getCompleted() != null && t.getCompleted() == 1) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_COMPLETED);
        }
        t.setCompleted(1);
        t.setVersion(cmd.version().intValue());
        if (taskMapper.updateById(t) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    @Transactional
    public void uncomplete(Long id, TaskCompleteCmd cmd) {
        Task t = authService.require(id);
        authService.assertWritable(id, UserContext.currentUserId());
        if (t.getCycleRule() != null) {
            uncompleteRecurring(t, cmd);
            return;
        }
        requireVersion(cmd.version());
        t.setCompleted(0);
        t.setVersion(cmd.version().intValue());
        if (taskMapper.updateById(t) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private void completeRecurring(Task t, TaskCompleteCmd cmd) {
        CycleRule rule = JsonUtils.parse(t.getCycleRule(), CycleRule.class);
        Instant target = cmd.dueAt();
        Instant last = t.getCycleLastCompleted();
        if (target == null) {
            target = CycleExpander.firstNext(rule, last, Instant.now(), Instant.now().plusSeconds(3660L * 24 * 365));
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_COMPLETED);
        }
        // 校验为合法发生实例且晚于 lastCompleted
        List<Instant> hits = CycleExpander.expand(rule, last, target, target, 1);
        if (hits.isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_COMPLETED);
        }
        t.setCycleLastCompleted(target);
        t.setCompleted(0); // 模板行 completed 恒 0
        taskMapper.updateById(t);
    }

    private void uncompleteRecurring(Task t, TaskCompleteCmd cmd) {
        CycleRule rule = JsonUtils.parse(t.getCycleRule(), CycleRule.class);
        Instant last = t.getCycleLastCompleted();
        Instant target = cmd.dueAt() != null ? cmd.dueAt() : last;
        if (last == null || target == null || !last.equals(target)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能取消最近一次完成的实例");
        }
        // 回退到恰好早于 target 的上一发生时刻；若为首个则置 NULL
        Instant prev = previousOccurrence(rule, target);
        t.setCycleLastCompleted(prev);
        taskMapper.updateById(t);
    }

    private Instant previousOccurrence(CycleRule rule, Instant target) {
        if (rule == null || rule.dtstart() == null) {
            return null;
        }
        // 在 target 之前一个足够宽的窗口内取最后一次发生
        List<Instant> before = CycleExpander.expand(rule, null,
                rule.dtstart(), target.minusMillis(1), Integer.MAX_VALUE);
        if (before.isEmpty()) {
            return null;
        }
        return before.get(before.size() - 1);
    }

    @Transactional
    public void collect(Long id) {
        Long me = UserContext.currentUserId();
        authService.assertVisible(id, me);
        Long cnt = collectMapper.selectCount(Wrappers.<TaskCollect>lambdaQuery()
                .eq(TaskCollect::getTaskId, id).eq(TaskCollect::getUserId, me));
        if (cnt != null && cnt > 0) {
            return;
        }
        TaskCollect c = new TaskCollect();
        c.setTaskId(id);
        c.setUserId(me);
        collectMapper.insert(c);
    }

    @Transactional
    public void uncollect(Long id) {
        Long me = UserContext.currentUserId();
        collectMapper.delete(Wrappers.<TaskCollect>lambdaQuery()
                .eq(TaskCollect::getTaskId, id).eq(TaskCollect::getUserId, me));
    }

    private void replaceParticipants(Long taskId, List<Long> assignees, List<Long> cc) {
        participantMapper.delete(Wrappers.<TaskParticipant>lambdaQuery().eq(TaskParticipant::getTaskId, taskId));
        if (assignees != null) {
            for (Long uid : assignees.stream().distinct().toList()) {
                insertParticipant(taskId, uid, TaskParticipant.ASSIGNEE);
            }
        }
        if (cc != null) {
            for (Long uid : cc.stream().distinct().toList()) {
                insertParticipant(taskId, uid, TaskParticipant.CC);
            }
        }
    }

    private void insertParticipant(Long taskId, Long userId, String role) {
        TaskParticipant p = new TaskParticipant();
        p.setTaskId(taskId);
        p.setUserId(userId);
        p.setRole(role);
        participantMapper.insert(p);
    }

    private void replaceTags(Long taskId, List<Long> tagIds) {
        taskTagMapper.delete(Wrappers.<TaskTag>lambdaQuery().eq(TaskTag::getTaskId, taskId));
        if (tagIds != null) {
            for (Long tid : tagIds.stream().distinct().toList()) {
                TaskTag tt = new TaskTag();
                tt.setTaskId(taskId);
                tt.setTagId(tid);
                taskTagMapper.insert(tt);
            }
        }
    }

    private void requireVersion(Long version) {
        if (version == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "缺少 version（乐观锁）");
        }
    }
}
