package com.bbtc.bluebird;

import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.LoginUser;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.org.application.DepartmentService;
import com.bbtc.bluebird.modules.task.application.TaskAuthService;
import com.bbtc.bluebird.modules.task.application.TaskQueryService;
import com.bbtc.bluebird.modules.task.application.TaskScopeResolver;
import com.bbtc.bluebird.modules.task.application.TaskVoAssembler;
import com.bbtc.bluebird.modules.task.application.UserLookupService;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskCollect;
import com.bbtc.bluebird.modules.task.domain.TaskMenu;
import com.bbtc.bluebird.modules.task.dto.TaskQuery;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskCollectMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuItemMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskParticipantMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskTagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务读查询的分支单测：搜索可见性过滤、详情 404 不暴露存在性、分页边界、
 * 自定义栏归属校验、下属视图开关、日历空候选。
 *
 * <p>这些分支此前只被端到端流程覆盖，改错会被"看起来正常"的用例漏掉。
 */
class TaskQueryServiceTest {

    private static final long ME = 7L;

    private TaskMapper taskMapper;
    private TaskParticipantMapper participantMapper;
    private TaskTagMapper taskTagMapper;
    private TaskCollectMapper collectMapper;
    private TagMapper tagMapper;
    private TaskMenuMapper menuMapper;
    private TaskMenuItemMapper menuItemMapper;
    private AttachmentMapper attachmentMapper;
    private DepartmentService departmentService;
    private UserLookupService userLookup;
    private TaskAuthService authService;
    private TaskScopeResolver scopeResolver;
    private TaskVoAssembler assembler;
    private TaskQueryService service;

    /**
     * 纯单测没有 Spring 上下文，MyBatis-Plus 的 lambda 列缓存（TableInfo）不会自动注册，
     * 一旦 wrapper 里用到 {@code Task::getId} 这类方法引用就会抛
     * "can not find lambda cache for this entity"。这里按实体预热一次。
     */
    @org.junit.jupiter.api.BeforeAll
    static void warmLambdaCache() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("unit-test");
        for (Class<?> entity : List.of(Task.class, TaskCollect.class, TaskMenu.class,
                com.bbtc.bluebird.modules.task.domain.TaskMenuItem.class,
                com.bbtc.bluebird.modules.task.domain.TaskParticipant.class,
                com.bbtc.bluebird.modules.task.domain.TaskTag.class,
                com.bbtc.bluebird.modules.task.domain.Tag.class,
                com.bbtc.bluebird.modules.file.domain.Attachment.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        taskMapper = mock(TaskMapper.class);
        participantMapper = mock(TaskParticipantMapper.class);
        taskTagMapper = mock(TaskTagMapper.class);
        collectMapper = mock(TaskCollectMapper.class);
        tagMapper = mock(TagMapper.class);
        menuMapper = mock(TaskMenuMapper.class);
        menuItemMapper = mock(TaskMenuItemMapper.class);
        attachmentMapper = mock(AttachmentMapper.class);
        departmentService = mock(DepartmentService.class);
        userLookup = mock(UserLookupService.class);
        authService = mock(TaskAuthService.class);

        scopeResolver = new TaskScopeResolver(taskMapper, participantMapper, collectMapper,
                menuMapper, menuItemMapper, departmentService);
        ReflectionTestUtils.setField(scopeResolver, "managerCanReadSubordinate", false);
        assembler = new TaskVoAssembler(taskMapper, participantMapper, taskTagMapper, tagMapper,
                attachmentMapper, userLookup, authService);
        service = new TaskQueryService(taskMapper, authService, scopeResolver, assembler);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(ME, "me", "我", 1L, "COMMON", List.of()), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /* ==================== search ==================== */

    @Test
    void 搜索关键词不足两字符直接返回空页且不查库() {
        var result = service.search("a", 1, 20);

        assertEquals(0, result.total());
        assertTrue(result.list().isEmpty());
        verify(taskMapper, never()).selectList(any());
    }

    @Test
    void 搜索按可见性过滤并按上限收敛每页条数() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "周报 A"), task(2L, "周报 B"), task(3L, "周报 C")));
        when(authService.isVisible(any(Task.class), eq(ME))).thenAnswer(i -> {
            Task t = i.getArgument(0);
            return t.getId() != 3L;
        });

        var result = service.search("  周报  ", 1, 999);

        assertEquals(2, result.total(), "不可见任务不应进入结果");
        assertEquals(20, result.size(), "搜索每页上限 20");
        assertEquals(2, result.list().size());
    }

    @Test
    void 搜索第二页只返回剩余条目() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "周报 A"), task(2L, "周报 B"), task(3L, "周报 C")));
        when(authService.isVisible(any(Task.class), eq(ME))).thenReturn(true);

        var result = service.search("周报", 2, 2);

        assertEquals(3, result.total());
        assertEquals(1, result.list().size());
    }

    /* ==================== detail ==================== */

    @Test
    void 详情不存在返回404() {
        when(taskMapper.selectById(1L)).thenReturn(null);

        HttpStatusException e = assertThrows(HttpStatusException.class, () -> service.detail(1L));

        assertEquals(404, e.getStatus().value());
    }

    @Test
    void 详情不可见也返回404_不暴露存在性() {
        when(taskMapper.selectById(1L)).thenReturn(task(1L, "机密"));
        when(authService.isVisible(any(Task.class), eq(ME))).thenReturn(false);

        HttpStatusException e = assertThrows(HttpStatusException.class, () -> service.detail(1L));

        assertEquals(404, e.getStatus().value(), "存在但无权应与不存在同码，避免探测");
    }

    /* ==================== 分页边界 ==================== */

    @Test
    void 列表分页参数越界时归一化() {
        var result = service.page(new TaskQuery("all", null, false, null, null, 0, 500));

        assertEquals(1, result.page(), "page 最小 1");
        assertEquals(100, result.size(), "size 上限 100");
        assertTrue(result.list().isEmpty());
    }

    @Test
    void 列表分页size小于1时按20处理() {
        var result = service.page(new TaskQuery("all", null, false, null, null, 1, 0));

        assertEquals(20, result.size());
    }

    /* ==================== 自定义栏 ==================== */

    @Test
    void 自定义栏不属于本人时报不存在() {
        collectMapperStub(List.of(1L, 2L));
        TaskMenu others = new TaskMenu();
        others.setId(5L);
        others.setUserId(99L);
        when(menuMapper.selectById(5L)).thenReturn(others);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.page(new TaskQuery("collect", null, false, null, 5L, 1, 20)));

        assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    }

    @Test
    void 自定义栏按条目与可见候选取交集() {
        collectMapperStub(List.of(1L, 2L));
        TaskMenu mine = new TaskMenu();
        mine.setId(5L);
        mine.setUserId(ME);
        when(menuMapper.selectById(5L)).thenReturn(mine);
        com.bbtc.bluebird.modules.task.domain.TaskMenuItem item = new com.bbtc.bluebird.modules.task.domain.TaskMenuItem();
        item.setMenuId(5L);
        item.setTaskId(2L);
        when(menuItemMapper.selectList(any())).thenReturn(List.of(item));
        when(taskMapper.selectList(any())).thenReturn(List.of(task(2L, "留下的任务")));
        when(authService.isWritable(any(Task.class), eq(ME))).thenReturn(true);

        var result = service.page(new TaskQuery("collect", null, false, null, 5L, 1, 20));

        assertEquals(1, result.list().size());
        assertEquals(2L, result.list().get(0).getId());
    }

    /* ==================== 计数 / 下属视图 ==================== */

    @Test
    void 计数空库时五视图均为0且都会查对应表() {
        var counts = service.counts();

        assertEquals(0, counts.day());
        assertEquals(0, counts.joined());
        assertEquals(0, counts.assigned());
        assertEquals(0, counts.collect());
        assertEquals(0, counts.all());
        // 五个视图各查一次：收藏走 collect 表，参加/指派走参与人表（各按 role 过滤）。
        verify(collectMapper).selectList(any());
        verify(participantMapper, org.mockito.Mockito.atLeast(2)).selectList(any());
    }

    @Test
    void 下属视图默认关闭时不去查下属() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        service.page(new TaskQuery("all", null, true, null, null, 1, 20));

        verify(departmentService, never()).subordinates(anyLong());
    }

    @Test
    void 下属视图开启后纳入下属名下任务() {
        ReflectionTestUtils.setField(scopeResolver, "managerCanReadSubordinate", true);
        when(departmentService.subordinates(ME)).thenReturn(Set.of(8L, 9L));
        when(taskMapper.selectList(any())).thenReturn(List.of());

        service.page(new TaskQuery("all", null, true, null, null, 1, 20));

        verify(departmentService).subordinates(ME);
    }

    /* ==================== 日历 ==================== */

    @Test
    void 日历无可见候选时直接返回空列表() {
        when(taskMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.calendar(java.time.Instant.now(), java.time.Instant.now().plusSeconds(86400)).isEmpty());
    }

    /* ==================== helpers ==================== */

    private void collectMapperStub(List<Long> taskIds) {
        List<TaskCollect> rows = taskIds.stream().map(id -> {
            TaskCollect c = new TaskCollect();
            c.setUserId(ME);
            c.setTaskId(id);
            return c;
        }).toList();
        when(collectMapper.selectList(any())).thenReturn(rows);
    }

    private Task task(Long id, String title) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setStatus("ACTIVE");
        t.setOwnerId(ME);
        t.setCompleted(0);
        return t;
    }
}
