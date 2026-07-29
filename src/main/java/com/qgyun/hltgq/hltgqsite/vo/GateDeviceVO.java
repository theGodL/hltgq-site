package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 闸门设备 VO（闸孔设备名称、ID、闸孔编号）
 */
@Data
public class GateDeviceVO {

    /** 闸孔记录 ID */
    private String id;

    /** 闸孔编号（如 "1"、"2"） */
    private String gateNo;

    /** 设备名称（如"南山寺节制闸2#"），由 stnm + gateNo + "#" 拼接 */
    private String deviceName;
}
