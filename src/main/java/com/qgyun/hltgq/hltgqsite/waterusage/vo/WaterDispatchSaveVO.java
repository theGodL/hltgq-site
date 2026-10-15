package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调度方案编制 / 编辑请求体（四页面对外数量口径：万m³）。
 * <p>状态不在此提交：新方案固定"待执行"，流转经 /water-dispatch/status。
 */
@Data
public class WaterDispatchSaveVO {

    /** 关联用水计划（可空；关联后受该计划该户配额约束） */
    private String planId;

    /** 关联需水申请（可空；须为已通过且同户） */
    private String demandId;

    private String waterUserId;

    /** 调度闸口（必填；须属于该户闸口组） */
    private String gateId;

    /** 计划放水量（万m³，>0，≤2 位小数） */
    private BigDecimal planAmount;

    /** 执行时段起（YYYY-MM-DD HH:mm 或 datetime-local 原样） */
    private String startTime;

    /** 执行时段止 */
    private String endTime;

    /** 调度负责人（选人下拉的 id；传了则后端按 id 回查固化姓名快照，不信任 owner 文本） */
    private String ownerUserId;

    /** 调度负责人姓名（快照回传；仅未换人时生效） */
    private String owner;

    private String remark;
}
