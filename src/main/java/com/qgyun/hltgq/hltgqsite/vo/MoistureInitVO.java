package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 墒情预测初始墒情 VO（四站点各自最新一条三层含水量有效数据）。
 * <p>mten/mtwenty/mthirty 均有效（非设备异常 -9991/设备不存在 -999）才参与预测。
 */
@Data
public class MoistureInitVO {

    /** 站点标识（stcd，如 9000000132） */
    private String stcd;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 10 厘米土壤含水量(%) */
    private BigDecimal mten;

    /** 20 厘米土壤含水量(%) */
    private BigDecimal mtwenty;

    /** 30 厘米土壤含水量(%) */
    private BigDecimal mthirty;
}
