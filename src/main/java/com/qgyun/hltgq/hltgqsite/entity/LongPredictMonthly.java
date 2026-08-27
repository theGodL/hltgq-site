package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 中长期来水逐月汇总（t_auto_hltgq_water_long_predict_monthly）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_long_predict_monthly\"")
public class LongPredictMonthly extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_long_predict_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 年月字符串（monthly[].年月，如 "2026-05"） */
    @TableField("\"year_month\"")
    private String yearMonth;

    /** 真实总量(万方)（monthly[].真实总量_万方） */
    @TableField("\"actual_total\"")
    private Double actualTotal;

    /** 预测总量(万方)（monthly[].预测总量_万方） */
    @TableField("\"predict_total\"")
    private Double predictTotal;

    /** 统计月份：year_month 转该月第一天 */
    @TableField("\"stat_date\"")
    private LocalDateTime statDate;
}
