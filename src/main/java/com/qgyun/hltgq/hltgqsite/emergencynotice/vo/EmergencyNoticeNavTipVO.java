package com.qgyun.hltgq.hltgqsite.emergencynotice.vo;

import lombok.Data;

import java.util.List;

/**
 * 导航页「应急响应 / 响应解除」提示视图。
 * <p>按响应类别分组判定：每个类别取最近一条「已发布」通知，事项为「终止响应」即该类别已解除，
 * 其余事项视为响应仍在进行中。前端可直接用 {@code tipText} 展示，也可按字段自行拼装。
 * <p>展示时长属前端展示口径：后端只返回事实与建议文案，不做过期过滤。
 */
@Data
public class EmergencyNoticeNavTipVO {

    /** 是否存在提示（items 非空） */
    private boolean hasTip;

    /** 提示类型：response 应急响应中 / release 响应解除 / none 无提示（有响应中记录时优先 response） */
    private String tipType;

    /** 应急通知所在应用编码（跳转用） */
    private String appCode;

    /** 应急通知表单编码（前端据此跳转「应急管理-应急通知」） */
    private String pageCode;

    /** 各类别提示（响应中在前，同类保持发布时间倒序） */
    private List<Item> items;

    /** 单个响应类别的当前状态 */
    @Data
    public static class Item {

        /** 通知主键 */
        private String noticeId;

        /** 通知标题 */
        private String title;

        /** 响应类别编码：防汛 #1# / 抗旱 #vtsx# / 其他 #aivs# */
        private String categoryCode;

        /** 响应类别名称 */
        private String categoryName;

        /** 该类别当前状态：response 响应中 / release 已解除 */
        private String status;

        /** 该类别当前状态名称：应急响应中 / 响应已解除 */
        private String statusName;

        /** 最近一条已发布通知的事项编码 */
        private String itemCode;

        /** 最近一条已发布通知的事项名称 */
        private String itemName;

        /** 当前响应等级编码（响应中取最近一次非空等级；解除时取解除通知自身等级，可能为空） */
        private String levelCode;

        /** 当前响应等级名称（Ⅰ级/Ⅱ级/Ⅲ级/Ⅳ级，可能为空） */
        private String levelName;

        /** 上级发布单位 */
        private String publishUnit;

        /** 上级发布时间（yyyy-MM-dd HH:mm，空值回退录入时间） */
        private String publishTime;

        /** 建议提示文案：如「防汛Ⅱ级应急响应启动」「防汛应急响应已解除」 */
        private String tipText;
    }
}
