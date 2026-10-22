package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 新增/编辑巡检计划请求（POST /patrol-schedule/create、/update，新增页面提交体）。
 * <p>巡检人员为单选（对应主表 "user" 列）；巡查范围多选站点写关系表。
 * <p>状态不在请求体传入：新增固定落草稿；编辑仅限草稿，状态保持草稿。
 */
@Data
public class PatrolScheduleSaveVO {

    /** 计划 id（仅编辑时传入；新增忽略） */
    private String id;

    /** 计划名称 */
    private String title;

    /** 计划类型：年度计划/月度计划 或编码 #1#/#zjgg# */
    private String planType;

    /** 巡查范围站点 id 列表（至少 1 个） */
    private List<String> siteIds;

    /** 巡查内容（≤1000 字） */
    private String content;

    /** 计划开始时间（yyyy-MM-dd） */
    private String startTime;

    /** 计划结束时间（yyyy-MM-dd，不得早于开始时间） */
    private String endTime;

    /** 巡检人员用户 id（单选，必填） */
    private String inspectorId;
}
