package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 新增巡检问题请求体（POST /patrol-issue/create，PC 巡检记录抽屉 / H5 共用）。
 * <p>必填：title、siteId、content；发现人缺省取当前登录人；time/handle_status/status 由服务端固定。
 */
@Data
public class PatrolIssueCreateRequest {

    /** 问题标题（必填，≤200） */
    private String title;

    /** 所属站点 id（必填，t_auto_hltgq_5nw74_vnqqef.id） */
    private String siteId;

    /** 设备 id（可选，须属于所选站点） */
    private String deviceId;

    /** 问题描述（必填，≤500） */
    private String content;

    /** 风险等级：低风险/中风险/高风险 或 #1#/#2#/#3#（可选） */
    private String riskLevel;

    /** 发现人 id（可选，缺省当前登录人） */
    private String userId;

    /** 关联巡检记录 id（可选，写入 abqezf） */
    private String recordId;

    /** 现场照片 id（可选，本服务上传接口返回；按数组顺序落库） */
    private List<String> photoIds;
}
