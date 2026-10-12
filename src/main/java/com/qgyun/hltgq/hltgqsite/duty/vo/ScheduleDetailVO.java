package com.qgyun.hltgq.hltgqsite.duty.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * 排班详情（GET /duty/schedule/detail、保存响应；契约见《值班交接班接口.md》§3.3）。
 * <p>memberIds/memberNames 与顺序一致（nature_order）；status 含读时纠偏。
 */
@Data
public class ScheduleDetailVO {

    /** 排班 id */
    private String id;

    /** 值班日期 yyyy-MM-dd（对外） */
    private String dutyDate;

    /** 班次编码（对外） */
    private String shiftCode;

    /** 班次中文（对外） */
    private String shift;

    /** 值班开始时间 yyyy-MM-dd HH:mm（对外） */
    private String timeStart;

    /** 值班结束时间 yyyy-MM-dd HH:mm（对外） */
    private String timeEnd;

    /** 单位 id（对外） */
    private String orgId;

    /** 单位名称（对外；空回落旧列单位文本） */
    private String orgName;

    /** 带班领导 id（对外） */
    private String leaderId;

    /** 带班领导姓名（对外） */
    private String leaderName;

    /** 值班人员用户 id 数组（对外） */
    private List<String> memberIds;

    /** 值班人员姓名数组（对外） */
    private List<String> memberNames;

    /** 展示状态（含「待值班」纠偏，对外） */
    private String status;

    /** 状态编码原文（peuzwi，对外） */
    private String statusCode;

    /** 值班日期原始值（COALESCE 定长文本，内部转换用） */
    @JsonIgnore
    private String dutyDateRaw;

    /** 值班开始时间原始值（tneakp_min 定长文本，内部转换用） */
    @JsonIgnore
    private String tneakpMinRaw;

    /** 值班结束时间原始值（tneakp_max 定长文本，内部转换用） */
    @JsonIgnore
    private String tneakpMaxRaw;

    /** 旧单位文本（ihdflq，orgName 兜底用） */
    @JsonIgnore
    private String legacyUnit;

    /** 值班人员 id 聚合文本（逗号分隔，内部转换用） */
    @JsonIgnore
    private String memberIdsRaw;

    /** 值班人员姓名聚合文本（顿号连接，内部转换用） */
    @JsonIgnore
    private String memberNamesRaw;
}
