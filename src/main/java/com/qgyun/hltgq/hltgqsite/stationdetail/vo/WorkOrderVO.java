package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点详情-维修工单记录列表行（表 t_auto_hltgq_water_work_order）。
 * <p>工单表 time 语义为「要求完成时间」（表内无实际维修完成时间字段）；
 * fault = 工单表 content（故障/内容描述），content = 工单表 result（处理结果）。
 */
@Data
public class WorkOrderVO {

    /** 维修编号（工单表 code，前缀 GD） */
    private String code;

    /** 维修时间（工单表 time = 要求完成时间） */
    private LocalDateTime time;

    /** 维修设备名称（JOIN 设备表 name，设备被删时为 null） */
    private String device;

    /** 故障描述（工单表 content 内容描述） */
    private String fault;

    /** 维修内容（工单表 result 处理结果） */
    private String content;
}
