package com.bbtc.bluebird.modules.task.util;

import com.bbtc.bluebird.modules.task.domain.CycleRule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 周期规则展开（纯函数，02 §4.3 修订 M2/R2）。
 *
 * <p>计算式展开：不物化多行；按 {@code freq/interval/byDay} 由 {@code dtstart} 递推，
 * 返回窗口 {@code [start,end]} 内、且晚于 {@code lastCompleted} 的发生时刻。
 */
public final class CycleExpander {

    private static final int HARD_CAP = 2000;

    private CycleExpander() {
    }

    public static List<Instant> expand(CycleRule rule, Instant lastCompleted, Instant start, Instant end, int max) {
        List<Instant> out = new ArrayList<>();
        if (rule == null || rule.freq() == null || rule.dtstart() == null || start == null || end == null) {
            return out;
        }
        ZoneId zone = resolveZone(rule.tz());
        int interval = rule.interval() == null || rule.interval() < 1 ? 1 : rule.interval();
        Integer count = rule.count();
        LocalDate until = parseDate(rule.until());
        List<DayOfWeek> byDay = parseByDay(rule.byDay());
        ZonedDateTime base = rule.dtstart().atZone(zone);
        ZonedDateTime windowEnd = end.atZone(zone);

        for (ZonedDateTime occ : occurrences(rule, zone, interval, byDay, until, count, base, windowEnd)) {
            Instant i = occ.toInstant();
            if (i.isBefore(start) || i.isAfter(end)) {
                continue;
            }
            if (lastCompleted != null && !i.isAfter(lastCompleted)) {
                continue;
            }
            out.add(i);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    /** 首个窗口内未完成发生时刻；无则 null。 */
    public static Instant firstNext(CycleRule rule, Instant lastCompleted, Instant now, Instant horizon) {
        List<Instant> list = expand(rule, lastCompleted, now, horizon, 1);
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<ZonedDateTime> occurrences(CycleRule rule, ZoneId zone, int interval, List<DayOfWeek> byDay,
                                                   LocalDate until, Integer count, ZonedDateTime base,
                                                   ZonedDateTime windowEnd) {
        List<ZonedDateTime> list = new ArrayList<>();
        int produced = 0;
        int guard = 0;
        String freq = rule.freq();

        if ("WEEKLY".equals(freq) && !byDay.isEmpty()) {
            LocalTime time = base.toLocalTime();
            LocalDate weekStart = base.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            outer:
            while (guard++ < HARD_CAP) {
                for (DayOfWeek dw : byDay) {
                    LocalDate d = weekStart.plusDays(dw.getValue() - 1L);
                    ZonedDateTime occ = d.atTime(time).atZone(zone);
                    if (occ.isBefore(base)) {
                        continue;
                    }
                    if (until != null && d.isAfter(until)) {
                        break outer;
                    }
                    if (count != null && produced >= count) {
                        break outer;
                    }
                    produced++;
                    list.add(occ);
                    if (occ.isAfter(windowEnd)) {
                        return list;
                    }
                }
                weekStart = weekStart.plusWeeks(interval);
            }
            return list;
        }

        ZonedDateTime cur = base;
        while (guard++ < HARD_CAP) {
            if (until != null && cur.toLocalDate().isAfter(until)) {
                break;
            }
            if (count != null && produced >= count) {
                break;
            }
            produced++;
            list.add(cur);
            if (cur.isAfter(windowEnd)) {
                break;
            }
            cur = switch (freq) {
                case "DAILY" -> cur.plusDays(interval);
                case "MONTHLY" -> cur.plusMonths(interval);
                default -> cur.plusWeeks(interval);
            };
        }
        return list;
    }

    static ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return value.length() > 10 ? Instant.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
                    : LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<DayOfWeek> parseByDay(List<String> byDay) {
        if (byDay == null || byDay.isEmpty()) {
            return List.of();
        }
        List<DayOfWeek> list = new ArrayList<>();
        for (String d : byDay) {
            DayOfWeek dw = mapDay(d);
            if (dw != null) {
                list.add(dw);
            }
        }
        list.sort((a, b) -> a.getValue() - b.getValue());
        return list;
    }

    /** 支持 2 字母（MO/TU/...）与全称（MONDAY/...）。 */
    private static DayOfWeek mapDay(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "MO", "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TU", "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WE", "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "TH", "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FR", "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SA", "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SU", "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
}
