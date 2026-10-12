package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 仓库（t_auto_hltgq_knc3g_twufyx）：单据表单候选 / P4 台账页左树。
 * <p>statusCode（aeleiw）：#1# 启用 / #zlji# 停用；
 * meta 候选只含启用仓库（disabled 恒 false），台账树含全部仓库（停用节点 disabled=true）。
 * <p>departmentId / operatorId 为仓库默认归口科室（dosozz）与默认经办人（ivplza），
 * 选仓库后前端用于带出默认值（可改）；姓名由后端 JOIN 平台部门/用户表得出。
 */
@Data
public class WarehouseVO {

    private String id;

    /** 仓库名称（xoysox） */
    private String name;

    /** 仓库编码（bcewqm） */
    private String code;

    /** 状态编码（aeleiw：#1# 启用 / #zlji# 停用） */
    private String statusCode;

    /** 是否停用（aeleiw = #zlji#） */
    private Boolean disabled;

    /** 默认归口科室 ID（dosozz） */
    private String departmentId;

    private String departmentName;

    /** 默认经办人 ID（ivplza） */
    private String operatorId;

    private String operatorName;
}
