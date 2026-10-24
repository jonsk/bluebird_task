package com.bbtc.bluebird.modules.note.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.note.application.NoteRelationService;
import com.bbtc.bluebird.modules.note.dto.NoteBacklinkVO;
import com.bbtc.bluebird.modules.note.dto.NoteRelationCreateReq;
import com.bbtc.bluebird.modules.note.dto.NoteRelationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 日志引用接口（不可见 note 一律 404）。 */
@Tag(name = "note")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteRelationController {

    private final NoteRelationService relationService;

    @Operation(summary = "添加引用（幂等）")
    @PostMapping("/{id}/relations")
    public ApiResult<NoteRelationVO> add(@PathVariable Long id,
                                         @Valid @RequestBody NoteRelationCreateReq req) {
        return ApiResult.ok(relationService.add(id, req));
    }

    @Operation(summary = "我引用了谁")
    @GetMapping("/{id}/relations")
    public ApiResult<List<NoteRelationVO>> list(@PathVariable Long id) {
        return ApiResult.ok(relationService.list(id));
    }

    @Operation(summary = "谁引用了我")
    @GetMapping("/{id}/backlinks")
    public ApiResult<List<NoteBacklinkVO>> backlinks(@PathVariable Long id) {
        return ApiResult.ok(relationService.backlinks(id));
    }

    @Operation(summary = "移除引用")
    @DeleteMapping("/{id}/relations/{relationId}")
    public ApiResult<Void> delete(@PathVariable Long id, @PathVariable Long relationId) {
        relationService.delete(id, relationId);
        return ApiResult.ok();
    }
}
