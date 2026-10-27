package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 调度记录（列表行 / 详情共用）：闸口操作留痕，不参与水量与费用计算。
 * <p>操作后闸门高度 gateHeightAfter（cm）为现场操作结果；关联指令可为空（指令删除后置空保留）。
 */
@Data
public class WaterDispatchRecordVO {

    private String id;

    /** 关联调度指令（可空） */
    private String dispatchId;

    private String dispatchNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    private String gateId;

    private String gateName;

    /** 操作时间（YYYY-MM-DD HH:mm） */
    private String opTime;

    /** 操作后闸门高度（cm，0~999；可空） */
    private Integer gateHeightAfter;

    /** 操作人 / 闸管员 */
    private String operator;

    private String remark;

    /** 创建时间（YYYY-MM-DD HH:mm） */
    private String createTime;
}
