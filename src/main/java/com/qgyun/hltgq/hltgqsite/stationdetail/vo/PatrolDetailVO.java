package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站点详情-巡检记录详情（「查看」弹窗）。
 * <p>现场照片：巡检记录表无照片字段，photos 恒返回空列表；
 * 巡检对象 device 多选格式由 Service 解析（支持逗号/竖线/# 分隔）并回填设备名。
 */
@Data
public class PatrolDetailVO {

    /** 记录编号（巡检记录主键 id） */
    private String code;

    /** 巡检时间 */
    private LocalDateTime time;

    /** 所属计划名称（JOIN 巡检计划表 title，未关联计划为 null） */
    private String plan;

    /** 巡检站点名称（JOIN 档案表 zzkaec） */
    private String site;

    /** 巡检对象（device 多选解析出的设备名，「 / 」拼接；原始值见 deviceIds） */
    private String object;

    /** 巡检对象原始串（device 列原文，权威值，供排查） */
    private String deviceIds;

    /** 巡检人员姓名（JOIN t_apaas_uc_user.name） */
    private String person;

    /** 巡检结果（result 翻译：待填写/正常/异常/隐患/缺陷/故障） */
    private String result;

    /** 巡检结果编码原文 */
    private String resultCode;

    /** 巡检说明（content） */
    private String remark;

    /** 现场照片（表无照片字段，恒空列表） */
    private List<String> photos;

    /** 状态（status 翻译：#1# 草稿、#2# 已提交） */
    private String status;

    /** 状态编码原文 */
    private String statusCode;

    /** 关联问题记录（问题表 abqezf = 本记录 id） */
    private List<IssueItem> issues;

    /**
     * 关联问题行
     */
    @Data
    public static class IssueItem {

        /** 问题编号（问题表 code） */
        private String code;

        /** 问题标题（问题表 title） */
        private String title;

        /** 设备名称（JOIN 设备表 name） */
        private String device;
    }
}
