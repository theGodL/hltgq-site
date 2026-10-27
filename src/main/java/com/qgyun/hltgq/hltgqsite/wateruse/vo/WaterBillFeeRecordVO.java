package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 征收/收缴统计 · 新版水费账单原始记录（内部使用，不出 JSON）。
 * <p>来源表：t_auto_hltgq_water_bill（调度方案出账与手工台账出账统一沉淀）。
 * <p>区域 = 用水户名称（water_user.name）：灌区业务口径为「一县即一个用水户」，户名即行政区名，
 * 故不再经额外区域字段映射；用水户缺失或名称为空时为 null（不计入区域图、仍计入月度趋势）。
 * <p>金额为库内单位「元」：应收 amount / 已收 paid；出参由服务层 ÷10^4 换算为万元。
 * <p>归桶锚点 = 账期终点 period_end：月/季/年账单统一按账期止落月，
 * 与旧水费表单「统计周期区间终点归桶」口径一致。
 */
@Data
public class WaterBillFeeRecordVO {

    /** 账单编号 bill_no（日志核对用） */
    private String billNo;

    /** 区域（用水户名称，如望江县）；用水户缺失或名称为空时为 null */
    private String userName;

    /** 归桶锚点 = 账期终点 period_end（落月用） */
    private LocalDateTime anchorTime;

    /** 应收金额 amount（原始值，单位 元；出参由服务层 ÷10^4 换算为万元） */
    private BigDecimal receivableRaw;

    /** 已收金额 paid（原始值，单位 元；库内默认 0，不区分「未填」） */
    private BigDecimal receivedRaw;
}
