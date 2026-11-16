package com.bbtc.bluebird;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bbtc.bluebird.modules.ai.application.AiModelService;
import com.bbtc.bluebird.modules.identity.domain.SysUser;
import com.bbtc.bluebird.modules.identity.infrastructure.SysUserMapper;
import com.bbtc.bluebird.modules.note.domain.Note;
import com.bbtc.bluebird.modules.note.infrastructure.NoteMapper;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportGenerationService;
import com.bbtc.bluebird.modules.weeklyreport.application.WeeklyReportService;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReport;
import com.bbtc.bluebird.modules.weeklyreport.domain.WeeklyReportPreference;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportMapper;
import com.bbtc.bluebird.modules.weeklyreport.infrastructure.WeeklyReportPreferenceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时生成：按用户保存的生成类型判断当周是否有数据，没有就跳过、不创建报告。
 *
 * <p>独立测试类使用独立 SQLite 文件，避免 autoGenerate 扫描到其他测试类的用户与日志。
 */
@SpringBootTest(properties = "app.config.release=false")
class WeeklyReportAutoGenerateFlowTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String db = System.getProperty("java.io.tmpdir") + "/bluebird-auto-weekly-" + UUID.randomUUID() + ".db";
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
        try {
            String dir = Files.createTempDirectory("bb-auto-weekly-files").toString();
            registry.add("app.storage.root", () -> dir);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    SysUserMapper userMapper;

    @Autowired
    NoteMapper noteMapper;

    @Autowired
    WeeklyReportMapper reportMapper;

    @Autowired
    WeeklyReportPreferenceMapper preferenceMapper;

    @Autowired
    WeeklyReportService reportService;

    @MockBean
    AiModelService aiModelService;

    @MockBean
    WeeklyReportGenerationService reportGenerationService;

    @Test
    void autoGenerateUsesSavedTypeAndSkipsMissingData() {
        when(aiModelService.hasActive()).thenReturn(true);
        String currentMonday = WeeklyReportService.currentWeekStart();

        SysUser weeklyPrefDailyOnly = user("auto-weekly-pref");
        note(weeklyPrefDailyOnly, currentMonday);
        preference(weeklyPrefDailyOnly, "WEEKLY");

        SysUser dailyPref = user("auto-daily");
        note(dailyPref, currentMonday);
        preference(dailyPref, "DAILY");

        SysUser defaultWeekly = user("auto-default");
        weeklyNote(defaultWeekly, currentMonday);

        assertEquals(2, reportService.autoGenerate(), "每日偏好与默认每周用户各生成一份");

        Map<Long, WeeklyReport> byOwner = reportMapper.selectList(
                        Wrappers.<WeeklyReport>lambdaQuery()
                                .eq(WeeklyReport::getWeekStart, currentMonday))
                .stream()
                .collect(Collectors.toMap(WeeklyReport::getOwnerId, Function.identity(), (a, b) -> a));

        assertFalse(byOwner.containsKey(weeklyPrefDailyOnly.getId()),
                "偏好每周但只有每日记录时必须跳过，不能回退生成");
        assertEquals("DAILY", byOwner.get(dailyPref.getId()).getReportType());
        assertEquals("WEEKLY", byOwner.get(defaultWeekly.getId()).getReportType());
        verify(reportGenerationService).generate(anyLong(), eq(dailyPref.getId()));
        verify(reportGenerationService).generate(anyLong(), eq(defaultWeekly.getId()));
        assertTrue(byOwner.values().stream().allMatch(r -> "GENERATING".equals(r.getStatus())));
    }

    private SysUser user(String prefix) {
        SysUser u = new SysUser();
        String name = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        u.setUsername(name);
        u.setName(prefix);
        u.setRoleCode("COMMON");
        u.setStatus("ACTIVE");
        u.setDeleted(0);
        userMapper.insert(u);
        return u;
    }

    private void preference(SysUser owner, String reportType) {
        WeeklyReportPreference pref = new WeeklyReportPreference();
        pref.setUserId(owner.getId());
        pref.setStyle("concise");
        pref.setReportType(reportType);
        pref.setCreatedBy(owner.getId());
        preferenceMapper.insert(pref);
    }

    private void note(SysUser owner, String logDate) {
        Note note = new Note();
        note.setOwnerId(owner.getId());
        note.setContent("<p>log</p>");
        note.setLogDate(logDate);
        note.setVersion(0);
        note.setDeleted(0);
        noteMapper.insert(note);
    }

    private void weeklyNote(SysUser owner, String weekStart) {
        Note note = new Note();
        note.setOwnerId(owner.getId());
        note.setContent("<p>weekly</p>");
        note.setLogDate(weekStart);
        note.setPeriodType(Note.PERIOD_WEEK);
        note.setPeriodStart(weekStart);
        note.setVersion(0);
        note.setDeleted(0);
        noteMapper.insert(note);
    }
}
