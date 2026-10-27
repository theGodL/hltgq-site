package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工用水台账保存入参（新增 id 为空；编辑必传 id）。
 * <p>cycle 传前端英文码 month/quarter/year，cycleKey 传结算周期（2026-10 / 2026-Q3 / 2026）；
 * 金额由服务端按 volume × price 计算（2 位小数），不信任前端传入。
 */
@Data
public class WaterLedgerSaveVO {

    /** 台账 ID（编辑时必传） */
    private String id;

    /** 用水户 ID */
    private String userId;

    /** 统计周期（前端英文码：month/quarter/year） */
    private String cycle;

    /** 结算周期（2026-10 / 2026-Q3 / 2026） */
    private String cycleKey;

    /** 用水量（m³，>0） */
    private BigDecimal volume;

    /** 单价（元/m³，>0） */
    private BigDecimal price;

    private String remark;
}
