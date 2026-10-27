package com.qgyun.hltgq.hltgqsite.emergencysupport.vo;

import lombok.Data;

import java.util.List;

/**
 * 应急保障列表/详情行（t_auto_hltgq_bcmxdv_ifjcmy）。
 * <p>编码列已由 Service 翻译为中文（categoryName/statusName），附件仅在详情中装配。
 */
@Data
public class EmergencySupportVO {

    private String id;

    /** 编号（diziai，YJBZ-年月日-当日序号，只读） */
    private String code;

    /** 保障类别编码（pxsefg，如 #nkzk#） */
    private String categoryCode;

    /** 保障类别中文 */
    private String categoryName;

    /** 保障措施/资源名称（haqjle） */
    private String name;

    /** 保障内容（fhlhmx） */
    private String content;

    /** 数量/能力（obhwqq） */
    private String capacity;

    /** 责任单位/负责人（bljckc） */
    private String owner;

    /** 适用场景（btjuks） */
    private String scene;

    /** 当前状态编码（gmypct） */
    private String statusCode;

    /** 当前状态中文 */
    private String statusName;

    /** 最近检查/有效期（onizsz，yyyy-MM-dd） */
    private String checkDate;

    /** 响应使用方式（zfvgfx） */
    private String usage;

    /** 创建时间（yyyy-MM-dd HH:mm） */
    private String createdAt;

    /** 附件清单（仅详情返回） */
    private List<EmergencyAttachmentVO> attachments;
}
