package com.qgyun.hltgq.hltgqsite.emergencynotice.vo;

import lombok.Data;

/**
 * 应急通知「已发布」记录投影（t_auto_hltgq_bcmxdv_lmqaew）。
 * <p>仅取导航提示推导所需字段，按上级发布时间倒序（空值回退录入时间）。
 */
@Data
public class EmergencyNoticeRowVO {

    /** 主键 */
    private String noticeId;

    /** 通知标题 */
    private String title;

    /** 响应类别编码：防汛 #1# / 抗旱 #vtsx# / 其他 #aivs# */
    private String categoryCode;

    /** 通知事项编码：启动 #1# / 升级 #ifde# / 降级 #sjib# / 终止 #doga# / 补充工作要求 #tckv# */
    private String itemCode;

    /** 市级响应等级编码：Ⅰ级 #1# / Ⅱ级 #ggdd# / Ⅲ级 #mytc# / Ⅳ级 #bylw# */
    private String levelCode;

    /** 上级发布单位 */
    private String publishUnit;

    /** 上级发布时间（yyyy-MM-dd HH:mm，空值回退录入时间） */
    private String publishTime;

    /** 通知状态编码：已发布 #ucpd# / 已终止 #mevt# */
    private String noticeStatus;

    /** 灌区落实要求（表单录入原文，可能为空） */
    private String requirementText;
}
