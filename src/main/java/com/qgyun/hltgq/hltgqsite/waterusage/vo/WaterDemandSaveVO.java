package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 需水上报新增 / 编辑请求体（四页面对外数量口径：万m³）。
 */
@Data
public class WaterDemandSaveVO {

    private String waterUserId;

    /** 申报拟用闸口（可空；填写须属于该户闸口组） */
    private String gateId;

    /** 申报计划水量（万m³，>0，≤2 位小数） */
    private BigDecimal amount;

    /** 用水时段起（YYYY-MM-DD） */
    private String startDate;

    /** 用水时段止（YYYY-MM-DD） */
    private String endDate;

    /** 用途 */
    private String usage;

    private String remark;
}
