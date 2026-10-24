package com.bbtc.bluebird.modules.note.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.note.domain.NoteTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTag> {
}
