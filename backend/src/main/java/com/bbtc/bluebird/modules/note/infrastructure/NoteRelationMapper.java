package com.bbtc.bluebird.modules.note.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.note.domain.NoteRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteRelationMapper extends BaseMapper<NoteRelation> {
}
