package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.JsonUtils;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import com.bbtc.bluebird.modules.task.domain.Category;
import com.bbtc.bluebird.modules.task.domain.CycleRule;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskCollect;
import com.bbtc.bluebird.modules.task.domain.TaskMenu;
import com.bbtc.bluebird.modules.task.domain.TaskMenuItem;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import com.bbtc.bluebird.modules.task.domain.TaskTag;
import com.bbtc.bluebird.modules.task.dto.CategoryBrief;
import com.bbtc.bluebird.modules.task.dto.CountVO;
import com.bbtc.bluebird.modules.task.dto.FileRef;
import com.bbtc.bluebird.modules.task.dto.TagVO;
import com.bbtc.bluebird.modules.task.dto.TaskDetailVO;
import com.bbtc.bluebird.modules.task.dto.TaskQuery;
import com.bbtc.bluebird.modules.task.dto.TaskVO;
import com.bbtc.bluebird.modules.task.dto.UserBrief;
import com.bbtc.bluebird.modules.task.infrastructure.CategoryMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskCollectMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuItemMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskParticipantMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskTagMapper;
import com.bbtc.bluebird.modules.task.util.CycleExpander;
import com.bbtc.bluebird.modules.task.util.TaskSorter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 任务读查询（02 §4.4/§4.5）：六大视图、详情、计数、日历、子任务。 */
@Service
@RequiredArgsConstructor
public class TaskQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int CYCLE_MAX = 100;

    private final TaskMapper taskMapper;
    private final TaskParticipantMapper participantMapper;
    private final TaskTagMapper taskTagMapper;
    private final TaskCollectMapper collectMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final TaskMenuMapper menuMapper;
    private final TaskMenuItemMapper menuItemMapper;
    private final CategoryService categoryService;
    private final AttachmentMapper attachmentMapper;
    private final DepartmentService departmentService;
    private final UserLookupService userLookup;
    private final TaskAuthService authService;

    @Value("${app.security.manager-can-read-subordinate:false}")
    private boolean managerCanReadSubordinate;

    /* ==================== 列表 / 计数 ==================== */

    public PageResult<TaskVO> page(TaskQuery q) {
        List<TaskVO> list = collect(q.scope(), q.keyword(), q.subordinate(), q.date(), q.categoryId(), q.menuId());
        list.sort(TaskSorter.comparator(Instant.now()));
        long total = list.size();
        long page = Math.max(1, q.page());
        long size = q.size() < 1 ? 20 : Math.min(q.size(), 100);
        int from = (int) Math.min((page - 1) * size, list.size());
        int to = (int) Math.min(from + size, list.size());
        return PageResult.of(new ArrayList<>(list.subList(from, to)), total, page, size);
    }

    public CountVO counts() {
        return new CountVO(
                collect("day", null, false, null, null, null).size(),
                collect("week", null, false, null, null, null).size(),
                collect("joined", null, false, null, null, null).size(),
                collect("assigned", null, false, null, null, null).size(),
                collect("collect", null, false, null, null, null).size(),
                collect("all", null, false, null, null, null).size());
    }

    public List<TaskVO> calendar(Instant start, Instant end) {
        Long me = UserContext.currentUserId();
        Set<Long> candidate = candidateTaskIds(me, "all", false);
        if (candidate.isEmpty()) {
            return List.of();
        }
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .in(Task::getId, candidate).eq(Task::getStatus, "ACTIVE")
                // 步骤/子任务不计入日历（只在父任务详情里展示）
                .isNull(Task::getParentId));
        Map<Long, List<TaskParticipant>> parts = participantsOf(tasks.stream().map(Task::getId).toList());
        List<TaskVO> out = new ArrayList<>();
        List<Task> plain = new ArrayList<>();
        for (Task t : tasks) {
            if (t.getCycleRule() == null) {
                plain.add(t);
            } else {
                CycleRule rule = JsonUtils.parse(t.getCycleRule(), CycleRule.class);
                for (Instant occ : CycleExpander.expand(rule, t.getCycleLastCompleted(), start, end, CYCLE_MAX)) {
                    out.add(assembleInstance(t, occ, parts));
                }
            }
        }
        out.addAll(assemble(plain, parts));
        return out;
    }

    public List<TaskVO> subtasks(Long parentId) {
        authService.assertVisible(parentId, UserContext.currentUserId());
        List<Task> list = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getParentId, parentId).orderByAsc(Task::getId));
        return assemble(list, participantsOf(list.stream().map(Task::getId).toList()));
    }

    public TaskDetailVO detail(Long id) {
        Long me = UserContext.currentUserId();
        Task t = authService.require(id);
        boolean visible = authService.isVisible(t, me);
        if (!visible) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Task> list = List.of(t);
        TaskVO vo = assemble(list, participantsOf(list.stream().map(Task::getId).toList())).get(0);
        TaskDetailVO detail = copy(vo);
        List<Task> children = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getParentId, id).orderByAsc(Task::getId));
        detail.setSubtasks(assemble(children, participantsOf(children.stream().map(Task::getId).toList())));
        return detail;
    }

    /* ==================== scope 解析 ==================== */

    private List<TaskVO> collect(String scope, String keyword, boolean subordinate, String date,
                                 Long categoryId, Long menuId) {
        Long me = UserContext.currentUserId();
        Set<Long> candidate = candidateTaskIds(me, scope, subordinate);
        if (categoryId != null) {
            candidate.retainAll(categoryTaskIds(categoryId));
        }
        if (menuId != null) {
            candidate.retainAll(menuTaskIds(menuId, me));
        }
        if (candidate.isEmpty()) {
            return new ArrayList<>();
        }
        // 我的一天（day）：按**任务建立时间**取当天 —— 对齐旧系统 getDayTask 的
        //   `task_setup_time LIKE 'yyyy-MM-dd'`（旧库该列注释即「任务建立时间」，非截止时间）。
        // 未来7天（week）：按**截止时间**开窗 —— 对齐旧系统 getWeekTask 的
        //   `DATE_ADD(task_setup_time, INTERVAL 7 DAY) > task_alarm_time`。
        boolean byCreated = "day".equals(scope);
        boolean byDueWindow = "week".equals(scope);
        Instant now = Instant.now();
        LocalDate base = StringUtils.hasText(date) ? LocalDate.parse(date) : LocalDate.now(ZONE);
        Instant wStart = null;
        Instant wEnd = null;
        if (byCreated) {
            wStart = base.atStartOfDay(ZONE).toInstant();
            wEnd = base.plusDays(1).atStartOfDay(ZONE).toInstant();
        } else if (byDueWindow) {
            wStart = base.atStartOfDay(ZONE).toInstant();
            wEnd = base.plusDays(7).atStartOfDay(ZONE).toInstant();
        }

        var wrapper = Wrappers.<Task>lambdaQuery()
                .in(Task::getId, candidate)
                .eq(Task::getStatus, "ACTIVE")
                // 步骤（子任务）不是独立的列表项：只在父任务详情的 subtasks 里展示，
                // 否则每加一个步骤，六大视图/计数里就多出一条「任务」（用户反馈的 BUG）。
                // 旧系统步骤存于独立的子表，天然不会混进 task_record 的列表查询。
                .isNull(Task::getParentId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Task::getTitle, kw).or().like(Task::getContent, kw));
        }
        // **不过滤 completed**：已完成的任务仍留在列表里，由 TaskSorter 排在最后，
        // 前端以删除线呈现（用户反馈：完成任务不应「消失」）。
        // 旧系统同样是「未完成 + 已完成」都在（taskListOne 的未完成区 + 已完成折叠面板，
        // 两查 completeStatus=0/1），没有把已完成从视图中剔除。
        if (byCreated) {
            // 当天新建：只看建立时间落在所选日期内，与是否设置截止时间无关
            wrapper.ge(Task::getCreatedAt, wStart).lt(Task::getCreatedAt, wEnd);
        } else if (byDueWindow) {
            wrapper.isNotNull(Task::getDueAt).ge(Task::getDueAt, wStart).lt(Task::getDueAt, wEnd);
        }
        // 非周期任务
        wrapper.isNull(Task::getCycleRule);
        List<Task> plainTasks = taskMapper.selectList(wrapper);

        // 周期任务（豁免 completed 过滤，按展开判定）
        var cycleWrapper = Wrappers.<Task>lambdaQuery()
                .in(Task::getId, candidate)
                .eq(Task::getStatus, "ACTIVE")
                .isNotNull(Task::getCycleRule)
                // 同 plain wrapper：步骤不进六大视图
                .isNull(Task::getParentId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            cycleWrapper.and(w -> w.like(Task::getTitle, kw).or().like(Task::getContent, kw));
        }
        if (byCreated) {
            // 周期任务同样按建立时间归属当天（旧系统 getDayTask 不展开周期实例）
            cycleWrapper.ge(Task::getCreatedAt, wStart).lt(Task::getCreatedAt, wEnd);
        }
        List<Task> cycleTasks = taskMapper.selectList(cycleWrapper);

        List<Long> allIds = new ArrayList<>();
        plainTasks.forEach(t -> allIds.add(t.getId()));
        cycleTasks.forEach(t -> allIds.add(t.getId()));
        Map<Long, List<TaskParticipant>> parts = participantsOf(allIds);

        List<TaskVO> out = new ArrayList<>(assemble(plainTasks, parts));
        for (Task t : cycleTasks) {
            CycleRule rule = JsonUtils.parse(t.getCycleRule(), CycleRule.class);
            if (byDueWindow) {
                for (Instant occ : CycleExpander.expand(rule, t.getCycleLastCompleted(), wStart, wEnd, CYCLE_MAX)) {
                    out.add(assembleInstance(t, occ, parts));
                }
            } else {
                Instant next = CycleExpander.firstNext(rule, t.getCycleLastCompleted(), now,
                        now.plusSeconds(3660L * 24 * 365));
                if (next != null) {
                    out.add(assembleInstance(t, next, parts));
                }
            }
        }
        return out;
    }

    private Set<Long> candidateTaskIds(Long me, String scope, boolean subordinate) {
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
        // day/week/all: owner=me 或 participant(me)
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

    /* ==================== 分类 / 自定义栏过滤 ==================== */

    /** 分类**子树**下的任务 id（含自身；共享分类 ADR-015）。 */
    private Set<Long> categoryTaskIds(Long rootId) {
        Set<Long> catIds = categoryService.descendants(rootId);
        if (catIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                        .select(Task::getId).in(Task::getCategoryId, catIds))
                .forEach(t -> ids.add(t.getId()));
        return ids;
    }

    /** 自定义栏条目对应的任务 id（仅本人自定义栏，否则 404）。 */
    private Set<Long> menuTaskIds(Long menuId, Long me) {
        TaskMenu menu = menuMapper.selectById(menuId);
        if (menu == null || !me.equals(menu.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "自定义栏不存在");
        }
        Set<Long> ids = new HashSet<>();
        menuItemMapper.selectList(Wrappers.<TaskMenuItem>lambdaQuery().eq(TaskMenuItem::getMenuId, menuId))
                .forEach(it -> ids.add(it.getTaskId()));
        return ids;
    }

    /* ==================== VO 组装 ==================== */

    private Map<Long, List<TaskParticipant>> participantsOf(List<Long> taskIds) {
        Map<Long, List<TaskParticipant>> map = new HashMap<>();
        if (taskIds == null || taskIds.isEmpty()) {
            return map;
        }
        participantMapper.selectList(Wrappers.<TaskParticipant>lambdaQuery().in(TaskParticipant::getTaskId, taskIds))
                .forEach(p -> map.computeIfAbsent(p.getTaskId(), k -> new ArrayList<>()).add(p));
        return map;
    }

    private List<TaskVO> assemble(List<Task> tasks, Map<Long, List<TaskParticipant>> parts) {
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = tasks.stream().map(Task::getId).toList();

        Map<Long, List<TagVO>> tags = new HashMap<>();
        Map<Long, Long> tagIdByTask = new LinkedHashMap<>();
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
        attachmentMapper.selectList(Wrappers.<Attachment>lambdaQuery().in(Attachment::getTaskId, ids))
                .forEach(a -> files.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>())
                        .add(new FileRef(a.getId(), a.getFileName(), a.getSize())));

        Set<Long> catIds = new HashSet<>();
        tasks.forEach(t -> {
            if (t.getCategoryId() != null) {
                catIds.add(t.getCategoryId());
            }
        });
        Map<Long, String> catNames = new HashMap<>();
        if (!catIds.isEmpty()) {
            categoryMapper.selectList(Wrappers.<Category>lambdaQuery().in(Category::getId, catIds))
                    .forEach(c -> catNames.put(c.getId(), c.getName()));
        }

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
            if (t.getCategoryId() != null && catNames.containsKey(t.getCategoryId())) {
                vo.setCategory(new CategoryBrief(t.getCategoryId(), catNames.get(t.getCategoryId())));
            }
            int[] sc = subCounts.get(t.getId());
            if (sc != null) {
                vo.setSubtaskTotal(sc[0]);
                vo.setSubtaskCompleted(sc[1]);
            }
            out.add(vo);
        }
        return out;
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

    private TaskVO assembleInstance(Task t, Instant occurrence, Map<Long, List<TaskParticipant>> parts) {
        TaskVO assembled = assemble(List.of(t), parts).get(0);
        assembled.setDueAt(occurrence);
        assembled.setCompleted(false);
        return assembled;
    }

    private TaskDetailVO copy(TaskVO vo) {
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
        d.setCategory(vo.getCategory());
        d.setTags(vo.getTags());
        d.setSubtaskCompleted(vo.getSubtaskCompleted());
        d.setSubtaskTotal(vo.getSubtaskTotal());
        d.setFiles(vo.getFiles());
        d.setCreatedAt(vo.getCreatedAt());
        return d;
    }
}
