package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.spi.TagUsageCleaner;
import com.bbtc.bluebird.modules.note.domain.NoteTag;
import com.bbtc.bluebird.modules.note.infrastructure.NoteTagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 标签删除时清理 {@code note_tag}。 */
@Component
@RequiredArgsConstructor
public class NoteTagCleaner implements TagUsageCleaner {

    private final NoteTagMapper noteTagMapper;

    @Override
    public void cleanByTagId(Long tagId) {
        noteTagMapper.delete(Wrappers.<NoteTag>lambdaQuery().eq(NoteTag::getTagId, tagId));
    }
}
