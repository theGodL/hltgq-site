package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 需水上报新增 / 编辑请求体（四页面对外数量口径：万m³）。
 * <p>用水时间按旬维度填写：年 + 月 + 旬（上旬/中旬/下旬，可多选），
 * 起止日期由后端按所选旬自动派生（最早旬首日 ~ 最晚旬末日），前端无需提交。
 * <p>startDate / endDate 为历史字段（旧版任意时段口径），仅兼容旧前端，新表单不再提交。
 */
@Data
public class WaterDemandSaveVO {

    private String waterUserId;

    /** 申报拟用闸口（可空；填写须属于该户闸口组） */
    private String gateId;

    /** 申报计划水量（万m³，>0，≤2 位小数） */
    private BigDecimal amount;

    /** 用水年份（如 2026） */
    private Integer periodYear;

    /** 用水月份（1~12） */
    private Integer periodMonth;

    /** 旬多选（1 上旬 / 2 中旬 / 3 下旬；兼容「上旬/中旬/下旬」文本） */
    private List<String> xunList;

    /** 【历史】用水时段起（YYYY-MM-DD） */
    private String startDate;

    /** 【历史】用水时段止（YYYY-MM-DD） */
    private String endDate;

    /** 用途 */
    private String usage;

    private String remark;
}
