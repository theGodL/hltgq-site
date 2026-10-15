package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 调度记录新增 / 编辑请求体。
 * <p>关联方案（dispatchId）选填；若填写，用水户与操作闸口须与方案一致（后端校验）。
 */
@Data
public class WaterDispatchRecordSaveVO {

    /** 关联调度方案（可空） */
    private String dispatchId;

    private String waterUserId;

    /** 操作闸口（必填） */
    private String gateId;

    /** 操作时间（YYYY-MM-DD HH:mm 或 datetime-local 原样） */
    private String opTime;

    /** 操作类型（前端英文码：up/down/adjust；空按 up） */
    private String opType;

    /** 升降高度（cm，0~999；可空） */
    private Integer height;

    /** 操作人 / 闸管员（选人下拉的 id；传了则后端按 id 回查固化姓名，不信任 operator 文本） */
    private String operatorUserId;

    /** 操作人 / 闸管员姓名（必填；未换人时回传原值） */
    private String operator;

    private String remark;
}
