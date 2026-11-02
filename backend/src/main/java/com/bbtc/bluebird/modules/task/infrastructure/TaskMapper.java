package com.bbtc.bluebird.modules.task.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bbtc.bluebird.modules.task.domain.Task;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 逻辑删除（回收站）：MP 的 {@code deleteById} 只会写 deleted=1，这里额外记录删除人与时间。
     * 自定义 SQL 不带 {@code deleted=0} 兜底，故显式加条件保证幂等。
     */
    @Update("UPDATE task SET deleted = 1, deleted_at = #{at}, deleted_by = #{by} WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id, @Param("at") Instant at, @Param("by") Long by);

    /** 从回收站恢复：MP 的通用 update 会被逻辑删条件拦住（WHERE deleted=0），故走自定义 SQL。 */
    @Update("UPDATE task SET deleted = 0, deleted_at = NULL, deleted_by = NULL WHERE id = #{id} AND deleted = 1")
    int restoreDeleted(@Param("id") Long id);

    /** 彻底删除（物理删行，调用方须先做级联清理）。 */
    @Delete("DELETE FROM task WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);

    /** 读回收站中的任务（绕过逻辑删过滤）。 */
    @Select("SELECT * FROM task WHERE id = #{id} AND deleted = 1")
    Task selectDeletedById(@Param("id") Long id);

    /** 读任意状态的任务（含已删除），用于校验父链是否已被彻底删除。 */
    @Select("SELECT * FROM task WHERE id = #{id}")
    Task selectRawById(@Param("id") Long id);

    /** 子树 id（含自身，含已删除节点）：删除/恢复/彻底删除都按子树处理。 */
    @Select("WITH RECURSIVE sub(id) AS ("
            + "SELECT id FROM task WHERE id = #{id} "
            + "UNION ALL "
            + "SELECT t.id FROM task t JOIN sub s ON t.parent_id = s.id"
            + ") SELECT id FROM sub")
    List<Long> selectSubtreeIds(@Param("id") Long id);
}
