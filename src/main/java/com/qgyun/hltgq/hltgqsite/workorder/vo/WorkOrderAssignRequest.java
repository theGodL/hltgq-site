package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 工单指派请求（/work-order/assign，仅待指派 #1# 可指派）。
 * <p>指派后状态置为处理中 #2#，负责部门/处理人员/要求完成时间即落库。
 */
@Data
public class WorkOrderAssignRequest {

    /** 工单 id（必填） */
    private String id;

    /** 处理人员 id（必填） */
    private String userId;

    /** 负责部门 id（可选） */
    private String orgId;

    /** 要求完成时间（可选，yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss） */
    private String time;
}
