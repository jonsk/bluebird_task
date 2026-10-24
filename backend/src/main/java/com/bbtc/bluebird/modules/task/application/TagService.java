package com.bbtc.bluebird.modules.task.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.common.exception.BusinessException;
import com.bbtc.bluebird.common.exception.ErrorCode;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.common.spi.TagUsageCleaner;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.dto.TagVO;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 标签服务（02 §4.5）。删除时通过 {@link TagUsageCleaner} 清理各实体关联表。 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;
    private final List<TagUsageCleaner> cleaners;

    public List<TagVO> list() {
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery()
                        .eq(Tag::getOwnerId, UserContext.currentUserId()).orderByAsc(Tag::getId))
                .stream().map(t -> new TagVO(t.getId(), t.getName())).toList();
    }

    @Transactional
    public Long create(String name) {
        Long me = UserContext.currentUserId();
        Long cnt = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getOwnerId, me).eq(Tag::getName, name));
        if (cnt != null && cnt > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "标签已存在");
        }
        Tag t = new Tag();
        t.setName(name);
        t.setOwnerId(me);
        tagMapper.insert(t);
        return t.getId();
    }

    @Transactional
    public void update(Long id, String name) {
        Tag t = requireOwned(id);
        t.setName(name);
        tagMapper.updateById(t);
    }

    @Transactional
    public void delete(Long id) {
        requireOwned(id);
        // 同一事务：先清理全部实体的关联（task_tag / note_tag），再删 tag 行；任一失败整体回滚
        for (TagUsageCleaner cleaner : cleaners) {
            cleaner.cleanByTagId(id);
        }
        tagMapper.deleteById(id);
    }

    private Tag requireOwned(Long id) {
        Tag t = tagMapper.selectById(id);
        if (t == null || !t.getOwnerId().equals(UserContext.currentUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        return t;
    }
}
