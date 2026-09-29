package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 任务权限（02 §4.7）：可写 = {owner, assignee, ADMIN}；可读 = 可写 ∪ {CC} ∪ 下属可见性（ADR-012）。
 */
@Service
@RequiredArgsConstructor
public class TaskAuthService {

    private final TaskMapper taskMapper;
    private final TaskParticipantMapper participantMapper;
    private final DepartmentService departmentService;

    @Value("${app.security.manager-can-read-subordinate:false}")
    private boolean managerCanReadSubordinate;

    public Task require(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    public void assertWritable(Long taskId, Long userId) {
        Task task = require(taskId);
        if (!isWritable(task, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean isWritable(Task task, Long userId) {
        if (isAdmin()) {
            return true;
        }
        if (task.getOwnerId() != null && task.getOwnerId().equals(userId)) {
            return true;
        }
        return isAssignee(task.getId(), userId);
    }

    public void assertVisible(Long taskId, Long userId) {
        Task task = require(taskId);
        if (!isVisible(task, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean isVisible(Task task, Long userId) {
        if (isAdmin()) {
            return true;
        }
        if (task.getOwnerId() != null && task.getOwnerId().equals(userId)) {
            return true;
        }
        if (isParticipant(task.getId(), userId)) {
            return true;
        }
        // 下属只读可见（受开关控制，ADR-012）
        if (managerCanReadSubordinate) {
            Set<Long> subs = departmentService.subordinates(userId);
            if (!subs.isEmpty()) {
                if (task.getOwnerId() != null && subs.contains(task.getOwnerId())) {
                    return true;
                }
                for (Long pid : participantUserIds(task.getId())) {
                    if (subs.contains(pid)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isAssignee(Long taskId, Long userId) {
        Long cnt = participantMapper.selectCount(Wrappers.<TaskParticipant>lambdaQuery()
                .eq(TaskParticipant::getTaskId, taskId)
                .eq(TaskParticipant::getUserId, userId)
                .eq(TaskParticipant::getRole, TaskParticipant.ASSIGNEE));
        return cnt != null && cnt > 0;
    }

    public boolean isParticipant(Long taskId, Long userId) {
        Long cnt = participantMapper.selectCount(Wrappers.<TaskParticipant>lambdaQuery()
                .eq(TaskParticipant::getTaskId, taskId)
                .eq(TaskParticipant::getUserId, userId));
        return cnt != null && cnt > 0;
    }

    public java.util.List<Long> participantUserIds(Long taskId) {
        return participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery()
                        .eq(TaskParticipant::getTaskId, taskId))
                .stream().map(TaskParticipant::getUserId).distinct().toList();
    }

    public boolean isAdmin() {
        return UserContext.isAdmin();
    }
}
