package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.domain.TaskMenu;
import com.bbtc.bluebird.modules.task.domain.TaskMenuItem;
import com.bbtc.bluebird.modules.task.dto.MenuCmd;
import com.bbtc.bluebird.modules.task.dto.MenuDTO;
import com.bbtc.bluebird.modules.task.dto.MenuItemDTO;
import com.bbtc.bluebird.modules.task.dto.TaskBrief;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMenuItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 自定义栏服务（02 §4.5）。 */
@Service
@RequiredArgsConstructor
public class TaskMenuService {

    private final TaskMenuMapper menuMapper;
    private final TaskMenuItemMapper menuItemMapper;
    private final TaskMapper taskMapper;

    public List<MenuDTO> list(Long userId) {
        Long target = userId != null ? userId : UserContext.currentUserId();
        List<TaskMenu> menus = menuMapper.selectList(Wrappers.<TaskMenu>lambdaQuery()
                .eq(TaskMenu::getUserId, target).orderByAsc(TaskMenu::getSort).orderByAsc(TaskMenu::getId));
        List<MenuDTO> out = new java.util.ArrayList<>();
        for (TaskMenu m : menus) {
            MenuDTO dto = new MenuDTO(m.getId(), m.getUserId(), m.getName(), m.getSort(), null);
            List<TaskMenuItem> items = menuItemMapper.selectList(Wrappers.<TaskMenuItem>lambdaQuery()
                    .eq(TaskMenuItem::getMenuId, m.getId()).orderByAsc(TaskMenuItem::getSort));
            for (TaskMenuItem it : items) {
                Task t = taskMapper.selectById(it.getTaskId());
                TaskBrief brief = t == null ? null
                        : new TaskBrief(t.getId(), t.getTitle(),
                        t.getCompleted() != null && t.getCompleted() == 1, t.getDueAt(), t.getPriority());
                dto.items.add(new MenuItemDTO(it.getId(), it.getMenuId(), it.getTaskId(), it.getSort(), brief));
            }
            out.add(dto);
        }
        return out;
    }

    @Transactional
    public Long create(MenuCmd cmd) {
        TaskMenu m = new TaskMenu();
        m.setUserId(UserContext.currentUserId());
        m.setName(cmd.name());
        m.setSort(cmd.sort() == null ? 0 : cmd.sort());
        m.setVersion(0);
        menuMapper.insert(m);
        return m.getId();
    }

    @Transactional
    public void update(Long id, MenuCmd cmd) {
        TaskMenu m = requireOwned(id);
        if (StringUtils.hasText(cmd.name())) {
            m.setName(cmd.name());
        }
        if (cmd.sort() != null) {
            m.setSort(cmd.sort());
        }
        menuMapper.updateById(m);
    }

    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        menuMapper.deleteById(id);
        menuItemMapper.delete(Wrappers.<TaskMenuItem>lambdaQuery().eq(TaskMenuItem::getMenuId, id));
    }

    @Transactional
    public void addItem(Long menuId, Long taskId) {
        requireOwned(menuId);
        Long cnt = menuItemMapper.selectCount(Wrappers.<TaskMenuItem>lambdaQuery()
                .eq(TaskMenuItem::getMenuId, menuId).eq(TaskMenuItem::getTaskId, taskId));
        if (cnt != null && cnt > 0) {
            return;
        }
        TaskMenuItem it = new TaskMenuItem();
        it.setMenuId(menuId);
        it.setTaskId(taskId);
        it.setSort(0);
        menuItemMapper.insert(it);
    }

    @Transactional
    public void removeItem(Long itemId) {
        TaskMenuItem it = menuItemMapper.selectById(itemId);
        if (it == null) {
            return;
        }
        requireOwned(it.getMenuId());
        menuItemMapper.deleteById(itemId);
    }

    private TaskMenu requireOwned(Long id) {
        TaskMenu m = menuMapper.selectById(id);
        if (m == null || !m.getUserId().equals(UserContext.currentUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "自定义栏不存在");
        }
        return m;
    }
}
