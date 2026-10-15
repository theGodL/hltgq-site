package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 计划灌溉进度 / 保证率实测聚合行（{@code WaterDispatchMapper.selectActiveDispatchRows}）：
 * 一行 = 某计划下某用水户的一条未取消调度方案，计量窗口为 [startTime, endTime]。
 * <p>时段为 'YYYY-MM-DD HH:mm' 展示文本（与账单计量同格式，直接喂给 WaterUsageMeter）。
 */
@Data
public class DispatchExecRowVO {

    private String planId;

    private String waterUserId;

    /** 方案执行闸口（该闸口的区间累计水量即为方案实测放水量） */
    private String gateId;

    /** 执行时段起（YYYY-MM-DD HH:mm） */
    private String startTime;

    /** 执行时段止（YYYY-MM-DD HH:mm） */
    private String endTime;
}
