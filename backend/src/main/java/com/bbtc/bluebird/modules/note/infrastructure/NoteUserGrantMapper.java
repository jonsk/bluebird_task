package com.bbtc.bluebird.modules.note.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.note.domain.NoteUserGrant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteUserGrantMapper extends BaseMapper<NoteUserGrant> {
}
