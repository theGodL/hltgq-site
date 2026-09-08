package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息中心分页结果：total/current/size/pages 与 PC 端分页结构一致，
 * records 元素结构按消息类型区分（AlertMessage / ComplaintMessage / SuggestionMessage）。
 */
@Data
public class MessagePageVO {

    private long total;

    private long current;

    private long size;

    private long pages;

    private List<?> records;

    /** 告警消息行（已确认/处理中，接收表 JOIN 告警表） */
    @Data
    public static class AlertMessage {

        /** 消息主键（告警表 id） */
        private String messageId;

        /** 告警编号 */
        private String code;

        /** 告警内容 */
        private String content;

        /** 告警级别（原始编码） */
        private String level;

        /** 告警状态编码：#2# 已确认 / #3# 处理中 */
        private String status;

        /** 告警类型：#1# 阈值超限、其他/空 = 异常告警 */
        private String type;

        /** 发生时间 */
        private LocalDateTime time;

        /** 站点 ID */
        private String siteId;

        /** 站点名称（站点被删为 null） */
        private String siteName;

        /** 设备 ID */
        private String deviceId;

        /** 设备名称（设备被删为 null） */
        private String deviceName;

        /** 当前登录人是否已读 */
        private Boolean isRead;
    }

    /** 举报投诉消息行 */
    @Data
    public static class ComplaintMessage {

        /** 消息主键（投诉表 id） */
        private String messageId;

        /** 投诉类型编码（表字段 jllmfa） */
        private String complaintType;

        /** 投诉类型名称（权威映射） */
        private String complaintTypeLabel;

        /** 投诉内容（表字段 humzvp） */
        private String content;

        /** 联系人姓名（表字段 eoitvf） */
        private String contact;

        /** 联系方式（表字段 sgloiw） */
        private String phone;

        /** 提交时间（平台公共字段 created_at） */
        private LocalDateTime createdAt;

        /** 当前登录人是否已读 */
        private Boolean isRead;
    }

    /** 意见征集消息行 */
    @Data
    public static class SuggestionMessage {

        /** 消息主键（意见表 id） */
        private String messageId;

        /** 意见类型编码（表字段 ywitii） */
        private String suggestionType;

        /** 意见类型名称（权威映射） */
        private String suggestionTypeLabel;

        /** 意见内容（表字段 iiyomw） */
        private String content;

        /** 联系人姓名（表字段 ymkxgm） */
        private String contact;

        /** 联系方式（表字段 nxziie） */
        private String phone;

        /** 提交时间（平台公共字段 created_at） */
        private LocalDateTime createdAt;

        /** 当前登录人是否已读 */
        private Boolean isRead;
    }
}
