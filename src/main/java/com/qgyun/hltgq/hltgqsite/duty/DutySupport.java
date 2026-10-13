package com.qgyun.hltgq.hltgqsite.duty;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 值班交接班模块共用支撑（排班/值班记录/交接班三表字典与时间文本工具，
 * 供 DutyScheduleService / DutyAttendanceService 共享；口径见《值班交接班实施方案.md》附录 A/B）。
 * <p>时间列一律以 SQL to_char(...,'YYYY-MM-DD HH24:MI:SS') 的定长文本进出本模块，
 * Java 侧字符串处理，避免金仓自有 timestamp OID 的类型识别问题；
 * 写入侧仍走 LocalDateTime + 全局 LocalDateTimeTypeHandler。
 */
public final class DutySupport {

    /** 班次编码 → 名称（hdbzyd.fjyyhu） */
    private static final Map<String, String> SHIFT_LABELS = new LinkedHashMap<>();

    /** 排班状态编码 → 名称（hdbzyd.peuzwi；#zzkl# 待值班为交班联动/纠偏展示） */
    private static final Map<String, String> SCHEDULE_STATUS_LABELS = new LinkedHashMap<>();

    /** 值班记录状态编码 → 名称（igahxz.ylncfw） */
    private static final Map<String, String> RECORD_STATUS_LABELS = new LinkedHashMap<>();

    /** 排班状态：可编辑/删除的存储态（仅未开始） */
    public static final String SCHEDULE_STATUS_NOT_STARTED = "#1#";

    /** 排班状态：待值班（上一班交班后由系统置位） */
    public static final String SCHEDULE_STATUS_WAITING = "#zzkl#";

    /** 排班状态：值班中（开始值班/签到后置位） */
    public static final String SCHEDULE_STATUS_ON_DUTY = "#cmiu#";

    /** 排班状态：已完成（交班后置位） */
    public static final String SCHEDULE_STATUS_COMPLETED = "#qavx#";

    /** 值班记录状态：值班中（开始值班时写入） */
    public static final String RECORD_STATUS_ON_DUTY = "#2#";

    /** 值班记录状态：已完成（交班时写入） */
    public static final String RECORD_STATUS_COMPLETED = "#3#";

    /** H5 值班提醒标记（遗留列 hfxuuk）：已发送（开始值班时写入，H5 提醒据此可见） */
    public static final String REMINDER_SENT = "#papu#";

    /** 时刻格式（子串定长口径：yyyy-MM-dd HH:mm） */
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 时刻格式（秒级定长口径：yyyy-MM-dd HH:mm:ss，与 SQL to_char 输出对齐） */
    private static final DateTimeFormatter SECOND_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        SHIFT_LABELS.put("#1#", "白班");
        SHIFT_LABELS.put("#jhyu#", "中班");
        SHIFT_LABELS.put("#zsfh#", "夜班");

        SCHEDULE_STATUS_LABELS.put("#1#", "未开始");
        SCHEDULE_STATUS_LABELS.put("#zzkl#", "待值班");
        SCHEDULE_STATUS_LABELS.put("#cmiu#", "值班中");
        SCHEDULE_STATUS_LABELS.put("#qavx#", "已完成");

        RECORD_STATUS_LABELS.put("#2#", "值班中");
        RECORD_STATUS_LABELS.put("#3#", "已完成");
        RECORD_STATUS_LABELS.put("#4#", "异常结束");
    }

    private DutySupport() {
    }

    /** 班次名称（编码；未知编码原样返回） */
    public static String shiftLabel(String code) {
        String c = trim(code);
        return c == null ? null : SHIFT_LABELS.getOrDefault(c, c);
    }

    /** 班次编码归一：中文（白班/中班/夜班）或编码均可，未识别返回 null（调用方 400） */
    public static String shiftCode(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        if (SHIFT_LABELS.containsKey(s)) {
            return s;
        }
        for (Map.Entry<String, String> e : SHIFT_LABELS.entrySet()) {
            if (e.getValue().equals(s)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 排班状态名称（编码；未知编码原样返回） */
    public static String scheduleStatusLabel(String code) {
        String c = trim(code);
        return c == null ? null : SCHEDULE_STATUS_LABELS.getOrDefault(c, c);
    }

    /** 值班记录状态名称（编码；未知编码原样返回） */
    public static String recordStatusLabel(String code) {
        String c = trim(code);
        return c == null ? null : RECORD_STATUS_LABELS.getOrDefault(c, c);
    }

    /**
     * 排班状态展示（读时纠偏，不落库）：存储 #1# 且已到点
     * （now ≥ COALESCE(tneakp_min, owcvsv)，入参 startRaw 即该 COALESCE 文本）时显示「待值班」。
     */
    public static String displayScheduleStatus(String statusCode, String startRaw) {
        if (SCHEDULE_STATUS_NOT_STARTED.equals(statusCode)) {
            LocalDateTime start = parseTs(startRaw);
            if (start != null && !LocalDateTime.now().isBefore(start)) {
                return "待值班";
            }
        }
        return scheduleStatusLabel(statusCode);
    }

    /** 时间戳文本 → yyyy-MM-dd（时间戳取前 10 位；其余原样） */
    public static String fmtDate(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        return s.length() > 10 ? s.substring(0, 10) : s;
    }

    /** 时间戳文本 → yyyy-MM-dd HH:mm（截取到分钟） */
    public static String fmtMinute(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        return s.length() > 16 ? s.substring(0, 16) : s;
    }

    /** 时间戳文本 → HH:mm（不足 16 字符返回 null；交接班时间线时刻列用） */
    public static String fmtHm(String raw) {
        String s = trim(raw);
        if (s == null || s.length() < 16) {
            return null;
        }
        return s.substring(11, 16);
    }

    /**
     * 班次时间文本：HH:mm-HH:mm；结束日期晚于开始日期时结束段加「次日」前缀
     * （如 20:00-次日08:00）。任一时间为空返回 null。
     */
    public static String fmtShiftTime(String startRaw, String endRaw) {
        return fmtShiftTime(parseTs(startRaw), parseTs(endRaw));
    }

    /** 班次时间文本（LocalDateTime 版；写入双写旧列与接口输出共用同一口径） */
    public static String fmtShiftTime(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return hhmm(start) + "-次日" + hhmm(end);
        }
        return hhmm(start) + "-" + hhmm(end);
    }

    /** LocalDateTime → yyyy-MM-dd HH:mm:ss 定长文本（与 SQL to_char 输出对齐；null 透传） */
    public static String fmtSecond(LocalDateTime time) {
        return time == null ? null : SECOND_FMT.format(time);
    }

    /** 时间戳文本解析：兼容定长时间戳、分钟级文本与纯日期（补 0 点）；不可解析返回 null */
    public static LocalDateTime parseTs(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19).replace(' ', 'T'));
            }
            if (s.length() >= 16) {
                return LocalDateTime.parse(s.substring(0, 16).replace(' ', 'T'));
            }
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10)).atStartOfDay();
            }
        } catch (Exception ignore) {
            // 非法文本按无法解析处理
        }
        return null;
    }

    /**
     * 按时间点推断班次名：08:00 → 白班、16:00 → 中班、00:00 → 夜班（其余或不可解析返回 null）。
     * <p>交接时间线用：上班次名取交接记录原班次开始时间、下班次名取结束时间（即下一班开始点）。
     */
    public static String shiftNameOfTime(String raw) {
        LocalDateTime t = parseTs(raw);
        if (t == null) {
            return null;
        }
        LocalTime hm = t.toLocalTime();
        if (hm.equals(LocalTime.of(8, 0))) {
            return SHIFT_LABELS.get("#1#");
        }
        if (hm.equals(LocalTime.of(16, 0))) {
            return SHIFT_LABELS.get("#jhyu#");
        }
        if (hm.equals(LocalTime.MIDNIGHT)) {
            return SHIFT_LABELS.get("#zsfh#");
        }
        return null;
    }

    /** 去首尾空白，空串按 null 处理 */
    public static String trim(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    /** 必填文本（trim 后为空报 400） */
    public static String requireText(String value, String message) {
        String s = trim(value);
        if (s == null) {
            throw new IllegalArgumentException(message);
        }
        return s;
    }

    /** 人员 id 列表去重（保序；null/空元素剔除） */
    public static List<String> distinctIds(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (String item : raw) {
            String id = trim(item);
            if (id != null && seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 聚合 id 文本拆分（逗号分隔；空 → 空数组） */
    public static List<String> splitIds(String raw) {
        return splitBy(raw, ",");
    }

    /** 聚合姓名文本拆分（顿号分隔；空 → 空数组） */
    public static List<String> splitNames(String raw) {
        return splitBy(raw, "、");
    }

    /** 当前操作人 id（写操作必须有人；会话缺失按未登录 400） */
    public static String requireOperator() {
        UserContext user = UserContextHolder.currentUser();
        String userId = user == null ? null : trim(user.getUserId());
        if (userId == null) {
            throw new IllegalArgumentException("未登录，请重新登录");
        }
        return userId;
    }

    /** 文本按分隔符拆分（trim 后剔除空段） */
    private static List<String> splitBy(String raw, String delim) {
        List<String> out = new ArrayList<>();
        String s = trim(raw);
        if (s == null) {
            return out;
        }
        for (String part : s.split(delim)) {
            String item = trim(part);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** 日期参数校验（可空；yyyy-MM-dd，非法值报 400） */
    public static String normalizeDay(String value, String label) {
        String s = trim(value);
        if (s == null) {
            return null;
        }
        try {
            LocalDate.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd：" + s);
        }
        return s;
    }

    /** 时刻参数解析（yyyy-MM-dd HH:mm；非法值报 400） */
    public static LocalDateTime parseMinute(String value, String label) {
        String s = trim(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, MINUTE_FMT);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd HH:mm：" + s);
        }
    }

    /** 页码收敛（1 ~ 100000） */
    public static int clampPage(int page) {
        return Math.min(100_000, Math.max(1, page));
    }

    /** 页大小收敛（1 ~ 100） */
    public static int clampSize(int size) {
        return Math.min(100, Math.max(1, size));
    }

    /** 分页结果（与全站口径一致：total/page/size/pages/records） */
    public static Map<String, Object> pageResult(long total, int page, int size, List<?> records) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", page);
        out.put("size", size);
        out.put("pages", size <= 0 ? 0 : (total + size - 1) / size);
        out.put("records", records);
        return out;
    }

    private static String hhmm(LocalDateTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
