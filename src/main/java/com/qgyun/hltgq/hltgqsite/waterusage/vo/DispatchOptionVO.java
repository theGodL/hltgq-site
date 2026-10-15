package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 调度方案下拉选项（调度记录关联方案、方案筛选等）。
 */
@Data
public class DispatchOptionVO {

    private String id;

    /** 编号：DD + 4 位序号 */
    private String dispatchNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    private String gateId;

    private String gateName;

    /** 执行状态（前端英文码） */
    private String status;
}
