package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskCollect;
import com.bbtc.bluebird.modules.task.domain.TaskMenu;
import com.bbtc.bluebird.modules.task.domain.TaskMenuItem;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import com.bbtc.bluebird.modules.task.infrastructure.TaskCollectMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuItemMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskParticipantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 视图 range 解析：把 {@code scope} 翻译成「候选任务 id 集合」。
 *
 * <p>从 {@link TaskQueryService} 抽出：六个视图的分页、计数、日历共用同一套口径，
 * 但这段逻辑与 VO 组装无关，单独放在这里便于对照 02 §4.4 的视图定义逐条读。
 */
@Service
@RequiredArgsConstructor
public class TaskScopeResolver {

    private final TaskMapper taskMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskCollectMapper collectMapper;
    private final TaskMenuMapper menuMapper;
    private final TaskMenuItemMapper menuItemMapper;
    private final DepartmentService departmentService;

    @Value("${app.security.manager-can-read-subordinate:false}")
    private boolean managerCanReadSubordinate;

    /** 视图范围内的候选任务 id（不做状态/步骤过滤，由调用方再加条件）。 */
    public Set<Long> candidateTaskIds(Long me, String scope, boolean subordinate) {
        Set<Long> ids = new HashSet<>();
        if ("collect".equals(scope)) {
            collectMapper.selectList(Wrappers.<TaskCollect>lambdaQuery().eq(TaskCollect::getUserId, me))
                    .forEach(c -> ids.add(c.getTaskId()));
            return ids;
        }
        if ("joined".equals(scope)) {
            participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery()
                            .eq(TaskParticipant::getUserId, me).eq(TaskParticipant::getRole, TaskParticipant.CC))
                    .forEach(p -> ids.add(p.getTaskId()));
            return ids;
        }
        if ("assigned".equals(scope)) {
            participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery()
                            .eq(TaskParticipant::getUserId, me).eq(TaskParticipant::getRole, TaskParticipant.ASSIGNEE))
                    .forEach(p -> ids.add(p.getTaskId()));
            return ids;
        }
        // day/all: owner=me 或 participant(me)
        Set<Long> ownerTasks = new HashSet<>();
        taskMapper.selectList(Wrappers.<Task>lambdaQuery().select(Task::getId).eq(Task::getOwnerId, me))
                .forEach(t -> ownerTasks.add(t.getId()));
        ids.addAll(ownerTasks);
        participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery().eq(TaskParticipant::getUserId, me))
                .forEach(p -> ids.add(p.getTaskId()));

        if (subordinate && "all".equals(scope) && managerCanReadSubordinate) {
            Set<Long> subs = departmentService.subordinates(me);
            if (!subs.isEmpty()) {
                taskMapper.selectList(Wrappers.<Task>lambdaQuery().select(Task::getId).in(Task::getOwnerId, subs))
                        .forEach(t -> ids.add(t.getId()));
                participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery().in(TaskParticipant::getUserId, subs))
                        .forEach(p -> ids.add(p.getTaskId()));
            }
        }
        return ids;
    }

    /** 自定义栏条目对应的任务 id（仅本人自定义栏，否则 404）。 */
    public Set<Long> menuTaskIds(Long menuId, Long me) {
        TaskMenu menu = menuMapper.selectById(menuId);
        if (menu == null || !me.equals(menu.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "自定义栏不存在");
        }
        Set<Long> ids = new HashSet<>();
        menuItemMapper.selectList(Wrappers.<TaskMenuItem>lambdaQuery().eq(TaskMenuItem::getMenuId, menuId))
                .forEach(it -> ids.add(it.getTaskId()));
        return ids;
    }
}
