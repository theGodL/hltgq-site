package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调度指令编制 / 编辑请求体（四页面对外数量口径：万m³）。
 * <p>状态不在此提交：新指令固定"待执行"，流转经 /water-dispatch/status。
 * <p>用水户、计划放水量选填；执行时间必填（开闸时刻），闸门高度选填（cm）。
 */
@Data
public class WaterDispatchSaveVO {

    /** 关联用水计划（可空；关联后受该计划该户配额约束） */
    private String planId;

    /** 关联需水申请（可空；须为已通过且同户） */
    private String demandId;

    /** 用水户（选填） */
    private String waterUserId;

    /** 调度闸口（必填） */
    private String gateId;

    /** 计划放水量（万m³，选填；填写时须 >0，≤2 位小数） */
    private BigDecimal planAmount;

    /** 执行时间（开闸时刻；YYYY-MM-DD HH:mm 或 datetime-local 原样） */
    private String execTime;

    /** 指定闸门高度（cm，0~999；选填） */
    private Integer gateHeightCm;

    /** 【历史】执行时段起（水费页调整兼容；新编制不再提交） */
    private String startTime;

    /** 【历史】执行时段止（水费页调整兼容；新编制不再提交） */
    private String endTime;

    /** 调度负责人（选人下拉的 id；传了则后端按 id 回查固化姓名快照，不信任 owner 文本） */
    private String ownerUserId;

    /** 调度负责人姓名（快照回传；仅未换人时生效） */
    private String owner;

    private String remark;
}
