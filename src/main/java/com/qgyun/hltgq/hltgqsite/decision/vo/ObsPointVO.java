package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逐点观测值（水位/流量的原始采集行，Service 内存取每日 8 时整点值）。
 */
@Data
public class ObsPointVO {

    /** 采集时间 */
    private LocalDateTime tm;

    /** 数值 */
    private Double value;
}
