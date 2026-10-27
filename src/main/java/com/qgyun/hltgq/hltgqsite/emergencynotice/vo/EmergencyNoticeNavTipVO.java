package com.qgyun.hltgq.hltgqsite.emergencynotice.vo;

import lombok.Data;

import java.util.List;

/**
 * 导航页「应急响应 / 响应解除」提示视图。
 * <p>每条通知独立成一条提示（不按响应类别合并）：`status` 直接反映通知状态，
 * 已发布 → {@code published}，已终止 → {@code terminated}（事项「终止响应」同属终止类）。
 * <p>终止类只返回近 3 天的；{@code tipText} 即该条通知录入的「灌区落实要求」原文，可直接展示。
 */
@Data
public class EmergencyNoticeNavTipVO {

    /** 是否存在提示（items 非空） */
    private boolean hasTip;

    /** 汇总类型：published 存在已发布 / terminated 全部已终止 / none 无提示 */
    private String tipType;

    /** 应急通知所在应用编码（跳转用） */
    private String appCode;

    /** 应急通知表单编码（前端据此跳转「应急管理-应急通知」） */
    private String pageCode;

    /** 全部提示（按上级发布时间倒序，同时间再按录入时间倒序；已发布与已终止混排） */
    private List<Item> items;

    /** 单条通知的提示 */
    @Data
    public static class Item {

        /** 通知主键 */
        private String noticeId;

        /** 通知标题 */
        private String title;

        /** 响应类别编码：防汛 #1# / 抗旱 #vtsx# / 其他 #aivs#（仅展示用，不参与合并） */
        private String categoryCode;

        /** 响应类别名称 */
        private String categoryName;

        /** 该条通知状态：published 已发布 / terminated 已终止 */
        private String status;

        /** 该条通知状态名称：已发布 / 已终止 */
        private String statusName;

        /** 通知事项编码：启动/升级/降级/补充工作要求/终止响应 */
        private String itemCode;

        /** 通知事项名称 */
        private String itemName;

        /** 响应等级编码（取该条自身；未填且非解除时回溯更早的同类别通知；可能为空） */
        private String levelCode;

        /** 当前响应等级名称（Ⅰ级/Ⅱ级/Ⅲ级/Ⅳ级，可能为空） */
        private String levelName;

        /** 上级发布单位 */
        private String publishUnit;

        /** 上级发布时间（yyyy-MM-dd HH:mm，空值回退录入时间） */
        private String publishTime;

        /** 灌区落实要求（该条通知表单录入的原文，未填写为 null） */
        private String requirementText;

        /** 提示文案：直接取「灌区落实要求」原文；未填写时回退通知标题（不做字段拼接） */
        private String tipText;
    }
}
