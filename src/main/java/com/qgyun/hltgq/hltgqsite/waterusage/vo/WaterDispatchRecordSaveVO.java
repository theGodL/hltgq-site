package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 调度记录新增 / 编辑请求体。
 * <p>用水户、操作闸口、操作时间、操作人必填；关联指令（dispatchId）选填，填写时用水户与操作闸口须与指令一致（后端校验）。
 * <p>操作后闸门高度 gateHeightAfter 单位 cm（0~999，选填）。
 */
@Data
public class WaterDispatchRecordSaveVO {

    /** 关联调度指令（可空） */
    private String dispatchId;

    /** 用水户（必填） */
    private String waterUserId;

    /** 操作闸口（必填） */
    private String gateId;

    /** 操作时间（YYYY-MM-DD HH:mm 或 datetime-local 原样） */
    private String opTime;

    /** 操作后闸门高度（cm，0~999；可空） */
    private Integer gateHeightAfter;

    /** 操作人 / 闸管员（选人下拉的 id；传了则后端按 id 回查固化姓名，不信任 operator 文本） */
    private String operatorUserId;

    /** 操作人 / 闸管员姓名（必填；未换人时回传原值） */
    private String operator;

    private String remark;
}
