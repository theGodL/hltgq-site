package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点详情-问题记录列表行（表 t_auto_hltgq_knc3g_bpzjoh）。
 * <p>handle/status 为翻译文本，*Code 为原始编码（权威值）；desc 为列别名（PG 关键字需引号）。
 */
@Data
public class IssueRecordVO {

    /** 问题编号（问题表 code） */
    private String code;

    /** 问题标题（问题表 title） */
    private String title;

    /** 所属站点名称（JOIN 档案表 zzkaec） */
    private String site;

    /** 设备名称（JOIN 设备表 name，设备被删时为 null） */
    private String device;

    /** 问题描述（问题表 content） */
    private String desc;

    /** 发现人姓名（JOIN t_apaas_uc_user.name） */
    private String finder;

    /** 发现时间（问题表 time） */
    private LocalDateTime foundAt;

    /** 处理方式（handle_status 翻译：#1# 待确认、#2# 转工单、#3# 直接处理） */
    private String handle;

    /** 处理方式编码原文 */
    private String handleCode;

    /** 状态（status 翻译：#1# 待处理、#2# 处理中、#3# 已转工单、#4# 已关闭、#5# 已作废） */
    private String status;

    /** 状态编码原文 */
    private String statusCode;

    /** 关联工单编号（JOIN 工单表 code，未关联为 null） */
    private String orderNo;
}
