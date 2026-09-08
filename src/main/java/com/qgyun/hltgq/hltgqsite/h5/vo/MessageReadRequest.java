package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.util.List;

/**
 * 消息标记已读请求体（read 与 read-all 共用：read-all 只传 messageType）。
 */
@Data
public class MessageReadRequest {

    /** 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集 */
    private String messageType;

    /** 消息主键数组（read 必填；read-all 忽略） */
    private List<String> messageIds;
}
