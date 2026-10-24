package com.bbtc.bluebird.modules.note.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.common.exception.HttpStatusException;
import com.bbtc.bluebird.modules.audit.annotation.OperateLog;
import com.bbtc.bluebird.modules.note.application.NoteCommandService;
import com.bbtc.bluebird.modules.note.application.NoteQueryService;
import com.bbtc.bluebird.modules.note.dto.NoteBrief;
import com.bbtc.bluebird.modules.note.dto.NoteCreateReq;
import com.bbtc.bluebird.modules.note.dto.NoteDetailVO;
import com.bbtc.bluebird.modules.note.dto.NoteQuery;
import com.bbtc.bluebird.modules.note.dto.NoteTagFacetItem;
import com.bbtc.bluebird.modules.note.dto.NoteUpdateReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 工作日志接口（全部需登录；不可见资源统一 404）。 */
@Tag(name = "note")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteCommandService commandService;
    private final NoteQueryService queryService;

    @Operation(summary = "日志列表（7 种 scope）")
    @GetMapping
    public ApiResult<PageResult<NoteBrief>> page(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        NoteQuery query = new NoteQuery(scope, tagId, tagName, keyword, visibility, pinned, archived,
                parseDate(start), parseDate(end), parseDate(date), sort, page, size);
        return ApiResult.ok(queryService.page(query));
    }

    @Operation(summary = "标签 facet（mine 含 id，其余 scope 按 name）")
    @GetMapping("/tags")
    public ApiResult<List<NoteTagFacetItem>> tags(@RequestParam(defaultValue = "mine") String scope) {
        return ApiResult.ok(queryService.tagFacet(scope));
    }

    @Operation(summary = "日志详情")
    @GetMapping("/{id}")
    public ApiResult<NoteDetailVO> detail(@PathVariable Long id) {
        return ApiResult.ok(queryService.detail(id));
    }

    @Operation(summary = "创建日志")
    @RepeatSubmit
    @PostMapping
    public ApiResult<NoteDetailVO> create(@Valid @RequestBody NoteCreateReq req) {
        return ApiResult.ok(commandService.create(req));
    }

    @Operation(summary = "更新日志（携带 version）")
    @PutMapping("/{id}")
    public ApiResult<NoteDetailVO> update(@PathVariable Long id, @RequestBody NoteUpdateReq req) {
        return ApiResult.ok(commandService.update(id, req));
    }

    @Operation(summary = "删除日志（逻辑删除 + 级联）")
    @OperateLog(module = "note", action = "delete")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        commandService.delete(id);
        return ApiResult.ok();
    }

    @Operation(summary = "置顶")
    @PostMapping("/{id}/pin")
    public ApiResult<Void> pin(@PathVariable Long id) {
        commandService.pin(id, true);
        return ApiResult.ok();
    }

    @Operation(summary = "取消置顶")
    @DeleteMapping("/{id}/pin")
    public ApiResult<Void> unpin(@PathVariable Long id) {
        commandService.pin(id, false);
        return ApiResult.ok();
    }

    @Operation(summary = "归档")
    @PostMapping("/{id}/archive")
    public ApiResult<Void> archive(@PathVariable Long id) {
        commandService.archive(id, true);
        return ApiResult.ok();
    }

    @Operation(summary = "取消归档")
    @PostMapping("/{id}/unarchive")
    public ApiResult<Void> unarchive(@PathVariable Long id) {
        commandService.archive(id, false);
        return ApiResult.ok();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw HttpStatusException.badRequest("日期格式非法（YYYY-MM-DD）：" + value);
        }
    }
}
