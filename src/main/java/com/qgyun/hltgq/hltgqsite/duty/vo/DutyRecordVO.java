package com.qgyun.hltgq.hltgqsite.duty.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 值班记录列表行（表 t_auto_hltgq_yn8cm_igahxz）。
 * <p>枚举字段返回中文（状态）并附编码原文（权威值）；
 * 值班日期 yaaebo 规范化为 YYYY-MM-DD；班次时间/实际时间为文本原样（可能为 null）。
 */
@Data
public class DutyRecordVO {

    /** 记录主键 id */
    private String id;

    /** 值班日期（yaaebo 规范化为 YYYY-MM-DD） */
    private String dutyDate;

    /** 班次时间（lzcjgq，文本原样，如「08:00-16:00」） */
    private String shift;

    /** 所在单位（sdcgli） */
    private String unit;

    /** 带班领导 id（atfzxl = 用户 id，权威值） */
    private String leaderId;

    /** 带班领导姓名（JOIN 用户表 name，未匹配为 null） */
    private String leader;

    /** 实际开始时间（jlailj，文本原样） */
    private String actualStart;

    /** 实际交班时间（yuyitx，文本原样） */
    private String actualEnd;

    /** 值班内容摘要（rdammr） */
    private String summary;

    /** 状态翻译（ylncfw：#2# 值班中、#3# 已完成、#4# 异常结束） */
    private String status;

    /** 状态编码原文（ylncfw，权威值） */
    private String statusCode;

    /** 值班日期原始值（yaaebo，内部转换用，不出 JSON） */
    @JsonIgnore
    private String dutyDateRaw;
}
