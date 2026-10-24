package com.bbtc.bluebird.modules.note.controller;

import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.modules.note.application.NoteQueryService;
import com.bbtc.bluebird.modules.note.dto.NoteCalendarDay;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** 日历（按 log_date 聚合；scope 与列表共用同一解析）。 */
@Tag(name = "note")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteCalendarController {

    private final NoteQueryService queryService;

    @Operation(summary = "按月统计每日日志数量")
    @GetMapping("/calendar")
    public ApiResult<List<NoteCalendarDay>> calendar(@RequestParam int year,
                                                     @RequestParam int month,
                                                     @RequestParam(defaultValue = "mine") String scope) {
        if (month < 1 || month > 12) {
            throw com.bbtc.bluebird.common.exception.HttpStatusException.badRequest("month 非法");
        }
        YearMonth.of(year, month);
        return ApiResult.ok(queryService.calendar(year, month, scope));
    }
}
