package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.util.JsonUtils;
import com.bbtc.bluebird.modules.task.domain.CycleRule;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskParticipant;
import com.bbtc.bluebird.modules.task.dto.CountVO;
import com.bbtc.bluebird.modules.task.dto.TaskDetailVO;
import com.bbtc.bluebird.modules.task.dto.TaskQuery;
import com.bbtc.bluebird.modules.task.dto.TaskSearchItem;
import com.bbtc.bluebird.modules.task.dto.TaskVO;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.util.CycleExpander;
import com.bbtc.bluebird.modules.task.util.TaskSorter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务读查询（02 §4.4/§4.5）：六大视图、详情、计数、日历、子任务。
 *
 * <p>只做「取哪些 id → 加什么条件 → 排序分页」的编排；视图范围的 id 解析在
 * {@link TaskScopeResolver}，实体到 VO 的组装在 {@link TaskVoAssembler}。
 */
@Service
@RequiredArgsConstructor
public class TaskQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int CYCLE_MAX = 100;

    private final TaskMapper taskMapper;
    private final TaskAuthService authService;
    private final TaskScopeResolver scopeResolver;
    private final TaskVoAssembler assembler;

    /* ==================== 列表 / 计数 ==================== */

    public PageResult<TaskVO> page(TaskQuery q) {
        List<TaskVO> list = collect(q.scope(), q.keyword(), q.subordinate(), q.date(), q.menuId());
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
                collect("day", null, false, null, null).size(),
                collect("joined", null, false, null, null).size(),
                collect("assigned", null, false, null, null).size(),
                collect("collect", null, false, null, null).size(),
                collect("all", null, false, null, null).size());
    }

    public List<TaskVO> calendar(Instant start, Instant end) {
        Long me = UserContext.currentUserId();
        Set<Long> candidate = scopeResolver.candidateTaskIds(me, "all", false);
        if (candidate.isEmpty()) {
            return List.of();
        }
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .in(Task::getId, candidate).eq(Task::getStatus, "ACTIVE")
                // 步骤/子任务不计入日历（只在父任务详情里展示）
                .isNull(Task::getParentId));
        Map<Long, List<TaskParticipant>> parts = assembler.participantsOf(tasks.stream().map(Task::getId).toList());
        List<TaskVO> out = new ArrayList<>();
        List<Task> plain = new ArrayList<>();
        for (Task t : tasks) {
            if (t.getCycleRule() == null) {
                plain.add(t);
            } else {
                CycleRule rule = JsonUtils.parse(t.getCycleRule(), CycleRule.class);
                for (Instant occ : CycleExpander.expand(rule, t.getCycleLastCompleted(), start, end, CYCLE_MAX)) {
                    out.add(assembler.assembleInstance(t, occ, parts));
                }
            }
        }
        out.addAll(assembler.assemble(plain, parts));
        return out;
    }

    public List<TaskVO> subtasks(Long parentId) {
        authService.assertVisible(parentId, UserContext.currentUserId());
        List<Task> list = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getParentId, parentId).orderByAsc(Task::getId));
        return assembler.assemble(list, assembler.participantsOf(list.stream().map(Task::getId).toList()));
    }

    /**
     * 任务引用选择器搜索（{@code GET /tasks/search}）：仅返回当前用户可见的任务。
     *
     * <p>keyword 少于 2 字符返回空列表（便于选择器空态）；匹配标题（大小写不敏感）。
     */
    public PageResult<TaskSearchItem> search(String keyword, long page, long size) {
        long p = Math.max(1, page);
        long s = size < 1 ? 20 : Math.min(size, 20);
        if (!StringUtils.hasText(keyword) || keyword.trim().length() < 2) {
            return PageResult.of(List.of(), 0, p, s);
        }
        Long me = UserContext.currentUserId();
        List<Task> candidates = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .like(Task::getTitle, keyword.trim())
                .isNull(Task::getParentId)
                .orderByDesc(Task::getCreatedAt)
                .last("LIMIT 200"));
        List<TaskSearchItem> visible = candidates.stream()
                .filter(t -> authService.isVisible(t, me))
                .map(t -> new TaskSearchItem(t.getId(), t.getTitle(), t.getStatus(), t.getDueAt()))
                .toList();
        int from = (int) Math.min((p - 1) * s, visible.size());
        int to = (int) Math.min(from + s, visible.size());
        return PageResult.of(new ArrayList<>(visible.subList(from, to)), visible.size(), p, s);
    }

    /**
     * 详情：不可见 / 不存在一律 **HTTP 404**（不暴露存在性，与附件下载路径一致）。
     *
     * <p>历史行为是 HTTP 200 + code 10003/30001，会把「存在但无权」和「不存在」区分给调用方，
     * 也与同模块附件路径（404）不一致；现统一为 404 + code 10004。
     */
    public TaskDetailVO detail(Long id) {
        Long me = UserContext.currentUserId();
        Task t = taskMapper.selectById(id);
        if (t == null || !authService.isVisible(t, me)) {
            throw HttpStatusException.notFound();
        }
        List<Task> list = List.of(t);
        TaskVO vo = assembler.assemble(list, assembler.participantsOf(list.stream().map(Task::getId).toList())).get(0);
        TaskDetailVO detail = assembler.copy(vo);
        // 子任务沿用「随父任务」口径：父任务可见即返回全部子任务（与旧行为一致，避免 CC/下属看不到步骤）
        List<Task> children = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getParentId, id).orderByAsc(Task::getId));
        detail.setSubtasks(assembler.assemble(children,
                assembler.participantsOf(children.stream().map(Task::getId).toList())));
        return detail;
    }

    /* ==================== scope 解析 ==================== */

    private List<TaskVO> collect(String scope, String keyword, boolean subordinate, String date, Long menuId) {
        Long me = UserContext.currentUserId();
        Set<Long> candidate = scopeResolver.candidateTaskIds(me, scope, subordinate);
        if (menuId != null) {
            candidate.retainAll(scopeResolver.menuTaskIds(menuId, me));
        }
        if (candidate.isEmpty()) {
            return new ArrayList<>();
        }
        // 我的一天（day）：按**任务建立时间**取当天 —— 对齐旧系统 getDayTask 的
        //   `task_setup_time LIKE 'yyyy-MM-dd'`（旧库该列注释即「任务建立时间」，非截止时间）。
        boolean byCreated = "day".equals(scope);
        Instant now = Instant.now();
        LocalDate base = StringUtils.hasText(date) ? LocalDate.parse(date) : LocalDate.now(ZONE);
        Instant wStart = null;
        Instant wEnd = null;
        if (byCreated) {
            wStart = base.atStartOfDay(ZONE).toInstant();
            wEnd = base.plusDays(1).atStartOfDay(ZONE).toInstant();
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
        Map<Long, List<TaskParticipant>> parts = assembler.participantsOf(allIds);

        List<TaskVO> out = new ArrayList<>(assembler.assemble(plainTasks, parts));
        for (Task t : cycleTasks) {
            CycleRule rule = JsonUtils.parse(t.getCycleRule(), CycleRule.class);
            Instant next = CycleExpander.firstNext(rule, t.getCycleLastCompleted(), now,
                    now.plusSeconds(3660L * 24 * 365));
            if (next != null) {
                out.add(assembler.assembleInstance(t, next, parts));
            }
        }
        return out;
    }
}
