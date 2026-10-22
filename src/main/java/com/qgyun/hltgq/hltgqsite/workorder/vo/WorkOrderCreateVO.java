package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 新建工单返回（问题记录「转工单」）。
 */
@Data
public class WorkOrderCreateVO {

    /** 工单主键 id */
    private String id;

    /** 工单编号（GD + 日期 + 时分秒 + 流水） */
    private String code;

    /** 工单标题 */
    private String title;
}
