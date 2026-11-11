package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.JsonUtils;
import com.bbtc.bluebird.modules.file.application.FileService;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import com.bbtc.bluebird.modules.task.domain.TaskTag;
import com.bbtc.bluebird.modules.task.dto.FileRef;
import com.bbtc.bluebird.modules.task.dto.TagVO;
import com.bbtc.bluebird.modules.task.dto.TaskDetailVO;
import com.bbtc.bluebird.modules.task.dto.TaskVO;
import com.bbtc.bluebird.modules.task.dto.UserBrief;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskParticipantMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link Task} 实体到 VO 的组装（标签、附件可见性、参与人、子任务统计）。
 *
 * <p>从 {@link TaskQueryService} 抽出：列表/详情/子任务/日历四条读路径都走这里，
 * 批量查询的写法（按 id 集合一次查回、再在内存里分组）必须保持一致，否则很容易
 * 退化成 N+1。
 */
@Service
@RequiredArgsConstructor
public class TaskVoAssembler {

    private final TaskMapper taskMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskTagMapper taskTagMapper;
    private final TagMapper tagMapper;
    private final AttachmentMapper attachmentMapper;
    private final UserLookupService userLookup;
    private final TaskAuthService authService;

    /** 按任务 id 批量取参与人，供调用方复用（避免重复查询）。 */
    public Map<Long, List<TaskParticipant>> participantsOf(List<Long> taskIds) {
        Map<Long, List<TaskParticipant>> map = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return map;
        }
        participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery().in(TaskParticipant::getTaskId, taskIds))
                .forEach(p -> map.computeIfAbsent(p.getTaskId(), k -> new ArrayList<>()).add(p));
        return map;
    }

    public List<TaskVO> assemble(List<Task> tasks, Map<Long, List<TaskParticipant>> parts) {
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = tasks.stream().map(Task::getId).toList();

        Map<Long, List<TagVO>> tags = new HashMap<>();
        List<TaskTag> links = taskTagMapper.selectList(Wrappers.<TaskTag>lambdaQuery().in(TaskTag::getTaskId, ids));
        Map<Long, String> tagNames = new HashMap<>();
        Set<Long> tagIds = new HashSet<>();
        links.forEach(l -> tagIds.add(l.getTagId()));
        if (!tagIds.isEmpty()) {
            tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, tagIds))
                    .forEach(t -> tagNames.put(t.getId(), t.getName()));
        }
        links.forEach(l -> {
            String name = tagNames.get(l.getTagId());
            if (name != null) {
                tags.computeIfAbsent(l.getTaskId(), k -> new ArrayList<>()).add(new TagVO(l.getTagId(), name));
            }
        });

        Map<Long, List<FileRef>> files = new HashMap<>();
        Long me = UserContext.currentUserId();
        boolean admin = UserContext.isAdmin();
        Map<Long, Boolean> writable = new HashMap<>();
        tasks.forEach(t -> writable.put(t.getId(), authService.isWritable(t, me)));
        attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery().in(Attachment::getTaskId, ids))
                .forEach(a -> {
                    boolean mine = a.getCreatedBy() != null && a.getCreatedBy().equals(me);
                    boolean taskWritable = Boolean.TRUE.equals(writable.get(a.getTaskId()));
                    files.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>())
                            .add(new FileRef(a.getId(), a.getFileName(), a.getSize(),
                                    FileService.taskDownloadUrl(a.getTaskId(), a.getId()),
                                    a.getCreatedBy(), admin || mine,
                                    taskWritable && (admin || mine)));
                });

        // 子任务统计
        Map<Long, int[]> subCounts = new HashMap<>();
        taskMapper.selectList(Wrappers.<Task>lambdaQuery().select(Task::getParentId, Task::getCompleted)
                        .in(Task::getParentId, ids))
                .forEach(c -> {
                    int[] arr = subCounts.computeIfAbsent(c.getParentId(), k -> new int[2]);
                    arr[0]++;
                    if (c.getCompleted() != null && c.getCompleted() == 1) {
                        arr[1]++;
                    }
                });

        Set<Long> userIds = new HashSet<>();
        tasks.forEach(t -> {
            if (t.getOwnerId() != null) {
                userIds.add(t.getOwnerId());
            }
        });
        parts.values().forEach(ps -> ps.forEach(p -> userIds.add(p.getUserId())));
        Map<Long, UserBrief> briefs = userLookup.briefs(userIds);

        List<TaskVO> out = new ArrayList<>();
        for (Task t : tasks) {
            TaskVO vo = baseVO(t, briefs);
            List<TaskParticipant> ps = parts.getOrDefault(t.getId(), List.of());
            for (TaskParticipant p : ps) {
                UserBrief b = briefs.get(p.getUserId());
                if (b == null) {
                    continue;
                }
                vo.getParticipantIds().add(p.getUserId());
                if (TaskParticipant.ASSIGNEE.equals(p.getRole())) {
                    vo.getAssignees().add(b);
                } else if (TaskParticipant.CC.equals(p.getRole())) {
                    vo.getCcUsers().add(b);
                }
            }
            vo.setTags(tags.getOrDefault(t.getId(), new ArrayList<>()));
            vo.setFiles(files.getOrDefault(t.getId(), new ArrayList<>()));
            int[] sc = subCounts.get(t.getId());
            if (sc != null) {
                vo.setSubtaskTotal(sc[0]);
                vo.setSubtaskCompleted(sc[1]);
            }
            out.add(vo);
        }
        return out;
    }

    /** 周期任务的一个实例：沿用任务本体，只改写截止时间与完成态。 */
    public TaskVO assembleInstance(Task t, Instant occurrence, Map<Long, List<TaskParticipant>> parts) {
        TaskVO assembled = assemble(List.of(t), parts).get(0);
        assembled.setDueAt(occurrence);
        assembled.setCompleted(false);
        return assembled;
    }

    public TaskDetailVO copy(TaskVO vo) {
        TaskDetailVO d = new TaskDetailVO();
        d.setId(vo.getId());
        d.setTitle(vo.getTitle());
        d.setContent(vo.getContent());
        d.setStatus(vo.getStatus());
        d.setCompleted(vo.getCompleted());
        d.setDueAt(vo.getDueAt());
        d.setRemindAt(vo.getRemindAt());
        d.setPriority(vo.getPriority());
        d.setCycleRule(vo.getCycleRule());
        d.setCycleLastCompleted(vo.getCycleLastCompleted());
        d.setOwner(vo.getOwner());
        d.setAssignees(vo.getAssignees());
        d.setCcUsers(vo.getCcUsers());
        d.setParticipantIds(vo.getParticipantIds());
        d.setVersion(vo.getVersion());
        d.setTags(vo.getTags());
        d.setSubtaskCompleted(vo.getSubtaskCompleted());
        d.setSubtaskTotal(vo.getSubtaskTotal());
        d.setFiles(vo.getFiles());
        d.setCreatedAt(vo.getCreatedAt());
        return d;
    }

    private TaskVO baseVO(Task t, Map<Long, UserBrief> briefs) {
        TaskVO vo = new TaskVO();
        vo.setId(t.getId());
        vo.setTitle(t.getTitle());
        vo.setContent(t.getContent());
        vo.setStatus(t.getStatus());
        vo.setCompleted(t.getCompleted() != null && t.getCompleted() == 1);
        vo.setDueAt(t.getDueAt());
        vo.setRemindAt(t.getRemindAt());
        vo.setPriority(t.getPriority());
        if (t.getCycleRule() != null) {
            vo.setCycleRule(JsonUtils.readTree(t.getCycleRule()));
        }
        vo.setCycleLastCompleted(t.getCycleLastCompleted());
        vo.setOwner(briefs.get(t.getOwnerId()));
        vo.setVersion(t.getVersion() == null ? null : t.getVersion().longValue());
        vo.setCreatedAt(t.getCreatedAt());
        return vo;
    }
}
