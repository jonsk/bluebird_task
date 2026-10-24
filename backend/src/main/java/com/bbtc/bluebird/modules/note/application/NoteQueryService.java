package com.bbtc.bluebird.modules.note.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.common.model.UserContext;
import com.bbtc.bluebird.modules.file.domain.Attachment;
import com.bbtc.bluebird.modules.file.infrastructure.AttachmentMapper;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.domain.NoteAttachmentRef;
import com.bbtc.bluebird.modules.note.domain.NoteRelation;
import com.bbtc.bluebird.modules.note.domain.NoteTag;
import com.bbtc.bluebird.modules.note.domain.NoteUserGrant;
import com.bbtc.bluebird.modules.note.domain.Visibility;
import com.bbtc.bluebird.modules.note.dto.NoteAttachmentVO;
import com.bbtc.bluebird.modules.note.dto.NoteBacklinkVO;
import com.bbtc.bluebird.modules.note.dto.NoteBrief;
import com.bbtc.bluebird.modules.note.dto.NoteCalendarDay;
import com.bbtc.bluebird.modules.note.dto.NoteDetailVO;
import com.bbtc.bluebird.modules.note.dto.NoteQuery;
import com.bbtc.bluebird.modules.note.dto.NoteRelationVO;
import com.bbtc.bluebird.modules.note.dto.NoteTagFacetItem;
import com.bbtc.bluebird.modules.note.dto.NoteVO;
import com.bbtc.bluebird.modules.note.infrastructure.NoteAttachmentRefMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteRelationMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteTagMapper;
import com.bbtc.bluebird.modules.note.infrastructure.NoteUserGrantMapper;
import com.bbtc.bluebird.modules.note.util.NoteVisibilitySqlBuilder;
import com.bbtc.bluebird.modules.org.domain.Department;
import com.bbtc.bluebird.modules.org.infrastructure.DepartmentMapper;
import com.bbtc.bluebird.modules.task.application.TaskAuthService;
import com.bbtc.bluebird.modules.task.domain.Tag;
import com.bbtc.bluebird.modules.task.domain.Task;
import com.bbtc.bluebird.modules.task.dto.TagVO;
import com.bbtc.bluebird.modules.task.infrastructure.TagMapper;
import com.bbtc.bluebird.modules.task.infrastructure.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日志读查询：7 种 scope 列表、详情、日历、标签 facet。
 *
 * <p>所有 scope 均通过 {@link NoteVisibilitySqlBuilder} 生成条件（与 isVisible 同源）。
 */
@Service
@RequiredArgsConstructor
public class NoteQueryService {

    private static final int SUMMARY_LIMIT = 200;

    private final NoteMapper noteMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteUserGrantMapper grantMapper;
    private final NoteAttachmentRefMapper attachmentRefMapper;
    private final NoteRelationMapper relationMapper;
    private final TagMapper tagMapper;
    private final SysUserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final AttachmentMapper attachmentMapper;
    private final TaskMapper taskMapper;
    private final TaskAuthService taskAuthService;
    private final NoteAuthService authService;
    private final NoteVisibilitySqlBuilder visibilitySql;

    /* ==================== 列表 ==================== */

    public PageResult<NoteBrief> page(NoteQuery q) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        String scope = StringUtils.hasText(q.scope()) ? q.scope() : "mine";
        if (!visibilitySql.validScope(scope)) {
            throw HttpStatusException.badRequest("非法 scope");
        }
        long page = Math.max(1, q.page());
        long size = q.size() < 1 ? 20 : Math.min(q.size(), 100);

        LambdaQueryWrapper<Note> w = Wrappers.lambdaQuery();
        w.apply(visibilitySql.scopePredicate(scope, me, admin));
        applyArchived(w, scope, q.archived(), admin);
        applyVisibility(w, scope, q.visibility());
        applyPinned(w, q.pinned());
        applyTag(w, scope, q.tagId(), q.tagName());
        applyKeyword(w, q.keyword());
        applyDate(w, q);
        applySort(w, q.sort());

        IPage<Note> p = noteMapper.selectPage(new Page<>(page, size), w);
        List<NoteBrief> list = toBriefs(p.getRecords());
        return PageResult.of(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private void applyArchived(LambdaQueryWrapper<Note> w, String scope, Boolean archived, boolean admin) {
        boolean wantArchived = Boolean.TRUE.equals(archived);
        if ("mine".equals(scope) || ("all".equals(scope) && admin)) {
            w.eq(Note::getArchived, wantArchived ? 1 : 0);
            return;
        }
        // 非 mine：archived=true 被忽略，一律未归档
        w.eq(Note::getArchived, 0);
    }

    private void applyVisibility(LambdaQueryWrapper<Note> w, String scope, String visibility) {
        if (!StringUtils.hasText(visibility)) {
            return;
        }
        Visibility v = Visibility.of(visibility);
        if (v == null) {
            return; // 非法值按「忽略过滤」处理，不报错
        }
        if ("internal".equals(scope) && v != Visibility.INTERNAL) {
            return;
        }
        if ("explore".equals(scope) && v != Visibility.PUBLIC) {
            return;
        }
        w.eq(Note::getVisibility, v.name());
    }

    private void applyPinned(LambdaQueryWrapper<Note> w, Boolean pinned) {
        if (pinned != null) {
            w.eq(Note::getPinned, pinned ? 1 : 0);
        }
    }

    private void applyTag(LambdaQueryWrapper<Note> w, String scope, Long tagId, String tagName) {
        if ("mine".equals(scope) && tagId != null) {
            w.apply("note.id IN (SELECT nt.note_id FROM note_tag nt WHERE nt.tag_id = {0})", tagId);
            return;
        }
        if (StringUtils.hasText(tagName)) {
            w.apply("note.id IN (SELECT nt.note_id FROM note_tag nt"
                    + " JOIN tag t ON t.id = nt.tag_id WHERE t.name = {0})", tagName.trim());
        }
    }

    private void applyKeyword(LambdaQueryWrapper<Note> w, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        w.apply("note.content LIKE {0} ESCAPE '\\'", likePattern(keyword.trim()));
    }

    private void applyDate(LambdaQueryWrapper<Note> w, NoteQuery q) {
        if (q.date() != null) {
            if (q.start() != null || q.end() != null) {
                throw HttpStatusException.badRequest("date 与 start/end 互斥");
            }
            w.eq(Note::getLogDate, q.date().toString());
            return;
        }
        if (q.start() != null) {
            w.ge(Note::getLogDate, q.start().toString());
        }
        if (q.end() != null) {
            w.le(Note::getLogDate, q.end().toString());
        }
    }

    private void applySort(LambdaQueryWrapper<Note> w, String sort) {
        w.orderByDesc(Note::getPinned);
        String s = StringUtils.hasText(sort) ? sort : "log_date_desc";
        switch (s) {
            case "log_date_asc" -> w.orderByAsc(Note::getLogDate).orderByAsc(Note::getCreatedAt);
            case "created_desc" -> w.orderByDesc(Note::getCreatedAt);
            case "updated_desc" -> w.orderByDesc(Note::getUpdatedAt).orderByDesc(Note::getCreatedAt);
            default -> w.orderByDesc(Note::getLogDate).orderByDesc(Note::getCreatedAt);
        }
    }

    private String likePattern(String kw) {
        String escaped = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /* ==================== 详情 ==================== */

    public NoteDetailVO detail(Long id) {
        Note note = authService.requireVisible(id);
        return toDetail(note);
    }

    public NoteDetailVO toDetail(Note note) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        NoteDetailVO vo = new NoteDetailVO();
        fillBase(vo, note, me, admin);
        vo.setContent(note.getContent());
        vo.setOwnerTags(authService.canEdit(note, me) || authService.canManage(note, me)
                ? ownerTags(note.getOwnerId()) : null);
        vo.setAttachments(attachmentVOs(note, me, admin));
        vo.setRelations(relationVOs(note, me));
        vo.setBacklinks(backlinkVOs(note, me));
        return vo;
    }

    private List<TagVO> ownerTags(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return tagMapper.selectList(Wrappers.<Tag>lambdaQuery()
                        .eq(Tag::getOwnerId, ownerId).orderByAsc(Tag::getId))
                .stream().map(t -> new TagVO(t.getId(), t.getName())).toList();
    }

    /* ==================== 日历 ==================== */

    public List<NoteCalendarDay> calendar(int year, int month, String scope) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        String s = StringUtils.hasText(scope) ? scope : "mine";
        if (!visibilitySql.validScope(s)) {
            throw HttpStatusException.badRequest("非法 scope");
        }
        YearMonth ym = YearMonth.of(year, month);
        QueryWrapper<Note> w = new QueryWrapper<>();
        w.select("note.log_date AS d", "COUNT(*) AS c");
        w.apply(visibilitySql.scopePredicate(s, me, admin));
        w.eq("note.archived", 0);
        w.apply("note.deleted = 0");
        w.between("note.log_date", ym.atDay(1).toString(), ym.atEndOfMonth().toString());
        w.groupBy("note.log_date");
        w.orderByAsc("note.log_date");
        List<Map<String, Object>> rows = noteMapper.selectMaps(w);
        List<NoteCalendarDay> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object d = valueIgnoreCase(row, "d", "log_date");
            Object c = valueIgnoreCase(row, "c", "count");
            if (d != null) {
                out.add(new NoteCalendarDay(String.valueOf(d), c == null ? 0 : ((Number) c).longValue()));
            }
        }
        return out;
    }

    private Object valueIgnoreCase(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (row.containsKey(k)) {
                return row.get(k);
            }
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey().equalsIgnoreCase(k)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    /* ==================== 标签 facet ==================== */

    public List<NoteTagFacetItem> tagFacet(String scope) {
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        String s = StringUtils.hasText(scope) ? scope : "mine";
        if (!visibilitySql.validScope(s)) {
            throw HttpStatusException.badRequest("非法 scope");
        }
        String predicate = visibilitySql.scopePredicate(s, me, admin);
        StringBuilder noteFilter = new StringBuilder("SELECT note.id FROM note WHERE ")
                .append(predicate).append(" AND note.deleted = 0");
        if (!("all".equals(s) && admin)) {
            noteFilter.append(" AND note.archived = 0");
        }
        QueryWrapper<NoteTag> w = new QueryWrapper<>();
        w.select("tag_id").inSql("note_id", noteFilter.toString());
        List<NoteTag> links = noteTagMapper.selectList(w);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (NoteTag l : links) {
            counts.merge(l.getTagId(), 1L, Long::sum);
        }
        Map<Long, String> names = new HashMap<>();
        tagMapper.selectBatchIds(counts.keySet()).forEach(t -> names.put(t.getId(), t.getName()));

        if ("mine".equals(s)) {
            List<NoteTagFacetItem> out = new ArrayList<>();
            counts.forEach((id, c) -> {
                String name = names.get(id);
                if (name != null) {
                    out.add(new NoteTagFacetItem(id, name, c));
                }
            });
            out.sort((a, b) -> b.count() != a.count()
                    ? Long.compare(b.count(), a.count()) : a.name().compareTo(b.name()));
            return out;
        }
        Map<String, Long> byName = new LinkedHashMap<>();
        counts.forEach((id, c) -> {
            String name = names.get(id);
            if (name != null) {
                byName.merge(name, c, Long::sum);
            }
        });
        List<NoteTagFacetItem> out = new ArrayList<>();
        byName.forEach((name, c) -> out.add(new NoteTagFacetItem(null, name, c)));
        out.sort((a, b) -> b.count() != a.count()
                ? Long.compare(b.count(), a.count()) : a.name().compareTo(b.name()));
        return out;
    }

    /* ==================== VO 组装 ==================== */

    private List<NoteBrief> toBriefs(List<Note> notes) {
        if (notes.isEmpty()) {
            return List.of();
        }
        Long me = UserContext.currentUserId();
        boolean admin = authService.isAdmin();
        List<Long> ids = notes.stream().map(Note::getId).toList();
        Map<Long, List<TagVO>> tags = tagsOf(ids);
        Set<Long> ownerIds = new HashSet<>();
        notes.forEach(n -> ownerIds.add(n.getOwnerId()));
        Map<Long, String> ownerNames = userNames(ownerIds);
        Map<Long, String> roles = grantRoles(me, ownerIds);

        List<NoteBrief> out = new ArrayList<>();
        for (Note n : notes) {
            String role = roleFor(n, me, admin, roles);
            boolean canEdit = canEdit(n, me, admin, role);
            boolean canManage = canManage(n, me, admin);
            out.add(new NoteBrief(n.getId(), summary(n.getContent()), n.getLogDate(), n.getVisibility(),
                    isTrue(n.getPinned()), isTrue(n.getArchived()), n.getOwnerId(),
                    ownerNames.get(n.getOwnerId()), tags.getOrDefault(n.getId(), List.of()),
                    canEdit, canManage, versionOf(n)));
        }
        return out;
    }

    private void fillBase(NoteVO vo, Note note, Long me, boolean admin) {
        String role = authService.grantRole(note, me);
        vo.setId(note.getId());
        vo.setContentSummary(summary(note.getContent()));
        vo.setLogDate(note.getLogDate());
        vo.setVisibility(note.getVisibility());
        vo.setPinned(isTrue(note.getPinned()));
        vo.setArchived(isTrue(note.getArchived()));
        vo.setOwnerId(note.getOwnerId());
        Map<Long, String> names = userNames(Set.of(note.getOwnerId()));
        vo.setOwnerName(names.get(note.getOwnerId()));
        vo.setOwnerDept(deptName(note.getDeptId()));
        vo.setVersion(versionOf(note));
        vo.setCanEdit(authService.canEdit(note, me));
        vo.setCanManage(authService.canManage(note, me));
        vo.setGrantRole(role);
        vo.setCreatedAt(note.getCreatedAt());
        vo.setUpdatedAt(note.getUpdatedAt());
        vo.setTags(tagsOf(List.of(note.getId())).getOrDefault(note.getId(), List.of()));
    }

    private Map<Long, List<TagVO>> tagsOf(List<Long> noteIds) {
        Map<Long, List<TagVO>> out = new HashMap<>();
        if (noteIds.isEmpty()) {
            return out;
        }
        List<NoteTag> links = noteTagMapper.selectList(
                Wrappers.<NoteTag>lambdaQuery().in(NoteTag::getNoteId, noteIds));
        if (links.isEmpty()) {
            return out;
        }
        Set<Long> tagIds = new HashSet<>();
        links.forEach(l -> tagIds.add(l.getTagId()));
        Map<Long, String> names = new HashMap<>();
        tagMapper.selectBatchIds(tagIds).forEach(t -> names.put(t.getId(), t.getName()));
        for (NoteTag l : links) {
            String name = names.get(l.getTagId());
            if (name != null) {
                out.computeIfAbsent(l.getNoteId(), k -> new ArrayList<>())
                        .add(new TagVO(l.getTagId(), name));
            }
        }
        return out;
    }

    private Map<Long, String> grantRoles(Long me, Set<Long> ownerIds) {
        Map<Long, String> out = new HashMap<>();
        if (me == null || ownerIds.isEmpty()) {
            return out;
        }
        grantMapper.selectList(Wrappers.<NoteUserGrant>lambdaQuery()
                        .eq(NoteUserGrant::getGranteeId, me)
                        .in(NoteUserGrant::getGrantorId, ownerIds)
                        .eq(NoteUserGrant::getStatus, NoteUserGrant.APPROVED))
                .forEach(g -> out.put(g.getGrantorId(), g.getRole()));
        return out;
    }

    private String roleFor(Note n, Long me, boolean admin, Map<Long, String> roles) {
        if (me != null && me.equals(n.getOwnerId())) {
            return "OWNER";
        }
        if (admin) {
            return "ADMIN";
        }
        return roles.get(n.getOwnerId());
    }

    private boolean canEdit(Note n, Long me, boolean admin, String role) {
        if (isTrue(n.getDeleted())) {
            return false;
        }
        if (admin || (me != null && me.equals(n.getOwnerId()))) {
            return true;
        }
        return NoteUserGrant.EDITOR.equals(role) && !isTrue(n.getArchived());
    }

    private boolean canManage(Note n, Long me, boolean admin) {
        if (isTrue(n.getDeleted())) {
            return false;
        }
        return admin || (me != null && me.equals(n.getOwnerId()));
    }

    private Map<Long, String> userNames(Set<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        Set<Long> filtered = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                filtered.add(id);
            }
        }
        if (filtered.isEmpty()) {
            return out;
        }
        userMapper.selectBatchIds(filtered).forEach(u -> out.put(u.getId(), u.getName()));
        return out;
    }

    private String deptName(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Department d = departmentMapper.selectById(deptId);
        return d == null ? null : d.getName();
    }

    public List<NoteAttachmentVO> attachmentVOs(Note note, Long me, boolean admin) {
        List<NoteAttachmentRef> refs = attachmentRefMapper.selectList(
                Wrappers.<NoteAttachmentRef>lambdaQuery()
                        .eq(NoteAttachmentRef::getNoteId, note.getId())
                        .orderByAsc(NoteAttachmentRef::getSort)
                        .orderByAsc(NoteAttachmentRef::getId));
        if (refs.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        refs.forEach(r -> ids.add(r.getAttachmentId()));
        Map<Long, Attachment> files = new HashMap<>();
        attachmentMapper.selectBatchIds(ids).forEach(a -> files.put(a.getId(), a));
        Set<Long> creators = new HashSet<>();
        files.values().forEach(a -> creators.add(a.getCreatedBy()));
        Map<Long, String> names = userNames(creators);
        boolean canRemoveRef = authService.canManage(note, me);

        List<NoteAttachmentVO> out = new ArrayList<>();
        for (NoteAttachmentRef r : refs) {
            Attachment a = files.get(r.getAttachmentId());
            if (a == null) {
                continue;
            }
            boolean canDelete = admin || (a.getCreatedBy() != null && a.getCreatedBy().equals(me));
            out.add(new NoteAttachmentVO(r.getId(), a.getId(), a.getFileName(), a.getSize(),
                    contentType(a.getFileName()),
                    "/notes/" + note.getId() + "/attachments/" + a.getId() + "/download",
                    a.getCreatedBy(), names.get(a.getCreatedBy()), a.getCreatedAt(),
                    canRemoveRef, canDelete));
        }
        return out;
    }

    private List<NoteRelationVO> relationVOs(Note note, Long me) {
        List<NoteRelation> relations = relationMapper.selectList(
                Wrappers.<NoteRelation>lambdaQuery()
                        .eq(NoteRelation::getSourceNoteId, note.getId())
                        .orderByDesc(NoteRelation::getCreatedAt));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> noteIds = new HashSet<>();
        Set<Long> taskIds = new HashSet<>();
        for (NoteRelation r : relations) {
            if (NoteRelation.TARGET_NOTE.equals(r.getTargetType())) {
                noteIds.add(r.getTargetId());
            } else {
                taskIds.add(r.getTargetId());
            }
        }
        Map<Long, Note> notes = new HashMap<>();
        if (!noteIds.isEmpty()) {
            noteMapper.selectBatchIds(noteIds).forEach(n -> notes.put(n.getId(), n));
        }
        Map<Long, Task> tasks = new HashMap<>();
        if (!taskIds.isEmpty()) {
            taskMapper.selectBatchIds(taskIds).forEach(t -> tasks.put(t.getId(), t));
        }
        Set<Long> creatorIds = new HashSet<>();
        relations.forEach(r -> creatorIds.add(r.getCreatedBy()));
        Map<Long, String> names = userNames(creatorIds);

        List<NoteRelationVO> out = new ArrayList<>();
        for (NoteRelation r : relations) {
            String summary;
            if (NoteRelation.TARGET_NOTE.equals(r.getTargetType())) {
                Note target = notes.get(r.getTargetId());
                if (target == null || !authService.isVisible(target, me)) {
                    continue; // 不可见目标整条隐藏
                }
                summary = summary(target.getContent());
            } else {
                Task target = tasks.get(r.getTargetId());
                if (target == null || !taskAuthService.isVisible(target, me)) {
                    continue;
                }
                summary = target.getTitle();
            }
            out.add(new NoteRelationVO(r.getId(), r.getTargetType(), r.getTargetId(), summary,
                    r.getCreatedAt(), r.getCreatedBy(), names.get(r.getCreatedBy())));
        }
        return out;
    }

    private List<NoteBacklinkVO> backlinkVOs(Note note, Long me) {
        List<NoteRelation> relations = relationMapper.selectList(
                Wrappers.<NoteRelation>lambdaQuery()
                        .eq(NoteRelation::getTargetType, NoteRelation.TARGET_NOTE)
                        .eq(NoteRelation::getTargetId, note.getId())
                        .orderByDesc(NoteRelation::getCreatedAt));
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> sourceIds = new HashSet<>();
        relations.forEach(r -> sourceIds.add(r.getSourceNoteId()));
        Map<Long, Note> sources = new HashMap<>();
        noteMapper.selectBatchIds(sourceIds).forEach(n -> sources.put(n.getId(), n));
        Set<Long> userIds = new HashSet<>();
        sources.values().forEach(n -> userIds.add(n.getOwnerId()));
        relations.forEach(r -> userIds.add(r.getCreatedBy()));
        Map<Long, String> names = userNames(userIds);

        List<NoteBacklinkVO> out = new ArrayList<>();
        for (NoteRelation r : relations) {
            Note source = sources.get(r.getSourceNoteId());
            if (source == null || !authService.isVisible(source, me)) {
                continue;
            }
            out.add(new NoteBacklinkVO(r.getId(), source.getId(), summary(source.getContent()),
                    source.getOwnerId(), names.get(source.getOwnerId()),
                    r.getCreatedAt(), r.getCreatedBy(), names.get(r.getCreatedBy())));
        }
        return out;
    }

    /* ==================== 工具 ==================== */

    /** 摘要：前 200 码位（保留换行，不补省略号）。 */
    public static String summary(String content) {
        if (content == null) {
            return null;
        }
        if (content.codePointCount(0, content.length()) <= SUMMARY_LIMIT) {
            return content;
        }
        int end = content.offsetByCodePoints(0, SUMMARY_LIMIT);
        return content.substring(0, end);
    }

    private Long versionOf(Note note) {
        return note.getVersion() == null ? null : note.getVersion().longValue();
    }

    private boolean isTrue(Integer v) {
        return v != null && v == 1;
    }

    private String contentType(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    /** 供创造/更新后返回详情（同一事务内可直接读）。 */
    public Instant now() {
        return Instant.now();
    }

    static LocalDate today() {
        return LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    }
}
