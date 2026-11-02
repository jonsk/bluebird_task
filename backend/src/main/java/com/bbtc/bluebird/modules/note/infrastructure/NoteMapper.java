package com.bbtc.bluebird.modules.note.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /** 逻辑删除（回收站）：记录删除人与时间，保证幂等。 */
    @Update("UPDATE note SET deleted = 1, deleted_at = #{at}, deleted_by = #{by} WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id, @Param("at") Instant at, @Param("by") Long by);

    /** 从回收站恢复（MP 通用 update 被逻辑删条件拦住，故自定义）。 */
    @Update("UPDATE note SET deleted = 0, deleted_at = NULL, deleted_by = NULL WHERE id = #{id} AND deleted = 1")
    int restoreDeleted(@Param("id") Long id);

    /** 彻底删除（物理删行，调用方须先做级联清理）。 */
    @Delete("DELETE FROM note WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);

    /** 读回收站中的日志（绕过逻辑删过滤）。 */
    @Select("SELECT * FROM note WHERE id = #{id} AND deleted = 1")
    Note selectDeletedById(@Param("id") Long id);
}
