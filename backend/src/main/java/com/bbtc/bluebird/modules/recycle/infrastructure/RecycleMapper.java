package com.bbtc.bluebird.modules.recycle.infrastructure;

import com.bbtc.bluebird.modules.recycle.dto.RecycleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 回收站只读查询。逻辑删实体（Task/Note）经 MP 会自动拼 {@code deleted=0}，
 * 查回收站必须走自定义 SQL 才能看到 {@code deleted=1} 的行。
 *
 * <p>可见范围 = 自己删的（{@code deleted_by = 我}）；升级前的历史行没有删除人记录
 * （{@code deleted_by IS NULL}），按 owner 归属回落到 owner 自己，避免老数据在回收站里"消失"。
 */
@Mapper
public interface RecycleMapper {

    /**
     * 任务 + 工作日志合并分页（{@code type}=all/task/note），按删除时间倒序；历史行 deleted_at 为 NULL 排在最后。
     */
    @Select("""
            <script>
            SELECT * FROM (
              SELECT 'TASK' AS type, id AS id, title AS title, deleted_at AS deleted_at, deleted_by AS deleted_by
                FROM task WHERE deleted = 1
                <if test="type == 'note'"> AND 1 = 0 </if>
                <if test="userId != null"> AND (deleted_by = #{userId} OR (deleted_by IS NULL AND owner_id = #{userId})) </if>
                <if test="keyword != null and keyword != ''"> AND title LIKE '%' || #{keyword} || '%' </if>
              UNION ALL
              SELECT 'NOTE' AS type, id AS id, substr(content, 1, 60) AS title, deleted_at AS deleted_at, deleted_by AS deleted_by
                FROM note WHERE deleted = 1
                <if test="type == 'task'"> AND 1 = 0 </if>
                <if test="userId != null"> AND (deleted_by = #{userId} OR (deleted_by IS NULL AND owner_id = #{userId})) </if>
                <if test="keyword != null and keyword != ''"> AND content LIKE '%' || #{keyword} || '%' </if>
            )
            ORDER BY deleted_at IS NULL, deleted_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RecycleRow> selectPage(@Param("type") String type,
                               @Param("userId") Long userId,
                               @Param("keyword") String keyword,
                               @Param("limit") long limit,
                               @Param("offset") long offset);

    @Select("""
            <script>
            SELECT
              (SELECT COUNT(*) FROM task WHERE deleted = 1
                <if test="type == 'note'"> AND 1 = 0 </if>
                <if test="userId != null"> AND (deleted_by = #{userId} OR (deleted_by IS NULL AND owner_id = #{userId})) </if>
                <if test="keyword != null and keyword != ''"> AND title LIKE '%' || #{keyword} || '%' </if>
              )
              +
              (SELECT COUNT(*) FROM note WHERE deleted = 1
                <if test="type == 'task'"> AND 1 = 0 </if>
                <if test="userId != null"> AND (deleted_by = #{userId} OR (deleted_by IS NULL AND owner_id = #{userId})) </if>
                <if test="keyword != null and keyword != ''"> AND content LIKE '%' || #{keyword} || '%' </if>
              )
            </script>
            """)
    long countPage(@Param("type") String type,
                   @Param("userId") Long userId,
                   @Param("keyword") String keyword);

    /** 清空回收站用：全部已删任务 id（不区分删除人，ADMIN 专用）。 */
    @Select("SELECT id FROM task WHERE deleted = 1")
    List<Long> selectAllDeletedTaskIds();

    @Select("SELECT id FROM note WHERE deleted = 1")
    List<Long> selectAllDeletedNoteIds();
}
