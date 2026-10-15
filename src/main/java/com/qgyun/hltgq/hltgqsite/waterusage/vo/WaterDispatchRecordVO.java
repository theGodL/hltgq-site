package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 调度记录（列表行 / 详情共用）：闸口操作留痕，不参与水量与费用计算。
 * <p>opType 对外为前端英文码 up/down/adjust，opTypeName 为中文名；关联方案可为空（方案删除后置空保留）。
 */
@Data
public class WaterDispatchRecordVO {

    private String id;

    /** 关联调度方案（可空） */
    private String dispatchId;

    private String dispatchNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    private String gateId;

    private String gateName;

    /** 操作时间（YYYY-MM-DD HH:mm） */
    private String opTime;

    /** 操作类型（前端英文码：up/down/adjust） */
    private String opType;

    private String opTypeName;

    /** 升降高度（cm，0~999；可空） */
    private Integer height;

    /** 操作人 / 闸管员 */
    private String operator;

    private String remark;

    /** 创建时间（YYYY-MM-DD HH:mm） */
    private String createTime;
}
