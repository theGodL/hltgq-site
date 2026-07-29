package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 闸门监测-闸孔数据（单孔开度与状态）
 */
@Data
public class GateHoleData {

    /** 闸孔编号，如 "1"、"2"、"3" */
    private String gateNo;

    /** 闸门开度 (m) */
    private BigDecimal openDegree;

    /** 闸门状态（如 开启/关闭/停止 等） */
    private String status;
}
