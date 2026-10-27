package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调度指令（列表行 / 详情共用）。
 * <p>执行时间 execTime 为开闸时刻、完成时间 finishTime 为关闸时刻；
 * 计入水量 = 该闸口在 [execTime, finishTime]（未完成取当前时间）的实测累计流量（带符号），
 * 旧数据 execTime 为空时回落到原执行时段 [startTime, endTime]。
 * <p>planAmount 为计划放水量（选填，对外万m³）；amount 为同值别名（兼容前端字段名）。
 * <p>billed=true 表示已出账（billId 非空）；trail 为调度记录数（动态统计）。
 */
@Data
public class WaterDispatchVO {

    private String id;

    /** 编号：DD + 4 位序号 */
    private String dispatchNo;

    /** 创建时间（YYYY-MM-DD HH:mm） */
    private String createTime;

    private String planId;

    private String planNo;

    private String planName;

    private String demandId;

    private String demandNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    private String gateId;

    private String gateName;

    /** 计划放水量（万m³，选填） */
    private BigDecimal planAmount;

    /** 计划放水量（万m³，= planAmount，兼容前端列表字段名） */
    private BigDecimal amount;

    /** 执行时间（开闸时刻；YYYY-MM-DD HH:mm） */
    private String execTime;

    /** 完成时间（关闸时刻；未完成返回空） */
    private String finishTime;

    /** 指定闸门高度（cm） */
    private Integer gateHeightCm;

    /** 计量窗口起（= execTime，旧数据为原执行时段起） */
    private String startTime;

    /** 计量窗口止（= finishTime，未完成取当前时间；旧数据为原执行时段止） */
    private String endTime;

    /** 调度负责人 */
    private String owner;

    /** 执行状态（前端英文码：pending/running/done/canceled） */
    private String status;

    private String statusName;

    /** 计费账单 ID（非空=已计费） */
    private String billId;

    /** 计费账单编号 */
    private String billNo;

    /** 是否已计费 */
    private Boolean billed;

    /** 调度记录数（trail） */
    private Integer trail;

    private String remark;
}
