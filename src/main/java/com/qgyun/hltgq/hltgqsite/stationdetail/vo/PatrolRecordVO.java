package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点详情-巡检记录列表行（表 t_auto_hltgq_water_inspection_record）。
 * <p>SQL 列别名与本类属性同名（map-underscore-to-camel-case=false 下 MyBatis 依赖同名映射）；
 * result/status 为翻译文本，*Code 为原始编码（权威值）。
 */
@Data
public class PatrolRecordVO {

    /** 记录编号（巡检记录主键 id，业务表无 code 字段） */
    private String code;

    /** 巡检时间 */
    private LocalDateTime time;

    /** 巡检人员姓名（JOIN t_apaas_uc_user.name，用户被删时为 null） */
    private String person;

    /** 巡检对象（巡检记录 device 多选解析出的设备名，顿号拼接；原始值见 deviceIds） */
    private String object;

    /** 巡检对象原始串（巡检记录 device 列原文，权威值，供排查） */
    private String deviceIds;

    /** 巡检说明（巡检记录 content） */
    private String content;

    /** 巡检结果（result 翻译：待填写/正常/异常/隐患/缺陷/故障） */
    private String result;

    /** 巡检结果编码原文（result：#1#~#6#） */
    private String resultCode;

    /** 是否发现问题（派生：存在关联问题记录 或 result 为异常档 #3#~#6#） */
    private Boolean hasIssue;

    /** 关联问题标题（问题表 abqezf = 本记录 id 的标题顿号拼接，无则 null） */
    private String relatedIssue;

    /** 状态（status 翻译：#1# 草稿、#2# 已提交） */
    private String status;

    /** 状态编码原文（status） */
    private String statusCode;
}
