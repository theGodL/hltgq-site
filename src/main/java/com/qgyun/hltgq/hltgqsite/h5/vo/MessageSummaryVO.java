package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

/**
 * 消息中心未读数汇总（按当前登录人接收记录统计）。
 */
@Data
public class MessageSummaryVO {

    /** 未读告警数（接收记录未读的已确认/处理中告警） */
    private long alertUnread;

    /** 未读举报投诉数 */
    private long complaintUnread;

    /** 未读意见征集数 */
    private long suggestionUnread;

    /** 三者之和 */
    private long totalUnread;
}
