package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.spi.TagUsageCleaner;
import com.bbtc.bluebird.modules.task.domain.TaskTag;
import com.bbtc.bluebird.modules.task.infrastructure.TaskTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 标签删除时清理 {@code task_tag}。 */
@Component
@RequiredArgsConstructor
public class TaskTagCleaner implements TagUsageCleaner {

    private final TaskTagMapper taskTagMapper;

    @Override
    public void cleanByTagId(Long tagId) {
        taskTagMapper.delete(Wrappers.<TaskTag>lambdaQuery().eq(TaskTag::getTagId, tagId));
    }
}
