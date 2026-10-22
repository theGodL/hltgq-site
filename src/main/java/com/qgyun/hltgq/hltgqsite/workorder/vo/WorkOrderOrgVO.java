package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 负责部门下拉选项（/work-order/orgs）。
 */
@Data
public class WorkOrderOrgVO {

    /** 部门 id（工单 org 落库值） */
    private String id;

    /** 部门短码（存量工单 org 兼容短码写法） */
    private String code;

    /** 部门名称 */
    private String name;
}
