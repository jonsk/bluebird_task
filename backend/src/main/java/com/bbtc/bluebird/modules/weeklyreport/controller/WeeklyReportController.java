package com.bbtc.bluebird.modules.weeklyreport.controller;

import com.bbtc.bluebird.common.annotation.RepeatSubmit;
import com.bbtc.bluebird.common.api.ApiResult;
import com.bbtc.bluebird.common.domain.PageResult;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportService;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportBrief;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportGenerateReq;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportPreferenceReq;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportPreferenceVO;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportUpdateReq;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportVO;
import com.bbtc.bluebird.modules.weeklyreport.dto.WeeklyReportWeekVO;
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

import java.util.List;

/**
 * 个人周报接口（全部需登录；只能访问自己的周报）。
 */
@Tag(name = "weekly-report")
@RestController
@RequestMapping("/api/v1/weekly-reports")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportService service;

    @Operation(summary = "周报列表")
    @GetMapping
    public ApiResult<PageResult<WeeklyReportBrief>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(service.page(page, size));
    }

    @Operation(summary = "可生成周报的周次列表（当前周及之前、有日志的周）")
    @GetMapping("/weeks")
    public ApiResult<List<WeeklyReportWeekVO>> weeks() {
        return ApiResult.ok(service.weeks());
    }

    @Operation(summary = "周报个人偏好")
    @GetMapping("/preference")
    public ApiResult<WeeklyReportPreferenceVO> preference() {
        return ApiResult.ok(service.preference());
    }

    @Operation(summary = "保存周报个人偏好")
    @PutMapping("/preference")
    public ApiResult<WeeklyReportPreferenceVO> savePreference(
            @Valid @RequestBody WeeklyReportPreferenceReq req) {
        return ApiResult.ok(service.savePreference(req));
    }

    @Operation(summary = "周报详情")
    @GetMapping("/{id}")
    public ApiResult<WeeklyReportVO> detail(@PathVariable Long id) {
        return ApiResult.ok(service.detail(id));
    }

    @Operation(summary = "生成周报（异步）")
    @RepeatSubmit
    @PostMapping("/generate")
    public ApiResult<WeeklyReportVO> generate(@RequestBody(required = false) WeeklyReportGenerateReq req) {
        return ApiResult.ok(service.generate(req));
    }

    @Operation(summary = "保存周报正文（定稿）")
    @PutMapping("/{id}")
    public ApiResult<WeeklyReportVO> update(@PathVariable Long id,
                                            @Valid @RequestBody WeeklyReportUpdateReq req) {
        return ApiResult.ok(service.update(id, req));
    }

    @Operation(summary = "删除周报")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
