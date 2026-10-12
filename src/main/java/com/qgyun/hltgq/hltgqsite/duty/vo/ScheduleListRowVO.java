package com.qgyun.hltgq.hltgqsite.duty.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * 排班列表行（GET /duty/schedule/list；契约见《值班交接班接口.md》§3.2）。
 * <p>Mapper 直出原始行，Service 转换为对外字段：
 * date=纠偏/兜底后的值班日期；shiftTime=HH:mm-HH:mm（跨天含「次日」）；
 * org 空时回落旧列单位文本；status 含「待值班」读时纠偏。
 */
@Data
public class ScheduleListRowVO {

    /** 排班 id */
    private String id;

    /** 值班日期 yyyy-MM-dd（对外） */
    private String date;

    /** 班次中文（对外） */
    private String shift;

    /** 班次编码（fjyyhu，对外） */
    private String shiftCode;

    /** 班次时间文本 HH:mm-HH:mm（对外；历史无时间为 null） */
    private String shiftTime;

    /** 值班开始时间 yyyy-MM-dd HH:mm（对外，编辑回显可用） */
    private String timeStart;

    /** 值班结束时间 yyyy-MM-dd HH:mm（对外） */
    private String timeEnd;

    /** 单位名称（对外；新列联单位表，空则旧列文本） */
    private String org;

    /** 单位 id（jepbhm，对外） */
    private String orgId;

    /** 带班领导姓名（对外） */
    private String leader;

    /** 带班领导 id（alidpq，对外） */
    private String leaderId;

    /** 值班人员姓名（顿号连接，对外） */
    private String members;

    /** 值班人员用户 id 数组（对外） */
    private List<String> memberIds;

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

    /** 单位名称原始值（JOIN 单位表 name，内部转换用） */
    @JsonIgnore
    private String orgName;

    /** 旧单位文本（ihdflq，org 兜底用） */
    @JsonIgnore
    private String legacyUnit;

    /** 值班人员 id 聚合文本（逗号分隔，内部转换用） */
    @JsonIgnore
    private String memberIdsRaw;
}
